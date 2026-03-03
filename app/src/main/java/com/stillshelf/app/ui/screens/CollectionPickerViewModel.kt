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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class CollectionPickerUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val collections: List<NamedEntitySummary> = emptyList(),
    val playlists: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class CollectionPickerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionPickerUiState())
    val uiState: StateFlow<CollectionPickerUiState> = mutableUiState.asStateFlow()

    fun loadDestinations(
        forceRefresh: Boolean = true,
        showLoader: Boolean = true
    ) {
        if (mutableUiState.value.isLoading) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = showLoader,
                    errorMessage = null
                )
            }
            val result = coroutineScope {
                val collectionsDeferred = async {
                    sessionRepository.fetchCollectionsForActiveLibrary(forceRefresh = forceRefresh)
                }
                val playlistsDeferred = async {
                    sessionRepository.fetchPlaylistsForActiveLibrary(forceRefresh = forceRefresh)
                }
                collectionsDeferred.await() to playlistsDeferred.await()
            }
            val collectionsResult = result.first
            val playlistsResult = result.second
            val current = uiState.value
            val nextCollections = when (collectionsResult) {
                is AppResult.Success -> collectionsResult.value
                is AppResult.Error -> current.collections
            }
            val nextPlaylists = when (playlistsResult) {
                is AppResult.Success -> playlistsResult.value
                is AppResult.Error -> current.playlists
            }
            val errorParts = buildList {
                if (collectionsResult is AppResult.Error) add("Collections: ${collectionsResult.message}")
                if (playlistsResult is AppResult.Error) add("Playlists: ${playlistsResult.message}")
            }
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    collections = nextCollections,
                    playlists = nextPlaylists,
                    errorMessage = errorParts.takeIf { parts -> parts.isNotEmpty() }?.joinToString("  ")
                )
            }
        }
    }

    fun addBookToExistingCollection(bookId: String, collectionId: String) {
        if (bookId.isBlank() || collectionId.isBlank()) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = sessionRepository.addBookToCollection(collectionId, bookId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Added to \"${resolveName(uiState.value.collections, collectionId, "collection")}\""
                        )
                    }
                    loadDestinations(forceRefresh = true, showLoader = false)
                }
                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun createCollectionAndAddBook(bookId: String, name: String) {
        val normalizedName = name.trim()
        if (bookId.isBlank()) return
        if (normalizedName.isBlank()) {
            mutableUiState.update { it.copy(errorMessage = "Collection name is required.") }
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val createResult = sessionRepository.createCollection(normalizedName)) {
                is AppResult.Success -> {
                    val created = createResult.value
                    when (val addResult = sessionRepository.addBookToCollection(created.id, bookId)) {
                        is AppResult.Success -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    collections = upsertEntity(
                                        entities = it.collections,
                                        added = created
                                    ),
                                    actionMessage = "Created \"${created.name}\" and added book"
                                )
                            }
                            loadDestinations(forceRefresh = true, showLoader = false)
                        }
                        is AppResult.Error -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    errorMessage = addResult.message
                                )
                            }
                        }
                    }
                }
                is AppResult.Error -> {
                    when (val fallbackResult = sessionRepository.createCollectionWithBook(normalizedName, bookId)) {
                        is AppResult.Success -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    collections = upsertEntity(
                                        entities = it.collections,
                                        added = fallbackResult.value
                                    ),
                                    actionMessage = "Created \"${fallbackResult.value.name}\" and added book"
                                )
                            }
                            loadDestinations(forceRefresh = true, showLoader = false)
                        }
                        is AppResult.Error -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    errorMessage = fallbackResult.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun addBookToExistingPlaylist(bookId: String, playlistId: String) {
        if (bookId.isBlank() || playlistId.isBlank()) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = sessionRepository.addBookToPlaylist(playlistId, bookId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Added to \"${resolveName(uiState.value.playlists, playlistId, "playlist")}\""
                        )
                    }
                    loadDestinations(forceRefresh = true, showLoader = false)
                }
                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun createPlaylistAndAddBook(bookId: String, name: String) {
        val normalizedName = name.trim()
        if (bookId.isBlank()) return
        if (normalizedName.isBlank()) {
            mutableUiState.update { it.copy(errorMessage = "Playlist name is required.") }
            return
        }

        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val createResult = sessionRepository.createPlaylist(normalizedName)) {
                is AppResult.Success -> {
                    val created = createResult.value
                    when (val addResult = sessionRepository.addBookToPlaylist(created.id, bookId)) {
                        is AppResult.Success -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    playlists = upsertEntity(
                                        entities = it.playlists,
                                        added = created
                                    ),
                                    actionMessage = "Created \"${created.name}\" and added book"
                                )
                            }
                            loadDestinations(forceRefresh = true, showLoader = false)
                        }
                        is AppResult.Error -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    errorMessage = addResult.message
                                )
                            }
                        }
                    }
                }
                is AppResult.Error -> {
                    when (val fallbackResult = sessionRepository.createPlaylistWithBook(normalizedName, bookId)) {
                        is AppResult.Success -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    playlists = upsertEntity(
                                        entities = it.playlists,
                                        added = fallbackResult.value
                                    ),
                                    actionMessage = "Created \"${fallbackResult.value.name}\" and added book"
                                )
                            }
                            loadDestinations(forceRefresh = true, showLoader = false)
                        }
                        is AppResult.Error -> {
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    errorMessage = fallbackResult.message
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    private fun resolveName(
        entities: List<NamedEntitySummary>,
        id: String,
        fallback: String
    ): String {
        return entities.firstOrNull { it.id == id }?.name
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun upsertEntity(
        entities: List<NamedEntitySummary>,
        added: NamedEntitySummary
    ): List<NamedEntitySummary> {
        val filtered = entities.filterNot {
            it.id == added.id || it.name.equals(added.name, ignoreCase = true)
        }
        return listOf(added) + filtered
    }
}
