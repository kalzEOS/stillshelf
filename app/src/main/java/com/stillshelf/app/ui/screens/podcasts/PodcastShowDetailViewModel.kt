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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastShowDetailUiState(
    val show: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val episodeQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PodcastShowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: PodcastRepository
) : ViewModel() {

    private val showId: String = checkNotNull(
        savedStateHandle[MainRoute.PODCAST_SHOW_ID_ARG]
    )

    private val _uiState = MutableStateFlow(PodcastShowDetailUiState())
    val uiState: StateFlow<PodcastShowDetailUiState> = _uiState.asStateFlow()

    private var allEpisodes: List<PodcastEpisode> = emptyList()

    init {
        load()
    }

    fun refresh() = load()

    fun setEpisodeQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            episodeQuery = query,
            episodes = applyEpisodeFilter(allEpisodes, query)
        )
    }

    private fun applyEpisodeFilter(episodes: List<PodcastEpisode>, query: String): List<PodcastEpisode> {
        if (query.isBlank()) return episodes
        val q = query.trim().lowercase()
        return episodes.filter { it.title.lowercase().contains(q) }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = podcastRepository.fetchPodcastShowDetail(showId)) {
                is AppResult.Success -> {
                    allEpisodes = result.value.episodes
                    _uiState.value = _uiState.value.copy(
                        show = result.value.show,
                        episodes = applyEpisodeFilter(allEpisodes, _uiState.value.episodeQuery),
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

    fun markEpisodePlayed(episodeId: String) {
        val episode = allEpisodes.firstOrNull { it.id == episodeId } ?: return
        updateEpisodeLocally(episodeId) { it.copy(isFinished = true, progressPercent = 1.0) }
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

    fun markEpisodeUnplayed(episodeId: String) {
        updateEpisodeLocally(episodeId) { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
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

    private inline fun updateEpisodeLocally(episodeId: String, transform: (PodcastEpisode) -> PodcastEpisode) {
        allEpisodes = allEpisodes.map { ep -> if (ep.id == episodeId) transform(ep) else ep }
        _uiState.value = _uiState.value.copy(
            episodes = applyEpisodeFilter(allEpisodes, _uiState.value.episodeQuery)
        )
    }
}
