package com.stillshelf.app.ui.screens.navidrome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeLibrary
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlaylistDetail
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.EndpointReachabilityStatus
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.ui.screens.ToggleSectionItem
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.ui.screens.SettingsServerOption
import com.stillshelf.app.ui.screens.resolveCurrentConnectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavidromeLoginUiState(
    val serverName: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val serverNameError: String? = null,
    val isTestingConnection: Boolean = false,
    val connectionMessage: String? = null,
    val connectionSuccess: Boolean? = null,
    val showCredentialsStep: Boolean = false,
    val isLoading: Boolean = false,
    val canContinue: Boolean = false,
    val canSubmit: Boolean = false,
    val errorMessage: String? = null,
    val loginSucceeded: Boolean = false
)

@HiltViewModel
class NavidromeLoginViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeLoginUiState())
    val uiState: StateFlow<NavidromeLoginUiState> = mutableUiState.asStateFlow()

    fun onServerNameChange(value: String) {
        val serverNameError = validateServerName(value)
        mutableUiState.update { state ->
            state.copy(
                serverName = value,
                serverNameError = serverNameError,
                canContinue = canContinue(
                    name = value,
                    baseUrl = state.baseUrl,
                    serverNameError = serverNameError
                ),
                errorMessage = null,
                connectionMessage = null,
                connectionSuccess = null
            )
        }
    }

    fun onBaseUrlChange(value: String) {
        val normalizedValue = value.replace(" ", "")
        val currentServerName = uiState.value.serverName
        val serverNameError = validateServerName(currentServerName)
        mutableUiState.update { state ->
            state.copy(
                baseUrl = normalizedValue,
                serverNameError = serverNameError,
                canContinue = canContinue(
                    name = currentServerName,
                    baseUrl = normalizedValue,
                    serverNameError = serverNameError
                ),
                canSubmit = canSubmit(normalizedValue, state.username, state.password),
                errorMessage = null,
                connectionMessage = null,
                connectionSuccess = null
            )
        }
    }

    fun onUsernameChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                username = value,
                canSubmit = canSubmit(state.baseUrl, value, state.password),
                errorMessage = null
            )
        }
    }

    fun onPasswordChange(value: String) {
        mutableUiState.update { state ->
            state.copy(
                password = value,
                canSubmit = canSubmit(state.baseUrl, state.username, value),
                errorMessage = null
            )
        }
    }

    fun onTestConnectionClick() {
        val baseUrl = uiState.value.baseUrl.trim()
        if (baseUrl.isBlank() || uiState.value.isTestingConnection) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isTestingConnection = true,
                    connectionMessage = null,
                    connectionSuccess = null,
                    errorMessage = null
                )
            }
            when (val result = navidromeRepository.testServerConnection(baseUrl)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionMessage = result.value,
                            connectionSuccess = true
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionMessage = result.message,
                            connectionSuccess = false
                        )
                    }
                }
            }
        }
    }

    fun continueToCredentials() {
        val currentState = mutableUiState.value
        val serverNameError = validateServerName(currentState.serverName)
        if (!canContinue(currentState.serverName, currentState.baseUrl, serverNameError)) {
            mutableUiState.update {
                it.copy(serverNameError = serverNameError)
            }
            return
        }
        mutableUiState.update {
            it.copy(
                serverNameError = serverNameError,
                showCredentialsStep = true,
                errorMessage = null
            )
        }
    }

    fun backToServerStep() {
        mutableUiState.update {
            it.copy(
                showCredentialsStep = false,
                errorMessage = null
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
                    serverName = currentState.serverName,
                    baseUrl = currentState.baseUrl,
                    username = currentState.username,
                    password = currentState.password
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            loginSucceeded = true
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

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    fun clearLoginSucceeded() {
        mutableUiState.update { it.copy(loginSucceeded = false) }
    }

    private fun canContinue(name: String, baseUrl: String, serverNameError: String?): Boolean {
        return name.isNotBlank() && baseUrl.isNotBlank() && serverNameError == null
    }

    private fun canSubmit(baseUrl: String, username: String, password: String): Boolean {
        return baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    private fun validateServerName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Server name is required."
        if (trimmed.length < 2) return "Server name must be at least 2 characters."
        val hasInvalidChar = trimmed.any { char ->
            !char.isLetterOrDigit() && char !in setOf(' ', '.', '-', '_', '\'')
        }
        if (hasInvalidChar) {
            return "Use letters, numbers, spaces, or . - _ ' only."
        }
        return null
    }
}

data class NavidromeHomeUiState(
    val recentAlbums: List<NavidromeAlbum> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList(),
    val playlists: List<NavidromePlaylist> = emptyList(),
    val radios: List<NavidromeRadio> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class NavidromeHomeViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeHomeUiState())
    val uiState: StateFlow<NavidromeHomeUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchHome(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            recentAlbums = result.value.recentAlbums,
                            artists = result.value.artists,
                            playlists = result.value.playlists,
                            radios = result.value.radios,
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

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.renamePlaylist(playlistId, name)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Playlist updated"
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String, playlistName: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.deletePlaylist(playlistId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Deleted \"$playlistName\""
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }
}

object NavidromeListSectionIds {
    const val ALBUMS = "navidrome_albums"
    const val ARTISTS = "navidrome_artists"
    const val RADIOS = "navidrome_radios"
    const val NEWEST_ALBUMS = "navidrome_newest_albums"
    const val SONGS = "navidrome_songs"
    const val FAVORITES = "navidrome_favorites"
    const val PLAYLISTS = "navidrome_playlists"
}

object NavidromeHomeSectionIds {
    const val CONTINUE = "navidrome_continue_listening"
    const val RECENTLY_ADDED = "navidrome_recently_added"
    const val DISCOVER = "navidrome_discover"
    const val ARTISTS = "navidrome_home_artists"
    const val PLAYLISTS = "navidrome_home_playlists"
}

enum class NavidromeArtistsLayoutMode {
    Grid,
    List
}

enum class NavidromeArtistSortOption(
    val label: String
) {
    NAME_ASC("Name A - Z"),
    NAME_DESC("Name Z - A"),
    MOST_ALBUMS("Most albums"),
    FEWEST_ALBUMS("Fewest albums")
}

data class NavidromeCustomizeUiState(
    val listSections: List<ToggleSectionItem> = emptyList(),
    val personalizedSections: List<ToggleSectionItem> = emptyList(),
    val hiddenListSectionIds: Set<String> = emptySet(),
    val hiddenPersonalizedSectionIds: Set<String> = emptySet()
)

@HiltViewModel
class NavidromeCustomizeViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val defaultListSections = listOf(
        ToggleSectionItem(NavidromeListSectionIds.ARTISTS, "Artists"),
        ToggleSectionItem(NavidromeListSectionIds.ALBUMS, "Albums"),
        ToggleSectionItem(NavidromeListSectionIds.RADIOS, "Radios"),
        ToggleSectionItem(NavidromeListSectionIds.NEWEST_ALBUMS, "Newest Albums"),
        ToggleSectionItem(NavidromeListSectionIds.SONGS, "Songs"),
        ToggleSectionItem(NavidromeListSectionIds.FAVORITES, "Favorite Songs"),
        ToggleSectionItem(NavidromeListSectionIds.PLAYLISTS, "Playlists")
    )
    private val defaultPersonalizedSections = listOf(
        ToggleSectionItem(NavidromeHomeSectionIds.CONTINUE, "Continue Listening"),
        ToggleSectionItem(NavidromeHomeSectionIds.RECENTLY_ADDED, "Recently Added"),
        ToggleSectionItem(NavidromeHomeSectionIds.DISCOVER, "Discover"),
        ToggleSectionItem(NavidromeHomeSectionIds.ARTISTS, "Artists"),
        ToggleSectionItem(NavidromeHomeSectionIds.PLAYLISTS, "Playlists")
    )

    private val mutableUiState = MutableStateFlow(
        NavidromeCustomizeUiState(
            listSections = defaultListSections,
            personalizedSections = defaultPersonalizedSections
        )
    )
    val uiState: StateFlow<NavidromeCustomizeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        listSections = applySavedOrder(defaultListSections, state.navidromeBrowseSectionOrder),
                        personalizedSections = applySavedOrder(defaultPersonalizedSections, state.navidromeHomeSectionOrder),
                        hiddenListSectionIds = state.navidromeHiddenBrowseSectionIds,
                        hiddenPersonalizedSectionIds = state.navidromeHiddenHomeSectionIds
                    )
                }
            }
        }
    }

    fun setHiddenListSectionIds(ids: Set<String>) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeHiddenBrowseSectionIds(ids)
        }
    }

    fun setHiddenPersonalizedSectionIds(ids: Set<String>) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeHiddenHomeSectionIds(ids)
        }
    }

    fun setListOrder(ids: List<String>) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeBrowseSectionOrder(ids)
        }
    }

    fun setPersonalizedOrder(ids: List<String>) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeHomeSectionOrder(ids)
        }
    }

    private fun applySavedOrder(
        defaults: List<ToggleSectionItem>,
        savedOrder: List<String>
    ): List<ToggleSectionItem> {
        if (savedOrder.isEmpty()) return defaults
        val byId = defaults.associateBy { it.id }
        return buildList {
            savedOrder.forEach { id ->
                byId[id]?.let(::add)
            }
            defaults.forEach { item ->
                if (none { it.id == item.id }) {
                    add(item)
                }
            }
        }
    }
}

