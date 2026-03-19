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
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.ui.screens.ToggleSectionItem
import com.stillshelf.app.core.datastore.SessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NavidromeLoginUiState(
    val baseUrl: String = "",
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
    val radios: List<NavidromeRadio> = emptyList(),
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

data class NavidromeSettingsUiState(
    val session: com.stillshelf.app.core.model.NavidromeSession? = null
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
