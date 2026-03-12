package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.ui.common.buildLibraryScopedDownloadKey
import com.stillshelf.app.ui.common.applyResolvedPlaybackProgress
import com.stillshelf.app.ui.common.completedDownloadUiKeys
import com.stillshelf.app.ui.common.toLiveBookProgressMutation
import com.stillshelf.app.ui.common.withBookProgressMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val isLoading: Boolean = false,
    val books: List<BookSummary> = emptyList(),
    val downloadItems: List<DownloadItem> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val listMode: Boolean = true,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val bookDownloadManager: BookDownloadManager,
    private val sessionPreferences: SessionPreferences,
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()
    private var hydratedLibraryBooksById: Map<String, BookSummary> = emptyMap()
    private var hydrationMissBookIds: Set<String> = emptySet()

    init {
        restoreListMode()
        observeDownloads()
        observeBookProgressMutations()
        observeLivePlaybackState()
    }

    fun refresh() {
        hydratedLibraryBooksById = emptyMap()
        hydrationMissBookIds = emptySet()
        mutableUiState.update { it.copy(isLoading = false, errorMessage = null) }
    }

    fun removeDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            bookDownloadManager.removeDownload(bookId)
            mutableUiState.update { state ->
                val removedDownloadKey = state.downloadItems
                    .firstOrNull { it.bookId == bookId }
                    ?.let { item -> buildLibraryScopedDownloadKey(item.libraryId, item.bookId) }
                state.copy(
                    downloadedBookIds = removedDownloadKey?.let(state.downloadedBookIds::minus)
                        ?: state.downloadedBookIds,
                    books = state.books.filterNot { it.id == bookId },
                    downloadItems = state.downloadItems.filterNot { it.bookId == bookId },
                    actionMessage = "Download removed"
                )
            }
        }
    }

    fun resetBookProgress(bookId: String) {
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
                    playbackController.applyResolvedPlaybackProgress(
                        bookId = bookId,
                        progress = result.value,
                        isFinished = false
                    )
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    fun setListMode(listMode: Boolean) {
        mutableUiState.update { it.copy(listMode = listMode) }
        viewModelScope.launch {
            sessionPreferences.setDownloadedListMode(listMode)
        }
    }

    private fun restoreListMode() {
        viewModelScope.launch {
            val pref = sessionPreferences.state.first()
            mutableUiState.update { it.copy(listMode = pref.downloadedListMode) }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            bookDownloadManager.activeItems.collect { items ->
                val visible = items
                    .filter { it.status != DownloadStatus.Failed }
                    .sortedByDescending { it.updatedAtMs }
                val visibleBookIds = visible.map { it.bookId }.toSet()
                val missingHydrationIds = visibleBookIds.filterNot { bookId ->
                    hydratedLibraryBooksById.containsKey(bookId) || hydrationMissBookIds.contains(bookId)
                }
                if (missingHydrationIds.isNotEmpty()) {
                    when (val result = sessionRepository.fetchAllBooksForActiveLibrary(forceRefresh = false)) {
                        is AppResult.Success -> {
                            val fetchedById = result.value.associateBy { it.id }
                            hydratedLibraryBooksById = hydratedLibraryBooksById + fetchedById
                            hydrationMissBookIds = hydrationMissBookIds + visibleBookIds.filterNot {
                                hydratedLibraryBooksById.containsKey(it)
                            }
                        }
                        is AppResult.Error -> Unit
                    }
                }
                val books = visible.map { item ->
                    val hydrated = hydratedLibraryBooksById[item.bookId]
                    if (hydrated != null) {
                        hydrated.copy(
                            title = item.title.ifBlank { hydrated.title },
                            authorName = item.authorName.ifBlank { hydrated.authorName },
                            durationSeconds = item.durationSeconds ?: hydrated.durationSeconds,
                            coverUrl = item.coverUrl ?: hydrated.coverUrl
                        )
                    } else {
                        BookSummary(
                            id = item.bookId,
                            libraryId = item.libraryId,
                            title = item.title,
                            authorName = item.authorName,
                            narratorName = null,
                            durationSeconds = item.durationSeconds,
                            coverUrl = item.coverUrl
                        )
                    }
                }
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        downloadItems = visible,
                        downloadedBookIds = visible.completedDownloadUiKeys(),
                        books = books
                    )
                }
            }
        }
    }

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(
                        books = state.books.map { it.withBookProgressMutation(mutation) }
                    )
                }
            }
        }
    }

    private fun observeLivePlaybackState() {
        viewModelScope.launch {
            playbackController.uiState.collect { playbackState ->
                val mutation = playbackState.toLiveBookProgressMutation() ?: return@collect
                mutableUiState.update { state ->
                    if (state.books.none { it.id == mutation.bookId }) {
                        state
                    } else {
                        state.copy(books = state.books.map { it.withBookProgressMutation(mutation) })
                    }
                }
            }
        }
    }
}