@HiltViewModel
class NavidromeArtistsViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableLayoutMode = MutableStateFlow(NavidromeArtistsLayoutMode.Grid)
    val layoutMode: StateFlow<NavidromeArtistsLayoutMode> = mutableLayoutMode.asStateFlow()
    private val mutableSortOption = MutableStateFlow(NavidromeArtistSortOption.NAME_ASC)
    val sortOption: StateFlow<NavidromeArtistSortOption> = mutableSortOption.asStateFlow()
    private val mutableSearchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = mutableSearchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableLayoutMode.value = state.navidromeArtistLayoutMode
                    ?.let { raw -> enumValueOrNull<NavidromeArtistsLayoutMode>(raw) }
                    ?: NavidromeArtistsLayoutMode.Grid
                mutableSortOption.value = state.navidromeArtistSort
                    ?.let { raw -> enumValueOrNull<NavidromeArtistSortOption>(raw) }
                    ?: NavidromeArtistSortOption.NAME_ASC
            }
        }
    }

    fun setLayoutMode(value: NavidromeArtistsLayoutMode) {
        if (mutableLayoutMode.value == value) return
        mutableLayoutMode.value = value
        viewModelScope.launch {
            sessionPreferences.setNavidromeArtistLayoutMode(value.name)
        }
    }

    fun setSortOption(value: NavidromeArtistSortOption) {
        if (mutableSortOption.value == value) return
        mutableSortOption.value = value
        viewModelScope.launch {
            sessionPreferences.setNavidromeArtistSort(value.name)
        }
    }

    fun onSearchQueryChange(value: String) {
        mutableSearchQuery.value = value
    }
}

