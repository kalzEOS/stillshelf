package com.stillshelf.app.playback.navidrome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.stillshelf.app.core.diagnostics.DiagnosticLogManager
import coil.imageLoader
import coil.request.ImageRequest
import com.stillshelf.app.MainActivity
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.NavidromeOutputDevice
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeQueueDisplayMode
import com.stillshelf.app.core.model.NavidromeCacheSizeOption
import com.stillshelf.app.core.model.NavidromeTrack
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal const val NAVIDROME_PLAYBACK_MEDIA_SCHEME = "stillshelf-navidrome"
internal const val NAVIDROME_PLAYBACK_MEDIA_AUTHORITY = "playback"
internal const val NAVIDROME_PLAYBACK_TRACK_ID_QUERY_PARAMETER = "trackId"
internal const val NAVIDROME_PLAYBACK_STREAM_URL_QUERY_PARAMETER = "streamUrl"
internal const val NAVIDROME_PLAYBACK_WARMUP_TRACK_LIMIT = 20

internal fun normalizeNavidromePlaybackWarmupTracks(tracks: List<NavidromeTrack>): List<NavidromeTrack> {
    return tracks
        .distinctBy { it.id }
        .filter {
            it.id.isNotBlank() &&
                it.streamUrl.isNotBlank() &&
                !it.id.startsWith("radio:")
        }
}

internal fun buildNavidromePlaybackWarmupSignature(tracks: List<NavidromeTrack>): String {
    return tracks.joinToString(separator = "|") { it.id }
}

internal fun selectNavidromePlaybackWarmupTracks(
    tracks: List<NavidromeTrack>,
    currentIndex: Int,
    trackLimit: Int = NAVIDROME_PLAYBACK_WARMUP_TRACK_LIMIT
): List<NavidromeTrack> {
    if (tracks.isEmpty() || trackLimit <= 0) return emptyList()
    val startIndex = currentIndex.coerceIn(0, tracks.lastIndex)
    return tracks.drop(startIndex).take(trackLimit)
}

internal fun buildNavidromePlaybackMediaUri(trackId: String, streamUrl: String): Uri {
    return Uri.parse(
        buildString {
            append(NAVIDROME_PLAYBACK_MEDIA_SCHEME)
            append("://")
            append(NAVIDROME_PLAYBACK_MEDIA_AUTHORITY)
            append("?")
            append(NAVIDROME_PLAYBACK_TRACK_ID_QUERY_PARAMETER)
            append("=")
            append(Uri.encode(trackId))
            append("&")
            append(NAVIDROME_PLAYBACK_STREAM_URL_QUERY_PARAMETER)
            append("=")
            append(Uri.encode(streamUrl))
        }
    )
}

internal fun chooseNavidromePlaybackUri(
    streamUrl: String,
    localPlaybackUri: String?,
    forceRemote: Boolean
): String {
    return if (!forceRemote && !localPlaybackUri.isNullOrBlank()) {
        localPlaybackUri
    } else {
        streamUrl
    }
}

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

internal fun isNavidromeOutputSwitchInFlight(
    nowElapsedMs: Long,
    suppressRefreshRoutingUntilElapsedMs: Long
): Boolean {
    return nowElapsedMs < suppressRefreshRoutingUntilElapsedMs
}

internal fun shouldPersistNavidromePlaybackCheckpoint(
    currentTrackId: String?,
    previousTrackId: String?,
    currentPositionMs: Int,
    previousPositionMs: Int,
    elapsedSinceLastPersistMs: Long,
    isNearStartAfterRewind: Boolean = false
): Boolean {
    if (currentTrackId.isNullOrBlank()) return false
    if (currentTrackId != previousTrackId) return true
    if (isNearStartAfterRewind) return true
    if (currentPositionMs <= 0) return false
    if (elapsedSinceLastPersistMs < 15_000L) return false
    return kotlin.math.abs(currentPositionMs - previousPositionMs) >= 10_000
}

internal data class NavidromeQueueRemovalResult(
    val queue: List<NavidromeTrack>,
    val currentIndex: Int
)

internal fun resolveNavidromeCurrentQueueIndex(
    queue: List<NavidromeTrack>,
    playerIndex: Int?,
    stateIndex: Int?,
    currentTrackId: String?
): Int {
    playerIndex?.takeIf { it in queue.indices }?.let { return it }
    stateIndex?.takeIf { it in queue.indices }?.let { return it }
    currentTrackId
        ?.takeIf { it.isNotBlank() }
        ?.let { trackId ->
            val matchingIndex = queue.indexOfFirst { it.id == trackId }
            if (matchingIndex >= 0 && queue.count { it.id == trackId } == 1) {
                matchingIndex
            } else {
                null
            }
        }
        ?.let { return it }
    return -1
}

