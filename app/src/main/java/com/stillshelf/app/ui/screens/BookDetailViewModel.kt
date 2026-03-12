package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.util.resolveUnfinishedProgressState
import com.stillshelf.app.data.repo.DetailRefreshPolicy
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.domain.usecase.SkipIntroOutroUseCase
import com.stillshelf.app.domain.usecase.toUserMessage
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.common.activeDownloadProgressByUiKey
import com.stillshelf.app.ui.common.completedDownloadUiKeys
import com.stillshelf.app.ui.common.downloadProgressForBook
import com.stillshelf.app.ui.common.withBookProgressMutation
import com.stillshelf.app.ui.navigation.DetailRoute
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

data class BookDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val detail: BookDetail? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val selectedTab: String = "About",
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressPercent: Int? = null
)

internal fun BookDetailUiState.beginRefresh(
    hasLocalDetail: Boolean,
    silent: Boolean
): BookDetailUiState {
    return if (!silent || !hasLocalDetail) {
        copy(
            isLoading = !hasLocalDetail,
            isRefreshing = hasLocalDetail,
            errorMessage = null
        )
    } else {
        this
    }
}

internal fun BookDetailUiState.applyPersistedDetail(
    detail: BookDetail?
): BookDetailUiState {
    return if (detail == null) {
        copy(
            detail = null,
            isLoading = isLoading && this.detail == null
        )
    } else {
        copy(
            isLoading = false,
            detail = detail,
            errorMessage = null,
            progressPercent = detail.book.progressPercent,
            currentTimeSeconds = detail.book.currentTimeSeconds
        )
    }
}

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    private data class FinishedUndoSnapshot(
        val currentTimeSeconds: Double,
        val durationSeconds: Double?,
        val progressPercent: Double?,
        val wasFinished: Boolean
    )

    private val bookId: String = savedStateHandle.get<String>(DetailRoute.BOOK_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(BookDetailUiState(isLoading = true))
    val uiState: StateFlow<BookDetailUiState> = mutableUiState.asStateFlow()
    val playbackUiState: StateFlow<PlaybackUiState> = playbackController.uiState
    private val mutableMarkFinishedUndoEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val markFinishedUndoEvents: SharedFlow<Unit> = mutableMarkFinishedUndoEvents.asSharedFlow()
    private var pendingFinishedUndoSnapshot: FinishedUndoSnapshot? = null

    init {
        observePreferences()
        observePersistedDetail()
        observeBookProgressMutations()
        refresh(policy = DetailRefreshPolicy.IfStale)
        refreshProgress()
    }

    fun refresh() {
        refresh(policy = DetailRefreshPolicy.Force)
    }

    fun refreshSilent() {
        refresh(policy = DetailRefreshPolicy.IfStale, silent = true)
    }

    fun onScreenStarted() {
        refreshSilent()
        refreshProgress(silent = true)
    }

    private fun refresh(policy: DetailRefreshPolicy, silent: Boolean = false) {
        if (bookId.isBlank()) {
            mutableUiState.update { it.copy(isLoading = false, errorMessage = "Invalid book id.") }
            return
        }
        val hasLocalDetail = uiState.value.detail != null
        mutableUiState.update { state -> state.beginRefresh(hasLocalDetail = hasLocalDetail, silent = silent) }
        viewModelScope.launch {
            when (val result = sessionRepository.refreshBookDetail(bookId, policy = policy)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            actionMessage = null
                        )
                    }
                    refreshProgress(silent = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = if (silent && state.detail != null) state.errorMessage else result.message
                        )
                    }
                }
            }
        }
    }

    private fun observePersistedDetail() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            sessionRepository.observeBookDetail(bookId).collect { detail ->
                mutableUiState.update { state -> state.applyPersistedDetail(detail) }
            }
        }
    }

    private fun refreshProgress(silent: Boolean = false) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val progressResult = sessionRepository.fetchPlaybackProgress(bookId)) {
                is AppResult.Success -> {
                    val progress = progressResult.value
                    mutableUiState.update {
                        it.copy(
                            progressPercent = progress?.progressPercent ?: it.detail?.book?.progressPercent,
                            currentTimeSeconds = progress?.currentTimeSeconds ?: it.detail?.book?.currentTimeSeconds
                        )
                    }
                }

                is AppResult.Error -> {
                    if (!silent) {
                        mutableUiState.update { it.copy(errorMessage = progressResult.message) }
                    }
                }
            }
        }
    }

    fun addToCollection() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.addBookToDefaultCollection(bookId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(actionMessage = "Added to Collections")
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(actionMessage = result.message)
                    }
                }
            }
        }
    }

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                if (mutation.bookId != bookId) return@collect
                mutableUiState.update { state ->
                    state.copy(
                        detail = state.detail?.copy(
                            book = state.detail.book.withBookProgressMutation(mutation)
                        ),
                        progressPercent = mutation.progressPercent,
                        currentTimeSeconds = mutation.currentTimeSeconds
                    )
                }
            }
        }
    }

    fun markAsFinished() {
        if (bookId.isBlank()) return
        val playbackState = playbackUiState.value
        val preservedProgress = run {
            val currentDurationSeconds = when {
                playbackState.book?.id == bookId && playbackState.durationMs > 0L -> {
                    playbackState.durationMs / 1000.0
                }

                else -> uiState.value.detail?.book?.durationSeconds
            }
            val currentTimeSeconds = when {
                playbackState.book?.id == bookId -> playbackState.positionMs.coerceAtLeast(0L) / 1000.0
                else -> uiState.value.currentTimeSeconds ?: uiState.value.detail?.book?.currentTimeSeconds
            } ?: 0.0
            val progressPercent = when {
                currentDurationSeconds != null && currentDurationSeconds > 0.0 -> {
                    (currentTimeSeconds / currentDurationSeconds).coerceIn(0.0, 1.0)
                }

                else -> uiState.value.progressPercent ?: uiState.value.detail?.book?.progressPercent ?: 0.0
            }
            PlaybackProgress(
                progressPercent = progressPercent,
                currentTimeSeconds = currentTimeSeconds,
                durationSeconds = currentDurationSeconds
            )
        }
        viewModelScope.launch {
            val undoSnapshot = captureFinishedUndoSnapshot()
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = true,
                    preservedProgress = preservedProgress
                )
            ) {
                is AppResult.Success -> {
                    if (playbackUiState.value.book?.id == bookId) {
                        playbackController.stopAndResetBookToStart(bookId)
                    }
                    pendingFinishedUndoSnapshot = undoSnapshot
                    mutableMarkFinishedUndoEvents.tryEmit(Unit)
                    mutableUiState.update { state ->
                        val detailDuration = state.detail?.book?.durationSeconds?.coerceAtLeast(0.0) ?: 0.0
                        state.copy(
                            detail = state.detail?.copy(
                                book = state.detail.book.copy(
                                    isFinished = true,
                                    progressPercent = 1.0,
                                    currentTimeSeconds = detailDuration
                                )
                            ),
                            progressPercent = 1.0,
                            currentTimeSeconds = detailDuration
                        )
                    }
                    refresh(policy = DetailRefreshPolicy.Force)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun undoMarkAsFinished() {
        val snapshot = pendingFinishedUndoSnapshot ?: return
        if (bookId.isBlank()) return
        pendingFinishedUndoSnapshot = null
        viewModelScope.launch {
            when (
                val result = sessionRepository.syncPlaybackProgress(
                    bookId = bookId,
                    currentTimeSeconds = snapshot.currentTimeSeconds,
                    durationSeconds = snapshot.durationSeconds,
                    isFinished = snapshot.wasFinished
                )
            ) {
                is AppResult.Success -> {
                    if (playbackUiState.value.book?.id == bookId) {
                        playbackController.stopAndRestoreBookProgress(
                            bookId = bookId,
                            currentTimeSeconds = snapshot.currentTimeSeconds,
                            durationSeconds = snapshot.durationSeconds,
                            isFinished = snapshot.wasFinished
                        )
                    }
                    mutableUiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                book = state.detail.book.copy(
                                    isFinished = snapshot.wasFinished,
                                    progressPercent = snapshot.progressPercent,
                                    currentTimeSeconds = snapshot.currentTimeSeconds
                                )
                            ),
                            progressPercent = snapshot.progressPercent,
                            currentTimeSeconds = snapshot.currentTimeSeconds,
                            actionMessage = "Undid mark as finished."
                        )
                    }
                    refresh(policy = DetailRefreshPolicy.Force)
                }

                is AppResult.Error -> {
                    pendingFinishedUndoSnapshot = snapshot
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun markAsUnfinished() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = false)) {
                is AppResult.Success -> {
                    pendingFinishedUndoSnapshot = null
                    val restoredProgress = result.value
                    if (playbackUiState.value.book?.id == bookId) {
                        playbackController.stopAndRestoreBookProgress(
                            bookId = bookId,
                            currentTimeSeconds = restoredProgress.currentTimeSeconds ?: 0.0,
                            durationSeconds = restoredProgress.durationSeconds,
                            isFinished = false
                        )
                    }
                    mutableUiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                book = state.detail.book.copy(
                                    isFinished = false,
                                    progressPercent = restoredProgress.progressPercent,
                                    currentTimeSeconds = restoredProgress.currentTimeSeconds
                                )
                            ),
                            progressPercent = restoredProgress.progressPercent,
                            currentTimeSeconds = restoredProgress.currentTimeSeconds,
                            actionMessage = "Marked as unfinished."
                        )
                    }
                    refresh(policy = DetailRefreshPolicy.Force)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun resetBookProgress() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    pendingFinishedUndoSnapshot = null
                    if (playbackUiState.value.book?.id == bookId) {
                        playbackController.stopAndResetBookToBeginning(bookId)
                    }
                    mutableUiState.update { state ->
                        state.copy(
                            detail = state.detail?.copy(
                                book = state.detail.book.copy(
                                    isFinished = false,
                                    progressPercent = 0.0,
                                    currentTimeSeconds = 0.0
                                )
                            ),
                            progressPercent = 0.0,
                            currentTimeSeconds = 0.0,
                            actionMessage = "Book progress reset."
                        )
                    }
                    refresh(policy = DetailRefreshPolicy.Force)
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun toggleDownload(): Unit {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val book = uiState.value.detail?.book
            if (book == null) {
                mutableUiState.update { it.copy(actionMessage = "Book details not ready yet.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(book)) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(actionMessage = result.value.message)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun setSelectedTab(tab: String) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    fun playChapter(startSeconds: Double) {
        if (bookId.isBlank()) return
        val positionMs = (startSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val playbackState = playbackUiState.value
        if (playbackState.book?.id == bookId) {
            playbackController.seekToPositionMs(positionMs = positionMs, commit = true)
            if (!playbackState.isPlaying) {
                playbackController.togglePlayPause()
            }
        } else {
            playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
        }
    }

    fun skipIntroOrOutro() {
        if (bookId.isBlank()) return
        val detail = uiState.value.detail ?: run {
            mutableUiState.update { it.copy(actionMessage = "Book details not ready yet.") }
            return
        }
        val currentSeconds = if (playbackUiState.value.book?.id == bookId) {
            playbackUiState.value.positionMs.coerceAtLeast(0L) / 1000.0
        } else {
            uiState.value.currentTimeSeconds?.coerceAtLeast(0.0) ?: 0.0
        }
        val skipResult = SkipIntroOutroUseCase.resolve(
            chapters = detail.chapters,
            currentPositionSeconds = currentSeconds,
            bookDurationSeconds = detail.book.durationSeconds
        )
        val targetSeconds = skipResult.targetSeconds
        if (targetSeconds == null) {
            val message = skipResult.failureReason?.toUserMessage() ?: "Unable to skip chapter."
            mutableUiState.update { it.copy(actionMessage = message) }
            return
        }
        val positionMs = (targetSeconds * 1000.0).toLong().coerceAtLeast(0L)
        playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
        mutableUiState.update { it.copy(actionMessage = "Skipped chapter.") }
    }

    fun playBookmark(bookmark: BookBookmark) {
        if (bookId.isBlank()) return
        val bookmarkSeconds = bookmark.timeSeconds ?: return
        val positionMs = (bookmarkSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
    }

    fun editBookmark(bookmark: BookBookmark, newTitle: String) {
        if (bookId.isBlank()) return
        val normalizedTitle = newTitle.trim()
        if (normalizedTitle.isBlank()) {
            mutableUiState.update { it.copy(actionMessage = "Bookmark title can't be empty.") }
            return
        }
        val previousDetail = uiState.value.detail ?: run {
            mutableUiState.update { it.copy(actionMessage = "Book details not ready yet.") }
            return
        }
        mutableUiState.update { state ->
            state.copy(
                detail = state.detail?.copy(
                    bookmarks = state.detail.bookmarks.map { existing ->
                        if (bookmarkMatches(existing, bookmark)) {
                            existing.copy(title = normalizedTitle)
                        } else {
                            existing
                        }
                    }
                )
            )
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
                    mutableUiState.update { it.copy(actionMessage = "Bookmark updated.") }
                    refresh(policy = DetailRefreshPolicy.Force)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            detail = previousDetail,
                            actionMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun deleteBookmark(bookmark: BookBookmark) {
        if (bookId.isBlank()) return
        val previousDetail = uiState.value.detail ?: run {
            mutableUiState.update { it.copy(actionMessage = "Book details not ready yet.") }
            return
        }
        mutableUiState.update { state ->
            state.copy(
                detail = state.detail?.copy(
                    bookmarks = state.detail.bookmarks.filterNot { existing ->
                        bookmarkMatches(existing, bookmark)
                    }
                )
            )
        }
        viewModelScope.launch {
            when (val result = sessionRepository.deleteBookmark(bookId = bookId, bookmark = bookmark)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Bookmark deleted.") }
                    refresh(policy = DetailRefreshPolicy.Force)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            detail = previousDetail,
                            actionMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            bookDownloadManager.activeItems.collect { items ->
                val progressPercent = items
                    .activeDownloadProgressByUiKey()
                    .downloadProgressForBook(uiState.value.detail?.book)
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = items.completedDownloadUiKeys(),
                        downloadProgressPercent = progressPercent
                    )
                }
            }
        }
    }

    private suspend fun captureFinishedUndoSnapshot(): FinishedUndoSnapshot {
        val detailBook = uiState.value.detail?.book
        val progress = when (val result = sessionRepository.fetchPlaybackProgress(bookId)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> null
        }
        val progressCurrentSeconds = progress?.currentTimeSeconds
        val progressPercentFromServer = progress?.progressPercent
        val currentTimeSeconds = when {
            playbackUiState.value.book?.id == bookId -> playbackUiState.value.positionMs.coerceAtLeast(0L) / 1000.0
            progressCurrentSeconds != null -> progressCurrentSeconds.coerceAtLeast(0.0)
            else -> (uiState.value.currentTimeSeconds ?: 0.0).coerceAtLeast(0.0)
        }
        val durationSeconds = progress?.durationSeconds ?: detailBook?.durationSeconds
        val progressPercent = when {
            progressPercentFromServer != null -> progressPercentFromServer.coerceIn(0.0, 1.0)
            durationSeconds != null && durationSeconds > 0.0 -> {
                (currentTimeSeconds / durationSeconds).coerceIn(0.0, 1.0)
            }
            else -> null
        }
        val wasFinished = when {
            detailBook?.isFinished == true -> true
            progressPercent != null -> progressPercent >= 0.995
            durationSeconds != null && durationSeconds > 0.0 -> (currentTimeSeconds / durationSeconds) >= 0.995
            else -> false
        }
        return FinishedUndoSnapshot(
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            progressPercent = progressPercent,
            wasFinished = wasFinished
        )
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
