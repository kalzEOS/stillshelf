package com.stillshelf.app.playback.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.playback.notification.PlaybackActionReceiver
import com.stillshelf.app.playback.notification.PlaybackForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Headers

data class PlaybackUiState(
    val isLoading: Boolean = false,
    val book: BookSummary? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) {
    companion object {
        private const val CHANNEL_ID = "stillshelf_playback_v3"
        private const val CHANNEL_NAME = "Playback"
        private const val NOTIFICATION_ID = 1101

        const val ACTION_PLAY_PAUSE = "com.stillshelf.app.playback.action.PLAY_PAUSE"
        const val ACTION_REWIND = "com.stillshelf.app.playback.action.REWIND"
        const val ACTION_FORWARD = "com.stillshelf.app.playback.action.FORWARD"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUiState = MutableStateFlow(PlaybackUiState())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentBookId: String? = null
    private var lastSyncedPositionMs: Long = -1L
    private var syncInFlight = false
    private var cachedContinueListeningItem: ContinueListeningItem? = null
    private var playRequestJob: Job? = null
    private var playRequestToken: Long = 0L
    private var artworkJob: Job? = null
    private var artworkBookId: String? = null
    private var artworkBitmap: Bitmap? = null
    private val attemptedAutoAdvanceTargetsMs = mutableSetOf<Long>()

    private val mediaSession = MediaSessionCompat(appContext, "StillShelfPlayback")
    private var rewindSeconds: Int = 15
    private var forwardSeconds: Int = 15

    val uiState: StateFlow<PlaybackUiState> = mutableUiState.asStateFlow()

    init {
        createNotificationChannel()
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onSkipToPrevious() = performRewindControl()
            override fun onSkipToNext() = performForwardControl()
        })
        mediaSession.isActive = true
        observePlaybackPreferences()
    }

    fun playBook(bookId: String, startPositionMs: Long? = null) {
        if (bookId.isBlank()) return
        if (currentBookId != bookId) {
            attemptedAutoAdvanceTargetsMs.clear()
        }
        val requestToken = beginPlayRequest()
        if (currentBookId == bookId && mediaPlayer != null) {
            if (startPositionMs != null) {
                seekToPosition(startPositionMs)
            }
            resume()
            return
        }

        updateUiState { it.copy(isLoading = true, errorMessage = null) }
        playRequestJob = scope.launch {
            val localDownload = bookDownloadManager.getCompletedDownload(bookId)
            if (localDownload?.localPath != null) {
                if (isStalePlayRequest(requestToken)) return@launch
                val localBook = when (val detailResult = sessionRepository.fetchBookDetail(bookId, forceRefresh = false)) {
                    is AppResult.Success -> detailResult.value.book
                    is AppResult.Error -> BookSummary(
                        id = localDownload.bookId,
                        libraryId = "",
                        title = localDownload.title,
                        authorName = localDownload.authorName,
                        narratorName = null,
                        durationSeconds = localDownload.durationSeconds,
                        coverUrl = localDownload.coverUrl
                    )
                }
                sessionRepository.setLastPlayedBookId(localBook.id)
                val progressResult = sessionRepository.fetchPlaybackProgress(localBook.id)
                if (isStalePlayRequest(requestToken)) return@launch
                val serverResumeMs = when (progressResult) {
                    is AppResult.Success -> {
                        ((progressResult.value?.currentTimeSeconds ?: 0.0) * 1000.0).toLong()
                    }
                    is AppResult.Error -> 0L
                }
                val resumeMs = startPositionMs ?: serverResumeMs
                val progressPercent = when {
                    startPositionMs != null -> null
                    progressResult is AppResult.Success -> progressResult.value?.progressPercent
                    else -> null
                }
                val currentTimeSeconds = when {
                    startPositionMs != null -> (startPositionMs / 1000.0).coerceAtLeast(0.0)
                    progressResult is AppResult.Success -> progressResult.value?.currentTimeSeconds
                    else -> null
                }
                cachedContinueListeningItem = ContinueListeningItem(
                    book = localBook,
                    progressPercent = progressPercent,
                    currentTimeSeconds = currentTimeSeconds
                )
                if (isStalePlayRequest(requestToken)) return@launch
                val localUri = Uri.fromFile(File(localDownload.localPath)).toString()
                prepareAndPlay(
                    bookId = localBook.id,
                    book = localBook,
                    streamUrl = localUri,
                    resumeMs = resumeMs
                )
                return@launch
            }

            when (val sourceResult = sessionRepository.fetchPlaybackSource(bookId)) {
                is AppResult.Success -> {
                    if (isStalePlayRequest(requestToken)) return@launch
                    sessionRepository.setLastPlayedBookId(sourceResult.value.book.id)
                    val progressResult = sessionRepository.fetchPlaybackProgress(sourceResult.value.book.id)
                    if (isStalePlayRequest(requestToken)) return@launch
                    val serverResumeMs = when (progressResult) {
                        is AppResult.Success -> {
                            ((progressResult.value?.currentTimeSeconds ?: 0.0) * 1000.0).toLong()
                        }

                        is AppResult.Error -> 0L
                    }
                    val resumeMs = startPositionMs ?: serverResumeMs
                    val progressPercent = when {
                        startPositionMs != null -> null
                        progressResult is AppResult.Success -> progressResult.value?.progressPercent
                        else -> null
                    }
                    val currentTimeSeconds = when {
                        startPositionMs != null -> (startPositionMs / 1000.0).coerceAtLeast(0.0)
                        progressResult is AppResult.Success -> progressResult.value?.currentTimeSeconds
                        else -> null
                    }
                    cachedContinueListeningItem = ContinueListeningItem(
                        book = sourceResult.value.book,
                        progressPercent = progressPercent,
                        currentTimeSeconds = currentTimeSeconds
                    )
                    if (isStalePlayRequest(requestToken)) return@launch
                    prepareAndPlay(
                        sourceResult.value.book.id,
                        sourceResult.value.book,
                        sourceResult.value.streamUrl,
                        resumeMs = resumeMs
                    )
                }

                is AppResult.Error -> {
                    if (isStalePlayRequest(requestToken)) return@launch
                    updateUiState {
                        it.copy(
                            isLoading = false,
                            errorMessage = sourceResult.message
                        )
                    }
                }
            }
        }
    }

    private fun beginPlayRequest(): Long {
        playRequestJob?.cancel()
        playRequestToken += 1L
        return playRequestToken
    }

    private fun isStalePlayRequest(requestToken: Long): Boolean {
        return requestToken != playRequestToken
    }

    fun playBookFromPosition(bookId: String, startPositionMs: Long) {
        playBook(
            bookId = bookId,
            startPositionMs = startPositionMs.coerceAtLeast(0L)
        )
    }

    fun togglePlayPause() {
        if (uiState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val duration = safeDuration(player)
        val current = safePosition(player)
        val target = if (duration > 0L) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        seekToPosition(targetMs = target, forceSync = true)
    }

    fun seekToProgress(progressFraction: Float, commit: Boolean) {
        val player = mediaPlayer ?: return
        val duration = safeDuration(player)
        if (duration <= 0L) return
        val clamped = progressFraction.coerceIn(0f, 1f)
        val targetMs = (duration.toDouble() * clamped.toDouble()).toLong()
        seekToPosition(targetMs = targetMs, forceSync = commit)
    }

    fun seekToPositionMs(positionMs: Long, commit: Boolean) {
        seekToPosition(targetMs = positionMs.coerceAtLeast(0L), forceSync = commit)
    }

    private fun seekToPosition(targetMs: Long, forceSync: Boolean = true) {
        val player = mediaPlayer ?: return
        val duration = safeDuration(player)
        val clamped = if (duration > 0L) {
            targetMs.coerceIn(0L, duration)
        } else {
            targetMs.coerceAtLeast(0L)
        }
        runCatching { player.seekTo(clamped.toInt()) }
        updateUiState { it.copy(positionMs = clamped) }
        if (forceSync) {
            syncProgress(force = true, isFinished = false)
        }
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        runCatching { player.pause() }
        updateProgress(player)
        syncProgress(force = true, isFinished = false)
        updateUiState { it.copy(isPlaying = false) }
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        runCatching { player.start() }
        updateUiState { it.copy(isPlaying = true, errorMessage = null) }
    }

    private fun prepareAndPlay(bookId: String, book: BookSummary, streamUrl: String, resumeMs: Long) {
        releasePlayer()
        if (artworkBookId != book.id) {
            artworkBitmap = null
            artworkBookId = book.id
        }

        val player = MediaPlayer()
        mediaPlayer = player
        currentBookId = bookId
        lastSyncedPositionMs = -1L
        updateUiState {
            it.copy(
                isLoading = true,
                book = book,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }
        updateCachedFromUiState()

        player.setOnPreparedListener { prepared ->
            val duration = safeDuration(prepared)
            val clampedResume = if (duration > 0L) {
                resumeMs.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
            } else {
                resumeMs.coerceAtLeast(0L)
            }
            if (clampedResume > 0L) {
                runCatching { prepared.seekTo(clampedResume.toInt()) }
            }
            runCatching { prepared.start() }
            updateUiState {
                it.copy(
                    isLoading = false,
                    isPlaying = true,
                    positionMs = clampedResume,
                    durationMs = duration
                )
            }
            updateCachedFromUiState()
            startProgressUpdates()
        }
        player.setOnCompletionListener { completed ->
            val duration = safeDuration(completed)
            handleCompletion(book = book, durationMs = duration)
        }
        player.setOnErrorListener { _, _, _ ->
            updateUiState {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = "Playback failed. Try another book."
                )
            }
            true
        }

        runCatching {
            val parsedUri = Uri.parse(streamUrl)
            val isLocalUri = parsedUri.scheme.equals("file", ignoreCase = true) ||
                parsedUri.scheme.equals("content", ignoreCase = true)
            if (isLocalUri) {
                player.setDataSource(appContext, parsedUri)
            } else {
                val resolvedStream = splitAuthenticatedUrl(streamUrl)
                val headers = resolvedStream.authToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token -> mapOf("Authorization" to authorizationHeaderValue(token)) }
                    .orEmpty()
                player.setDataSource(appContext, Uri.parse(resolvedStream.cleanUrl), headers)
            }
            player.prepareAsync()
        }.onFailure { throwable ->
            updateUiState {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = throwable.message ?: "Unable to start playback."
                )
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    updateProgress(it)
                    syncProgress(force = false, isFinished = false)
                }
                delay(500L)
            }
        }
    }

    private fun updateProgress(player: MediaPlayer) {
        updateUiState {
            it.copy(
                positionMs = safePosition(player),
                durationMs = safeDuration(player)
            )
        }
        updateCachedFromUiState()
    }

    private fun safePosition(player: MediaPlayer): Long {
        return runCatching { player.currentPosition.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun safeDuration(player: MediaPlayer): Long {
        val duration = runCatching { player.duration.toLong() }.getOrDefault(0L)
        return duration.coerceAtLeast(0L)
    }

    private fun releasePlayer() {
        syncProgress(force = true, isFinished = false)
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
        updatePlaybackSurface()
    }

    fun saveProgressSnapshot() {
        syncProgress(force = true, isFinished = false)
    }

    fun cacheContinueListeningItem(item: ContinueListeningItem?) {
        if (item != null) {
            cachedContinueListeningItem = item
        }
    }

    fun getCachedContinueListeningItem(): ContinueListeningItem? = cachedContinueListeningItem

    fun handleExternalPlaybackAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_REWIND -> performRewindControl()
            ACTION_FORWARD -> performForwardControl()
        }
    }

    private fun syncProgress(force: Boolean, isFinished: Boolean) {
        val bookId = currentBookId ?: return
        val state = uiState.value
        val currentMs = state.positionMs.coerceAtLeast(0L)
        if (!force && lastSyncedPositionMs >= 0L && abs(currentMs - lastSyncedPositionMs) < 15_000L) {
            return
        }
        if (syncInFlight) return

        syncInFlight = true
        scope.launch(Dispatchers.IO) {
            sessionRepository.syncPlaybackProgress(
                bookId = bookId,
                currentTimeSeconds = currentMs / 1000.0,
                durationSeconds = state.book?.durationSeconds ?: state.durationMs.takeIf { it > 0L }?.div(1000.0),
                isFinished = isFinished
            )
            lastSyncedPositionMs = currentMs
            syncInFlight = false
        }
    }

    private fun updateCachedFromUiState() {
        val state = uiState.value
        val currentBook = state.book ?: return
        val durationSeconds = currentBook.durationSeconds
            ?: state.durationMs.takeIf { it > 0L }?.div(1000.0)
        val currentSeconds = state.positionMs.coerceAtLeast(0L) / 1000.0
        val progressPercent = durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (currentSeconds / it).coerceIn(0.0, 1.0) }

        cachedContinueListeningItem = ContinueListeningItem(
            book = currentBook,
            progressPercent = progressPercent,
            currentTimeSeconds = currentSeconds
        )
    }

    private fun observePlaybackPreferences() {
        scope.launch {
            sessionPreferences.state.collect { pref ->
                rewindSeconds = pref.skipBackwardSeconds.coerceIn(10, 60)
                forwardSeconds = pref.skipForwardSeconds.coerceIn(10, 60)
                updatePlaybackSurface()
            }
        }
    }

    private fun performRewindControl() {
        seekBy(deltaMs = -(rewindSeconds * 1000L))
    }

    private fun performForwardControl() {
        seekBy(deltaMs = (forwardSeconds * 1000L))
    }

    private inline fun updateUiState(transform: (PlaybackUiState) -> PlaybackUiState) {
        mutableUiState.update(transform)
        updatePlaybackSurface()
    }

    private fun updatePlaybackSurface() {
        val state = uiState.value
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.positionMs,
                if (state.isPlaying) 1f else 0f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
        mediaSession.isActive = state.book != null

        val book = state.book
        if (book == null) {
            artworkJob?.cancel()
            artworkJob = null
            artworkBookId = null
            artworkBitmap = null
            PlaybackForegroundService.stop(appContext)
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            return
        }

        maybeLoadArtwork(book)

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.authorName)
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    (book.durationSeconds?.times(1000.0)?.toLong()) ?: state.durationMs
                )
                .apply {
                    artworkBitmap?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                    }
                }
                .build()
        )
        showPlaybackNotification(state)
    }

    private fun showPlaybackNotification(state: PlaybackUiState) {
        val book = state.book ?: return

        val contentIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    appContext,
                    11,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val rewindIntent = PendingIntent.getBroadcast(
            appContext,
            12,
            Intent(appContext, PlaybackActionReceiver::class.java).apply {
                action = ACTION_REWIND
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            appContext,
            13,
            Intent(appContext, PlaybackActionReceiver::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val forwardIntent = PendingIntent.getBroadcast(
            appContext,
            14,
            Intent(appContext, PlaybackActionReceiver::class.java).apply {
                action = ACTION_FORWARD
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            .setContentTitle(book.title)
            .setContentText(book.authorName)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLargeIcon(artworkBitmap)
            .addAction(android.R.drawable.ic_media_rew, "Rewind", rewindIntent)
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_ff, "Forward", forwardIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        runCatching {
            PlaybackForegroundService.startOrUpdate(appContext, notification)
        }.onFailure {
            // Avoid playback crashes if OEM notification policy rejects a publish attempt.
        }
    }

    private fun maybeLoadArtwork(book: BookSummary) {
        val bookId = book.id
        val coverUrl = book.coverUrl.orEmpty()
        if (coverUrl.isBlank()) return
        if (artworkBookId == bookId && artworkBitmap != null) return
        if (artworkJob?.isActive == true && artworkBookId == bookId) return

        artworkBookId = bookId
        artworkJob?.cancel()
        artworkJob = scope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                val split = splitAuthenticatedUrl(coverUrl)
                val requestBuilder = ImageRequest.Builder(appContext)
                    .data(split.cleanUrl)
                    .allowHardware(false)
                split.authToken
                    ?.takeIf { it.isNotBlank() }
                    ?.let { token ->
                        requestBuilder.headers(
                            Headers.Builder()
                                .add("Authorization", authorizationHeaderValue(token))
                                .build()
                        )
                    }
                val result = appContext.imageLoader.execute(requestBuilder.build())
                result.drawable?.toBitmap()
            }.getOrNull()
            if (bitmap != null && artworkBookId == bookId) {
                artworkBitmap = bitmap
                scope.launch(Dispatchers.Main.immediate) {
                    updatePlaybackSurface()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun handleCompletion(book: BookSummary?, durationMs: Long) {
        if (book == null) {
            completePlayback(durationMs)
            return
        }
        scope.launch {
            val nextStartMs = resolveNextChapterStartMs(
                bookId = book.id,
                finishedStreamDurationMs = durationMs
            )
            if (
                nextStartMs != null &&
                nextStartMs > durationMs &&
                attemptedAutoAdvanceTargetsMs.add(nextStartMs)
            ) {
                playBookFromPosition(bookId = book.id, startPositionMs = nextStartMs)
                return@launch
            }
            completePlayback(durationMs)
        }
    }

    private suspend fun resolveNextChapterStartMs(
        bookId: String,
        finishedStreamDurationMs: Long
    ): Long? {
        return when (val detail = sessionRepository.fetchBookDetail(bookId = bookId, forceRefresh = false)) {
            is AppResult.Success -> {
                detail.value.chapters
                    .map { (it.startSeconds * 1000.0).toLong() }
                    .sorted()
                    .firstOrNull { chapterStartMs ->
                        chapterStartMs > (finishedStreamDurationMs + 500L)
                    }
            }

            is AppResult.Error -> null
        }
    }

    private fun completePlayback(durationMs: Long) {
        updateUiState {
            it.copy(
                isPlaying = false,
                positionMs = durationMs,
                durationMs = durationMs
            )
        }
        updateCachedFromUiState()
        syncProgress(force = true, isFinished = true)
    }
}
