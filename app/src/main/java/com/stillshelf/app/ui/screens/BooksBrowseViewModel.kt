package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
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
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BooksBrowseUiState())
    val uiState: StateFlow<BooksBrowseUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreUiPreferences()
            loadBooks(isUserRefresh = false, clearBootstrap = true)
        }
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

    private suspend fun loadBooks(
        isUserRefresh: Boolean,
        clearBootstrap: Boolean = false
    ) {
        if (uiState.value.isLoading) return
        mutableUiState.update {
            it.copy(
                isBootstrapping = if (clearBootstrap) false else it.isBootstrapping,
                isLoading = true,
                isRefreshing = isUserRefresh,
                errorMessage = null
            )
        }

        when (val result = sessionRepository.fetchBooksForActiveLibrary()) {
            is AppResult.Success -> {
                mutableUiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isLoading = false,
                        isRefreshing = false,
                        books = result.value
                    )
                }
            }

            is AppResult.Error -> {
                mutableUiState.update {
                    it.copy(
                        isBootstrapping = false,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
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
    val collapseSeries: Boolean = true
)

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
    return runCatching { enumValueOf<T>(raw) }.getOrNull()
}
