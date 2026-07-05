package com.stillshelf.app.playback.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.net.ConnectivityManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.stillshelf.app.core.diagnostics.DiagnosticLogManager
import com.stillshelf.app.core.datastore.PlaybackCheckpointSnapshot
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.toLocalPlaybackSource
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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

// Keep paused playback reachable while the player still exists so resume is stable across route changes.
internal fun shouldKeepPlaybackSessionActive(
    book: BookSummary?,
    hasActivePlayer: Boolean
): Boolean = book != null && hasActivePlayer

internal fun shouldScheduleAbsPausedPlayerRelease(
    book: BookSummary?,
    hasActivePlayer: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int,
    appInForeground: Boolean
): Boolean {
    return book != null &&
        hasActivePlayer &&
        !appInForeground &&
        !isPlaying &&
        !playWhenReady &&
        playbackState != Player.STATE_BUFFERING
}

internal enum class ResumeProgressUpdateMode {
    Immediate,
    OnAudioFocusGain,
    Never
}

internal fun resolveResumeProgressUpdateMode(audioFocusResult: Int): ResumeProgressUpdateMode {
    return when (audioFocusResult) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> ResumeProgressUpdateMode.Immediate
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> ResumeProgressUpdateMode.OnAudioFocusGain
        else -> ResumeProgressUpdateMode.Never
    }
}

