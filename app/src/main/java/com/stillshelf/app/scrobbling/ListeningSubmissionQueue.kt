package com.stillshelf.app.scrobbling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.data.repo.NavidromeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ListeningSubmissionQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secrets: SecureTokenStorage,
    private val settings: ListeningSettingsStore,
    private val clients: ListeningClients,
    private val repository: NavidromeRepository,
    private val navidrome: NavidromeListeningClient
) {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private val mutableDirectStatus = MutableStateFlow<Pair<String, String>?>(null)
    val directStatus = mutableDirectStatus.asStateFlow()
    private fun reportStatus(owner: String, message: String) {
        mutableStatus.value = message
        if (owner != "navidrome") mutableDirectStatus.value = owner to message
    }
    fun storageFailure() {
        mutableStatus.value = "Could not securely save this listen for submission. Check secure storage on this device."
    }

    suspend fun enqueue(listen: TrackListen, session: String, owner: String) = mutex.withLock {
        val pending = read().filterNot { it.optString("key") == "$session|$owner|${listen.id}|${listen.startedAtSeconds}" }.takeLast(499)
        val record = JSONObject().put("key", "$session|$owner|${listen.id}|${listen.startedAtSeconds}")
            .put("session", session).put("owner", owner).put("id", listen.id).put("title", listen.title)
            .put("artist", listen.artist).put("album", listen.album).put("duration", listen.durationSeconds)
            .put("time", listen.startedAtSeconds)
        save(pending + record)
        schedule()
    }

    private suspend fun read() = secrets.getNamedSecret(KEY)?.let { JSONArray(it).objects() }.orEmpty()
    private suspend fun save(records: List<JSONObject>) = secrets.saveNamedSecret(
        KEY, JSONArray(records).toString(), persistAcrossRestarts = true, allowInsecureStorage = false)

    fun schedule() {
        val request = OneTimeWorkRequestBuilder<ListeningSubmissionWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("listening-submissions", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Returns true when retryable records remain. Secrets never appear in errors. */
    suspend fun drain(): Boolean = mutex.withLock {
        settings.load()
        val remaining = read().toMutableList()
        var retry = false
        val unavailableOwners = mutableSetOf<String>()
        for (record in remaining.toList()) {
            val current = settings.state.value
            val owner = record.getString("owner")
            val validOwner = owner == "navidrome" || current.canSubmit && current.sendingConnection?.revision == owner
            val validSession = repository.currentPlaybackSessionKey() == record.getString("session")
            if (!validOwner || !validSession || System.currentTimeMillis() / 1000 - record.getLong("time") > 7 * 86_400) {
                remaining.remove(record)
                save(remaining)
                continue
            }
            if (owner in unavailableOwners) continue
            val listen = TrackListen(record.getString("id"), record.getString("title"), record.getString("artist"),
                record.getString("album"), record.getInt("duration"), record.getLong("time"))
            try {
                if (owner == "navidrome") {
                    val auth = repository.currentAuth() ?: throw IOException("Navidrome is unavailable.")
                    navidrome.report(auth, listen, submission = true)
                } else {
                    val connection = settings.state.value.takeIf { it.canSubmit }?.sendingConnection
                    if (connection?.revision != owner) continue
                    clients.forProvider(connection.provider).submit(connection, listen)
                }
                remaining.remove(record)
                reportStatus(owner, "Last listen submitted successfully.")
            } catch (e: CancellationException) { throw e } catch (e: ListeningApiException) {
                reportStatus(owner, e.message ?: "The listening service rejected this listen.")
                if (e.retryable) { retry = true; unavailableOwners += owner; continue }
                // Pause authorization failures until the user repairs this same account connection.
                // A connection edit schedules another drain; account changes invalidate ownership.
                if (e.status in setOf(401, 403, 4, 9, 10, 13, 15, 26)) { unavailableOwners += owner; continue }
                remaining.remove(record)
            } catch (_: IOException) {
                reportStatus(owner, "Listening service unavailable. Saved listens will retry when connected.")
                retry = true
                unavailableOwners += owner
            }
            save(remaining)
        }
        save(remaining)
        retry
    }

    private companion object { const val KEY = "listening_pending_v1" }
}

class ListeningSubmissionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val queue = EntryPointAccessors.fromApplication(applicationContext, ListeningWorkerEntryPoint::class.java).listeningQueue()
        return try { if (queue.drain()) Result.retry() else Result.success() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ListeningWorkerEntryPoint { fun listeningQueue(): ListeningSubmissionQueue }
