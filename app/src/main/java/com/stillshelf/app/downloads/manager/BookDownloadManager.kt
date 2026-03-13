package com.stillshelf.app.downloads.manager

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack
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

data class DownloadTrackItem(
    val index: Int,
    val startOffsetSeconds: Double,
    val durationSeconds: Double?,
    val downloadId: Long? = null,
    val localPath: String? = null
)

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
    val tracks: List<DownloadTrackItem> = emptyList(),
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

internal data class DownloadTrackRequest(
    val index: Int,
    val startOffsetSeconds: Double,
    val durationSeconds: Double?,
    val streamUrl: String
)

private enum class DownloadTrackRuntimeStatus {
    Downloading,
    Completed,
    Failed
}

private data class DownloadTrackProgressSnapshot(
    val track: DownloadTrackItem,
    val status: DownloadTrackRuntimeStatus,
    val progressPercent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localPath: String?,
    val errorMessage: String? = null
)

internal fun DownloadItem.resolvedTracks(): List<DownloadTrackItem> {
    val normalizedTracks = tracks
        .sortedBy { it.index }
        .distinctBy { it.index }
    if (normalizedTracks.isNotEmpty()) return normalizedTracks
    return if (downloadId != null || !localPath.isNullOrBlank()) {
        listOf(
            DownloadTrackItem(
                index = 0,
                startOffsetSeconds = 0.0,
                durationSeconds = durationSeconds,
                downloadId = downloadId,
                localPath = localPath
            )
        )
    } else {
        emptyList()
    }
}

internal fun DownloadItem.toLocalPlaybackSource(book: BookSummary): PlaybackSource? {
    val localTracks = resolvedTracks()
        .mapNotNull { track ->
            val localUri = track.localPath.toPlayableLocalUri() ?: return@mapNotNull null
            PlaybackTrack(
                startOffsetSeconds = track.startOffsetSeconds.coerceAtLeast(0.0),
                durationSeconds = track.durationSeconds?.takeIf { it >= 0.0 },
                streamUrl = localUri
            )
        }
        .sortedBy { it.startOffsetSeconds }
    if (localTracks.isEmpty()) return null
    return PlaybackSource(
        book = book,
        streamUrl = localTracks.first().streamUrl,
        tracks = localTracks
    )
}

internal fun PlaybackSource.toDownloadTrackRequests(): List<DownloadTrackRequest> {
    val normalizedTracks = tracks
        .sortedBy { it.startOffsetSeconds }
        .mapIndexedNotNull { index, track ->
            val streamUrl = track.streamUrl.trim()
            if (streamUrl.isBlank()) {
                null
            } else {
                DownloadTrackRequest(
                    index = index,
                    startOffsetSeconds = track.startOffsetSeconds.coerceAtLeast(0.0),
                    durationSeconds = track.durationSeconds?.takeIf { it >= 0.0 },
                    streamUrl = streamUrl
                )
            }
        }
        .sortedBy { it.startOffsetSeconds }
    if (normalizedTracks.isNotEmpty()) return normalizedTracks
    val primaryStreamUrl = streamUrl.trim()
    if (primaryStreamUrl.isBlank()) return emptyList()
    return listOf(
        DownloadTrackRequest(
            index = 0,
            startOffsetSeconds = 0.0,
            durationSeconds = book.durationSeconds?.takeIf { it >= 0.0 },
            streamUrl = primaryStreamUrl
        )
    )
}

private fun String?.toPlayableLocalUri(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return when {
        normalized.startsWith("content://") || normalized.startsWith("file://") -> normalized
        else -> Uri.fromFile(File(normalized)).toString()
    }
}

private fun DownloadItem.withResolvedTracks(
    tracks: List<DownloadTrackItem>,
    status: DownloadStatus = this.status,
    progressPercent: Int = this.progressPercent,
    errorMessage: String? = this.errorMessage,
    updatedAtMs: Long = System.currentTimeMillis()
): DownloadItem {
    val normalizedTracks = tracks
        .sortedBy { it.index }
        .distinctBy { it.index }
    val primaryTrack = normalizedTracks.firstOrNull()
    return copy(
        status = status,
        progressPercent = progressPercent.coerceIn(0, 100),
        downloadId = primaryTrack?.downloadId,
        localPath = primaryTrack?.localPath,
        tracks = normalizedTracks,
        errorMessage = errorMessage,
        updatedAtMs = updatedAtMs
    )
}

