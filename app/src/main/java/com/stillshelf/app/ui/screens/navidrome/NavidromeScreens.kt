package com.stillshelf.app.ui.screens.navidrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.navigation.NavidromeRoute
import kotlin.math.min
import androidx.compose.material3.rememberModalBottomSheetState

private enum class NavidromeShellTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Home(NavidromeRoute.HOME, "Home", Icons.Outlined.Home),
    Library(NavidromeRoute.LIBRARY, "Library", Icons.Outlined.LibraryMusic);

    companion object {
        fun fromRoute(route: String?): NavidromeShellTab? {
            return when (route) {
                NavidromeRoute.HOME -> Home
                NavidromeRoute.LIBRARY,
                NavidromeRoute.ARTISTS,
                NavidromeRoute.ALBUMS,
                NavidromeRoute.PLAYLISTS,
                NavidromeRoute.SEARCH,
                NavidromeRoute.SETTINGS,
                NavidromeRoute.ARTIST_PATTERN,
                NavidromeRoute.ALBUM_PATTERN -> Library
                else -> null
            }
        }
    }
}

private data class NavidromeLibraryDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

private enum class NavidromeAlbumsDisplayStyle(
    val label: String
) {
    GRID("Grid"),
    LIST("List")
}

@Composable
fun NavidromeLoginRoute(
    onSwitchMode: () -> Unit,
    viewModel: NavidromeLoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileGlyph()
                EditButton(
                    label = "Modes",
                    onClick = onSwitchMode
                )
            }
            Text(
                text = "Connect Navidrome",
                style = MaterialTheme.typography.displaySmall
            )
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.baseUrl,
                        onValueChange = viewModel::onBaseUrlChange,
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = viewModel::submit,
                        enabled = uiState.canSubmit && !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Connect")
                        }
                    }
                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NavidromeAppRoute(
    onSwitchMode: () -> Unit
) {
    val navController = rememberNavController()
    val playerViewModel: NavidromePlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val shellTab = NavidromeShellTab.fromRoute(currentRoute)
    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }

    if (showPlayerSheet && playerState.currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlayerSheet = false },
            sheetState = playerSheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            NavidromeExpandedPlayerSheet(
                state = playerState,
                onDismiss = { showPlayerSheet = false },
                onPrevious = playerViewModel::playPrevious,
                onPlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::playNext
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (shellTab != null) {
                NavidromeBottomChrome(
                    currentTab = shellTab,
                    playerState = playerState,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(NavidromeRoute.HOME)
                            launchSingleTop = true
                        }
                    },
                    onSearch = {
                        navController.navigate(NavidromeRoute.SEARCH) {
                            launchSingleTop = true
                        }
                    },
                    onPrevious = playerViewModel::playPrevious,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::playNext,
                    onOpenPlayer = {
                        if (playerState.currentTrack != null) {
                            showPlayerSheet = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavidromeRoute.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(NavidromeRoute.HOME) {
                NavidromeHomeRoute(
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) }
                )
            }
            composable(NavidromeRoute.LIBRARY) {
                NavidromeLibraryRoute(
                    onOpenArtists = { navController.navigate(NavidromeRoute.ARTISTS) },
                    onOpenAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenNewestAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenSongs = { navController.navigate(NavidromeRoute.SEARCH) },
                    onOpenPlaylists = { navController.navigate(NavidromeRoute.PLAYLISTS) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) }
                )
            }
            composable(NavidromeRoute.SEARCH) {
                NavidromeSearchRoute(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.SETTINGS) {
                NavidromeSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onSwitchMode = onSwitchMode
                )
            }
            composable(NavidromeRoute.ARTISTS) {
                NavidromeArtistsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.ALBUMS) {
                NavidromeAlbumsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(NavidromeRoute.PLAYLISTS) {
                NavidromePlaylistsRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = NavidromeRoute.ARTIST_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ARTIST_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeArtistDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(
                route = NavidromeRoute.ALBUM_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ALBUM_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeAlbumDetailRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun NavidromeHomeRoute(
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: NavidromeHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NavidromeHomeScreen(
        uiState = uiState,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenSettings = onOpenSettings
    )
}

@Composable
private fun NavidromeHomeScreen(
    uiState: NavidromeHomeUiState,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var randomAlbumsVersion by rememberSaveable { mutableIntStateOf(0) }
    val randomAlbums = remember(randomAlbumsVersion, uiState.recentAlbums) {
        uiState.recentAlbums.shuffled().take(min(12, uiState.recentAlbums.size))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TopLevelHeader(
                title = "Home",
                onProfileClick = onOpenSettings,
                onEditClick = onOpenSettings
            )
        }
        item {
            AlbumShelf(
                title = "Random Albums",
                albums = randomAlbums,
                onOpenAlbum = onOpenAlbum,
                actionIcon = Icons.Outlined.Refresh,
                onActionClick = { randomAlbumsVersion += 1 }
            )
        }
        item {
            AlbumShelf(
                title = "Recently Played Albums",
                albums = uiState.recentAlbums,
                onOpenAlbum = onOpenAlbum
            )
        }
        if (uiState.playlists.isNotEmpty()) {
            item {
                PlaylistShelf(
                    title = "Recently Played Playlists",
                    playlists = uiState.playlists
                )
            }
        }
        item {
            AlbumShelf(
                title = "Newest Albums",
                albums = uiState.recentAlbums,
                onOpenAlbum = onOpenAlbum
            )
        }
        if (uiState.artists.isNotEmpty()) {
            item {
                ArtistShelf(
                    title = "Artists",
                    artists = uiState.artists.take(8),
                    onOpenArtist = onOpenArtist
                )
            }
        }
        if (uiState.errorMessage != null) {
            item {
                ErrorCard(uiState.errorMessage)
            }
        }
    }
}

@Composable
private fun NavidromeLibraryRoute(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenNewestAlbums: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items = remember {
        listOf(
            NavidromeLibraryDestination("Artists", Icons.Outlined.Person, onOpenArtists),
            NavidromeLibraryDestination("Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Newest Albums", Icons.Outlined.Album, onOpenNewestAlbums),
            NavidromeLibraryDestination("Recently Played Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Songs", Icons.Outlined.MusicNote, onOpenSongs),
            NavidromeLibraryDestination("Favorite Songs", Icons.Outlined.Favorite, onOpenSongs),
            NavidromeLibraryDestination("Playlists", Icons.Outlined.QueueMusic, onOpenPlaylists)
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        item {
            TopLevelHeader(
                title = "Library",
                onProfileClick = onOpenSettings,
                onEditClick = onOpenSettings
            )
        }
        item {
            Card(
                modifier = Modifier.padding(top = 10.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    items.forEachIndexed { index, destination ->
                        LibraryMenuRow(
                            label = destination.label,
                            icon = destination.icon,
                            onClick = destination.onClick
                        )
                        if (index != items.lastIndex) {
                            DividerLine()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeArtistsRoute(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Artists", onBack = onBack) {
        items(uiState.artists) { artist ->
            ArtistRow(artist = artist, onClick = { onOpenArtist(artist.id) })
        }
    }
}

@Composable
private fun NavidromeAlbumsRoute(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var displayStyle by rememberSaveable { mutableStateOf(NavidromeAlbumsDisplayStyle.GRID) }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Albums",
            onBack = onBack,
            actions = {
                Box {
                    RoundIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = "Album options",
                        onClick = { menuExpanded = true }
                    )
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        AppDropdownMenuItem(
                            text = { Text("Grid view") },
                            leadingIcon = {
                                if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                displayStyle = NavidromeAlbumsDisplayStyle.GRID
                                menuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("List view") },
                            leadingIcon = {
                                if (displayStyle == NavidromeAlbumsDisplayStyle.LIST) {
                                    Icon(Icons.Outlined.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                displayStyle = NavidromeAlbumsDisplayStyle.LIST
                                menuExpanded = false
                            }
                        )
                        DividerLine()
                        NavidromeAlbumSortOption.entries.forEach { sort ->
                            AppDropdownMenuItem(
                                text = { Text("Sort: ${sort.label}") },
                                leadingIcon = {
                                    if (uiState.albumSort == sort) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setAlbumSort(sort)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        )
        if (uiState.isLoading) {
            LoadingCard()
        } else if (!uiState.errorMessage.isNullOrBlank()) {
            ErrorCard(uiState.errorMessage ?: "Unable to load albums.")
        } else if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(uiState.albums.size) { index ->
                    val album = uiState.albums[index]
                    AlbumGridCard(
                        album = album,
                        onClick = { onOpenAlbum(album.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(uiState.albums) { album ->
                    AlbumRow(album = album, onClick = { onOpenAlbum(album.id) })
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun NavidromePlaylistsRoute(
    onBack: () -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Playlists", onBack = onBack) {
        items(uiState.playlists) { playlist ->
            PlaylistRow(playlist)
        }
    }
}

@Composable
private fun NavidromeSearchRoute(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    StandardTopScreen(
        title = "Search",
        onBack = onBack,
        topContent = {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Artists, albums, songs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                })
            )
        }
    ) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        }
        if (uiState.results.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            items(uiState.results.artists) { artist ->
                ArtistRow(artist = artist, onClick = { onOpenArtist(artist.id) })
            }
        }
        if (uiState.results.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            items(uiState.results.albums) { album ->
                AlbumRow(album = album, onClick = { onOpenAlbum(album.id) })
            }
        }
        if (uiState.results.tracks.isNotEmpty()) {
            item { SectionTitle("Songs") }
            items(uiState.results.tracks) { track ->
                TrackRow(track = track, onClick = {})
            }
        }
        if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Search failed.") }
        }
    }
}

@Composable
private fun NavidromeArtistDetailRoute(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Artist", onBack = onBack) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            item {
                CenteredDetailHero(
                    title = detail.artist.name,
                    subtitle = "${detail.artist.albumCount} albums",
                    imageUrl = detail.artist.imageUrl ?: detail.artist.coverUrl,
                    circular = true
                )
            }
            item { SectionTitle("Albums") }
            items(detail.albums) { album ->
                AlbumRow(album = album, onClick = { onOpenAlbum(album.id) })
            }
        } else {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load artist.") }
        }
    }
}

@Composable
private fun NavidromeAlbumDetailRoute(
    onBack: () -> Unit,
    viewModel: NavidromeAlbumDetailViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Album", onBack = onBack) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            item {
                AlbumDetailHero(
                    detail = detail,
                    onPlayAlbum = { playerViewModel.playTracks(detail.tracks, 0) },
                    onShuffleAlbum = {
                        val shuffledTracks = detail.tracks.shuffled()
                        playerViewModel.playTracks(shuffledTracks, 0)
                    }
                )
            }
            items(detail.tracks.size) { index ->
                val track = detail.tracks[index]
                TrackRow(
                    track = track,
                    onClick = { playerViewModel.playTracks(detail.tracks, index) }
                )
            }
        } else {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load album.") }
        }
    }
}

@Composable
private fun NavidromeSettingsRoute(
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    viewModel: NavidromeSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(
        title = "Settings",
        onBack = onBack,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        item {
            GroupedSettingsCard {
                SettingsValueRow(
                    title = "Account",
                    value = uiState.session?.username ?: "Navidrome"
                )
                DividerLine()
                SettingsValueRow(
                    title = "Server",
                    value = uiState.session?.baseUrl ?: ""
                )
            }
        }
        item {
            GroupedSettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Switch product mode",
                    subtitle = "Return to the backend chooser.",
                    onClick = onSwitchMode
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "Sign out of Navidrome",
                    subtitle = "Clear this music session on the device.",
                    onClick = viewModel::signOut
                )
            }
        }
    }
}

@Composable
private fun StandardTopScreen(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(modifier = Modifier.statusBarsPadding()) {
                DetailHeader(title = title, onBack = onBack, actions = actions)
            }
        }
        if (topContent != null) {
            item { topContent() }
        }
        content()
    }
}

@Composable
private fun TopLevelHeader(
    title: String,
    onProfileClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF0F1))
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Account",
                    tint = Color(0xFFFF334B)
                )
            }
            EditButton(label = "Edit", onClick = onEditClick)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall
        )
    }
}

