package com.stillshelf.app.absstats

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

internal object AbsSessionSyncScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<AbsSessionSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("abs-listening-session-sync", ExistingWorkPolicy.KEEP, request)
    }
    fun ensureBackstop(context: Context) {
        // Covers process death and a checkpoint saved just as the one-time worker finishes.
        val request = PeriodicWorkRequestBuilder<AbsSessionSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("abs-listening-session-backstop", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class AbsSessionSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, AbsSessionSyncEntryPoint::class.java)
        // The shared endpoint resolver registers a lifecycle observer when first created.
        val repository = withContext(Dispatchers.Main.immediate) { entry.absStatisticsRepository() }
        val queue = entry.absSessionQueue()
        return queue.syncMutex.withLock {
        var failed = false
        val accounts = mutableMapOf<String, AbsStatisticsAccount?>()
        for (session in queue.pending()) {
            try {
                val account = if (accounts.containsKey(session.serverId)) accounts[session.serverId]
                    else repository.resolveRecordingAccount(session.serverId).also { accounts[session.serverId] = it }
                // Never upload an old account's sessions after a login/server change.
                if (account == null) continue
                if (account.key != session.owner && !(repository.localAccounts.verified(account.key) &&
                    repository.localAccounts.verified(session.owner) &&
                    repository.localAccounts.owner(account.key) == repository.localAccounts.owner(session.owner))) continue
                if (session.startedAt <= repository.localAccounts.clearedAt(session.owner)) { queue.acknowledge(session); continue }
                queue.stateMutex.withLock { repository.retainLocal(session) }
                repository.submitSession(account, session.payload())
                queue.stateMutex.withLock {
                    repository.localJournal.acknowledge(session)
                    queue.acknowledge(session)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: AbsPodcastEpisodeUnavailable) {
                // A supported local-only listen must not put the whole queue into
                // exponential backoff and delay newly submitted books/episodes.
                // Keep it durable; the periodic backstop checks server availability.
            }
            catch (_: Exception) { failed = true }
        }
        if (failed) Result.retry() else Result.success()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AbsSessionSyncEntryPoint {
    fun absStatisticsRepository(): AbsStatisticsRepository
    fun absSessionQueue(): AbsSessionQueue
}
