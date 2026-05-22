package com.stillshelf.app.ui.screens.navidrome

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MAX_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MIN_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_STEP_DB
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeEqualizerProfile
import com.stillshelf.app.core.model.NavidromeLibrary
import com.stillshelf.app.core.model.NavidromeLibraryResyncProgress
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlaylistDetail
import com.stillshelf.app.core.model.NavidromeQueueDisplayMode
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.NavidromeServerScanProgress
import com.stillshelf.app.core.model.NavidromeServerScanStatus
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.EndpointReachabilityStatus
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import com.stillshelf.app.core.network.NetworkConnectionType
import com.stillshelf.app.core.network.NetworkMonitor
import com.stillshelf.app.core.model.NavidromeCacheSizeOption
import com.stillshelf.app.core.model.flatNavidromeEqualizerBandLevels
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadItem
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadManager
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadStatus
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.ui.screens.ToggleSectionItem
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.ui.screens.SettingsServerOption
import com.stillshelf.app.ui.screens.resolveCurrentConnectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong

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
    val discoverAlbums: List<NavidromeAlbum> = emptyList(),
    val artists: List<NavidromeArtist> = emptyList(),
    val playlists: List<NavidromePlaylist> = emptyList(),
    val radios: List<NavidromeRadio> = emptyList(),
    val lyricsCacheSizeBytes: Long = 0L,
    val isOffline: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class NavidromeHomeViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeHomeUiState())
    val uiState: StateFlow<NavidromeHomeUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
        viewModelScope.launch {
            sessionPreferences.observeCachedNavidromeLyricsSizeBytes()
                .distinctUntilChanged()
                .collect { sizeBytes ->
                    mutableUiState.update { it.copy(lyricsCacheSizeBytes = sizeBytes) }
                }
        }
        viewModelScope.launch {
            networkMonitor.observeConnectionType()
                .distinctUntilChanged()
                .collect { type ->
                    mutableUiState.update {
                        it.copy(isOffline = type == NetworkConnectionType.Offline)
                    }
                }
        }
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
    }

    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchHome(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            recentAlbums = result.value.recentAlbums,
                            discoverAlbums = result.value.discoverAlbums,
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
    const val MUSIC_RADIO = "navidrome_music_radio"
    const val NEWEST_ALBUMS = "navidrome_newest_albums"
    const val SONGS = "navidrome_songs"
    const val DOWNLOADED = "navidrome_downloaded"
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

enum class NavidromeDownloadedLayoutMode {
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
        ToggleSectionItem(NavidromeListSectionIds.MUSIC_RADIO, "Music Radio"),
        ToggleSectionItem(NavidromeListSectionIds.RADIOS, "Internet Radio"),
        ToggleSectionItem(NavidromeListSectionIds.NEWEST_ALBUMS, "Newest Albums"),
        ToggleSectionItem(NavidromeListSectionIds.SONGS, "Songs"),
        ToggleSectionItem(NavidromeListSectionIds.DOWNLOADED, "Downloaded"),
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
    private val mutableSearchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = mutableSearchQuery.asStateFlow()

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

    fun onSearchQueryChange(value: String) {
        mutableSearchQuery.value = value
    }
}

@HiltViewModel
class NavidromeDownloadedViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableLayoutMode = MutableStateFlow(NavidromeDownloadedLayoutMode.List)
    val layoutMode: StateFlow<NavidromeDownloadedLayoutMode> = mutableLayoutMode.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                mutableLayoutMode.value = state.navidromeDownloadedLayoutMode
                    ?.let { raw -> enumValueOrNull<NavidromeDownloadedLayoutMode>(raw) }
                    ?: NavidromeDownloadedLayoutMode.List
            }
        }
    }

    fun setLayoutMode(value: NavidromeDownloadedLayoutMode) {
        if (mutableLayoutMode.value == value) return
        mutableLayoutMode.value = value
        viewModelScope.launch {
            sessionPreferences.setNavidromeDownloadedLayoutMode(value.name)
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
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
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
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
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
                    mutableUiState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = buildNavidromePlaylistAddMessage(
                                playlistName = playlistName,
                                addedCount = result.value.addedCount,
                                duplicateCount = result.value.duplicateCount
                            )
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

internal fun buildNavidromePlaylistAddMessage(
    playlistName: String,
    addedCount: Int,
    duplicateCount: Int
): String {
    return if (addedCount == 0) {
        if (duplicateCount == 1) {
            "This song is already in \"$playlistName\""
        } else {
            "All selected songs are already in \"$playlistName\""
        }
    } else if (duplicateCount > 0) {
        "Added $addedCount songs to \"$playlistName\". $duplicateCount already there"
    } else if (addedCount == 1) {
        "Added to \"$playlistName\""
    } else {
        "Added $addedCount songs to \"$playlistName\""
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
    val sortOption: NavidromeSongSortOption = NavidromeSongSortOption.ARTIST,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

enum class NavidromeSongSortOption(
    val label: String
) {
    TITLE_ASC("Title A-Z"),
    TITLE_DESC("Title Z-A"),
    ARTIST("Artist"),
    ALBUM("Album")
}

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

data class NavidromeDownloadsUiState(
    val downloadedTrackIds: Set<String> = emptySet(),
    val cachedTrackIds: Set<String> = emptySet(),
    val trackProgressById: Map<String, Int> = emptyMap(),
    val albumProgressById: Map<String, Int> = emptyMap(),
    val artistProgressById: Map<String, Int> = emptyMap(),
    val downloadedTrackCountByAlbumId: Map<String, Int> = emptyMap(),
    val fullyDownloadedAlbumCountByArtistId: Map<String, Int> = emptyMap(),
    val homeDownloads: List<NavidromeHomeDownloadEntry> = emptyList(),
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val errorMessage: String? = null
)

sealed interface NavidromeHomeDownloadEntry {
    val key: String
    val title: String
    val subtitle: String
    val coverUrl: String?
    val albumId: String?
    val artistId: String?
}

data class NavidromeHomeDownloadedAlbum(
    val album: NavidromeAlbum,
    override val subtitle: String
) : NavidromeHomeDownloadEntry {
    override val key: String = "album:${album.id}"
    override val title: String = album.name
    override val coverUrl: String? = album.coverUrl
    override val albumId: String = album.id
    override val artistId: String? = album.artistId
}

data class NavidromeHomeDownloadedTrack(
    val track: NavidromeTrack,
    override val subtitle: String
) : NavidromeHomeDownloadEntry {
    override val key: String = "track:${track.id}"
    override val title: String = track.title
    override val coverUrl: String? = track.coverUrl
    override val albumId: String? = track.albumId
    override val artistId: String? = track.artistId
}

@HiltViewModel
class NavidromeDownloadsViewModel @Inject constructor(
    private val downloadManager: NavidromeDownloadManager,
    private val navidromeRepository: NavidromeRepository
) : ViewModel() {
    private val mutableFeedbackState = MutableStateFlow(
        NavidromeDownloadsUiState()
    )

    val uiState: StateFlow<NavidromeDownloadsUiState> = combine(
        downloadManager.activeItems,
        downloadManager.activeCacheItems,
        mutableFeedbackState
    ) { items, cacheItems, feedback ->
        val cachedTrackIds = cacheItems.map { it.trackId }.toSet()
        val completedItems = items.filter { it.status == NavidromeDownloadStatus.Completed }
        val downloadingItems = items.filter {
            it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading
        }
        val downloadedTrackCountByAlbumId = completedItems
            .mapNotNull { item -> item.albumId?.let { albumId -> albumId to item.trackId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, trackIds) -> trackIds.distinct().size }
        val albumProgressById = items
            .filter { it.albumId != null }
            .groupBy { it.albumId!! }
            .mapNotNull { (albumId, albumItems) ->
                val hasActive = albumItems.any {
                    it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading
                }
                if (!hasActive) {
                    null
                } else {
                    val progress = albumItems
                        .filter { it.status != NavidromeDownloadStatus.Failed }
                        .map {
                            when (it.status) {
                                NavidromeDownloadStatus.Completed -> 100
                                NavidromeDownloadStatus.Queued,
                                NavidromeDownloadStatus.Downloading -> it.progressPercent.coerceIn(0, 99)
                                NavidromeDownloadStatus.Failed -> 0
                            }
                        }
                        .ifEmpty { listOf(0) }
                        .average()
                        .roundToInt()
                        .coerceIn(0, 99)
                    albumId to progress
                }
            }
            .toMap()
        val artistProgressById = items
            .filter { it.artistId != null }
            .groupBy { it.artistId!! }
            .mapNotNull { (artistId, artistItems) ->
                val hasActive = artistItems.any {
                    it.status == NavidromeDownloadStatus.Queued || it.status == NavidromeDownloadStatus.Downloading
                }
                if (!hasActive) {
                    null
                } else {
                    val progress = artistItems
                        .filter { it.status != NavidromeDownloadStatus.Failed }
                        .map {
                            when (it.status) {
                                NavidromeDownloadStatus.Completed -> 100
                                NavidromeDownloadStatus.Queued,
                                NavidromeDownloadStatus.Downloading -> it.progressPercent.coerceIn(0, 99)
                                NavidromeDownloadStatus.Failed -> 0
                            }
                        }
                        .ifEmpty { listOf(0) }
                        .average()
                        .roundToInt()
                        .coerceIn(0, 99)
                    artistId to progress
                }
            }
            .toMap()
        val fullyDownloadedAlbumCountByArtistId = computeFullyDownloadedAlbumCountByArtistId(completedItems)

        feedback.copy(
            downloadedTrackIds = completedItems.map { it.trackId }.toSet(),
            cachedTrackIds = cachedTrackIds,
            trackProgressById = downloadingItems.associate { it.trackId to it.progressPercent.coerceIn(0, 99) },
            albumProgressById = albumProgressById,
            artistProgressById = artistProgressById,
            downloadedTrackCountByAlbumId = downloadedTrackCountByAlbumId,
            fullyDownloadedAlbumCountByArtistId = fullyDownloadedAlbumCountByArtistId,
            homeDownloads = buildNavidromeHomeDownloadEntries(completedItems)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NavidromeDownloadsUiState()
    )

    fun clearMessages() {
        mutableFeedbackState.update { it.copy(actionMessage = null, errorMessage = null) }
    }

    fun toggleTrackDownload(track: NavidromeTrack, albumSongCount: Int? = null) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            val resolvedAlbumSongCount = resolveTrackAlbumSongCount(track, albumSongCount)
            when (val result = downloadManager.toggleTrackDownload(track, resolvedAlbumSongCount)) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(
                            isSubmitting = false,
                            actionMessage = result.value.message
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    private suspend fun resolveTrackAlbumSongCount(
        track: NavidromeTrack,
        explicitAlbumSongCount: Int?
    ): Int? {
        if (explicitAlbumSongCount != null) return explicitAlbumSongCount
        val albumId = track.albumId?.trim().orEmpty()
        if (albumId.isBlank()) return null
        return when (val detailResult = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = false)) {
            is AppResult.Success -> detailResult.value.album.songCount
            is AppResult.Error -> null
        }
    }

    fun toggleAlbumDownload(album: NavidromeAlbum, tracks: List<NavidromeTrack>) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            when (
                val result = downloadManager.toggleTrackBatchDownload(
                    tracks = tracks,
                    albumSongCountByAlbumId = mapOf(album.id to album.songCount),
                    downloadLabel = "album"
                )
            ) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, actionMessage = result.value.message)
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun toggleAlbumDownload(album: NavidromeAlbum) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            when (val detailResult = navidromeRepository.fetchAlbumDetail(album.id, forceRefresh = false)) {
                is AppResult.Success -> {
                    when (
                        val result = downloadManager.toggleTrackBatchDownload(
                            tracks = detailResult.value.tracks,
                            albumSongCountByAlbumId = mapOf(album.id to album.songCount),
                            downloadLabel = "album"
                        )
                    ) {
                        is AppResult.Success -> {
                            mutableFeedbackState.update {
                                it.copy(isSubmitting = false, actionMessage = result.value.message)
                            }
                        }

                        is AppResult.Error -> {
                            mutableFeedbackState.update {
                                it.copy(isSubmitting = false, errorMessage = result.message)
                            }
                        }
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = detailResult.message)
                    }
                }
            }
        }
    }

    fun toggleArtistDownload(artist: NavidromeArtist, albums: List<NavidromeAlbum>) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            val albumDetails = buildList {
                for (album in albums) {
                    when (val result = navidromeRepository.fetchAlbumDetail(album.id, forceRefresh = false)) {
                        is AppResult.Success -> add(result.value)
                        is AppResult.Error -> {
                            mutableFeedbackState.update {
                                it.copy(isSubmitting = false, errorMessage = result.message)
                            }
                            return@launch
                        }
                    }
                }
            }
            val tracks = albumDetails.flatMap { it.tracks }
            val songCounts = albumDetails.associate { it.album.id to it.album.songCount }
            when (
                val result = downloadManager.toggleTrackBatchDownload(
                    tracks = tracks,
                    albumSongCountByAlbumId = songCounts,
                    downloadLabel = "artist"
                )
            ) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, actionMessage = result.value.message)
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun removeTrackDownload(trackId: String) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            when (val result = downloadManager.removeTrackDownload(trackId)) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, actionMessage = result.value.message)
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun removeAlbumDownload(albumId: String) {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            when (val result = downloadManager.removeAlbumDownload(albumId)) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, actionMessage = result.value.message)
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun removeAllDownloads() {
        viewModelScope.launch {
            mutableFeedbackState.update { it.copy(isSubmitting = true, actionMessage = null, errorMessage = null) }
            when (val result = downloadManager.removeAllDownloads()) {
                is AppResult.Success -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, actionMessage = result.value.message)
                    }
                }

                is AppResult.Error -> {
                    mutableFeedbackState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }
}

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
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeSongsUiState())
    val uiState: StateFlow<NavidromeSongsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { state ->
                val sortOption = state.navidromeSongSort
                    ?.let { raw -> enumValueOrNull<NavidromeSongSortOption>(raw) }
                    ?: NavidromeSongSortOption.ARTIST
                mutableUiState.update { current -> current.copy(sortOption = sortOption) }
            }
        }
        refresh(forceRefresh = false)
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
    }

    fun setSortOption(value: NavidromeSongSortOption) {
        if (mutableUiState.value.sortOption == value) return
        mutableUiState.update { it.copy(sortOption = value) }
        viewModelScope.launch {
            sessionPreferences.setNavidromeSongSort(value.name)
        }
    }

    fun onSearchQueryChange(value: String) {
        mutableUiState.update { it.copy(searchQuery = value) }
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
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
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
    private val sessionPreferences: SessionPreferences,
    private val downloadManager: NavidromeDownloadManager,
    private val playerController: NavidromePlayerController
) : ViewModel() {
    private val mutableLocalState = MutableStateFlow(NavidromeSettingsLocalState())

    val uiState: StateFlow<NavidromeSettingsUiState> = combine(
        combine(
            combine(
                navidromeRepository.observeSession(),
                navidromeRepository.observeServers(),
                sessionPreferences.state,
                navidromeRepository.observeActiveConnectionStatus(),
                navidromeRepository.observeEndpointHealth()
            ) { session, servers, preferences, connectionStatus, endpointHealth ->
                Quintuple(session, servers, preferences, connectionStatus, endpointHealth)
            },
            sessionPreferences.observeCachedNavidromeLyricsSizeBytes(),
            downloadManager.activeCacheItems
        ) { upstream, lyricsCacheSizeBytes, cacheItems ->
            Septuple(
                upstream.session,
                upstream.servers,
                upstream.preferences,
                upstream.connectionStatus,
                upstream.endpointHealth,
                lyricsCacheSizeBytes,
                Pair(
                    cacheItems.sumOf { item ->
                        item.fileSizeBytes
                            ?: item.localPath
                                ?.takeIf { it.isNotBlank() }
                                ?.let { path -> runCatching { File(path).length() }.getOrNull() }
                            ?: 0L
                    },
                    cacheItems.size
                )
            )
        },
        mutableLocalState
    ) { upstream, localState ->
        val session = upstream.session
        val servers = upstream.servers
        val preferences = upstream.preferences
        val connectionStatus = upstream.connectionStatus
        val endpointHealth = upstream.endpointHealth
        val lyricsCacheSizeBytes = upstream.sixth
        val playbackCacheUsageBytes = upstream.seventh.first
        val playbackCacheItemCount = upstream.seventh.second
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
            lyricsSources = preferences.navidromeLyricsSources.map { source ->
                SettingsServerOption(
                    id = source.id,
                    name = source.name,
                    baseUrl = source.baseUrl,
                    host = parseHost(source.baseUrl)
                )
            },
            activeLyricsSourceId = preferences.activeNavidromeLyricsSourceId,
            lyricsCacheSizeBytes = lyricsCacheSizeBytes,
            playbackCacheSizeLimit = preferences.navidromeCacheSizeLimit,
            playbackCacheUsageBytes = playbackCacheUsageBytes,
            playbackCacheItemCount = playbackCacheItemCount,
            isClearingCache = localState.isClearingCache,
            navidromeSkipForwardSeconds = preferences.navidromeSkipForwardSeconds,
            navidromeSkipBackwardSeconds = preferences.navidromeSkipBackwardSeconds,
            activeServerId = activeServerId,
            availableLibraries = localState.libraries,
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
            lastLibrarySyncAtMs = preferences.lastLibrarySyncAtMs,
            isBusy = localState.isBusy,
            resyncProgress = localState.resyncProgress,
            serverScanProgress = localState.serverScanProgress,
            showServerScanProgressDialog = localState.showServerScanProgressDialog,
            syncToastMessage = localState.syncToastMessage,
            errorMessage = localState.errorMessage
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
                    if (serverId != null) {
                        refreshLibraries(forceRefresh = false)
                    } else {
                        mutableLocalState.update { it.copy(libraries = emptyList()) }
                    }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            mutableLocalState.update { it.copy(isBusy = true) }
            val state = uiState.value
            val activeServerId = state.activeServerId
            val activeServer = state.savedServers.firstOrNull { it.id == activeServerId }
            if (activeServer != null) {
                val hasFallbackServer = state.savedServers.any { it.id != activeServerId }
                when (val result = navidromeRepository.deleteServer(activeServer.id)) {
                    is AppResult.Success -> {
                        mutableLocalState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = null,
                                syncToastMessage = if (hasFallbackServer) {
                                    "Signed out and removed ${activeServer.name} from this device."
                                } else {
                                    null
                                }
                            )
                        }
                        refreshConnectionStatus()
                        refreshLibraries(forceRefresh = false)
                    }

                    is AppResult.Error -> {
                        mutableLocalState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            } else {
                navidromeRepository.signOut()
                mutableLocalState.update {
                    it.copy(
                        isBusy = false,
                        libraries = emptyList(),
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun onResyncLibraryClick() {
        if (mutableLocalState.value.isBusy) return
        viewModelScope.launch {
            mutableLocalState.update {
                it.copy(
                    isBusy = true,
                    syncToastMessage = null,
                    resyncProgress = NavidromeLibraryResyncProgress(
                        title = "Preparing resync",
                        detail = "Refreshing Navidrome data from the server.",
                        completedSteps = 0,
                        totalSteps = 5
                    )
                )
            }
            when (
                val result = navidromeRepository.resyncLibrary { progress ->
                    mutableLocalState.update { state -> state.copy(resyncProgress = progress) }
                }
            ) {
                is AppResult.Success -> {
                    val syncedAtMs = System.currentTimeMillis()
                    sessionPreferences.setLastLibrarySyncAtMs(syncedAtMs)
                    mutableLocalState.update {
                        it.copy(
                            libraries = result.value,
                            errorMessage = null,
                            syncToastMessage = "Library resynced",
                            isBusy = false,
                            resyncProgress = null
                        )
                    }
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableLocalState.update {
                        it.copy(
                            errorMessage = result.message,
                            syncToastMessage = "Resync failed",
                            isBusy = false,
                            resyncProgress = null
                        )
                    }
                }
            }
        }
    }

    fun onTriggerServerScanClick() {
        if (mutableLocalState.value.isBusy || mutableLocalState.value.serverScanProgress?.isRunning == true) return
        viewModelScope.launch {
            mutableLocalState.update {
                it.copy(
                    isBusy = true,
                    syncToastMessage = null,
                    serverScanProgress = NavidromeServerScanProgress(
                        title = "Starting server scan",
                        detail = "Asking Navidrome to scan the server library.",
                        isRunning = true
                    ),
                    showServerScanProgressDialog = true
                )
            }
            when (val startResult = navidromeRepository.triggerServerScan(fullScan = false)) {
                is AppResult.Success -> {
                    var status = startResult.value
                    mutableLocalState.update {
                        it.copy(
                            isBusy = false,
                            serverScanProgress = status.toProgress(detail = buildServerScanDetail(status))
                        )
                    }
                    var pollCount = 0
                    while (status.scanning && pollCount < SERVER_SCAN_MAX_POLLS) {
                        delay(SERVER_SCAN_POLL_DELAY_MS)
                        when (val statusResult = navidromeRepository.fetchServerScanStatus()) {
                            is AppResult.Success -> {
                                status = statusResult.value
                                mutableLocalState.update {
                                    it.copy(
                                        serverScanProgress = status.toProgress(
                                            detail = buildServerScanDetail(status)
                                        )
                                    )
                                }
                            }

                            is AppResult.Error -> {
                                mutableLocalState.update {
                                    it.copy(
                                        errorMessage = statusResult.message,
                                        syncToastMessage = "Unable to track server scan",
                                        serverScanProgress = NavidromeServerScanProgress(
                                            title = "Server scan started",
                                            detail = "Navidrome started scanning, but the app could not keep tracking the status.",
                                            status = status,
                                            isRunning = false
                                        ),
                                        isBusy = false
                                    )
                                }
                                return@launch
                            }
                        }
                        pollCount += 1
                    }
                    if (status.scanning) {
                        mutableLocalState.update {
                            it.copy(
                                serverScanProgress = NavidromeServerScanProgress(
                                    title = "Server scan still running",
                                    detail = "Navidrome is still scanning. You can close this window and resync the app library after the scan finishes.",
                                    status = status,
                                    isRunning = false
                                ),
                                syncToastMessage = "Server scan is still running"
                            )
                        }
                    } else {
                        mutableLocalState.update {
                            it.copy(
                                serverScanProgress = NavidromeServerScanProgress(
                                    title = "Server scan finished",
                                    detail = "Navidrome finished scanning. Use Resync Library to pull the fresh changes into the app.",
                                    status = status,
                                    isRunning = false
                                ),
                                syncToastMessage = "Server scan finished"
                            )
                        }
                    }
                    mutableLocalState.update { it.copy(errorMessage = null, isBusy = false) }
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableLocalState.update {
                        it.copy(
                            errorMessage = startResult.message,
                            syncToastMessage = "Server scan failed",
                            serverScanProgress = null,
                            showServerScanProgressDialog = false,
                            isBusy = false
                        )
                    }
                }
            }
        }
    }

    fun dismissServerScanProgress() {
        mutableLocalState.update { state ->
            if (state.serverScanProgress?.isRunning == true) {
                state.copy(showServerScanProgressDialog = false)
            } else {
                state.copy(
                    showServerScanProgressDialog = false,
                    serverScanProgress = null
                )
            }
        }
    }

    fun consumeSyncToastMessage() {
        mutableLocalState.update { it.copy(syncToastMessage = null) }
    }

    fun setPlaybackCacheSizeLimit(option: String) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeCacheSizeLimit(option)
            if (NavidromeCacheSizeOption.toBytes(option) != null) {
                playerController.invalidatePlaybackCacheWarmup()
            }
        }
    }

    fun setNavidromeSkipForwardSeconds(seconds: Int) {
        viewModelScope.launch { sessionPreferences.setNavidromeSkipForwardSeconds(seconds) }
    }

    fun setNavidromeSkipBackwardSeconds(seconds: Int) {
        viewModelScope.launch { sessionPreferences.setNavidromeSkipBackwardSeconds(seconds) }
    }

    fun clearPlaybackCache() {
        if (mutableLocalState.value.isClearingCache) return
        viewModelScope.launch {
            mutableLocalState.update { it.copy(isClearingCache = true) }
            downloadManager.clearPlaybackCache()
            playerController.invalidatePlaybackCacheWarmup()
            mutableLocalState.update { it.copy(isClearingCache = false, syncToastMessage = "Cached music cleared") }
        }
    }

    fun setActiveServer(serverId: String) {
        if (mutableLocalState.value.isBusy) return
        viewModelScope.launch {
            mutableLocalState.update { it.copy(isBusy = true) }
            when (val result = navidromeRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    mutableLocalState.update { it.copy(isBusy = false, errorMessage = null) }
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = false)
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun updateServer(serverId: String, name: String, baseUrl: String) {
        if (mutableLocalState.value.isBusy) return
        viewModelScope.launch {
            mutableLocalState.update { it.copy(isBusy = true) }
            when (val result = navidromeRepository.updateServer(serverId, name, baseUrl)) {
                is AppResult.Success -> {
                    mutableLocalState.update { it.copy(isBusy = false, errorMessage = null) }
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun deleteServer(serverId: String) {
        if (mutableLocalState.value.isBusy) return
        viewModelScope.launch {
            val serverName = uiState.value.savedServers.firstOrNull { it.id == serverId }?.name
            mutableLocalState.update { it.copy(isBusy = true) }
            when (val result = navidromeRepository.deleteServer(serverId)) {
                is AppResult.Success -> {
                    mutableLocalState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = null,
                            syncToastMessage = serverName?.let { name -> "Deleted $name." }
                        )
                    }
                    refreshConnectionStatus()
                    refreshLibraries(forceRefresh = false)
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(isBusy = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun setActiveLyricsSource(sourceId: String) {
        viewModelScope.launch {
            sessionPreferences.setActiveNavidromeLyricsSourceId(sourceId)
            navidromeRepository.clearLyricsCache()
            mutableLocalState.update { it.copy(errorMessage = null) }
        }
    }

    fun addLyricsSource(name: String, baseUrl: String) {
        val normalizedName = name.trim()
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
        if (normalizedName.length < 2) {
            mutableLocalState.update { it.copy(errorMessage = "Source name must be at least 2 characters.") }
            return
        }
        if (!normalizedBaseUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            mutableLocalState.update { it.copy(errorMessage = "Base URL must start with http:// or https://") }
            return
        }
        viewModelScope.launch {
            val current = sessionPreferences.state.first().navidromeLyricsSources
            val source = com.stillshelf.app.core.model.NavidromeLyricsSource(
                id = UUID.randomUUID().toString(),
                name = normalizedName,
                baseUrl = normalizedBaseUrl,
                createdAt = System.currentTimeMillis()
            )
            sessionPreferences.setNavidromeLyricsSources(current + source)
            if (sessionPreferences.state.first().activeNavidromeLyricsSourceId.isNullOrBlank()) {
                sessionPreferences.setActiveNavidromeLyricsSourceId(source.id)
            }
            mutableLocalState.update { it.copy(errorMessage = null) }
        }
    }

    fun updateLyricsSource(sourceId: String, name: String, baseUrl: String) {
        val normalizedName = name.trim()
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
        if (normalizedName.length < 2) {
            mutableLocalState.update { it.copy(errorMessage = "Source name must be at least 2 characters.") }
            return
        }
        if (!normalizedBaseUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            mutableLocalState.update { it.copy(errorMessage = "Base URL must start with http:// or https://") }
            return
        }
        viewModelScope.launch {
            val state = sessionPreferences.state.first()
            val updated = state.navidromeLyricsSources.map { source ->
                if (source.id == sourceId) {
                    source.copy(
                        name = normalizedName,
                        baseUrl = normalizedBaseUrl
                    )
                } else {
                    source
                }
            }
            sessionPreferences.setNavidromeLyricsSources(updated)
            navidromeRepository.clearLyricsCache()
            mutableLocalState.update { it.copy(errorMessage = null) }
        }
    }

    fun deleteLyricsSource(sourceId: String) {
        viewModelScope.launch {
            val state = sessionPreferences.state.first()
            val updated = state.navidromeLyricsSources.filterNot { it.id == sourceId }
            sessionPreferences.setNavidromeLyricsSources(updated)
            if (state.activeNavidromeLyricsSourceId == sourceId) {
                sessionPreferences.setActiveNavidromeLyricsSourceId(updated.firstOrNull()?.id)
            }
            mutableLocalState.update { it.copy(errorMessage = null) }
        }
    }

    fun clearAllLyricsCache() {
        viewModelScope.launch {
            navidromeRepository.clearLyricsCache()
            mutableLocalState.update {
                it.copy(
                    errorMessage = null,
                    syncToastMessage = "Lyrics cache cleared"
                )
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
                    mutableLocalState.update { it.copy(errorMessage = null) }
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(errorMessage = result.message) }
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
        mutableLocalState.update { it.copy(errorMessage = null) }
    }

    fun setActiveLibrary(libraryId: String) {
        viewModelScope.launch {
            when (val result = navidromeRepository.setActiveLibrary(libraryId)) {
                is AppResult.Success -> {
                    mutableLocalState.update { it.copy(errorMessage = null) }
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            when (val result = navidromeRepository.refreshActiveConnectionStatus()) {
                is AppResult.Success -> {
                    mutableLocalState.update { it.copy(errorMessage = null) }
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun refreshLibraries(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchLibraries(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableLocalState.update {
                        it.copy(
                            libraries = result.value,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableLocalState.update { state ->
                        state.copy(errorMessage = result.message)
                    }
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
                    mutableLocalState.update { it.copy(errorMessage = null) }
                    refreshConnectionStatus()
                }

                is AppResult.Error -> {
                    mutableLocalState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }
}

data class NavidromeEqualizerUiState(
    val isEnabled: Boolean = false,
    val activeProfileId: String? = null,
    val profiles: List<NavidromeEqualizerProfile> = emptyList(),
    val editorProfile: NavidromeEqualizerProfile = newNavidromeEqualizerDraft(emptyList()),
    val isEditorPersisted: Boolean = false,
    val isEditorDirty: Boolean = false
) {
    val activeProfileName: String
        get() = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "Off"

    val canSave: Boolean
        get() = editorProfile.name.trim().isNotBlank() && (!isEditorPersisted || isEditorDirty)

    val canDelete: Boolean
        get() = isEditorPersisted || isEditorDirty
}

private data class NavidromeEqualizerEditorResolution(
    val profile: NavidromeEqualizerProfile,
    val isPersisted: Boolean,
    val isDirty: Boolean
)

@HiltViewModel
class NavidromeEqualizerViewModel @Inject constructor(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeEqualizerUiState())
    val uiState: StateFlow<NavidromeEqualizerUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionPreferences.state.collect { preferences ->
                val profiles = preferences.navidromeEqualizerProfiles
                val activeProfileId = preferences.navidromeEqualizerActiveProfileId
                    ?.takeIf { activeId -> profiles.any { it.id == activeId } }
                val previousState = mutableUiState.value
                val editorResolution = resolveNavidromeEqualizerEditorState(
                    state = previousState,
                    profiles = profiles,
                    activeProfileId = activeProfileId
                )
                mutableUiState.value = previousState.copy(
                    isEnabled = preferences.navidromeEqualizerEnabled,
                    activeProfileId = activeProfileId,
                    profiles = profiles,
                    editorProfile = editorResolution.profile,
                    isEditorPersisted = editorResolution.isPersisted,
                    isEditorDirty = editorResolution.isDirty
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionPreferences.setNavidromeEqualizerEnabled(enabled)
        }
    }

    fun setActiveProfile(profileId: String?) {
        val resolvedProfileId = profileId?.takeIf { requestedId ->
            uiState.value.profiles.any { it.id == requestedId }
        }
        viewModelScope.launch {
            sessionPreferences.setNavidromeEqualizerActiveProfileId(resolvedProfileId)
        }
    }

    fun selectEditorProfile(profileId: String) {
        val selected = uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        mutableUiState.update {
            it.copy(
                editorProfile = selected,
                isEditorPersisted = true,
                isEditorDirty = false
            )
        }
    }

    fun createNewProfile() {
        mutableUiState.update { state ->
            state.copy(
                editorProfile = newNavidromeEqualizerDraft(state.profiles),
                isEditorPersisted = false,
                isEditorDirty = false
            )
        }
    }

    fun updateEditorName(name: String) {
        mutableUiState.update { state ->
            state.copy(
                editorProfile = state.editorProfile.copy(name = name.take(40)),
                isEditorDirty = true
            )
        }
    }

    fun updateBandLevel(index: Int, levelDb: Float) {
        val current = uiState.value
        if (index !in current.editorProfile.normalizedBandLevelsDb().indices) return
        val normalizedLevel = normalizeNavidromeEqualizerLevel(levelDb)
        val updatedLevels = current.editorProfile.normalizedBandLevelsDb().toMutableList()
        if (abs(updatedLevels[index] - normalizedLevel) < 0.001f) return
        updatedLevels[index] = normalizedLevel
        mutableUiState.update { state ->
            state.copy(
                editorProfile = state.editorProfile.copy(bandLevelsDb = updatedLevels),
                isEditorDirty = true
            )
        }
    }

    fun saveEditor() {
        val state = uiState.value
        val draft = state.editorProfile.copy(
            name = state.editorProfile.name.trim().ifBlank {
                newNavidromeEqualizerDraft(state.profiles).name
            },
            bandLevelsDb = state.editorProfile.normalizedBandLevelsDb()
        )
        val updatedProfiles = state.profiles.toMutableList().apply {
            val existingIndex = indexOfFirst { it.id == draft.id }
            if (existingIndex >= 0) {
                this[existingIndex] = draft
            } else {
                add(draft)
            }
        }
        viewModelScope.launch {
            sessionPreferences.setNavidromeEqualizerProfiles(updatedProfiles)
        }
        mutableUiState.update {
            it.copy(
                editorProfile = draft,
                isEditorPersisted = true,
                isEditorDirty = false
            )
        }
    }

    fun deleteEditor() {
        val state = uiState.value
        if (!state.isEditorPersisted) {
            mutableUiState.update {
                it.copy(
                    editorProfile = newNavidromeEqualizerDraft(state.profiles),
                    isEditorPersisted = false,
                    isEditorDirty = false
                )
            }
            return
        }

        val remainingProfiles = state.profiles.filterNot { it.id == state.editorProfile.id }
        val nextEditor = remainingProfiles.firstOrNull() ?: newNavidromeEqualizerDraft(remainingProfiles)
        val nextIsPersisted = remainingProfiles.any { it.id == nextEditor.id }
        viewModelScope.launch {
            sessionPreferences.setNavidromeEqualizerProfiles(remainingProfiles)
            if (state.activeProfileId == state.editorProfile.id) {
                sessionPreferences.setNavidromeEqualizerActiveProfileId(null)
            }
        }
        mutableUiState.update {
            it.copy(
                activeProfileId = if (state.activeProfileId == state.editorProfile.id) null else state.activeProfileId,
                profiles = remainingProfiles,
                editorProfile = nextEditor,
                isEditorPersisted = nextIsPersisted,
                isEditorDirty = false
            )
        }
    }
}

@HiltViewModel
class NavidromePlayerViewModel @Inject constructor(
    private val playerController: NavidromePlayerController,
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableLyricsUiState = MutableStateFlow(NavidromeLyricsUiState())
    private var lyricsLoadJob: kotlinx.coroutines.Job? = null
    private var lyricsLoadRequestId = 0L
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
    val lyricsUiState: StateFlow<NavidromeLyricsUiState> = mutableLyricsUiState.asStateFlow()
    val favoriteTrackIds: StateFlow<Set<String>> = preferenceState
        .map { state -> favoriteTracksForCurrentSession(state).map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )
    val skipForwardSeconds: StateFlow<Int> = preferenceState
        .map { it.navidromeSkipForwardSeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)
    val skipBackwardSeconds: StateFlow<Int> = preferenceState
        .map { it.navidromeSkipBackwardSeconds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)

    init {
        viewModelScope.launch {
            playerController.state
                .map { it.currentTrack }
                .distinctUntilChangedBy { it?.id }
                .collect { track ->
                    val state = mutableLyricsUiState.value
                    if (track == null) {
                        lyricsLoadJob?.cancel()
                        lyricsLoadJob = null
                        lyricsLoadRequestId += 1L
                        mutableLyricsUiState.value = NavidromeLyricsUiState()
                    } else if (state.isVisible) {
                        loadLyrics(track, showSheet = true)
                    } else if (state.trackId != track.id) {
                        mutableLyricsUiState.value = NavidromeLyricsUiState(trackId = track.id)
                    }
                }
        }
    }

    fun playTracks(
        tracks: List<NavidromeTrack>,
        startIndex: Int,
        queueDisplayMode: NavidromeQueueDisplayMode = NavidromeQueueDisplayMode.FULL
    ) {
        playerController.playTracks(tracks, startIndex, queueDisplayMode)
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

    fun removeQueueItem(index: Int): Boolean {
        return playerController.removeTrackFromQueue(index)
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

    fun playAlbums(albums: List<NavidromeAlbum>, shuffle: Boolean = false) {
        val albumIds = albums
            .map { it.id.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (albumIds.isEmpty()) return

        viewModelScope.launch {
            val tracks = buildList {
                albumIds.forEach { albumId ->
                    when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = false)) {
                        is AppResult.Success -> addAll(result.value.tracks)
                        is AppResult.Error -> Unit
                    }
                }
            }
                .filter { it.id.isNotBlank() }

            if (tracks.isEmpty()) return@launch
            val queue = if (shuffle) tracks.shuffled() else tracks
            playerController.playTracks(queue, startIndex = 0)
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

    fun seekForward() {
        playerController.seekBy(skipForwardSeconds.value * 1000L)
    }

    fun seekBackward() {
        playerController.seekBy(-skipBackwardSeconds.value * 1000L)
    }

    fun refreshAudioOutputs() {
        playerController.refreshAudioOutputs()
    }

    fun selectAudioOutputDevice(deviceId: Int?) {
        playerController.selectAudioOutputDevice(deviceId)
    }

    fun showLyrics() {
        val track = uiState.value.currentTrack ?: return
        loadLyrics(track = track, showSheet = true)
    }

    fun dismissLyrics() {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        lyricsLoadRequestId += 1L
        mutableLyricsUiState.update { it.copy(isVisible = false, errorMessage = null) }
    }

    fun clearLyricsCache() {
        viewModelScope.launch {
            navidromeRepository.clearLyricsCache(uiState.value.currentTrack)
        }
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

    private fun loadLyrics(track: NavidromeTrack, showSheet: Boolean) {
        lyricsLoadJob?.cancel()
        val requestId = lyricsLoadRequestId + 1L
        lyricsLoadRequestId = requestId
        lyricsLoadJob = viewModelScope.launch {
            mutableLyricsUiState.update {
                it.copy(
                    isVisible = showSheet,
                    isLoading = true,
                    loadingMessage = "Searching for lyrics...",
                    trackId = track.id,
                    trackTitle = track.title,
                    albumName = track.albumName,
                    artistName = track.artistName,
                    sourceLabel = null,
                    lyrics = emptyList(),
                    isSynced = false,
                    errorMessage = null
                )
            }
            val loadingMessageJob = launch {
                delay(4_000)
                mutableLyricsUiState.update { current ->
                    if (
                        lyricsLoadRequestId != requestId ||
                        current.trackId != track.id ||
                        !current.isLoading
                    ) {
                        current
                    } else {
                        current.copy(loadingMessage = "Trying fallback sources...")
                    }
                }
            }
            when (val result = navidromeRepository.fetchLyrics(track)) {
                is AppResult.Success -> {
                    loadingMessageJob.cancel()
                    mutableLyricsUiState.update { current ->
                        if (
                            lyricsLoadRequestId != requestId ||
                            current.trackId != track.id ||
                            (showSheet && !current.isVisible)
                        ) {
                            return@update current
                        }
                        current.copy(
                            isVisible = showSheet,
                            isLoading = false,
                            loadingMessage = null,
                            sourceLabel = result.value.sourceLabel,
                            lyrics = result.value.lines,
                            isSynced = result.value.isSynced,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    loadingMessageJob.cancel()
                    mutableLyricsUiState.update { current ->
                        if (
                            lyricsLoadRequestId != requestId ||
                            current.trackId != track.id ||
                            (showSheet && !current.isVisible)
                        ) {
                            return@update current
                        }
                        current.copy(
                            isVisible = showSheet,
                            isLoading = false,
                            loadingMessage = null,
                            lyrics = emptyList(),
                            sourceLabel = null,
                            isSynced = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
            if (lyricsLoadRequestId == requestId) {
                lyricsLoadJob = null
            }
        }
    }
}

data class NavidromeLyricsUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val trackId: String? = null,
    val trackTitle: String = "",
    val albumName: String = "",
    val artistName: String = "",
    val sourceLabel: String? = null,
    val lyrics: List<com.stillshelf.app.core.model.NavidromeLyricsLine> = emptyList(),
    val isSynced: Boolean = false,
    val errorMessage: String? = null
)

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
data class NavidromeMusicRadioUiState(
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isStartingPlayback: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

@HiltViewModel
class NavidromeMusicRadioViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val playerController: NavidromePlayerController
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeMusicRadioUiState())
    val uiState: StateFlow<NavidromeMusicRadioUiState> = mutableUiState.asStateFlow()

    init {
        loadGenres()
    }

    fun loadGenres() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchGenres()) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(genres = result.value, isLoading = false)
                }
                is AppResult.Error -> mutableUiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun playLibraryRadio() {
        startRadio(genre = null)
    }

    fun playGenreRadio(genre: String) {
        startRadio(genre = genre)
    }

    fun clearMessages() {
        mutableUiState.update { it.copy(errorMessage = null, actionMessage = null) }
    }

    private fun startRadio(genre: String?) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isStartingPlayback = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchRandomSongs(count = 500, genre = genre)) {
                is AppResult.Success -> {
                    val tracks = result.value
                    if (tracks.isEmpty()) {
                        mutableUiState.update {
                            it.copy(
                                isStartingPlayback = false,
                                errorMessage = "No songs found for this radio."
                            )
                        }
                    } else {
                        playerController.playTracks(tracks.shuffled(), startIndex = 0)
                        mutableUiState.update {
                            it.copy(isStartingPlayback = false, actionMessage = "Playing ${tracks.size} songs")
                        }
                    }
                }
                is AppResult.Error -> mutableUiState.update {
                    it.copy(isStartingPlayback = false, errorMessage = result.message)
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
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val artistId: String =
        savedStateHandle.get<String>(NavidromeRoute.ARTIST_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(NavidromeArtistDetailUiState())
    val uiState: StateFlow<NavidromeArtistDetailUiState> = mutableUiState.asStateFlow()
    private var hasObservedLibrarySyncAtMs = false
    private var lastObservedLibrarySyncAtMs: Long? = null
    private val refreshGeneration = AtomicLong(0L)

    init {
        observeLibrarySyncs()
        refresh(forceRefresh = false)
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
    }

    fun refresh(forceRefresh: Boolean = true) {
        val requestGeneration = refreshGeneration.incrementAndGet()
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = navidromeRepository.fetchArtistDetail(artistId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    if (refreshGeneration.get() != requestGeneration) return@launch
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false, errorMessage = null)
                    }
                }

                is AppResult.Error -> {
                    if (refreshGeneration.get() != requestGeneration) return@launch
                    mutableUiState.update {
                        it.copy(isLoading = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    private fun observeLibrarySyncs() {
        viewModelScope.launch {
            sessionPreferences.state
                .map { state -> state.lastLibrarySyncAtMs }
                .distinctUntilChanged()
                .collect { syncedAtMs ->
                    val shouldRefresh = hasObservedLibrarySyncAtMs &&
                        lastObservedLibrarySyncAtMs != syncedAtMs
                    hasObservedLibrarySyncAtMs = true
                    lastObservedLibrarySyncAtMs = syncedAtMs
                    if (shouldRefresh) {
                        refresh(forceRefresh = true)
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
    private val refreshGeneration = AtomicLong(0L)

    init {
        refresh(forceRefresh = false)
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
    }

    fun refresh(forceRefresh: Boolean = true) {
        val requestGeneration = refreshGeneration.incrementAndGet()
        viewModelScope.launch {
            when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    if (refreshGeneration.get() != requestGeneration) return@launch
                    mutableUiState.update {
                        it.copy(detail = result.value, isLoading = false, errorMessage = null)
                    }
                }

                is AppResult.Error -> {
                    if (refreshGeneration.get() != requestGeneration) return@launch
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
        observeNavidromeConnectionChanges(navidromeRepository) {
            refresh(forceRefresh = true)
        }
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
    val lyricsSources: List<SettingsServerOption> = emptyList(),
    val activeLyricsSourceId: String? = null,
    val lyricsCacheSizeBytes: Long = 0L,
    val playbackCacheSizeLimit: String = NavidromeCacheSizeOption.default,
    val playbackCacheUsageBytes: Long = 0L,
    val playbackCacheItemCount: Int = 0,
    val isClearingCache: Boolean = false,
    val navidromeSkipForwardSeconds: Int = 15,
    val navidromeSkipBackwardSeconds: Int = 15,
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
    val lastLibrarySyncAtMs: Long? = null,
    val isBusy: Boolean = false,
    val resyncProgress: NavidromeLibraryResyncProgress? = null,
    val serverScanProgress: NavidromeServerScanProgress? = null,
    val showServerScanProgressDialog: Boolean = false,
    val syncToastMessage: String? = null,
    val errorMessage: String? = null
)

data class NavidromeHomeMenuUiState(
    val servers: List<SettingsServerOption> = emptyList(),
    val activeServerId: String? = null,
    val isSwitchingServer: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class NavidromeHomeMenuViewModel @Inject constructor(
    private val navidromeRepository: NavidromeRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NavidromeHomeMenuUiState())
    val uiState: StateFlow<NavidromeHomeMenuUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                navidromeRepository.observeServers(),
                sessionPreferences.state
            ) { servers, preferences ->
                val activeServerId = preferences.activeNavidromeServerId ?: servers.firstOrNull()?.id
                NavidromeHomeMenuUiState(
                    servers = servers.map { server ->
                        SettingsServerOption(
                            id = server.id,
                            name = server.name,
                            baseUrl = server.baseUrl,
                            host = parseHost(server.baseUrl)
                        )
                    },
                    activeServerId = activeServerId,
                    isSwitchingServer = mutableUiState.value.isSwitchingServer,
                    errorMessage = mutableUiState.value.errorMessage
                )
            }.collect { state ->
                mutableUiState.update {
                    it.copy(
                        servers = state.servers,
                        activeServerId = state.activeServerId
                    )
                }
            }
        }
    }

    fun onServerSelected(serverId: String) {
        if (mutableUiState.value.isSwitchingServer || serverId == mutableUiState.value.activeServerId) return
        mutableUiState.update { it.copy(isSwitchingServer = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = navidromeRepository.setActiveServer(serverId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(isSwitchingServer = false, errorMessage = null) }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isSwitchingServer = false,
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
}

private fun NavidromeServerScanStatus.toProgress(detail: String): NavidromeServerScanProgress {
    return NavidromeServerScanProgress(
        title = if (scanning) "Server scan in progress" else "Server scan finished",
        detail = detail,
        status = this,
        isRunning = scanning
    )
}

private fun buildServerScanDetail(status: NavidromeServerScanStatus): String {
    if (!status.scanning) {
        return "Navidrome is not currently scanning."
    }
    val progressBits = buildList {
        status.scannedCount?.let { add("$it scanned") }
        status.folderCount?.let { add("$it folders") }
    }
    return if (progressBits.isEmpty()) {
        "Navidrome is scanning the server library."
    } else {
        "Navidrome is scanning the server library. ${progressBits.joinToString(" • ")}"
    }
}

private fun resolveNavidromeEqualizerEditorState(
    state: NavidromeEqualizerUiState,
    profiles: List<NavidromeEqualizerProfile>,
    activeProfileId: String?
): NavidromeEqualizerEditorResolution {
    if (!state.isEditorPersisted) {
        val savedDraft = profiles.firstOrNull { it.id == state.editorProfile.id }
        if (savedDraft != null) {
            return NavidromeEqualizerEditorResolution(
                profile = savedDraft,
                isPersisted = true,
                isDirty = false
            )
        }
        return NavidromeEqualizerEditorResolution(
            profile = state.editorProfile,
            isPersisted = false,
            isDirty = state.isEditorDirty
        )
    }

    if (state.isEditorDirty) {
        return NavidromeEqualizerEditorResolution(
            profile = state.editorProfile,
            isPersisted = true,
            isDirty = true
        )
    }

    val persistedEditor = profiles.firstOrNull { it.id == state.editorProfile.id }
    if (persistedEditor != null) {
        return NavidromeEqualizerEditorResolution(
            profile = persistedEditor,
            isPersisted = true,
            isDirty = false
        )
    }

    val fallbackProfile = profiles.firstOrNull { it.id == activeProfileId }
        ?: profiles.firstOrNull()
        ?: newNavidromeEqualizerDraft(profiles)
    val fallbackPersisted = profiles.any { it.id == fallbackProfile.id }
    return NavidromeEqualizerEditorResolution(
        profile = fallbackProfile,
        isPersisted = fallbackPersisted,
        isDirty = false
    )
}

private fun normalizeNavidromeEqualizerLevel(levelDb: Float): Float {
    val safeLevelDb = levelDb.takeIf { it.isFinite() } ?: 0f
    return ((safeLevelDb.coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)) / NAVIDROME_EQUALIZER_STEP_DB)
        .roundToInt()
        .toFloat() * NAVIDROME_EQUALIZER_STEP_DB
}

private fun newNavidromeEqualizerDraft(profiles: List<NavidromeEqualizerProfile>): NavidromeEqualizerProfile {
    val defaultBaseName = "My new Equalizer"
    val existingNames = profiles.map { it.name.trim() }.toSet()
    val uniqueName = generateSequence(1) { it + 1 }
        .map { index ->
            if (index == 1) defaultBaseName else "$defaultBaseName $index"
        }
        .first { candidate -> candidate !in existingNames }
    return NavidromeEqualizerProfile(
        id = "nav_eq_${System.currentTimeMillis()}_${profiles.size + 1}",
        name = uniqueName,
        bandLevelsDb = flatNavidromeEqualizerBandLevels()
    )
}

private const val SERVER_SCAN_POLL_DELAY_MS = 1_500L
private const val SERVER_SCAN_MAX_POLLS = 120

private fun parseHost(baseUrl: String): String {
    return runCatching {
        URI(baseUrl).host?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: baseUrl
}

internal fun computeFullyDownloadedAlbumCountByArtistId(
    completedItems: List<NavidromeDownloadItem>
): Map<String, Int> {
    return completedItems
        .filter { it.albumId != null && it.albumSongCount != null && it.artistId != null }
        .groupBy { it.albumId!! }
        .mapNotNull { (_, albumItems) ->
            val albumSongCount = albumItems.firstOrNull()?.albumSongCount ?: return@mapNotNull null
            if (albumItems.map { it.trackId }.distinct().size >= albumSongCount) {
                albumItems.firstOrNull()?.artistId
            } else {
                null
            }
        }
        .groupingBy { it }
        .eachCount()
}

internal fun buildNavidromeHomeDownloadEntries(
    completedItems: List<NavidromeDownloadItem>
): List<NavidromeHomeDownloadEntry> {
    val uniqueCompletedItems = completedItems
        .filter { it.status == NavidromeDownloadStatus.Completed }
        .distinctBy { it.trackId }
        .sortedByDescending { it.updatedAtMs }
    if (uniqueCompletedItems.isEmpty()) return emptyList()

    val fullyDownloadedAlbumIds = uniqueCompletedItems
        .filter { it.albumId != null && it.albumSongCount != null }
        .groupBy { it.albumId!! }
        .mapNotNull { (albumId, albumItems) ->
            val expectedSongCount = albumItems.firstNotNullOfOrNull { it.albumSongCount } ?: return@mapNotNull null
            if (expectedSongCount > 0 && albumItems.size >= expectedSongCount) {
                albumId
            } else {
                null
            }
        }
        .toSet()

    val albumEntries = uniqueCompletedItems
        .filter { it.albumId in fullyDownloadedAlbumIds }
        .groupBy { it.albumId!! }
        .values
        .mapNotNull { albumItems ->
            val first = albumItems.maxByOrNull { it.updatedAtMs } ?: return@mapNotNull null
            val albumId = first.albumId ?: return@mapNotNull null
            NavidromeHomeDownloadedAlbum(
                album = NavidromeAlbum(
                    id = albumId,
                    name = first.albumName.ifBlank { first.title },
                    artistName = first.artistName,
                    artistId = first.artistId,
                    year = null,
                    songCount = first.albumSongCount ?: albumItems.size,
                    durationSeconds = albumItems.mapNotNull { it.durationSeconds }.takeIf { it.isNotEmpty() }?.sum(),
                    coverUrl = first.coverUrl,
                    genre = null
                ),
                subtitle = buildString {
                    append(first.artistName.ifBlank { "Unknown artist" })
                    append(" • Downloaded album")
                }
            )
        }
        .sortedByDescending { entry ->
            uniqueCompletedItems
                .filter { it.albumId == entry.album.id }
                .maxOfOrNull { it.updatedAtMs }
                ?: 0L
        }

    val trackEntries = uniqueCompletedItems
        .filterNot { it.albumId in fullyDownloadedAlbumIds }
        .map { item ->
            NavidromeHomeDownloadedTrack(
                track = NavidromeTrack(
                    id = item.trackId,
                    title = item.title,
                    artistName = item.artistName,
                    albumName = item.albumName,
                    albumId = item.albumId,
                    artistId = item.artistId,
                    trackNumber = null,
                    durationSeconds = item.durationSeconds,
                    coverUrl = item.coverUrl,
                    streamUrl = "",
                    formatLabel = item.formatLabel,
                    bitRateKbps = null
                ),
                subtitle = listOfNotNull(
                    item.artistName.takeIf { it.isNotBlank() },
                    item.albumName.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifBlank { "Downloaded song" }
            )
        }

    return (albumEntries + trackEntries)
        .sortedByDescending { entry ->
            when (entry) {
                is NavidromeHomeDownloadedAlbum -> {
                    uniqueCompletedItems
                        .filter { it.albumId == entry.album.id }
                        .maxOfOrNull { it.updatedAtMs }
                        ?: 0L
                }

                is NavidromeHomeDownloadedTrack -> {
                    uniqueCompletedItems
                        .firstOrNull { it.trackId == entry.track.id }
                        ?.updatedAtMs
                        ?: 0L
                }
            }
        }
}

private fun ViewModel.observeNavidromeConnectionChanges(
    navidromeRepository: NavidromeRepository,
    onConnectionChanged: () -> Unit
) {
    viewModelScope.launch {
        var hasObservedStatus = false
        var lastStatus: ActiveServerConnectionStatus? = null
        navidromeRepository.observeActiveConnectionStatus().collect { status ->
            if (!hasObservedStatus) {
                hasObservedStatus = true
                lastStatus = status
                return@collect
            }
            if (status?.serverId != lastStatus?.serverId ||
                status?.effectiveBaseUrl != lastStatus?.effectiveBaseUrl
            ) {
                lastStatus = status
                onConnectionChanged()
            } else {
                lastStatus = status
            }
        }
    }
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

private data class Sextuple<A, B, C, D, E, F>(
    val session: A,
    val servers: B,
    val preferences: C,
    val connectionStatus: D,
    val endpointHealth: E,
    val sixth: F
)

private data class Septuple<A, B, C, D, E, F, G>(
    val session: A,
    val servers: B,
    val preferences: C,
    val connectionStatus: D,
    val endpointHealth: E,
    val sixth: F,
    val seventh: G
)

private data class NavidromeSettingsLocalState(
    val libraries: List<NavidromeLibrary> = emptyList(),
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
    val isClearingCache: Boolean = false,
    val syncToastMessage: String? = null,
    val resyncProgress: NavidromeLibraryResyncProgress? = null,
    val serverScanProgress: NavidromeServerScanProgress? = null,
    val showServerScanProgressDialog: Boolean = false
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