private fun DownloadItem.hasAllLocalTracks(
    localResourceExists: (String?) -> Boolean
): Boolean {
    val normalizedTracks = resolvedTracks()
    return normalizedTracks.isNotEmpty() && normalizedTracks.all { track ->
        localResourceExists(track.localPath)
    }
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

        val downloadTracks = (sourceResult as AppResult.Success).value.toDownloadTrackRequests()
        if (downloadTracks.isEmpty()) {
            return AppResult.Error("This book does not expose a downloadable audio stream.")
        }
        val startedDownloadIds = mutableListOf<Long>()

        return runCatching {
            val enqueuedTracks = mutableListOf<DownloadTrackItem>()
            downloadTracks.forEach { track ->
                val split = splitAuthenticatedUrl(track.streamUrl)
                val destinationName = buildDestinationFilename(
                    serverId = activeServerId,
                    libraryId = libraryId,
                    book = book,
                    trackIndex = track.index,
                    trackCount = downloadTracks.size,
                    sourceUrl = split.cleanUrl
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
                val downloadId = downloadManager.enqueue(request)
                startedDownloadIds += downloadId
                enqueuedTracks += DownloadTrackItem(
                    index = track.index,
                    startOffsetSeconds = track.startOffsetSeconds,
                    durationSeconds = track.durationSeconds,
                    downloadId = downloadId
                )
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
                    status = DownloadStatus.Downloading,
                    progressPercent = 0
                ).withResolvedTracks(enqueuedTracks, status = DownloadStatus.Downloading)
            )
            DownloadToggleResult(
                nowDownloaded = true,
                message = "Downloading..."
            )
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                startedDownloadIds.forEach { downloadId ->
                    runCatching { downloadManager.remove(downloadId) }
                }
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
        val localRefs = item.resolvedTracks().map { track ->
            track.localPath ?: queryLocalPathByDownloadId(track.downloadId)
        }
        item.resolvedTracks().mapNotNull { it.downloadId }.distinct().forEach { id ->
            runCatching { downloadManager.remove(id) }
        }
        localRefs.distinct().forEach(::deleteLocalCopy)
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
                item.hasAllLocalTracks(::localResourceExists)
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
            val trackSnapshots = item.resolvedTracks().map { track ->
                queryTrackProgress(track, fallbackProgressPercent = item.progressPercent)
            }
            if (trackSnapshots.isEmpty()) return@forEach
            val downloadedBytes = trackSnapshots.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
            val totalBytes = trackSnapshots.sumOf { it.totalBytes.coerceAtLeast(0L) }
            val aggregateProgress = if (totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                (trackSnapshots.sumOf { it.progressPercent } / trackSnapshots.size).coerceIn(0, 100)
            }
            val nextStatus = when {
                trackSnapshots.any { it.status == DownloadTrackRuntimeStatus.Failed } -> DownloadStatus.Failed
                trackSnapshots.all { it.status == DownloadTrackRuntimeStatus.Completed } -> DownloadStatus.Completed
                else -> DownloadStatus.Downloading
            }
            val nextError = trackSnapshots.firstOrNull { it.errorMessage != null }?.errorMessage
            val next = item.withResolvedTracks(
                tracks = trackSnapshots.map { snapshot ->
                    snapshot.track.copy(localPath = snapshot.localPath ?: snapshot.track.localPath)
                },
                status = nextStatus,
                progressPercent = if (nextStatus == DownloadStatus.Completed) 100 else aggregateProgress,
                errorMessage = if (nextStatus == DownloadStatus.Downloading) null else nextError
            )
            if (next != item) {
                updates += next
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

    private fun queryTrackProgress(
        track: DownloadTrackItem,
        fallbackProgressPercent: Int
    ): DownloadTrackProgressSnapshot {
        val downloadId = track.downloadId
        if (downloadId == null || downloadId <= 0L) {
            return if (localResourceExists(track.localPath)) {
                DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Completed,
                    progressPercent = 100,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    localPath = track.localPath
                )
            } else {
                DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Failed,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    localPath = track.localPath,
                    errorMessage = "Download was removed."
                )
            }
        }
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { downloadManager.query(query) }.getOrNull()
        if (cursor == null) {
            return DownloadTrackProgressSnapshot(
                track = track,
                status = DownloadTrackRuntimeStatus.Failed,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = 0L,
                localPath = track.localPath,
                errorMessage = "Download was removed."
            )
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                return DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Failed,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    localPath = track.localPath,
                    errorMessage = "Download was removed."
                )
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val progress = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                fallbackProgressPercent.coerceIn(0, 100)
            }
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Completed,
                    progressPercent = 100,
                    downloadedBytes = downloaded.coerceAtLeast(0L),
                    totalBytes = total.coerceAtLeast(0L),
                    localPath = resolveLocalPath(localUri) ?: track.localPath
                )

                DownloadManager.STATUS_FAILED -> DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Failed,
                    progressPercent = progress,
                    downloadedBytes = downloaded.coerceAtLeast(0L),
                    totalBytes = total.coerceAtLeast(0L),
                    localPath = track.localPath,
                    errorMessage = "Download failed ($reason)"
                )

                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING -> DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Downloading,
                    progressPercent = progress,
                    downloadedBytes = downloaded.coerceAtLeast(0L),
                    totalBytes = total.coerceAtLeast(0L),
                    localPath = track.localPath
                )

                else -> DownloadTrackProgressSnapshot(
                    track = track,
                    status = DownloadTrackRuntimeStatus.Downloading,
                    progressPercent = progress,
                    downloadedBytes = downloaded.coerceAtLeast(0L),
                    totalBytes = total.coerceAtLeast(0L),
                    localPath = track.localPath
                )
            }
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
                    !item.hasAllLocalTracks(::localResourceExists)
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
        book: BookSummary,
        trackIndex: Int,
        trackCount: Int,
        sourceUrl: String
    ): String {
        val safeTitle = book.title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
        val parsedPath = runCatching { Uri.parse(sourceUrl).lastPathSegment.orEmpty() }.getOrDefault("")
        val extension = parsedPath.substringAfterLast('.', "").trim().lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
            ?: "mp3"
        val trackSuffix = if (trackCount > 1) {
            "-part${(trackIndex + 1).toString().padStart(3, '0')}"
        } else {
            ""
        }
        return "${safeTitle.ifBlank { "book" }}-${serverId.take(6)}-${libraryId.take(6)}-${book.id.take(8)}$trackSuffix.$extension"
    }

    private fun syncDownloadedIds(items: List<DownloadItem>) {
        scope.launch {
            val ids = items
                .filter { it.status == DownloadStatus.Completed }
                .filter { item -> item.hasAllLocalTracks(::localResourceExists) }
                .map { it.targetKey() }
                .toSet()
            sessionPreferences.setDownloadedBookIds(ids)
        }
    }
}