@HiltViewModel
class NavidromeAlbumsViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableLayoutMode = MutableStateFlow(NavidromeAlbumsDisplayStyle.GRID)
    val layoutMode: StateFlow<NavidromeAlbumsDisplayStyle> = mutableLayoutMode.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableLayoutMode.value = state.navidromeAlbumLayoutMode
                    ?.let { raw -> enumValueOrNull<NavidromeAlbumsDisplayStyle>(raw) }
                    ?: NavidromeAlbumsDisplayStyle.GRID
            }
        }
    }

    fun setLayoutMode(value: NavidromeAlbumsDisplayStyle) {
        if (mutableLayoutMode.value == value) return
        mutableLayoutMode.value = value
        viewModelScope.launch {
            sessionPreferences.setNavidromeAlbumLayoutMode(value.name)
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

enum class NavidromePlaylistSortOption(
    val label: String
) {
    NAME("Name"),
    DURATION("Duration")
}

data class NavidromePlaylistsUiState(
    val playlists: List<NavidromePlaylist> = emptyList(),
    val sortOption: NavidromePlaylistSortOption = NavidromePlaylistSortOption.NAME,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

data class NavidromePlaylistPickerUiState(
    val playlists: List<NavidromePlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class NavidromeBrowseViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeBrowseUiState())
    val uiState: StateFlow<NavidromeBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun selectSection(section: NavidromeBrowseSection) {
        mutableUiState.update { it.copy(selectedSection = section) }
    }

    fun setAlbumSort(sort: NavidromeAlbumSortOption) {
        if (sort == mutableUiState.value.albumSort) return
        mutableUiState.update { it.copy(albumSort = sort) }
        refreshAlbums(sort, forceRefresh = true)
    }

    fun refresh(forceRefresh: Boolean = true) {
        refreshAlbums(mutableUiState.value.albumSort, forceRefresh = forceRefresh)
        refreshArtists(forceRefresh = forceRefresh)
        refreshPlaylists(forceRefresh = forceRefresh)
    }

    private fun refreshAlbums(sort: NavidromeAlbumSortOption, forceRefresh: Boolean) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchAlbums(sort, forceRefresh = forceRefresh)) {
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

    private fun refreshArtists(forceRefresh: Boolean) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchArtists(forceRefresh = forceRefresh)) {
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

    private fun refreshPlaylists(forceRefresh: Boolean) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchPlaylists(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(playlists = result.value) }
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

@HiltViewModel
class NavidromePlaylistsViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromePlaylistsUiState())
    val uiState: StateFlow<NavidromePlaylistsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        sortOption = state.navidromePlaylistSort
                            ?.let { raw -> enumValueOrNull<NavidromePlaylistSortOption>(raw) }
                            ?: NavidromePlaylistSortOption.NAME
                    )
                }
            }
        }
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchPlaylists(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            playlists = result.value,
                            isLoading = false,
                            errorMessage = null
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

    fun setSortOption(value: NavidromePlaylistSortOption) {
        if (mutableUiState.value.sortOption == value) return
        mutableUiState.update { it.copy(sortOption = value) }
        viewModelScope.launch {
            sessionPreferences.setNavidromePlaylistSort(value.name)
        }
    }

    fun onSearchQueryChange(value: String) {
        mutableUiState.update { it.copy(searchQuery = value) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Created \"${result.value.name}\""
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.renamePlaylist(playlistId, name)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Playlist updated"
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String, playlistName: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.deletePlaylist(playlistId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Deleted \"$playlistName\""
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }
}

@HiltViewModel
class NavidromePlaylistPickerViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromePlaylistPickerUiState())
    val uiState: StateFlow<NavidromePlaylistPickerUiState> = mutableUiState.asStateFlow()

    fun loadPlaylists(forceRefresh: Boolean = false, showLoader: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = showLoader,
                    errorMessage = null
                )
            }
            when (val result = navidromeRepository.fetchPlaylists(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            playlists = result.value,
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

    fun addTracksToPlaylist(trackIds: List<String>, playlistId: String) {
        val normalizedTrackIds = trackIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (normalizedTrackIds.isEmpty() || playlistId.isBlank()) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.addTracksToPlaylist(playlistId, normalizedTrackIds)) {
                is AppResult.Success -> {
                    val playlistName = uiState.value.playlists
                        .firstOrNull { it.id == playlistId }
                        ?.name
                        ?: "playlist"
                    val actionMessage = if (result.value.addedCount == 0) {
                        if (result.value.duplicateCount == 1) {
                            "This song is already in \"$playlistName\""
                        } else {
                            "All selected songs are already in \"$playlistName\""
                        }
                    } else if (result.value.duplicateCount > 0) {
                        "Added ${result.value.addedCount} songs to \"$playlistName\". ${result.value.duplicateCount} already there"
                    } else if (result.value.addedCount == 1) {
                        "Added to \"$playlistName\""
                    } else {
                        "Added ${result.value.addedCount} songs to \"$playlistName\""
                    }
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = actionMessage
                        )
                    }
                    loadPlaylists(forceRefresh = true, showLoader = false)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun addTrackToPlaylist(trackId: String, playlistId: String) {
        addTracksToPlaylist(trackIds = listOf(trackId), playlistId = playlistId)
    }

    fun createPlaylistAndAddTracks(trackIds: List<String>, name: String) {
        val normalizedTrackIds = trackIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        if (normalizedTrackIds.isEmpty()) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val createResult = navidromeRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    val created = createResult.value
                    when (val addResult = navidromeRepository.addTracksToPlaylist(created.id, normalizedTrackIds)) {
                        is AppResult.Success -> {
                            val actionMessage = if (addResult.value.addedCount == 1) {
                                "Created \"${created.name}\" and added song"
                            } else {
                                "Created \"${created.name}\" and added ${addResult.value.addedCount} songs"
                            }
                            mutableUiState.update {
                                it.copy(
                                    isSubmitting = false,
                                    actionMessage = actionMessage
                                )
                            }
                            loadPlaylists(forceRefresh = true, showLoader = false)
                        }

                        is AppResult.Error -> {
                            mutableUiState.update {
                                it.copy(isSubmitting = false, errorMessage = addResult.message)
                            }
                        }
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = createResult.message)
                    }
                }
            }
        }
    }

    fun createPlaylistAndAddTrack(trackId: String, name: String) {
        createPlaylistAndAddTracks(trackIds = listOf(trackId), name = name)
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
}

