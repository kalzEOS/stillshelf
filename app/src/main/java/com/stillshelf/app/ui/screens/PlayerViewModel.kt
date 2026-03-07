package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.PendingFinishedRestoreSnapshot
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.util.resolveUnfinishedProgressState
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.navigation.MainRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PlayerControlPrefs(
    val skipForwardSeconds: Int = 15,
    val skipBackwardSeconds: Int = 15
)

private sealed interface PendingSleepTimerRequest {
    data class Minutes(val minutes: Int) : PendingSleepTimerRequest
    object EndOfChapter : PendingSleepTimerRequest
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    private data class FinishedUndoSnapshot(
        val bookId: String,
        val currentTimeSeconds: Double,
        val durationSeconds: Double?,
        val wasFinished: Boolean
    )

    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState
    private val mutablePreviewItem = MutableStateFlow<ContinueListeningItem?>(null)
    val previewItem: StateFlow<ContinueListeningItem?> = mutablePreviewItem.asStateFlow()
    private val mutableChapters = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters: StateFlow<List<BookChapter>> = mutableChapters.asStateFlow()
    private val mutableBookmarks = MutableStateFlow<List<BookBookmark>>(emptyList())
    val bookmarks: StateFlow<List<BookBookmark>> = mutableBookmarks.asStateFlow()
    private val mutableActionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = mutableActionMessage.asStateFlow()
    private val mutableControlPrefs = MutableStateFlow(PlayerControlPrefs())
    val controlPrefs: StateFlow<PlayerControlPrefs> = mutableControlPrefs.asStateFlow()
    private val mutableDownloadedBookIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedBookIds: StateFlow<Set<String>> = mutableDownloadedBookIds.asStateFlow()
    private val mutableDownloadProgressPercent = MutableStateFlow<Int?>(null)
    val downloadProgressPercent: StateFlow<Int?> = mutableDownloadProgressPercent.asStateFlow()
    private val mutableMarkFinishedUndoEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val markFinishedUndoEvents: SharedFlow<Unit> = mutableMarkFinishedUndoEvents.asSharedFlow()
    private var currentDownloadItems: List<DownloadItem> = emptyList()
    private var loadedBookId: String? = null
    private var pendingSleepTimerRequest: PendingSleepTimerRequest? = null
    private var pendingFinishedUndoSnapshot: FinishedUndoSnapshot? = null

    init {
        observeControlPrefs()
        observeDownloads()
        val bookId = savedStateHandle.get<String>(MainRoute.PLAYER_BOOK_ID_ARG).orEmpty()
        val startSeconds = savedStateHandle
            .get<String>(MainRoute.PLAYER_START_SECONDS_ARG)
            ?.toDoubleOrNull()
            ?.coerceAtLeast(0.0)
        val startPositionMs = startSeconds?.let { (it * 1000.0).toLong() }
        if (bookId.isNotBlank()) {
            loadBookMetadata(bookId, forceRefresh = true)
            playbackController.playBook(bookId, startPositionMs = startPositionMs)
        } else if (playbackController.uiState.value.book == null) {
            val cachedItem = playbackController.getCachedContinueListeningItem()
            if (cachedItem != null) {
                mutablePreviewItem.value = cachedItem
                loadBookMetadata(cachedItem.book.id, forceRefresh = true)
                syncCurrentDownloadState()
            } else {
                viewModelScope.launch {
                    when (val result = sessionRepository.fetchMiniPlayerItem()) {
                        is AppResult.Success -> {
                            mutablePreviewItem.value = result.value
                            result.value?.book?.id?.let { loadBookMetadata(it, forceRefresh = true) }
                            syncCurrentDownloadState()
                        }

                        is AppResult.Error -> Unit
                    }
                }
            }
        }

        viewModelScope.launch {
            playbackController.uiState.collect { playbackState ->
                if (playbackState.book != null) {
                    mutablePreviewItem.value = null
                    loadBookMetadata(playbackState.book.id)
                    applyPendingSleepTimerRequestIfNeeded()
                }
                syncCurrentDownloadState()
            }
        }
    }

