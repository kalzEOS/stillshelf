package com.stillshelf.app.ui.screens.podcasts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.PodcastRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.ui.navigation.MainRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PodcastEpisodeDetailUiState(
    val show: PodcastShow? = null,
    val episode: PodcastEpisode? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val rssWarning: String? = null,
    val syncError: String? = null,
    val podcastDownloadLocal: Boolean = false
)

@HiltViewModel
class PodcastEpisodeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: PodcastRepository,
    private val bookDownloadManager: BookDownloadManager,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val showId: String = checkNotNull(savedStateHandle[MainRoute.PODCAST_EPISODE_SHOW_ID_ARG])
    private val episodeId: String = checkNotNull(savedStateHandle[MainRoute.PODCAST_EPISODE_ID_ARG])

    private val _uiState = MutableStateFlow(PodcastEpisodeDetailUiState())
    val uiState: StateFlow<PodcastEpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { prefs ->
                _uiState.value = _uiState.value.copy(podcastDownloadLocal = prefs.podcastDownloadLocal)
            }
        }
        load()
    }

    fun refresh() {
        load(forceRefresh = true)
    }

    fun markEpisodePlayed() {
        val episode = _uiState.value.episode ?: return
        updateEpisodeLocally { it.copy(isFinished = true, progressPercent = 1.0) }
        _uiState.value = _uiState.value.copy(syncError = null)
        viewModelScope.launch {
            when (
                val result = podcastRepository.syncEpisodeProgress(
                    showId = showId,
                    episodeId = episodeId,
                    currentTimeSeconds = episode.durationSeconds ?: 0.0,
                    durationSeconds = episode.durationSeconds,
                    isFinished = true
                )
            ) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
            }
        }
    }

    fun playEpisode(onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit) {
        val episode = _uiState.value.episode ?: return
        if (episode.isFinished) {
            updateEpisodeLocally { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
            _uiState.value = _uiState.value.copy(syncError = null)
            viewModelScope.launch {
                when (
                    val result = podcastRepository.syncEpisodeProgress(
                        showId = showId,
                        episodeId = episodeId,
                        currentTimeSeconds = 0.0,
                        durationSeconds = episode.durationSeconds,
                        isFinished = false
                    )
                ) {
                    is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                    is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
                }
            }
            onPlayEpisode(showId, episodeId, 0.0)
        } else {
            onPlayEpisode(showId, episodeId, episode.currentTimeSeconds)
        }
    }

    fun markEpisodeUnplayed() {
        val episode = _uiState.value.episode ?: return
        updateEpisodeLocally { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
        _uiState.value = _uiState.value.copy(syncError = null)
        viewModelScope.launch {
            when (
                val result = podcastRepository.syncEpisodeProgress(
                    showId = showId,
                    episodeId = episodeId,
                    currentTimeSeconds = 0.0,
                    durationSeconds = episode.durationSeconds,
                    isFinished = false
                )
            ) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
            }
        }
    }

    fun resetEpisodeProgress() {
        val episode = _uiState.value.episode ?: return
        updateEpisodeLocally { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
        _uiState.value = _uiState.value.copy(syncError = null)
        viewModelScope.launch {
            when (
                val result = podcastRepository.syncEpisodeProgress(
                    showId = showId,
                    episodeId = episodeId,
                    currentTimeSeconds = 0.0,
                    durationSeconds = episode.durationSeconds,
                    isFinished = false
                )
            ) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
            }
        }
    }

    fun toggleDownload() {
        val show = _uiState.value.show ?: return
        val episode = _uiState.value.episode ?: return
        val compoundId = "$showId::$episodeId"
        viewModelScope.launch {
            val activeServerId = sessionPreferences.state.first().activeServerId?.trim().orEmpty()
            val hasExistingDownload = bookDownloadManager.items.value.any { item ->
                item.bookId == compoundId &&
                    item.serverId == activeServerId &&
                    item.libraryId == show.libraryId &&
                    item.status != DownloadStatus.Failed
            }
            if (hasExistingDownload) {
                val book = BookSummary(
                    id = compoundId,
                    libraryId = show.libraryId,
                    title = episode.title,
                    authorName = show.author ?: show.title,
                    narratorName = null,
                    durationSeconds = episode.durationSeconds,
                    coverUrl = show.coverUrl
                )
                when (val result = bookDownloadManager.toggleDownload(book)) {
                    is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                    is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
                }
                return@launch
            }
            if (!uiState.value.podcastDownloadLocal) {
                _uiState.value = _uiState.value.copy(
                    syncError = "Enable device downloads in Settings > Podcasts to download episodes."
                )
                return@launch
            }
            when (val result = podcastRepository.fetchPodcastEpisodeDownloadSource(showId, episodeId)) {
                is AppResult.Success -> {
                    when (
                        val downloadResult = bookDownloadManager.toggleDownload(
                            book = result.value.book,
                            sourceOverride = result.value
                        )
                    ) {
                        is AppResult.Success -> _uiState.value = _uiState.value.copy(syncError = null)
                        is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = downloadResult.message)
                    }
                }
                is AppResult.Error -> _uiState.value = _uiState.value.copy(syncError = result.message)
            }
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
