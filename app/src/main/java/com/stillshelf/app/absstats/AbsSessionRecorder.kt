package com.stillshelf.app.absstats

import android.content.Context
import android.os.SystemClock
import com.stillshelf.app.core.datastore.SessionPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.withLock

@Singleton
class AbsSessionRecorder @Inject constructor(private val repository: AbsStatisticsRepository,
    private val queue: AbsSessionQueue, preferences: SessionPreferences,
    @ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracker = AbsSessionTracker()
    private val saves = Channel<AbsRecordedSession>(Channel.UNLIMITED)
    @Volatile private var owner: Pair<String, String>? = null
    private var captureGeneration: Pair<Boolean, Long>? = null
    init {
        scope.launch {
            preferences.state.collectLatest { pref ->
                try {
                    owner = pref.activeServerId?.let { id -> repository.accountForServer(id)?.let { id to it.key } }
                    pref.activeServerId?.let { id ->
                        repository.resolveRecordingAccount(id)?.let { account ->
                            queue.stateMutex.withLock {
                                queue.pending().filter { it.serverId == id && repository.localAccounts.owner(it.owner) == repository.localAccounts.owner(account.key) }
                                    .forEach { repository.retainLocal(it) }
                            }
                        }
                    }
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { owner = null }
            }
        }
        scope.launch {
            for (session in saves) {
                try {
                    repository.checkpoint(queue, session)
                    AbsSessionSyncScheduler.ensureBackstop(context); AbsSessionSyncScheduler.enqueue(context)
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Next cumulative checkpoint retries without interrupting playback. */ }
            }
        }
        scope.launch {
            val pending = queue.stateMutex.withLock {
                queue.pending().also { rows -> rows.forEach { repository.retainLocal(it) } }
            }
            if (pending.isNotEmpty()) {
                AbsSessionSyncScheduler.ensureBackstop(context)
                AbsSessionSyncScheduler.enqueue(context)
            }
        }
    }
    /** Called only by the ABS player on its main-thread callbacks/ticker. */
    fun observe(serverId: String?, itemId: String?, positionMs: Long, playing: Boolean, speed: Float,
        libraryId: String? = null, title: String = "", author: String = "", genres: List<String> = emptyList(), narrators: List<String> = emptyList()) {
        val account = owner?.takeIf { it.first == serverId }
        val generation = account?.let { repository.localAccounts.recording(it.second) to repository.localAccounts.clearedAt(it.second) }
        if (generation != captureGeneration) { tracker.sample(null).forEach { saves.trySend(it) }; captureGeneration = generation }
        val sample = if (account != null && !itemId.isNullOrBlank()) AbsPlaybackSample(account.first, account.second,
            itemId, positionMs.coerceAtLeast(0), playing, speed, SystemClock.elapsedRealtime(), System.currentTimeMillis(),
            libraryId, title, author, org.json.JSONObject().put("genres", org.json.JSONArray(genres))
                .put("narrators", org.json.JSONArray(narrators)).toString(),
            if (repository.localAccounts.verified(account.second)) repository.localAccounts.recording(account.second) else null) else null
        tracker.sample(sample).forEach { saves.trySend(it) }
    }
}
