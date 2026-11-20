package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.NavidromeTrack
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Uses the same qualification clock as external scrobbling, with its own opt-in boundary. */
@Singleton
class LocalListeningRecorder internal constructor(private val history: LocalListeningRepository, private val scope: CoroutineScope) {
    @Inject constructor(history: LocalListeningRepository) : this(history,
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    private val accumulator = ListenAccumulator()
    private var identity: String? = null
    private var record: LocalListen? = null
    private var writeAt = 0L
    private var epoch = 0L
    private var lastQueuedMs = -1L
    private var wasPlaying = false
    @Volatile private var failedId: String? = null
    private var baseMs = 0L
    private var pendingResume = false
    private var resumeChecked = false
    private var qualified: LocalListen? = null
    private var positionMs: Long? = null
    private var transitionVersion = 0L

    fun transition() {
        transitionVersion++
        flush(ended = true)
        accumulator.reset()
        record = null
        identity = null
        lastQueuedMs = -1
        wasPlaying = false
        baseMs = 0
        pendingResume = false
        resumeChecked = false
        qualified = null
        positionMs = null
    }

    fun sample(track: NavidromeTrack?, playing: Boolean, session: String?, enabled: Boolean, elapsed: Long, wall: Long, position: Long? = null) {
        val next = if (enabled && track != null && session != null) "$session|${track.id}|${history.epoch.value}" else null
        if (next != identity) {
            transition(); identity = next; epoch = history.epoch.value
        }
        if (!resumeChecked && next != null && position != null && session != null && track != null) {
            if (position > 1_000) {
                resumeChecked = true
                pendingResume = true
                val generation = epoch
                val version = transitionVersion
                scope.launch {
                    val previous = try { history.resumable(session, track.id, position) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                    if (identity != next || epoch != generation || version != transitionVersion) {
                        if (previous != null) runCatching { history.record(previous.copy(ended = true), generation) }
                        return@launch
                    }
                    pendingResume = false
                    if (previous != null) { record = previous; baseMs = previous.listenedMs }
                    else record = qualified
                    flush()
                }
            } else if (playing) resumeChecked = true
        }
        if (next == null || track == null) return
        positionMs = position ?: positionMs
        val started = accumulator.sample(track.id, playing, elapsed, wall,
            track.durationSeconds?.let { scrobbleThresholdMs(it, ListeningProvider.LISTEN_BRAINZ) })
        if (started != null) {
            fun key(vararg values: String) = localIdentity(values.joinToString("") { val v = it.trim().lowercase(Locale.ROOT); "${v.length}:$v" })
            qualified = LocalListen(UUID.randomUUID().toString(), localIdentity(session!!), track.id, track.artistId, track.albumId,
                track.title, track.artistName, track.albumName, track.musicBrainzId?.takeIf { runCatching { UUID.fromString(it) }.isSuccess },
                track.durationSeconds, started, accumulator.listenedMs,
                key(track.artistName), key(track.artistName, track.albumName), key(track.artistName, track.albumName, track.title),
                genresJson = org.json.JSONArray(track.genres.map { it.trim() }.filter(String::isNotBlank).distinctBy { it.lowercase(Locale.ROOT) }.take(32)).toString(),
                releaseYear = track.releaseYear?.takeIf { it in 1000..java.time.LocalDate.now().year + 1 })
            if (!pendingResume && record == null) record = qualified
            flush(); writeAt = elapsed
        } else if (record != null && ((!playing && wasPlaying) || elapsed - writeAt >= 5_000)) { flush(); writeAt = elapsed }
        if (!playing && position != null && track.durationSeconds != null && position >= track.durationSeconds * 1000L - 500)
            flush(ended = true)
        wasPlaying = playing
    }

    private fun flush(ended: Boolean = false) {
        val current = record?.copy(listenedMs = baseMs + accumulator.listenedMs, positionMs = positionMs,
            ended = ended || record?.ended == true) ?: return
        if (current.listenedMs == lastQueuedMs && failedId != current.id && current.ended == record?.ended && current.positionMs == record?.positionMs) return
        record = current
        lastQueuedMs = current.listenedMs
        failedId = null
        val generation = epoch
        scope.launch { try { history.record(current, generation) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            // Retry from the next sample; storage errors must never interrupt playback or other destinations.
            failedId = current.id
            history.storageFailure()
        } }
    }
}
