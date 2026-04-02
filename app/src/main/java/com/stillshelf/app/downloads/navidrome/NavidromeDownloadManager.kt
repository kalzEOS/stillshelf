package com.stillshelf.app.downloads.navidrome

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.downloads.worker.DownloadProgressPoller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NavidromeDownloadToggleResult(
    val nowDownloaded: Boolean,
    val message: String
)

private data class NavidromeActiveSelection(
    val serverId: String = "",
    val libraryId: String = ""
)

@Singleton
class NavidromeDownloadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionPreferences: SessionPreferences,
    private val downloadStorage: NavidromeDownloadStorage
) {
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableItems = MutableStateFlow(downloadStorage.loadItems())
    private val mutableActiveSelection = MutableStateFlow(NavidromeActiveSelection())
    private val progressPoller = DownloadProgressPoller(
        scope = scope,
        pollIntervalMs = 1000L,
        onTick = ::refreshProgress
    )

    val items: StateFlow<List<NavidromeDownloadItem>> = mutableItems.asStateFlow()
    val activeItems: Flow<List<NavidromeDownloadItem>> = combine(mutableItems, mutableActiveSelection) { items, selection ->
        items.filter { item ->
            item.serverId == selection.serverId && item.libraryId == selection.libraryId
        }
    }

    init {
        scope.launch {
            sessionPreferences.state.collect { state ->
                val serverId = state.activeNavidromeServerId?.trim().orEmpty()
                val libraryId = state.navidromeActiveLibraryIds[serverId]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "_all"
                mutableActiveSelection.value = NavidromeActiveSelection(serverId = serverId, libraryId = libraryId)
            }
        }
        scope.launch {
            refreshProgress()
        }
    }

    suspend fun toggleTrackDownload(
        track: NavidromeTrack,
        albumSongCount: Int? = null
    ): AppResult<NavidromeDownloadToggleResult> = mutex.withLock {
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val existing = mutableItems.value.firstOrNull {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.trackId == track.id
        }
        if (existing != null && existing.status != NavidromeDownloadStatus.Failed) {
            removeItem(existing)
            return@withLock AppResult.Success(
                NavidromeDownloadToggleResult(
                    nowDownloaded = false,
                    message = "Download removed"
                )
            )
        }
        val newItem = enqueueTrack(
            selection = selection,
            track = track,
            albumSongCount = albumSongCount
        ) ?: return@withLock AppResult.Error("Unable to queue this download.")
        replaceItems { items ->
            items.filterNot {
                it.serverId == selection.serverId &&
                    it.libraryId == selection.libraryId &&
                    it.trackId == track.id
            } + newItem
        }
        AppResult.Success(
            NavidromeDownloadToggleResult(
                nowDownloaded = true,
                message = "Downloading..."
            )
        )
    }

    suspend fun toggleTrackBatchDownload(
        tracks: List<NavidromeTrack>,
        albumSongCountByAlbumId: Map<String, Int> = emptyMap(),
        downloadLabel: String
    ): AppResult<NavidromeDownloadToggleResult> = mutex.withLock {
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val normalizedTracks = tracks
            .distinctBy { it.id }
            .filter { it.id.isNotBlank() && it.streamUrl.isNotBlank() }
        if (normalizedTracks.isEmpty()) {
            return@withLock AppResult.Error("Nothing to download.")
        }
        val existingByTrackId = mutableItems.value
            .filter { it.serverId == selection.serverId && it.libraryId == selection.libraryId }
            .associateBy { it.trackId }
        val allAlreadyPresent = normalizedTracks.all { track ->
            val existing = existingByTrackId[track.id]
            existing != null && existing.status != NavidromeDownloadStatus.Failed
        }
        if (allAlreadyPresent) {
            normalizedTracks.forEach { track ->
                existingByTrackId[track.id]?.let(::removeItem)
            }
            return@withLock AppResult.Success(
                NavidromeDownloadToggleResult(
                    nowDownloaded = false,
                    message = "$downloadLabel download removed"
                )
            )
        }

        val newItems = normalizedTracks.mapNotNull { track ->
            val existing = existingByTrackId[track.id]
            if (existing != null && existing.status != NavidromeDownloadStatus.Failed) {
                null
            } else {
                enqueueTrack(
                    selection = selection,
                    track = track,
                    albumSongCount = track.albumId?.let(albumSongCountByAlbumId::get)
                )
            }
        }
        if (newItems.isEmpty()) {
            return@withLock AppResult.Error("Nothing to download.")
        }
        replaceItems { items ->
            val newTrackIds = newItems.map { it.trackId }.toSet()
            items.filterNot {
                it.serverId == selection.serverId &&
                    it.libraryId == selection.libraryId &&
                    it.trackId in newTrackIds
            } + newItems
        }
        AppResult.Success(
            NavidromeDownloadToggleResult(
                nowDownloaded = true,
                message = "Downloading $downloadLabel"
            )
        )
    }

    suspend fun removeTrackDownload(trackId: String): AppResult<NavidromeDownloadToggleResult> = mutex.withLock {
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val existing = mutableItems.value.firstOrNull {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.trackId == trackId &&
                it.status != NavidromeDownloadStatus.Failed
        } ?: return@withLock AppResult.Error("Download not found.")
        removeItem(existing)
        AppResult.Success(
            NavidromeDownloadToggleResult(
                nowDownloaded = false,
                message = "Download removed"
            )
        )
    }

    suspend fun removeAlbumDownload(albumId: String): AppResult<NavidromeDownloadToggleResult> = mutex.withLock {
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val existingItems = mutableItems.value.filter {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.albumId == albumId &&
                it.status != NavidromeDownloadStatus.Failed
        }
        if (existingItems.isEmpty()) {
            return@withLock AppResult.Error("Download not found.")
        }
        existingItems.forEach(::removeItem)
        AppResult.Success(
            NavidromeDownloadToggleResult(
                nowDownloaded = false,
                message = "Album download removed"
            )
        )
    }

    suspend fun removeAllDownloads(): AppResult<NavidromeDownloadToggleResult> = mutex.withLock {
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val existingItems = mutableItems.value.filter {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.status != NavidromeDownloadStatus.Failed
        }
        if (existingItems.isEmpty()) {
            return@withLock AppResult.Error("No downloads to remove.")
        }
        existingItems.forEach(::removeItem)
        AppResult.Success(
            NavidromeDownloadToggleResult(
                nowDownloaded = false,
                message = "All downloads removed"
            )
        )
    }

    fun localPlaybackUri(track: NavidromeTrack): String? {
        val selection = mutableActiveSelection.value
        if (selection.serverId.isBlank()) return null
        val item = mutableItems.value.firstOrNull {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.trackId == track.id &&
                it.status == NavidromeDownloadStatus.Completed &&
                localFileExists(it.localPath)
        } ?: return null
        return item.localPath.toPlayableLocalUri()
    }

    private suspend fun resolveActiveSelection(): NavidromeActiveSelection? {
        val state = sessionPreferences.state.first()
        val serverId = state.activeNavidromeServerId?.trim().orEmpty()
        if (serverId.isBlank()) return null
        val libraryId = state.navidromeActiveLibraryIds[serverId]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "_all"
        return NavidromeActiveSelection(serverId = serverId, libraryId = libraryId)
    }

    private fun enqueueTrack(
        selection: NavidromeActiveSelection,
        track: NavidromeTrack,
        albumSongCount: Int?
    ): NavidromeDownloadItem? {
        val split = splitAuthenticatedUrl(track.streamUrl)
        val targetFile = buildTrackTargetFile(
            serverId = selection.serverId,
            libraryId = selection.libraryId,
            trackId = track.id,
            formatLabel = track.formatLabel
        )
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val request = DownloadManager.Request(Uri.parse(split.cleanUrl))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setVisibleInDownloadsUi(false)
            .setDestinationUri(Uri.fromFile(targetFile))
            .setTitle(track.title)
            .setDescription(track.artistName)
        split.authToken
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                request.addRequestHeader("Authorization", authorizationHeaderValue(token))
            }
        val downloadId = runCatching { downloadManager.enqueue(request) }.getOrNull() ?: return null
        progressPoller.start()
        return NavidromeDownloadItem(
            serverId = selection.serverId,
            libraryId = selection.libraryId,
            trackId = track.id,
            albumId = track.albumId,
            albumSongCount = albumSongCount,
            artistId = track.artistId,
            title = track.title,
            artistName = track.artistName,
            albumName = track.albumName,
            coverUrl = track.coverUrl,
            durationSeconds = track.durationSeconds,
            formatLabel = track.formatLabel,
            status = NavidromeDownloadStatus.Downloading,
            progressPercent = 0,
            downloadId = downloadId,
            localPath = targetFile.absolutePath,
            errorMessage = null
        )
    }

    private suspend fun refreshProgress(): Unit = mutex.withLock {
        val items = mutableItems.value
        val activeItems = items.filter {
            (it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading) &&
                it.downloadId != null
        }
        val snapshots = mutableMapOf<Long, NavidromeDownloadItem>()
        if (activeItems.isNotEmpty()) {
            val query = DownloadManager.Query().setFilterById(*activeItems.mapNotNull { it.downloadId }.toLongArray())
            downloadManager.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val downloadId = cursor.getLongOrNull(DownloadManager.COLUMN_ID) ?: continue
                    val status = cursor.getIntOrNull(DownloadManager.COLUMN_STATUS)
                    val downloadedBytes = cursor.getLongOrNull(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR) ?: 0L
                    val totalBytes = cursor.getLongOrNull(DownloadManager.COLUMN_TOTAL_SIZE_BYTES) ?: -1L
                    val progress = if (downloadedBytes > 0L && totalBytes > 0L) {
                        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    val localUri = cursor.getStringOrNull(DownloadManager.COLUMN_LOCAL_URI)
                    val localPath = localUri
                        ?.takeIf { it.startsWith("file://") }
                        ?.let(Uri::parse)
                        ?.path
                    val matchedItem = activeItems.firstOrNull { it.downloadId == downloadId } ?: continue
                    val updatedItem = when (status) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> matchedItem.copy(
                            status = NavidromeDownloadStatus.Queued,
                            progressPercent = progress,
                            errorMessage = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                        DownloadManager.STATUS_RUNNING -> matchedItem.copy(
                            status = NavidromeDownloadStatus.Downloading,
                            progressPercent = progress,
                            errorMessage = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                        DownloadManager.STATUS_SUCCESSFUL -> matchedItem.copy(
                            status = NavidromeDownloadStatus.Completed,
                            progressPercent = 100,
                            localPath = localPath ?: matchedItem.localPath,
                            errorMessage = null,
                            updatedAtMs = System.currentTimeMillis()
                        )
                        DownloadManager.STATUS_FAILED -> matchedItem.copy(
                            status = NavidromeDownloadStatus.Failed,
                            progressPercent = 0,
                            errorMessage = "Download failed.",
                            updatedAtMs = System.currentTimeMillis()
                        )
                        else -> matchedItem
                    }
                    snapshots[downloadId] = updatedItem
                }
            }
        }
        val updatedItems = reconcileNavidromeDownloadItems(
            items = items,
            snapshotsByDownloadId = snapshots,
            localFileExists = ::localFileExists
        )
        mutableItems.value = updatedItems
        downloadStorage.persistItems(updatedItems)
        syncProgressPolling(updatedItems)
    }

    private fun syncProgressPolling(items: List<NavidromeDownloadItem>) {
        val hasActive = items.any {
            it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading
        }
        if (hasActive) {
            progressPoller.start()
        } else {
            progressPoller.stop()
        }
    }

    private fun replaceItems(transform: (List<NavidromeDownloadItem>) -> List<NavidromeDownloadItem>) {
        val updated = transform(mutableItems.value)
        mutableItems.value = updated
        downloadStorage.persistItems(updated)
        syncProgressPolling(updated)
    }

    private fun removeItem(item: NavidromeDownloadItem) {
        item.downloadId?.let { downloadManager.remove(it) }
        item.localPath?.let { path ->
            runCatching { File(path).delete() }
        }
        replaceItems { items ->
            items.filterNot {
                it.serverId == item.serverId &&
                    it.libraryId == item.libraryId &&
                    it.trackId == item.trackId
            }
        }
    }

    private fun buildTrackTargetFile(
        serverId: String,
        libraryId: String,
        trackId: String,
        formatLabel: String?
    ): File {
        val baseDir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(appContext.filesDir, "music")
        val extension = formatLabel
            ?.lowercase()
            ?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.isNotBlank() }
            ?: "bin"
        val safeServer = sanitizeFileSegment(serverId)
        val safeLibrary = sanitizeFileSegment(libraryId)
        val safeTrackId = sanitizeFileSegment(trackId)
        return File(baseDir, "navidrome/$safeServer/$safeLibrary/$safeTrackId.$extension")
    }

    private fun sanitizeFileSegment(value: String): String {
        return value.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "item" }
    }

    private fun localFileExists(path: String?): Boolean {
        val normalized = path?.trim().orEmpty()
        return normalized.isNotBlank() && File(normalized).exists()
    }
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val columnIndex = getColumnIndex(columnName)
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}

