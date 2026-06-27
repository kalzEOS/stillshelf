package com.stillshelf.app.ui.screens.podcasts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.data.repo.PodcastRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.ui.navigation.MainRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastShowDetailUiState(
    val show: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val episodeQuery: String = "",
    val episodeStatusFilter: EpisodeStatusFilter = EpisodeStatusFilter.All,
    val episodeSortOrder: EpisodeSortOrder = EpisodeSortOrder.Newest,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val rssWarning: String? = null,
    val syncError: String? = null,
    val podcastDownloadLocal: Boolean = false
)

@HiltViewModel
class PodcastShowDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: PodcastRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {

    private val showId: String = checkNotNull(
        savedStateHandle[MainRoute.PODCAST_SHOW_ID_ARG]
    )

    private val _uiState = MutableStateFlow(PodcastShowDetailUiState())
    val uiState: StateFlow<PodcastShowDetailUiState> = _uiState.asStateFlow()

    private var allEpisodes: List<PodcastEpisode> = emptyList()

    init {
        viewModelScope.launch {
            restoreUiPreferences()
            // Pre-populate from in-memory cache so the screen isn't blank while loading
            val serverId = sessionPreferences.state.first().activeServerId.orEmpty()
            podcastRepository.getCachedPodcastShowDetail(serverId, showId)?.let { cached ->
                allEpisodes = cached.episodes
                _uiState.value = _uiState.value.copy(
                    show = cached.show,
                    episodes = applyFilter(allEpisodes, _uiState.value.episodeQuery, _uiState.value.episodeStatusFilter, _uiState.value.episodeSortOrder),
                    rssWarning = cached.rssError
                )
            }
            load()
        }
        viewModelScope.launch {
            sessionPreferences.state.collect { prefs ->
                _uiState.value = _uiState.value.copy(podcastDownloadLocal = prefs.podcastDownloadLocal)
            }
        }
        observeExternalEpisodeMutations()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, syncError = null)
            val syncResult = podcastRepository.checkForNewEpisodes(showId)
            doLoad(forceRefresh = true)
            if (syncResult is AppResult.Error && _uiState.value.show != null) {
                _uiState.value = _uiState.value.copy(syncError = "Could not check for new episodes: ${syncResult.message}")
            }
        }
    }

    fun setEpisodeQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            episodeQuery = query,
            episodes = applyFilter(
                episodes = allEpisodes,
                query = query,
                filter = _uiState.value.episodeStatusFilter,
                sortOrder = _uiState.value.episodeSortOrder
            )
        )
    }

    fun setEpisodeStatusFilter(filter: EpisodeStatusFilter) {
        _uiState.value = _uiState.value.copy(
            episodeStatusFilter = filter,
            episodes = applyFilter(
                episodes = allEpisodes,
                query = _uiState.value.episodeQuery,
                filter = filter,
                sortOrder = _uiState.value.episodeSortOrder
            )
        )
        viewModelScope.launch {
            sessionPreferences.setPodcastEpisodeStatusFilter(filter.name)
        }
    }

    fun setEpisodeSortOrder(sortOrder: EpisodeSortOrder) {
        _uiState.value = _uiState.value.copy(
            episodeSortOrder = sortOrder,
            episodes = applyFilter(
                episodes = allEpisodes,
                query = _uiState.value.episodeQuery,
                filter = _uiState.value.episodeStatusFilter,
                sortOrder = sortOrder
            )
        )
        viewModelScope.launch {
            sessionPreferences.setPodcastEpisodeSortOrder(sortOrder.name)
        }
    }

    private fun applyFilter(
        episodes: List<PodcastEpisode>,
        query: String,
        filter: EpisodeStatusFilter,
        sortOrder: EpisodeSortOrder
    ): List<PodcastEpisode> {
        val filteredByQuery = if (query.isBlank()) {
            episodes
        } else {
            val q = query.trim().lowercase()
            episodes.filter { it.title.lowercase().contains(q) }
        }
        return filteredByQuery.filter { episode ->
            episode.matchesStatusFilter(filter)
        }.sortedByEpisodeSortOrder(sortOrder)
    }

    private fun load() {
        viewModelScope.launch {
            // Only show the loading spinner if there's nothing on screen yet
            if (_uiState.value.show == null) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, syncError = null)
            }
            doLoad()
        }
    }

    private suspend fun doLoad(forceRefresh: Boolean = false) {
        when (val result = podcastRepository.fetchPodcastShowDetail(showId, forceRefresh = forceRefresh)) {
            is AppResult.Success -> {
                allEpisodes = result.value.episodes
                _uiState.value = _uiState.value.copy(
                    show = result.value.show,
                    episodes = applyFilter(
                        allEpisodes,
                        _uiState.value.episodeQuery,
                        _uiState.value.episodeStatusFilter,
                        _uiState.value.episodeSortOrder
                    ),
                    isLoading = false,
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

    fun markEpisodePlayed(episodeId: String) {
        val episode = allEpisodes.firstOrNull { it.id == episodeId } ?: return
        updateEpisodeLocally(episodeId) { it.copy(isFinished = true, progressPercent = 1.0) }
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

    fun markEpisodeUnplayed(episodeId: String) {
        val episode = allEpisodes.firstOrNull { it.id == episodeId } ?: return
        updateEpisodeLocally(episodeId) { it.copy(isFinished = false, progressPercent = null, currentTimeSeconds = null) }
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

    fun resetEpisodeProgress(episodeId: String) {
        val episode = allEpisodes.firstOrNull { it.id == episodeId } ?: return
        updateEpisodeLocally(episodeId) { it.copy(progressPercent = null, currentTimeSeconds = null, isFinished = false) }
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

    fun toggleEpisodeDownload(episodeId: String) {
        val episode = allEpisodes.firstOrNull { it.id == episodeId } ?: return
        val show = _uiState.value.show ?: return
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
            if (!_uiState.value.podcastDownloadLocal) {
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

    private fun observeExternalEpisodeMutations() {
        viewModelScope.launch {
            podcastRepository.observeEpisodeMutations().collect { mutation ->
                if (mutation.showId != showId) return@collect
                updateEpisodeLocally(mutation.episodeId) { episode ->
                    val progressPercent = if (mutation.durationSeconds != null && mutation.durationSeconds > 0.0) {
                        (mutation.currentTimeSeconds / mutation.durationSeconds).coerceIn(0.0, 1.0)
                    } else {
                        if (mutation.isFinished) 1.0 else null
                    }
                    episode.copy(
                        isFinished = mutation.isFinished,
                        currentTimeSeconds = mutation.currentTimeSeconds,
                        progressPercent = progressPercent
                    )
                }
            }
        }
    }

    private inline fun updateEpisodeLocally(episodeId: String, transform: (PodcastEpisode) -> PodcastEpisode) {
        allEpisodes = allEpisodes.map { ep -> if (ep.id == episodeId) transform(ep) else ep }
        _uiState.value = _uiState.value.copy(
            episodes = applyFilter(
                episodes = allEpisodes,
                query = _uiState.value.episodeQuery,
                filter = _uiState.value.episodeStatusFilter,
                sortOrder = _uiState.value.episodeSortOrder
            )
        )
    }

    private suspend fun restoreUiPreferences() {
        val pref = sessionPreferences.state.first()
        val filter = pref.podcastEpisodeStatusFilter
            ?.let { raw -> enumValueOrNull<EpisodeStatusFilter>(raw) }
            ?: EpisodeStatusFilter.All
        val sortOrder = pref.podcastEpisodeSortOrder
            ?.let { raw -> enumValueOrNull<EpisodeSortOrder>(raw) }
            ?: EpisodeSortOrder.Newest
        _uiState.value = _uiState.value.copy(
            episodeStatusFilter = filter,
            episodeSortOrder = sortOrder
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
        return runCatching { enumValueOf<T>(raw) }.getOrNull()
    }
}
