package com.stillshelf.app.downloads.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadStatus {
    Queued,
    Downloading,
    Completed,
    Failed
}

data class DownloadItem(
    val bookId: String,
    val title: String,
    val authorName: String,
    val coverUrl: String?,
    val durationSeconds: Double?,
    val status: DownloadStatus,
    val progressPercent: Int,
    val downloadId: Long? = null,
    val localPath: String? = null,
    val errorMessage: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class DownloadToggleResult(
    val nowDownloaded: Boolean,
    val message: String
)

@Singleton
class BookDownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) {
    companion object {
        private const val PREF_NAME = "stillshelf_downloads"
        private const val PREF_KEY_ITEMS = "items"
    }

    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableItems = MutableStateFlow(loadItemsFromPrefs())
    val items: StateFlow<List<DownloadItem>> = mutableItems.asStateFlow()

    init {
        scope.launch {
            sanitizeCompletedItems()
        }
        scope.launch {
            while (isActive) {
                refreshProgress()
                delay(1000L)
            }
        }
        syncDownloadedIds(mutableItems.value)
    }

    suspend fun toggleDownload(book: BookSummary): AppResult<DownloadToggleResult> {
        val bookId = book.id.trim()
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")

        val existing = items.value.firstOrNull { it.bookId == bookId }
        if (existing != null && existing.status != DownloadStatus.Failed) {
            removeDownload(bookId)
            return AppResult.Success(
                DownloadToggleResult(
                    nowDownloaded = false,
                    message = "Download removed"
                )
            )
        }
        if (existing?.status == DownloadStatus.Failed) {
            deleteItem(bookId)
        }

        upsertItem(
            DownloadItem(
                bookId = bookId,
                title = book.title,
                authorName = book.authorName,
                coverUrl = book.coverUrl,
                durationSeconds = book.durationSeconds,
                status = DownloadStatus.Queued,
                progressPercent = 0
            )
        )

        val sourceResult = sessionRepository.fetchPlaybackSource(bookId)
        if (sourceResult is AppResult.Error) {
            upsertItem(
                DownloadItem(
                    bookId = bookId,
                    title = book.title,
                    authorName = book.authorName,
                    coverUrl = book.coverUrl,
                    durationSeconds = book.durationSeconds,
                    status = DownloadStatus.Failed,
                    progressPercent = 0,
                    errorMessage = sourceResult.message
                )
            )
            return AppResult.Error(sourceResult.message)
        }

        val streamUrl = (sourceResult as AppResult.Success).value.streamUrl
        val split = splitAuthenticatedUrl(streamUrl)
        val destinationName = buildDestinationFilename(book)
        val request = DownloadManager.Request(Uri.parse(split.cleanUrl))
            .setTitle(book.title)
            .setDescription(book.authorName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_PODCASTS,
                destinationName
            )
        split.authToken
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                request.addRequestHeader("Authorization", authorizationHeaderValue(token))
            }

