package com.stillshelf.app.playback.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

internal fun shouldRetryPlaybackCheckpointSync(
    allowBackgroundRetry: Boolean,
    hasPendingCheckpoints: Boolean
): Boolean = allowBackgroundRetry && hasPendingCheckpoints

object PlaybackProgressSyncScheduler {
    private const val UNIQUE_WORK_NAME = "playback-progress-sync"
    private const val URGENT_DELAY_SECONDS = 2L
    private const val NORMAL_DELAY_SECONDS = 15L
    internal const val ALLOW_BACKGROUND_RETRY_KEY = "allow_background_retry"

    fun enqueue(
        context: Context,
        urgent: Boolean,
        allowBackgroundRetry: Boolean
    ) {
        val delaySeconds = if (urgent) URGENT_DELAY_SECONDS else NORMAL_DELAY_SECONDS
        val request = OneTimeWorkRequestBuilder<PlaybackProgressSyncWorker>()
            .setInputData(
                workDataOf(ALLOW_BACKGROUND_RETRY_KEY to allowBackgroundRetry)
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}

class PlaybackProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PlaybackProgressSyncWorkerEntryPoint::class.java
        )
        val sessionPreferences = entryPoint.sessionPreferences()
        val sessionRepository = entryPoint.sessionRepository()
        val serverDao = entryPoint.serverDao()
        val secureTokenStorage = entryPoint.secureTokenStorage()
        val allowBackgroundRetry = inputData.getBoolean(
            PlaybackProgressSyncScheduler.ALLOW_BACKGROUND_RETRY_KEY,
            false
        )
        val checkpoints = sessionPreferences.getPlaybackCheckpoints()
            .sortedBy { checkpoint -> checkpoint.savedAtMs }
        if (checkpoints.isEmpty()) {
            return Result.success()
        }

        var shouldRetry = false
        checkpoints.forEach { checkpoint ->
            val serverId = checkpoint.serverId?.trim()
            if (serverId.isNullOrBlank()) {
                sessionPreferences.clearPlaybackCheckpoint(serverId = checkpoint.serverId, bookId = checkpoint.bookId)
                return@forEach
            }
            if (serverDao.getById(serverId) == null || secureTokenStorage.getToken(serverId).isNullOrBlank()) {
                sessionPreferences.clearPlaybackCheckpoint(serverId = serverId, bookId = checkpoint.bookId)
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
                    sessionPreferences.clearPlaybackCheckpoint(serverId = serverId, bookId = checkpoint.bookId)
                }

                is AppResult.Error -> {
                    shouldRetry = true
                }
            }
        }

        return if (
            shouldRetryPlaybackCheckpointSync(
                allowBackgroundRetry = allowBackgroundRetry,
                hasPendingCheckpoints = sessionPreferences.getPlaybackCheckpoints().isNotEmpty()
            )
        ) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackProgressSyncWorkerEntryPoint {
    fun sessionRepository(): SessionRepository
    fun sessionPreferences(): SessionPreferences
    fun serverDao(): ServerDao
    fun secureTokenStorage(): SecureTokenStorage
}