internal fun removeNavidromeTrackFromQueue(
    queue: List<NavidromeTrack>,
    currentIndex: Int,
    removeIndex: Int
): NavidromeQueueRemovalResult? {
    if (queue.isEmpty()) return null
    if (currentIndex !in queue.indices) return null
    if (removeIndex !in queue.indices) return null
    if (removeIndex == currentIndex) return null

    val updatedQueue = queue.toMutableList().apply {
        removeAt(removeIndex)
    }
    val adjustedCurrentIndex = if (removeIndex < currentIndex) currentIndex - 1 else currentIndex
    return NavidromeQueueRemovalResult(
        queue = updatedQueue,
        currentIndex = adjustedCurrentIndex.coerceIn(0, updatedQueue.lastIndex)
    )
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
    val bitRateKbps: Int?,
    val sizeBytes: Long? = null
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
        bitRateKbps = bitRateKbps,
        sizeBytes = sizeBytes
    )
}

internal fun NavidromeTrackSnapshotPayload.toTrack(): NavidromeTrack? {
    if (id.isBlank()) return null
    return NavidromeTrack(
        id = id,
        title = title.normalizeNavidromeText().ifBlank { "Unknown track" },
        artistName = artistName.normalizeNavidromeText().ifBlank { "Unknown artist" },
        albumName = albumName.normalizeNavidromeText().ifBlank { "Unknown album" },
        albumId = albumId?.takeIf { it.isNotBlank() },
        artistId = artistId?.takeIf { it.isNotBlank() },
        trackNumber = trackNumber?.takeIf { it > 0 },
        durationSeconds = durationSeconds?.takeIf { it > 0 },
        coverUrl = coverUrl?.takeIf { it.isNotBlank() },
        streamUrl = streamUrl.trim(),
        formatLabel = formatLabel?.takeIf { it.isNotBlank() },
        bitRateKbps = bitRateKbps?.takeIf { it > 0 },
        sizeBytes = sizeBytes?.takeIf { it > 0L }
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
            payload.sizeBytes?.let { put("sizeBytes", it) }
        }
}

internal fun parseNavidromeTrackSnapshot(item: JSONObject): NavidromeTrack? {
    return NavidromeTrackSnapshotPayload(
        id = item.optString("id").trim(),
        title = item.optString("title").normalizeNavidromeText(),
        artistName = item.optString("artistName").normalizeNavidromeText(),
        albumName = item.optString("albumName").normalizeNavidromeText(),
        albumId = item.optString("albumId").ifBlank { null },
        artistId = item.optString("artistId").ifBlank { null },
        trackNumber = item.takeIf { it.has("trackNumber") }?.optInt("trackNumber"),
        durationSeconds = item.takeIf { it.has("durationSeconds") }?.optInt("durationSeconds"),
        coverUrl = item.optString("coverUrl").ifBlank { null },
        streamUrl = item.optString("streamUrl").trim(),
        formatLabel = item.optString("formatLabel").ifBlank { null },
        bitRateKbps = item.takeIf { it.has("bitRateKbps") }?.optInt("bitRateKbps"),
        sizeBytes = item.takeIf { it.has("sizeBytes") }?.optLong("sizeBytes")
    ).toTrack()
}

