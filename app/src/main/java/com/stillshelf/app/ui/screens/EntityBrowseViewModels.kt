package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntityBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null
)

data class SeriesBrowseCard(
    val id: String,
    val name: String,
    val subtitle: String?,
    val coverUrl: String?
)

data class SeriesBrowseUiState(
    val isLoading: Boolean = false,
    val series: List<SeriesBrowseCard> = emptyList(),
    val errorMessage: String? = null
)

data class CollectionsBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class AuthorsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(EntityBrowseUiState())
    val uiState: StateFlow<EntityBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchAuthorsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val filtered = result.value.filterNot { isStillShelfProbeCollection(it.name) }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = filtered
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class NarratorsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(EntityBrowseUiState())
    val uiState: StateFlow<EntityBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchNarratorsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = result.value
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class SeriesBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SeriesBrowseUiState())
    val uiState: StateFlow<SeriesBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val seriesResult = sessionRepository.fetchSeriesForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val books = when (
                        val booksResult = sessionRepository.fetchBooksForActiveLibrary(
                            limit = 400,
                            page = 0,
                            forceRefresh = forceRefresh
                        )
                    ) {
                        is AppResult.Success -> booksResult.value
                        is AppResult.Error -> emptyList()
                    }
                    val booksBySeries = books
                        .asSequence()
                        .filter { !it.seriesName.isNullOrBlank() }
                        .groupBy { normalizeSeriesName(it.seriesName.orEmpty()) }

                    val cards = seriesResult.value.map { series ->
                        val matchedBooks = booksBySeries[normalizeSeriesName(series.name)].orEmpty()
                        val preferredCoverUrl = matchedBooks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                            ?: series.imageUrl
                            ?: matchedBooks.firstOrNull()?.coverUrl
                        SeriesBrowseCard(
                            id = series.id,
                            name = series.name,
                            subtitle = series.subtitle ?: matchedBooks.takeIf { it.isNotEmpty() }?.size?.let { "$it books" },
                            coverUrl = preferredCoverUrl
                        )
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            series = cards
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = seriesResult.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class CollectionsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionsBrowseUiState())
    val uiState: StateFlow<CollectionsBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createCollection(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection created.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun renameCollection(collectionId: String, name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.renameCollection(collectionId = collectionId, name = name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection renamed.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.deleteCollection(collectionId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection deleted.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchCollectionsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val filtered = result.value.filterNot { isStillShelfProbeCollection(it.name) }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = filtered
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class PlaylistsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionsBrowseUiState())
    val uiState: StateFlow<CollectionsBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist created.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.renamePlaylist(playlistId = playlistId, name = name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist renamed.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.deletePlaylist(playlistId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist deleted.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchPlaylistsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = result.value
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeSeriesName(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

private fun isStillShelfProbeCollection(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized.startsWith("stillshelf probe") ||
        normalized.startsWith("stillshelf dup probe") ||
        normalized.startsWith("stillshelf probe add")
}
