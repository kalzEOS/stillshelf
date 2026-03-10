package com.stillshelf.app.playback.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.stillshelf.app.core.datastore.PlaybackCheckpointSnapshot
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.playback.notification.PlaybackActionReceiver
import com.stillshelf.app.playback.service.PlaybackServiceController
import com.stillshelf.app.playback.sync.PlaybackProgressSyncScheduler
import com.stillshelf.app.playback.sync.PlaybackSyncGate
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

enum class SleepTimerMode {
    Off,
    Duration,
    EndOfChapter
}

data class PlaybackOutputDevice(
    val id: Int?,
    val name: String,
    val typeLabel: String
)

data class PlaybackUiState(
    val isLoading: Boolean = false,
    val book: BookSummary? = null,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val softToneLevel: Float = 0f,
    val boostLevel: Float = 0f,
    val sleepTimerMode: SleepTimerMode = SleepTimerMode.Off,
    val sleepTimerRemainingMs: Long? = null,
    val sleepTimerTotalMs: Long? = null,
    val sleepTimerExpiredPromptVisible: Boolean = false,
    val outputDevices: List<PlaybackOutputDevice> = emptyList(),
    val selectedOutputDeviceId: Int? = null,
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
        private const val CHANNEL_ID = "stillshelf_playback_v4"
        private const val CHANNEL_NAME = "Playback"
        private const val NOTIFICATION_ID = 1101
        private const val ACTIVE_PLAYBACK_SYNC_INTERVAL_MS = 10_000L
        private const val LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS = 2_000L
        private const val PROGRESS_SYNC_RETRY_DELAY_MS = 3_000L
        private const val BACKGROUND_SYNC_MIN_INTERVAL_MS = 2_000L
        private const val PROGRESS_MATCH_EPSILON_SECONDS = 1.0
        private const val LOCK_SCREEN_MODE_SKIP = "skip"
        private const val LOCK_SCREEN_MODE_NEXT = "next"
        private const val LOCK_SCREEN_SECOND_PREVIOUS_POSITION_WINDOW_MS = 1_500L
        private const val LOCK_SCREEN_PREVIOUS_DOUBLE_PRESS_WINDOW_MS = 6_000L
        private const val LOCK_SCREEN_BOOK_NAV_PAGE_SIZE = 200
        private const val LOCK_SCREEN_BOOK_NAV_MAX_PAGES = 20

        const val ACTION_PLAY_PAUSE = "com.stillshelf.app.playback.action.PLAY_PAUSE"
        const val ACTION_REWIND = "com.stillshelf.app.playback.action.REWIND"
        const val ACTION_FORWARD = "com.stillshelf.app.playback.action.FORWARD"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUiState = MutableStateFlow(PlaybackUiState())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var syncQueueJob: Job? = null
    private var currentBookId: String? = null
    private val playbackSyncGate = PlaybackSyncGate(minimumDeltaMs = ACTIVE_PLAYBACK_SYNC_INTERVAL_MS)
    private var cachedContinueListeningItem: ContinueListeningItem? = null
    private var playRequestJob: Job? = null
    private var playRequestToken: Long = 0L
    private var artworkJob: Job? = null
    private var sleepTimerTickerJob: Job? = null
    private var sleepTimerChapterBoundariesMs: List<Long> = emptyList()
    private var sleepTimerTargetBoundaryMs: Long? = null
    private var artworkBookId: String? = null
    private var artworkBitmap: Bitmap? = null
    private var preferredOutputDeviceId: Int? = null
    private var outputRouteDeviceIdsByRouteKey: Map<String, List<Int>> = emptyMap()
    private var outputRouteKeyByDisplayedId: Map<Int, String> = emptyMap()
    private var lastKnownOutputDeviceIds: Set<Int> = emptySet()
    private val attemptedAutoAdvanceTargetsMs = mutableSetOf<Long>()
    private var suppressNextAutoAdvanceOnCompletion = false
    private var lastNotificationSignature: NotificationSignature? = null

    private val mediaSession = MediaSessionCompat(appContext, "StillShelfPlayback")
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playbackAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        scope.launch(Dispatchers.Main.immediate) {
            handleAudioFocusChange(focusChange)
        }
    }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var pendingPlayAfterAudioFocusGain: Boolean = false
    private var pendingPlayStartsProgressUpdates: Boolean = false
    private var wasPausedForTransientAudioFocusLoss: Boolean = false
    private var isDuckedForAudioFocus: Boolean = false
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            scope.launch(Dispatchers.Main.immediate) {
                pauseForNoisyOutput()
            }
        }
    }
    private var noisyAudioReceiverRegistered: Boolean = false
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputDevices(reason = OutputRefreshReason.DeviceAdded)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputDevices(reason = OutputRefreshReason.DeviceRemoved)
        }
    }
    private var rewindSeconds: Int = 15
    private var forwardSeconds: Int = 15
    private var lockScreenControlMode: String = LOCK_SCREEN_MODE_SKIP
    private var currentPlaybackSpeed: Float = 1.0f
    private var currentSoftToneLevel: Float = 0f
    private var currentBoostLevel: Float = 0f
    private var audioEffectsSessionId: Int? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var previousRestartState: PreviousRestartState? = null
    private val pendingSyncRequests = linkedMapOf<String, ProgressSyncRequest>()
    private var lastCheckpointPositionMs: Long = -1L
    private var lastCheckpointSavedAtElapsedMs: Long = 0L
    private var lastAppBackgroundSyncAtElapsedMs: Long = 0L
    private var lastAppBackgroundSyncPositionMs: Long = -1L
    private var observedActiveServerId: String? = null
    private var hasObservedActiveServerId: Boolean = false

    private data class NotificationSignature(
        val bookId: String,
        val title: String,
        val author: String?,
        val isPlaying: Boolean,
        val hasArtwork: Boolean
    )

    private data class PreviousRestartState(
        val bookId: String,
        val restartStartMs: Long,
        val chapterMode: Boolean,
        val triggeredAtElapsedMs: Long
    )

    private data class ProgressSyncRequest(
        val serverId: String?,
        val bookId: String,
        val positionMs: Long,
        val currentTimeSeconds: Double,
        val durationSeconds: Double?,
        val isFinished: Boolean,
        val checkpointSavedAtMs: Long
    )

    private data class ResolvedPlaybackStart(
        val resumeMs: Long,
        val progressPercent: Double?,
        val currentTimeSeconds: Double?,
        val shouldRestartFromBeginning: Boolean
    )

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            lastAppBackgroundSyncAtElapsedMs = 0L
            lastAppBackgroundSyncPositionMs = -1L
        }

        override fun onStop(owner: LifecycleOwner) {
            scope.launch(Dispatchers.Main.immediate) {
                syncProgressOnAppBackgroundIfNeeded()
            }
        }
    }

    private enum class OutputRefreshReason {
        General,
        DeviceAdded,
        DeviceRemoved
    }

    private enum class PauseReason {
        User,
        AudioFocusTransientLoss,
        AudioFocusLoss,
        NoisyOutput,
        Internal
    }

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
            override fun onSkipToPrevious() = performLockScreenPreviousControl()
            override fun onSkipToNext() = performLockScreenNextControl()
        })
        mediaSession.isActive = true
        registerNoisyAudioReceiver()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
        observePlaybackPreferences()
        observeActiveServerSelection()
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

        updateUiState {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sleepTimerExpiredPromptVisible = false
            )
        }
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
                val start = resolvePlaybackStart(
                    bookId = localBook.id,
                    defaultDurationSeconds = localBook.durationSeconds,
                    startPositionMs = startPositionMs,
                    progressResult = progressResult
                )
                val shouldRestartFromBeginning = start.shouldRestartFromBeginning
                val playbackBook = if (shouldRestartFromBeginning) {
                    localBook.copy(
                        progressPercent = 0.0,
                        currentTimeSeconds = 0.0,
                        isFinished = false
                    )
                } else {
                    localBook
                }
                cachedContinueListeningItem = ContinueListeningItem(
                    book = playbackBook,
                    progressPercent = start.progressPercent,
                    currentTimeSeconds = start.currentTimeSeconds
                )
                if (isStalePlayRequest(requestToken)) return@launch
                val localUri = Uri.fromFile(File(localDownload.localPath)).toString()
                prepareAndPlay(
                    bookId = playbackBook.id,
                    book = playbackBook,
                    streamUrl = localUri,
                    resumeMs = start.resumeMs
                )
                return@launch
            }

            when (val sourceResult = sessionRepository.fetchPlaybackSource(bookId)) {
                is AppResult.Success -> {
                    if (isStalePlayRequest(requestToken)) return@launch
                    sessionRepository.setLastPlayedBookId(sourceResult.value.book.id)
                    val progressResult = sessionRepository.fetchPlaybackProgress(sourceResult.value.book.id)
                    if (isStalePlayRequest(requestToken)) return@launch
                    val start = resolvePlaybackStart(
                        bookId = sourceResult.value.book.id,
                        defaultDurationSeconds = sourceResult.value.book.durationSeconds,
                        startPositionMs = startPositionMs,
                        progressResult = progressResult
                    )
                    val shouldRestartFromBeginning = start.shouldRestartFromBeginning
                    val playbackBook = if (shouldRestartFromBeginning) {
                        sourceResult.value.book.copy(
                            progressPercent = 0.0,
                            currentTimeSeconds = 0.0,
                            isFinished = false
                        )
                    } else {
                        sourceResult.value.book
                    }
                    cachedContinueListeningItem = ContinueListeningItem(
                        book = playbackBook,
                        progressPercent = start.progressPercent,
                        currentTimeSeconds = start.currentTimeSeconds
                    )
                    if (isStalePlayRequest(requestToken)) return@launch
                    prepareAndPlay(
                        playbackBook.id,
                        playbackBook,
                        sourceResult.value.streamUrl,
                        resumeMs = start.resumeMs
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

    private suspend fun resolvePlaybackStart(
        bookId: String,
        defaultDurationSeconds: Double?,
        startPositionMs: Long?,
        progressResult: AppResult<PlaybackProgress?>
    ): ResolvedPlaybackStart {
        if (startPositionMs != null) {
            val explicitSeconds = (startPositionMs / 1000.0).coerceAtLeast(0.0)
            return ResolvedPlaybackStart(
                resumeMs = startPositionMs.coerceAtLeast(0L),
                progressPercent = null,
                currentTimeSeconds = explicitSeconds,
                shouldRestartFromBeginning = false
            )
        }

        val serverProgress = (progressResult as? AppResult.Success)?.value
        val localCheckpoint = sessionPreferences.getPlaybackCheckpoint(
            serverId = observedActiveServerId,
            bookId = bookId
        )
        val resolvedProgress = preferLocalCheckpoint(
            serverProgress = serverProgress,
            localCheckpoint = localCheckpoint
        )
        if (localCheckpoint != null && localCheckpointMatchesResolvedProgress(localCheckpoint, resolvedProgress)) {
            enqueueProgressSyncRequest(
                request = localCheckpoint.toProgressSyncRequest(),
                bypassGate = true
            )
        }
        val shouldRestartFromBeginning = resolvedProgress.shouldRestartFromBeginning(
            defaultDurationSeconds = defaultDurationSeconds
        )
        return if (shouldRestartFromBeginning) {
            ResolvedPlaybackStart(
                resumeMs = 0L,
                progressPercent = 0.0,
                currentTimeSeconds = 0.0,
                shouldRestartFromBeginning = true
            )
        } else {
            ResolvedPlaybackStart(
                resumeMs = ((resolvedProgress?.currentTimeSeconds ?: 0.0) * 1000.0).toLong(),
                progressPercent = resolvedProgress?.progressPercent,
                currentTimeSeconds = resolvedProgress?.currentTimeSeconds,
                shouldRestartFromBeginning = false
            )
        }
    }

    private fun preferLocalCheckpoint(
        serverProgress: PlaybackProgress?,
        localCheckpoint: PlaybackCheckpointSnapshot?
    ): PlaybackProgress? {
        if (localCheckpoint == null) return serverProgress
        val localProgress = localCheckpoint.toPlaybackProgress()
        if (serverProgress == null) return localProgress

        val localUpdatedAtMs = localCheckpoint.savedAtMs.takeIf { it > 0L }
        val serverUpdatedAtMs = serverProgress.updatedAtMs?.takeIf { it > 0L }
        if (localUpdatedAtMs != null && serverUpdatedAtMs != null && localUpdatedAtMs != serverUpdatedAtMs) {
            return if (localUpdatedAtMs > serverUpdatedAtMs) localProgress else serverProgress
        }

        val localSeconds = localProgress.currentTimeSeconds
        val serverSeconds = serverProgress.currentTimeSeconds
        if (localSeconds != null && serverSeconds != null) {
            return when {
                abs(localSeconds - serverSeconds) <= PROGRESS_MATCH_EPSILON_SECONDS -> {
                    if ((localUpdatedAtMs ?: 0L) >= (serverUpdatedAtMs ?: 0L)) localProgress else serverProgress
                }

                localSeconds > serverSeconds -> localProgress
                else -> serverProgress
            }
        }

        return localSeconds?.let { localProgress } ?: serverProgress
    }

    private fun localCheckpointMatchesResolvedProgress(
        localCheckpoint: PlaybackCheckpointSnapshot,
        resolvedProgress: PlaybackProgress?
    ): Boolean {
        if (resolvedProgress == null) return false
        if (localCheckpoint.isFinished) {
            val resolvedPercent = resolvedProgress.progressPercent ?: 0.0
            return resolvedPercent >= 0.995
        }
        val resolvedSeconds = resolvedProgress.currentTimeSeconds ?: return false
        return abs(resolvedSeconds - localCheckpoint.currentTimeSeconds) <= PROGRESS_MATCH_EPSILON_SECONDS
    }

    private fun PlaybackCheckpointSnapshot.toPlaybackProgress(): PlaybackProgress {
        val progressPercent = durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { duration -> (currentTimeSeconds / duration).coerceIn(0.0, 1.0) }
        return PlaybackProgress(
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            updatedAtMs = savedAtMs.takeIf { it > 0L }
        )
    }

    private fun PlaybackCheckpointSnapshot.toProgressSyncRequest(): ProgressSyncRequest {
        val safePositionMs = (currentTimeSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        return ProgressSyncRequest(
            serverId = serverId,
            bookId = bookId,
            positionMs = safePositionMs,
            currentTimeSeconds = currentTimeSeconds.coerceAtLeast(0.0),
            durationSeconds = durationSeconds,
            isFinished = isFinished,
            checkpointSavedAtMs = savedAtMs
        )
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

    fun stopAndResetBookToStart(bookId: String): Boolean {
        if (bookId.isBlank()) return false
        if (currentBookId != bookId) return false
        val player = mediaPlayer ?: return false
        if (uiState.value.isPlaying) {
            pause(reason = PauseReason.Internal)
        } else {
            clearDucking(player)
            runCatching { player.pause() }
            updateUiState { it.copy(isPlaying = false) }
        }
        val state = uiState.value
        val resolvedDurationMs = safeDuration(player)
            .takeIf { it > 0L }
            ?: state.durationMs.takeIf { it > 0L }
            ?: state.book?.durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
            ?: 0L
        seekToPosition(targetMs = resolvedDurationMs, forceSync = false)
        updateUiState { state ->
            val currentBook = state.book
            if (currentBook != null && currentBook.id == bookId) {
                state.copy(
                    book = currentBook.copy(
                        isFinished = true,
                        progressPercent = 1.0,
                        currentTimeSeconds = resolvedDurationMs / 1000.0
                    ),
                    positionMs = resolvedDurationMs,
                    durationMs = maxOf(state.durationMs, resolvedDurationMs),
                    isPlaying = false
                )
            } else {
                state.copy(
                    positionMs = resolvedDurationMs,
                    durationMs = maxOf(state.durationMs, resolvedDurationMs),
                    isPlaying = false
                )
            }
        }
        updateCachedFromUiState()
        persistPlaybackCheckpointIfNeeded(force = true, isFinished = true)
        return true
    }

    fun stopAndResetBookToBeginning(bookId: String): Boolean {
        if (bookId.isBlank()) return false
        if (currentBookId != bookId) return false
        val player = mediaPlayer ?: return false
        if (uiState.value.isPlaying) {
            pause(reason = PauseReason.Internal)
        } else {
            clearDucking(player)
            runCatching { player.pause() }
            updateUiState { it.copy(isPlaying = false) }
        }
        seekToPosition(targetMs = 0L, forceSync = false)
        updateUiState { state ->
            val currentBook = state.book
            if (currentBook != null && currentBook.id == bookId) {
                state.copy(
                    book = currentBook.copy(
                        isFinished = false,
                        progressPercent = 0.0,
                        currentTimeSeconds = 0.0
                    ),
                    positionMs = 0L,
                    isPlaying = false
                )
            } else {
                state.copy(
                    positionMs = 0L,
                    isPlaying = false
                )
            }
        }
        updateCachedFromUiState()
        persistPlaybackCheckpointIfNeeded(force = true, isFinished = false)
        return true
    }

    fun stopAndRestoreBookProgress(
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): Boolean {
        if (bookId.isBlank()) return false
        if (currentBookId != bookId) return false
        val player = mediaPlayer ?: return false
        if (uiState.value.isPlaying) {
            pause(reason = PauseReason.Internal)
        } else {
            clearDucking(player)
            runCatching { player.pause() }
            updateUiState { it.copy(isPlaying = false) }
        }
        val state = uiState.value
        val resolvedDurationMs = safeDuration(player)
            .takeIf { it > 0L }
            ?: state.durationMs.takeIf { it > 0L }
            ?: durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
            ?: state.book?.durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
            ?: 0L
        val rawTargetMs = (currentTimeSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val targetMs = if (resolvedDurationMs > 0L) {
            rawTargetMs.coerceIn(0L, resolvedDurationMs)
        } else {
            rawTargetMs.coerceAtLeast(0L)
        }
        seekToPosition(targetMs = targetMs, forceSync = false)
        val progressPercent = when {
            isFinished -> 1.0
            resolvedDurationMs > 0L -> (targetMs.toDouble() / resolvedDurationMs.toDouble()).coerceIn(0.0, 1.0)
            else -> null
        }
        updateUiState { latest ->
            val currentBook = latest.book
            if (currentBook != null && currentBook.id == bookId) {
                latest.copy(
                    book = currentBook.copy(
                        isFinished = isFinished,
                        progressPercent = progressPercent,
                        currentTimeSeconds = targetMs / 1000.0
                    ),
                    positionMs = targetMs,
                    durationMs = maxOf(latest.durationMs, resolvedDurationMs),
                    isPlaying = false
                )
            } else {
                latest.copy(
                    positionMs = targetMs,
                    durationMs = maxOf(latest.durationMs, resolvedDurationMs),
                    isPlaying = false
                )
            }
        }
        updateCachedFromUiState()
        persistPlaybackCheckpointIfNeeded(force = true, isFinished = isFinished)
        return true
    }

    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.7f, 2.0f)
        currentPlaybackSpeed = clampedSpeed
        val wasPlaying = uiState.value.isPlaying
        mediaPlayer?.let { player ->
            applyPlaybackSpeed(player = player, speed = clampedSpeed)
            // Some devices resume playback when playbackParams are changed while paused.
            // Force paused state to stay paused when the user only adjusts speed.
            if (!wasPlaying) {
                runCatching { if (player.isPlaying) player.pause() }
            }
            updateProgress(player)
        }
        updateUiState {
            it.copy(
                playbackSpeed = clampedSpeed,
                isPlaying = if (wasPlaying) it.isPlaying else false
            )
        }
    }

    fun setSoftToneLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        currentSoftToneLevel = clamped
        mediaPlayer?.let { player ->
            applyAudioEffects(player)
            updateProgress(player)
        }
        updateUiState { it.copy(softToneLevel = clamped) }
        scope.launch(Dispatchers.IO) {
            sessionPreferences.setSoftToneLevel(clamped)
        }
    }

    fun setBoostLevel(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        currentBoostLevel = clamped
        mediaPlayer?.let { player ->
            applyAudioEffects(player)
            updateProgress(player)
        }
        updateUiState { it.copy(boostLevel = clamped) }
        scope.launch(Dispatchers.IO) {
            sessionPreferences.setBoostLevel(clamped)
        }
    }

    fun startSleepTimerMinutes(minutes: Int) {
        if (uiState.value.book == null) return
        val durationMs = (minutes.coerceAtLeast(1) * 60_000L).coerceAtMost(24L * 60L * 60L * 1000L)
        startSleepTimer(durationMs = durationMs, mode = SleepTimerMode.Duration)
    }

    suspend fun startSleepTimerEndOfChapter(): Boolean {
        if (uiState.value.book == null) return false
        val bookId = currentBookId ?: uiState.value.book?.id ?: return false
        val chapterBoundariesMs = resolveChapterBoundariesMs(bookId) ?: return false
        val currentPositionMs = uiState.value.positionMs.coerceAtLeast(0L)
        val nextBoundaryMs = chapterBoundariesMs.firstOrNull { boundaryMs ->
            boundaryMs > (currentPositionMs + 200L)
        } ?: return false
        val remainingMs = (nextBoundaryMs - currentPositionMs).coerceAtLeast(0L)
        if (remainingMs <= 750L) return false
        startSleepTimer(
            durationMs = remainingMs,
            mode = SleepTimerMode.EndOfChapter,
            chapterBoundariesMs = chapterBoundariesMs
        )
        return true
    }

    fun clearSleepTimer() {
        cancelSleepTimer(updateUi = true)
    }

    fun extendSleepTimerOneMinute() {
        if (uiState.value.book == null) return
        startSleepTimer(durationMs = 60_000L, mode = SleepTimerMode.Duration)
        resume()
    }

    fun dismissSleepTimerExpiredPrompt() {
        updateUiState { it.copy(sleepTimerExpiredPromptVisible = false) }
    }

    fun refreshAudioOutputDevices() {
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
    }

    private fun refreshAudioOutputDevices(reason: OutputRefreshReason) {
        val available = queryOutputDevices()
        val availableIds = available.mapNotNull { it.id }.toSet()
        val bluetoothOutputId = available.firstOrNull { output ->
            val displayedId = output.id ?: return@firstOrNull false
            outputRouteKeyByDisplayedId[displayedId]?.startsWith("bt:") == true
        }?.id
        val shouldAutoSwitchToBluetooth = reason == OutputRefreshReason.DeviceAdded &&
            bluetoothOutputId != null &&
            bluetoothOutputId !in lastKnownOutputDeviceIds
        val validPreferredId = preferredOutputDeviceId?.takeIf { preferredId ->
            available.any { it.id == preferredId }
        }
        val resolvedPreferredId = when {
            shouldAutoSwitchToBluetooth -> bluetoothOutputId
            validPreferredId != null -> validPreferredId
            else -> available.firstOrNull()?.id
        }
        if (preferredOutputDeviceId != resolvedPreferredId) {
            preferredOutputDeviceId = resolvedPreferredId
        }
        mediaPlayer?.let { player ->
            applyPreferredOutputDevice(player)
        }
        updateUiState {
            it.copy(
                outputDevices = available,
                selectedOutputDeviceId = preferredOutputDeviceId
            )
        }
        lastKnownOutputDeviceIds = availableIds
    }

    fun selectAudioOutputDevice(deviceId: Int?): Boolean {
        val available = queryOutputDevices()
        if (available.none { output -> output.id == deviceId }) {
            refreshAudioOutputDevices()
            return false
        }
        preferredOutputDeviceId = deviceId
        val player = mediaPlayer
        if (player != null) {
            if (deviceId == null) {
                applySystemDefaultOutputRouting(player)
            } else {
                applySystemDefaultOutputRouting(player)
                val isSpeakerTarget = isSpeakerOutputDevice(displayedDeviceId = deviceId)
                var applied = if (isSpeakerTarget) {
                    applyOutputViaAudioManagerFallback(displayedDeviceId = deviceId)
                } else {
                    applyPreferredOutputForDisplayedId(player = player, displayedDeviceId = deviceId)
                }
                if (!applied) {
                    applied = if (isSpeakerTarget) {
                        applyPreferredOutputForDisplayedId(player = player, displayedDeviceId = deviceId)
                    } else {
                        applyOutputViaAudioManagerFallback(displayedDeviceId = deviceId)
                    }
                }
                if (!applied) {
                    refreshAudioOutputDevices()
                    return false
                }
            }
        }
        refreshAudioOutputDevices()
        return true
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
        pause(reason = PauseReason.User)
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        clearDucking(player)
        wasPausedForTransientAudioFocusLoss = false
        val focusResult = requestAudioFocusForPlayback()
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pendingPlayAfterAudioFocusGain = false
            pendingPlayStartsProgressUpdates = false
            runCatching { player.start() }
            updateUiState { it.copy(isPlaying = true, errorMessage = null) }
            return
        }
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            pendingPlayAfterAudioFocusGain = true
            pendingPlayStartsProgressUpdates = false
            updateUiState { it.copy(isPlaying = false, errorMessage = null) }
            return
        }
        pendingPlayAfterAudioFocusGain = false
        pendingPlayStartsProgressUpdates = false
        updateUiState {
            it.copy(
                isPlaying = false,
                errorMessage = "Could not take audio output right now."
            )
        }
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
        playbackSyncGate.reset()
        lastCheckpointPositionMs = -1L
        lastCheckpointSavedAtElapsedMs = 0L
        lastAppBackgroundSyncAtElapsedMs = 0L
        lastAppBackgroundSyncPositionMs = -1L
        configurePlayerAudioAttributes(player)
        applyPreferredOutputDevice(player)
        updateUiState {
            it.copy(
                isLoading = true,
                book = book,
                isPlaying = false,
                playbackSpeed = currentPlaybackSpeed,
                softToneLevel = currentSoftToneLevel,
                boostLevel = currentBoostLevel,
                sleepTimerExpiredPromptVisible = false,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }
        updateCachedFromUiState()

        player.setOnPreparedListener { prepared ->
            applyPlaybackSpeed(player = prepared, speed = currentPlaybackSpeed)
            applyAudioEffects(prepared)
            val duration = safeDuration(prepared)
            val clampedResume = if (duration > 0L) {
                resumeMs.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
            } else {
                resumeMs.coerceAtLeast(0L)
            }
            if (clampedResume > 0L) {
                runCatching { prepared.seekTo(clampedResume.toInt()) }
            }
            val focusResult = requestAudioFocusForPlayback()
            updateUiState {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    playbackSpeed = currentPlaybackSpeed,
                    positionMs = clampedResume,
                    durationMs = duration
                )
            }
            updateCachedFromUiState()
            persistPlaybackCheckpointIfNeeded(force = true, isFinished = false)
            when (focusResult) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    pendingPlayAfterAudioFocusGain = false
                    pendingPlayStartsProgressUpdates = false
                    runCatching { prepared.start() }
                    updateUiState { it.copy(isPlaying = true, errorMessage = null) }
                    startProgressUpdates()
                }

                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    pendingPlayAfterAudioFocusGain = true
                    pendingPlayStartsProgressUpdates = true
                }

                else -> {
                    pendingPlayAfterAudioFocusGain = false
                    pendingPlayStartsProgressUpdates = false
                    updateUiState {
                        it.copy(errorMessage = "Could not take audio output right now.")
                    }
                }
            }
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
            abandonAudioFocus()
            updateUiState {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = throwable.message ?: "Unable to start playback."
                )
            }
        }
    }

    private fun pause(reason: PauseReason) {
        val player = mediaPlayer
        if (player != null) {
            clearDucking(player)
            runCatching { player.pause() }
            updateProgress(player)
        }
        pendingPlayAfterAudioFocusGain = false
        pendingPlayStartsProgressUpdates = false
        if (reason != PauseReason.AudioFocusTransientLoss) {
            wasPausedForTransientAudioFocusLoss = false
        }
        if (
            reason == PauseReason.User ||
            reason == PauseReason.NoisyOutput ||
            reason == PauseReason.Internal
        ) {
            abandonAudioFocus()
        }
        syncProgress(force = true, isFinished = false)
        updateUiState { it.copy(isPlaying = false) }
    }

    private fun configurePlayerAudioAttributes(player: MediaPlayer) {
        runCatching {
            if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(playbackAudioAttributes)
            } else {
                @Suppress("DEPRECATION")
                player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
        }
    }

    private fun requestAudioFocusForPlayback(): Int {
        if (hasAudioFocus) return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val requestResult = if (SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAudioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        if (requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasAudioFocus = true
        }
        return requestResult
    }

    private fun abandonAudioFocus() {
        hasAudioFocus = false
        val request = audioFocusRequest
        if (SDK_INT >= Build.VERSION_CODES.O && request != null) {
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(audioFocusChangeListener) }
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                clearDucking(mediaPlayer)
                if (pendingPlayAfterAudioFocusGain) {
                    val player = mediaPlayer
                    if (player != null) {
                        pendingPlayAfterAudioFocusGain = false
                        val shouldStartProgress = pendingPlayStartsProgressUpdates
                        pendingPlayStartsProgressUpdates = false
                        wasPausedForTransientAudioFocusLoss = false
                        runCatching { player.start() }
                        updateUiState { it.copy(isPlaying = true, errorMessage = null) }
                        if (shouldStartProgress) {
                            startProgressUpdates()
                        }
                    }
                } else if (wasPausedForTransientAudioFocusLoss) {
                    wasPausedForTransientAudioFocusLoss = false
                    resume()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                val player = mediaPlayer ?: return
                val isPlayingNow = runCatching { player.isPlaying }.getOrDefault(false)
                if (isPlayingNow) {
                    applyDucking(player)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                clearDucking(mediaPlayer)
                val player = mediaPlayer ?: return
                val isPlayingNow = runCatching { player.isPlaying }.getOrDefault(false)
                if (isPlayingNow) {
                    wasPausedForTransientAudioFocusLoss = true
                    pause(reason = PauseReason.AudioFocusTransientLoss)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                clearDucking(mediaPlayer)
                pendingPlayAfterAudioFocusGain = false
                pendingPlayStartsProgressUpdates = false
                wasPausedForTransientAudioFocusLoss = false
                val player = mediaPlayer
                val isPlayingNow = player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false
                if (isPlayingNow) {
                    pause(reason = PauseReason.AudioFocusLoss)
                } else {
                    updateUiState { it.copy(isPlaying = false) }
                }
            }
        }
    }

    private fun applyDucking(player: MediaPlayer) {
        runCatching {
            player.setVolume(0.30f, 0.30f)
            isDuckedForAudioFocus = true
        }
    }

    private fun clearDucking(player: MediaPlayer?) {
        if (!isDuckedForAudioFocus || player == null) return
        runCatching { player.setVolume(1.0f, 1.0f) }
        isDuckedForAudioFocus = false
    }

    private fun registerNoisyAudioReceiver() {
        if (noisyAudioReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        val registered = runCatching {
            if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(noisyAudioReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(noisyAudioReceiver, filter)
            }
            true
        }.getOrDefault(false)
        noisyAudioReceiverRegistered = registered
    }

    private fun pauseForNoisyOutput() {
        val player = mediaPlayer ?: return
        val isPlayingNow = runCatching { player.isPlaying }.getOrDefault(false)
        if (!isPlayingNow) return
        pause(reason = PauseReason.NoisyOutput)
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    updateProgress(it)
                    if (uiState.value.isPlaying) {
                        syncProgress(force = false, isFinished = false)
                    }
                }
                delay(500L)
            }
        }
    }

    private fun PlaybackProgress?.shouldRestartFromBeginning(
        defaultDurationSeconds: Double?
    ): Boolean {
        val progress = this ?: return false
        val progressPercent = progress.progressPercent
        if (progressPercent != null && progressPercent >= 0.995) {
            return true
        }
        val current = progress.currentTimeSeconds ?: return false
        val duration = (progress.durationSeconds ?: defaultDurationSeconds)?.takeIf { it > 0.0 } ?: return false
        return (current / duration) >= 0.995
    }

    private fun updateProgress(player: MediaPlayer) {
        updateUiState {
            it.copy(
                positionMs = safePosition(player),
                durationMs = safeDuration(player)
            )
        }
        updateCachedFromUiState()
        persistPlaybackCheckpointIfNeeded(force = false, isFinished = shouldSyncAsFinished())
    }

    private fun safePosition(player: MediaPlayer): Long {
        return runCatching { player.currentPosition.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun safeDuration(player: MediaPlayer): Long {
        val duration = runCatching { player.duration.toLong() }.getOrDefault(0L)
        return duration.coerceAtLeast(0L)
    }

    private fun releasePlayer() {
        releasePlayer(syncProgressBeforeRelease = true)
    }

    private fun releasePlayer(syncProgressBeforeRelease: Boolean) {
        if (syncProgressBeforeRelease) {
            syncProgress(force = true, isFinished = shouldSyncAsFinished())
        }
        progressJob?.cancel()
        progressJob = null
        pendingPlayAfterAudioFocusGain = false
        pendingPlayStartsProgressUpdates = false
        wasPausedForTransientAudioFocusLoss = false
        releaseAudioEffects()
        clearDucking(mediaPlayer)
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
        abandonAudioFocus()
        updatePlaybackSurface()
    }

    fun saveProgressSnapshot() {
        syncProgress(force = true, isFinished = shouldSyncAsFinished())
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
        val durationSeconds = state.book?.durationSeconds ?: state.durationMs.takeIf { it > 0L }?.div(1000.0)
        val checkpoint = persistPlaybackCheckpointIfNeeded(force = force, isFinished = isFinished)
        if (!playbackSyncGate.shouldSync(currentPositionMs = currentMs, force = force)) return
        enqueueProgressSyncRequest(
            request = ProgressSyncRequest(
                serverId = observedActiveServerId,
                bookId = bookId,
                positionMs = currentMs,
                currentTimeSeconds = currentMs / 1000.0,
                durationSeconds = durationSeconds,
                isFinished = isFinished,
                checkpointSavedAtMs = checkpoint?.savedAtMs ?: 0L
            ),
            bypassGate = true,
            urgentBackstop = force
        )
    }

    private fun syncProgressOnAppBackgroundIfNeeded() {
        val state = uiState.value
        val book = state.book ?: return
        if (currentBookId.isNullOrBlank()) return
        val currentPositionMs = state.positionMs.coerceAtLeast(0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        val positionAdvancedEnough = abs(currentPositionMs - lastAppBackgroundSyncPositionMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS
        val backgroundSyncRecentlyTriggered =
            (elapsedNow - lastAppBackgroundSyncAtElapsedMs) < BACKGROUND_SYNC_MIN_INTERVAL_MS
        if (backgroundSyncRecentlyTriggered && !positionAdvancedEnough) return
        if (!state.isPlaying && currentPositionMs <= 0L && (book.currentTimeSeconds ?: 0.0) <= 0.0) return

        lastAppBackgroundSyncAtElapsedMs = elapsedNow
        lastAppBackgroundSyncPositionMs = currentPositionMs
        syncProgress(force = true, isFinished = shouldSyncAsFinished())
    }

    private fun persistPlaybackCheckpointIfNeeded(force: Boolean, isFinished: Boolean): PlaybackCheckpointSnapshot? {
        val bookId = currentBookId ?: return null
        val state = uiState.value
        val positionMs = state.positionMs.coerceAtLeast(0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        val shouldPersist = force ||
            lastCheckpointPositionMs < 0L ||
            abs(positionMs - lastCheckpointPositionMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS ||
            (elapsedNow - lastCheckpointSavedAtElapsedMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS
        if (!shouldPersist) return null
        val snapshot = PlaybackCheckpointSnapshot(
            serverId = observedActiveServerId,
            bookId = bookId,
            currentTimeSeconds = positionMs / 1000.0,
            durationSeconds = state.book?.durationSeconds ?: state.durationMs.takeIf { it > 0L }?.div(1000.0),
            isFinished = isFinished,
            savedAtMs = System.currentTimeMillis()
        )
        lastCheckpointPositionMs = positionMs
        lastCheckpointSavedAtElapsedMs = elapsedNow
        scope.launch(Dispatchers.IO) {
            sessionPreferences.setPlaybackCheckpoint(snapshot)
        }
        return snapshot
    }

    private fun enqueueProgressSyncRequest(
        request: ProgressSyncRequest,
        bypassGate: Boolean,
        urgentBackstop: Boolean = false
    ) {
        if (
            !bypassGate &&
            !playbackSyncGate.shouldSync(currentPositionMs = request.positionMs, force = request.isFinished)
        ) {
            return
        }
        val requestKey = progressSyncKey(serverId = request.serverId, bookId = request.bookId)
        pendingSyncRequests[requestKey] = mergeProgressSyncRequests(
            existing = pendingSyncRequests[requestKey],
            incoming = request
        )
        PlaybackProgressSyncScheduler.enqueue(appContext, urgent = urgentBackstop)
        if (syncQueueJob?.isActive == true) return
        syncQueueJob = scope.launch {
            while (true) {
                val nextEntry = pendingSyncRequests.entries.firstOrNull() ?: break
                val nextRequest = nextEntry.value
                pendingSyncRequests.remove(nextEntry.key)
                val result = runCatching {
                    sessionRepository.syncPlaybackProgress(
                        bookId = nextRequest.bookId,
                        currentTimeSeconds = nextRequest.currentTimeSeconds,
                        durationSeconds = nextRequest.durationSeconds,
                        isFinished = nextRequest.isFinished
                    )
                }
                val syncSucceeded = result.getOrNull() is AppResult.Success
                if (syncSucceeded) {
                    playbackSyncGate.markSyncFinished(currentPositionMs = nextRequest.positionMs)
                    clearPlaybackCheckpointIfCovered(nextRequest)
                    continue
                }
                val failedRequestKey = progressSyncKey(
                    serverId = nextRequest.serverId,
                    bookId = nextRequest.bookId
                )
                pendingSyncRequests[failedRequestKey] = mergeProgressSyncRequests(
                    existing = pendingSyncRequests[failedRequestKey],
                    incoming = nextRequest
                )
                delay(PROGRESS_SYNC_RETRY_DELAY_MS)
            }
            syncQueueJob = null
        }
    }

    private fun mergeProgressSyncRequests(
        existing: ProgressSyncRequest?,
        incoming: ProgressSyncRequest
    ): ProgressSyncRequest {
        if (existing == null) return incoming
        return incoming.copy(isFinished = existing.isFinished || incoming.isFinished)
    }

    private fun progressSyncKey(serverId: String?, bookId: String): String {
        val normalizedServerId = serverId?.trim().orEmpty()
        return "$normalizedServerId::$bookId"
    }

    private fun clearPlaybackCheckpointIfCovered(request: ProgressSyncRequest) {
        if (request.checkpointSavedAtMs <= 0L) return
        scope.launch(Dispatchers.IO) {
            val checkpoint = sessionPreferences.getPlaybackCheckpoint(
                serverId = request.serverId,
                bookId = request.bookId
            ) ?: return@launch
            if (checkpoint.savedAtMs != request.checkpointSavedAtMs) return@launch
            sessionPreferences.clearPlaybackCheckpoint(
                serverId = request.serverId,
                bookId = request.bookId
            )
            if (sessionPreferences.getPlaybackCheckpoints().isEmpty()) {
                PlaybackProgressSyncScheduler.cancel(appContext)
            }
        }
    }

    private fun shouldSyncAsFinished(): Boolean {
        val state = uiState.value
        if (state.book?.isFinished == true) {
            return true
        }
        val durationMs = state.durationMs.takeIf { it > 0L }
            ?: state.book?.durationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
            ?: return false
        if (durationMs <= 0L) return false
        return state.positionMs >= (durationMs * 0.995).toLong()
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
                lockScreenControlMode = normalizeLockScreenControlMode(pref.lockScreenControlMode)
                val desiredSoftTone = pref.softToneLevel.coerceIn(0f, 1f)
                val desiredBoost = pref.boostLevel.coerceIn(0f, 1f)
                val toneChanged = abs(currentSoftToneLevel - desiredSoftTone) > 0.001f
                val boostChanged = abs(currentBoostLevel - desiredBoost) > 0.001f
                currentSoftToneLevel = desiredSoftTone
                currentBoostLevel = desiredBoost

                if (toneChanged || boostChanged) {
                    mediaPlayer?.let { player ->
                        applyAudioEffects(player)
                        updateProgress(player)
                    }
                }
                updateUiState {
                    it.copy(
                        softToneLevel = desiredSoftTone,
                        boostLevel = desiredBoost
                    )
                }
            }
        }
    }

    private fun observeActiveServerSelection() {
        scope.launch {
            sessionPreferences.state.collect { pref ->
                val nextServerId = pref.activeServerId
                if (!hasObservedActiveServerId) {
                    observedActiveServerId = nextServerId
                    hasObservedActiveServerId = true
                    return@collect
                }
                val previousServerId = observedActiveServerId
                observedActiveServerId = nextServerId
                if (previousServerId != nextServerId) {
                    clearPlaybackForServerSwitch()
                }
            }
        }
    }

    private fun clearPlaybackForServerSwitch() {
        playRequestJob?.cancel()
        playRequestToken += 1L
        syncQueueJob?.cancel()
        syncQueueJob = null
        pendingSyncRequests.clear()
        releasePlayer(syncProgressBeforeRelease = false)
        currentBookId = null
        cachedContinueListeningItem = null
        attemptedAutoAdvanceTargetsMs.clear()
        previousRestartState = null
        playbackSyncGate.reset()
        lastCheckpointPositionMs = -1L
        lastCheckpointSavedAtElapsedMs = 0L
        lastAppBackgroundSyncAtElapsedMs = 0L
        lastAppBackgroundSyncPositionMs = -1L
        suppressNextAutoAdvanceOnCompletion = false
        updateUiState { state ->
            state.copy(
                isLoading = false,
                book = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null,
                sleepTimerMode = SleepTimerMode.Off,
                sleepTimerRemainingMs = null,
                sleepTimerTotalMs = null,
                sleepTimerExpiredPromptVisible = false
            )
        }
    }

    private fun performRewindControl() {
        seekBy(deltaMs = -(rewindSeconds * 1000L))
    }

    private fun performForwardControl() {
        seekBy(deltaMs = (forwardSeconds * 1000L))
    }

    private fun performLockScreenPreviousControl() {
        if (lockScreenControlMode != LOCK_SCREEN_MODE_NEXT) {
            previousRestartState = null
            performRewindControl()
            return
        }
        scope.launch {
            navigateLockScreenPrevious()
        }
    }

    private fun performLockScreenNextControl() {
        if (lockScreenControlMode != LOCK_SCREEN_MODE_NEXT) {
            previousRestartState = null
            performForwardControl()
            return
        }
        previousRestartState = null
        scope.launch {
            navigateLockScreenNext()
        }
    }

    private suspend fun navigateLockScreenNext() {
        val bookId = currentBookId ?: uiState.value.book?.id ?: return
        val chapterStartsMs = resolveChapterStartsMs(bookId)
        if (chapterStartsMs.isNotEmpty()) {
            val currentPositionMs = uiState.value.positionMs.coerceAtLeast(0L)
            val currentChapterIndex = resolveCurrentChapterIndex(chapterStartsMs, currentPositionMs)
            val nextChapterStartMs = chapterStartsMs.getOrNull(currentChapterIndex + 1)
            if (nextChapterStartMs != null) {
                seekToPosition(targetMs = nextChapterStartMs, forceSync = true)
                return
            }
        }
        playAdjacentBook(direction = 1)
    }

    private suspend fun navigateLockScreenPrevious() {
        val bookId = currentBookId ?: uiState.value.book?.id ?: return
        val currentPositionMs = uiState.value.positionMs.coerceAtLeast(0L)
        val chapterStartsMs = resolveChapterStartsMs(bookId)
        if (chapterStartsMs.isNotEmpty()) {
            val currentChapterIndex = resolveCurrentChapterIndex(chapterStartsMs, currentPositionMs)
            val currentChapterStartMs = chapterStartsMs
                .getOrNull(currentChapterIndex)
                ?.coerceAtLeast(0L)
                ?: 0L
            if (!shouldGoToPreviousAfterRestart(bookId, currentChapterStartMs, chapterMode = true, currentPositionMs)) {
                rememberRestart(bookId, currentChapterStartMs, chapterMode = true)
                seekToPosition(targetMs = currentChapterStartMs, forceSync = true)
                return
            }
            previousRestartState = null
            val previousChapterStartMs = chapterStartsMs.getOrNull(currentChapterIndex - 1)
            if (previousChapterStartMs != null) {
                seekToPosition(targetMs = previousChapterStartMs, forceSync = true)
                return
            }
            playAdjacentBook(direction = -1)
            return
        }

        if (!shouldGoToPreviousAfterRestart(bookId, 0L, chapterMode = false, currentPositionMs)) {
            rememberRestart(bookId, restartStartMs = 0L, chapterMode = false)
            seekToPosition(targetMs = 0L, forceSync = true)
            return
        }
        previousRestartState = null
        playAdjacentBook(direction = -1)
    }

    private suspend fun playAdjacentBook(direction: Int) {
        if (direction != -1 && direction != 1) return
        val currentId = currentBookId ?: uiState.value.book?.id ?: return
        val books = fetchBooksForLockScreenNavigation()
        if (books.isEmpty()) return
        val currentIndex = books.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return
        val targetBook = books.getOrNull(currentIndex + direction) ?: return
        previousRestartState = null
        playBookFromPosition(bookId = targetBook.id, startPositionMs = 0L)
    }

    private suspend fun fetchBooksForLockScreenNavigation(): List<BookSummary> {
        val collected = mutableListOf<BookSummary>()
        var page = 0
        while (page < LOCK_SCREEN_BOOK_NAV_MAX_PAGES) {
            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = LOCK_SCREEN_BOOK_NAV_PAGE_SIZE,
                    page = page
                )
            ) {
                is AppResult.Success -> {
                    val batch = result.value
                    if (batch.isEmpty()) break
                    collected += batch
                    if (batch.size < LOCK_SCREEN_BOOK_NAV_PAGE_SIZE) break
                }

                is AppResult.Error -> return emptyList()
            }
            page += 1
        }
        return collected.distinctBy { it.id }
    }

    private suspend fun resolveChapterStartsMs(bookId: String): List<Long> {
        return when (val detail = sessionRepository.fetchBookDetail(bookId = bookId, forceRefresh = false)) {
            is AppResult.Success -> detail.value.chapters
                .toChapterStartsMs()
            is AppResult.Error -> emptyList()
        }
    }

    private fun resolveCurrentChapterIndex(chapterStartsMs: List<Long>, positionMs: Long): Int {
        if (chapterStartsMs.isEmpty()) return 0
        val seekPositionMs = positionMs.coerceAtLeast(0L) + 200L
        return chapterStartsMs.indexOfLast { startMs -> startMs <= seekPositionMs }
            .takeIf { index -> index >= 0 }
            ?: 0
    }

    private fun normalizeLockScreenControlMode(rawMode: String?): String {
        return if (rawMode.equals(LOCK_SCREEN_MODE_NEXT, ignoreCase = true)) {
            LOCK_SCREEN_MODE_NEXT
        } else {
            LOCK_SCREEN_MODE_SKIP
        }
    }

    private fun shouldGoToPreviousAfterRestart(
        bookId: String,
        restartStartMs: Long,
        chapterMode: Boolean,
        currentPositionMs: Long
    ): Boolean {
        val state = previousRestartState ?: return false
        if (state.bookId != bookId) return false
        if (state.restartStartMs != restartStartMs) return false
        if (state.chapterMode != chapterMode) return false
        val elapsedSinceTriggerMs = SystemClock.elapsedRealtime() - state.triggeredAtElapsedMs
        if (elapsedSinceTriggerMs > LOCK_SCREEN_PREVIOUS_DOUBLE_PRESS_WINDOW_MS) return false
        return currentPositionMs <= (restartStartMs + LOCK_SCREEN_SECOND_PREVIOUS_POSITION_WINDOW_MS)
    }

    private fun rememberRestart(bookId: String, restartStartMs: Long, chapterMode: Boolean) {
        previousRestartState = PreviousRestartState(
            bookId = bookId,
            restartStartMs = restartStartMs.coerceAtLeast(0L),
            chapterMode = chapterMode,
            triggeredAtElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    private fun List<BookChapter>.toChapterStartsMs(): List<Long> {
        if (isEmpty()) return emptyList()
        return asSequence()
            .map { chapter -> (chapter.startSeconds * 1000.0).toLong().coerceAtLeast(0L) }
            .distinct()
            .sorted()
            .toList()
    }

    private inline fun updateUiState(transform: (PlaybackUiState) -> PlaybackUiState) {
        mutableUiState.update(transform)
        updatePlaybackSurface()
    }

    private fun startSleepTimer(
        durationMs: Long,
        mode: SleepTimerMode,
        chapterBoundariesMs: List<Long> = emptyList()
    ) {
        cancelSleepTimer(updateUi = false)
        val totalDurationMs = durationMs.coerceAtLeast(1_000L)
        var durationRemainingMs = totalDurationMs
        var lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        sleepTimerChapterBoundariesMs = if (mode == SleepTimerMode.EndOfChapter) {
            chapterBoundariesMs.sorted()
        } else {
            emptyList()
        }
        sleepTimerTargetBoundaryMs = if (mode == SleepTimerMode.EndOfChapter) {
            nextChapterBoundaryForPosition(uiState.value.positionMs)
        } else {
            null
        }
        suppressNextAutoAdvanceOnCompletion = false
        val wasPlaying = uiState.value.isPlaying

        val initialRemainingMs = if (mode == SleepTimerMode.EndOfChapter) {
            remainingToTargetChapterBoundaryMs(uiState.value.positionMs)
        } else {
            durationRemainingMs
        }
        updateUiState {
            it.copy(
                sleepTimerMode = mode,
                sleepTimerRemainingMs = initialRemainingMs,
                sleepTimerTotalMs = totalDurationMs,
                sleepTimerExpiredPromptVisible = false,
                isPlaying = if (wasPlaying) it.isPlaying else false
            )
        }
        if (!wasPlaying) {
            mediaPlayer?.let { player ->
                runCatching { if (player.isPlaying) player.pause() }
                updateProgress(player)
            }
        }

        sleepTimerTickerJob = scope.launch {
            while (isActive) {
                val nowElapsedRealtime = SystemClock.elapsedRealtime()
                val state = uiState.value
                val remainingMs = if (mode == SleepTimerMode.EndOfChapter) {
                    remainingToTargetChapterBoundaryMs(state.positionMs)
                } else {
                    if (state.isPlaying) {
                        val elapsedSinceLastTick = (nowElapsedRealtime - lastTickElapsedRealtime).coerceAtLeast(0L)
                        durationRemainingMs = (durationRemainingMs - elapsedSinceLastTick).coerceAtLeast(0L)
                    }
                    durationRemainingMs
                }
                lastTickElapsedRealtime = nowElapsedRealtime
                mutableUiState.update {
                    it.copy(
                        sleepTimerMode = mode,
                        sleepTimerRemainingMs = remainingMs,
                        sleepTimerTotalMs = totalDurationMs,
                        sleepTimerExpiredPromptVisible = false
                    )
                }
                if (remainingMs <= 0L) break
                delay(if (mode == SleepTimerMode.EndOfChapter) 250L else 500L)
            }
            expireSleepTimer()
        }
    }

    private fun cancelSleepTimer(updateUi: Boolean) {
        sleepTimerTickerJob?.cancel()
        sleepTimerTickerJob = null
        sleepTimerChapterBoundariesMs = emptyList()
        sleepTimerTargetBoundaryMs = null
        if (updateUi) {
            updateUiState {
                it.copy(
                    sleepTimerMode = SleepTimerMode.Off,
                    sleepTimerRemainingMs = null,
                    sleepTimerTotalMs = null,
                    sleepTimerExpiredPromptVisible = false
                )
            }
        }
    }

    private fun expireSleepTimer(positionOverrideMs: Long? = null) {
        if (uiState.value.sleepTimerMode == SleepTimerMode.EndOfChapter) {
            suppressNextAutoAdvanceOnCompletion = true
        }
        pause()
        cancelSleepTimer(updateUi = false)
        updateUiState { current ->
            current.copy(
                sleepTimerMode = SleepTimerMode.Off,
                sleepTimerRemainingMs = null,
                sleepTimerTotalMs = null,
                sleepTimerExpiredPromptVisible = true,
                isPlaying = false,
                positionMs = positionOverrideMs?.coerceAtLeast(current.positionMs) ?: current.positionMs
            )
        }
    }

    private suspend fun resolveChapterBoundariesMs(bookId: String): List<Long>? {
        return when (val detail = sessionRepository.fetchBookDetail(bookId = bookId, forceRefresh = false)) {
            is AppResult.Success -> {
                val chapters = detail.value.chapters.sortedBy { it.startSeconds }
                if (chapters.isEmpty()) {
                    null
                } else {
                    val fallbackDurationMs = uiState.value.durationMs
                        .takeIf { it > 0L }
                        ?: uiState.value.book?.durationSeconds?.times(1000.0)?.toLong()
                        ?: 0L
                    chapters.mapIndexedNotNull { index, chapter ->
                        val chapterEndSeconds = chapter.endSeconds
                            ?: chapters.getOrNull(index + 1)?.startSeconds
                            ?: (fallbackDurationMs / 1000.0).takeIf { it > 0.0 }
                        chapterEndSeconds
                            ?.let { endSeconds -> (endSeconds * 1000.0).toLong().coerceAtLeast(0L) }
                    }.distinct().sorted()
                }
            }

            is AppResult.Error -> null
        }
    }

    private fun remainingToNextChapterBoundaryMs(positionMs: Long): Long {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val nextBoundaryMs = sleepTimerChapterBoundariesMs.firstOrNull { boundaryMs ->
            boundaryMs > (safePositionMs + 200L)
        }
        return if (nextBoundaryMs == null) 0L else (nextBoundaryMs - safePositionMs).coerceAtLeast(0L)
    }

    private fun nextChapterBoundaryForPosition(positionMs: Long): Long? {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        return sleepTimerChapterBoundariesMs.firstOrNull { boundaryMs ->
            boundaryMs > (safePositionMs + 200L)
        }
    }

    private fun remainingToTargetChapterBoundaryMs(positionMs: Long): Long {
        val targetBoundaryMs = sleepTimerTargetBoundaryMs ?: return remainingToNextChapterBoundaryMs(positionMs)
        val safePositionMs = positionMs.coerceAtLeast(0L)
        if (safePositionMs >= targetBoundaryMs - 200L) {
            return 0L
        }
        return (targetBoundaryMs - safePositionMs).coerceAtLeast(0L)
    }

    private fun queryOutputDevices(): List<PlaybackOutputDevice> {
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val groupedCandidates = rawDevices
            .asSequence()
            .filter { device -> isMainOutputType(device.type) }
            .mapNotNull { device ->
                val routeKey = outputRouteKey(device) ?: return@mapNotNull null
                val routePriority = outputRoutePriority(device.type)
                OutputRouteCandidate(
                    routeKey = routeKey,
                    priority = routePriority,
                    device = device
                )
            }
            .groupBy { it.routeKey }
            .mapValues { (_, candidates) ->
                candidates.sortedByDescending { candidate -> candidate.priority }
            }
            .values
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .map { it.first() }
            .sortedWith(
                compareByDescending<OutputRouteCandidate> { it.priority }
                    .thenBy { resolveOutputDeviceName(it.device).lowercase() }
            )
        outputRouteDeviceIdsByRouteKey = groupedCandidates.associate { candidate ->
            val routeKey = candidate.routeKey
            val candidateIds = rawDevices
                .asSequence()
                .filter { device -> outputRouteKey(device) == routeKey }
                .sortedByDescending { device -> outputRoutePriority(device.type) }
                .map { device -> device.id }
                .distinct()
                .toList()
            routeKey to candidateIds
        }
        outputRouteKeyByDisplayedId = groupedCandidates.associate { candidate ->
            candidate.device.id to candidate.routeKey
        }
        return groupedCandidates.map { candidate ->
            PlaybackOutputDevice(
                id = candidate.device.id,
                name = resolveOutputDeviceName(candidate.device),
                typeLabel = outputTypeLabel(candidate.device.type)
            )
        }
    }

    private fun resolveOutputDeviceName(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
            else -> {
                val productName = device.productName?.toString()?.trim().orEmpty()
                productName.ifBlank { outputTypeLabel(device.type) }
            }
        }
    }

    private fun isMainOutputType(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
            else -> false
        }
    }

    private fun outputRouteKey(device: AudioDeviceInfo): String? {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> "line"
            AudioDeviceInfo.TYPE_DOCK -> "dock"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val name = resolveBluetoothRouteName(device) ?: return null
                "bt:${name.lowercase()}"
            }
            else -> null
        }
    }

    private fun resolveBluetoothRouteName(device: AudioDeviceInfo): String? {
        val productName = device.productName?.toString()?.trim().orEmpty()
        if (productName.isBlank()) return null
        val model = Build.MODEL.orEmpty().trim()
        val deviceName = Build.DEVICE.orEmpty().trim()
        if (productName.equals(model, ignoreCase = true) || productName.equals(deviceName, ignoreCase = true)) {
            return null
        }
        if (SDK_INT >= Build.VERSION_CODES.P) {
            val address = device.address.orEmpty()
            if (address.isBlank() || address == "00:00:00:00:00:00") {
                return null
            }
        }
        return productName
    }

    private fun outputRoutePriority(type: Int): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 100
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> 95
            AudioDeviceInfo.TYPE_BLE_HEADSET -> 92
            AudioDeviceInfo.TYPE_HEARING_AID -> 90
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> 88
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 85
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> 84
            AudioDeviceInfo.TYPE_USB_HEADSET -> 80
            AudioDeviceInfo.TYPE_USB_DEVICE -> 79
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> 78
            AudioDeviceInfo.TYPE_HDMI_EARC -> 75
            AudioDeviceInfo.TYPE_HDMI_ARC -> 74
            AudioDeviceInfo.TYPE_HDMI -> 73
            AudioDeviceInfo.TYPE_DOCK -> 72
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> 71
            AudioDeviceInfo.TYPE_LINE_ANALOG -> 70
            AudioDeviceInfo.TYPE_AUX_LINE -> 69
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 20
            else -> 0
        }
    }

    private fun outputTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth"
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> "Line out"
            AudioDeviceInfo.TYPE_DOCK -> "Dock"
            else -> "Output"
        }
    }

    private data class OutputRouteCandidate(
        val routeKey: String,
        val priority: Int,
        val device: AudioDeviceInfo
    )

    private fun applyPlaybackSpeed(player: MediaPlayer, speed: Float) {
        runCatching {
            val params = player.playbackParams ?: android.media.PlaybackParams()
            player.playbackParams = params
                .setSpeed(speed)
                .setPitch(1.0f)
        }
    }

    private fun applyAudioEffects(player: MediaPlayer) {
        ensureAudioEffects(player)
        applyBoostEffect()
        applySoftToneEffect()
    }

    private fun ensureAudioEffects(player: MediaPlayer) {
        val sessionId = runCatching { player.audioSessionId }.getOrDefault(0)
        if (sessionId <= 0) return
        if (audioEffectsSessionId == sessionId) return

        releaseAudioEffects()
        audioEffectsSessionId = sessionId

        loudnessEnhancer = runCatching {
            LoudnessEnhancer(sessionId).apply { enabled = true }
        }.getOrNull()
        equalizer = runCatching {
            Equalizer(0, sessionId).apply { enabled = true }
        }.getOrNull()
    }

    private fun applyBoostEffect() {
        val enhancer = loudnessEnhancer ?: return
        val targetGainMb = (currentBoostLevel * 1800f).toInt().coerceIn(0, 2000)
        runCatching {
            enhancer.setTargetGain(targetGainMb)
            enhancer.enabled = targetGainMb > 0
        }
    }

    private fun applySoftToneEffect() {
        val toneEq = equalizer ?: return
        runCatching {
            val bandCount = toneEq.numberOfBands.toInt()
            val levelRange = toneEq.bandLevelRange
            val minLevel = levelRange.getOrNull(0)?.toInt() ?: -1500
            val maxLevel = levelRange.getOrNull(1)?.toInt() ?: 1500

            for (band in 0 until bandCount) {
                val ratio = if (bandCount <= 1) 0f else band.toFloat() / (bandCount - 1).toFloat()
                val attenuationWeight = ((ratio - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val attenuationMb = (currentSoftToneLevel * attenuationWeight * 900f).toInt()
                val targetLevel = (0 - attenuationMb).coerceIn(minLevel, maxLevel)
                toneEq.setBandLevel(band.toShort(), targetLevel.toShort())
            }
            toneEq.enabled = currentSoftToneLevel > 0f
        }
    }

    private fun releaseAudioEffects() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { equalizer?.release() }
        loudnessEnhancer = null
        equalizer = null
        audioEffectsSessionId = null
    }

    private fun applyPreferredOutputDevice(player: MediaPlayer) {
        val preferredId = preferredOutputDeviceId
        if (preferredId == null) {
            applySystemDefaultOutputRouting(player)
            return
        }
        val preferredApplied = applyPreferredOutputForDisplayedId(player = player, displayedDeviceId = preferredId)
        if (!preferredApplied) {
            applyOutputViaAudioManagerFallback(displayedDeviceId = preferredId)
        }
    }

    private fun isSpeakerOutputDevice(displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        return candidates.firstOrNull()?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    private fun applySystemDefaultOutputRouting(player: MediaPlayer): Boolean {
        val preferredCleared = clearPreferredOutputDevice(player)
        val communicationCleared = if (SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                audioManager.clearCommunicationDevice()
                true
            }.getOrDefault(false)
        } else {
            true
        }
        val speakerReset = runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            true
        }.getOrDefault(false)
        return preferredCleared || communicationCleared || speakerReset
    }

    private fun resolveOutputCandidatesForDisplayedId(displayedDeviceId: Int): List<AudioDeviceInfo> {
        val currentOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val routeKey = outputRouteKeyByDisplayedId[displayedDeviceId]
        val candidateIds = routeKey
            ?.let { key -> outputRouteDeviceIdsByRouteKey[key] }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(displayedDeviceId)
        return candidateIds
            .mapNotNull { candidateId -> currentOutputs.firstOrNull { output -> output.id == candidateId } }
            .ifEmpty {
                routeKey
                    ?.let { key ->
                        currentOutputs.filter { output -> outputRouteKey(output) == key }
                            .sortedByDescending { output -> outputRoutePriority(output.type) }
                    }
                    .orEmpty()
            }
    }

    private fun applyPreferredOutputForDisplayedId(player: MediaPlayer, displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        return candidates.any { targetDevice ->
            setPreferredOutputDevice(player, targetDevice)
        }
    }

    private fun clearPreferredOutputDevice(player: MediaPlayer): Boolean {
        if (SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching { player.setPreferredDevice(null) }.getOrDefault(false)
    }

    private fun setPreferredOutputDevice(player: MediaPlayer, targetDevice: AudioDeviceInfo): Boolean {
        if (SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching { player.setPreferredDevice(targetDevice) }.getOrDefault(false)
    }

    private fun applyOutputViaAudioManagerFallback(displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        if (candidates.isEmpty()) return false
        val primaryType = candidates.first().type
        val speakerRoute = primaryType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        if (speakerRoute) {
            return runCatching {
                if (SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                true
            }.getOrDefault(false)
        }
        if (SDK_INT >= Build.VERSION_CODES.S) {
            val communicationApplied = candidates.any { candidate ->
                runCatching { audioManager.setCommunicationDevice(candidate) }.getOrDefault(false)
            }
            if (communicationApplied) return true
        }
        runCatching {
            if (SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
        return false
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
                if (state.isPlaying) state.playbackSpeed else 0f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
        mediaSession.isActive = state.book != null

        val book = state.book
        if (book == null) {
            cancelSleepTimer(updateUi = false)
            suppressNextAutoAdvanceOnCompletion = false
            if (
                state.sleepTimerMode != SleepTimerMode.Off ||
                state.sleepTimerRemainingMs != null ||
                state.sleepTimerTotalMs != null ||
                state.sleepTimerExpiredPromptVisible
            ) {
                mutableUiState.update {
                    it.copy(
                        sleepTimerMode = SleepTimerMode.Off,
                        sleepTimerRemainingMs = null,
                        sleepTimerTotalMs = null,
                        sleepTimerExpiredPromptVisible = false
                    )
                }
            }
            artworkJob?.cancel()
            artworkJob = null
            artworkBookId = null
            artworkBitmap = null
            lastNotificationSignature = null
            PlaybackServiceController.stop(appContext)
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
        val notificationSignature = NotificationSignature(
            bookId = book.id,
            title = book.title,
            author = book.authorName,
            isPlaying = state.isPlaying,
            hasArtwork = artworkBitmap != null
        )
        if (notificationSignature == lastNotificationSignature) {
            return
        }

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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
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
            PlaybackServiceController.startOrUpdate(appContext, notification)
        }.onFailure {
            // Avoid playback crashes if OEM notification policy rejects a publish attempt.
        }.onSuccess {
            lastNotificationSignature = notificationSignature
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
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (
            existing != null &&
            (
                existing.importance > NotificationManager.IMPORTANCE_LOW ||
                    existing.shouldVibrate() ||
                    existing.sound != null
                )
        ) {
            manager.deleteNotificationChannel(CHANNEL_ID)
        } else if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
        }
        manager.createNotificationChannel(channel)
    }

    private fun handleCompletion(book: BookSummary?, durationMs: Long) {
        if (suppressNextAutoAdvanceOnCompletion) {
            suppressNextAutoAdvanceOnCompletion = false
            return
        }
        if (
            uiState.value.sleepTimerMode == SleepTimerMode.EndOfChapter ||
            sleepTimerTargetBoundaryMs != null
        ) {
            expireSleepTimer(positionOverrideMs = durationMs)
            return
        }
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
        cancelSleepTimer(updateUi = false)
        suppressNextAutoAdvanceOnCompletion = false
        clearDucking(mediaPlayer)
        pendingPlayAfterAudioFocusGain = false
        pendingPlayStartsProgressUpdates = false
        wasPausedForTransientAudioFocusLoss = false
        abandonAudioFocus()
        updateUiState {
            it.copy(
                isPlaying = false,
                sleepTimerMode = SleepTimerMode.Off,
                sleepTimerRemainingMs = null,
                sleepTimerTotalMs = null,
                sleepTimerExpiredPromptVisible = false,
                positionMs = durationMs,
                durationMs = durationMs
            )
        }
        updateCachedFromUiState()
        syncProgress(force = true, isFinished = true)
    }
}