private fun Cursor.getIntOrNull(columnName: String): Int? {
    val columnIndex = getColumnIndex(columnName)
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getInt(columnIndex)
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val columnIndex = getColumnIndex(columnName)
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getString(columnIndex)
}

private fun String?.toPlayableLocalUri(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return Uri.fromFile(File(normalized)).toString()
}

internal fun reconcileNavidromeDownloadItems(
    items: List<NavidromeDownloadItem>,
    snapshotsByDownloadId: Map<Long, NavidromeDownloadItem>,
    localFileExists: (String?) -> Boolean
): List<NavidromeDownloadItem> {
    val now = System.currentTimeMillis()
    return items.map { item ->
        val updated = when {
            item.downloadId != null && snapshotsByDownloadId.containsKey(item.downloadId) -> {
                snapshotsByDownloadId.getValue(item.downloadId)
            }

            item.status == NavidromeDownloadStatus.Queued || item.status == NavidromeDownloadStatus.Downloading -> {
                item.copy(
                    status = NavidromeDownloadStatus.Failed,
                    progressPercent = 0,
                    errorMessage = "Download was interrupted.",
                    updatedAtMs = now
                )
            }

            else -> item
        }
        if (updated.status == NavidromeDownloadStatus.Completed && !localFileExists(updated.localPath)) {
            updated.copy(
                status = NavidromeDownloadStatus.Failed,
                progressPercent = 0,
                errorMessage = "Downloaded file is missing.",
                updatedAtMs = now
            )
        } else {
            updated
        }
    }
}
