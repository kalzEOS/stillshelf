package com.stillshelf.app.downloads.navidrome

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal object NavidromeDownloadFileCoordinator {
    private const val LOCK_COUNT = 32
    private val locks = Array(LOCK_COUNT) { Any() }

    fun prepare(targetFile: File, transferId: String): Boolean = synchronized(lockFor(targetFile)) {
        targetFile.parentFile?.mkdirs()
        val previousTransferId = activeTransferId(targetFile)
        if (targetFile.exists() && !targetFile.delete()) {
            return@synchronized false
        }
        previousTransferId?.let { temporaryFile(targetFile, it).delete() }
        legacyTemporaryFile(targetFile).delete()
        runCatching {
            activeTransferFile(targetFile).writeText(transferId)
        }.isSuccess
    }

    fun cancelAndDelete(targetFile: File, transferId: String?) = synchronized(lockFor(targetFile)) {
        transferId?.let { temporaryFile(targetFile, it).delete() }
        val ownsTarget = transferId == null || activeTransferId(targetFile) == transferId
        if (ownsTarget) {
            activeTransferFile(targetFile).delete()
            legacyTemporaryFile(targetFile).delete()
            targetFile.delete()
        }
    }

    fun isCurrent(targetFile: File, transferId: String): Boolean {
        return activeTransferId(targetFile) == transferId
    }

    fun finalizeDownload(targetFile: File, transferId: String): Boolean = synchronized(lockFor(targetFile)) {
        val temporaryFile = temporaryFile(targetFile, transferId)
        if (!isCurrent(targetFile, transferId)) {
            temporaryFile.delete()
            return@synchronized false
        }
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("Unable to replace the existing download.")
        }
        if (!temporaryFile.renameTo(targetFile)) {
            throw IOException("Unable to finalize the downloaded file.")
        }
        activeTransferFile(targetFile).delete()
        true
    }

    fun temporaryFile(targetFile: File, transferId: String): File {
        return File("${targetFile.absolutePath}.$transferId.part")
    }

    private fun legacyTemporaryFile(targetFile: File): File = File("${targetFile.absolutePath}.part")

    private fun activeTransferFile(targetFile: File): File = File("${targetFile.absolutePath}.active")

    private fun activeTransferId(targetFile: File): String? {
        return runCatching { activeTransferFile(targetFile).readText().trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun lockFor(targetFile: File): Any {
        return locks[(targetFile.absolutePath.hashCode() and Int.MAX_VALUE) % LOCK_COUNT]
    }
}

class NavidromeDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(INPUT_URL)?.takeIf { it.isNotBlank() }
            ?: return@withContext failure("Download URL is missing.")
        val targetPath = inputData.getString(INPUT_TARGET_PATH)?.takeIf { it.isNotBlank() }
            ?: return@withContext failure("Download destination is missing.")
        val targetFile = File(targetPath)
        val transferId = id.toString()
        val temporaryFile = NavidromeDownloadFileCoordinator.temporaryFile(targetFile, transferId)
        if (!NavidromeDownloadFileCoordinator.isCurrent(targetFile, transferId)) {
            return@withContext failure("Download was cancelled.")
        }
        val okHttpClient = EntryPointAccessors.fromApplication(
            applicationContext,
            NavidromeDownloadWorkerEntryPoint::class.java
        ).okHttpClient()
        try {
            targetFile.parentFile?.mkdirs()
            val resumeOffset = temporaryFile.length().coerceAtLeast(0L)
            val request = Request.Builder()
                .url(url)
                .apply {
                    inputData.getString(INPUT_AUTHORIZATION)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { header("Authorization", it) }
                    if (resumeOffset > 0L) {
                        header("Range", "bytes=$resumeOffset-")
                    }
                }
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 416 && resumeOffset > 0L) {
                    temporaryFile.delete()
                    return@withContext if (canRetry()) Result.retry() else failure("Download failed: invalid resume response.")
                }
                if (!response.isSuccessful) {
                    val message = "Download failed: server returned HTTP ${response.code}."
                    return@withContext if (isRetryableNavidromeHttpCode(response.code) && canRetry()) {
                        Result.retry()
                    } else {
                        temporaryFile.delete()
                        failure(message)
                    }
                }
                val body = response.body ?: run {
                    temporaryFile.delete()
                    return@withContext failure("Download failed: empty server response.")
                }
                val isResuming = resumeOffset > 0L && response.code == 206
                if (isResuming && !isValidNavidromeContentRange(response.header("Content-Range"), resumeOffset)) {
                    temporaryFile.delete()
                    return@withContext if (canRetry()) Result.retry() else failure("Download failed: invalid resume response.")
                }
                if (!isResuming) {
                    temporaryFile.delete()
                }
                val initialBytes = if (isResuming) resumeOffset else 0L
                val totalBytes = body.contentLength()
                    .takeIf { it >= 0L }
                    ?.plus(initialBytes)
                    ?: -1L
                var downloadedBytes = initialBytes
                var lastProgress = -1
                body.byteStream().use { input ->
                    FileOutputStream(temporaryFile, isResuming).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            val progress = calculateNavidromeDownloadProgress(downloadedBytes, totalBytes)
                            if (progress != lastProgress) {
                                setProgress(
                                    workDataOf(
                                        PROGRESS_PERCENT to progress,
                                        PROGRESS_DOWNLOADED_BYTES to downloadedBytes,
                                        PROGRESS_TOTAL_BYTES to totalBytes
                                    )
                                )
                                lastProgress = progress
                            }
                        }
                    }
                }
                if (!NavidromeDownloadFileCoordinator.finalizeDownload(targetFile, transferId)) {
                    throw CancellationException("Download was cancelled.")
                }
                Result.success(workDataOf(OUTPUT_FILE_SIZE_BYTES to downloadedBytes))
            }
        } catch (cancellation: CancellationException) {
            if (!NavidromeDownloadFileCoordinator.isCurrent(targetFile, transferId)) {
                temporaryFile.delete()
            }
            throw cancellation
        } catch (throwable: Throwable) {
            if (canRetry()) {
                Result.retry()
            } else {
                temporaryFile.delete()
                failure("Download failed: ${throwable.toDownloadErrorMessage()}")
            }
        }
    }

    private fun canRetry(): Boolean = runAttemptCount < MAX_RETRY_ATTEMPTS

    private fun failure(message: String): Result {
        return Result.failure(workDataOf(OUTPUT_ERROR_MESSAGE to message))
    }

    companion object {
        internal const val PROGRESS_PERCENT = "progress_percent"
        internal const val PROGRESS_DOWNLOADED_BYTES = "progress_downloaded_bytes"
        internal const val PROGRESS_TOTAL_BYTES = "progress_total_bytes"
        internal const val OUTPUT_FILE_SIZE_BYTES = "output_file_size_bytes"
        internal const val OUTPUT_ERROR_MESSAGE = "output_error_message"

        private const val INPUT_URL = "url"
        private const val INPUT_AUTHORIZATION = "authorization"
        private const val INPUT_TARGET_PATH = "target_path"
        private const val MAX_RETRY_ATTEMPTS = 2

        fun createRequest(
            url: String,
            authorization: String?,
            targetPath: String
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(INPUT_URL, url)
                .putString(INPUT_TARGET_PATH, targetPath)
                .apply {
                    authorization?.takeIf { it.isNotBlank() }?.let {
                        putString(INPUT_AUTHORIZATION, it)
                    }
                }
                .build()
            return OneTimeWorkRequestBuilder<NavidromeDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        }
    }
}

internal fun calculateNavidromeDownloadProgress(downloadedBytes: Long, totalBytes: Long): Int {
    if (downloadedBytes <= 0L || totalBytes <= 0L) return 0
    return ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
}

internal fun isRetryableNavidromeHttpCode(statusCode: Int): Boolean {
    return statusCode == 408 || statusCode == 429 || statusCode >= 500
}

internal fun isValidNavidromeContentRange(contentRange: String?, expectedStart: Long): Boolean {
    val actualStart = contentRange
        ?.trim()
        ?.takeIf { it.startsWith("bytes ", ignoreCase = true) }
        ?.substringAfter(' ')
        ?.substringBefore('-')
        ?.toLongOrNull()
    return actualStart == expectedStart
}

private fun Throwable.toDownloadErrorMessage(): String {
    return message?.takeIf { it.isNotBlank() }
        ?: this::class.java.simpleName.ifBlank { "unknown error" }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavidromeDownloadWorkerEntryPoint {
    fun okHttpClient(): OkHttpClient
}