@Composable
private fun EditButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<NavidromeAlbum>,
    onOpenAlbum: (String) -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            actionIcon = actionIcon,
            onActionClick = onActionClick
        )
        if (albums.isEmpty()) {
            EmptyCard("No albums found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(albums) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistShelf(
    title: String,
    artists: List<NavidromeArtist>,
    onOpenArtist: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        if (artists.isEmpty()) {
            EmptyCard("No artists found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(artists) { artist ->
                    Column(
                        modifier = Modifier
                            .width(92.dp)
                            .clickable(onClick = { onOpenArtist(artist.id) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArtistArt(
                            url = artist.imageUrl ?: artist.coverUrl,
                            size = 84.dp
                        )
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    title: String,
    playlists: List<NavidromePlaylist>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        if (playlists.isEmpty()) {
            EmptyCard("No playlists found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier.width(168.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QueueMusic,
                                    contentDescription = null
                                )
                            }
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playlist.songCount?.let { "$it tracks" } ?: "Playlist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (actionIcon != null && onActionClick != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = title
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    SectionHeader(title = title)
}

@Composable
private fun AlbumGridCard(
    album: NavidromeAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArt(url = album.coverUrl, size = 168.dp)
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumCard(
    album: NavidromeAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(122.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArt(url = album.coverUrl, size = 122.dp)
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryMenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF334B)
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumRow(
    album: NavidromeAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = album.coverUrl, size = 58.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = listOfNotNull(album.artistName, album.year?.toString()).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DividerLine()
    }
}

@Composable
private fun ArtistRow(
    artist: NavidromeArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistArt(url = artist.imageUrl ?: artist.coverUrl, size = 54.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${artist.albumCount} albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DividerLine()
    }
}

@Composable
private fun PlaylistRow(playlist: NavidromePlaylist) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = playlist.songCount?.let { "$it tracks" } ?: "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun TrackRow(
    track: NavidromeTrack,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track.trackNumber?.toString() ?: "•",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${track.artistName} • ${track.albumName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null
            )
        }
        DividerLine()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupedSettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CenteredDetailHero(
    title: String,
    subtitle: String,
    imageUrl: String?,
    circular: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (circular) {
            ArtistArt(url = imageUrl, size = 118.dp)
        } else {
            AlbumArt(url = imageUrl, size = 160.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AlbumDetailHero(
    detail: NavidromeAlbumDetail,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AlbumArt(url = detail.album.coverUrl, size = 188.dp)
        Text(
            text = detail.album.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = detail.album.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = listOfNotNull(
                detail.album.year?.toString(),
                "${detail.album.songCount} tracks",
                detail.album.genre
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PlayArrow,
                label = "Play",
                onClick = onPlayAlbum
            )
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Shuffle,
                label = "Shuffle",
                onClick = onShuffleAlbum
            )
        }
    }
}

@Composable
private fun PillActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFF334B)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF334B)
            )
        }
    }
}

@Composable
private fun NavidromeBottomChrome(
    currentTab: NavidromeShellTab,
    playerState: NavidromePlayerState,
    onTabSelected: (NavidromeShellTab) -> Unit,
    onSearch: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (playerState.currentTrack != null) {
            NavidromeMiniPlayerBar(
                state = playerState,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onOpenPlayer = onOpenPlayer
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NavidromeTabButton(
                        tab = NavidromeShellTab.Home,
                        selected = currentTab == NavidromeShellTab.Home,
                        onClick = { onTabSelected(NavidromeShellTab.Home) },
                        modifier = Modifier.weight(1f)
                    )
                    NavidromeTabButton(
                        tab = NavidromeShellTab.Library,
                        selected = currentTab == NavidromeShellTab.Library,
                        onClick = { onTabSelected(NavidromeShellTab.Library) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(58.dp)
                    .clickable(onClick = onSearch)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search"
                    )
                }
            }
        }
    }
}

