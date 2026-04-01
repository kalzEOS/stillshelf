package com.stillshelf.app.playback.navidrome

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import com.stillshelf.app.MainActivity
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeEqualizerProfile
import com.stillshelf.app.core.model.NavidromeOutputDevice
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeQueueDisplayMode
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.navidromeEqualizerBandFrequenciesHz
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadManager
import com.stillshelf.app.playback.notification.PlaybackActionReceiver
import com.stillshelf.app.playback.service.PlaybackServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal fun shouldScheduleNavidromePausedPlayerRelease(
    currentTrack: NavidromeTrack?,
    hasActivePlayer: Boolean,
    isPlaying: Boolean,
    playWhenReady: Boolean,
    playbackState: Int
): Boolean {
    return currentTrack != null &&
        !currentTrack.id.startsWith("radio:") &&
        hasActivePlayer &&
        !isPlaying &&
        !playWhenReady &&
        playbackState != Player.STATE_BUFFERING
}

internal fun resolveNavidromeEqualizerBandLevelDb(
    effectCenterFrequencyMilliHz: Int,
    desiredLevels: List<Float>
): Float {
    if (desiredLevels.isEmpty()) return 0f
    val centerFrequencyHz = effectCenterFrequencyMilliHz / 1000f
    val nearestIndex = navidromeEqualizerBandFrequenciesHz.indices.minByOrNull { index ->
        abs(navidromeEqualizerBandFrequenciesHz[index] - centerFrequencyHz)
    } ?: return desiredLevels.last()
    return desiredLevels.getOrElse(nearestIndex) { 0f }
}

internal fun isNavidromeOutputSwitchInFlight(
    nowElapsedMs: Long,
    suppressRefreshRoutingUntilElapsedMs: Long
): Boolean {
    return nowElapsedMs < suppressRefreshRoutingUntilElapsedMs
}

internal data class NavidromeTrackSnapshotPayload(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val durationSeconds: Int?,
    val coverUrl: String?,
    val streamUrl: String,
    val formatLabel: String?,
    val bitRateKbps: Int?
)

internal fun NavidromeTrack.toSnapshotPayload(): NavidromeTrackSnapshotPayload {
    return NavidromeTrackSnapshotPayload(
        id = id,
        title = title,
        artistName = artistName,
        albumName = albumName,
        albumId = albumId,
        artistId = artistId,
        trackNumber = trackNumber,
        durationSeconds = durationSeconds,
        coverUrl = coverUrl,
        streamUrl = streamUrl,
        formatLabel = formatLabel,
        bitRateKbps = bitRateKbps
    )
}

internal fun NavidromeTrackSnapshotPayload.toTrack(): NavidromeTrack? {
    if (id.isBlank()) return null
    return NavidromeTrack(
        id = id,
        title = title.ifBlank { "Unknown track" },
        artistName = artistName.ifBlank { "Unknown artist" },
        albumName = albumName.ifBlank { "Unknown album" },
        albumId = albumId?.takeIf { it.isNotBlank() },
        artistId = artistId?.takeIf { it.isNotBlank() },
        trackNumber = trackNumber?.takeIf { it > 0 },
        durationSeconds = durationSeconds?.takeIf { it > 0 },
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
        streamUrl = streamUrl.trim(),
        formatLabel = formatLabel?.takeIf { it.isNotBlank() },
        bitRateKbps = bitRateKbps?.takeIf { it > 0 }
    )
}

internal fun serializeNavidromeTrackSnapshot(track: NavidromeTrack): JSONObject {
    val payload = track.toSnapshotPayload()
    return JSONObject()
        .put("id", payload.id)
        .put("title", payload.title)
        .put("artistName", payload.artistName)
        .put("albumName", payload.albumName)
        .put("streamUrl", payload.streamUrl)
        .apply {
            payload.albumId?.let { put("albumId", it) }
            payload.artistId?.let { put("artistId", it) }
            payload.trackNumber?.let { put("trackNumber", it) }
            payload.durationSeconds?.let { put("durationSeconds", it) }
            payload.coverUrl?.let { put("coverUrl", it) }
            payload.formatLabel?.let { put("formatLabel", it) }
            payload.bitRateKbps?.let { put("bitRateKbps", it) }
        }
}

internal fun parseNavidromeTrackSnapshot(item: JSONObject): NavidromeTrack? {
    return NavidromeTrackSnapshotPayload(
        id = item.optString("id").trim(),
        title = item.optString("title"),
        artistName = item.optString("artistName"),
        albumName = item.optString("albumName"),
        albumId = item.optString("albumId").ifBlank { null },
        artistId = item.optString("artistId").ifBlank { null },
        trackNumber = item.takeIf { it.has("trackNumber") }?.optInt("trackNumber"),
        durationSeconds = item.takeIf { it.has("durationSeconds") }?.optInt("durationSeconds"),
        coverUrl = item.optString("coverUrl").ifBlank { null },
        streamUrl = item.optString("streamUrl").trim(),
        formatLabel = item.optString("formatLabel").ifBlank { null },
        bitRateKbps = item.takeIf { it.has("bitRateKbps") }?.optInt("bitRateKbps")
    ).toTrack()
}

