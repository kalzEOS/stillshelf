package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
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
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        observeActiveLibrary()
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
        val cachedResult = sessionRepository.fetchCachedHomeFeed()
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
        refreshNetwork()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNetwork()
        }
    }

    private suspend fun refreshNetwork() {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

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
            .take(6)
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

}

data class HomeUiState(
    val isLoading: Boolean = false,
    val libraryName: String = "Library",
    val continueListening: List<ContinueListeningItem> = emptyList(),
    val recentlyAdded: List<BookSummary> = emptyList(),
    val recentSeries: List<SeriesStackSummary> = emptyList(),
    val authorImageUrls: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)
