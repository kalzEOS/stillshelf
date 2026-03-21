package com.stillshelf.app.playback.navidrome

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import com.stillshelf.app.MainActivity
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeOutputDevice
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeTrack
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

@Singleton
class NavidromePlayerController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences,
    private val downloadManager: NavidromeDownloadManager
) {
    private companion object {
        const val MAX_RECENT_TRACKS = 7
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
    private var recentTracks: List<NavidromeTrack> = emptyList()
    private var lastRecordedTrackId: String? = null
    private var appInForeground = false
    private var preferredOutputDeviceId: Int? = null
    private var outputRouteDeviceIdsByRouteKey: Map<String, List<Int>> = emptyMap()
    private var outputRouteKeyByDisplayedId: Map<Int, String> = emptyMap()
    private var lastKnownOutputDeviceIds: Set<Int> = emptySet()
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSession = MediaSessionCompat(appContext, "StillShelfNavidromePlayback")
    private var artworkBitmap: Bitmap? = null
    private var artworkTrackId: String? = null
    private var artworkJob: Job? = null
    private var lastNotificationSignature: NotificationSignature? = null

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
            }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        return createPlayer().also { created ->
            player = created
            applyPreferredOutputDevice(created)
        }
    }

    fun refreshAudioOutputs() {
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
    }

    fun selectAudioOutputDevice(deviceId: Int?): Boolean {
        val available = queryOutputDevices()
        if (available.none { output -> output.id == deviceId }) {
            refreshAudioOutputs()
            return false
        }
        preferredOutputDeviceId = deviceId
        val activePlayer = player
        if (activePlayer == null) {
            refreshAudioOutputs()
            return true
        }
        val speakerTarget = deviceId?.let(::isSpeakerOutputDevice) == true
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
        startIndex: Int
    ) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        queueTracks = tracks
        mutableState.value = mutableState.value.copy(
            queue = tracks,
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
        val activePlayer = player
        if (activePlayer == null) {
            val nextIndex = (mutableState.value.currentIndex + 1).takeIf { it in queueTracks.indices } ?: return
            startPlaybackAt(index = nextIndex, positionMs = 0, playWhenReady = true)
            return
        }
        if (!activePlayer.hasNextMediaItem()) return
        activePlayer.seekToNextMediaItem()
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun playPrevious() {
        val activePlayer = player
        if (activePlayer == null) {
            val previousIndex = (mutableState.value.currentIndex - 1).takeIf { it in queueTracks.indices } ?: return
            startPlaybackAt(index = previousIndex, positionMs = 0, playWhenReady = true)
            return
        }
        if (!activePlayer.hasPreviousMediaItem()) return
        activePlayer.seekToPreviousMediaItem()
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun playQueueIndex(index: Int) {
        if (index !in queueTracks.indices) return
        val activePlayer = player
        if (activePlayer == null) {
            startPlaybackAt(index = index, positionMs = 0, playWhenReady = true)
            return
        }
        activePlayer.seekTo(index, 0L)
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot()
        ensureProgressUpdates()
    }

    fun stop() {
        queueTracks = emptyList()
        lastRecordedTrackId = null
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

    private fun startPlaybackAt(index: Int, positionMs: Int, playWhenReady: Boolean) {
        if (queueTracks.isEmpty()) return
        val safeIndex = index.coerceIn(0, queueTracks.lastIndex)
        val safePositionMs = positionMs.coerceAtLeast(0)
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

    private fun resolvePlaybackUri(track: NavidromeTrack): String {
        return downloadManager.localPlaybackUri(track) ?: track.streamUrl
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

    private fun releasePlayer(clearQueue: Boolean) {
        val activePlayer = player
        if (activePlayer != null) {
            stopProgressUpdates()
            cancelOutputRecovery()
            runCatching { activePlayer.removeListener(playerListener) }
            runCatching { activePlayer.release() }
            player = null
        }
        if (clearQueue) {
            queueTracks = emptyList()
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
            .put(
                "recentTracks",
                JSONArray().apply {
                    recentTracks.forEach { put(it.toJson()) }
                }
            )
            .toString()
        scope.launch(Dispatchers.IO) {
            sessionPreferences.setCachedNavidromePlayback(
                payload = payload,
                savedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun restorePlaybackSnapshot() {
        scope.launch(Dispatchers.IO) {
            val snapshot = sessionPreferences.getCachedNavidromePlayback()
                ?.payload
                ?.let(::parsePlaybackSnapshot)
                ?: return@launch
            scope.launch(Dispatchers.Main.immediate) {
                queueTracks = snapshot.queue
                recentTracks = snapshot.recentTracks
                val currentTrack = snapshot.queue.getOrNull(snapshot.currentIndex)
                lastRecordedTrackId = currentTrack?.id
                mutableState.value = mutableState.value.copy(
                    queue = snapshot.queue,
                    currentIndex = snapshot.currentIndex,
                    currentTrack = currentTrack,
                    recentTracks = snapshot.recentTracks,
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
                delay(500)
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
        val shouldScheduleRelease = currentTrack != null &&
            !currentTrack.isRadioTrack() &&
            activePlayer != null &&
            !activePlayer.isPlaying &&
            activePlayer.playbackState != Player.STATE_BUFFERING
        if (!shouldScheduleRelease) {
            cancelPausedPlayerRelease()
            return
        }
        if (pausedReleaseJob?.isActive == true) return
        pausedReleaseJob = scope.launch {
            delay(PAUSED_PLAYER_RELEASE_DELAY_MS.toLong())
            val playerToRelease = player
            val state = mutableState.value
            val trackToRelease = state.currentTrack
            val stillPaused = playerToRelease != null &&
                trackToRelease != null &&
                !trackToRelease.isRadioTrack() &&
                !playerToRelease.isPlaying &&
                playerToRelease.playbackState != Player.STATE_BUFFERING
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
        val currentIndex = activePlayer.currentMediaItemIndex
            .takeIf { it in queueTracks.indices }
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

        mutableState.value = mutableState.value.copy(
            queue = queueTracks,
            currentIndex = currentIndex,
            currentTrack = currentTrack,
            recentTracks = recentTracks,
            outputDevices = mutableState.value.outputDevices,
            selectedOutputDeviceId = mutableState.value.selectedOutputDeviceId,
            isPlaying = activePlayer.isPlaying,
            isLoading = currentTrack != null && playbackState == Player.STATE_BUFFERING,
            positionMs = positionMs,
            durationMs = durationMs,
            errorMessage = if (playbackState == Player.STATE_IDLE) {
                mutableState.value.errorMessage
            } else {
                null
            }
        )
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
        if (notificationSignature == lastNotificationSignature) return

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
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artistName", artistName)
            .put("albumName", albumName)
            .apply {
                albumId?.let { put("albumId", it) }
                artistId?.let { put("artistId", it) }
                trackNumber?.let { put("trackNumber", it) }
                durationSeconds?.let { put("durationSeconds", it) }
                coverUrl?.let { put("coverUrl", it) }
                put("streamUrl", streamUrl)
                formatLabel?.let { put("formatLabel", it) }
                bitRateKbps?.let { put("bitRateKbps", it) }
            }
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
                val id = item.optString("id").trim()
                val streamUrl = item.optString("streamUrl").trim()
                if (id.isBlank() || streamUrl.isBlank()) return@repeat
                add(
                    NavidromeTrack(
                        id = id,
                        title = item.optString("title").ifBlank { "Unknown track" },
                        artistName = item.optString("artistName").ifBlank { "Unknown artist" },
                        albumName = item.optString("albumName").ifBlank { "Unknown album" },
                        albumId = item.optString("albumId").ifBlank { null },
                        artistId = item.optString("artistId").ifBlank { null },
                        trackNumber = item.takeIf { it.has("trackNumber") }?.optInt("trackNumber")?.takeIf { it > 0 },
                        durationSeconds = item.takeIf { it.has("durationSeconds") }?.optInt("durationSeconds")?.takeIf { it > 0 },
                        coverUrl = item.optString("coverUrl").ifBlank { null },
                        streamUrl = streamUrl,
                        formatLabel = item.optString("formatLabel").ifBlank { null },
                        bitRateKbps = item.takeIf { it.has("bitRateKbps") }?.optInt("bitRateKbps")?.takeIf { it > 0 }
                    )
                )
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
        val shouldAutoSwitchToBluetooth = reason == OutputRefreshReason.DeviceAdded &&
            bluetoothOutputId != null &&
            bluetoothOutputId !in lastKnownOutputDeviceIds
        val validPreferredId = preferredOutputDeviceId?.takeIf { preferredId ->
            available.any { it.id == preferredId }
        }
        preferredOutputDeviceId = when {
            shouldAutoSwitchToBluetooth -> bluetoothOutputId
            validPreferredId != null -> validPreferredId
            else -> available.firstOrNull()?.id
        }
        player?.let(::applyPreferredOutputDevice)
        mutableState.value = mutableState.value.copy(
            outputDevices = available,
            selectedOutputDeviceId = preferredOutputDeviceId
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
