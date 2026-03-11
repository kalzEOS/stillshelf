package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.ui.common.withBookProgressMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BooksBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    companion object {
        private const val BOOKS_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
        private const val INITIAL_BOOKS_PAGE_SIZE: Int = 400
    }

    private val booksCacheByLibrary = mutableMapOf<String, List<BookSummary>>()
    private val booksCacheAtMsByLibrary = mutableMapOf<String, Long>()
    private var loadRequestId: Long = 0L

    private val mutableUiState = MutableStateFlow(BooksBrowseUiState())
    val uiState: StateFlow<BooksBrowseUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreUiPreferences()
            loadBooks(isUserRefresh = false, clearBootstrap = true)
        }
        observeBookProgressMutations()
        observeDownloadedState()
    }

    fun refresh() {
        viewModelScope.launch { loadBooks(isUserRefresh = true) }
    }

    fun setLayoutMode(value: BooksLayoutMode) {
        mutableUiState.update { it.copy(layoutMode = value) }
        viewModelScope.launch {
            sessionPreferences.setBooksLayoutMode(value.name)
        }
    }

    fun setStatusFilter(value: BooksStatusFilter) {
        mutableUiState.update { it.copy(statusFilter = value) }
        viewModelScope.launch {
            sessionPreferences.setBooksStatusFilter(value.name)
        }
    }

    fun setSortKey(value: BooksSortKey) {
        mutableUiState.update { it.copy(sortKey = value) }
        viewModelScope.launch {
            sessionPreferences.setBooksSortKey(value.name)
        }
    }

    fun toggleCollapseSeries() {
        val nextValue = !uiState.value.collapseSeries
        mutableUiState.update { it.copy(collapseSeries = nextValue) }
        viewModelScope.launch {
            sessionPreferences.setBooksCollapseSeries(nextValue)
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
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = true)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(actionMessage = "Marked as finished. Progress is now 100%.")
                    }
                    loadBooks(isUserRefresh = true)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsUnfinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(actionMessage = "Marked as unfinished.")
                    }
                    loadBooks(isUserRefresh = true)
                }
                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
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
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    loadBooks(isUserRefresh = true)
                }

                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun toggleDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val book = uiState.value.books.firstOrNull { it.id == bookId }
            if (book == null) {
                mutableUiState.update { it.copy(actionMessage = "Unable to find book for download.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(book)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = result.value.message) }
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
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

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private suspend fun restoreUiPreferences() {
        val pref = sessionPreferences.state.first()
        mutableUiState.update {
            it.copy(
                layoutMode = pref.booksLayoutMode
                    ?.let { raw -> enumValueOrNull<BooksLayoutMode>(raw) }
                    ?: BooksLayoutMode.Grid,
                statusFilter = pref.booksStatusFilter
                    ?.let { raw -> enumValueOrNull<BooksStatusFilter>(raw) }
                    ?: BooksStatusFilter.All,
                sortKey = pref.booksSortKey
                    ?.let { raw -> enumValueOrNull<BooksSortKey>(raw) }
                    ?: BooksSortKey.Title,
                collapseSeries = pref.booksCollapseSeries
            )
        }
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

    private suspend fun loadBooks(
        isUserRefresh: Boolean,
        clearBootstrap: Boolean = false
    ) {
        val requestId = ++loadRequestId
        val activeLibraryId = sessionRepository.observeSessionState().first().activeLibraryId
        if (!isUserRefresh && !activeLibraryId.isNullOrBlank()) {
            val cachedBooks = booksCacheByLibrary[activeLibraryId]
            val cachedAt = booksCacheAtMsByLibrary[activeLibraryId] ?: 0L
            val isFresh = (System.currentTimeMillis() - cachedAt) <= BOOKS_CACHE_MAX_AGE_MS
            if (!cachedBooks.isNullOrEmpty() && isFresh) {
                mutableUiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        books = cachedBooks
                    )
                }
                return
            }
        }
        mutableUiState.update {
            it.copy(
                isBootstrapping = if (clearBootstrap) false else it.isBootstrapping,
                isLoading = true,
                isRefreshing = isUserRefresh,
                errorMessage = null
            )
        }

        when (
            val initialResult = sessionRepository.fetchBooksForActiveLibrary(
                limit = INITIAL_BOOKS_PAGE_SIZE,
                page = 0,
                forceRefresh = isUserRefresh
            )
        ) {
            is AppResult.Success -> {
                if (requestId != loadRequestId) return
                val initialBooks = initialResult.value
                val shouldReconcileFullLibrary = initialBooks.size >= INITIAL_BOOKS_PAGE_SIZE
                mutableUiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isLoading = false,
                        isRefreshing = isUserRefresh && shouldReconcileFullLibrary,
                        books = initialBooks
                    )
                }

                if (!shouldReconcileFullLibrary) {
                    if (!activeLibraryId.isNullOrBlank()) {
                        booksCacheByLibrary[activeLibraryId] = initialBooks
                        booksCacheAtMsByLibrary[activeLibraryId] = System.currentTimeMillis()
                    }
                    return
                }

                when (val allBooksResult = sessionRepository.fetchAllBooksForActiveLibrary(forceRefresh = isUserRefresh)) {
                    is AppResult.Success -> {
                        if (requestId != loadRequestId) return
                        if (!activeLibraryId.isNullOrBlank()) {
                            booksCacheByLibrary[activeLibraryId] = allBooksResult.value
                            booksCacheAtMsByLibrary[activeLibraryId] = System.currentTimeMillis()
                        }
                        mutableUiState.update {
                            it.copy(
                                isBootstrapping = false,
                                isLoading = false,
                                isRefreshing = false,
                                books = allBooksResult.value
                            )
                        }
                    }

                    is AppResult.Error -> {
                        if (requestId != loadRequestId) return
                        mutableUiState.update {
                            it.copy(
                                isBootstrapping = false,
                                isLoading = false,
                                isRefreshing = false
                            )
                        }
                    }
                }
            }

            is AppResult.Error -> {
                if (requestId != loadRequestId) return
                mutableUiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = initialResult.message
                    )
                }
            }
        }
    }
}

data class BooksBrowseUiState(
    val isBootstrapping: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val books: List<BookSummary> = emptyList(),
    val errorMessage: String? = null,
    val layoutMode: BooksLayoutMode = BooksLayoutMode.Grid,
    val statusFilter: BooksStatusFilter = BooksStatusFilter.All,
    val sortKey: BooksSortKey = BooksSortKey.Title,
    val collapseSeries: Boolean = true,
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressByBookId: Map<String, Int> = emptyMap(),
    val actionMessage: String? = null
)

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
    return runCatching { enumValueOf<T>(raw) }.getOrNull()
}
