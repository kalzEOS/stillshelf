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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastsUiState(
    val podcastLibraryId: String? = null,
    val shows: List<PodcastShow> = emptyList(),
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

    fun refresh() {
        loadShows()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadShows() {
        val libraryId = _uiState.value.podcastLibraryId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = podcastRepository.fetchPodcastShows(forceRefresh = true)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        shows = result.value,
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