@Composable
private fun NavidromeTabButton(
    tab: NavidromeShellTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = Color(0xFFE9E8E8)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) selectedColor else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Color(0xFFFF334B) else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 10.sp),
                color = if (selected) Color(0xFFFF334B) else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NavidromeMiniPlayerBar(
    state: NavidromePlayerState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val track = state.currentTrack ?: return
    Surface(
        modifier = Modifier
            .clickable(onClick = onOpenPlayer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = track.coverUrl, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@Composable
private fun NavidromeExpandedPlayerSheet(
    state: NavidromePlayerState,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val track = state.currentTrack ?: return
    val queuedTracks = remember(state.queue, state.currentIndex) {
        if (state.currentIndex in state.queue.indices) {
            state.queue.drop(state.currentIndex).take(6)
        } else {
            listOf(track)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Close player",
                    onClick = onDismiss
                )
                Text(
                    text = "Now Playing",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    AlbumArt(url = track.coverUrl, size = 284.dp)
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = track.albumName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlayerStatPill(
                            label = if (state.queue.isEmpty()) "Streaming" else "Queue",
                            value = if (state.queue.isEmpty()) "Navidrome" else "${state.queue.size} tracks"
                        )
                        PlayerStatPill(
                            label = "Length",
                            value = track.durationSeconds?.let(::formatDuration) ?: "Live"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(
                                    if (state.queue.isEmpty()) 0.2f
                                    else ((state.currentIndex + 1).coerceAtLeast(1).toFloat() / state.queue.size.coerceAtLeast(1))
                                        .coerceIn(0f, 1f)
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFFF5A5F))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0:00",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = track.durationSeconds?.let(::formatDuration) ?: "--:--",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    queuedTracks.forEachIndexed { index, queuedTrack ->
                        PlayerQueueRow(
                            track = queuedTrack,
                            isCurrent = index == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStatPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlayerQueueRow(
    track: NavidromeTrack,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(url = track.coverUrl, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isCurrent) "Playing" else track.durationSeconds?.let(::formatDuration) ?: "--:--",
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) Color(0xFFFF5A5F) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun ProfileGlyph() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFF0F1)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = Color(0xFFFF334B)
        )
    }
}

@Composable
private fun AlbumArt(
    url: String?,
    size: androidx.compose.ui.unit.Dp
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Album, contentDescription = null)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ArtistArt(
    url: String?,
    size: androidx.compose.ui.unit.Dp
) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    )
}
