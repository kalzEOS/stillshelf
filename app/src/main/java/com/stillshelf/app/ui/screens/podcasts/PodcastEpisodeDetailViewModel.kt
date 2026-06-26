package com.stillshelf.app.ui.screens.podcasts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.PodcastRepository
import com.stillshelf.app.ui.navigation.MainRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PodcastEpisodeDetailUiState(
    val show: PodcastShow? = null,
    val episode: PodcastEpisode? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val rssWarning: String? = null
)

@HiltViewModel
class PodcastEpisodeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val showId: String = checkNotNull(savedStateHandle[MainRoute.PODCAST_EPISODE_SHOW_ID_ARG])
    private val episodeId: String = checkNotNull(savedStateHandle[MainRoute.PODCAST_EPISODE_ID_ARG])

    private val _uiState = MutableStateFlow(PodcastEpisodeDetailUiState())
    val uiState: StateFlow<PodcastEpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    fun markEpisodePlayed() {
        val episode = _uiState.value.episode ?: return
        updateEpisodeLocally { it.copy(isFinished = true, progressPercent = 1.0) }
        viewModelScope.launch {
            podcastRepository.syncEpisodeProgress(
                showId = showId,
                episodeId = episodeId,
                currentTimeSeconds = episode.durationSeconds ?: 0.0,
                durationSeconds = episode.durationSeconds,
                isFinished = true
            )
        }
    }

    fun markEpisodeUnplayed() {
        updateEpisodeLocally { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
        viewModelScope.launch {
            podcastRepository.syncEpisodeProgress(
                showId = showId,
                episodeId = episodeId,
                currentTimeSeconds = 0.0,
                durationSeconds = null,
                isFinished = false
            )
        }
    }

    fun resetEpisodeProgress() {
        updateEpisodeLocally { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
        viewModelScope.launch {
            podcastRepository.syncEpisodeProgress(
                showId = showId,
                episodeId = episodeId,
                currentTimeSeconds = 0.0,
                durationSeconds = null,
                isFinished = false
            )
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = podcastRepository.fetchPodcastShowDetail(showId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val episode = result.value.episodes.firstOrNull { it.id == episodeId }
                    _uiState.value = _uiState.value.copy(
                        show = result.value.show,
                        episode = episode,
                        isLoading = false,
                        errorMessage = if (episode == null) "Episode not found." else null,
                        rssWarning = result.value.rssError
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

    private inline fun updateEpisodeLocally(transform: (PodcastEpisode) -> PodcastEpisode) {
        _uiState.value = _uiState.value.copy(
            episode = _uiState.value.episode?.let(transform)
        )
    }
}
