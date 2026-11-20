package com.stillshelf.app.scrobbling

import android.os.SystemClock
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.data.repo.NavidromeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class ListeningPlaybackTracker @Inject constructor(
    private val settings: ListeningSettingsStore,
    private val queue: ListeningSubmissionQueue,
    private val repository: NavidromeRepository,
    private val navidrome: NavidromeListeningClient,
    private val clients: ListeningClients,
    private val local: LocalListeningRecorder? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val accumulator = ListenAccumulator()
    private val nativeAccumulator = ListenAccumulator()
    private var ownerKey: String? = null
    private var nowPlayingId: String? = null
    private var externalNowPlayingId: String? = null
    private var sessionKey: String? = null

    init {
        scope.launch { settings.state.map { it.loaded && it.localRecordingEnabled }.distinctUntilChanged().collect {
            local?.transition()
        } }
        scope.launch {
            runCatching { settings.load() }
            // SessionPreferences also emits for playback checkpoints. Only a real account,
            // endpoint or library change starts a new qualification period.
            repository.observeSession().map { session ->
                Triple(session?.username, session?.baseUrl, repository.currentPlaybackSessionKey())
            }.distinctUntilChanged().collect { (_, _, key) ->
                sessionKey = key
                transition()
            }
        }
    }

    fun transition() { local?.transition(); accumulator.reset(); nativeAccumulator.reset(); nowPlayingId = null; externalNowPlayingId = null }

    fun sample(track: NavidromeTrack?, playing: Boolean, positionMs: Long? = null) {
        val current = settings.state.value
        val connection = current.sendingConnection
        val owner = connection?.revision.takeIf { current.canSubmit }
        val identity = "$sessionKey|$owner"
        if (identity != ownerKey) { accumulator.reset(); externalNowPlayingId = null; ownerKey = identity }
        val eligible = track?.takeIf { it.durationSeconds != null && it.durationSeconds > 0 && it.artistName.isNotBlank() }
        val session = sessionKey
        local?.sample(eligible, playing, session, current.loaded && current.localRecordingEnabled,
            SystemClock.elapsedRealtime(), System.currentTimeMillis() / 1000, positionMs)
        if (eligible == null || session == null) { transition(); return }
        val duration = eligible.durationSeconds ?: return
        val now = System.currentTimeMillis() / 1000
        if (playing && nowPlayingId != eligible.id) {
            nowPlayingId = eligible.id
            scope.launch {
                runCatching {
                    val auth = repository.currentAuth() ?: return@runCatching
                    if (repository.currentPlaybackSessionKey() == session) navidrome.report(auth,
                        TrackListen(eligible.id, eligible.title, eligible.artistName, eligible.albumName, duration, now), submission = false)
                }
            }
        }
        val elapsed = SystemClock.elapsedRealtime()
        val nativeStarted = nativeAccumulator.sample(eligible.id, playing, elapsed, now, scrobbleThresholdMs(duration, ListeningProvider.LAST_FM))
        if (nativeStarted != null) enqueue(TrackListen(eligible.id, eligible.title, eligible.artistName, eligible.albumName, duration, nativeStarted), session, "navidrome")
        if (owner == null || connection == null) { accumulator.reset(); return }
        if (playing && externalNowPlayingId != eligible.id) {
            externalNowPlayingId = eligible.id
            scope.launch {
                // Playing-now is ephemeral: never retry a stale status after reconnecting.
                if (settings.state.value.canSubmit && settings.state.value.sendingConnection?.revision == owner && repository.currentPlaybackSessionKey() == session)
                    runCatching { clients.forProvider(connection.provider).playingNow(connection,
                        TrackListen(eligible.id, eligible.title, eligible.artistName, eligible.albumName, duration, now)) }
            }
        }
        val threshold = scrobbleThresholdMs(duration, connection.provider)
        val started = accumulator.sample(eligible.id, playing, elapsed, now, threshold) ?: return
        val listen = TrackListen(eligible.id, eligible.title, eligible.artistName, eligible.albumName, duration, started)
        enqueue(listen, session, owner)
    }

    private fun enqueue(listen: TrackListen, session: String, owner: String) {
        scope.launch { runCatching { queue.enqueue(listen, session, owner) }.onFailure { queue.storageFailure() } }
    }
}
