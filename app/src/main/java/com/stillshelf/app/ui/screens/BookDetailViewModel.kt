package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.domain.usecase.SkipIntroOutroUseCase
import com.stillshelf.app.domain.usecase.toUserMessage
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class BookDetailUiState(
    val isLoading: Boolean = false,
    val detail: BookDetail? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val selectedTab: String = "About",
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressPercent: Int? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    private val bookId: String = savedStateHandle.get<String>(DetailRoute.BOOK_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(BookDetailUiState(isLoading = true))
    val uiState: StateFlow<BookDetailUiState> = mutableUiState.asStateFlow()
    val playbackUiState: StateFlow<PlaybackUiState> = playbackController.uiState

    init {
        observePreferences()
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (bookId.isBlank()) {
            mutableUiState.update { it.copy(isLoading = false, errorMessage = "Invalid book id.") }
            return
        }
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val progress = when (val progressResult = sessionRepository.fetchPlaybackProgress(bookId)) {
                        is AppResult.Success -> progressResult.value
                        is AppResult.Error -> null
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            detail = result.value,
                            actionMessage = null,
                            progressPercent = progress?.progressPercent,
                            currentTimeSeconds = progress?.currentTimeSeconds
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
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

    fun markAsFinished() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = true)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as finished. Progress is now 100%.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
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
                    mutableUiState.update { it.copy(actionMessage = "Marked as unfinished. Progress reset to 0%.") }
                    refresh(forceRefresh = true)
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
        playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
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
                    refresh(forceRefresh = true)
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
                    refresh(forceRefresh = true)
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
            bookDownloadManager.items.collect { items ->
                val downloadedIds = items
                    .filter { it.status == DownloadStatus.Completed }
                    .map { it.bookId }
                    .toSet()
                val progressPercent = items.firstOrNull {
                    it.bookId == bookId &&
                        (it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading)
                }?.progressPercent
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = downloadedIds,
                        downloadProgressPercent = progressPercent
                    )
                }
            }
        }
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