@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager,
    private val diagnosticLogManager: DiagnosticLogManager
) {
    companion object {
        private const val CHANNEL_ID = "stillshelf_playback_v4"
        private const val CHANNEL_NAME = "Playback"
        private const val NOTIFICATION_ID = 1101
        private const val PAUSED_PLAYER_RELEASE_DELAY_MS = 10 * 60 * 1000L
        private const val ACTIVE_PLAYBACK_SYNC_INTERVAL_MS = 15_000L
        private const val LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS = 2_000L
        private const val PROGRESS_SYNC_RETRY_DELAY_MS = 3_000L
        private const val BACKGROUND_SYNC_MIN_INTERVAL_MS = 2_000L
        private const val LOCK_SCREEN_BOOK_NAV_PAGE_SIZE = 200
        private const val LOCK_SCREEN_BOOK_NAV_MAX_PAGES = 20
        private const val OUTPUT_SWITCH_RESTORE_DELAY_MS = 220L
        private const val SPEAKER_OUTPUT_SWITCH_RESTORE_DELAY_MS = 450L
        private const val SPEAKER_OUTPUT_VOLUME_RAMP_STEPS = 5
        private const val SPEAKER_OUTPUT_VOLUME_RAMP_STEP_DELAY_MS = 90L
        private const val OUTPUT_SWITCH_REFRESH_GRACE_MS = 500L
        private const val PLAYBACK_READINESS_TIMEOUT_MS = 15_000L
        private const val TAG = "PlaybackController"
        const val ACTION_PLAY_PAUSE = "com.stillshelf.app.playback.action.PLAY_PAUSE"
        const val ACTION_REWIND = "com.stillshelf.app.playback.action.REWIND"
        const val ACTION_FORWARD = "com.stillshelf.app.playback.action.FORWARD"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUiState = MutableStateFlow(PlaybackUiState())
    private var mediaPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var syncQueueJob: Job? = null
    private var pausedReleaseJob: Job? = null
    private var playbackReadinessTimeoutJob: Job? = null
    private var playerInitialReadyHandled = false
    private val oomFallbackStreamingBookIds = mutableSetOf<String>()
    private var currentBookId: String? = null
    private var currentPlaybackSource: PlaybackSource? = null
    private var currentTrackStartOffsetMs: Long = 0L
    private var currentBookDurationMs: Long = 0L
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
    private var outputRecoveryJob: Job? = null
    private var suppressRefreshRoutingUntilElapsedMs: Long = 0L
    private val attemptedAutoAdvanceTargetsMs = mutableSetOf<Long>()
    private var suppressNextAutoAdvanceOnCompletion = false
    private var lastNotificationSignature: NotificationSignature? = null
    private var observedActiveLibraryId: String? = null
    private var hasObservedActiveLibraryId: Boolean = false

    private val mediaSession = MediaSessionCompat(appContext, "StillShelfPlayback")
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playbackAudioAttributes: Media3AudioAttributes by lazy {
        Media3AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
    }
    private val audioFocusAudioAttributes: PlatformAudioAttributes by lazy {
        PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_SPEECH)
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
            logPlaybackTrace(
                "audio_route_change=device_added count=${addedDevices?.size ?: 0}"
            )
            refreshAudioOutputDevices(reason = OutputRefreshReason.DeviceAdded)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            logPlaybackTrace(
                "audio_route_change=device_removed count=${removedDevices?.size ?: 0}"
            )
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
    private var pendingAutoAdvanceUiBookId: String? = null
    private var pendingAutoAdvanceUiPositionMs: Long? = null
    private var appInForeground: Boolean = false

    private data class NotificationSignature(
        val bookId: String,
        val title: String,
        val author: String?,
        val isPlaying: Boolean,
        val hasArtwork: Boolean
    )

    private data class ProgressSyncRequest(
        val serverId: String?,
        val bookId: String,
        val positionMs: Long,
        val currentTimeSeconds: Double,
        val durationSeconds: Double?,
        val isFinished: Boolean,
        val checkpointSavedAtMs: Long,
        val allowBackgroundRetry: Boolean
    )

    private data class ResolvedPlaybackStart(
        val resumeMs: Long,
        val progressPercent: Double?,
        val currentTimeSeconds: Double?,
        val shouldRestartFromBeginning: Boolean
    )

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appInForeground = true
            lastAppBackgroundSyncAtElapsedMs = 0L
            lastAppBackgroundSyncPositionMs = -1L
            syncPendingPlaybackCheckpointsOnForeground()
            ensurePausedPlayerReleasePolicy()
        }

        override fun onStop(owner: LifecycleOwner) {
            appInForeground = false
            scope.launch(Dispatchers.Main.immediate) {
                syncProgressOnAppBackgroundIfNeeded()
                ensurePausedPlayerReleasePolicy()
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
    val hasActivePlayer: Boolean get() = mediaPlayer != null

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
        mediaSession.isActive = false
        registerNoisyAudioReceiver()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        appInForeground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
        observePlaybackPreferences()
        observeActiveSessionSelection()
    }

    fun playBook(bookId: String, startPositionMs: Long? = null, forceStreaming: Boolean = false) {
        if (bookId.isBlank()) return
        val effectiveForceStreaming = forceStreaming || bookId in oomFallbackStreamingBookIds
        logPlaybackTrace(
            "playback_command=play_book start_position_ms=${startPositionMs?.coerceAtLeast(0L)} " +
                "has_current_book=${currentBookId != null} has_player=${mediaPlayer != null} force_streaming=$effectiveForceStreaming"
        )
        if (currentBookId != bookId) {
            attemptedAutoAdvanceTargetsMs.clear()
            clearPendingAutoAdvanceUiTarget()
        }
        val requestToken = beginPlayRequest()
        if (currentBookId == bookId && mediaPlayer != null) {
            if (startPositionMs != null) {
                val targetMs = startPositionMs.coerceAtLeast(0L)
                val targetTrackStartOffsetMs = resolveTrackStartOffsetForPosition(
                    tracks = currentPlaybackSource?.tracks.orEmpty(),
                    positionMs = targetMs
                )
                if (targetTrackStartOffsetMs == null || targetTrackStartOffsetMs == currentTrackStartOffsetMs) {
                    seekToPosition(targetMs)
                    resume()
                    return
                }
            } else {
                resume()
                return
            }
        }

        updateUiState {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sleepTimerExpiredPromptVisible = false
            )
        }
        playRequestJob = scope.launch {
            val isPodcastEpisode = bookId.splitPodcastId() != null
            val localDownload = if (effectiveForceStreaming) null else {
                if (isPodcastEpisode) {
                    bookDownloadManager.getCompletedDownloadForPodcast(bookId)
                } else {
                    bookDownloadManager.getCompletedDownload(bookId)
                }
            }
            if (localDownload != null) {
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
                localBook.id.splitPodcastId()?.let { (showId, episodeId) ->
                    sessionPreferences.setPodcastLastPlayedEpisode(showId, episodeId)
                }
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
                val playbackSource = localDownload.toLocalPlaybackSource(playbackBook)
                if (playbackSource == null) {
                    updateUiState {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            errorMessage = "Downloaded audio files are unavailable."
                        )
                    }
                    return@launch
                }
                prepareAndPlay(
                    bookId = playbackBook.id,
                    book = playbackBook,
                    playbackSource = playbackSource,
                    resumeMs = start.resumeMs
                )
                return@launch
            }

            if (isPodcastEpisode) {
                updateUiState {
                    it.copy(
                        isLoading = false,
                        errorMessage = "This episode is not downloaded. Open it from the podcast list to play."
                    )
                }
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
                        bookId = playbackBook.id,
                        book = playbackBook,
                        playbackSource = sourceResult.value.copy(book = playbackBook),
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

    fun playFromSource(
        source: PlaybackSource,
        startPositionMs: Long? = null
    ) {
        val requestToken = beginPlayRequest()
        updateUiState {
            it.copy(isLoading = true, errorMessage = null, sleepTimerExpiredPromptVisible = false)
        }
        playRequestJob = scope.launch {
            val resumeMs = startPositionMs?.coerceAtLeast(0L) ?: run {
                val progressResult = sessionRepository.fetchPlaybackProgress(source.book.id)
                val start = resolvePlaybackStart(
                    bookId = source.book.id,
                    defaultDurationSeconds = source.book.durationSeconds,
                    startPositionMs = null,
                    progressResult = progressResult
                )
                start.resumeMs
            }
            if (isStalePlayRequest(requestToken)) return@launch
            currentCoroutineContext().ensureActive()
            cachedContinueListeningItem = ContinueListeningItem(
                book = source.book,
                progressPercent = source.book.progressPercent,
                currentTimeSeconds = source.book.currentTimeSeconds
            )
            sessionRepository.setLastPlayedBookId(source.book.id)
            currentCoroutineContext().ensureActive()
            source.book.id.splitPodcastId()?.let { (showId, episodeId) ->
                sessionPreferences.setPodcastLastPlayedEpisode(showId, episodeId)
            }
            prepareAndPlay(
                bookId = source.book.id,
                book = source.book,
                playbackSource = source,
                resumeMs = resumeMs
            )
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
        val preferredProgress = resolvePreferredPlaybackProgress(
            serverProgress = serverProgress,
            localCheckpoint = localCheckpoint
        )
        val resolvedProgress = preferredProgress.progress
        if (
            localCheckpoint != null &&
            shouldReplayLocalCheckpointAtStartup(
                selectedSourceIsLocal = preferredProgress.source == PlaybackProgressSource.Local,
                localCheckpointMatchesResolvedProgress = localCheckpointMatchesResolvedProgress(
                    localCheckpoint,
                    resolvedProgress
                )
            )
        ) {
            enqueueProgressSyncRequest(
                request = localCheckpoint.toProgressSyncRequest(),
                bypassGate = true,
                allowBackgroundRetry = false
            )
        }
        val shouldRestartFromBeginning = shouldRestartFromBeginning(
            progress = resolvedProgress,
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

    private fun PlaybackCheckpointSnapshot.toProgressSyncRequest(): ProgressSyncRequest {
        val safePositionMs = (currentTimeSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        return ProgressSyncRequest(
            serverId = serverId,
            bookId = bookId,
            positionMs = safePositionMs,
            currentTimeSeconds = currentTimeSeconds.coerceAtLeast(0.0),
            durationSeconds = durationSeconds,
            isFinished = isFinished,
            checkpointSavedAtMs = savedAtMs,
            allowBackgroundRetry = false
        )
    }

    fun playBookFromPosition(bookId: String, startPositionMs: Long) {
        logPlaybackTrace("playback_command=play_book_from_position start_position_ms=${startPositionMs.coerceAtLeast(0L)}")
        playBook(
            bookId = bookId,
            startPositionMs = startPositionMs.coerceAtLeast(0L)
        )
    }

    fun togglePlayPause() {
        logPlaybackTrace("playback_command=toggle_play_pause is_playing=${uiState.value.isPlaying}")
        if (uiState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun playCurrent() {
        logPlaybackTrace("playback_command=play_current")
        resume()
    }

    fun pauseCurrent() {
        logPlaybackTrace("playback_command=pause_current")
        pause()
    }

    fun stop() {
        logPlaybackTrace("playback_command=stop had_book=${uiState.value.book != null}")
        val hadBook = uiState.value.book != null
        if (hadBook) {
            updateCachedFromUiState()
        }
        playRequestJob?.cancel()
        playRequestToken += 1L
        syncQueueJob?.cancel()
        syncQueueJob = null
        pendingSyncRequests.clear()
        releasePlayer(syncProgressBeforeRelease = true)
        currentBookId = null
        currentPlaybackSource = null
        currentTrackStartOffsetMs = 0L
        currentBookDurationMs = 0L
        attemptedAutoAdvanceTargetsMs.clear()
        previousRestartState = null
        playbackSyncGate.reset()
        lastCheckpointPositionMs = -1L
        lastCheckpointSavedAtElapsedMs = 0L
        lastAppBackgroundSyncAtElapsedMs = 0L
        lastAppBackgroundSyncPositionMs = -1L
        suppressNextAutoAdvanceOnCompletion = false
        cancelSleepTimer(updateUi = false)
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

    fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val duration = resolveDisplayedDurationMs(player)
        val current = safePosition(player)
        val target = if (duration > 0L) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        logPlaybackTrace(
            "playback_command=seek_by delta_ms=$deltaMs target_ms=$target duration_ms=$duration"
        )
        seekToPosition(targetMs = target, forceSync = true)
    }

    fun seekToProgress(progressFraction: Float, commit: Boolean) {
        val player = mediaPlayer ?: return
        val duration = resolveDisplayedDurationMs(player)
        if (duration <= 0L) return
        val clamped = progressFraction.coerceIn(0f, 1f)
        val targetMs = (duration.toDouble() * clamped.toDouble()).toLong()
        logPlaybackTrace(
            "playback_command=seek_to_progress fraction=$clamped target_ms=$targetMs duration_ms=$duration commit=$commit"
        )
        seekToPosition(targetMs = targetMs, forceSync = commit)
    }

    fun seekToPositionMs(positionMs: Long, commit: Boolean) {
        logPlaybackTrace(
            "playback_command=seek_to_position_ms target_ms=${positionMs.coerceAtLeast(0L)} commit=$commit"
        )
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
        val resolvedDurationMs = resolveDisplayedDurationMs(player)
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
        val restoredState = resolveRestoredPlaybackProgressState(
            currentTimeSeconds = currentTimeSeconds,
            displayedDurationMs = resolveDisplayedDurationMs(player).takeIf { it > 0L },
            uiDurationMs = state.durationMs.takeIf { it > 0L },
            requestedDurationSeconds = durationSeconds,
            bookDurationSeconds = state.book?.durationSeconds,
            isFinished = isFinished
        )
        val resolvedDurationMs = restoredState.resolvedDurationMs
        val targetMs = restoredState.targetMs
        seekToPosition(targetMs = targetMs, forceSync = false)
        val progressPercent = restoredState.progressPercent
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
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
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

    fun cyclePlaybackSpeed(
        steps: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.2f, 1.3f, 1.5f, 2.0f)
    ): Float {
        val nextSpeed = resolveCycledPlaybackSpeed(
            currentSpeed = uiState.value.playbackSpeed,
            steps = steps
        )
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun increasePlaybackSpeed(
        steps: List<Float> = listOf(0.5f, 1.0f, 1.2f, 1.5f, 2.0f)
    ): Float {
        val nextSpeed = resolveIncreasedPlaybackSpeed(
            currentSpeed = uiState.value.playbackSpeed,
            steps = steps
        )
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun decreasePlaybackSpeed(
        steps: List<Float> = listOf(0.5f, 1.0f, 1.2f, 1.5f, 2.0f)
    ): Float {
        val nextSpeed = resolveDecreasedPlaybackSpeed(
            currentSpeed = uiState.value.playbackSpeed,
            steps = steps
        )
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
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
        val nextBoundaryMs = resolveNextChapterBoundaryMs(
            boundariesMs = chapterBoundariesMs,
            positionMs = currentPositionMs
        ) ?: return false
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

    suspend fun cycleCarSleepTimer(): String {
        val state = uiState.value
        val currentMode = state.sleepTimerMode
        val remainingMinutes = (((state.sleepTimerRemainingMs ?: 0L) + 59_999L) / 60_000L).toInt()
        return when {
            currentMode == SleepTimerMode.Off -> {
                startSleepTimerMinutes(15)
                "Sleep timer 15m"
            }
            currentMode == SleepTimerMode.Duration && remainingMinutes <= 15 -> {
                startSleepTimerMinutes(30)
                "Sleep timer 30m"
            }
            currentMode == SleepTimerMode.Duration -> {
                if (startSleepTimerEndOfChapter()) {
                    "Sleep timer end of chapter"
                } else {
                    clearSleepTimer()
                    "Sleep timer off"
                }
            }
            currentMode == SleepTimerMode.EndOfChapter -> {
                clearSleepTimer()
                "Sleep timer off"
            }
            else -> {
                clearSleepTimer()
                "Sleep timer off"
            }
        }
    }

    fun addBookmarkAtCurrentPosition(title: String? = null) {
        val bookId = currentBookId ?: uiState.value.book?.id ?: return
        val timeSeconds = uiState.value.positionMs.coerceAtLeast(0L) / 1000.0
        scope.launch {
            when (
                val result = sessionRepository.createBookmark(
                    bookId = bookId,
                    timeSeconds = timeSeconds,
                    title = normalizeBookmarkTitle(title)
                )
            ) {
                is AppResult.Success -> updateUiState { it.copy(errorMessage = null) }
                is AppResult.Error -> updateUiState { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun jumpToNextChapter() {
        scope.launch {
            val bookId = currentBookId ?: uiState.value.book?.id ?: return@launch
            val chapterStartsMs = resolveChapterStartsMs(bookId)
            if (chapterStartsMs.isEmpty()) return@launch
            val currentPositionMs = uiState.value.positionMs.coerceAtLeast(0L)
            val currentChapterIndex = resolveCurrentChapterIndex(chapterStartsMs, currentPositionMs)
            val nextChapterStartMs = chapterStartsMs.getOrNull(currentChapterIndex + 1) ?: return@launch
            seekToPosition(targetMs = nextChapterStartMs, forceSync = true)
        }
    }

    fun jumpToPreviousChapter() {
        scope.launch {
            val bookId = currentBookId ?: uiState.value.book?.id ?: return@launch
            val chapterStartsMs = resolveChapterStartsMs(bookId)
            if (chapterStartsMs.isEmpty()) return@launch
            val currentPositionMs = uiState.value.positionMs.coerceAtLeast(0L)
            val currentChapterIndex = resolveCurrentChapterIndex(chapterStartsMs, currentPositionMs)
            val currentChapterStartMs = chapterStartsMs.getOrNull(currentChapterIndex)?.coerceAtLeast(0L) ?: 0L
            if (currentPositionMs > currentChapterStartMs + 1_000L) {
                seekToPosition(targetMs = currentChapterStartMs, forceSync = true)
                return@launch
            }
            val previousChapterStartMs = chapterStartsMs.getOrNull(currentChapterIndex - 1) ?: return@launch
            seekToPosition(targetMs = previousChapterStartMs, forceSync = true)
        }
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
        logPlaybackTrace(
            "audio_output_refresh reason=${describeOutputRefreshReason(reason)} available_count=${available.size} " +
                "has_explicit_selection=${preferredOutputDeviceId != null}"
        )
        val availableIds = available.mapNotNull { it.id }.toSet()
        val bluetoothOutputId = available.firstOrNull { output ->
            val displayedId = output.id ?: return@firstOrNull false
            outputRouteKeyByDisplayedId[displayedId]?.startsWith("bt:") == true
        }?.id
        val shouldAutoSwitchToBluetooth = reason == OutputRefreshReason.DeviceAdded &&
            bluetoothOutputId != null &&
            bluetoothOutputId !in lastKnownOutputDeviceIds
        val resolvedPreferredId = when {
            shouldAutoSwitchToBluetooth -> bluetoothOutputId
            preferredOutputDeviceId?.let { preferredId ->
                available.any { it.id == preferredId }
            } == true -> preferredOutputDeviceId
            else -> available.firstOrNull()?.id
        }
        if (preferredOutputDeviceId != resolvedPreferredId) {
            preferredOutputDeviceId = resolvedPreferredId
        }
        val shouldSkipRoutingApply = SystemClock.elapsedRealtime() < suppressRefreshRoutingUntilElapsedMs
        mediaPlayer?.let { player ->
            if (!shouldSkipRoutingApply) {
                applyPreferredOutputDevice(player)
            }
        }
        updateUiState {
            it.copy(
                outputDevices = available,
                selectedOutputDeviceId = preferredOutputDeviceId
            )
        }
        lastKnownOutputDeviceIds = availableIds
        logPlaybackTrace(
            "audio_output_refresh_applied reason=${describeOutputRefreshReason(reason)} selected_device=${preferredOutputDeviceId != null} " +
                "available_count=${available.size}"
        )
    }

    fun selectAudioOutputDevice(deviceId: Int?): Boolean {
        val available = queryOutputDevices()
        logPlaybackTrace(
            "audio_output_select requested=${deviceId != null} available_count=${available.size}"
        )
        if (available.none { output -> output.id == deviceId }) {
            refreshAudioOutputDevices()
            return false
        }
        val previousPreferredId = preferredOutputDeviceId
        preferredOutputDeviceId = deviceId
        val activePlayer = mediaPlayer
        if (activePlayer == null) {
            refreshAudioOutputDevices()
            return true
        }
        if (deviceId == null) {
            val applied = performMutedOutputSwitch(
                player = activePlayer,
                block = { applySystemDefaultOutputRouting(activePlayer) },
                toSpeakerRoute = false
            )
            if (!applied) {
                preferredOutputDeviceId = previousPreferredId
                updateOutputSelectionWithoutRouting(available)
                return false
            }
            refreshAudioOutputDevices()
            return true
        }
        val speakerTarget = isSpeakerOutputDevice(deviceId)
        val applied = performMutedOutputSwitch(
            player = activePlayer,
            block = { applyTargetedOutputRouting(activePlayer, deviceId) },
            toSpeakerRoute = speakerTarget
        )
        if (!applied) {
            preferredOutputDeviceId = previousPreferredId
            updateOutputSelectionWithoutRouting(available)
            return false
        }
        refreshAudioOutputDevices()
        return true
    }

    private fun seekToPosition(targetMs: Long, forceSync: Boolean = true) {
        val player = mediaPlayer ?: return
        val safeTargetMs = targetMs.coerceAtLeast(0L)
        logPlaybackTrace(
            "playback_command=seek_to_position target_ms=$safeTargetMs force_sync=$forceSync"
        )
        val targetTrackStartOffsetMs = resolveTrackStartOffsetForPosition(
            tracks = currentPlaybackSource?.tracks.orEmpty(),
            positionMs = safeTargetMs
        )
        if (
            currentBookId != null &&
            targetTrackStartOffsetMs != null &&
            targetTrackStartOffsetMs != currentTrackStartOffsetMs
        ) {
            playBookFromPosition(bookId = currentBookId.orEmpty(), startPositionMs = safeTargetMs)
            return
        }
        val duration = safeDuration(player)
        val localTargetMs = (safeTargetMs - currentTrackStartOffsetMs).coerceAtLeast(0L)
        val clampedLocalMs = if (duration > 0L) {
            localTargetMs.coerceIn(0L, duration)
        } else {
            localTargetMs
        }
            runCatching { player.seekTo(clampedLocalMs) }
        updateUiState {
            it.copy(
                positionMs = currentTrackStartOffsetMs + clampedLocalMs,
                durationMs = resolveDisplayedDurationMs(player)
            )
        }
        if (forceSync) {
            syncProgress(
                force = true,
                isFinished = false,
                allowBackgroundRetry = uiState.value.isPlaying
            )
        }
    }

    private fun pause() {
        logPlaybackTrace("playback_command=pause_request")
        pause(reason = PauseReason.User)
    }

    private fun resume() {
        logPlaybackTrace(
            "playback_command=resume_request has_player=${mediaPlayer != null} has_book=${uiState.value.book != null} is_loading=${uiState.value.isLoading}"
        )
        val player = mediaPlayer
        if (player == null) {
            val book = uiState.value.book ?: return
            playBook(book.id, uiState.value.positionMs)
            return
        }
        // If the player is in an error state, calling play() does nothing useful and hides the error.
        // Recreate the player from the current position instead.
        if (runCatching { player.playerError }.getOrNull() != null) {
            logPlaybackTrace("playback_command=resume_after_error recreating_player")
            val book = uiState.value.book ?: return
            releasePlayer(syncProgressBeforeRelease = false)
            playBook(book.id, uiState.value.positionMs)
            return
        }
        clearDucking(player)
        wasPausedForTransientAudioFocusLoss = false
        val focusResult = requestAudioFocusForPlayback()
        val progressUpdateMode = resolveResumeProgressUpdateMode(focusResult)
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pendingPlayAfterAudioFocusGain = false
            pendingPlayStartsProgressUpdates = false
            runCatching { player.play() }
            if (!playerInitialReadyHandled && runCatching { player.playbackState }.getOrDefault(Player.STATE_IDLE) == Player.STATE_BUFFERING) {
                launchPlaybackReadinessTimeout(player)
            }
            updateUiState { it.copy(isPlaying = true, errorMessage = null) }
            if (progressUpdateMode == ResumeProgressUpdateMode.Immediate) {
                startProgressUpdates()
            }
            return
        }
        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            pendingPlayAfterAudioFocusGain = true
            pendingPlayStartsProgressUpdates =
                progressUpdateMode == ResumeProgressUpdateMode.OnAudioFocusGain
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

    private fun prepareAndPlay(bookId: String, book: BookSummary, playbackSource: PlaybackSource, resumeMs: Long) {
        releasePlayer()
        if (artworkBookId != book.id) {
            artworkBitmap = null
            artworkBookId = book.id
        }

        val trackSelection = resolvePlaybackTrackSelection(playbackSource, resumeMs)
        val player = createAbsPlayer(trackSelection.streamUrl)
        mediaPlayer = player
        currentBookId = bookId
        currentPlaybackSource = playbackSource
        currentTrackStartOffsetMs = trackSelection.trackStartOffsetMs
        currentBookDurationMs = trackSelection.bookDurationMs
        playbackSyncGate.reset()
        lastCheckpointPositionMs = -1L
        lastCheckpointSavedAtElapsedMs = 0L
        lastAppBackgroundSyncAtElapsedMs = 0L
        lastAppBackgroundSyncPositionMs = -1L
        updateUiState {
            it.copy(
                isLoading = true,
                book = book,
                isPlaying = false,
                playbackSpeed = currentPlaybackSpeed,
                softToneLevel = currentSoftToneLevel,
                boostLevel = currentBoostLevel,
                sleepTimerExpiredPromptVisible = false,
                positionMs = resumeMs.coerceAtLeast(0L),
                durationMs = currentBookDurationMs,
                errorMessage = null
            )
        }
        updateCachedFromUiState()

        playerInitialReadyHandled = false
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== mediaPlayer) return
                logPlaybackTrace(
                    "playback_state_changed state=${describePlaybackState(playbackState)} " +
                        "is_playing=${runCatching { player.isPlaying }.getOrDefault(false)} " +
                        "play_when_ready=${runCatching { player.playWhenReady }.getOrDefault(false)} " +
                        "position_ms=${safePosition(player)} duration_ms=${safeDuration(player)}"
                )
                when (playbackState) {
                    Player.STATE_READY -> {
                        playbackReadinessTimeoutJob?.cancel()
                        playbackReadinessTimeoutJob = null
                        if (playerInitialReadyHandled) return
                        playerInitialReadyHandled = true
                        applyPlaybackSpeed(player = player, speed = currentPlaybackSpeed)
                        applyAudioEffects(player)
                        val duration = safeDuration(player)
                        currentBookDurationMs = maxOf(currentBookDurationMs, currentTrackStartOffsetMs + duration)
                        updateUiState {
                            it.copy(
                                isLoading = false,
                                isPlaying = false,
                                playbackSpeed = currentPlaybackSpeed,
                                positionMs = safePosition(player),
                                durationMs = resolveDisplayedDurationMs(player)
                            )
                        }
                        updateCachedFromUiState()
                        persistPlaybackCheckpointIfNeeded(force = true, isFinished = false)
                        val focusResult = requestAudioFocusForPlayback()
                        when (focusResult) {
                            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                                pendingPlayAfterAudioFocusGain = false
                                pendingPlayStartsProgressUpdates = false
                                runCatching { player.play() }
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

                    Player.STATE_ENDED -> {
                        val duration = currentTrackStartOffsetMs + safeDuration(player)
                        handleCompletion(book = book, durationMs = duration)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player !== mediaPlayer) return
                logPlaybackTrace(
                    "playback_flag_changed is_playing=$isPlaying state=${describePlaybackState(player.playbackState)} " +
                        "position_ms=${safePosition(player)}"
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (player !== mediaPlayer) return
                playbackReadinessTimeoutJob?.cancel()
                playbackReadinessTimeoutJob = null
                diagnosticLogManager.logPlaybackError(
                    tag = TAG,
                    errorType = error::class.java.simpleName,
                    throwable = error
                )
                val isOom = generateSequence(error.cause) { it.cause }.any { it is OutOfMemoryError }
                if (isOom && bookId !in oomFallbackStreamingBookIds) {
                    if (isNetworkAvailable()) {
                        logPlaybackTrace("playback_oom_fallback=streaming book_id=$bookId")
                        oomFallbackStreamingBookIds.add(bookId)
                        val positionMs = uiState.value.positionMs
                        releasePlayer(syncProgressBeforeRelease = false)
                        playBook(bookId, positionMs, forceStreaming = true)
                        return
                    } else {
                        updateUiState {
                            it.copy(
                                isLoading = false,
                                isPlaying = false,
                                errorMessage = "This file needs more memory than your device has available. Connect to the internet to stream it instead."
                            )
                        }
                        return
                    }
                }
                updateUiState {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        errorMessage = error.message ?: "Playback failed. Try another book."
                    )
                }
            }
        })

        runCatching {
            applyPlaybackSpeed(player = player, speed = currentPlaybackSpeed)
            val sourceTarget = resolveAbsPlaybackSourceTarget(trackSelection.streamUrl)
            player.setMediaItem(
                MediaItem.fromUri(sourceTarget.playbackUrl),
                trackSelection.localSeekMs.coerceAtLeast(0L)
            )
            player.prepare()
            launchPlaybackReadinessTimeout(player)
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

    private fun describePlaybackState(playbackState: Int): String {
        return when (playbackState) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> "ended"
            else -> "state_$playbackState"
        }
    }

    private fun describePauseReason(reason: PauseReason): String {
        return when (reason) {
            PauseReason.User -> "user"
            PauseReason.AudioFocusTransientLoss -> "audio_focus_transient_loss"
            PauseReason.AudioFocusLoss -> "audio_focus_loss"
            PauseReason.NoisyOutput -> "noisy_output"
            PauseReason.Internal -> "internal"
        }
    }

    private fun describeAudioFocusChange(focusChange: Int): String {
        return when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> "gain"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "loss_transient_can_duck"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "loss_transient"
            AudioManager.AUDIOFOCUS_LOSS -> "loss"
            else -> "change_$focusChange"
        }
    }

    private fun describeOutputRefreshReason(reason: OutputRefreshReason): String {
        return when (reason) {
            OutputRefreshReason.General -> "general"
            OutputRefreshReason.DeviceAdded -> "device_added"
            OutputRefreshReason.DeviceRemoved -> "device_removed"
        }
    }

    private fun logPlaybackTrace(message: String) {
        diagnosticLogManager.logDiagnosticEvent(TAG, message)
    }

    private fun pause(reason: PauseReason) {
        logPlaybackTrace(
            "playback_command=pause reason=${describePauseReason(reason)} has_player=${mediaPlayer != null} is_playing=${uiState.value.isPlaying}"
        )
        val player = mediaPlayer
        if (player != null) {
            clearDucking(player)
            playbackReadinessTimeoutJob?.cancel()
            playbackReadinessTimeoutJob = null
            runCatching { player.pause() }
            updateProgress(player)
        }
        progressJob?.cancel()
        progressJob = null
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
        syncProgress(
            force = true,
            isFinished = false,
            allowBackgroundRetry = false
        )
        updateUiState { it.copy(isPlaying = false) }
    }

    private fun configurePlayerAudioAttributes(player: ExoPlayer) {
        runCatching {
            player.setAudioAttributes(playbackAudioAttributes, false)
        }
    }

    private fun createAbsPlayer(streamUrl: String): ExoPlayer {
        val sourceTarget = resolveAbsPlaybackSourceTarget(streamUrl)
        val headers = sourceTarget.headers
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val dataSourceFactory = if (headers.isNotEmpty()) {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
            DefaultDataSource.Factory(appContext, httpFactory)
        } else {
            DefaultDataSource.Factory(appContext)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,
                45_000,
                5_000,
                15_000
            )
            .build()
        return ExoPlayer.Builder(appContext, AudioOnlyRenderersFactory(appContext))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                configurePlayerAudioAttributes(this)
                applyPreferredOutputDevice(this)
            }
    }

    private fun requestAudioFocusForPlayback(): Int {
        if (hasAudioFocus) return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        val requestResult = if (SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioFocusAudioAttributes)
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
        logPlaybackTrace(
            "audio_focus_request result=${when (requestResult) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "failed"
                else -> "result_$requestResult"
            }} has_focus=$hasAudioFocus"
        )
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
        logPlaybackTrace("audio_focus_abandoned")
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        logPlaybackTrace(
            "audio_focus_change=${describeAudioFocusChange(focusChange)} pending_play=$pendingPlayAfterAudioFocusGain " +
                "is_ducked=$isDuckedForAudioFocus"
        )
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
                        runCatching { player.play() }
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

    private fun applyDucking(player: ExoPlayer) {
        runCatching {
            player.volume = 0.30f
            isDuckedForAudioFocus = true
        }
    }

    private fun clearDucking(player: ExoPlayer?) {
        if (!isDuckedForAudioFocus || player == null) return
        runCatching { player.volume = 1.0f }
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
        logPlaybackTrace("audio_route_change=noisy_output_pause")
        pause(reason = PauseReason.NoisyOutput)
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    updateProgress(it)
                    if (uiState.value.isPlaying) {
                        syncProgress(
                            force = false,
                            isFinished = false,
                            allowBackgroundRetry = true
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun updateProgress(player: ExoPlayer) {
        if (player !== mediaPlayer) return
        val rawPositionMs = safePosition(player)
        val guardedPositionMs = applyPendingAutoAdvanceUiGuard(rawPositionMs)
        updateUiState {
            it.copy(
                positionMs = guardedPositionMs,
                durationMs = resolveDisplayedDurationMs(player)
            )
        }
        updateCachedFromUiState()
        persistPlaybackCheckpointIfNeeded(force = false, isFinished = shouldSyncAsFinished())
    }

    private fun setPendingAutoAdvanceUiTarget(bookId: String, targetPositionMs: Long) {
        val safeTargetMs = targetPositionMs.coerceAtLeast(0L)
        pendingAutoAdvanceUiBookId = bookId
        pendingAutoAdvanceUiPositionMs = safeTargetMs
        updateUiState { state ->
            if (state.book?.id != bookId) {
                state
            } else {
                state.copy(
                    positionMs = maxOf(state.positionMs, safeTargetMs),
                    durationMs = maxOf(state.durationMs, safeTargetMs)
                )
            }
        }
    }

    private fun clearPendingAutoAdvanceUiTarget() {
        pendingAutoAdvanceUiBookId = null
        pendingAutoAdvanceUiPositionMs = null
    }

    private fun applyPendingAutoAdvanceUiGuard(rawPositionMs: Long): Long {
        val targetBookId = pendingAutoAdvanceUiBookId
        val targetPositionMs = pendingAutoAdvanceUiPositionMs
        if (
            targetBookId.isNullOrBlank() ||
            targetPositionMs == null ||
            currentBookId != targetBookId
        ) {
            return rawPositionMs
        }
        if (rawPositionMs >= targetPositionMs) {
            clearPendingAutoAdvanceUiTarget()
            return rawPositionMs
        }
        return targetPositionMs
    }

    private fun safePosition(player: ExoPlayer): Long {
        return currentTrackStartOffsetMs +
            runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun safeDuration(player: ExoPlayer): Long {
        val duration = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
        if (duration == C.TIME_UNSET) return 0L
        return duration.coerceAtLeast(0L)
    }

    private fun resolveDisplayedDurationMs(player: ExoPlayer): Long {
        return maxOf(
            currentBookDurationMs,
            currentTrackStartOffsetMs + safeDuration(player)
        )
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.activeNetwork != null
    }

    private fun launchPlaybackReadinessTimeout(player: ExoPlayer) {
        playbackReadinessTimeoutJob?.cancel()
        playbackReadinessTimeoutJob = scope.launch {
            delay(PLAYBACK_READINESS_TIMEOUT_MS)
            if (player !== mediaPlayer) return@launch
            if (playerInitialReadyHandled) return@launch
            logPlaybackTrace("playback_readiness_timeout book_id=$currentBookId")
            releasePlayer(syncProgressBeforeRelease = false)
            updateUiState {
                it.copy(isLoading = false, isPlaying = false, errorMessage = "Playback timed out. Please try again.")
            }
        }
    }

    private fun releasePlayer() {
        releasePlayer(syncProgressBeforeRelease = true)
    }

    private fun releasePlayer(syncProgressBeforeRelease: Boolean) {
        cancelPausedPlayerRelease()
        playbackReadinessTimeoutJob?.cancel()
        playbackReadinessTimeoutJob = null
        playerInitialReadyHandled = false
        if (syncProgressBeforeRelease) {
            syncProgress(
                force = true,
                isFinished = shouldSyncAsFinished(),
                allowBackgroundRetry = false
            )
        }
        progressJob?.cancel()
        progressJob = null
        pendingPlayAfterAudioFocusGain = false
        pendingPlayStartsProgressUpdates = false
        wasPausedForTransientAudioFocusLoss = false
        cancelOutputRecovery()
        releaseAudioEffects()
        clearDucking(mediaPlayer)
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
        currentPlaybackSource = null
        currentTrackStartOffsetMs = 0L
        currentBookDurationMs = 0L
        abandonAudioFocus()
        updatePlaybackSurface()
    }

    fun saveProgressSnapshot() {
        syncProgress(
            force = true,
            isFinished = shouldSyncAsFinished(),
            allowBackgroundRetry = false
        )
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

    private fun syncProgress(
        force: Boolean,
        isFinished: Boolean,
        allowBackgroundRetry: Boolean
    ) {
        val bookId = currentBookId ?: return
        val state = uiState.value
        val currentMs = state.positionMs.coerceAtLeast(0L)
        val durationSeconds = state.resolvedProgressDurationSeconds()
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
                checkpointSavedAtMs = checkpoint?.savedAtMs ?: 0L,
                allowBackgroundRetry = allowBackgroundRetry
            ),
            bypassGate = true,
            urgentBackstop = force,
            allowBackgroundRetry = allowBackgroundRetry
        )
    }

    private fun syncProgressOnAppBackgroundIfNeeded() {
        val state = uiState.value
        val book = state.book ?: return
        if (currentBookId.isNullOrBlank()) return
        val currentPositionMs = state.positionMs.coerceAtLeast(0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        if (
            !shouldSyncProgressOnBackground(
                isPlaying = state.isPlaying,
                currentPositionMs = currentPositionMs,
                bookCurrentTimeSeconds = book.currentTimeSeconds,
                elapsedNowMs = elapsedNow,
                lastBackgroundSyncAtElapsedMs = lastAppBackgroundSyncAtElapsedMs,
                lastBackgroundSyncPositionMs = lastAppBackgroundSyncPositionMs
            )
        ) {
            return
        }

        lastAppBackgroundSyncAtElapsedMs = elapsedNow
        lastAppBackgroundSyncPositionMs = currentPositionMs
        syncProgress(
            force = true,
            isFinished = shouldSyncAsFinished(),
            allowBackgroundRetry = state.isPlaying
        )
    }

    private fun persistPlaybackCheckpointIfNeeded(force: Boolean, isFinished: Boolean): PlaybackCheckpointSnapshot? {
        val bookId = currentBookId ?: return null
        val state = uiState.value
        val positionMs = state.positionMs.coerceAtLeast(0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        val shouldPersist = shouldPersistPlaybackCheckpoint(
            force = force,
            positionMs = positionMs,
            lastCheckpointPositionMs = lastCheckpointPositionMs,
            elapsedNowMs = elapsedNow,
            lastCheckpointSavedAtElapsedMs = lastCheckpointSavedAtElapsedMs
        )
        if (!shouldPersist) return null
        val snapshot = PlaybackCheckpointSnapshot(
            serverId = observedActiveServerId,
            bookId = bookId,
            currentTimeSeconds = positionMs / 1000.0,
            durationSeconds = state.resolvedProgressDurationSeconds(),
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
        urgentBackstop: Boolean = false,
        allowBackgroundRetry: Boolean
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
        if (allowBackgroundRetry) {
            PlaybackProgressSyncScheduler.enqueue(
                context = appContext,
                urgent = urgentBackstop,
                allowBackgroundRetry = true
            )
        } else {
            PlaybackProgressSyncScheduler.cancel(appContext)
        }
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
                if (
                    !shouldContinuePlaybackSyncRetry(
                        allowBackgroundRetry = nextRequest.allowBackgroundRetry,
                        requestBookId = nextRequest.bookId,
                        currentBookId = currentBookId,
                        isPlaybackActive = uiState.value.isPlaying
                    )
                ) {
                    PlaybackProgressSyncScheduler.cancel(appContext)
                    continue
                }
                val failedRequestKey = progressSyncKey(
                    serverId = nextRequest.serverId,
                    bookId = nextRequest.bookId
                )
                val queuedReplacementRequest = pendingSyncRequests[failedRequestKey]
                pendingSyncRequests[failedRequestKey] = mergeProgressSyncRequests(
                    existing = nextRequest,
                    incoming = queuedReplacementRequest ?: nextRequest
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
        return incoming.copy(
            isFinished = resolveMergedProgressSyncFinishedState(
                existingIsFinished = existing.isFinished,
                incomingIsFinished = incoming.isFinished
            ),
            allowBackgroundRetry = incoming.allowBackgroundRetry
        )
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
            sessionPreferences.markPlaybackCheckpointSynced(
                serverId = request.serverId,
                bookId = request.bookId,
                savedAtMs = checkpoint.savedAtMs
            )
            if (sessionPreferences.getPendingPlaybackCheckpoints().isEmpty()) {
                PlaybackProgressSyncScheduler.cancel(appContext)
            }
        }
    }

    private fun shouldSyncAsFinished(): Boolean {
        val state = uiState.value
        return shouldTreatPlaybackAsFinished(
            bookIsFinished = state.book?.isFinished == true,
            positionMs = state.positionMs,
            durationMs = state.durationMs.takeIf { it > 0L },
            bookDurationSeconds = state.book?.durationSeconds
        )
    }

    private fun updateCachedFromUiState() {
        val state = uiState.value
        val currentBook = state.book ?: return
        cachedContinueListeningItem = buildContinueListeningItem(
            book = currentBook,
            positionMs = state.positionMs,
            fallbackDurationMs = state.durationMs.takeIf { it > 0L }
        )
    }

    private fun PlaybackUiState.resolvedProgressDurationSeconds(): Double? {
        val metadataDuration = book?.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
        val liveDuration = durationMs.takeIf { it > 0L }?.div(1000.0)
        return listOfNotNull(metadataDuration, liveDuration).maxOrNull()
    }

    private fun String.splitPodcastId(): Pair<String, String>? {
        val parts = split("::", limit = 2)
        val showId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val episodeId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return showId to episodeId
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

    private fun observeActiveSessionSelection() {
        scope.launch {
            sessionPreferences.state.collect { pref ->
                val nextServerId = pref.activeServerId
                val nextLibraryId = pref.activeLibraryId
                if (!hasObservedActiveServerId || !hasObservedActiveLibraryId) {
                    observedActiveServerId = nextServerId
                    observedActiveLibraryId = nextLibraryId
                    hasObservedActiveServerId = true
                    hasObservedActiveLibraryId = true
                    return@collect
                }
                val previousServerId = observedActiveServerId
                val previousLibraryId = observedActiveLibraryId
                observedActiveServerId = nextServerId
                observedActiveLibraryId = nextLibraryId
                if (previousServerId != nextServerId) {
                    clearPlaybackForServerSwitch()
                } else if (previousLibraryId != nextLibraryId) {
                    clearPlaybackForLibrarySwitch()
                }
            }
        }
    }

    private suspend fun clearPlaybackForLibrarySwitch() {
        val hadBook = uiState.value.book != null
        if (hadBook) {
            updateCachedFromUiState()
        }
        if (hadBook) {
            if (uiState.value.isPlaying) {
                pause(reason = PauseReason.Internal)
            } else {
                saveProgressSnapshot()
            }
            syncQueueJob?.join()
        }
        playRequestJob?.cancel()
        playRequestToken += 1L
        releasePlayer(syncProgressBeforeRelease = false)
        currentBookId = null
        currentPlaybackSource = null
        currentTrackStartOffsetMs = 0L
        currentBookDurationMs = 0L
        cachedContinueListeningItem = null
        attemptedAutoAdvanceTargetsMs.clear()
        oomFallbackStreamingBookIds.clear()
        previousRestartState = null
        playbackSyncGate.reset()
        lastCheckpointPositionMs = -1L
        lastCheckpointSavedAtElapsedMs = 0L
        lastAppBackgroundSyncAtElapsedMs = 0L
        lastAppBackgroundSyncPositionMs = -1L
        suppressNextAutoAdvanceOnCompletion = false
        cancelSleepTimer(updateUi = false)
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

    private fun clearPlaybackForServerSwitch() {
        playRequestJob?.cancel()
        playRequestToken += 1L
        syncQueueJob?.cancel()
        syncQueueJob = null
        pendingSyncRequests.clear()
        releasePlayer(syncProgressBeforeRelease = false)
        currentBookId = null
        currentPlaybackSource = null
        currentTrackStartOffsetMs = 0L
        currentBookDurationMs = 0L
        cachedContinueListeningItem = null
        attemptedAutoAdvanceTargetsMs.clear()
        oomFallbackStreamingBookIds.clear()
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

    private fun normalizeLockScreenControlMode(rawMode: String?): String {
        return com.stillshelf.app.playback.controller.normalizeLockScreenControlMode(rawMode)
    }

    private fun shouldGoToPreviousAfterRestart(
        bookId: String,
        restartStartMs: Long,
        chapterMode: Boolean,
        currentPositionMs: Long
    ): Boolean {
        return com.stillshelf.app.playback.controller.shouldGoToPreviousAfterRestart(
            previousRestartState = previousRestartState,
            bookId = bookId,
            restartStartMs = restartStartMs,
            chapterMode = chapterMode,
            currentPositionMs = currentPositionMs,
            nowElapsedMs = SystemClock.elapsedRealtime()
        )
    }

    private fun rememberRestart(bookId: String, restartStartMs: Long, chapterMode: Boolean) {
        previousRestartState = rememberRestartState(
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
        return resolveRemainingToChapterBoundaryMs(
            positionMs = positionMs,
            targetBoundaryMs = resolveNextChapterBoundaryMs(
                boundariesMs = sleepTimerChapterBoundariesMs,
                positionMs = positionMs
            )
        )
    }

    private fun nextChapterBoundaryForPosition(positionMs: Long): Long? {
        return resolveNextChapterBoundaryMs(
            boundariesMs = sleepTimerChapterBoundariesMs,
            positionMs = positionMs
        )
    }

    private fun remainingToTargetChapterBoundaryMs(positionMs: Long): Long {
        val targetBoundaryMs = sleepTimerTargetBoundaryMs ?: return remainingToNextChapterBoundaryMs(positionMs)
        return resolveRemainingToChapterBoundaryMs(
            positionMs = positionMs,
            targetBoundaryMs = targetBoundaryMs
        )
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

    private fun applyPlaybackSpeed(player: ExoPlayer, speed: Float) {
        runCatching {
            player.playbackParameters = PlaybackParameters(speed, 1.0f)
        }
    }

    private fun applyAudioEffects(player: ExoPlayer) {
        ensureAudioEffects(player)
        applyBoostEffect()
        applySoftToneEffect()
    }

    private fun ensureAudioEffects(player: ExoPlayer) {
        val sessionId = runCatching { player.audioSessionId }.getOrDefault(C.AUDIO_SESSION_ID_UNSET)
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
        val targetGainMb = resolveBoostGainMb(currentBoostLevel)
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
                val targetLevel = resolveSoftToneBandLevel(
                    softToneLevel = currentSoftToneLevel,
                    bandIndex = band,
                    bandCount = bandCount,
                    minLevelMb = minLevel,
                    maxLevelMb = maxLevel
                )
                toneEq.setBandLevel(band.toShort(), targetLevel)
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

    private fun applyPreferredOutputDevice(player: ExoPlayer) {
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

    private fun applySystemDefaultOutputRouting(player: ExoPlayer): Boolean {
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

    private fun applyPreferredOutputForDisplayedId(player: ExoPlayer, displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        return candidates.any { targetDevice ->
            setPreferredOutputDevice(player, targetDevice)
        }
    }

    private fun applyTargetedOutputRouting(player: ExoPlayer, deviceId: Int?): Boolean {
        if (deviceId == null) {
            return applySystemDefaultOutputRouting(player)
        }
        val speakerTarget = isSpeakerOutputDevice(deviceId)
        return if (speakerTarget) {
            prepareForSpeakerPreferredRouting(player)
            applyPreferredOutputForDisplayedId(player, deviceId) || applyOutputViaAudioManagerFallback(deviceId)
        } else {
            clearSpeakerRouteOverride(player)
            applyPreferredOutputForDisplayedId(player, deviceId) || applyOutputViaAudioManagerFallback(deviceId)
        }
    }

    private fun performMutedOutputSwitch(player: ExoPlayer, block: () -> Boolean, toSpeakerRoute: Boolean): Boolean {
        cancelOutputRecovery()
        suppressRefreshRoutingUntilElapsedMs =
            SystemClock.elapsedRealtime() + resolveOutputSwitchRefreshSuppressionMs(toSpeakerRoute)
        val originalVolume = player.volume
        val shouldResumePlayback = player.isPlaying || player.playWhenReady
        if (shouldResumePlayback) {
            runCatching { player.pause() }
            updateUiState { it.copy(isPlaying = false, errorMessage = null) }
        }
        runCatching { player.volume = 0f }
        val applied = block()
        if (!applied) {
            suppressRefreshRoutingUntilElapsedMs = 0L
            runCatching { player.volume = originalVolume }
            if (shouldResumePlayback) {
                runCatching { player.play() }
                updateUiState { it.copy(isPlaying = true, errorMessage = null) }
            }
            return false
        }
        scheduleOutputSwitchRecovery(player, originalVolume, shouldResumePlayback, toSpeakerRoute)
        return applied
    }

    private fun scheduleOutputSwitchRecovery(
        player: ExoPlayer,
        volume: Float,
        shouldResumePlayback: Boolean,
        toSpeakerRoute: Boolean
    ) {
        outputRecoveryJob = scope.launch {
            delay(if (toSpeakerRoute) SPEAKER_OUTPUT_SWITCH_RESTORE_DELAY_MS else OUTPUT_SWITCH_RESTORE_DELAY_MS)
            if (mediaPlayer !== player) {
                outputRecoveryJob = null
                return@launch
            }
            if (shouldResumePlayback) {
                runCatching { player.play() }
                updateUiState { it.copy(isPlaying = true, errorMessage = null) }
            }
            if (toSpeakerRoute) {
                val targetVolume = volume.coerceAtLeast(0f)
                repeat(SPEAKER_OUTPUT_VOLUME_RAMP_STEPS) { step ->
                    if (mediaPlayer !== player) {
                        outputRecoveryJob = null
                        return@launch
                    }
                    val progress = (step + 1).toFloat() / SPEAKER_OUTPUT_VOLUME_RAMP_STEPS.toFloat()
                    runCatching { player.volume = targetVolume * progress }
                    if (step < SPEAKER_OUTPUT_VOLUME_RAMP_STEPS - 1) {
                        delay(SPEAKER_OUTPUT_VOLUME_RAMP_STEP_DELAY_MS)
                    }
                }
            } else {
                runCatching { player.volume = volume }
            }
            outputRecoveryJob = null
        }
    }

    private fun cancelOutputRecovery() {
        outputRecoveryJob?.cancel()
        outputRecoveryJob = null
        suppressRefreshRoutingUntilElapsedMs = 0L
    }

    private fun resolveOutputSwitchRefreshSuppressionMs(toSpeakerRoute: Boolean): Long {
        val restoreDelayMs = if (toSpeakerRoute) {
            SPEAKER_OUTPUT_SWITCH_RESTORE_DELAY_MS
        } else {
            OUTPUT_SWITCH_RESTORE_DELAY_MS
        }
        return restoreDelayMs + OUTPUT_SWITCH_REFRESH_GRACE_MS
    }

    private fun updateOutputSelectionWithoutRouting(available: List<PlaybackOutputDevice>) {
        updateUiState {
            it.copy(
                outputDevices = available,
                selectedOutputDeviceId = preferredOutputDeviceId
            )
        }
    }

    private fun clearPreferredOutputDevice(player: ExoPlayer): Boolean {
        return runCatching {
            player.setPreferredAudioDevice(null)
            true
        }.getOrDefault(false)
    }

    private fun setPreferredOutputDevice(player: ExoPlayer, targetDevice: AudioDeviceInfo): Boolean {
        return runCatching {
            player.setPreferredAudioDevice(targetDevice)
            true
        }.getOrDefault(false)
    }

    private fun clearCommunicationRouteOverride(): Boolean {
        if (SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching {
            audioManager.clearCommunicationDevice()
            true
        }.getOrDefault(false)
    }

    private fun clearSpeakerRouteOverride(player: ExoPlayer): Boolean {
        val preferredCleared = clearPreferredOutputDevice(player)
        val communicationCleared = clearCommunicationRouteOverride()
        val speakerReset = runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            true
        }.getOrDefault(false)
        return preferredCleared || communicationCleared || speakerReset
    }

    private fun prepareForSpeakerPreferredRouting(player: ExoPlayer): Boolean {
        val preferredCleared = clearPreferredOutputDevice(player)
        val communicationCleared = clearCommunicationRouteOverride()
        val speakerReset = runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            true
        }.getOrDefault(false)
        return preferredCleared || communicationCleared || speakerReset
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
                @Suppress("DEPRECATION")
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
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        return false
    }

    private fun updatePlaybackSurface() {
        val state = uiState.value
        val keepPlaybackSessionActive = shouldKeepPlaybackSessionActive(
            book = state.book,
            hasActivePlayer = mediaPlayer != null
        )
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
        mediaSession.isActive = keepPlaybackSessionActive

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
            if (PlaybackServiceController.stop(appContext, BackendProvider.AUDIOBOOKSHELF)) {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            }
            return
        }

        if (!keepPlaybackSessionActive) {
            lastNotificationSignature = null
            if (PlaybackServiceController.stop(appContext, BackendProvider.AUDIOBOOKSHELF)) {
                NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
            }
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
        ensurePausedPlayerReleasePolicy()
    }

    private fun cancelPausedPlayerRelease() {
        pausedReleaseJob?.cancel()
        pausedReleaseJob = null
    }

    private fun ensurePausedPlayerReleasePolicy() {
        val player = mediaPlayer
        val state = uiState.value
        val shouldScheduleRelease = shouldScheduleAbsPausedPlayerRelease(
            book = state.book,
            hasActivePlayer = player != null,
            isPlaying = player?.isPlaying == true,
            playWhenReady = player?.playWhenReady == true,
            playbackState = player?.playbackState ?: Player.STATE_IDLE,
            appInForeground = appInForeground
        )
        if (!shouldScheduleRelease) {
            cancelPausedPlayerRelease()
            return
        }
        if (pausedReleaseJob?.isActive == true) return
        pausedReleaseJob = scope.launch {
            delay(PAUSED_PLAYER_RELEASE_DELAY_MS)
            val playerToRelease = mediaPlayer
            val currentState = uiState.value
            val stillPaused = shouldScheduleAbsPausedPlayerRelease(
                book = currentState.book,
                hasActivePlayer = playerToRelease != null,
                isPlaying = playerToRelease?.isPlaying == true,
                playWhenReady = playerToRelease?.playWhenReady == true,
                playbackState = playerToRelease?.playbackState ?: Player.STATE_IDLE,
                appInForeground = appInForeground
            )
            if (!stillPaused) return@launch
            releasePlayer(syncProgressBeforeRelease = true)
            updateUiState {
                it.copy(
                    isPlaying = false,
                    isLoading = false
                )
            }
        }
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
        if (
            notificationSignature == lastNotificationSignature &&
            PlaybackServiceController.isActive()
        ) {
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
            PlaybackServiceController.startOrUpdate(
                context = appContext,
                notification = notification,
                owner = BackendProvider.AUDIOBOOKSHELF
            )
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
            val deleted = runCatching {
                manager.deleteNotificationChannel(CHANNEL_ID)
            }.isSuccess
            if (!deleted) {
                return
            }
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
            val nextTrackStartMs = resolveNextTrackStartMs(
                tracks = currentPlaybackSource?.tracks.orEmpty(),
                currentTrackStartOffsetMs = currentTrackStartOffsetMs
            )
            if (
                nextTrackStartMs != null &&
                attemptedAutoAdvanceTargetsMs.add(nextTrackStartMs)
            ) {
                setPendingAutoAdvanceUiTarget(bookId = book.id, targetPositionMs = nextTrackStartMs)
                playBookFromPosition(bookId = book.id, startPositionMs = nextTrackStartMs)
                return@launch
            }
            val nextStartMs = resolveNextChapterStartMs(
                bookId = book.id,
                finishedStreamDurationMs = durationMs
            )
            if (
                nextStartMs != null &&
                attemptedAutoAdvanceTargetsMs.add(nextStartMs)
            ) {
                setPendingAutoAdvanceUiTarget(bookId = book.id, targetPositionMs = nextStartMs)
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
            is AppResult.Success -> ChapterAutoAdvanceResolver.resolveNextChapterStartMs(
                chapters = detail.value.chapters,
                finishedStreamDurationMs = finishedStreamDurationMs,
                bookDurationMs = detail.value.book.durationSeconds
                    ?.times(1000.0)
                    ?.toLong()
            )

            is AppResult.Error -> null
        }
    }

    private fun completePlayback(durationMs: Long) {
        clearPendingAutoAdvanceUiTarget()
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
        syncProgress(
            force = true,
            isFinished = true,
            allowBackgroundRetry = false
        )
    }

    private fun syncPendingPlaybackCheckpointsOnForeground() {
        scope.launch(Dispatchers.IO) {
            val checkpoints = sessionPreferences.getPendingPlaybackCheckpoints()
                .sortedBy { checkpoint -> checkpoint.savedAtMs }
            if (checkpoints.isEmpty()) {
                PlaybackProgressSyncScheduler.cancel(appContext)
                return@launch
            }
            PlaybackProgressSyncScheduler.cancel(appContext)
            checkpoints.forEach { checkpoint ->
                val serverId = checkpoint.serverId?.trim()
                if (serverId.isNullOrBlank()) {
                    sessionPreferences.clearPlaybackCheckpoint(
                        serverId = checkpoint.serverId,
                        bookId = checkpoint.bookId
                    )
                    return@forEach
                }
                when (
                    sessionRepository.syncPlaybackProgressForServer(
                        serverId = serverId,
                        bookId = checkpoint.bookId,
                        currentTimeSeconds = checkpoint.currentTimeSeconds,
                        durationSeconds = checkpoint.durationSeconds,
                        isFinished = checkpoint.isFinished
                    )
                ) {
                    is AppResult.Success -> {
                        sessionPreferences.markPlaybackCheckpointSynced(
                            serverId = serverId,
                            bookId = checkpoint.bookId,
                            savedAtMs = checkpoint.savedAtMs
                        )
                    }

                    is AppResult.Error -> Unit
                }
            }
            if (sessionPreferences.getPendingPlaybackCheckpoints().isEmpty()) {
                PlaybackProgressSyncScheduler.cancel(appContext)
            }
        }
    }
}

internal fun resolveMergedProgressSyncFinishedState(
    existingIsFinished: Boolean,
    incomingIsFinished: Boolean
): Boolean = incomingIsFinished

internal fun shouldContinuePlaybackSyncRetry(
    allowBackgroundRetry: Boolean,
    requestBookId: String,
    currentBookId: String?,
    isPlaybackActive: Boolean
): Boolean {
    return allowBackgroundRetry &&
        isPlaybackActive &&
        !currentBookId.isNullOrBlank() &&
        currentBookId == requestBookId
}

internal fun shouldReplayLocalCheckpointAtStartup(
    selectedSourceIsLocal: Boolean,
    localCheckpointMatchesResolvedProgress: Boolean
): Boolean = selectedSourceIsLocal && localCheckpointMatchesResolvedProgress