@Singleton
class NavidromePlayerController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences,
    private val downloadManager: NavidromeDownloadManager,
    private val navidromeRepository: NavidromeRepository
) {
    private companion object {
        const val MAX_RECENT_TRACKS = 7
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000
        const val PLAYING_PROGRESS_UPDATE_INTERVAL_MS = 80L
        const val IDLE_PROGRESS_UPDATE_INTERVAL_MS = 250L
        const val CHANNEL_ID = "stillshelf_playback_v4"
        const val NOTIFICATION_ID = 1101
        const val ACTION_PLAY_PAUSE = "com.stillshelf.app.navidrome.playback.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.stillshelf.app.navidrome.playback.action.PREVIOUS"
        const val ACTION_NEXT = "com.stillshelf.app.navidrome.playback.action.NEXT"
        const val PAUSED_PLAYER_RELEASE_DELAY_MS = 8 * 60 * 1000L
        const val OUTPUT_SWITCH_RESTORE_DELAY_MS = 220L
        const val SPEAKER_OUTPUT_SWITCH_RESTORE_DELAY_MS = 450L
        const val SPEAKER_OUTPUT_VOLUME_RAMP_STEPS = 5
        const val SPEAKER_OUTPUT_VOLUME_RAMP_STEP_DELAY_MS = 90L
        const val OUTPUT_SWITCH_REFRESH_GRACE_MS = 500L
        const val NAVIDROME_PREAMP_MAX_GAIN_MB = 1200
    }

    private enum class OutputRefreshReason {
        General,
        DeviceAdded,
        DeviceRemoved
    }

    private data class NotificationSignature(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val hasArtwork: Boolean
    )

    private data class MediaArtworkPayload(
        val trackId: String,
        val bitmap: Bitmap?
    )

    private data class OutputRouteCandidate(
        val routeKey: String,
        val priority: Int,
        val device: AudioDeviceInfo
    )

    private data class NavidromePlaybackSnapshot(
        val queue: List<NavidromeTrack>,
        val recentTracks: List<NavidromeTrack>,
        val queueDisplayMode: NavidromeQueueDisplayMode,
        val currentIndex: Int,
        val positionMs: Int
    )

    private val mutableState = MutableStateFlow(NavidromePlayerState())
    val state: StateFlow<NavidromePlayerState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var pausedReleaseJob: Job? = null
    private var outputRecoveryJob: Job? = null
    private var queueTracks: List<NavidromeTrack> = emptyList()
    private var queueDisplayMode: NavidromeQueueDisplayMode = NavidromeQueueDisplayMode.FULL
    private var recentTracks: List<NavidromeTrack> = emptyList()
    private var lastRecordedTrackId: String? = null
    private var repeatMode: Int = REPEAT_MODE_OFF
    private var appInForeground = false
    private var preferredOutputDeviceId: Int? = null
    private var hasExplicitOutputSelection = false
    private var outputRouteDeviceIdsByRouteKey: Map<String, List<Int>> = emptyMap()
    private var outputRouteKeyByDisplayedId: Map<Int, String> = emptyMap()
    private var lastKnownOutputDeviceIds: Set<Int> = emptySet()
    private var suppressRefreshRoutingUntilElapsedMs: Long = 0L
    private val forcedRemoteTrackIds = mutableSetOf<String>()
    private var playbackRecoveryTrackId: String? = null
    private var restorePlaybackGeneration: Long = 0L
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSession = MediaSessionCompat(appContext, "StillShelfNavidromePlayback")
    private var artworkBitmap: Bitmap? = null
    private var artworkTrackId: String? = null
    private var artworkJob: Job? = null
    private var lastNotificationSignature: NotificationSignature? = null
    private var navidromeEqualizerSessionId: Int? = null
    private var navidromeEqualizer: Equalizer? = null
    private var navidromePreamp: LoudnessEnhancer? = null
    private var navidromeEqualizerEnabled = false
    private var navidromeActiveEqualizerProfileId: String? = null
    private var navidromeEqualizerProfiles: List<NavidromeEqualizerProfile> = emptyList()
    private var navidromeEqualizerPreampLevel = 0f

    private val playbackAudioAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateStateFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStateFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStateFromPlayer()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                isPlaying = false,
                errorMessage = error.message ?: "Playback failed for this track."
            )
            recoverPlaybackAfterError(error)
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputDevices(reason = OutputRefreshReason.DeviceAdded)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioOutputDevices(reason = OutputRefreshReason.DeviceRemoved)
        }
    }

    private var player: ExoPlayer? = null

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appInForeground = true
            updateStateFromPlayer()
            ensureProgressUpdates()
        }

        override fun onStop(owner: LifecycleOwner) {
            appInForeground = false
            stopProgressUpdates()
            persistPlaybackSnapshot()
            ensurePausedPlayerReleasePolicy()
        }
    }

    init {
        appInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onSkipToPrevious() = playPrevious()
            override fun onSkipToNext() = playNext()
        })
        mediaSession.isActive = false
        observeEqualizerPreferences()
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
        restorePlaybackSnapshot()
        ensureProgressUpdates()
    }

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(appContext)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(playbackAudioAttributes, true)
                addListener(playerListener)
                ensureNavidromeAudioEffects(this)
            }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        return createPlayer().also { created ->
            player = created
            created.repeatMode = repeatMode
            applyPreferredOutputDevice(created)
        }
    }

    fun refreshAudioOutputs() {
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
    }

    fun selectAudioOutputDevice(deviceId: Int?): Boolean {
        val available = queryOutputDevices()
        if (deviceId != null && available.none { output -> output.id == deviceId }) {
            refreshAudioOutputs()
            return false
        }
        preferredOutputDeviceId = deviceId
        hasExplicitOutputSelection = deviceId != null
        val activePlayer = player
        if (activePlayer == null) {
            refreshAudioOutputs()
            return true
        }
        if (deviceId == null) {
            val applied = performMutedOutputSwitch(
                player = activePlayer,
                block = { applySystemDefaultOutputRouting(activePlayer) },
                toSpeakerRoute = false
            )
            if (!applied) {
                refreshAudioOutputs()
                return false
            }
            refreshAudioOutputs()
            return true
        }
        val speakerTarget = isSpeakerOutputDevice(deviceId)
        val applied = performMutedOutputSwitch(
            player = activePlayer,
            block = { applyTargetedOutputRouting(activePlayer, deviceId) },
            toSpeakerRoute = speakerTarget
        )
        if (!applied) {
            refreshAudioOutputs()
            return false
        }
        refreshAudioOutputs()
        return true
    }

    fun playTracks(
        tracks: List<NavidromeTrack>,
        startIndex: Int,
        queueDisplayMode: NavidromeQueueDisplayMode = NavidromeQueueDisplayMode.FULL
    ) {
        if (tracks.isEmpty()) return
        invalidatePendingPlaybackRestore()
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        playbackRecoveryTrackId = null
        queueTracks = tracks
        this.queueDisplayMode = queueDisplayMode
        mutableState.value = mutableState.value.copy(
            queue = tracks,
            queueDisplayMode = queueDisplayMode,
            currentIndex = index,
            currentTrack = tracks[index],
            recentTracks = recentTracks,
            isLoading = true,
            isPlaying = false,
            positionMs = 0,
            durationMs = resolveTrackDurationMs(tracks[index]),
            errorMessage = null
        )
        val activePlayer = getOrCreatePlayer()
        activePlayer.setMediaItems(
            tracks.map { track ->
                MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(resolvePlaybackUri(track))
                    .build()
            },
            index,
            0L
        )
        applyPreferredOutputDevice(activePlayer)
        activePlayer.prepare()
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun playTracksNext(tracks: List<NavidromeTrack>) {
        val normalizedTracks = tracks.filter { it.id.isNotBlank() }
        if (normalizedTracks.isEmpty()) return
        if (queueTracks.isEmpty()) {
            playTracks(normalizedTracks, startIndex = 0)
            return
        }
        val currentIndex = mutableState.value.currentIndex
            .takeIf { it in queueTracks.indices }
            ?: 0
        val insertIndex = (currentIndex + 1).coerceAtMost(queueTracks.size)
        queueTracks = buildList {
            addAll(queueTracks.take(insertIndex))
            addAll(normalizedTracks)
            addAll(queueTracks.drop(insertIndex))
        }
        queueDisplayMode = NavidromeQueueDisplayMode.FULL
        player?.addMediaItems(insertIndex, normalizedTracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(resolvePlaybackUri(track))
                .build()
        })
        updateStateFromPlayer()
        persistPlaybackSnapshot()
    }

    fun appendTracksToQueue(tracks: List<NavidromeTrack>) {
        val normalizedTracks = tracks.filter { it.id.isNotBlank() }
        if (normalizedTracks.isEmpty()) return
        if (queueTracks.isEmpty()) {
            playTracks(normalizedTracks, startIndex = 0)
            return
        }
        queueTracks = queueTracks + normalizedTracks
        queueDisplayMode = NavidromeQueueDisplayMode.FULL
        player?.addMediaItems(normalizedTracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(resolvePlaybackUri(track))
                .build()
        })
        updateStateFromPlayer()
        persistPlaybackSnapshot()
    }

    fun togglePlayPause() {
        if (mutableState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if (queueTracks.isEmpty()) return
        val currentTrack = queueTracks.getOrNull(mutableState.value.currentIndex)
        val activePlayer = player
        if (currentTrack != null && currentTrack.isRadioTrack()) {
            if (activePlayer?.isPlaying == true) return
            resumeFromSnapshot(playWhenReady = true)
            return
        }
        if (activePlayer == null) {
            resumeFromSnapshot(playWhenReady = true)
            return
        }
        if (activePlayer.isPlaying) return
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun pause() {
        if (queueTracks.isEmpty()) return
        val currentTrack = queueTracks.getOrNull(mutableState.value.currentIndex)
        val activePlayer = player ?: return
        if (currentTrack != null && currentTrack.isRadioTrack()) {
            if (!activePlayer.isPlaying) return
            restartOrStopRadio(currentTrack)
            return
        }
        if (!activePlayer.isPlaying && activePlayer.playbackState != Player.STATE_BUFFERING) return
        activePlayer.pause()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun playNext() {
        val currentIndex = resolveCurrentQueueIndex()
        val nextIndex = (currentIndex + 1).takeIf { it in queueTracks.indices } ?: return
        seekToQueueIndex(index = nextIndex, positionMs = 0, playWhenReady = true)
    }

    fun playPrevious() {
        val currentIndex = resolveCurrentQueueIndex()
        if (currentIndex !in queueTracks.indices) return
        val currentTrack = queueTracks[currentIndex]
        if (!currentTrack.isRadioTrack() && mutableState.value.positionMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            seekToQueueIndex(index = currentIndex, positionMs = 0, playWhenReady = true)
            return
        }
        val previousIndex = (currentIndex - 1).takeIf { it in queueTracks.indices } ?: run {
            seekToQueueIndex(index = currentIndex, positionMs = 0, playWhenReady = true)
            return
        }
        seekToQueueIndex(index = previousIndex, positionMs = 0, playWhenReady = true)
    }

    fun playQueueIndex(index: Int) {
        if (index !in queueTracks.indices) return
        seekToQueueIndex(index = index, positionMs = 0, playWhenReady = true)
    }

    fun shuffleQueue() {
        if (queueTracks.isEmpty()) return
        val currentIndex = mutableState.value.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val currentTrack = queueTracks[currentIndex]
        val remaining = queueTracks
            .filterIndexed { index, _ -> index != currentIndex }
            .shuffled()
        playTracks(
            tracks = listOf(currentTrack) + remaining,
            startIndex = 0,
            queueDisplayMode = queueDisplayMode
        )
    }

    fun cycleRepeatMode(): Int {
        repeatMode = when (repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            else -> REPEAT_MODE_OFF
        }
        player?.repeatMode = repeatMode
        updatePlaybackSurface()
        return repeatMode
    }

    fun currentRepeatMode(): Int = repeatMode

    fun stop() {
        invalidatePendingPlaybackRestore()
        queueTracks = emptyList()
        lastRecordedTrackId = null
        forcedRemoteTrackIds.clear()
        playbackRecoveryTrackId = null
        releasePlayer(clearQueue = true)
        stopProgressUpdates()
        clearPlaybackSurface()
        scope.launch(Dispatchers.IO) {
            sessionPreferences.clearCachedNavidromePlayback()
        }
        mutableState.value = NavidromePlayerState(
            recentTracks = recentTracks,
            outputDevices = mutableState.value.outputDevices,
            selectedOutputDeviceId = mutableState.value.selectedOutputDeviceId
        )
    }

    fun handleExternalPlaybackAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
        }
    }

    fun seekTo(positionMs: Int) {
        val durationMs = resolvePlayerDurationMs()
        val clampedPosition = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
        player?.seekTo(clampedPosition.toLong())
        mutableState.value = mutableState.value.copy(
            positionMs = clampedPosition,
            durationMs = durationMs
        )
        persistPlaybackSnapshot()
    }

    private fun startPlaybackAt(
        index: Int,
        positionMs: Int,
        playWhenReady: Boolean,
        resetRecoveryState: Boolean = true
    ) {
        if (queueTracks.isEmpty()) return
        val safeIndex = index.coerceIn(0, queueTracks.lastIndex)
        val safePositionMs = positionMs.coerceAtLeast(0)
        if (resetRecoveryState) {
            playbackRecoveryTrackId = null
        }
        releasePlayer(clearQueue = false)
        val activePlayer = getOrCreatePlayer()
        activePlayer.setMediaItems(
            queueTracks.map { track ->
                MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(resolvePlaybackUri(track))
                    .build()
            },
            safeIndex,
            safePositionMs.toLong()
        )
        applyPreferredOutputDevice(activePlayer)
        activePlayer.prepare()
        if (playWhenReady) {
            activePlayer.play()
        }
        mutableState.value = mutableState.value.copy(
            queue = queueTracks,
            queueDisplayMode = queueDisplayMode,
            currentIndex = safeIndex,
            currentTrack = queueTracks[safeIndex],
            recentTracks = recentTracks,
            isLoading = playWhenReady,
            isPlaying = false,
            positionMs = safePositionMs,
            durationMs = resolveTrackDurationMs(queueTracks[safeIndex]),
            errorMessage = null
        )
        persistPlaybackSnapshot()
        updateStateFromPlayer()
        ensureProgressUpdates()
    }

    private fun recoverPlaybackAfterError(error: androidx.media3.common.PlaybackException) {
        val failedTrack = mutableState.value.currentTrack ?: return
        val failedTrackId = failedTrack.id
        if (failedTrackId.isBlank() || playbackRecoveryTrackId == failedTrackId || queueTracks.isEmpty()) {
            return
        }
        playbackRecoveryTrackId = failedTrackId
        val retryIndex = mutableState.value.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val retryPositionMs = mutableState.value.positionMs.coerceAtLeast(0)
        val bypassBrokenLocalCopy = failedTrackId !in forcedRemoteTrackIds &&
            downloadManager.localPlaybackUri(failedTrack) != null
        scope.launch(Dispatchers.IO) {
            val refreshedQueue = when (val result = navidromeRepository.refreshPlayableTracks(queueTracks)) {
                is com.stillshelf.app.core.util.AppResult.Success -> result.value
                is com.stillshelf.app.core.util.AppResult.Error -> null
            }
            scope.launch(Dispatchers.Main.immediate) {
                if (mutableState.value.currentTrack?.id != failedTrackId) {
                    playbackRecoveryTrackId = null
                    return@launch
                }
                if (bypassBrokenLocalCopy) {
                    forcedRemoteTrackIds += failedTrackId
                }
                if (refreshedQueue.isNullOrEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        sessionPreferences.clearCachedNavidromePlayback()
                    }
                    mutableState.value = mutableState.value.copy(
                        errorMessage = error.message ?: "Playback failed for this track."
                    )
                    playbackRecoveryTrackId = null
                    return@launch
                }
                queueTracks = refreshedQueue
                recentTracks = recentTracks.map { recent ->
                    refreshedQueue.firstOrNull { it.id == recent.id } ?: recent
                }
                val restartedIndex = retryIndex.coerceIn(0, refreshedQueue.lastIndex)
                startPlaybackAt(
                    index = restartedIndex,
                    positionMs = retryPositionMs,
                    playWhenReady = true,
                    resetRecoveryState = false
                )
            }
        }
    }

    private fun resolvePlaybackUri(track: NavidromeTrack): String {
        val localUri = if (track.id in forcedRemoteTrackIds) {
            null
        } else {
            downloadManager.localPlaybackUri(track)
        }
        return localUri ?: track.streamUrl
    }

    private fun resumeFromSnapshot(playWhenReady: Boolean) {
        if (queueTracks.isEmpty()) return
        val state = mutableState.value
        val index = state.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val positionMs = if (queueTracks.getOrNull(index).isRadioTrack()) {
            0
        } else {
            state.positionMs.coerceAtLeast(0)
        }
        startPlaybackAt(index = index, positionMs = positionMs, playWhenReady = playWhenReady)
    }

    private fun resolveCurrentQueueIndex(): Int {
        val activePlayer = player
        return activePlayer?.currentMediaItemIndex
            ?.takeIf { it in queueTracks.indices }
            ?: mutableState.value.currentIndex.takeIf { it in queueTracks.indices }
            ?: -1
    }

    private fun seekToQueueIndex(
        index: Int,
        positionMs: Int,
        playWhenReady: Boolean
    ) {
        if (index !in queueTracks.indices) return
        val safePositionMs = positionMs.coerceAtLeast(0)
        val activePlayer = player
        if (activePlayer == null) {
            startPlaybackAt(index = index, positionMs = safePositionMs, playWhenReady = playWhenReady)
            return
        }
        activePlayer.seekTo(index, safePositionMs.toLong())
        if (playWhenReady) {
            activePlayer.play()
        } else {
            activePlayer.pause()
        }
        mutableState.value = mutableState.value.copy(
            currentIndex = index,
            currentTrack = queueTracks[index],
            positionMs = safePositionMs,
            durationMs = resolveTrackDurationMs(queueTracks[index]),
            isLoading = true,
            errorMessage = null
        )
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    private fun releasePlayer(clearQueue: Boolean) {
        val activePlayer = player
        if (activePlayer != null) {
            stopProgressUpdates()
            cancelOutputRecovery()
            releaseNavidromeAudioEffects()
            runCatching { activePlayer.removeListener(playerListener) }
            runCatching { activePlayer.release() }
            player = null
        }
        if (clearQueue) {
            queueTracks = emptyList()
            queueDisplayMode = NavidromeQueueDisplayMode.FULL
        }
    }

    private fun persistPlaybackSnapshot() {
        if (queueTracks.isEmpty()) {
            scope.launch(Dispatchers.IO) {
                sessionPreferences.clearCachedNavidromePlayback()
            }
            return
        }
        val state = mutableState.value
        val currentIndex = state.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val currentTrack = queueTracks.getOrNull(currentIndex)
        val payload = JSONObject()
            .put("currentIndex", currentIndex)
            .put(
                "positionMs",
                if (currentTrack.isRadioTrack()) 0 else state.positionMs.coerceAtLeast(0)
            )
            .put(
                "queue",
                JSONArray().apply {
                    queueTracks.forEach { put(it.toJson()) }
                }
            )
            .put("queueDisplayMode", queueDisplayMode.name)
            .put(
                "recentTracks",
                JSONArray().apply {
                    recentTracks.forEach { put(it.toJson()) }
                }
            )
            .toString()
        scope.launch(Dispatchers.IO) {
            val sessionKey = navidromeRepository.currentPlaybackSessionKey()
            sessionPreferences.setCachedNavidromePlayback(
                sessionKey = sessionKey,
                payload = payload,
                savedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun restorePlaybackSnapshot() {
        scope.launch(Dispatchers.IO) {
            val cachedSnapshot = sessionPreferences.getCachedNavidromePlayback()
                ?: return@launch
            val restoreGeneration = restorePlaybackGeneration
            val currentSessionKey = navidromeRepository.currentPlaybackSessionKey()
            if (cachedSnapshot.sessionKey.isNullOrBlank()) {
                sessionPreferences.clearCachedNavidromePlayback()
                return@launch
            }
            if (
                currentSessionKey != null &&
                cachedSnapshot.sessionKey != currentSessionKey
            ) {
                sessionPreferences.clearCachedNavidromePlayback()
                return@launch
            }
            val snapshot = cachedSnapshot.payload
                .let(::parsePlaybackSnapshot)
                ?: return@launch
            val refreshedQueue = when (val result = navidromeRepository.refreshPlayableTracks(snapshot.queue)) {
                is com.stillshelf.app.core.util.AppResult.Success -> result.value
                is com.stillshelf.app.core.util.AppResult.Error -> {
                    if (snapshot.queue.any { it.streamUrl.isBlank() && !it.isRadioTrack() }) {
                        sessionPreferences.clearCachedNavidromePlayback()
                        return@launch
                    }
                    snapshot.queue
                }
            }
            val refreshedRecent = when (val result = navidromeRepository.refreshPlayableTracks(snapshot.recentTracks)) {
                is com.stillshelf.app.core.util.AppResult.Success -> result.value
                is com.stillshelf.app.core.util.AppResult.Error -> snapshot.recentTracks
            }
            if (refreshedQueue.isEmpty()) {
                sessionPreferences.clearCachedNavidromePlayback()
                return@launch
            }
            scope.launch(Dispatchers.Main.immediate) {
                if (
                    restoreGeneration != restorePlaybackGeneration ||
                    player != null ||
                    queueTracks.isNotEmpty()
                ) {
                    return@launch
                }
                queueTracks = refreshedQueue
                queueDisplayMode = snapshot.queueDisplayMode
                recentTracks = refreshedRecent
                val restoredIndex = snapshot.currentIndex.coerceIn(0, refreshedQueue.lastIndex)
                val currentTrack = refreshedQueue.getOrNull(restoredIndex)
                lastRecordedTrackId = currentTrack?.id
                mutableState.value = mutableState.value.copy(
                    queue = refreshedQueue,
                    queueDisplayMode = snapshot.queueDisplayMode,
                    currentIndex = restoredIndex,
                    currentTrack = currentTrack,
                    recentTracks = refreshedRecent,
                    isPlaying = false,
                    isLoading = false,
                    positionMs = if (currentTrack.isRadioTrack()) 0 else snapshot.positionMs,
                    durationMs = currentTrack?.let(::resolveTrackDurationMs) ?: 0,
                    outputDevices = mutableState.value.outputDevices,
                    selectedOutputDeviceId = mutableState.value.selectedOutputDeviceId,
                    errorMessage = null
                )
            }
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                if (queueTracks.isNotEmpty()) {
                    updateStateFromPlayer()
                }
                val activePlayer = player
                delay(
                    if (activePlayer?.isPlaying == true) {
                        PLAYING_PROGRESS_UPDATE_INTERVAL_MS
                    } else {
                        IDLE_PROGRESS_UPDATE_INTERVAL_MS
                    }
                )
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun cancelPausedPlayerRelease() {
        pausedReleaseJob?.cancel()
        pausedReleaseJob = null
    }

    private fun ensurePausedPlayerReleasePolicy() {
        val currentTrack = mutableState.value.currentTrack
        val activePlayer = player
        val shouldScheduleRelease = shouldScheduleNavidromePausedPlayerRelease(
            currentTrack = currentTrack,
            hasActivePlayer = activePlayer != null,
            isPlaying = activePlayer?.isPlaying == true,
            playWhenReady = activePlayer?.playWhenReady == true,
            playbackState = activePlayer?.playbackState ?: Player.STATE_IDLE
        )
        if (!shouldScheduleRelease) {
            cancelPausedPlayerRelease()
            return
        }
        if (pausedReleaseJob?.isActive == true) return
        pausedReleaseJob = scope.launch {
            delay(PAUSED_PLAYER_RELEASE_DELAY_MS.toLong())
            val playerToRelease = player
            val state = mutableState.value
            val stillPaused = shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = state.currentTrack,
                hasActivePlayer = playerToRelease != null,
                isPlaying = playerToRelease?.isPlaying == true,
                playWhenReady = playerToRelease?.playWhenReady == true,
                playbackState = playerToRelease?.playbackState ?: Player.STATE_IDLE
            )
            if (!stillPaused) return@launch
            persistPlaybackSnapshot()
            releasePlayer(clearQueue = false)
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                isLoading = false
            )
            updatePlaybackSurface()
        }
    }

    private fun ensureProgressUpdates() {
        val activePlayer = player
        val shouldRun = appInForeground &&
            queueTracks.isNotEmpty() &&
            activePlayer != null &&
            (activePlayer.isPlaying || activePlayer.playbackState == Player.STATE_BUFFERING)
        if (shouldRun) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
        ensurePausedPlayerReleasePolicy()
    }

    private fun updateStateFromPlayer() {
        val activePlayer = player
        if (activePlayer == null) {
            mutableState.value = mutableState.value.copy(
                queue = queueTracks,
                queueDisplayMode = queueDisplayMode,
                recentTracks = recentTracks,
                isPlaying = false,
                isLoading = false,
                outputDevices = mutableState.value.outputDevices,
                selectedOutputDeviceId = mutableState.value.selectedOutputDeviceId
            )
            updatePlaybackSurface()
            ensureProgressUpdates()
            return
        }
        ensureNavidromeAudioEffects(activePlayer)
        val previousState = mutableState.value
        val fallbackIndex = previousState.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val currentIndex = activePlayer.currentMediaItemIndex
            .takeIf { it in queueTracks.indices }
            ?: fallbackIndex.takeIf { queueTracks.isNotEmpty() }
            ?: -1
        val currentTrack = queueTracks.getOrNull(currentIndex)
        if (currentTrack != null && currentTrack.id != lastRecordedTrackId) {
            recentTracks = buildList {
                add(currentTrack)
                addAll(
                    recentTracks.filterNot { it.id == currentTrack.id }
                        .take(MAX_RECENT_TRACKS - 1)
                )
            }
            lastRecordedTrackId = currentTrack.id
        } else if (currentTrack == null) {
            lastRecordedTrackId = null
        }
        val playbackState = activePlayer.playbackState
        val durationMs = resolvePlayerDurationMs(currentTrack)
        val positionMs = resolvePlayerPositionMs(durationMs)

        mutableState.value = previousState.copy(
            queue = queueTracks,
            queueDisplayMode = queueDisplayMode,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            recentTracks = recentTracks,
            outputDevices = previousState.outputDevices,
            selectedOutputDeviceId = previousState.selectedOutputDeviceId,
            isPlaying = activePlayer.isPlaying,
            isLoading = queueTracks.isNotEmpty() && playbackState == Player.STATE_BUFFERING,
            positionMs = positionMs,
            durationMs = durationMs,
            errorMessage = if (playbackState == Player.STATE_IDLE) {
                previousState.errorMessage
            } else {
                null
            }
        )
        if ((activePlayer.isPlaying || playbackState == Player.STATE_READY) && currentTrack?.id == playbackRecoveryTrackId) {
            playbackRecoveryTrackId = null
        }
        updatePlaybackSurface()
        ensureProgressUpdates()
    }

    private fun resolvePlayerDurationMs(
        currentTrack: NavidromeTrack? = queueTracks.getOrNull(player?.currentMediaItemIndex ?: mutableState.value.currentIndex)
    ): Int {
        val activePlayer = player
        val durationMs = activePlayer?.duration ?: C.TIME_UNSET
        return if (durationMs == C.TIME_UNSET || durationMs < 0L) {
            currentTrack?.let(::resolveTrackDurationMs) ?: mutableState.value.durationMs
        } else {
            durationMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun resolvePlayerPositionMs(durationMs: Int): Int {
        val activePlayer = player ?: return mutableState.value.positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
        val positionMs = activePlayer.currentPosition
        if (positionMs == C.TIME_UNSET || positionMs < 0L) return 0
        return positionMs
            .coerceAtLeast(0L)
            .coerceAtMost(durationMs.toLong().takeIf { durationMs > 0 } ?: positionMs)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun resolveTrackDurationMs(track: NavidromeTrack): Int =
        ((track.durationSeconds ?: 0) * 1000).coerceAtLeast(0)

    private fun restartOrStopRadio(currentTrack: NavidromeTrack) {
        val currentIndex = mutableState.value.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val activePlayer = player
        if (activePlayer != null && activePlayer.isPlaying) {
            releasePlayer(clearQueue = false)
            mutableState.value = mutableState.value.copy(
                queue = queueTracks,
                queueDisplayMode = queueDisplayMode,
                currentIndex = currentIndex,
                currentTrack = currentTrack,
                isPlaying = false,
                isLoading = false,
                positionMs = 0,
                durationMs = 0,
                errorMessage = null
            )
            persistPlaybackSnapshot()
            ensureProgressUpdates()
        } else {
            startPlaybackAt(index = currentIndex, positionMs = 0, playWhenReady = true)
        }
    }

    private fun NavidromeTrack?.isRadioTrack(): Boolean {
        return this?.id?.startsWith("radio:") == true
    }

    private fun updatePlaybackSurface() {
        val state = mutableState.value
        val currentTrack = state.currentTrack
        val keepMediaSessionActive = currentTrack != null
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .setState(
                when {
                    currentTrack == null -> PlaybackStateCompat.STATE_NONE
                    state.isLoading -> PlaybackStateCompat.STATE_BUFFERING
                    state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
                    else -> PlaybackStateCompat.STATE_PAUSED
                },
                state.positionMs.toLong(),
                if (state.isPlaying) 1f else 0f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
        mediaSession.isActive = keepMediaSessionActive

        if (currentTrack == null) {
            clearPlaybackSurface()
            return
        }

        maybeLoadArtwork(currentTrack)
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrack.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack.artistName)
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM,
                    if (currentTrack.isRadioTrack()) "Internet Radio" else currentTrack.albumName
                )
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationMs.toLong())
                .apply {
                    artworkBitmap?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                    }
                }
                .build()
        )

        showPlaybackNotification(state, currentTrack)
    }

    private fun clearPlaybackSurface() {
        cancelPausedPlayerRelease()
        artworkJob?.cancel()
        artworkJob = null
        artworkBitmap = null
        artworkTrackId = null
        lastNotificationSignature = null
        mediaSession.setMetadata(null)
        mediaSession.isActive = false
        PlaybackServiceController.stop(appContext)
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun observeEqualizerPreferences() {
        scope.launch {
            sessionPreferences.state.collect { preferences ->
                navidromeEqualizerEnabled = preferences.navidromeEqualizerEnabled
                navidromeEqualizerProfiles = preferences.navidromeEqualizerProfiles
                navidromeActiveEqualizerProfileId = preferences.navidromeEqualizerActiveProfileId
                    ?.takeIf { activeId -> navidromeEqualizerProfiles.any { it.id == activeId } }
                navidromeEqualizerPreampLevel = preferences.navidromeEqualizerPreampLevel

                val activePlayer = player
                if (activePlayer != null) {
                    ensureNavidromeAudioEffects(activePlayer)
                } else {
                    releaseNavidromeAudioEffects()
                }
            }
        }
    }

    private fun ensureNavidromeAudioEffects(player: ExoPlayer) {
        if (!shouldApplyNavidromeAudioEffects()) {
            releaseNavidromeAudioEffects()
            return
        }
        val sessionId = runCatching { player.audioSessionId }.getOrDefault(C.AUDIO_SESSION_ID_UNSET)
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        if (navidromeEqualizerSessionId != sessionId) {
            releaseNavidromeAudioEffects()
            navidromeEqualizerSessionId = sessionId
            navidromeEqualizer = runCatching {
                Equalizer(0, sessionId).apply { enabled = false }
            }.getOrNull()
            navidromePreamp = runCatching {
                LoudnessEnhancer(sessionId).apply { enabled = false }
            }.getOrNull()
        }
        applyNavidromeAudioEffects()
    }

    private fun applyNavidromeAudioEffects() {
        if (!shouldApplyNavidromeAudioEffects()) {
            releaseNavidromeAudioEffects()
            return
        }
        applyNavidromeEqualizer()
        applyNavidromePreamp()
    }

    private fun applyNavidromeEqualizer() {
        val effect = navidromeEqualizer ?: return
        val profile = navidromeEqualizerProfiles.firstOrNull { it.id == navidromeActiveEqualizerProfileId }
        if (!shouldApplyNavidromeEqualizer() || profile == null) {
            runCatching { effect.enabled = false }
            return
        }
        val desiredLevels = profile.effectiveBandLevelsDb()

        val bandCount = runCatching { effect.numberOfBands.toInt() }.getOrDefault(0)
        val levelRange = runCatching { effect.bandLevelRange }.getOrNull()
        val minLevel = levelRange?.getOrNull(0)?.toInt() ?: -1500
        val maxLevel = levelRange?.getOrNull(1)?.toInt() ?: 1500

        for (band in 0 until bandCount) {
            val centerFrequencyMilliHz = runCatching {
                effect.getCenterFreq(band.toShort())
            }.getOrDefault(navidromeEqualizerBandFrequenciesHz.getOrElse(band) { 1_000 } * 1000)
            val targetLevelMillibels = (resolveNavidromeEqualizerBandLevelDb(
                effectCenterFrequencyMilliHz = centerFrequencyMilliHz,
                desiredLevels = desiredLevels
            ) * 100f).toInt().coerceIn(minLevel, maxLevel)
            runCatching {
                effect.setBandLevel(band.toShort(), targetLevelMillibels.toShort())
            }
        }
        runCatching { effect.enabled = true }
    }

    private fun shouldApplyNavidromeEqualizer(): Boolean {
        if (!navidromeEqualizerEnabled) return false
        val profile = navidromeEqualizerProfiles.firstOrNull { it.id == navidromeActiveEqualizerProfileId } ?: return false
        return !profile.isFlat()
    }

    private fun shouldApplyNavidromePreamp(): Boolean {
        return navidromeEqualizerEnabled && navidromeEqualizerPreampLevel > 0f
    }

    private fun shouldApplyNavidromeAudioEffects(): Boolean {
        return shouldApplyNavidromeEqualizer() || shouldApplyNavidromePreamp()
    }

    private fun applyNavidromePreamp() {
        val effect = navidromePreamp ?: return
        val targetGainMb = if (shouldApplyNavidromePreamp()) {
            (navidromeEqualizerPreampLevel.coerceIn(0f, 1f) * NAVIDROME_PREAMP_MAX_GAIN_MB)
                .toInt()
                .coerceIn(0, NAVIDROME_PREAMP_MAX_GAIN_MB)
        } else {
            0
        }
        runCatching {
            effect.setTargetGain(targetGainMb)
            effect.enabled = targetGainMb > 0
        }
    }

    private fun releaseNavidromeAudioEffects() {
        runCatching { navidromeEqualizer?.release() }
        runCatching { navidromePreamp?.release() }
        navidromeEqualizer = null
        navidromePreamp = null
        navidromeEqualizerSessionId = null
    }

    private fun showPlaybackNotification(
        state: NavidromePlayerState,
        track: NavidromeTrack
    ) {
        val notificationSignature = NotificationSignature(
            trackId = track.id,
            title = track.title,
            artist = track.artistName,
            album = if (track.isRadioTrack()) "Internet Radio" else track.albumName,
            isPlaying = state.isPlaying,
            hasArtwork = artworkBitmap != null
        )
        if (
            notificationSignature == lastNotificationSignature &&
            PlaybackServiceController.isActive()
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            31,
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val previousIntent = PendingIntent.getBroadcast(
            appContext,
            32,
            Intent(appContext, PlaybackActionReceiver::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            appContext,
            33,
            Intent(appContext, PlaybackActionReceiver::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            appContext,
            34,
            Intent(appContext, PlaybackActionReceiver::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            .setContentTitle(track.title)
            .setContentText(track.artistName)
            .setSubText(if (track.isRadioTrack()) "Internet Radio" else track.albumName)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLargeIcon(artworkBitmap)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        runCatching {
            PlaybackServiceController.startOrUpdate(appContext, notification)
        }.onSuccess {
            lastNotificationSignature = notificationSignature
        }
    }

    private fun maybeLoadArtwork(track: NavidromeTrack) {
        val coverUrl = track.coverUrl.orEmpty()
        if (coverUrl.isBlank()) {
            artworkJob?.cancel()
            artworkJob = null
            artworkTrackId = track.id
            artworkBitmap = null
            return
        }
        if (artworkTrackId == track.id && artworkBitmap != null) return
        if (artworkJob?.isActive == true && artworkTrackId == track.id) return

        artworkTrackId = track.id
        artworkJob?.cancel()
        artworkJob = scope.launch(Dispatchers.IO) {
            val payload = runCatching {
                val request = ImageRequest.Builder(appContext)
                    .data(coverUrl)
                    .allowHardware(false)
                    .build()
                MediaArtworkPayload(
                    trackId = track.id,
                    bitmap = appContext.imageLoader.execute(request).drawable?.toBitmap()
                )
            }.getOrDefault(MediaArtworkPayload(track.id, null))
            scope.launch(Dispatchers.Main.immediate) {
                if (artworkTrackId != payload.trackId) return@launch
                artworkBitmap = payload.bitmap
                updatePlaybackSurface()
            }
        }
    }

    private fun NavidromeTrack.toJson(): JSONObject {
        return serializeNavidromeTrackSnapshot(this)
    }

    private fun parsePlaybackSnapshot(payload: String): NavidromePlaybackSnapshot? {
        return runCatching {
            val root = JSONObject(payload)
            val queue = root.optJSONArray("queue").orEmptyTracks()
            if (queue.isEmpty()) return null
            val currentIndex = root.optInt("currentIndex", 0).coerceIn(0, queue.lastIndex)
            val recent = root.optJSONArray("recentTracks").orEmptyTracks()
            NavidromePlaybackSnapshot(
                queue = queue,
                recentTracks = recent,
                queueDisplayMode = root.optString("queueDisplayMode")
                    .takeIf { it.isNotBlank() }
                    ?.let { mode ->
                        runCatching { NavidromeQueueDisplayMode.valueOf(mode) }.getOrNull()
                    }
                    ?: NavidromeQueueDisplayMode.FULL,
                currentIndex = currentIndex,
                positionMs = root.optInt("positionMs", 0).coerceAtLeast(0)
            )
        }.getOrNull()
    }

    private fun JSONArray?.orEmptyTracks(): List<NavidromeTrack> {
        if (this == null) return emptyList()
        return buildList {
            repeat(length()) { index ->
                val item = optJSONObject(index) ?: return@repeat
                parseNavidromeTrackSnapshot(item)?.let(::add)
            }
        }
    }

    private fun refreshAudioOutputDevices(reason: OutputRefreshReason) {
        val available = queryOutputDevices()
        val availableIds = available.mapNotNull { it.id }.toSet()
        val bluetoothOutputId = available.firstOrNull { output ->
            val displayedId = output.id ?: return@firstOrNull false
            outputRouteKeyByDisplayedId[displayedId]?.startsWith("bt:") == true
        }?.id
        val wiredOutputId = available.firstOrNull { output ->
            val displayedId = output.id ?: return@firstOrNull false
            isAutoPreferredWiredRoute(outputRouteKeyByDisplayedId[displayedId])
        }?.id
        val wiredAutoOutputId = available.firstOrNull { output ->
            val displayedId = output.id ?: return@firstOrNull false
            isAutoPreferredWiredRoute(outputRouteKeyByDisplayedId[displayedId]) &&
                displayedId !in lastKnownOutputDeviceIds
        }?.id
        val validPreferredId = preferredOutputDeviceId?.takeIf { preferredId ->
            available.any { it.id == preferredId }
        }
        val shouldFollowSystemRoute = !hasExplicitOutputSelection || validPreferredId == null
        preferredOutputDeviceId = when {
            hasExplicitOutputSelection && validPreferredId != null -> validPreferredId
            else -> null
        }
        val displayedSelectionId = when {
            preferredOutputDeviceId != null -> preferredOutputDeviceId
            reason == OutputRefreshReason.DeviceAdded && wiredAutoOutputId != null -> wiredAutoOutputId
            shouldFollowSystemRoute && wiredOutputId != null -> wiredOutputId
            bluetoothOutputId != null && bluetoothOutputId in availableIds -> bluetoothOutputId
            else -> available.firstOrNull()?.id
        }
        val shouldSkipRoutingApply = isNavidromeOutputSwitchInFlight(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            suppressRefreshRoutingUntilElapsedMs = suppressRefreshRoutingUntilElapsedMs
        )
        player?.let { activePlayer ->
            if (!shouldSkipRoutingApply) {
                if (shouldFollowSystemRoute) {
                    applySystemDefaultOutputRouting(activePlayer)
                } else {
                    applyPreferredOutputDevice(activePlayer)
                }
            }
        }
        mutableState.value = mutableState.value.copy(
            outputDevices = available,
            selectedOutputDeviceId = displayedSelectionId
        )
        lastKnownOutputDeviceIds = availableIds
    }

    private fun queryOutputDevices(): List<NavidromeOutputDevice> {
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val groupedCandidates = rawDevices
            .asSequence()
            .filter { device -> isMainOutputType(device.type) }
            .mapNotNull { device ->
                val routeKey = outputRouteKey(device) ?: return@mapNotNull null
                OutputRouteCandidate(
                    routeKey = routeKey,
                    priority = outputRoutePriority(device.type),
                    device = device
                )
            }
            .groupBy { it.routeKey }
            .mapValues { (_, candidates) -> candidates.sortedByDescending { it.priority } }
            .values
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
            NavidromeOutputDevice(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

    private fun applyPreferredOutputDevice(player: ExoPlayer) {
        val preferredId = preferredOutputDeviceId
        if (preferredId == null) {
            applySystemDefaultOutputRouting(player)
            return
        }
        val preferredApplied = applyPreferredOutputForDisplayedId(player, preferredId)
        if (!preferredApplied) {
            applyOutputViaAudioManagerFallback(preferredId)
        }
    }

    private fun applySystemDefaultOutputRouting(player: ExoPlayer): Boolean {
        val preferredCleared = runCatching {
            player.setPreferredAudioDevice(null)
            true
        }.getOrDefault(false)
        val communicationCleared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    private fun isSpeakerOutputDevice(displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        return candidates.firstOrNull()?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
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
                routeKey?.let { key ->
                    currentOutputs.filter { output -> outputRouteKey(output) == key }
                        .sortedByDescending { output -> outputRoutePriority(output.type) }
                }.orEmpty()
            }
    }

    private fun applyPreferredOutputForDisplayedId(player: ExoPlayer, displayedDeviceId: Int): Boolean {
        val candidates = resolveOutputCandidatesForDisplayedId(displayedDeviceId)
        return candidates.any { targetDevice ->
            runCatching {
                player.setPreferredAudioDevice(targetDevice)
                true
            }.getOrDefault(false)
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
        }
        runCatching { player.volume = 0f }
        val applied = block()
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
            if (this@NavidromePlayerController.player !== player) {
                outputRecoveryJob = null
                return@launch
            }
            if (shouldResumePlayback) {
                runCatching { player.play() }
            }
            if (toSpeakerRoute) {
                val targetVolume = volume.coerceAtLeast(0f)
                repeat(SPEAKER_OUTPUT_VOLUME_RAMP_STEPS) { step ->
                    if (this@NavidromePlayerController.player !== player) {
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

    private fun invalidatePendingPlaybackRestore() {
        restorePlaybackGeneration += 1L
    }

    private fun clearPreferredOutputOverride(player: ExoPlayer): Boolean {
        return runCatching {
            player.setPreferredAudioDevice(null)
            true
        }.getOrDefault(false)
    }

    private fun clearCommunicationRouteOverride(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching {
            audioManager.clearCommunicationDevice()
            true
        }.getOrDefault(false)
    }

    private fun clearSpeakerRouteOverride(player: ExoPlayer): Boolean {
        val preferredCleared = clearPreferredOutputOverride(player)
        val communicationCleared = clearCommunicationRouteOverride()
        val speakerReset = runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            true
        }.getOrDefault(false)
        return preferredCleared || communicationCleared || speakerReset
    }

    private fun prepareForSpeakerPreferredRouting(player: ExoPlayer): Boolean {
        val preferredCleared = clearPreferredOutputOverride(player)
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
        val speakerRoute = candidates.first().type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        if (speakerRoute) {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                }
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
                true
            }.getOrDefault(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val communicationApplied = candidates.any { candidate ->
                runCatching { audioManager.setCommunicationDevice(candidate) }.getOrDefault(false)
            }
            if (communicationApplied) return true
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
        return false
    }
}

internal fun isAutoPreferredWiredRoute(routeKey: String?): Boolean {
    return when (routeKey) {
        "wired", "usb", "hdmi", "dock", "line" -> true
        else -> false
    }
}
