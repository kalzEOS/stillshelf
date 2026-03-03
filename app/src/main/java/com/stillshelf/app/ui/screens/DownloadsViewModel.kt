package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
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
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = mutableUiState.asStateFlow()

    init {
        restoreListMode()
        observeDownloads()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = false, errorMessage = null) }
    }

    fun removeDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            bookDownloadManager.removeDownload(bookId)
            mutableUiState.update { state ->
                state.copy(
                    downloadedBookIds = state.downloadedBookIds - bookId,
                    books = state.books.filterNot { it.id == bookId },
                    downloadItems = state.downloadItems.filterNot { it.bookId == bookId },
                    actionMessage = "Download removed"
                )
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
            bookDownloadManager.items.collect { items ->
                val visible = items
                    .filter { it.status != DownloadStatus.Failed }
                    .sortedByDescending { it.updatedAtMs }
                val books = visible.map {
                    BookSummary(
                        id = it.bookId,
                        libraryId = "",
                        title = it.title,
                        authorName = it.authorName,
                        narratorName = null,
                        durationSeconds = it.durationSeconds,
                        coverUrl = it.coverUrl
                    )
                }
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        downloadItems = visible,
                        downloadedBookIds = visible
                            .filter { it.status == DownloadStatus.Completed }
                            .map { item -> item.bookId }
                            .toSet(),
                        books = books
                    )
                }
            }
        }
    }
}