        return runCatching {
            val downloadId = downloadManager.enqueue(request)
            upsertItem(
                DownloadItem(
                    bookId = bookId,
                    title = book.title,
                    authorName = book.authorName,
                    coverUrl = book.coverUrl,
                    durationSeconds = book.durationSeconds,
                    status = DownloadStatus.Downloading,
                    progressPercent = 0,
                    downloadId = downloadId
                )
            )
            DownloadToggleResult(
                nowDownloaded = true,
                message = "Downloading..."
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                val message = it.message ?: "Unable to start download."
                upsertItem(
                    DownloadItem(
                        bookId = bookId,
                        title = book.title,
                        authorName = book.authorName,
                        coverUrl = book.coverUrl,
                        durationSeconds = book.durationSeconds,
                        status = DownloadStatus.Failed,
                        progressPercent = 0,
                        errorMessage = message
                    )
                )
                AppResult.Error(message, it)
            }
        )
    }

    suspend fun removeDownload(bookId: String) {
        val item = items.value.firstOrNull { it.bookId == bookId } ?: return
        item.downloadId?.let { id ->
            runCatching { downloadManager.remove(id) }
        }
        val localPath = item.localPath ?: queryLocalPathByDownloadId(item.downloadId)
        localPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path ->
                runCatching { File(path).delete() }
            }
        deleteItem(bookId)
    }

    fun getCompletedDownload(bookId: String): DownloadItem? {
        val normalized = bookId.trim()
        if (normalized.isBlank()) return null
        return items.value.firstOrNull { item ->
            item.bookId == normalized &&
                item.status == DownloadStatus.Completed &&
                item.localPath?.let { File(it).exists() } == true
        }
    }

    private suspend fun refreshProgress() {
        val snapshot = items.value
        val active = snapshot.filter {
            it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading
        }
        if (active.isEmpty()) return

        val updates = mutableListOf<DownloadItem>()
        active.forEach { item ->
            val id = item.downloadId ?: return@forEach
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = runCatching { downloadManager.query(query) }.getOrNull() ?: return@forEach
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    updates += item.copy(
                        status = DownloadStatus.Failed,
                        errorMessage = "Download was removed.",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    return@use
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val progress = if (total > 0L) {
                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                } else {
                    item.progressPercent
                }
                val next = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> item.copy(
                        status = DownloadStatus.Completed,
                        progressPercent = 100,
                        localPath = resolveLocalPath(localUri),
                        errorMessage = null,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    DownloadManager.STATUS_FAILED -> item.copy(
                        status = DownloadStatus.Failed,
                        errorMessage = "Download failed ($reason)",
                        updatedAtMs = System.currentTimeMillis()
                    )
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING -> item.copy(
                        status = DownloadStatus.Downloading,
                        progressPercent = progress,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    else -> item
                }
                if (next != item) {
                    updates += next
                }
            }
        }

        if (updates.isNotEmpty()) {
            replaceItems(updates)
        }
    }

    private fun resolveLocalPath(localUri: String?): String? {
        val value = localUri?.trim().orEmpty()
        if (value.isBlank()) return null
        return if (value.startsWith("file://")) {
            Uri.parse(value).path
        } else {
            value
        }
    }

    private fun queryLocalPathByDownloadId(downloadId: Long?): String? {
        if (downloadId == null || downloadId <= 0L) return null
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { downloadManager.query(query) }.getOrNull() ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val localUri = runCatching {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            }.getOrNull()
            return resolveLocalPath(localUri)
        }
    }

    private suspend fun upsertItem(item: DownloadItem) {
        mutex.withLock {
            val current = mutableItems.value.toMutableList()
            val index = current.indexOfFirst { it.bookId == item.bookId }
            if (index >= 0) {
                current[index] = item
            } else {
                current += item
            }
            persistItemsLocked(current)
        }
    }

    private suspend fun replaceItems(partial: List<DownloadItem>) {
        if (partial.isEmpty()) return
        mutex.withLock {
            val updates = partial.associateBy { it.bookId }
            val merged = mutableItems.value.map { existing ->
                updates[existing.bookId] ?: existing
            }
            persistItemsLocked(merged)
        }
    }

    private suspend fun deleteItem(bookId: String) {
        mutex.withLock {
            val next = mutableItems.value.filterNot { it.bookId == bookId }
            persistItemsLocked(next)
        }
    }

    private suspend fun persistItemsLocked(items: List<DownloadItem>) {
        mutableItems.value = items
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("bookId", item.bookId)
                        .put("title", item.title)
                        .put("authorName", item.authorName)
                        .put("coverUrl", item.coverUrl)
                        .put("durationSeconds", item.durationSeconds)
                        .put("status", item.status.name)
                        .put("progressPercent", item.progressPercent)
                        .put("downloadId", item.downloadId)
                        .put("localPath", item.localPath)
                        .put("errorMessage", item.errorMessage)
                        .put("updatedAtMs", item.updatedAtMs)
                )
            }
        }.toString()
        prefs.edit().putString(PREF_KEY_ITEMS, payload).apply()
        syncDownloadedIds(items)
    }

    private suspend fun sanitizeCompletedItems() {
        mutex.withLock {
            val sanitized = mutableItems.value.filterNot { item ->
                item.status == DownloadStatus.Completed &&
                    (item.localPath.isNullOrBlank() || !File(item.localPath).exists())
            }
            if (sanitized != mutableItems.value) {
                persistItemsLocked(sanitized)
            } else {
                syncDownloadedIds(sanitized)
            }
        }
    }

    private fun loadItemsFromPrefs(): List<DownloadItem> {
        val raw = prefs.getString(PREF_KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.optJSONObject(index) ?: continue
                    val bookId = node.optString("bookId").trim()
                    if (bookId.isBlank()) continue
                    add(
                        DownloadItem(
                            bookId = bookId,
                            title = node.optString("title"),
                            authorName = node.optString("authorName"),
                            coverUrl = node.optString("coverUrl").ifBlank { null },
                            durationSeconds = node.optDouble("durationSeconds").takeIf { !it.isNaN() },
                            status = runCatching {
                                DownloadStatus.valueOf(node.optString("status"))
                            }.getOrDefault(DownloadStatus.Queued),
                            progressPercent = node.optInt("progressPercent", 0).coerceIn(0, 100),
                            downloadId = node.optLong("downloadId").takeIf { it > 0L },
                            localPath = node.optString("localPath").ifBlank { null },
                            errorMessage = node.optString("errorMessage").ifBlank { null },
                            updatedAtMs = node.optLong("updatedAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildDestinationFilename(book: BookSummary): String {
        val safeTitle = book.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return "${safeTitle.ifBlank { "book" }}-${book.id.take(8)}.m4b"
    }

    private fun syncDownloadedIds(items: List<DownloadItem>) {
        scope.launch {
            val ids = items
                .filter { it.status == DownloadStatus.Completed }
                .filter { item -> item.localPath?.let { File(it).exists() } == true }
                .map { it.bookId }
                .toSet()
            sessionPreferences.setDownloadedBookIds(ids)
        }
    }
}
