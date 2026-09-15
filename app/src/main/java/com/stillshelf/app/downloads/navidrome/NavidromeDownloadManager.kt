package com.stillshelf.app.downloads.navidrome

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeCacheSizeOption
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.downloads.worker.DownloadProgressPoller
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val legacyDownloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableItems = MutableStateFlow(downloadStorage.loadItems())
    private val mutableFailures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val mutableActiveSelection = MutableStateFlow(NavidromeActiveSelection())
    private val progressPoller = DownloadProgressPoller(
        scope = scope,
        pollIntervalMs = 1000L,
        onTick = ::refreshProgress
    )

    val items: StateFlow<List<NavidromeDownloadItem>> = mutableItems.asStateFlow()
    val failures: SharedFlow<String> = mutableFailures.asSharedFlow()
    val activeItems: Flow<List<NavidromeDownloadItem>> = combine(mutableItems, mutableActiveSelection) { items, selection ->
        items.filter { item ->
            item.serverId == selection.serverId && item.libraryId == selection.libraryId
        }.filterNot { it.isPlaybackCache }
    }
    val activeCacheItems: Flow<List<NavidromeDownloadItem>> = combine(mutableItems, mutableActiveSelection) { items, selection ->
        items.filter { item ->
            item.serverId == selection.serverId &&
                item.libraryId == selection.libraryId &&
                item.isPlaybackCache &&
                item.status != NavidromeDownloadStatus.Failed
        }
    }

    fun activeCacheItemsSnapshot(): List<NavidromeDownloadItem> {
        val selection = mutableActiveSelection.value
        return mutableItems.value.filter { item ->
            item.serverId == selection.serverId &&
                item.libraryId == selection.libraryId &&
                item.isPlaybackCache &&
                item.status != NavidromeDownloadStatus.Failed
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
            cancelStalePlaybackCacheDownloads()
            refreshProgress()
        }
    }

    suspend fun cancelOutOfWindowCacheDownloads(keepTrackIds: Set<String>) = mutex.withLock {
        val selection = mutableActiveSelection.value
        val updated = mutableItems.value.map { item ->
            if (item.serverId == selection.serverId &&
                item.libraryId == selection.libraryId &&
                item.isPlaybackCache &&
                item.trackId !in keepTrackIds &&
                (item.status == NavidromeDownloadStatus.Queued || item.status == NavidromeDownloadStatus.Downloading)
            ) {
                cancelTransfer(item)
                item.copy(
                    status = NavidromeDownloadStatus.Failed,
                    progressPercent = 0,
                    downloadId = null,
                    workId = null,
                    errorMessage = null
                )
            } else {
                item
            }
        }
        if (updated == mutableItems.value) return@withLock
        mutableItems.value = updated
        downloadStorage.persistItems(updated)
    }

    suspend fun evictOutOfWindowCacheItems(keepTrackIds: Set<String>) = mutex.withLock {
        val selection = mutableActiveSelection.value
        val (toEvict, toKeep) = mutableItems.value.partition { item ->
            item.serverId == selection.serverId &&
                item.libraryId == selection.libraryId &&
                item.isPlaybackCache &&
                item.trackId !in keepTrackIds
        }
        if (toEvict.isEmpty()) return@withLock
        toEvict.forEach { item ->
            cancelTransfer(item)
        }
        mutableItems.value = toKeep
        downloadStorage.persistItems(toKeep)
    }

    private suspend fun cancelStalePlaybackCacheDownloads() {
        mutex.withLock {
            val stale = mutableItems.value.filter {
                it.isPlaybackCache &&
                    (it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading)
            }
            // Always cancel in-progress cache downloads on startup. Navidrome stream URLs
            // expire between sessions, so preserving in-progress cache downloads is futile —
            // background retries would otherwise keep using an expired URL.
            if (stale.isEmpty()) return
            stale.forEach(::cancelTransfer)
            val cleaned = mutableItems.value.map { item ->
                if (item.isPlaybackCache &&
                    (item.status == NavidromeDownloadStatus.Queued || item.status == NavidromeDownloadStatus.Downloading)
                ) {
                    item.copy(
                        status = NavidromeDownloadStatus.Failed,
                        progressPercent = 0,
                        downloadId = null,
                        workId = null,
                        errorMessage = null
                    )
                } else {
                    item
                }
            }
            mutableItems.value = cleaned
            downloadStorage.persistItems(cleaned)
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
        if (existing != null && !existing.isPlaybackCache && existing.status != NavidromeDownloadStatus.Failed) {
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
            albumSongCount = albumSongCount,
            isPlaybackCache = false
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
            .filter {
                it.id.isNotBlank() &&
                    it.streamUrl.isNotBlank() &&
                    !it.id.startsWith("radio:")
            }
        if (normalizedTracks.isEmpty()) {
            return@withLock AppResult.Error("Nothing to download.")
        }
        val existingByTrackId = mutableItems.value
            .filter { it.serverId == selection.serverId && it.libraryId == selection.libraryId }
            .associateBy { it.trackId }
        val allAlreadyPresent = normalizedTracks.all { track ->
            val existing = existingByTrackId[track.id]
            existing != null && !existing.isPlaybackCache && existing.status != NavidromeDownloadStatus.Failed
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
            if (existing != null && !existing.isPlaybackCache && existing.status != NavidromeDownloadStatus.Failed) {
                null
            } else {
                enqueueTrack(
                    selection = selection,
                    track = track,
                    albumSongCount = track.albumId?.let(albumSongCountByAlbumId::get),
                    isPlaybackCache = false
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

    suspend fun prefetchPlaybackQueue(tracks: List<NavidromeTrack>): AppResult<Int> = mutex.withLock {
        val cacheLimitOption = sessionPreferences.state.first().navidromeCacheSizeLimit
        if (NavidromeCacheSizeOption.toBytes(cacheLimitOption) == null) {
            return@withLock AppResult.Success(0)
        }
        val selection = resolveActiveSelection()
            ?: return@withLock AppResult.Error("Select a Navidrome server first.")
        val normalizedTracks = tracks
            .distinctBy { it.id }
            .filter {
                it.id.isNotBlank() &&
                    it.streamUrl.isNotBlank() &&
                    !it.id.startsWith("radio:")
            }
        if (normalizedTracks.isEmpty()) {
            return@withLock AppResult.Success(0)
        }

        val existingByTrackId = mutableItems.value
            .filter { it.serverId == selection.serverId && it.libraryId == selection.libraryId }
            .associateBy { it.trackId }

        val newItems = normalizedTracks.mapNotNull { track ->
            val existing = existingByTrackId[track.id]
            // A permanent download is authoritative — warmup must never overwrite it.
            if (existing != null && !existing.isPlaybackCache) return@mapNotNull null
            val needsDownload = existing == null ||
                existing.status == NavidromeDownloadStatus.Failed ||
                (existing.status == NavidromeDownloadStatus.Completed && localPlaybackUri(track) == null)
            if (!needsDownload) {
                null
            } else {
                enqueueTrack(
                    selection = selection,
                    track = track,
                    // Warmup downloads are queue-scoped cache entries, so do not stamp album completion metadata.
                    albumSongCount = null,
                    isPlaybackCache = true
                )
            }
        }

        if (newItems.isEmpty()) {
            return@withLock AppResult.Success(0)
        }

        replaceItems { items ->
            val newTrackIds = newItems.map { it.trackId }.toSet()
            items.filterNot {
                it.serverId == selection.serverId &&
                    it.libraryId == selection.libraryId &&
                    it.trackId in newTrackIds &&
                    it.isPlaybackCache
            } + newItems
        }

        AppResult.Success(newItems.size)
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

    suspend fun prunePlaybackCache(keepTrackIds: Set<String>): Int = mutex.withLock {
        val selection = resolveActiveSelection() ?: return@withLock 0
        val removable = mutableItems.value.filter {
            it.serverId == selection.serverId &&
                it.libraryId == selection.libraryId &&
                it.isPlaybackCache &&
                it.trackId !in keepTrackIds
        }
        if (removable.isEmpty()) return@withLock 0
        removeItems(removable)
        removable.size
    }

    suspend fun clearPlaybackCache(): Int = prunePlaybackCache(emptySet())

    fun currentPlaybackCacheSizeBytes(): Long {
        val selection = mutableActiveSelection.value
        return mutableItems.value
            .filter {
                it.serverId == selection.serverId &&
                    it.libraryId == selection.libraryId &&
                    it.isPlaybackCache &&
                    it.status != NavidromeDownloadStatus.Failed
            }
            .sumOf { it.effectivePlaybackCacheSizeBytes() }
    }

    suspend fun evictPlaybackCacheToLimit(limitBytes: Long) = mutex.withLock {
        val selection = resolveActiveSelection() ?: return@withLock
        val cacheItems = mutableItems.value
            .filter {
                it.serverId == selection.serverId &&
                    it.libraryId == selection.libraryId &&
                    it.isPlaybackCache &&
                    it.status != NavidromeDownloadStatus.Failed
            }
            .map { it to it.effectivePlaybackCacheSizeBytes() }
            .filter { (_, size) -> size > 0L }
            .sortedBy { (item, _) -> item.lastAccessedAtMs }
        var totalBytes = cacheItems.sumOf { (_, size) -> size }
        val toRemove = mutableListOf<NavidromeDownloadItem>()
        for ((item, size) in cacheItems) {
            if (totalBytes <= limitBytes) break
            toRemove += item
            totalBytes -= size
        }
        if (toRemove.isNotEmpty()) {
            removeItems(toRemove)
        }
    }

    fun touchCacheItem(trackId: String) {
        scope.launch {
            mutex.withLock {
                val selection = mutableActiveSelection.value
                val items = mutableItems.value
                val now = System.currentTimeMillis()
                val updated = items.map { item ->
                    if (item.isPlaybackCache &&
                        item.serverId == selection.serverId &&
                        item.libraryId == selection.libraryId &&
                        item.trackId == trackId
                    ) {
                        item.copy(lastAccessedAtMs = now)
                    } else {
                        item
                    }
                }
                if (updated != items) {
                    mutableItems.value = updated
                    downloadStorage.persistItems(updated)
                }
            }
        }
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
        albumSongCount: Int?,
        isPlaybackCache: Boolean
    ): NavidromeDownloadItem? {
        val split = splitAuthenticatedUrl(track.streamUrl)
        val destinationRelativePath = buildTrackRelativePath(
            serverId = selection.serverId,
            libraryId = selection.libraryId,
            trackId = track.id,
            formatLabel = track.formatLabel
        )
        if (!isPlaybackCache && appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) == null) {
            return null
        }
        val targetFile = buildTrackTargetFile(
            isPlaybackCache = isPlaybackCache,
            relativePath = destinationRelativePath
        )
        targetFile.parentFile?.mkdirs()
        val authorization = split.authToken
            ?.takeIf { it.isNotBlank() }
            ?.let(::authorizationHeaderValue)
        val request = NavidromeDownloadWorker.createRequest(
            url = split.cleanUrl,
            authorization = authorization,
            targetPath = targetFile.absolutePath
        )
        if (!NavidromeDownloadFileCoordinator.prepare(targetFile, request.id.toString())) {
            return null
        }
        if (runCatching { workManager.enqueue(request) }.isFailure) {
            NavidromeDownloadFileCoordinator.cancelAndDelete(targetFile, request.id.toString())
            return null
        }
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
            status = NavidromeDownloadStatus.Queued,
            progressPercent = 0,
            downloadId = null,
            workId = request.id.toString(),
            localPath = targetFile.absolutePath,
            errorMessage = null,
            isPlaybackCache = isPlaybackCache
        )
    }

    private suspend fun refreshProgress(): Unit = mutex.withLock {
        val items = mutableItems.value
        val activeItems = items.filter {
            it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading
        }
        val snapshots = activeItems.mapNotNull { item ->
            val workId = item.workId ?: return@mapNotNull null
            workId to queryWorkSnapshot(item, workId)
        }.toMap()
        activeItems
            .filter { it.workId == null && it.downloadId != null }
            .forEach { item -> item.downloadId?.let { id -> legacyDownloadManager.remove(id) } }
        val updatedItems = reconcileNavidromeDownloadItems(
            items = items,
            snapshotsByWorkId = snapshots,
            localFileExists = ::localFileExists
        )
        newNavidromeDownloadFailureMessage(items, updatedItems)?.let(mutableFailures::tryEmit)
        mutableItems.value = updatedItems
        downloadStorage.persistItems(updatedItems)
        syncProgressPolling(updatedItems)
    }

    private fun queryWorkSnapshot(item: NavidromeDownloadItem, workId: String): NavidromeDownloadItem {
        val uuid = runCatching { UUID.fromString(workId) }.getOrNull()
            ?: return item.toFailedDownload("Download tracking ID is invalid.")
        val workInfo = runCatching { workManager.getWorkInfoById(uuid).get() }.getOrNull()
            ?: return item.toFailedDownload("Download was interrupted.")
        val now = System.currentTimeMillis()
        return when (workInfo.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> item.copy(
                status = NavidromeDownloadStatus.Queued,
                progressPercent = workInfo.progress.getInt(NavidromeDownloadWorker.PROGRESS_PERCENT, item.progressPercent),
                errorMessage = null,
                updatedAtMs = now
            )
            WorkInfo.State.RUNNING -> item.copy(
                status = NavidromeDownloadStatus.Downloading,
                progressPercent = workInfo.progress.getInt(NavidromeDownloadWorker.PROGRESS_PERCENT, item.progressPercent),
                errorMessage = null,
                updatedAtMs = now
            )
            WorkInfo.State.SUCCEEDED -> item.copy(
                status = NavidromeDownloadStatus.Completed,
                progressPercent = 100,
                workId = null,
                fileSizeBytes = workInfo.outputData
                    .getLong(NavidromeDownloadWorker.OUTPUT_FILE_SIZE_BYTES, -1L)
                    .takeIf { it > 0L }
                    ?: item.localPath?.let { File(it).length() }?.takeIf { it > 0L },
                errorMessage = null,
                updatedAtMs = now
            )
            WorkInfo.State.FAILED -> item.toFailedDownload(
                workInfo.outputData.getString(NavidromeDownloadWorker.OUTPUT_ERROR_MESSAGE)
                    ?: "Download failed."
            )
            WorkInfo.State.CANCELLED -> item.toFailedDownload("Download was cancelled.")
        }
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
        removeItems(listOf(item))
    }

    private fun removeItems(itemsToRemove: List<NavidromeDownloadItem>) {
        if (itemsToRemove.isEmpty()) return
        itemsToRemove.forEach { item ->
            cancelTransfer(item)
        }
        data class RemovalKey(val serverId: String, val libraryId: String, val trackId: String, val isPlaybackCache: Boolean)
        val removalKeys = itemsToRemove.map { RemovalKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) }.toSet()
        replaceItems { items ->
            items.filterNot {
                RemovalKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) in removalKeys
            }
        }
    }

    private fun cancelTransfer(item: NavidromeDownloadItem) {
        item.localPath?.let { path ->
            runCatching { NavidromeDownloadFileCoordinator.cancelAndDelete(File(path), item.workId) }
        }
        item.workId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let(workManager::cancelWorkById)
        item.downloadId?.let { id -> legacyDownloadManager.remove(id) }
    }

    private fun buildTrackTargetFile(
        isPlaybackCache: Boolean,
        relativePath: String
    ): File {
        val baseDir = if (isPlaybackCache) {
            // Prefer app-private external cache storage so cached tracks do not consume internal storage.
            appContext.externalCacheDir?.let { File(it, "navidrome") }
                ?: appContext.getExternalFilesDir(null)?.let { File(it, "navidrome_cache") }
                ?: File(appContext.filesDir, "navidrome_cache")
        } else {
            appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(appContext.filesDir, "music")
        }
        return File(baseDir, relativePath)
    }

    private fun buildTrackRelativePath(
        serverId: String,
        libraryId: String,
        trackId: String,
        formatLabel: String?
    ): String {
        val extension = formatLabel
            ?.lowercase()
            ?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.isNotBlank() }
            ?: "bin"
        val safeServer = sanitizeFileSegment(serverId)
        val safeLibrary = sanitizeFileSegment(libraryId)
        val safeTrackId = sanitizeFileSegment(trackId)
        return "navidrome/$safeServer/$safeLibrary/$safeTrackId.$extension"
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

private fun NavidromeDownloadItem.effectivePlaybackCacheSizeBytes(): Long {
    return fileSizeBytes
        ?: localPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> runCatching { File(path).length() }.getOrNull() }
        ?: 0L
}

private fun String?.toPlayableLocalUri(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return Uri.fromFile(File(normalized)).toString()
}

private fun NavidromeDownloadItem.toFailedDownload(message: String): NavidromeDownloadItem {
    return copy(
        status = NavidromeDownloadStatus.Failed,
        progressPercent = 0,
        downloadId = null,
        workId = null,
        errorMessage = message,
        updatedAtMs = System.currentTimeMillis()
    )
}

internal fun reconcileNavidromeDownloadItems(
    items: List<NavidromeDownloadItem>,
    snapshotsByWorkId: Map<String, NavidromeDownloadItem>,
    localFileExists: (String?) -> Boolean
): List<NavidromeDownloadItem> {
    val now = System.currentTimeMillis()
    return items.map { item ->
        val updated = when {
            item.workId != null && snapshotsByWorkId.containsKey(item.workId) -> {
                snapshotsByWorkId.getValue(item.workId)
            }

            item.status == NavidromeDownloadStatus.Queued || item.status == NavidromeDownloadStatus.Downloading -> {
                item.toFailedDownload("Download was interrupted.")
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

internal fun newNavidromeDownloadFailureMessage(
    previousItems: List<NavidromeDownloadItem>,
    updatedItems: List<NavidromeDownloadItem>
): String? {
    data class ItemKey(
        val serverId: String,
        val libraryId: String,
        val trackId: String,
        val isPlaybackCache: Boolean
    )

    val activeKeys = previousItems
        .filter {
            !it.isPlaybackCache &&
                (it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading)
        }
        .map { ItemKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) }
        .toSet()
    val newFailures = updatedItems.filter {
        it.status == NavidromeDownloadStatus.Failed &&
            ItemKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) in activeKeys
    }
    if (newFailures.isEmpty()) return null
    val firstMessage = newFailures.firstNotNullOfOrNull { it.errorMessage?.takeIf(String::isNotBlank) }
        ?: "Download failed."
    return if (newFailures.size == 1) firstMessage else "${newFailures.size} downloads failed. $firstMessage"
}