    fun onPlayPauseClick() {
        val playbackState = playbackController.uiState.value
        if (playbackState.book != null) {
            playbackController.togglePlayPause()
            return
        }
        val previewBookId = previewItem.value?.book?.id.orEmpty()
        if (previewBookId.isNotBlank()) {
            playbackController.playBook(previewBookId)
        }
    }

    fun onRewindClick() {
        playbackController.seekBy(deltaMs = -(controlPrefs.value.skipBackwardSeconds * 1000L))
    }

    fun onForwardClick() {
        playbackController.seekBy(deltaMs = (controlPrefs.value.skipForwardSeconds * 1000L))
    }

    fun onScrubProgress(progressFraction: Float) {
        playbackController.seekToProgress(progressFraction = progressFraction, commit = false)
    }

    fun onScrubProgressFinished(progressFraction: Float) {
        playbackController.seekToProgress(progressFraction = progressFraction, commit = true)
    }

    fun onDismissPlayer() {
        playbackController.saveProgressSnapshot()
    }

    fun refreshBookMetadata() {
        val activeBookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        if (activeBookId.isNullOrBlank()) return
        loadBookMetadata(bookId = activeBookId, forceRefresh = true, silent = true)
    }

    fun seekToPositionMs(positionMs: Long, commit: Boolean) {
        playbackController.seekToPositionMs(positionMs = positionMs, commit = commit)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
    }

    fun setSoftToneLevel(level: Float) {
        playbackController.setSoftToneLevel(level)
    }

    fun setBoostLevel(level: Float) {
        playbackController.setBoostLevel(level)
    }

    fun startSleepTimerMinutes(minutes: Int) {
        val normalizedMinutes = minutes.coerceAtLeast(1)
        if (uiState.value.book != null) {
            playbackController.startSleepTimerMinutes(normalizedMinutes)
            return
        }
        val queued = queueSleepTimerAndStartPlayback(
            request = PendingSleepTimerRequest.Minutes(normalizedMinutes)
        )
        if (!queued) {
            mutableActionMessage.value = "Start playback to use the timer."
        }
    }

    fun startSleepTimerEndOfChapter() {
        if (uiState.value.book == null) {
            val queued = queueSleepTimerAndStartPlayback(request = PendingSleepTimerRequest.EndOfChapter)
            if (!queued) {
                mutableActionMessage.value = "Unable to set end-of-chapter timer."
            }
            return
        }
        viewModelScope.launch {
            val started = playbackController.startSleepTimerEndOfChapter()
            if (!started) {
                mutableActionMessage.value = "Unable to set end-of-chapter timer."
            }
        }
    }

    fun clearSleepTimer() {
        playbackController.clearSleepTimer()
    }

    fun extendSleepTimerOneMinute() {
        playbackController.extendSleepTimerOneMinute()
    }

    fun dismissSleepTimerExpiredPrompt() {
        playbackController.dismissSleepTimerExpiredPrompt()
    }

    fun refreshAudioOutputs() {
        playbackController.refreshAudioOutputDevices()
    }

    fun selectAudioOutputDevice(deviceId: Int?) {
        val applied = playbackController.selectAudioOutputDevice(deviceId)
        if (!applied) {
            mutableActionMessage.value = "Unable to switch output on this device."
        }
    }

