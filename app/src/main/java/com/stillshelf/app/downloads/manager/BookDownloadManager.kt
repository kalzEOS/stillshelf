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
import com.stillshelf.app.downloads.storage.DownloadStorage
import com.stillshelf.app.downloads.worker.DownloadProgressPoller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class DownloadStatus {
    Queued,
    Downloading,
    Completed,
    Failed
}

data class DownloadItem(
    val serverId: String,
    val libraryId: String,
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

internal fun buildDownloadTargetKey(
    serverId: String,
    libraryId: String,
    bookId: String
): String {
    return listOf(serverId.trim(), libraryId.trim(), bookId.trim()).joinToString("|")
}

internal fun DownloadItem.targetKey(): String = buildDownloadTargetKey(
    serverId = serverId,
    libraryId = libraryId,
    bookId = bookId
)

internal fun DownloadItem.matchesSelection(
    serverId: String?,
    libraryId: String?
): Boolean {
    val normalizedServerId = serverId?.trim().orEmpty()
    val normalizedLibraryId = libraryId?.trim().orEmpty()
    if (normalizedServerId.isBlank() || normalizedLibraryId.isBlank()) return false
    return this.serverId == normalizedServerId && this.libraryId == normalizedLibraryId
}

data class DownloadToggleResult(
    val nowDownloaded: Boolean,
    val message: String
)

private data class ActiveDownloadSelection(
    val serverId: String = "",
    val libraryId: String = ""
)

@Singleton
class BookDownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val downloadStorage: DownloadStorage
) {
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableItems = MutableStateFlow(downloadStorage.loadItems())
    private val mutableActiveSelection = MutableStateFlow(ActiveDownloadSelection())
    private val progressPoller = DownloadProgressPoller(
        scope = scope,
        pollIntervalMs = 1000L,
        onTick = ::refreshProgress
    )
    val items: StateFlow<List<DownloadItem>> = mutableItems.asStateFlow()
    val activeItems: Flow<List<DownloadItem>> = combine(mutableItems, sessionPreferences.state) { items, pref ->
        items.filter { item ->
            item.matchesSelection(
                serverId = pref.activeServerId,
                libraryId = pref.activeLibraryId
            )
        }
    }

    init {
        scope.launch {
            sessionPreferences.state.collect { pref ->
                mutableActiveSelection.value = ActiveDownloadSelection(
                    serverId = pref.activeServerId?.trim().orEmpty(),
                    libraryId = pref.activeLibraryId?.trim().orEmpty()
                )
            }
        }
        scope.launch {
            sanitizeCompletedItems()
        }
        progressPoller.start()
        syncDownloadedIds(mutableItems.value)
    }

    suspend fun toggleDownload(book: BookSummary): AppResult<DownloadToggleResult> {
        val bookId = book.id.trim()
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val activeServerId = sessionPreferences.state.first().activeServerId?.trim().orEmpty()
        val libraryId = book.libraryId.trim()
        if (activeServerId.isBlank() || libraryId.isBlank()) {
            return AppResult.Error("No active library selected.")
        }
        val targetKey = buildDownloadTargetKey(
            serverId = activeServerId,
            libraryId = libraryId,
            bookId = bookId
        )

        val existing = items.value.firstOrNull { it.targetKey() == targetKey }
        if (existing != null && existing.status != DownloadStatus.Failed) {
            removeDownload(bookId = bookId, serverId = activeServerId, libraryId = libraryId)
            return AppResult.Success(
                DownloadToggleResult(
                    nowDownloaded = false,
                    message = "Download removed"
                )
            )
        }
        if (existing?.status == DownloadStatus.Failed) {
            deleteItem(targetKey)
        }

        upsertItem(
            DownloadItem(
                serverId = activeServerId,
                libraryId = libraryId,
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
                    serverId = activeServerId,
                    libraryId = libraryId,
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
        val destinationName = buildDestinationFilename(
            serverId = activeServerId,
            libraryId = libraryId,
            book = book
        )
        val request = DownloadManager.Request(Uri.parse(split.cleanUrl))
            .setTitle(book.title)
            .setDescription(book.authorName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setVisibleInDownloadsUi(false)
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
                    serverId = activeServerId,
                    libraryId = libraryId,
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
                    serverId = activeServerId,
                    libraryId = libraryId,
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
        val pref = sessionPreferences.state.first()
        val activeServerId = pref.activeServerId?.trim().orEmpty()
        val activeLibraryId = pref.activeLibraryId?.trim().orEmpty()
        removeDownload(
            bookId = bookId,
            serverId = activeServerId,
            libraryId = activeLibraryId
        )
    }

    private suspend fun removeDownload(
        bookId: String,
        serverId: String,
        libraryId: String
    ) {
        if (serverId.isBlank() || libraryId.isBlank()) return
        val item = items.value.firstOrNull {
            it.targetKey() == buildDownloadTargetKey(serverId, libraryId, bookId)
        } ?: return
        val localRef = item.localPath ?: queryLocalPathByDownloadId(item.downloadId)
        item.downloadId?.let { id ->
            runCatching { downloadManager.remove(id) }
        }
        deleteLocalCopy(localRef)
        deleteItem(item.targetKey())
    }

    fun getCompletedDownload(bookId: String): DownloadItem? {
        val normalized = bookId.trim()
        if (normalized.isBlank()) return null
        val selection = mutableActiveSelection.value
        val activeServerId = selection.serverId
        val activeLibraryId = selection.libraryId
        if (activeServerId.isBlank() || activeLibraryId.isBlank()) return null
        return items.value.firstOrNull { item ->
            item.serverId == activeServerId &&
                item.libraryId == activeLibraryId &&
                item.bookId == normalized &&
                item.status == DownloadStatus.Completed &&
                localResourceExists(item.localPath)
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

    private fun localResourceExists(localPath: String?): Boolean {
        val ref = localPath?.trim().orEmpty()
        if (ref.isBlank()) return false
        return runCatching {
            if (ref.startsWith("content://")) {
                appContext.contentResolver.openFileDescriptor(Uri.parse(ref), "r")?.use { true } ?: false
            } else if (ref.startsWith("file://")) {
                val path = Uri.parse(ref).path.orEmpty()
                path.isNotBlank() && File(path).exists()
            } else {
                File(ref).exists()
            }
        }.getOrDefault(false)
    }

    private fun deleteLocalCopy(localPath: String?) {
        val ref = localPath?.trim().orEmpty()
        if (ref.isBlank()) return
        runCatching {
            when {
                ref.startsWith("content://") -> {
                    appContext.contentResolver.delete(Uri.parse(ref), null, null)
                }
                ref.startsWith("file://") -> {
                    Uri.parse(ref).path?.let { path ->
                        if (path.isNotBlank()) File(path).delete()
                    }
                }
                else -> {
                    File(ref).delete()
                }
            }
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
            val index = current.indexOfFirst { it.targetKey() == item.targetKey() }
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
            val updates = partial.associateBy { it.targetKey() }
            val merged = mutableItems.value.map { existing ->
                updates[existing.targetKey()] ?: existing
            }
            persistItemsLocked(merged)
        }
    }

    private suspend fun deleteItem(targetKey: String) {
        mutex.withLock {
            val next = mutableItems.value.filterNot { it.targetKey() == targetKey }
            persistItemsLocked(next)
        }
    }

    private suspend fun persistItemsLocked(items: List<DownloadItem>) {
        mutableItems.value = items
        downloadStorage.persistItems(items)
        syncDownloadedIds(items)
    }

    private suspend fun sanitizeCompletedItems() {
        mutex.withLock {
            val sanitized = mutableItems.value.filterNot { item ->
                item.status == DownloadStatus.Completed &&
                    !localResourceExists(item.localPath)
            }
            if (sanitized != mutableItems.value) {
                persistItemsLocked(sanitized)
            } else {
                syncDownloadedIds(sanitized)
            }
        }
    }

    private fun buildDestinationFilename(
        serverId: String,
        libraryId: String,
        book: BookSummary
    ): String {
        val safeTitle = book.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        return "${safeTitle.ifBlank { "book" }}-${serverId.take(6)}-${libraryId.take(6)}-${book.id.take(8)}.m4b"
    }

    private fun syncDownloadedIds(items: List<DownloadItem>) {
        scope.launch {
            val ids = items
                .filter { it.status == DownloadStatus.Completed }
                .filter { item -> localResourceExists(item.localPath) }
                .map { it.targetKey() }
                .toSet()
            sessionPreferences.setDownloadedBookIds(ids)
        }
    }
}
