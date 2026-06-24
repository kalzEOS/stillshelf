package com.stillshelf.app.ui.screens.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PodcastsLayoutMode { Grid, List }

enum class PodcastsSortKey(val label: String, val hint: String) {
    Title("Title", "A – Z"),
    Author("Author", "A – Z"),
    DateAdded("Date Added", "Newest first"),
    EpisodeCount("Episode Count", "Most first")
}

data class PodcastsUiState(
    val podcastLibraryId: String? = null,
    val shows: List<PodcastShow> = emptyList(),
    val searchQuery: String = "",
    val layoutMode: PodcastsLayoutMode = PodcastsLayoutMode.Grid,
    val sortKey: PodcastsSortKey = PodcastsSortKey.Title,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastsUiState())
    val uiState: StateFlow<PodcastsUiState> = _uiState.asStateFlow()

    private var allShows: List<PodcastShow> = emptyList()

    init {
        combine(
            sessionPreferences.state,
            sessionPreferences.getPodcastLibraryIds()
        ) { prefs, podcastLibraryIds ->
            val serverId = prefs.activeServerId
            val libraryId = if (serverId != null) podcastLibraryIds[serverId] else null
            val previousLibraryId = _uiState.value.podcastLibraryId
            _uiState.value = _uiState.value.copy(podcastLibraryId = libraryId)
            if (libraryId != null && libraryId != previousLibraryId) {
                loadShows()
            }
        }.launchIn(viewModelScope)
    }

    fun refresh() = loadShows()

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            shows = applyFilterAndSort(allShows, query, _uiState.value.sortKey)
        )
    }

    fun setLayoutMode(value: PodcastsLayoutMode) {
        _uiState.value = _uiState.value.copy(layoutMode = value)
    }

    fun setSortKey(value: PodcastsSortKey) {
        _uiState.value = _uiState.value.copy(
            sortKey = value,
            shows = applyFilterAndSort(allShows, _uiState.value.searchQuery, value)
        )
    }

    private fun applyFilterAndSort(
        shows: List<PodcastShow>,
        query: String,
        sortKey: PodcastsSortKey
    ): List<PodcastShow> {
        val filtered = if (query.isBlank()) {
            shows
        } else {
            val q = query.trim().lowercase()
            shows.filter { show ->
                show.title.lowercase().contains(q) ||
                    show.author?.lowercase()?.contains(q) == true
            }
        }
        return when (sortKey) {
            PodcastsSortKey.Title -> filtered.sortedBy { it.title.lowercase() }
            PodcastsSortKey.Author -> filtered.sortedBy { it.author?.lowercase() ?: "￿" }
            PodcastsSortKey.DateAdded -> filtered.sortedByDescending { it.addedAtMs ?: Long.MIN_VALUE }
            PodcastsSortKey.EpisodeCount -> filtered.sortedByDescending { it.numEpisodes }
        }
    }

    private fun loadShows() {
        _uiState.value.podcastLibraryId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = podcastRepository.fetchPodcastShows(forceRefresh = true)) {
                is AppResult.Success -> {
                    allShows = result.value
                    _uiState.value = _uiState.value.copy(
                        shows = applyFilterAndSort(
                            allShows,
                            _uiState.value.searchQuery,
                            _uiState.value.sortKey
                        ),
                        isLoading = false
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}