private fun String.normalizeNavidromeText(): String {
    return trim()
        .replace("Â’", "'")
        .replace("Â'", "'")
        .replace("â€™", "'")
        .replace("â€˜", "'")
        .replace("â€œ", "\"")
        .replace("â€�", "\"")
        .replace("Â\"", "\"")
        .replace('\u0091', '\'')
        .replace('\u0092', '\'')
        .replace('\u0093', '"')
        .replace('\u0094', '"')
        .replace(Regex("(?<=[\\p{L}\\p{N}])\uFFFD(?=[\\p{L}\\p{N}])"), "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Singleton
class NavidromePlayerController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences,
    private val downloadManager: NavidromeDownloadManager,
    private val navidromeRepository: NavidromeRepository,
    private val diagnosticLogManager: DiagnosticLogManager
) {
    private companion object {
        const val TAG = "NavidromePlayerController"
        const val MAX_RECENT_TRACKS = 7
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000
        const val PLAYING_PROGRESS_UPDATE_INTERVAL_MS = 80L
        const val BACKGROUND_PLAYING_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
        const val IDLE_PROGRESS_UPDATE_INTERVAL_MS = 250L
        const val CHANNEL_ID = "stillshelf_playback_v4"
        const val CHANNEL_NAME = "Playback"
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

    private data class NavidromeEqualizerSettingsSnapshot(
        val enabled: Boolean,
        val bandLevelsDb: List<Float>
    )

    private val mutableState = MutableStateFlow(NavidromePlayerState())
    val state: StateFlow<NavidromePlayerState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var pausedReleaseJob: Job? = null
    private var outputRecoveryJob: Job? = null
    private var playbackCacheWarmupJob: Job? = null
    @Volatile private var queueTracks: List<NavidromeTrack> = emptyList()
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
    @Volatile private var forcedRemoteTrackIds: Set<String> = emptySet()
    private var playbackRecoveryTrackId: String? = null
    private var restorePlaybackGeneration: Long = 0L
    private var lastPersistedSnapshotTrackId: String? = null
    private var lastPersistedSnapshotPositionMs: Int = 0
    private var lastPersistedSnapshotElapsedMs: Long = 0L
    @Volatile private var playbackCacheWarmupSignature: String? = null
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSession = MediaSessionCompat(appContext, "StillShelfNavidromePlayback")
    private var artworkBitmap: Bitmap? = null
    private var artworkTrackId: String? = null
    private var artworkJob: Job? = null
    private var lastNotificationSignature: NotificationSignature? = null
    private val navidromeEqualizerAudioProcessor = NavidromeEqualizerAudioProcessor()
    private var lastNavidromeEqualizerSettings = NavidromeEqualizerSettingsSnapshot(
        enabled = false,
        bandLevelsDb = emptyList()
    )

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
            logPlaybackTrace(
                "playback_state_changed state=${describePlaybackState(playbackState)} " +
                    "is_playing=${runCatching { player?.isPlaying }.getOrDefault(false)} " +
                    "play_when_ready=${runCatching { player?.playWhenReady }.getOrDefault(false)} " +
                    "current_index=${mutableState.value.currentIndex} queue_size=${queueTracks.size} " +
                    "position_ms=${mutableState.value.positionMs}"
            )
            updateStateFromPlayer()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            logPlaybackTrace(
                "playback_flag_changed is_playing=$isPlaying state=${describePlaybackState(player?.playbackState ?: Player.STATE_IDLE)} " +
                    "current_index=${mutableState.value.currentIndex} position_ms=${mutableState.value.positionMs}"
            )
            updateStateFromPlayer()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            diagnosticLogManager.logPlaybackError(
                tag = TAG,
                errorType = error::class.java.simpleName,
                throwable = error
            )
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

    private var player: ExoPlayer? = null

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appInForeground = true
            updateStateFromPlayer()
            ensureProgressUpdates()
        }

        override fun onStop(owner: LifecycleOwner) {
            appInForeground = false
            persistPlaybackSnapshot(force = true)
            ensureProgressUpdates()
            ensurePausedPlayerReleasePolicy()
        }
    }

    init {
        appInForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        createNotificationChannel()
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
        observeActiveConnectionChanges()
        refreshAudioOutputDevices(reason = OutputRefreshReason.General)
        restorePlaybackSnapshot()
        ensureProgressUpdates()
    }

    private fun createPlayer(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf(navidromeEqualizerAudioProcessor))
                    .build()
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(createNavidromePlaybackDataSourceFactory())
        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(playbackAudioAttributes, true)
                addListener(playerListener)
            }
    }

    private fun createNavidromePlaybackDataSourceFactory(): ResolvingDataSource.Factory {
        return ResolvingDataSource.Factory(
            DefaultDataSource.Factory(appContext),
            ResolvingDataSource.Resolver { dataSpec ->
                resolveNavidromePlaybackDataSpec(dataSpec)
            }
        )
    }

    private fun resolveNavidromePlaybackDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != NAVIDROME_PLAYBACK_MEDIA_SCHEME) {
            return dataSpec
        }
        val trackId = uri.getQueryParameter(NAVIDROME_PLAYBACK_TRACK_ID_QUERY_PARAMETER).orEmpty()
        val embeddedStreamUrl = uri.getQueryParameter(NAVIDROME_PLAYBACK_STREAM_URL_QUERY_PARAMETER).orEmpty()
        val track = queueTracks.firstOrNull { it.id == trackId }
        val forcedRemoteSnapshot = forcedRemoteTrackIds
        val resolvedUri = if (track != null) {
            val forceRemote = track.id in forcedRemoteSnapshot
            Uri.parse(
                chooseNavidromePlaybackUri(
                    streamUrl = track.streamUrl,
                    localPlaybackUri = if (forceRemote) null else downloadManager.localPlaybackUri(track),
                    forceRemote = forceRemote
                )
            )
        } else if (embeddedStreamUrl.isNotBlank()) {
            Uri.parse(embeddedStreamUrl)
        } else {
            uri
        }
        return dataSpec.buildUpon().setUri(resolvedUri).build()
    }

    private fun buildNavidromePlaybackMediaItem(track: NavidromeTrack): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(buildNavidromePlaybackMediaUri(track.id, track.streamUrl))
            .build()
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
        logPlaybackTrace(
            "audio_output_select requested=${deviceId != null} available_count=${available.size}"
        )
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
        logPlaybackTrace(
            "playback_command=play_tracks queue_size=${tracks.size} start_index=${startIndex.coerceIn(0, tracks.lastIndex)} " +
                "queue_mode=${queueDisplayMode.name.lowercase()}"
        )
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
            tracks.map(::buildNavidromePlaybackMediaItem),
            index,
            0L
        )
        applyPreferredOutputDevice(activePlayer)
        activePlayer.prepare()
        activePlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot(force = true)
        ensureProgressUpdates()
        schedulePlaybackCacheWarmup(tracks)
    }

    fun playTracksNext(tracks: List<NavidromeTrack>) {
        val normalizedTracks = tracks.filter { it.id.isNotBlank() }
        if (normalizedTracks.isEmpty()) return
        logPlaybackTrace(
            "playback_command=play_tracks_next queue_size=${normalizedTracks.size}"
        )
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
            buildNavidromePlaybackMediaItem(track)
        })
        updateStateFromPlayer()
        persistPlaybackSnapshot(force = true)
        schedulePlaybackCacheWarmup(queueTracks)
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
            buildNavidromePlaybackMediaItem(track)
        })
        updateStateFromPlayer()
        persistPlaybackSnapshot(force = true)
        schedulePlaybackCacheWarmup(queueTracks)
    }

    fun removeTrackFromQueue(index: Int): Boolean {
        val currentIndex = resolveCurrentQueueIndex()
            .takeIf { it in queueTracks.indices }
            ?: mutableState.value.currentIndex.takeIf { it in queueTracks.indices }
            ?: return false
        val removal = removeNavidromeTrackFromQueue(
            queue = queueTracks,
            currentIndex = currentIndex,
            removeIndex = index
        ) ?: return false

        invalidatePendingPlaybackRestore()
        queueTracks = removal.queue

        val activePlayer = player
        if (activePlayer != null) {
            activePlayer.removeMediaItem(index)
            if (activePlayer.currentMediaItemIndex != removal.currentIndex) {
                activePlayer.seekTo(removal.currentIndex, activePlayer.currentPosition.coerceAtLeast(0L))
            }
            updateStateFromPlayer()
        } else {
            val currentTrack = queueTracks.getOrNull(removal.currentIndex)
            mutableState.value = mutableState.value.copy(
                queue = queueTracks,
                queueDisplayMode = queueDisplayMode,
                currentIndex = removal.currentIndex,
                currentTrack = currentTrack,
                recentTracks = recentTracks,
                isLoading = false,
                isPlaying = false,
                positionMs = mutableState.value.positionMs.coerceAtLeast(0),
                durationMs = currentTrack?.let(::resolveTrackDurationMs) ?: 0,
                errorMessage = null
            )
        }

        persistPlaybackSnapshot(force = true)
        ensureProgressUpdates()
        schedulePlaybackCacheWarmup(queueTracks)
        return true
    }

    fun togglePlayPause() {
        logPlaybackTrace("playback_command=toggle_play_pause is_playing=${mutableState.value.isPlaying}")
        if (mutableState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        logPlaybackTrace(
            "playback_command=play queue_size=${queueTracks.size} current_index=${mutableState.value.currentIndex} " +
                "has_player=${player != null} is_playing=${mutableState.value.isPlaying}"
        )
        if (queueTracks.isEmpty()) return
        val currentTrack = queueTracks.getOrNull(mutableState.value.currentIndex)
        val activePlayer = player
        if (currentTrack != null && currentTrack.isRadioTrack()) {
            if (activePlayer?.isPlaying == true) return
            resumeFromSnapshot(playWhenReady = true)
            return
        }
        if (shouldRecreatePlayerForTransportCommands(activePlayer)) {
            resumeFromSnapshot(playWhenReady = true)
            return
        }
        val confirmedPlayer = activePlayer ?: return
        if (confirmedPlayer.isPlaying) return
        confirmedPlayer.play()
        updateStateFromPlayer()
        persistPlaybackSnapshot(force = true)
        ensureProgressUpdates()
    }

    fun pause() {
        logPlaybackTrace(
            "playback_command=pause queue_size=${queueTracks.size} current_index=${mutableState.value.currentIndex} " +
                "has_player=${player != null} is_playing=${mutableState.value.isPlaying}"
        )
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
        persistPlaybackSnapshot(force = true)
        ensureProgressUpdates()
    }

    fun playNext() {
        val currentIndex = resolveCurrentQueueIndex()
        logPlaybackTrace(
            "playback_command=next current_index=$currentIndex queue_size=${queueTracks.size}"
        )
        val nextIndex = (currentIndex + 1).takeIf { it in queueTracks.indices } ?: return
        seekToQueueIndex(index = nextIndex, positionMs = 0, playWhenReady = true)
    }

    fun playPrevious() {
        val currentIndex = resolveCurrentQueueIndex()
        logPlaybackTrace(
            "playback_command=previous current_index=$currentIndex queue_size=${queueTracks.size} " +
                "position_ms=${mutableState.value.positionMs}"
        )
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
        logPlaybackTrace(
            "playback_command=stop queue_size=${queueTracks.size} current_index=${mutableState.value.currentIndex}"
        )
        invalidatePendingPlaybackRestore()
        queueTracks = emptyList()
        lastRecordedTrackId = null
        lastPersistedSnapshotTrackId = null
        lastPersistedSnapshotPositionMs = 0
        lastPersistedSnapshotElapsedMs = 0L
        forcedRemoteTrackIds = emptySet()
        playbackRecoveryTrackId = null
        cancelPlaybackCacheWarmup(clearSignature = true)
        releasePlayer(clearQueue = true)
        stopProgressUpdates()
        clearPlaybackSurface()
        clearCachedNavidromePlayback("stop")
        mutableState.value = NavidromePlayerState(
            recentTracks = recentTracks,
            outputDevices = mutableState.value.outputDevices,
            selectedOutputDeviceId = mutableState.value.selectedOutputDeviceId
        )
    }

    private fun clearCachedNavidromePlayback(reason: String) {
        logPlaybackTrace("playback_cache_cleared reason=$reason")
        scope.launch(Dispatchers.IO) {
            sessionPreferences.clearCachedNavidromePlayback()
        }
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
        logPlaybackTrace(
            "playback_command=seek target_ms=$clampedPosition duration_ms=$durationMs"
        )
        player?.seekTo(clampedPosition.toLong())
        mutableState.value = mutableState.value.copy(
            positionMs = clampedPosition,
            durationMs = durationMs
        )
        persistPlaybackSnapshot(force = true)
    }

    fun seekBy(deltaMs: Long) {
        val duration = resolvePlayerDurationMs()
        val current = resolvePlayerPositionMs(duration).toLong()
        seekTo((current + deltaMs).coerceIn(0L, duration.toLong().coerceAtLeast(0L)).toInt())
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
        logPlaybackTrace(
            "playback_command=start_playback index=$safeIndex position_ms=$safePositionMs play_when_ready=$playWhenReady " +
                "queue_size=${queueTracks.size} reset_recovery=$resetRecoveryState"
        )
        if (resetRecoveryState) {
            playbackRecoveryTrackId = null
        }
        releasePlayer(clearQueue = false)
        val activePlayer = getOrCreatePlayer()
        activePlayer.setMediaItems(
            queueTracks.map(::buildNavidromePlaybackMediaItem),
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
        persistPlaybackSnapshot(force = true)
        updateStateFromPlayer()
        ensureProgressUpdates()
        schedulePlaybackCacheWarmup(queueTracks)
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
                    forcedRemoteTrackIds = forcedRemoteTrackIds + failedTrackId
                }
                if (refreshedQueue.isNullOrEmpty()) {
                    releasePlayer(clearQueue = false)
                    clearCachedNavidromePlayback("error_refresh_failed")
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
        val forcedRemoteSnapshot = forcedRemoteTrackIds
        val forceRemote = track.id in forcedRemoteSnapshot
        return chooseNavidromePlaybackUri(
            streamUrl = track.streamUrl,
            localPlaybackUri = if (forceRemote) null else downloadManager.localPlaybackUri(track),
            forceRemote = forceRemote
        )
    }

    fun invalidatePlaybackCacheWarmup() {
        scope.launch(Dispatchers.Main.immediate) {
            cancelPlaybackCacheWarmup(clearSignature = true)
            schedulePlaybackCacheWarmup(queueTracks)
        }
    }

    private fun cancelPlaybackCacheWarmup(clearSignature: Boolean) {
        playbackCacheWarmupJob?.cancel()
        playbackCacheWarmupJob = null
        if (clearSignature) {
            playbackCacheWarmupSignature = null
        }
    }

    private fun schedulePlaybackCacheWarmup(tracks: List<NavidromeTrack>) {
        val queueIndex = mutableState.value.currentIndex
        val warmupTracks = selectNavidromePlaybackWarmupTracks(
            tracks = normalizeNavidromePlaybackWarmupTracks(tracks),
            currentIndex = queueIndex
        )
        if (warmupTracks.isEmpty()) {
            cancelPlaybackCacheWarmup(clearSignature = true)
            return
        }
        val signature = buildNavidromePlaybackWarmupSignature(warmupTracks)
        if (signature == playbackCacheWarmupSignature) {
            return
        }
        playbackCacheWarmupSignature = signature
        playbackCacheWarmupJob?.cancel()
        playbackCacheWarmupJob = scope.launch(Dispatchers.IO) {
            logPlaybackTrace(
                "playback_cache_warmup_started queue_size=${warmupTracks.size}"
            )
            val refreshedQueue = when (val result = navidromeRepository.refreshPlayableTracks(warmupTracks)) {
                is com.stillshelf.app.core.util.AppResult.Success -> result.value
                is com.stillshelf.app.core.util.AppResult.Error -> {
                    logPlaybackTrace(
                        "playback_cache_warmup_refresh_failed message=${result.message}"
                    )
                    warmupTracks
                }
            }
            if (!isActive) return@launch
            val cacheLimitBytes = sessionPreferences.state.first()
                .navidromeCacheSizeLimit
                .let { NavidromeCacheSizeOption.toBytes(it) }
            if (cacheLimitBytes != null) {
                downloadManager.evictPlaybackCacheToLimit(cacheLimitBytes)
            }
            if (!isActive) return@launch
            val prefetchResult = downloadManager.prefetchPlaybackQueue(refreshedQueue)
            if (!isActive) return@launch
            scope.launch(Dispatchers.Main.immediate) {
                if (playbackCacheWarmupSignature != signature) {
                    return@launch
                }
                if (queueTracks.map { it.id } == warmupTracks.map { it.id }) {
                    queueTracks = refreshedQueue
                    recentTracks = recentTracks.map { recent ->
                        refreshedQueue.firstOrNull { it.id == recent.id } ?: recent
                    }
                    updateStateFromPlayer()
                }
                when (prefetchResult) {
                    is com.stillshelf.app.core.util.AppResult.Success -> {
                        logPlaybackTrace(
                            "playback_cache_warmup_prefetch_queued count=${prefetchResult.value}"
                        )
                        scope.launch(Dispatchers.IO) {
                            downloadManager.prunePlaybackCache(warmupTracks.map { it.id }.toSet())
                        }
                    }
                    is com.stillshelf.app.core.util.AppResult.Error -> {
                        logPlaybackTrace(
                            "playback_cache_warmup_prefetch_failed message=${prefetchResult.message}"
                        )
                    }
                }
            }
        }
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
        logPlaybackTrace(
            "playback_command=resume_from_snapshot index=$index position_ms=$positionMs play_when_ready=$playWhenReady"
        )
        startPlaybackAt(index = index, positionMs = positionMs, playWhenReady = playWhenReady)
    }

    private fun resolveCurrentQueueIndex(): Int {
        return resolveNavidromeCurrentQueueIndex(
            queue = queueTracks,
            playerIndex = player?.currentMediaItemIndex,
            stateIndex = mutableState.value.currentIndex,
            currentTrackId = mutableState.value.currentTrack?.id
        )
    }

    private fun shouldRecreatePlayerForTransportCommands(activePlayer: ExoPlayer?): Boolean {
        return activePlayer == null || activePlayer.playbackState == Player.STATE_IDLE
    }

    private fun seekToQueueIndex(
        index: Int,
        positionMs: Int,
        playWhenReady: Boolean
    ) {
        if (index !in queueTracks.indices) return
        val safePositionMs = positionMs.coerceAtLeast(0)
        logPlaybackTrace(
            "playback_command=seek_queue_index index=$index position_ms=$safePositionMs play_when_ready=$playWhenReady " +
                "has_player=${player != null}"
        )
        val activePlayer = player
        if (shouldRecreatePlayerForTransportCommands(activePlayer)) {
            startPlaybackAt(index = index, positionMs = safePositionMs, playWhenReady = playWhenReady)
            return
        }
        val confirmedPlayer = activePlayer ?: return
        confirmedPlayer.seekTo(index, safePositionMs.toLong())
        if (playWhenReady) {
            confirmedPlayer.play()
        } else {
            confirmedPlayer.pause()
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
        persistPlaybackSnapshot(force = true)
        ensureProgressUpdates()
        schedulePlaybackCacheWarmup(queueTracks)
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
            cancelPlaybackCacheWarmup(clearSignature = true)
            queueTracks = emptyList()
            queueDisplayMode = NavidromeQueueDisplayMode.FULL
        }
    }

    private fun persistPlaybackSnapshot(force: Boolean = false) {
        if (queueTracks.isEmpty()) {
            lastPersistedSnapshotTrackId = null
            lastPersistedSnapshotPositionMs = 0
            lastPersistedSnapshotElapsedMs = 0L
            clearCachedNavidromePlayback("queue_empty")
            return
        }
        val state = mutableState.value
        val currentIndex = state.currentIndex.takeIf { it in queueTracks.indices } ?: 0
        val currentTrack = queueTracks.getOrNull(currentIndex)
        val currentTrackId = currentTrack?.id
        val currentPositionMs = if (currentTrack.isRadioTrack()) 0 else state.positionMs.coerceAtLeast(0)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isNearStartAfterRewind = currentTrackId != null &&
            currentTrackId == lastPersistedSnapshotTrackId &&
            currentPositionMs <= 5_000 &&
            lastPersistedSnapshotPositionMs - currentPositionMs >= 30_000 &&
            player?.playbackState == Player.STATE_READY
        if (
            !force &&
            !shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = currentTrackId,
                previousTrackId = lastPersistedSnapshotTrackId,
                currentPositionMs = currentPositionMs,
                previousPositionMs = lastPersistedSnapshotPositionMs,
                elapsedSinceLastPersistMs = nowElapsedMs - lastPersistedSnapshotElapsedMs,
                isNearStartAfterRewind = isNearStartAfterRewind
            )
        ) {
            return
        }
        val payload = JSONObject()
            .put("currentIndex", currentIndex)
            .put(
                "positionMs",
                currentPositionMs
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
        lastPersistedSnapshotTrackId = currentTrackId
        lastPersistedSnapshotPositionMs = currentPositionMs
        lastPersistedSnapshotElapsedMs = nowElapsedMs
        logPlaybackTrace(
            "playback_snapshot_saved index=$currentIndex position_ms=$currentPositionMs queue_size=${queueTracks.size} " +
                "force=$force is_playing=${state.isPlaying}"
        )
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
            logPlaybackTrace(
                "playback_snapshot_restore_found has_session_key=${cachedSnapshot.sessionKey != null} payload_bytes=${cachedSnapshot.payload.length}"
            )
            val restoreGeneration = restorePlaybackGeneration
            val currentSessionKey = navidromeRepository.currentPlaybackSessionKey()
            if (cachedSnapshot.sessionKey.isNullOrBlank()) {
                clearCachedNavidromePlayback("restore_missing_session_key")
                return@launch
            }
            if (
                currentSessionKey != null &&
                cachedSnapshot.sessionKey != currentSessionKey
            ) {
                clearCachedNavidromePlayback("restore_session_mismatch")
                return@launch
            }
            val snapshot = cachedSnapshot.payload
                .let(::parsePlaybackSnapshot)
                ?: return@launch
            val refreshedQueue = when (val result = navidromeRepository.refreshPlayableTracks(snapshot.queue)) {
                is com.stillshelf.app.core.util.AppResult.Success -> result.value
                is com.stillshelf.app.core.util.AppResult.Error -> {
                    if (snapshot.queue.any { it.streamUrl.isBlank() && !it.isRadioTrack() }) {
                        clearCachedNavidromePlayback("restore_refresh_missing_stream_url")
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
                clearCachedNavidromePlayback("restore_refresh_empty")
                return@launch
            }
            logPlaybackTrace(
                "playback_snapshot_restore_ready queue_size=${refreshedQueue.size} recent_size=${refreshedRecent.size}"
            )
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
                lastPersistedSnapshotTrackId = currentTrack?.id
                lastPersistedSnapshotPositionMs = if (currentTrack.isRadioTrack()) 0 else snapshot.positionMs
                lastPersistedSnapshotElapsedMs = SystemClock.elapsedRealtime()
                logPlaybackTrace(
                    "playback_snapshot_restored index=$restoredIndex position_ms=${if (currentTrack.isRadioTrack()) 0 else snapshot.positionMs} " +
                        "queue_size=${refreshedQueue.size}"
                )
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
                schedulePlaybackCacheWarmup(refreshedQueue)
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
                        if (appInForeground) {
                            PLAYING_PROGRESS_UPDATE_INTERVAL_MS
                        } else {
                            BACKGROUND_PLAYING_PROGRESS_UPDATE_INTERVAL_MS
                        }
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
            persistPlaybackSnapshot(force = true)
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
        val shouldRun = queueTracks.isNotEmpty() &&
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
            scope.launch(Dispatchers.IO) { downloadManager.touchCacheItem(currentTrack.id) }
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
        persistPlaybackSnapshot()
        if (previousState.currentIndex != currentIndex && currentIndex in queueTracks.indices) {
            schedulePlaybackCacheWarmup(queueTracks)
        }
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
            persistPlaybackSnapshot(force = true)
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

    private fun observeActiveConnectionChanges() {
        scope.launch {
            var hasObservedStatus = false
            var lastStatus: ActiveServerConnectionStatus? = null
            navidromeRepository.observeActiveConnectionStatus().collect { status ->
                if (!hasObservedStatus) {
                    hasObservedStatus = true
                    lastStatus = status
                    return@collect
                }
                if (status != lastStatus) {
                    lastStatus = status
                    refreshPlaybackPresentationForConnectionChange()
                }
            }
        }
    }

    private fun refreshPlaybackPresentationForConnectionChange() {
        val currentTrack = mutableState.value.currentTrack ?: return
        if (currentTrack.id.startsWith("radio:")) return
        val queueSnapshot = queueTracks
        val recentSnapshot = recentTracks
        scope.launch(Dispatchers.IO) {
            val refreshedQueue = if (queueSnapshot.isEmpty()) {
                queueSnapshot
            } else {
                when (val result = navidromeRepository.refreshPlayableTracks(queueSnapshot)) {
                    is com.stillshelf.app.core.util.AppResult.Success -> result.value
                    is com.stillshelf.app.core.util.AppResult.Error -> queueSnapshot
                }
            }
            val refreshedRecent = if (recentSnapshot.isEmpty()) {
                recentSnapshot
            } else {
                when (val result = navidromeRepository.refreshPlayableTracks(recentSnapshot)) {
                    is com.stillshelf.app.core.util.AppResult.Success -> result.value
                    is com.stillshelf.app.core.util.AppResult.Error -> recentSnapshot
                }
            }
            val refreshedCurrentTrack = currentTrack.albumId?.let { albumId ->
                when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = true)) {
                    is com.stillshelf.app.core.util.AppResult.Success -> {
                        result.value.tracks.firstOrNull { it.id == currentTrack.id } ?: currentTrack
                    }
                    is com.stillshelf.app.core.util.AppResult.Error -> currentTrack
                }
            } ?: currentTrack
            scope.launch(Dispatchers.Main.immediate) {
                if (mutableState.value.currentTrack?.id != currentTrack.id) return@launch
                queueTracks = refreshedQueue.map { track ->
                    if (track.id == refreshedCurrentTrack.id) refreshedCurrentTrack else track
                }
                recentTracks = refreshedRecent.map { track ->
                    if (track.id == refreshedCurrentTrack.id) refreshedCurrentTrack else track
                }
                artworkBitmap = null
                artworkTrackId = null
                lastNotificationSignature = null
                mutableState.value = mutableState.value.copy(
                    queue = queueTracks,
                    recentTracks = recentTracks,
                    currentTrack = refreshedCurrentTrack
                )
                persistPlaybackSnapshot(force = true)
                updatePlaybackSurface()
                ensureProgressUpdates()
                schedulePlaybackCacheWarmup(queueTracks)
            }
        }
    }

    private fun observeEqualizerPreferences() {
        scope.launch {
            sessionPreferences.state.collect { preferences ->
                val profiles = preferences.navidromeEqualizerProfiles
                val activeProfileId = preferences.navidromeEqualizerActiveProfileId
                    ?.takeIf { activeId -> profiles.any { it.id == activeId } }
                val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
                val updatedSettings = NavidromeEqualizerSettingsSnapshot(
                    enabled = preferences.navidromeEqualizerEnabled,
                    bandLevelsDb = activeProfile?.effectiveBandLevelsDb().orEmpty()
                )
                val settingsChanged = updatedSettings != lastNavidromeEqualizerSettings
                lastNavidromeEqualizerSettings = updatedSettings

                navidromeEqualizerAudioProcessor.updateSettings(
                    enabled = updatedSettings.enabled,
                    bandLevelsDb = updatedSettings.bandLevelsDb
                )

                val activePlayer = player
                if (settingsChanged && activePlayer != null) {
                    flushNavidromeEqualizerPlayback(activePlayer)
                }
            }
        }
    }

    private fun flushNavidromeEqualizerPlayback(activePlayer: ExoPlayer) {
        val currentIndex = activePlayer.currentMediaItemIndex
            .takeIf { it in queueTracks.indices }
            ?: return
        val currentTrack = queueTracks.getOrNull(currentIndex)
        if (currentTrack.isRadioTrack() || !activePlayer.isCurrentMediaItemSeekable) {
            return
        }
        val currentPositionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = activePlayer.playWhenReady
        activePlayer.seekTo(currentIndex, currentPositionMs)
        if (wasPlaying) {
            activePlayer.play()
        } else {
            activePlayer.pause()
        }
    }

    private fun showPlaybackNotification(
        state: NavidromePlayerState,
        track: NavidromeTrack
    ) {
        createNotificationChannel()
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
        logPlaybackTrace(
            "audio_output_refresh reason=${describeOutputRefreshReason(reason)} available_count=${available.size} " +
                "has_explicit_selection=$hasExplicitOutputSelection"
        )
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
        logPlaybackTrace(
            "audio_output_refresh_applied reason=${describeOutputRefreshReason(reason)} selected_output=${preferredOutputDeviceId != null} " +
                "available_count=${available.size}"
        )
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