    fun toggleDownload() {
        val book = uiState.value.book ?: previewItem.value?.book
        if (book == null) {
            mutableActionMessage.value = "Book details not ready yet."
            return
        }
        viewModelScope.launch {
            when (val result = bookDownloadManager.toggleDownload(book)) {
                is AppResult.Success -> {
                    mutableActionMessage.value = result.value.message
                }

                is AppResult.Error -> {
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun markAsFinished() {
        setBookFinishedState(finished = true)
    }

    fun markAsUnfinished() {
        setBookFinishedState(finished = false)
    }

    fun undoMarkAsFinished() {
        val snapshot = pendingFinishedUndoSnapshot ?: return
        pendingFinishedUndoSnapshot = null
        viewModelScope.launch {
            when (
                val result = sessionRepository.syncPlaybackProgress(
                    bookId = snapshot.bookId,
                    currentTimeSeconds = snapshot.currentTimeSeconds,
                    durationSeconds = snapshot.durationSeconds,
                    isFinished = snapshot.wasFinished
                )
            ) {
                is AppResult.Success -> {
                    sessionPreferences.setPendingFinishedRestoreSnapshot(null)
                    if (uiState.value.book?.id == snapshot.bookId) {
                        playbackController.stopAndRestoreBookProgress(
                            bookId = snapshot.bookId,
                            currentTimeSeconds = snapshot.currentTimeSeconds,
                            durationSeconds = snapshot.durationSeconds,
                            isFinished = snapshot.wasFinished
                        )
                    } else {
                        val preview = previewItem.value
                        if (preview?.book?.id == snapshot.bookId) {
                            val restoredProgressPercent = snapshot.durationSeconds
                                ?.takeIf { it > 0.0 }
                                ?.let { duration -> (snapshot.currentTimeSeconds / duration).coerceIn(0.0, 1.0) }
                            mutablePreviewItem.value = preview.copy(
                                book = preview.book.copy(
                                    isFinished = snapshot.wasFinished,
                                    progressPercent = restoredProgressPercent,
                                    currentTimeSeconds = snapshot.currentTimeSeconds
                                ),
                                progressPercent = restoredProgressPercent,
                                currentTimeSeconds = snapshot.currentTimeSeconds
                            )
                        }
                    }
                    mutableActionMessage.value = "Undid mark as finished."
                    loadBookMetadata(bookId = snapshot.bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    pendingFinishedUndoSnapshot = snapshot
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun jumpToSeconds(seconds: Double) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id ?: return
        val positionMs = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val playbackState = uiState.value
        if (playbackState.book?.id == bookId) {
            playbackController.seekToPositionMs(positionMs = positionMs, commit = true)
            if (!playbackState.isPlaying) {
                playbackController.togglePlayPause()
            }
        } else {
            playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
        }
    }

    fun addBookmark(positionSeconds: Double, title: String?) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        if (bookId.isNullOrBlank()) {
            mutableActionMessage.value = "Unable to add bookmark right now."
            return
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.createBookmark(
                    bookId = bookId,
                    timeSeconds = positionSeconds.coerceAtLeast(0.0),
                    title = title?.trim().takeUnless { it.isNullOrBlank() }
                )
            ) {
                is AppResult.Success -> {
                    mutableActionMessage.value = "Bookmark added."
                    loadBookMetadata(bookId = bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun editBookmark(bookmark: BookBookmark, newTitle: String) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        if (bookId.isNullOrBlank()) {
            mutableActionMessage.value = "Unable to edit bookmark right now."
            return
        }
        val normalizedTitle = newTitle.trim()
        if (normalizedTitle.isBlank()) {
            mutableActionMessage.value = "Bookmark title can't be empty."
            return
        }
        val previousBookmarks = mutableBookmarks.value
        mutableBookmarks.update { current ->
            current.map { existing ->
                if (bookmarkMatches(existing, bookmark)) {
                    existing.copy(title = normalizedTitle)
                } else {
                    existing
                }
            }
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.updateBookmark(
                    bookId = bookId,
                    bookmark = bookmark,
                    newTitle = normalizedTitle
                )
            ) {
                is AppResult.Success -> {
                    mutableActionMessage.value = "Bookmark updated."
                    loadBookMetadata(bookId = bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableBookmarks.value = previousBookmarks
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun deleteBookmark(bookmark: BookBookmark) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        if (bookId.isNullOrBlank()) {
            mutableActionMessage.value = "Unable to delete bookmark right now."
            return
        }
        val previousBookmarks = mutableBookmarks.value
        mutableBookmarks.update { current ->
            current.filterNot { existing -> bookmarkMatches(existing, bookmark) }
        }
        viewModelScope.launch {
            when (val result = sessionRepository.deleteBookmark(bookId = bookId, bookmark = bookmark)) {
                is AppResult.Success -> {
                    mutableActionMessage.value = "Bookmark deleted."
                    loadBookMetadata(bookId = bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableBookmarks.value = previousBookmarks
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableActionMessage.value = null
    }

    private fun queueSleepTimerAndStartPlayback(request: PendingSleepTimerRequest): Boolean {
        val preview = previewItem.value ?: return false
        val previewBookId = preview.book.id
        if (previewBookId.isBlank()) return false
        pendingSleepTimerRequest = request
        val startPositionSeconds = preview.currentTimeSeconds
            ?: preview.book.currentTimeSeconds
            ?: 0.0
        val startPositionMs = (startPositionSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        playbackController.playBook(bookId = previewBookId, startPositionMs = startPositionMs)
        mutableActionMessage.value = "Starting playback and timer..."
        return true
    }

    private fun applyPendingSleepTimerRequestIfNeeded() {
        val pending = pendingSleepTimerRequest ?: return
        if (uiState.value.book == null) return
        pendingSleepTimerRequest = null
        when (pending) {
            is PendingSleepTimerRequest.Minutes -> {
                playbackController.startSleepTimerMinutes(pending.minutes)
            }

            PendingSleepTimerRequest.EndOfChapter -> {
                viewModelScope.launch {
                    val started = playbackController.startSleepTimerEndOfChapter()
                    if (!started) {
                        mutableActionMessage.value = "Unable to set end-of-chapter timer."
                    }
                }
            }
        }
    }

    private fun setBookFinishedState(finished: Boolean) {
        val activeBook = uiState.value.book ?: previewItem.value?.book
        val bookId = activeBook?.id
        if (bookId.isNullOrBlank()) {
            mutableActionMessage.value = "Book details not ready yet."
            return
        }
        viewModelScope.launch {
            val undoSnapshot = if (finished) captureFinishedUndoSnapshot(book = activeBook) else null
            val preservedProgress = if (finished) {
                val currentDurationSeconds = when {
                    uiState.value.book?.id == bookId && uiState.value.durationMs > 0L -> {
                        uiState.value.durationMs / 1000.0
                    }

                    else -> activeBook.durationSeconds
                }
                val currentTimeSeconds = when {
                    uiState.value.book?.id == bookId -> uiState.value.positionMs.coerceAtLeast(0L) / 1000.0
                    else -> previewItem.value?.currentTimeSeconds ?: activeBook.currentTimeSeconds
                } ?: 0.0
                val progressPercent = when {
                    currentDurationSeconds != null && currentDurationSeconds > 0.0 -> {
                        (currentTimeSeconds / currentDurationSeconds).coerceIn(0.0, 1.0)
                    }

                    else -> previewItem.value?.progressPercent ?: activeBook.progressPercent ?: 0.0
                }
                PlaybackProgress(
                    progressPercent = progressPercent,
                    currentTimeSeconds = currentTimeSeconds,
                    durationSeconds = currentDurationSeconds
                )
            } else {
                null
            }
            val unfinishedState = if (finished) {
                null
            } else {
                pendingFinishedUndoSnapshot?.takeIf { it.bookId == bookId }?.let { snapshot ->
                    resolveUnfinishedProgressState(
                        currentTimeSeconds = snapshot.currentTimeSeconds,
                        durationSeconds = snapshot.durationSeconds,
                        progressPercent = null
                    )
                } ?: resolveUnfinishedProgressState(
                    currentTimeSeconds = when {
                        uiState.value.book?.id == bookId -> uiState.value.positionMs.coerceAtLeast(0L) / 1000.0
                        else -> previewItem.value?.currentTimeSeconds ?: activeBook.currentTimeSeconds
                    },
                    durationSeconds = when {
                        uiState.value.book?.id == bookId && uiState.value.durationMs > 0L -> {
                            uiState.value.durationMs / 1000.0
                        }

                        else -> activeBook.durationSeconds
                    },
                    progressPercent = previewItem.value?.progressPercent ?: activeBook.progressPercent
                )
            }
            val persistedSnapshot = if (!finished) {
                sessionPreferences.getPendingFinishedRestoreSnapshot()?.takeIf { it.bookId == bookId }
            } else {
                null
            }
            if (!finished && persistedSnapshot != null) {
                when (
                    val result = sessionRepository.syncPlaybackProgress(
                        bookId = bookId,
                        currentTimeSeconds = persistedSnapshot.currentTimeSeconds,
                        durationSeconds = persistedSnapshot.durationSeconds,
                        isFinished = persistedSnapshot.wasFinished
                    )
                ) {
                    is AppResult.Success -> {
                        pendingFinishedUndoSnapshot = null
                        sessionPreferences.setPendingFinishedRestoreSnapshot(null)
                        if (uiState.value.book?.id == bookId) {
                            playbackController.stopAndRestoreBookProgress(
                                bookId = bookId,
                                currentTimeSeconds = persistedSnapshot.currentTimeSeconds,
                                durationSeconds = persistedSnapshot.durationSeconds,
                                isFinished = persistedSnapshot.wasFinished
                            )
                        } else {
                            mutablePreviewItem.value = ContinueListeningItem(
                                book = activeBook.copy(
                                    isFinished = persistedSnapshot.wasFinished,
                                    progressPercent = persistedSnapshot.progressPercent,
                                    currentTimeSeconds = persistedSnapshot.currentTimeSeconds
                                ),
                                progressPercent = persistedSnapshot.progressPercent,
                                currentTimeSeconds = persistedSnapshot.currentTimeSeconds
                            )
                        }
                        mutableActionMessage.value = "Marked as unfinished."
                        loadBookMetadata(bookId = bookId, forceRefresh = true)
                    }

                    is AppResult.Error -> {
                        mutableActionMessage.value = result.message
                    }
                }
                return@launch
            }
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = finished,
                    preservedProgress = preservedProgress
                )
            ) {
                is AppResult.Success -> {
                    if (finished) {
                        pendingFinishedUndoSnapshot = undoSnapshot
                        if (undoSnapshot != null) {
                            sessionPreferences.setPendingFinishedRestoreSnapshot(
                                PendingFinishedRestoreSnapshot(
                                    bookId = undoSnapshot.bookId,
                                    currentTimeSeconds = undoSnapshot.currentTimeSeconds,
                                    durationSeconds = undoSnapshot.durationSeconds,
                                    wasFinished = undoSnapshot.wasFinished,
                                    progressPercent = undoSnapshot.durationSeconds
                                        ?.takeIf { it > 0.0 }
                                        ?.let { duration ->
                                            (undoSnapshot.currentTimeSeconds / duration).coerceIn(0.0, 1.0)
                                        }
                                )
                            )
                        }
                        if (uiState.value.book?.id == bookId) {
                            playbackController.stopAndResetBookToStart(bookId)
                        }
                        mutableMarkFinishedUndoEvents.tryEmit(Unit)
                        if (uiState.value.book?.id != bookId) {
                            val duration = activeBook.durationSeconds?.coerceAtLeast(0.0) ?: 0.0
                            mutablePreviewItem.value = ContinueListeningItem(
                                book = activeBook.copy(
                                    isFinished = true,
                                    progressPercent = 1.0,
                                    currentTimeSeconds = duration
                                ),
                                progressPercent = 1.0,
                                currentTimeSeconds = duration
                            )
                        }
                    }
                    if (finished) {
                        mutableActionMessage.value = null
                    } else {
                        pendingFinishedUndoSnapshot = null
                        sessionPreferences.setPendingFinishedRestoreSnapshot(null)
                        val restoredState = unfinishedState ?: resolveUnfinishedProgressState(
                            currentTimeSeconds = activeBook.currentTimeSeconds,
                            durationSeconds = activeBook.durationSeconds,
                            progressPercent = activeBook.progressPercent
                        )
                        if (uiState.value.book?.id == bookId) {
                            playbackController.stopAndRestoreBookProgress(
                                bookId = bookId,
                                currentTimeSeconds = restoredState.currentTimeSeconds,
                                durationSeconds = restoredState.durationSeconds,
                                isFinished = false
                            )
                        } else {
                            mutablePreviewItem.value = ContinueListeningItem(
                                book = activeBook.copy(
                                    isFinished = false,
                                    progressPercent = restoredState.progressPercent,
                                    currentTimeSeconds = restoredState.currentTimeSeconds
                                ),
                                progressPercent = restoredState.progressPercent,
                                currentTimeSeconds = restoredState.currentTimeSeconds
                            )
                        }
                        mutableActionMessage.value = "Marked as unfinished."
                    }
                    loadBookMetadata(bookId = bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    private suspend fun captureFinishedUndoSnapshot(book: BookSummary?): FinishedUndoSnapshot {
        val fallbackCurrentTime = when {
            uiState.value.book?.id == book?.id -> uiState.value.positionMs.coerceAtLeast(0L) / 1000.0
            else -> previewItem.value?.currentTimeSeconds
                ?: previewItem.value?.book?.currentTimeSeconds
                ?: 0.0
        }
        val progress = when (val result = sessionRepository.fetchPlaybackProgress(book?.id.orEmpty())) {
            is AppResult.Success -> result.value
            is AppResult.Error -> null
        }
        val progressCurrentSeconds = progress?.currentTimeSeconds
        val progressPercentFromServer = progress?.progressPercent
        val currentTimeSeconds = progressCurrentSeconds?.coerceAtLeast(0.0)
            ?: fallbackCurrentTime.coerceAtLeast(0.0)
        val durationSeconds = progress?.durationSeconds ?: book?.durationSeconds
        val progressPercent = when {
            progressPercentFromServer != null -> progressPercentFromServer.coerceIn(0.0, 1.0)
            durationSeconds != null && durationSeconds > 0.0 -> {
                (currentTimeSeconds / durationSeconds).coerceIn(0.0, 1.0)
            }
            else -> null
        }
        val wasFinished = when {
            book?.isFinished == true -> true
            progressPercent != null -> progressPercent >= 0.995
            durationSeconds != null && durationSeconds > 0.0 -> (currentTimeSeconds / durationSeconds) >= 0.995
            else -> false
        }
        return FinishedUndoSnapshot(
            bookId = book?.id.orEmpty(),
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            wasFinished = wasFinished
        )
    }

    private fun loadBookMetadata(bookId: String, forceRefresh: Boolean = false, silent: Boolean = false) {
        if (bookId.isBlank()) return
        if (!forceRefresh && loadedBookId == bookId) return
        loadedBookId = bookId
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableChapters.value = result.value.chapters
                    mutableBookmarks.value = result.value.bookmarks
                }

                is AppResult.Error -> {
                    if (forceRefresh && !silent) {
                        mutableActionMessage.value = result.message
                    } else if (!forceRefresh) {
                        mutableChapters.value = emptyList()
                        mutableBookmarks.value = emptyList()
                    }
                }
            }
        }
    }

    private fun observeControlPrefs() {
        viewModelScope.launch {
            sessionPreferences.state.collect { pref ->
                mutableControlPrefs.update {
                    it.copy(
                        skipForwardSeconds = pref.skipForwardSeconds.coerceIn(10, 60),
                        skipBackwardSeconds = pref.skipBackwardSeconds.coerceIn(10, 60)
                    )
                }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            bookDownloadManager.items.collect { items ->
                syncCurrentDownloadState(items)
            }
        }
    }

    private fun syncCurrentDownloadState(items: List<DownloadItem> = currentDownloadItems) {
        currentDownloadItems = items
        mutableDownloadedBookIds.value = items
            .filter { it.status == DownloadStatus.Completed }
            .map { it.bookId }
            .toSet()
        val activeBookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        val progress = activeBookId?.let { id ->
            items.firstOrNull { item ->
                item.bookId == id &&
                    (item.status == DownloadStatus.Queued || item.status == DownloadStatus.Downloading)
            }?.progressPercent
        }
        mutableDownloadProgressPercent.value = progress
    }

    private fun bookmarkMatches(source: BookBookmark, target: BookBookmark): Boolean {
        val sourceId = source.id.trim()
        val targetId = target.id.trim()
        if (sourceId.isNotBlank() && targetId.isNotBlank() && sourceId.equals(targetId, ignoreCase = true)) {
            return true
        }
        if (!source.libraryItemId.equals(target.libraryItemId, ignoreCase = true)) {
            return false
        }
        val sourceTime = source.timeSeconds
        val targetTime = target.timeSeconds
        val timeMatches = sourceTime != null && targetTime != null && abs(sourceTime - targetTime) <= 2.0
        val titleMatches = !source.title.isNullOrBlank() &&
            !target.title.isNullOrBlank() &&
            source.title.trim().equals(target.title.trim(), ignoreCase = true)
        return timeMatches || titleMatches
    }
}