data class NavidromeSearchUiState(
    val query: String = "",
    val results: NavidromeSearchResults = NavidromeSearchResults(
        artists = emptyList(),
        albums = emptyList(),
        tracks = emptyList()
    ),
    val recentSearchTerms: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class NavidromeSongsUiState(
    val songs: List<NavidromeTrack> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class NavidromeRadiosUiState(
    val radios: List<NavidromeRadio> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

data class NavidromeFavoriteSongsUiState(
    val songs: List<NavidromeTrack> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeSearchViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeSearchUiState())
    val uiState: StateFlow<NavidromeSearchUiState> = mutableUiState.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null
    private var searchRequestToken: Long = 0L

    init {
        viewModelScope.launch {
            sessionPreferences.state
                .map { it.navidromeRecentSearchTerms }
                .distinctUntilChanged()
                .collect { recentSearchTerms ->
                    mutableUiState.update { it.copy(recentSearchTerms = recentSearchTerms) }
                }
        }
    }

    fun onQueryChange(value: String) {
        mutableUiState.update { it.copy(query = value, errorMessage = null) }
        if (value.isBlank()) {
            clearQuery()
            return
        }
        search(value)
    }

    fun submitSearch() {
        commitCurrentQuery()
        val query = mutableUiState.value.query.trim()
        if (query.isBlank()) return
        search(query, debounceMs = 0L)
    }

    fun clearQuery() {
        searchJob?.cancel()
        mutableUiState.update {
            it.copy(
                query = "",
                results = NavidromeSearchResults(
                    artists = emptyList(),
                    albums = emptyList(),
                    tracks = emptyList()
                ),
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun useRecentSearchTerm(term: String) {
        val normalizedTerm = term.trim()
        if (normalizedTerm.isBlank()) return
        mutableUiState.update { it.copy(query = normalizedTerm, errorMessage = null) }
        search(normalizedTerm, debounceMs = 0L)
    }

    fun commitCurrentQuery() {
        val query = uiState.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            sessionPreferences.addNavidromeRecentSearchTerm(query)
        }
    }

    fun clearRecentSearchTerms() {
        viewModelScope.launch {
            sessionPreferences.clearNavidromeRecentSearchTerms()
        }
    }

    private fun search(query: String, debounceMs: Long = 220L) {
        searchJob?.cancel()
        val requestToken = nextSearchRequestToken()
        val requestedQuery = query.trim()
        if (requestedQuery.isBlank()) return
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) kotlinx.coroutines.delay(debounceMs)
            if (isStaleSearchRequest(requestToken, requestedQuery)) return@launch

            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.search(requestedQuery)) {
                is AppResult.Success -> {
                    if (isStaleSearchRequest(requestToken, requestedQuery)) return@launch
                    mutableUiState.update {
                        it.copy(
                            results = result.value,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    if (isStaleSearchRequest(requestToken, requestedQuery)) return@launch
                    mutableUiState.update {
                        it.copy(
                            results = NavidromeSearchResults(
                                artists = emptyList(),
                                albums = emptyList(),
                                tracks = emptyList()
                            ),
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun nextSearchRequestToken(): Long {
        searchRequestToken += 1L
        return searchRequestToken
    }

    private fun isStaleSearchRequest(requestToken: Long, expectedQuery: String? = null): Boolean {
        if (requestToken != searchRequestToken) return true
        val currentQuery = uiState.value.query.trim()
        return expectedQuery != null && currentQuery != expectedQuery
    }
}

@HiltViewModel
class NavidromeSongsViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeSongsUiState())
    val uiState: StateFlow<NavidromeSongsUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchSongs(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            songs = result.value,
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

@HiltViewModel
class NavidromeRadiosViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeRadiosUiState())
    val uiState: StateFlow<NavidromeRadiosUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchRadios(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            radios = result.value,
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

@HiltViewModel
class NavidromeFavoriteSongsViewModel @Inject constructor(
    sessionPreferences: SessionPreferences
) : ViewModel() {
    private val sessionPreferences = sessionPreferences
    private val sessionState = sessionPreferences.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.stillshelf.app.core.datastore.SessionPreferenceState(
                activeServerId = null,
                activeLibraryId = null
            )
        )

    val uiState: StateFlow<NavidromeFavoriteSongsUiState> = sessionState
        .map { state ->
            val favoriteTracks = favoriteTracksForCurrentSession(state)
            val ids = favoriteTracks.map { it.id }.toSet()
            NavidromeFavoriteSongsUiState(
                songs = favoriteTracks,
                favoriteTrackIds = ids,
                isLoading = false,
                errorMessage = null
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NavidromeFavoriteSongsUiState(isLoading = false)
        )

    fun removeFavoriteTrack(trackId: String) {
        val sessionKey = navidromeSessionKey(
            baseUrl = sessionState.value.navidromeBaseUrl,
            username = sessionState.value.navidromeUsername
        ) ?: return
        viewModelScope.launch {
            sessionPreferences.removeNavidromeFavoriteTrack(sessionKey, trackId)
        }
    }

    fun clearFavorites() {
        val sessionKey = navidromeSessionKey(
            baseUrl = sessionState.value.navidromeBaseUrl,
            username = sessionState.value.navidromeUsername
        ) ?: return
        viewModelScope.launch {
            sessionPreferences.clearNavidromeFavoriteTracks(sessionKey)
        }
    }
}

@HiltViewModel
class NavidromeSettingsViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    private val mutableBusyState = MutableStateFlow(false)
    private val mutableLibraries = MutableStateFlow<List<NavidromeLibrary>>(emptyList())

    val uiState: StateFlow<NavidromeSettingsUiState> = combine(
        combine(
            navidromeRepository.observeSession(),
            navidromeRepository.observeServers(),
            sessionPreferences.state,
            navidromeRepository.observeActiveConnectionStatus(),
            navidromeRepository.observeEndpointHealth()
        ) { session, servers, preferences, connectionStatus, endpointHealth ->
            Quintuple(session, servers, preferences, connectionStatus, endpointHealth)
        },
        mutableLibraries,
        mutableErrorMessage,
        mutableBusyState
    ) { upstream, libraries, errorMessage, isBusy ->
        val session = upstream.session
        val servers = upstream.servers
        val preferences = upstream.preferences
        val connectionStatus = upstream.connectionStatus
        val endpointHealth = upstream.endpointHealth
        val activeServerId = preferences.activeNavidromeServerId ?: servers.firstOrNull()?.id
        val activeServer = servers.firstOrNull { it.id == activeServerId }
        val activeLibraryId = activeServer?.id?.let(preferences.navidromeActiveLibraryIds::get)
        val switchingConfig = activeServer?.id?.let(preferences.serverEndpointSwitchingConfigs::get)
            ?: ServerEndpointSwitchingConfig()
        val effectiveBaseUrl = connectionStatus?.effectiveBaseUrl ?: activeServer?.baseUrl
        val connectionStatusLabel = when (endpointHealth?.reachabilityStatus) {
            EndpointReachabilityStatus.Reachable -> "Reachable"
            EndpointReachabilityStatus.Unavailable -> "Unavailable"
            EndpointReachabilityStatus.Checking, null -> "Checking"
        }
        NavidromeSettingsUiState(
            session = session,
            savedServers = servers.map { server ->
                SettingsServerOption(
                    id = server.id,
                    name = server.name,
                    baseUrl = server.baseUrl,
                    host = parseHost(server.baseUrl)
                )
            },
            activeServerId = activeServerId,
            availableLibraries = libraries,
            activeLibraryId = activeLibraryId,
            automaticServerSwitchingEnabled = switchingConfig.enabled,
            lanServerUrl = switchingConfig.lanBaseUrl.orEmpty(),
            wanServerUrl = switchingConfig.wanBaseUrl.orEmpty(),
            currentConnectionLabel = resolveCurrentConnectionLabel(
                serverPresent = activeServer != null,
                switchingIsActive = connectionStatus?.switchingEnabled == true,
                currentRoute = connectionStatus?.route,
                effectiveBaseUrl = effectiveBaseUrl,
                localBaseUrl = switchingConfig.lanBaseUrl,
                remoteBaseUrl = switchingConfig.wanBaseUrl
            ),
            currentEndpointUrl = endpointHealth?.endpointUrl ?: effectiveBaseUrl.orEmpty(),
            connectionStatusLabel = if (activeServer == null) "Not configured" else connectionStatusLabel,
            connectionLatencyMs = endpointHealth?.latencyMs,
            isBusy = isBusy,
            errorMessage = errorMessage
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NavidromeSettingsUiState()
        )

    init {
        refreshConnectionStatus()
        viewModelScope.launch {
            navidromeRepository.observeSession()
                .map { it?.serverId }
                .distinctUntilChanged()
                .collect { serverId ->
                    mutableLibraries.value = emptyList()
                    if (serverId != null) {
                        refreshLibraries(forceRefresh = false)
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            mutableBusyState.value = true
            navidromeRepository.signOut()
            mutableLibraries.value = emptyList()
            mutableBusyState.value = false
        }
    }

    fun setActiveServer(serverId: String) {
        if (mutableBusyState.value) return
        viewModelScope.launch {
            mutableBusyState.value = true
            when (val result = navidromeRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = null
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = false)
                }

                is AppResult.Error -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun updateServer(serverId: String, name: String, baseUrl: String) {
        if (mutableBusyState.value) return
        viewModelScope.launch {
            mutableBusyState.value = true
            when (val result = navidromeRepository.updateServer(serverId, name, baseUrl)) {
                is AppResult.Success -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = null
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun deleteServer(serverId: String) {
        if (mutableBusyState.value) return
        viewModelScope.launch {
            mutableBusyState.value = true
            when (val result = navidromeRepository.deleteServer(serverId)) {
                is AppResult.Success -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = null
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = false)
                }

                is AppResult.Error -> {
                    mutableBusyState.value = false
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun setAutomaticServerSwitchingEnabled(enabled: Boolean) {
        val state = uiState.value
        val serverId = state.activeServerId ?: return
        viewModelScope.launch {
            when (
                val result = navidromeRepository.updateServerEndpointSwitchingConfig(
                    serverId = serverId,
                    config = ServerEndpointSwitchingConfig(
                        enabled = enabled,
                        lanBaseUrl = state.lanServerUrl.ifBlank { null },
                        wanBaseUrl = state.wanServerUrl.ifBlank { null }
                    )
                )
            ) {
                is AppResult.Success -> {
                    mutableErrorMessage.value = null
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun updateLanServerUrl(baseUrl: String) {
        updateAdvancedServerUrl(target = ServerConnectionMode.Local, baseUrl = baseUrl)
    }

    fun updateWanServerUrl(baseUrl: String) {
        updateAdvancedServerUrl(target = ServerConnectionMode.Remote, baseUrl = baseUrl)
    }

    fun clearError() {
        mutableErrorMessage.value = null
    }

    fun setActiveLibrary(libraryId: String) {
        viewModelScope.launch {
            when (val result = navidromeRepository.setActiveLibrary(libraryId)) {
                is AppResult.Success -> {
                    mutableErrorMessage.value = null
                }

                is AppResult.Error -> {
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            when (val result = navidromeRepository.refreshActiveConnectionStatus()) {
                is AppResult.Success -> {
                    mutableErrorMessage.value = null
                }

                is AppResult.Error -> {
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    fun refreshLibraries(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchLibraries(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableLibraries.value = result.value
                    mutableErrorMessage.value = null
                }

                is AppResult.Error -> {
                    mutableLibraries.value = emptyList()
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }

    private fun updateAdvancedServerUrl(target: ServerConnectionMode, baseUrl: String) {
        val state = uiState.value
        val serverId = state.activeServerId ?: return
        val config = ServerEndpointSwitchingConfig(
            enabled = state.automaticServerSwitchingEnabled,
            lanBaseUrl = if (target == ServerConnectionMode.Local) {
                baseUrl.ifBlank { null }
            } else {
                state.lanServerUrl.ifBlank { null }
            },
            wanBaseUrl = if (target == ServerConnectionMode.Remote) {
                baseUrl.ifBlank { null }
            } else {
                state.wanServerUrl.ifBlank { null }
            }
        )
        viewModelScope.launch {
            when (val result = navidromeRepository.updateServerEndpointSwitchingConfig(serverId, config)) {
                is AppResult.Success -> {
                    mutableErrorMessage.value = null
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableErrorMessage.value = result.message
                }
            }
        }
    }
}

@HiltViewModel
class NavidromePlayerViewModel @Inject constructor(
    private val playerController: NavidromePlayerController,
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val preferenceState = sessionPreferences.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.stillshelf.app.core.datastore.SessionPreferenceState(
                activeServerId = null,
                activeLibraryId = null
            )
        )

    val uiState: StateFlow<NavidromePlayerState> = playerController.state
    val favoriteTrackIds: StateFlow<Set<String>> = preferenceState
        .map { state -> favoriteTracksForCurrentSession(state).map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    fun playTracks(tracks: List<NavidromeTrack>, startIndex: Int) {
        playerController.playTracks(tracks, startIndex)
    }

    fun playTrack(track: NavidromeTrack) {
        playerController.playTracks(listOf(track), startIndex = 0)
    }

    fun playTracksNext(tracks: List<NavidromeTrack>) {
        playerController.playTracksNext(tracks)
    }

    fun addTracksToQueue(tracks: List<NavidromeTrack>) {
        playerController.appendTracksToQueue(tracks)
    }

    fun playRadio(radio: NavidromeRadio) {
        playerController.playTracks(listOf(radio.toTrack()), startIndex = 0)
    }

    fun playRadios(radios: List<NavidromeRadio>, startIndex: Int) {
        val queue = radios.map { it.toTrack() }
        if (queue.isNotEmpty()) {
            playerController.playTracks(queue, startIndex = startIndex)
        }
    }

    fun playAlbum(albumId: String, shuffle: Boolean = false) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchAlbumDetail(albumId)) {
                is AppResult.Success -> {
                    val tracks = if (shuffle) {
                        result.value.tracks.shuffled()
                    } else {
                        result.value.tracks
                    }
                    if (tracks.isNotEmpty()) {
                        playerController.playTracks(tracks, startIndex = 0)
                    }
                }

                is AppResult.Error -> Unit
            }
        }
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

    fun playQueueIndex(index: Int) {
        playerController.playQueueIndex(index)
    }

    fun seekTo(positionMs: Int) {
        playerController.seekTo(positionMs)
    }

    fun refreshAudioOutputs() {
        playerController.refreshAudioOutputs()
    }

    fun selectAudioOutputDevice(deviceId: Int?) {
        playerController.selectAudioOutputDevice(deviceId)
    }

    fun toggleFavoriteTrack(track: NavidromeTrack): Boolean {
        if (track.id.startsWith("radio:")) return false
        val sessionKey = navidromeSessionKey(
            baseUrl = preferenceState.value.navidromeBaseUrl,
            username = preferenceState.value.navidromeUsername
        ) ?: return false
        val wasFavorite = favoriteTrackIds.value.contains(track.id)
        viewModelScope.launch {
            sessionPreferences.toggleNavidromeFavoriteTrack(sessionKey, track)
        }
        return !wasFavorite
    }
}

private fun favoriteTracksForCurrentSession(
    state: com.stillshelf.app.core.datastore.SessionPreferenceState
): List<NavidromeTrack> {
    val sessionKey = navidromeSessionKey(
        baseUrl = state.navidromeBaseUrl,
        username = state.navidromeUsername
    ) ?: return emptyList()
    return state.navidromeFavoriteTracksBySession[sessionKey].orEmpty()
}

private fun navidromeSessionKey(
    baseUrl: String?,
    username: String?
): String? {
    val normalizedBaseUrl = baseUrl?.trim()?.removeSuffix("/").orEmpty()
    val normalizedUsername = username?.trim().orEmpty()
    if (normalizedBaseUrl.isBlank() || normalizedUsername.isBlank()) return null
    return "${normalizedBaseUrl.lowercase()}|${normalizedUsername.lowercase()}"
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
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchArtistDetail(artistId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false, errorMessage = null)
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
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false, errorMessage = null)
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

data class NavidromePlaylistDetailUiState(
    val detail: NavidromePlaylistDetail? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class NavidromePlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val playlistId: String =
        savedStateHandle.get<String>(NavidromeRoute.PLAYLIST_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(NavidromePlaylistDetailUiState())
    val uiState: StateFlow<NavidromePlaylistDetailUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchPlaylistDetail(playlistId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            detail = result.value,
                            isLoading = false,
                            errorMessage = null
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

    fun renamePlaylist(name: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.renamePlaylist(playlistId, name)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Playlist updated"
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun removeTrack(index: Int, title: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.removeTrackFromPlaylist(playlistId, index)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Removed \"$title\""
                        )
                    }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun deletePlaylist() {
        val playlistName = uiState.value.detail?.playlist?.name ?: "playlist"
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = navidromeRepository.deletePlaylist(playlistId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = "Deleted \"$playlistName\"",
                            deleted = true
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(actionMessage = null, errorMessage = null) }
    }
}

data class NavidromeSettingsUiState(
    val session: com.stillshelf.app.core.model.NavidromeSession? = null,
    val savedServers: List<SettingsServerOption> = emptyList(),
    val activeServerId: String? = null,
    val availableLibraries: List<NavidromeLibrary> = emptyList(),
    val activeLibraryId: String? = null,
    val automaticServerSwitchingEnabled: Boolean = false,
    val lanServerUrl: String = "",
    val wanServerUrl: String = "",
    val currentConnectionLabel: String = "",
    val currentEndpointUrl: String = "",
    val connectionStatusLabel: String = "Checking",
    val connectionLatencyMs: Long? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

private fun parseHost(baseUrl: String): String {
    return runCatching {
        URI(baseUrl).host?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: baseUrl
}

private data class Quadruple<A, B, C, D>(
    val session: A,
    val servers: B,
    val preferences: C,
    val connectionStatus: D
)

private data class Quintuple<A, B, C, D, E>(
    val session: A,
    val servers: B,
    val preferences: C,
    val connectionStatus: D,
    val endpointHealth: E
)

private fun NavidromeRadio.toTrack(): NavidromeTrack {
    return NavidromeTrack(
        id = "radio:$id",
        title = name,
        artistName = "Internet Radio",
        albumName = homePageUrl ?: "Live stream",
        albumId = null,
        artistId = null,
        trackNumber = null,
        durationSeconds = null,
        coverUrl = null,
        streamUrl = streamUrl,
        formatLabel = null,
        bitRateKbps = null
    )
}
