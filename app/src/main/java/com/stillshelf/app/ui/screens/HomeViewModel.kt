package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    companion object {
        private const val HOME_FEED_CACHE_MAX_AGE_MS: Long = 10 * 60 * 1000L
    }

    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        observeActiveLibrary()
        observeDownloadedState()
    }

    private fun observeActiveLibrary() {
        viewModelScope.launch {
            sessionRepository.observeSessionState()
                .map { it.activeLibraryId }
                .distinctUntilChanged()
                .collect { libraryId ->
                    if (libraryId.isNullOrBlank()) {
                        mutableUiState.update { HomeUiState() }
                    } else {
                        loadCachedThenMaybeRefresh()
                    }
                }
        }
    }

    private suspend fun loadCachedThenMaybeRefresh() {
        val cachedResult = sessionRepository.fetchCachedHomeFeed(
            maxAgeMs = HOME_FEED_CACHE_MAX_AGE_MS
        )
        if (cachedResult is AppResult.Success && cachedResult.value != null) {
            val resolvedRecentSeries = resolveRecentSeries(
                backendRecentSeries = cachedResult.value.recentSeries,
                recentlyAdded = cachedResult.value.recentlyAdded
            )
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    libraryName = cachedResult.value.libraryName,
                    continueListening = cachedResult.value.continueListening,
                    recentlyAdded = cachedResult.value.recentlyAdded,
                    recentSeries = resolvedRecentSeries,
                    authorImageUrls = cachedResult.value.authorImageUrls
                )
            }
            return
        }
        refreshNetwork(showLoading = true)
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNetwork(showLoading = true)
        }
    }

    fun addToCollection(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.addBookToDefaultCollection(bookId)) {
                is AppResult.Success -> mutableUiState.update { it.copy(actionMessage = "Added to Collections") }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsFinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId, finished = true)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as finished. Progress is now 100%.") }
                    refreshNetwork(showLoading = false)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsUnfinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as unfinished. Progress reset to 0%.") }
                    refreshNetwork(showLoading = false)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun removeFromContinueListening(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            continueListening = it.continueListening.filterNot { item -> item.book.id == bookId },
                            actionMessage = "Removed from Continue Listening"
                        )
                    }
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun toggleDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val target = findBookById(bookId)
            if (target == null) {
                mutableUiState.update { it.copy(actionMessage = "Unable to find book for download.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(target)) {
                is AppResult.Success -> mutableUiState.update { it.copy(actionMessage = result.value.message) }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private suspend fun refreshNetwork(showLoading: Boolean) {
        if (uiState.value.isLoading) return
        if (showLoading) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        } else {
            mutableUiState.update { it.copy(errorMessage = null) }
        }

        when (val result = sessionRepository.fetchHomeFeed()) {
            is AppResult.Success -> {
                val resolvedRecentSeries = resolveRecentSeries(
                    backendRecentSeries = result.value.recentSeries,
                    recentlyAdded = result.value.recentlyAdded
                )
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        libraryName = result.value.libraryName,
                        continueListening = result.value.continueListening,
                        recentlyAdded = result.value.recentlyAdded,
                        recentSeries = resolvedRecentSeries,
                        authorImageUrls = result.value.authorImageUrls
                    )
                }
            }

            is AppResult.Error -> {
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = if (state.recentlyAdded.isNotEmpty() || state.continueListening.isNotEmpty()) {
                            null
                        } else {
                            result.message
                        }
                    )
                }
            }
        }
    }

    private fun resolveRecentSeries(
        backendRecentSeries: List<SeriesStackSummary>,
        recentlyAdded: List<BookSummary>
    ): List<SeriesStackSummary> {
        if (backendRecentSeries.isNotEmpty()) return backendRecentSeries

        val grouped = linkedMapOf<String, MutableList<BookSummary>>()
        recentlyAdded.forEach { book ->
            val rawSeriesName = book.seriesName?.trim().orEmpty()
            if (rawSeriesName.isBlank()) return@forEach
            val key = normalizeSeriesKey(rawSeriesName)
            grouped.getOrPut(key) { mutableListOf() }.add(book)
        }

        return grouped.values
            .mapNotNull { books ->
                if (books.size <= 1) {
                    null
                } else {
                    val lead = books.first()
                    SeriesStackSummary(
                        seriesName = cleanSeriesName(lead.seriesName.orEmpty()),
                        leadBook = lead,
                        count = books.size
                    )
                }
            }
            .take(20)
    }

    private fun normalizeSeriesKey(value: String): String {
        return cleanSeriesName(value).lowercase()
    }

    private fun cleanSeriesName(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s*#\\d+.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun observeDownloadedState() {
        viewModelScope.launch {
            bookDownloadManager.items.collect { items ->
                val downloadedIds = items
                    .filter { it.status == DownloadStatus.Completed }
                    .map { it.bookId }
                    .toSet()
                val progressByBookId = items
                    .filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
                    .associate { it.bookId to it.progressPercent.coerceIn(0, 100) }
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = downloadedIds,
                        downloadProgressByBookId = progressByBookId
                    )
                }
            }
        }
    }

    private fun findBookById(bookId: String): BookSummary? {
        val state = uiState.value
        return state.continueListening.firstOrNull { it.book.id == bookId }?.book
            ?: state.recentlyAdded.firstOrNull { it.id == bookId }
            ?: state.recentSeries.firstOrNull { it.leadBook.id == bookId }?.leadBook
    }

}

data class HomeUiState(
    val isLoading: Boolean = false,
    val libraryName: String = "Library",
    val continueListening: List<ContinueListeningItem> = emptyList(),
    val recentlyAdded: List<BookSummary> = emptyList(),
    val recentSeries: List<SeriesStackSummary> = emptyList(),
    val authorImageUrls: Map<String, String> = emptyMap(),
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressByBookId: Map<String, Int> = emptyMap(),
    val actionMessage: String? = null,
    val errorMessage: String? = null
)
