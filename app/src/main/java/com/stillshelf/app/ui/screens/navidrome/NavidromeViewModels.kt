package com.stillshelf.app.ui.screens.navidrome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import com.stillshelf.app.ui.navigation.NavidromeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavidromeLoginUiState(
    val baseUrl: String = "https://music.kalzilab.com",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val canSubmit: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeLoginViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeLoginUiState())
    val uiState: StateFlow<NavidromeLoginUiState> = mutableUiState.asStateFlow()

    fun onBaseUrlChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                baseUrl = value,
                canSubmit = canSubmit(value, state.username, state.password)
            )
        }
    }

    fun onUsernameChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                username = value,
                canSubmit = canSubmit(state.baseUrl, value, state.password)
            )
        }
    }

    fun onPasswordChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                password = value,
                canSubmit = canSubmit(state.baseUrl, state.username, value)
            )
        }
    }

    fun submit() {
        val currentState = mutableUiState.value
        if (!currentState.canSubmit || currentState.isLoading) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (
                val result = navidromeRepository.login(
                    baseUrl = currentState.baseUrl,
                    username = currentState.username,
                    password = currentState.password
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = null)
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

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    private fun canSubmit(baseUrl: String, username: String, password: String): Boolean {
        return baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }
}

data class NavidromeHomeUiState(
    val recentAlbums: List<NavidromeAlbum> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList(),
    val playlists: List<NavidromePlaylist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeHomeViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeHomeUiState())
    val uiState: StateFlow<NavidromeHomeUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchHome()) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            recentAlbums = result.value.recentAlbums,
                            artists = result.value.artists,
                            playlists = result.value.playlists,
                            isLoading = false,
                            errorMessage = null
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

enum class NavidromeBrowseSection(
    val label: String
) {
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

data class NavidromeBrowseUiState(
    val selectedSection: NavidromeBrowseSection = NavidromeBrowseSection.ALBUMS,
    val albumSort: NavidromeAlbumSortOption = NavidromeAlbumSortOption.ALBUM_ARTIST,
    val albums: List<NavidromeAlbum> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList(),
    val playlists: List<NavidromePlaylist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeBrowseViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeBrowseUiState())
    val uiState: StateFlow<NavidromeBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun selectSection(section: NavidromeBrowseSection) {
        mutableUiState.update { it.copy(selectedSection = section) }
    }

    fun setAlbumSort(sort: NavidromeAlbumSortOption) {
        if (sort == mutableUiState.value.albumSort) return
        mutableUiState.update { it.copy(albumSort = sort) }
        refreshAlbums(sort)
    }

    fun refresh() {
        refreshAlbums(mutableUiState.value.albumSort)
        refreshArtists()
        refreshPlaylists()
    }

    private fun refreshAlbums(sort: NavidromeAlbumSortOption) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchAlbums(sort)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            albums = result.value,
                            isLoading = false
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    private fun refreshArtists() {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchArtists()) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(artists = result.value) }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        if (it.errorMessage == null) it.copy(errorMessage = result.message) else it
                    }
                }
            }
        }
    }

    private fun refreshPlaylists() {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchHome()) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(playlists = result.value.playlists) }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        if (it.errorMessage == null) it.copy(errorMessage = result.message) else it
                    }
                }
            }
        }
    }
}

data class NavidromeSearchUiState(
    val query: String = "",
    val results: NavidromeSearchResults = NavidromeSearchResults(
        artists = emptyList(),
        albums = emptyList(),
        tracks = emptyList()
    ),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeSearchViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeSearchUiState())
    val uiState: StateFlow<NavidromeSearchUiState> = mutableUiState.asStateFlow()

    fun onQueryChange(value: String) {
        mutableUiState.update { it.copy(query = value) }
    }

    fun submitSearch() {
        val query = mutableUiState.value.query.trim()
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.search(query)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            results = result.value,
                            isLoading = false,
                            errorMessage = null
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

data class NavidromeArtistDetailUiState(
    val detail: NavidromeArtistDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val artistId: String =
        savedStateHandle.get<String>(NavidromeRoute.ARTIST_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(NavidromeArtistDetailUiState())
    val uiState: StateFlow<NavidromeArtistDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchArtistDetail(artistId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false)
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }
}

data class NavidromeAlbumDetailUiState(
    val detail: NavidromeAlbumDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val albumId: String =
        savedStateHandle.get<String>(NavidromeRoute.ALBUM_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(NavidromeAlbumDetailUiState())
    val uiState: StateFlow<NavidromeAlbumDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchAlbumDetail(albumId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false)
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }
}

data class NavidromeSettingsUiState(
    val session: com.stillshelf.app.core.model.NavidromeSession? = null
)

@HiltViewModel
class NavidromeSettingsViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    val uiState: StateFlow<NavidromeSettingsUiState> = navidromeRepository.observeSession()
        .map { NavidromeSettingsUiState(session = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NavidromeSettingsUiState()
        )

    fun signOut() {
        viewModelScope.launch {
            navidromeRepository.signOut()
        }
    }
}

@HiltViewModel
class NavidromePlayerViewModel @Inject constructor(
    private val playerController: NavidromePlayerController
) : ViewModel() {
    val uiState: StateFlow<NavidromePlayerState> = playerController.state

    fun playTracks(tracks: List<NavidromeTrack>, startIndex: Int) {
        playerController.playTracks(tracks, startIndex)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun playNext() {
        playerController.playNext()
    }

    fun playPrevious() {
        playerController.playPrevious()
    }
}
