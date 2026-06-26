package com.stillshelf.app.ui.screens.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.Library
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

data class PodcastSettingsUiState(
    val activeServerId: String? = null,
    val selectedLibraryId: String? = null,
    val availableLibraries: List<Library> = emptyList(),
    val isLoadingLibraries: Boolean = false,
    val errorMessage: String? = null,
    val podcastDownloadLocal: Boolean = false
)

@HiltViewModel
class PodcastSettingsViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastSettingsUiState())
    val uiState: StateFlow<PodcastSettingsUiState> = _uiState.asStateFlow()

    init {
        combine(
            sessionPreferences.state,
            sessionPreferences.getPodcastLibraryIds()
        ) { prefs, podcastLibraryIds ->
            val serverId = prefs.activeServerId
            val selectedId = if (serverId != null) podcastLibraryIds[serverId] else null
            _uiState.value = _uiState.value.copy(
                activeServerId = serverId,
                selectedLibraryId = selectedId,
                podcastDownloadLocal = prefs.podcastDownloadLocal
            )
        }.launchIn(viewModelScope)

        loadLibraries()
    }

    fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLibraries = true, errorMessage = null)
            when (val result = podcastRepository.fetchLibrariesWithMediaType()) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        availableLibraries = result.value,
                        isLoadingLibraries = false
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLibraries = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun selectLibrary(library: Library?) {
        val serverId = _uiState.value.activeServerId ?: return
        viewModelScope.launch {
            podcastRepository.setPodcastLibraryId(serverId, library?.id)
        }
    }

    fun setDownloadLocal(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setPodcastDownloadLocal(enabled)
        }
    }
}
