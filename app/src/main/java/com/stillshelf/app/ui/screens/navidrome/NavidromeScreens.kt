package com.stillshelf.app.ui.screens.navidrome

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeOutputDevice
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.ui.screens.AppAppearanceViewModel
import com.stillshelf.app.ui.screens.AppScreenHorizontalPadding
import com.stillshelf.app.ui.screens.ToggleSectionItem
import com.stillshelf.app.ui.theme.AppThemeMode
import com.stillshelf.app.ui.theme.LocalMaterialDesignEnabled
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavidromeLibraryDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

private data class NavidromeHomeDestination(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

enum class NavidromeAlbumsDisplayStyle(
    val label: String
) {
    GRID("Grid"),
    LIST("List")
}

private val NavidromeHomeTopBarLibrarySelectorMinWidth = 184.dp
private val NavidromeHomeTopBarLibrarySelectorPreferredWidth = 236.dp
private val NavidromeOverlayBottomContentPadding = 120.dp

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
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val playerViewModel: NavidromePlayerViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val showMiniPlayer = playerState.currentTrack != null
    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val systemInsets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    val safeBottomInset = with(density) { (systemInsets?.bottom ?: 0).toDp() }

    fun navigateHome() {
        navController.navigate(NavidromeRoute.HOME) {
            popUpTo(NavidromeRoute.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    val topHomeAction: (() -> Unit)? = if (showMiniPlayer) null else { { navigateHome() } }

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
                onNext = playerViewModel::playNext,
                onSelectTrack = playerViewModel::playQueueIndex,
                onSeekTo = playerViewModel::seekTo,
                onRefreshAudioOutputs = playerViewModel::refreshAudioOutputs,
                onSelectAudioOutput = playerViewModel::selectAudioOutputDevice,
                isFavorite = playerState.currentTrack?.id in favoriteTrackIds,
                onToggleFavorite = playerViewModel::toggleFavoriteTrack,
                onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = NavidromeRoute.HOME,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 260)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 260)
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(durationMillis = 260)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 260)
                )
            }
        ) {
            composable(NavidromeRoute.HOME) {
                NavidromeHomeRoute(
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) },
                    onOpenArtists = { navController.navigate(NavidromeRoute.ARTISTS) },
                    onOpenAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenRadios = { navController.navigate(NavidromeRoute.RADIOS) },
                    onOpenSongs = { navController.navigate(NavidromeRoute.SONGS) },
                    onOpenFavorites = { navController.navigate(NavidromeRoute.FAVORITES) },
                    onOpenPlaylists = { navController.navigate(NavidromeRoute.PLAYLISTS) },
                    onOpenSearch = { navController.navigate(NavidromeRoute.SEARCH) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) },
                    onOpenCustomize = { navController.navigate(NavidromeRoute.CUSTOMIZE) },
                    onSwitchMode = onSwitchMode,
                    playerState = playerState,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onOpenPlayer = {
                        if (playerState.currentTrack != null) {
                            showPlayerSheet = true
                        }
                    }
                )
            }
            composable(NavidromeRoute.LIBRARY) {
                NavidromeLibraryRoute(
                    onOpenArtists = { navController.navigate(NavidromeRoute.ARTISTS) },
                    onOpenAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenRadios = { navController.navigate(NavidromeRoute.RADIOS) },
                    onOpenNewestAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenSongs = { navController.navigate(NavidromeRoute.SONGS) },
                    onOpenFavoriteSongs = { navController.navigate(NavidromeRoute.FAVORITES) },
                    onOpenPlaylists = { navController.navigate(NavidromeRoute.PLAYLISTS) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) }
                )
            }
            composable(NavidromeRoute.RADIOS) {
                NavidromeRadiosRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.SONGS) {
                NavidromeSongsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.FAVORITES) {
                NavidromeFavoriteSongsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.SEARCH) {
                NavidromeSearchRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.SETTINGS) {
                NavidromeSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onSwitchMode = onSwitchMode
                )
            }
            composable(NavidromeRoute.CUSTOMIZE) {
                NavidromeCustomizeRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.ARTISTS) {
                NavidromeArtistsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.ALBUMS) {
                NavidromeAlbumsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(NavidromeRoute.PLAYLISTS) {
                NavidromePlaylistsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(
                route = NavidromeRoute.ARTIST_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ARTIST_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeArtistDetailRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(
                route = NavidromeRoute.ALBUM_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ALBUM_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeAlbumDetailRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
        }
        if (safeBottomInset > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(safeBottomInset)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        AnimatedVisibility(
            visible = showMiniPlayer,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 260)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 260)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = safeBottomInset + 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                NavidromeMiniPlayerBar(
                    state = playerState,
                    onPrevious = playerViewModel::playPrevious,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::playNext,
                    onOpenPlayer = {
                        if (playerState.currentTrack != null) {
                            showPlayerSheet = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (currentRoute != NavidromeRoute.HOME) {
                    val homeBubbleShape = RoundedCornerShape(24.dp)
                    val homeBubbleBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(54.dp)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(homeBubbleShape)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = homeBubbleShape
                                )
                                .border(width = 1.5.dp, color = homeBubbleBorderColor, shape = homeBubbleShape)
                                .clickable(onClick = ::navigateHome),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Home,
                                contentDescription = "Home",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeHomeRoute(
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onSwitchMode: () -> Unit,
    playerState: NavidromePlayerState,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: NavidromeHomeViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    customizeViewModel: NavidromeCustomizeViewModel = hiltViewModel(),
    settingsViewModel: NavidromeSettingsViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customizeUiState by customizeViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    NavidromeHomeScreen(
        uiState = uiState,
        customizeUiState = customizeUiState,
        libraryTitle = settingsUiState.session?.username?.takeIf { it.isNotBlank() }?.let { "$it Music" }
            ?: "Navidrome Music",
        serverLabel = settingsUiState.session?.baseUrl
            ?.let(::formatNavidromeServerLabel)
            ?: "Navidrome",
        playerState = playerState,
        materialDesignEnabled = appearanceUiState.navidromeMaterialDesignEnabled,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenArtists = onOpenArtists,
        onOpenAlbums = onOpenAlbums,
        onOpenRadios = onOpenRadios,
        onOpenSongs = onOpenSongs,
        onOpenFavorites = onOpenFavorites,
        onOpenPlaylists = onOpenPlaylists,
        onOpenSearch = onOpenSearch,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onOpenCustomize = onOpenCustomize,
        onSwitchMode = onSwitchMode,
        onPlayPause = onPlayPause,
        onPlayTrack = playerViewModel::playTrack,
        onPlayAlbum = playerViewModel::playAlbum,
        onOpenPlayer = onOpenPlayer
    )
}

@Composable
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
private fun NavidromeHomeScreen(
    uiState: NavidromeHomeUiState,
    customizeUiState: NavidromeCustomizeUiState,
    libraryTitle: String,
    serverLabel: String,
    playerState: NavidromePlayerState,
    materialDesignEnabled: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onSwitchMode: () -> Unit,
    onPlayPause: () -> Unit,
    onPlayTrack: (NavidromeTrack) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val homeStartInset = AppScreenHorizontalPadding
    val homeEndInset = AppScreenHorizontalPadding
    val homeInsetTotal = homeStartInset + homeEndInset
    val homeFullBleedModifier = remember(homeStartInset, homeEndInset) {
        Modifier
            .fillMaxWidth()
            .padding(start = homeStartInset, end = homeEndInset)
    }
    val homeCarouselModifier = remember { Modifier.fillMaxWidth() }
    val homeCarouselContentPadding = remember(homeStartInset) {
        PaddingValues(start = homeStartInset, end = 0.dp)
    }
    val homeShelfPosterWidth = StandardGridCoverWidth
    val homeShelfPosterHeight = StandardGridCoverHeight
    val configuration = LocalConfiguration.current
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = onRefresh
    )
    var isLibraryMenuExpanded by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var randomAlbumsVersion by rememberSaveable { mutableIntStateOf(0) }
    val randomAlbums = remember(randomAlbumsVersion, uiState.recentAlbums) {
        uiState.recentAlbums.shuffled().take(min(12, uiState.recentAlbums.size))
    }
    val listItemById = remember(
        onOpenAlbums,
        onOpenArtists,
        onOpenRadios,
        onOpenSongs,
        onOpenFavorites,
        onOpenPlaylists
    ) {
        mapOf(
            NavidromeListSectionIds.ARTISTS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.ARTISTS,
                label = "Artists",
                icon = Icons.Outlined.PersonOutline,
                onClick = onOpenArtists
            ),
            NavidromeListSectionIds.ALBUMS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.ALBUMS,
                label = "Albums",
                icon = Icons.Outlined.Album,
                onClick = onOpenAlbums
            ),
            NavidromeListSectionIds.RADIOS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.RADIOS,
                label = "Radios",
                icon = Icons.Outlined.GraphicEq,
                onClick = onOpenRadios
            ),
            NavidromeListSectionIds.NEWEST_ALBUMS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.NEWEST_ALBUMS,
                label = "Newest Albums",
                icon = Icons.Outlined.Album,
                onClick = onOpenAlbums
            ),
            NavidromeListSectionIds.SONGS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.SONGS,
                label = "Songs",
                icon = Icons.Outlined.MusicNote,
                onClick = onOpenSongs
            ),
            NavidromeListSectionIds.FAVORITES to NavidromeHomeDestination(
                id = NavidromeListSectionIds.FAVORITES,
                label = "Favorite Songs",
                icon = Icons.Outlined.Favorite,
                onClick = onOpenFavorites
            ),
            NavidromeListSectionIds.PLAYLISTS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.PLAYLISTS,
                label = "Playlists",
                icon = Icons.Outlined.MusicNote,
                onClick = onOpenPlaylists
            )
        )
    }
    val orderedListItems = customizeUiState.listSections
        .mapNotNull { listItemById[it.id] }
        .filterNot { customizeUiState.hiddenListSectionIds.contains(it.id) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        val availableHomeContentWidth = remember(maxWidth, homeInsetTotal) {
            (maxWidth - homeInsetTotal).coerceAtLeast(0.dp)
        }
        val continueListeningPosterWidth = 72.dp
        val continueListeningPosterHeight = 80.dp
        val continueListeningCardWidth = remember(availableHomeContentWidth, configuration.fontScale) {
            val widthFactor = if (configuration.fontScale > 1.05f) 0.84f else 0.8f
            (availableHomeContentWidth * widthFactor).coerceIn(266.dp, 336.dp)
        }
        val continueListeningCardHeight = remember(configuration.fontScale) {
            (
                continueListeningPosterHeight +
                    12.dp +
                    ((configuration.fontScale - 1f).coerceAtLeast(0f) * 8f).dp
                ).coerceIn(96.dp, 124.dp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = homeStartInset, end = homeEndInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f)
                    ) {
                        val hasServerMenu = serverLabel.isNotBlank()
                        val libraryMenuWidth = NavidromeHomeTopBarLibrarySelectorPreferredWidth
                            .coerceAtMost(maxWidth)
                            .coerceAtLeast(NavidromeHomeTopBarLibrarySelectorMinWidth.coerceAtMost(maxWidth))
                        Row(
                            modifier = Modifier
                                .width(libraryMenuWidth)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = hasServerMenu) {
                                    isLibraryMenuExpanded = true
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = libraryTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasServerMenu) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isLibraryMenuExpanded) {
                                        Icons.Outlined.KeyboardArrowUp
                                    } else {
                                        Icons.Outlined.KeyboardArrowDown
                                    },
                                    contentDescription = "View music source",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        AppDropdownMenu(
                            expanded = isLibraryMenuExpanded && hasServerMenu,
                            onDismissRequest = { isLibraryMenuExpanded = false },
                            modifier = Modifier.width(libraryMenuWidth)
                        ) {
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        text = serverLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Dns,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Active music source"
                                    )
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                    CircleActionButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search",
                        onClick = onOpenSearch
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        CircleActionButton(
                            icon = Icons.Outlined.MoreHoriz,
                            contentDescription = "More",
                            onClick = { isMenuExpanded = true }
                        )
                        AppDropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            AppDropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenSettings()
                                }
                            )
                            AppDropdownMenuItem(
                                text = { Text("Customize") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenCustomize()
                                }
                            )
                            HorizontalDivider()
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Servers",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                enabled = false,
                                onClick = {}
                            )
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        text = serverLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Dns, contentDescription = null)
                                },
                                trailingIcon = {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Active server")
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = homeFullBleedModifier,
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (materialDesignEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
                            )
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (materialDesignEnabled) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        } else if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.Black.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        orderedListItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clickable(onClick = item.onClick),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(23.dp)
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < orderedListItems.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }
            customizeUiState.personalizedSections.forEach { section ->
                if (customizeUiState.hiddenPersonalizedSectionIds.contains(section.id)) {
                    return@forEach
                }
                when (section.id) {
                    NavidromeHomeSectionIds.CONTINUE -> {
                        item {
                            SectionTitle(
                                title = "Continue Listening",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            when {
                                playerState.recentTracks.isEmpty() -> {
                                    Text(
                                        text = "No music in progress yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                    )
                                }
                                else -> {
                                    LazyRow(
                                        modifier = homeCarouselModifier,
                                        contentPadding = homeCarouselContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(playerState.recentTracks.take(7), key = { it.id }) { track ->
                                            NavidromeContinueListeningCard(
                                                track = track,
                                                isCurrent = playerState.currentTrack?.id == track.id,
                                                isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                                                cardWidth = continueListeningCardWidth,
                                                cardHeight = continueListeningCardHeight,
                                                posterWidth = continueListeningPosterWidth,
                                                posterHeight = continueListeningPosterHeight,
                                                onPlayPause = onPlayPause,
                                                onPlayTrack = { onPlayTrack(track) },
                                                onClick = {
                                                    if (playerState.currentTrack?.id == track.id) {
                                                        onOpenPlayer()
                                                    } else {
                                                        onPlayTrack(track)
                                                    }
                                                },
                                                onOpenAlbum = track.albumId?.let { albumId ->
                                                    { onOpenAlbum(albumId) }
                                                },
                                                onOpenArtist = track.artistId?.let { artistId ->
                                                    { onOpenArtist(artistId) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.RECENTLY_ADDED -> {
                        item {
                            SectionTitle(
                                title = "Recently Added",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.recentAlbums, key = { it.id }) { album ->
                                    NavidromeHomeAlbumCard(
                                        album = album,
                                        posterWidth = homeShelfPosterWidth,
                                        posterHeight = homeShelfPosterHeight,
                                        onClick = { onOpenAlbum(album.id) },
                                        onPlayAlbum = { onPlayAlbum(album.id, false) },
                                        onShuffleAlbum = { onPlayAlbum(album.id, true) },
                                        onOpenAlbum = { onOpenAlbum(album.id) },
                                        onOpenArtist = { album.artistId?.let(onOpenArtist) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.DISCOVER -> {
                        item {
                            SectionTitle(
                                title = "Discover",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(randomAlbums, key = { it.id }) { album ->
                                    NavidromeHomeAlbumCard(
                                        album = album,
                                        posterWidth = homeShelfPosterWidth,
                                        posterHeight = homeShelfPosterHeight,
                                        onClick = { onOpenAlbum(album.id) },
                                        onPlayAlbum = { onPlayAlbum(album.id, false) },
                                        onShuffleAlbum = { onPlayAlbum(album.id, true) },
                                        onOpenAlbum = { onOpenAlbum(album.id) },
                                        onOpenArtist = { album.artistId?.let(onOpenArtist) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.ARTISTS -> if (uiState.artists.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Artists",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.artists.take(12), key = { it.id }) { artist ->
                                    NavidromeHomeArtistCard(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist.id) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.PLAYLISTS -> if (uiState.playlists.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = "Playlists",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.playlists.take(12), key = { it.id }) { playlist ->
                                    NavidromeHomePlaylistCard(playlist = playlist)
                                }
                            }
                        }
                    }
                }
            }
            if (uiState.errorMessage != null) {
                item {
                    Box(modifier = homeFullBleedModifier) {
                        ErrorCard(uiState.errorMessage)
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = uiState.isLoading,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun NavidromeCustomizeRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeCustomizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    var selectedTab by remember { mutableStateOf("Lists") }
    var pendingListRows by remember { mutableStateOf<List<ToggleSectionItem>?>(null) }
    var pendingPersonalizedRows by remember { mutableStateOf<List<ToggleSectionItem>?>(null) }
    var pendingHiddenListSectionIds by remember { mutableStateOf<Set<String>?>(null) }
    var pendingHiddenPersonalizedSectionIds by remember { mutableStateOf<Set<String>?>(null) }

    fun cancelAndExit() {
        pendingListRows = null
        pendingPersonalizedRows = null
        pendingHiddenListSectionIds = null
        pendingHiddenPersonalizedSectionIds = null
        onBack()
    }

    fun saveAndExit() {
        pendingListRows?.let { viewModel.setListOrder(it.map(ToggleSectionItem::id)) }
        pendingPersonalizedRows?.let { viewModel.setPersonalizedOrder(it.map(ToggleSectionItem::id)) }
        pendingHiddenListSectionIds?.let(viewModel::setHiddenListSectionIds)
        pendingHiddenPersonalizedSectionIds?.let(viewModel::setHiddenPersonalizedSectionIds)
        cancelAndExit()
    }

    BackHandler(onBack = ::cancelAndExit)

    val effectiveListRows = pendingListRows ?: uiState.listSections
    val effectivePersonalizedRows = pendingPersonalizedRows ?: uiState.personalizedSections
    val effectiveHiddenListSectionIds = pendingHiddenListSectionIds ?: uiState.hiddenListSectionIds
    val effectiveHiddenPersonalizedSectionIds =
        pendingHiddenPersonalizedSectionIds ?: uiState.hiddenPersonalizedSectionIds
    val orderedRows = if (selectedTab == "Lists") effectiveListRows else effectivePersonalizedRows

    fun moveRow(source: List<ToggleSectionItem>, from: Int, to: Int): List<ToggleSectionItem> {
        if (from !in source.indices || to !in source.indices || from == to) return source
        val mutable = source.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

    fun moveRowByDelta(rowId: String, delta: Int) {
        if (delta == 0) return
        val fromIndex = orderedRows.indexOfFirst { it.id == rowId }
        if (fromIndex < 0) return
        val toIndex = fromIndex + delta
        if (toIndex !in orderedRows.indices) return
        val updated = moveRow(orderedRows, fromIndex, toIndex)
        if (selectedTab == "Lists") {
            pendingListRows = updated
        } else {
            pendingPersonalizedRows = updated
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = ::cancelAndExit
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (onHome != null) {
                CircleActionButton(
                    icon = Icons.Outlined.Home,
                    contentDescription = "Home",
                    onClick = onHome
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = "Customize",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = Icons.Filled.Check,
                contentDescription = "Done",
                onClick = ::saveAndExit
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavidromeCustomizeTabChip(
                label = "Lists",
                selected = selectedTab == "Lists",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Lists" }
            )
            NavidromeCustomizeTabChip(
                label = "Personalized",
                selected = selectedTab == "Personalized",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Personalized" }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                itemsIndexed(orderedRows, key = { _, item -> item.id }) { index, row ->
                    val enabled = if (selectedTab == "Lists") {
                        !effectiveHiddenListSectionIds.contains(row.id)
                    } else {
                        !effectiveHiddenPersonalizedSectionIds.contains(row.id)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable {
                                if (selectedTab == "Lists") {
                                    pendingHiddenListSectionIds =
                                        toggleHiddenSection(effectiveHiddenListSectionIds, row.id)
                                } else {
                                    pendingHiddenPersonalizedSectionIds =
                                        toggleHiddenSection(effectiveHiddenPersonalizedSectionIds, row.id)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (enabled) Color.Black else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (enabled) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { moveRowByDelta(row.id, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    }
                                )
                            }
                            IconButton(
                                onClick = { moveRowByDelta(row.id, 1) },
                                enabled = index < orderedRows.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (index < orderedRows.lastIndex) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    }
                                )
                            }
                        }
                    }
                    if (index < orderedRows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeCustomizeTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val materialDesignEnabled = LocalMaterialDesignEnabled.current
    val containerColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.primaryContainer
        materialDesignEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
    }
    val borderColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        materialDesignEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }
    val textColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

private fun toggleHiddenSection(hidden: Set<String>, id: String): Set<String> {
    val next = hidden.toMutableSet()
    if (!next.add(id)) next.remove(id)
    return next
}

@Composable
private fun NavidromeLibraryRoute(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenNewestAlbums: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenFavoriteSongs: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items = remember {
        listOf(
            NavidromeLibraryDestination("Artists", Icons.Outlined.Person, onOpenArtists),
            NavidromeLibraryDestination("Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Radios", Icons.Outlined.GraphicEq, onOpenRadios),
            NavidromeLibraryDestination("Newest Albums", Icons.Outlined.Album, onOpenNewestAlbums),
            NavidromeLibraryDestination("Recently Played Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Songs", Icons.Outlined.MusicNote, onOpenSongs),
            NavidromeLibraryDestination("Favorite Songs", Icons.Outlined.Favorite, onOpenFavoriteSongs),
            NavidromeLibraryDestination("Playlists", Icons.Outlined.QueueMusic, onOpenPlaylists)
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = NavidromeOverlayBottomContentPadding
        )
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
    onHome: (() -> Unit)?,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel(),
    artistsViewModel: NavidromeArtistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode by artistsViewModel.layoutMode.collectAsStateWithLifecycle()
    val sortOption by artistsViewModel.sortOption.collectAsStateWithLifecycle()
    val searchQuery by artistsViewModel.searchQuery.collectAsStateWithLifecycle()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val showSearchField = searchExpanded || searchQuery.isNotBlank()
    val displayedArtists = remember(uiState.artists, sortOption, searchQuery) {
        val filteredArtists = uiState.artists.filter { artist ->
            searchQuery.isBlank() || artist.name.contains(searchQuery.trim(), ignoreCase = true)
        }
        when (sortOption) {
            NavidromeArtistSortOption.NAME_ASC -> filteredArtists.sortedBy { it.name.lowercase(Locale.getDefault()) }
            NavidromeArtistSortOption.NAME_DESC -> filteredArtists.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            NavidromeArtistSortOption.MOST_ALBUMS -> filteredArtists.sortedWith(
                compareByDescending<NavidromeArtist> { it.albumCount }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
            NavidromeArtistSortOption.FEWEST_ALBUMS -> filteredArtists.sortedWith(
                compareBy<NavidromeArtist> { it.albumCount }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Artists",
            onBack = onBack,
            onHome = onHome,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = if (showSearchField) "Hide artist search" else "Show artist search",
                        onClick = { searchExpanded = !showSearchField }
                    )
                    Box {
                        RoundIconButton(
                            icon = Icons.Outlined.MoreHoriz,
                            contentDescription = "Artist options",
                            onClick = { menuExpanded = true }
                        )
                        AppDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            AppDropdownMenuItem(
                                text = { Text("Grid") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.GridView,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (layoutMode == NavidromeArtistsLayoutMode.Grid) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    artistsViewModel.setLayoutMode(NavidromeArtistsLayoutMode.Grid)
                                    menuExpanded = false
                                }
                            )
                            AppDropdownMenuItem(
                                text = { Text("List") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ViewList,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (layoutMode == NavidromeArtistsLayoutMode.List) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    artistsViewModel.setLayoutMode(NavidromeArtistsLayoutMode.List)
                                    menuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            NavidromeArtistSortOption.entries.forEach { sort ->
                                AppDropdownMenuItem(
                                    text = { Text("Sort: ${sort.label}") },
                                    trailingIcon = {
                                        if (sortOption == sort) {
                                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        artistsViewModel.setSortOption(sort)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
        AnimatedVisibility(visible = showSearchField) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = artistsViewModel::onSearchQueryChange,
                label = { Text("Search artists") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            )
        }
        if (uiState.isLoading) {
            LoadingCard()
        } else if (!uiState.errorMessage.isNullOrBlank()) {
            ErrorCard(uiState.errorMessage ?: "Unable to load artists.")
        } else if (displayedArtists.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No artists yet" else "No matching artists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "This server does not have any artists to show."
                        } else {
                            "Try a different artist name."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (layoutMode == NavidromeArtistsLayoutMode.Grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = NavidromeOverlayBottomContentPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(displayedArtists.size) { index ->
                    val artist = displayedArtists[index]
                    ArtistGridCard(
                        artist = artist,
                        onClick = { onOpenArtist(artist.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = NavidromeOverlayBottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(displayedArtists) { artist ->
                    ArtistRow(artist = artist, onClick = { onOpenArtist(artist.id) })
                }
            }
        }
    }
}

@Composable
private fun NavidromeAlbumsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel(),
    albumsViewModel: NavidromeAlbumsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayStyle by albumsViewModel.layoutMode.collectAsStateWithLifecycle()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Albums",
            onBack = onBack,
            onHome = onHome,
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
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.GRID)
                                menuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                                text = { Text("List view") },
                                leadingIcon = {
                                    if (displayStyle == NavidromeAlbumsDisplayStyle.LIST) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.LIST)
                                menuExpanded = false
                            }
                        )
                        DividerLine()
                        NavidromeAlbumSortOption.entries.forEach { sort ->
                            AppDropdownMenuItem(
                                text = { Text("Sort: ${sort.label}") },
                                leadingIcon = {
                                    if (uiState.albumSort == sort) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
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
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = NavidromeOverlayBottomContentPadding
                ),
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
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = NavidromeOverlayBottomContentPadding
                ),
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
    onHome: (() -> Unit)?,
    viewModel: NavidromeBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Playlists", onBack = onBack, onHome = onHome) {
        items(uiState.playlists) { playlist ->
            PlaylistRow(playlist)
        }
    }
}

@Composable
private fun NavidromeRadiosRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeRadiosViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Radios", onBack = onBack, onHome = onHome) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.radios.isNotEmpty()) {
            items(uiState.radios.size) { index ->
                val radio = uiState.radios[index]
                val isCurrent = playerState.currentTrack?.id == "radio:${radio.id}"
                RadioRow(
                    radio = radio,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playerState.isPlaying,
                    onClick = { playerViewModel.playRadios(uiState.radios, index) }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load radios.") }
        } else {
            item { EmptyCard("No radios found.") }
        }
    }
}

@Composable
private fun NavidromeFavoriteSongsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeFavoriteSongsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingTrackRemoval by remember { mutableStateOf<NavidromeTrack?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    StandardTopScreen(
        title = "Favorite Songs",
        onBack = onBack,
        onHome = onHome,
        actions = {
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Favorite song options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Clear Favorite Songs") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Favorite, contentDescription = null)
                        },
                        enabled = uiState.songs.isNotEmpty(),
                        onClick = {
                            menuExpanded = false
                            showClearAllConfirmation = true
                        }
                    )
                }
            }
        }
    ) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.songs.isNotEmpty()) {
            items(uiState.songs.size) { index ->
                val track = uiState.songs[index]
                TrackRow(
                    track = track,
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                    onClick = { playerViewModel.playTracks(uiState.songs, index) },
                    trailingContent = {
                        var rowMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { rowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Favorite song options"
                                )
                            }
                            NavidromeTrackActionsMenu(
                                expanded = rowMenuExpanded,
                                onDismissRequest = { rowMenuExpanded = false },
                                onPlayTrack = {
                                    rowMenuExpanded = false
                                    playerViewModel.playTracks(uiState.songs, index)
                                },
                                playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                onShowAlbum = onOpenAlbum?.let { openAlbum ->
                                    track.albumId?.let { albumId ->
                                        {
                                            rowMenuExpanded = false
                                            openAlbum(albumId)
                                        }
                                    }
                                },
                                onShowArtist = onOpenArtist?.let { openArtist ->
                                    track.artistId?.let { artistId ->
                                        {
                                            rowMenuExpanded = false
                                            openArtist(artistId)
                                        }
                                    }
                                },
                                extraActions = {
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = { Text("Remove Favorite") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Favorite,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            pendingTrackRemoval = track
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load favorite songs.") }
        } else {
            item { EmptyCard("No favorite songs yet.") }
        }
    }
    pendingTrackRemoval?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingTrackRemoval = null },
            title = { Text("Remove Favorite") },
            text = { Text("Remove \"${track.title}\" from Favorite Songs?") },
            dismissButton = {
                TextButton(onClick = { pendingTrackRemoval = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFavoriteTrack(track.id)
                        pendingTrackRemoval = null
                    }
                ) {
                    Text("Remove")
                }
            }
        )
    }
    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            title = { Text("Clear Favorite Songs") },
            text = { Text("Remove all songs from this local Favorite Songs list?") },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearFavorites()
                        showClearAllConfirmation = false
                    }
                ) {
                    Text("Clear All")
                }
            }
        )
    }
}

@Composable
private fun NavidromeSongsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeSongsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Songs", onBack = onBack, onHome = onHome) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.songs.isNotEmpty()) {
            items(uiState.songs.size) { index ->
                val track = uiState.songs[index]
                TrackRow(
                    track = track,
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                    onClick = { playerViewModel.playTracks(uiState.songs, index) }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load songs.") }
        } else {
            item { EmptyCard("No songs found.") }
        }
    }
}

@Composable
private fun NavidromeSearchRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    StandardTopScreen(
        title = "Search",
        onBack = onBack,
        onHome = onHome,
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
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeArtistDetailViewModel = hiltViewModel(),
    albumsViewModel: NavidromeAlbumsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayStyle by albumsViewModel.layoutMode.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Artist",
            onBack = onBack,
            onHome = onHome,
            actions = {
                Box {
                    RoundIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = "Artist album options",
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
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.GRID)
                                menuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("List view") },
                            leadingIcon = {
                                if (displayStyle == NavidromeAlbumsDisplayStyle.LIST) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.LIST)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        )
        if (uiState.isLoading) {
            LoadingCard()
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = NavidromeOverlayBottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CenteredDetailHero(
                        title = detail.artist.name,
                        subtitle = "${detail.artist.albumCount} albums",
                        imageUrl = detail.artist.imageUrl ?: detail.artist.coverUrl,
                        circular = true
                    )
                }
                item { SectionTitle("Albums") }
                if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            userScrollEnabled = false,
                            modifier = Modifier.height((((detail.albums.size + 1) / 2) * 244).dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(detail.albums.size) { index ->
                                val album = detail.albums[index]
                                AlbumGridCard(
                                    album = album,
                                    isCurrent = playerState.currentTrack?.albumId == album.id,
                                    onClick = { onOpenAlbum(album.id) }
                                )
                            }
                        }
                    }
                } else {
                    items(detail.albums) { album ->
                        AlbumRow(
                            album = album,
                            isCurrent = playerState.currentTrack?.albumId == album.id,
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                }
            }
        } else {
            ErrorCard(uiState.errorMessage ?: "Unable to load artist.")
        }
    }
}

@Composable
private fun NavidromeAlbumDetailRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeAlbumDetailViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Album", onBack = onBack, onHome = onHome) {
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
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
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
    onHome: (() -> Unit)?,
    onSwitchMode: () -> Unit,
    viewModel: NavidromeSettingsViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(
        title = "Settings",
        onBack = onBack,
        onHome = onHome,
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
                SettingsSwitchRow(
                    title = "Material Design",
                    checked = appearanceUiState.navidromeMaterialDesignEnabled,
                    onCheckedChange = appearanceViewModel::setNavidromeMaterialDesignEnabled
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Follow System Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.FollowSystem,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.FollowSystem) }
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Light Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.Light,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.Light) }
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Dark Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.Dark,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.Dark) }
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
    onHome: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(modifier = Modifier.statusBarsPadding()) {
                DetailHeader(title = title, onBack = onBack, onHome = onHome, actions = actions)
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
    onHome: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }
        if (onHome != null) {
            CircleActionButton(
                icon = Icons.Outlined.Home,
                contentDescription = "Home",
                onClick = onHome
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (actions != null) {
            Row(
                modifier = Modifier.padding(end = 2.dp),
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
private fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun NavidromeContinueListeningCard(
    track: NavidromeTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    cardWidth: Dp = 266.dp,
    cardHeight: Dp = 98.dp,
    posterWidth: Dp = 72.dp,
    posterHeight: Dp = 80.dp,
    onPlayPause: () -> Unit,
    onPlayTrack: () -> Unit,
    onClick: () -> Unit,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fallbackCardColor = Color(0xFF665A2E)
    val dominantCoverColor = rememberDominantNavidromeCoverColor(
        coverUrl = track.coverUrl,
        enabled = true
    )
    val containerColor = remember(dominantCoverColor) {
        val baseColor = dominantCoverColor ?: fallbackCardColor
        val vividBase = brightenAndSaturateNavidromeCardColor(baseColor)
        val darkenAmount = when {
            vividBase.luminance() > 0.62f -> 0.32f
            vividBase.luminance() > 0.45f -> 0.2f
            else -> 0.1f
        }
        lerp(vividBase, Color.Black, darkenAmount)
    }
    val primaryTextColor = if (containerColor.luminance() > 0.45f) Color(0xFF1B1B1B) else Color.White
    val secondaryTextColor = if (containerColor.luminance() > 0.45f) Color(0xFF2F2F2F) else Color(0xFFD8D8D8)
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    url = track.coverUrl,
                    width = posterWidth,
                    height = posterHeight,
                    shape = RoundedCornerShape(6.dp),
                    contentScale = ContentScale.Fit
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 28.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = track.albumName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        if (isCurrent) {
                            onPlayPause()
                        } else {
                            onPlayTrack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isCurrent && isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                        tint = primaryTextColor
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Continue listening actions",
                        tint = primaryTextColor
                    )
                }
                NavidromeTrackActionsMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onPlayTrack = {
                        if (isCurrent) {
                            onPlayPause()
                        } else {
                            onPlayTrack()
                        }
                        menuExpanded = false
                    },
                    playLabel = if (isCurrent && isPlaying) "Pause" else if (isCurrent) "Resume" else "Play Now",
                    onShowAlbum = onOpenAlbum?.let { action ->
                        {
                            action()
                            menuExpanded = false
                        }
                    },
                    onShowArtist = onOpenArtist?.let { action ->
                        {
                            action()
                            menuExpanded = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavidromeHomeAlbumCard(
    album: NavidromeAlbum,
    posterWidth: Dp,
    posterHeight: Dp,
    onClick: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(posterWidth)
            .clickable(onClick = onClick)
    ) {
        AlbumArt(url = album.coverUrl, size = posterWidth)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.width(posterWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "Album actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NavidromeAlbumActionsMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onPlayAlbum = {
                            onPlayAlbum()
                            menuExpanded = false
                        },
                        onShuffleAlbum = {
                            onShuffleAlbum()
                            menuExpanded = false
                        },
                        onShowAlbum = {
                            onOpenAlbum()
                            menuExpanded = false
                        },
                        onShowArtist = onOpenArtist?.let { action ->
                            {
                                action()
                                menuExpanded = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavidromeAlbumActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayAlbum: (() -> Unit)? = null,
    onShuffleAlbum: (() -> Unit)? = null,
    onShowAlbum: () -> Unit,
    onShowArtist: (() -> Unit)?
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        onPlayAlbum?.let { action ->
            AppDropdownMenuItem(
                text = { Text("Play Album") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null
                    )
                },
                onClick = action
            )
        }
        onShuffleAlbum?.let { action ->
            AppDropdownMenuItem(
                text = { Text("Shuffle Album") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = null
                    )
                },
                onClick = action
            )
        }
        if (onPlayAlbum != null || onShuffleAlbum != null) {
            HorizontalDivider()
        }
        AppDropdownMenuItem(
            text = { Text("Show Album") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Album,
                    contentDescription = null
                )
            },
            onClick = onShowAlbum
        )
        AppDropdownMenuItem(
            text = { Text("Show Artist") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            enabled = onShowArtist != null,
            onClick = { onShowArtist?.invoke() }
        )
    }
}

@Composable
private fun NavidromeTrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayTrack: () -> Unit,
    playLabel: String,
    onShowAlbum: (() -> Unit)?,
    onShowArtist: (() -> Unit)?,
    extraActions: (@Composable ColumnScope.() -> Unit)? = null
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        AppDropdownMenuItem(
            text = { Text(playLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            },
            onClick = onPlayTrack
        )
        AppDropdownMenuItem(
            text = { Text("Show Album") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Album,
                    contentDescription = null
                )
            },
            enabled = onShowAlbum != null,
            onClick = { onShowAlbum?.invoke() }
        )
        AppDropdownMenuItem(
            text = { Text("Show Artist") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            enabled = onShowArtist != null,
            onClick = { onShowArtist?.invoke() }
        )
        extraActions?.invoke(this)
    }
}

@Composable
private fun NavidromeHomeArtistCard(
    artist: NavidromeArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArtistArt(url = artist.imageUrl ?: artist.coverUrl, size = 84.dp)
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NavidromeHomePlaylistCard(
    playlist: NavidromePlaylist
) {
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
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 4.dp)
    )
}

@Composable
private fun AlbumGridCard(
    album: NavidromeAlbum,
    isCurrent: Boolean = false,
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
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isCurrent) "Playing" else album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) Color(0xFFFF5A5F) else MaterialTheme.colorScheme.onSurfaceVariant,
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
    isCurrent: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = album.coverUrl, size = 58.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = listOfNotNull(album.artistName, album.year?.toString()).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) {
                Text(
                    text = "Playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5F)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun ArtistGridCard(
    artist: NavidromeArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArtistArt(
            url = artist.imageUrl ?: artist.coverUrl,
            size = 132.dp
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
private fun RadioRow(
    radio: NavidromeRadio,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = remember(radio.homePageUrl, radio.streamUrl) {
        formatRadioSubtitle(radio)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = radio.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5F)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun TrackRow(
    track: NavidromeTrack,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = track.coverUrl, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = "${track.artistName} • ${track.albumName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (isCurrent) {
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5F)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            }
        }
        DividerLine()
    }
}

private fun formatRadioSubtitle(radio: NavidromeRadio): String {
    val source = radio.homePageUrl?.ifBlank { null } ?: radio.streamUrl
    val host = source
        .substringAfter("://", source)
        .substringBefore('/')
        .substringBefore('?')
        .removePrefix("www.")
    return host.ifBlank { "Internet radio" }
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
private fun ThemeSettingsRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
private fun NavidromeMiniPlayerBar(
    state: NavidromePlayerState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val isRadio = remember(track.id) { track.id.startsWith("radio:") }
    val shape = RoundedCornerShape(24.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .clickable(onClick = onOpenPlayer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = track.coverUrl, size = 30.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isRadio && state.isPlaying -> Icons.Outlined.Stop
                            state.isPlaying -> Icons.Outlined.Pause
                            else -> Icons.Outlined.PlayArrow
                        },
                        contentDescription = when {
                            isRadio && state.isPlaying -> "Stop"
                            state.isPlaying -> "Pause"
                            else -> "Play"
                        },
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavidromeExpandedPlayerSheet(
    state: NavidromePlayerState,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (Int?) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: (NavidromeTrack) -> Boolean,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null
) {
    val track = state.currentTrack ?: return
    val isRadio = remember(track.id) { track.id.startsWith("radio:") }
    val context = LocalContext.current
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val outputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queuedTracks = remember(state.queue, track) {
        state.queue.ifEmpty { listOf(track) }
    }
    val selectedOutput = remember(state.outputDevices, state.selectedOutputDeviceId) {
        state.outputDevices.firstOrNull { it.id == state.selectedOutputDeviceId }
    }
    val outputLabel = remember(selectedOutput) {
        selectedOutput?.let { device ->
            if (device.typeLabel.equals("Phone speaker", ignoreCase = true)) {
                "Phone"
            } else {
                device.typeLabel
            }
        } ?: "Output"
    }
    val outputIcon = remember(selectedOutput) {
        playerOutputToolIcon(selectedOutput?.typeLabel)
    }
    val resolvedDurationMs = remember(state.durationMs, track.durationSeconds) {
        state.durationMs.takeIf { it > 0 } ?: ((track.durationSeconds ?: 0) * 1000)
    }
    var sliderPosition by remember(state.positionMs, resolvedDurationMs) {
        mutableStateOf(
            if (resolvedDurationMs > 0) {
                state.positionMs.coerceIn(0, resolvedDurationMs).toFloat()
            } else {
                0f
            }
        )
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val compactLayout = maxHeight < 760.dp
        val queueExpandedLayout = showQueue
        val coverSize = when {
            queueExpandedLayout && maxHeight < 680.dp -> 156.dp
            queueExpandedLayout && maxHeight < 760.dp -> 176.dp
            queueExpandedLayout -> 196.dp
            maxHeight < 680.dp -> 220.dp
            maxHeight < 760.dp -> 242.dp
            else -> 280.dp
        }.coerceAtMost(maxWidth - 48.dp)
        val outerSpacing = when {
            queueExpandedLayout -> 8.dp
            compactLayout -> 10.dp
            else -> 14.dp
        }
        val titleSpacing = if (queueExpandedLayout || compactLayout) 3.dp else 5.dp
        val topPadding = if (compactLayout) 8.dp else 12.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 6.dp)
                .then(
                    if (queueExpandedLayout) {
                        Modifier.verticalScroll(contentScrollState)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(outerSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    AlbumArt(
                        url = track.coverUrl,
                        width = coverSize,
                        height = coverSize,
                        fallbackIcon = if (isRadio) Icons.Outlined.GraphicEq else Icons.Outlined.Album
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(titleSpacing)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.albumName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = (-4).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.clickable(onClick = { isMenuExpanded = true }),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreHoriz,
                                contentDescription = "Player options",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    AppDropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        if (!isRadio) {
                            AppDropdownMenuItem(
                                text = { Text(if (isFavorite) "Remove Favorite" else "Favorite") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Favorite, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    val added = onToggleFavorite(track)
                                    Toast.makeText(
                                        context,
                                        if (added) "Added to Favorite Songs" else "Removed from Favorite Songs",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        onOpenAlbum?.takeIf { track.albumId != null }?.let { openAlbum ->
                            AppDropdownMenuItem(
                                text = { Text("Show Album") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Album, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onDismiss()
                                    openAlbum(track.albumId!!)
                                }
                            )
                        }
                        onOpenArtist?.takeIf { track.artistId != null }?.let { openArtist ->
                            AppDropdownMenuItem(
                                text = { Text("Show Artist") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onDismiss()
                                    openArtist(track.artistId!!)
                                }
                            )
                        }
                        AppDropdownMenuItem(
                            text = { Text("Track Details") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                showTrackDetails = true
                            }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavidromePlayerProgressBar(
                    progress = if (resolvedDurationMs > 0) {
                        (sliderPosition / resolvedDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    activeColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    onProgressChange = { newProgress ->
                        sliderPosition = resolvedDurationMs * newProgress.coerceIn(0f, 1f)
                    },
                    onProgressChangeFinished = { finalProgress ->
                        val finalPosition = (resolvedDurationMs * finalProgress.coerceIn(0f, 1f)).roundToInt()
                        sliderPosition = finalPosition.toFloat()
                        onSeekTo(finalPosition)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMillis(sliderPosition.roundToInt()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTrackTechnicalDetails(track),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDurationMillis(resolvedDurationMs),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(if (queueExpandedLayout) 56.dp else 64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(if (queueExpandedLayout) 32.dp else 36.dp)
                    )
                }
                Surface(
                    modifier = Modifier.size(if (queueExpandedLayout) 80.dp else 88.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isRadio && state.isPlaying -> Icons.Outlined.Stop
                                state.isPlaying -> Icons.Outlined.Pause
                                else -> Icons.Outlined.PlayArrow
                            },
                            contentDescription = when {
                                isRadio && state.isPlaying -> "Stop"
                                state.isPlaying -> "Pause"
                                else -> "Play"
                            },
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(if (queueExpandedLayout) 38.dp else 42.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(if (queueExpandedLayout) 56.dp else 64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(if (queueExpandedLayout) 32.dp else 36.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavidromePlayerToolButton(
                    modifier = Modifier.weight(1f),
                    icon = outputIcon,
                    label = outputLabel,
                    onClick = {
                        onRefreshAudioOutputs()
                        showOutputSheet = true
                    }
                )
                NavidromePlayerToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.QueueMusic,
                    label = if (showQueue) "Hide Queue" else "Queue",
                    onClick = { showQueue = !showQueue }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (showQueue) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        modifier = Modifier.fillMaxWidth(),
                        visible = showQueue,
                        enter = slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(durationMillis = 220)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 4 },
                            animationSpec = tween(durationMillis = 180)
                        )
                    ) {
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
                                        isCurrent = index == state.currentIndex,
                                        onClick = { onSelectTrack(index) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showOutputSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutputSheet = false },
            sheetState = outputSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ) {
            NavidromePlayerOutputSheet(
                outputDevices = state.outputDevices,
                selectedOutputDeviceId = state.selectedOutputDeviceId,
                onSelectOutput = { deviceId ->
                    onSelectAudioOutput(deviceId)
                    showOutputSheet = false
                }
            )
        }
    }
    if (showTrackDetails) {
        AlertDialog(
            onDismissRequest = { showTrackDetails = false },
            confirmButton = {
                TextButton(onClick = { showTrackDetails = false }) {
                    Text("Done")
                }
            },
            title = {
                Text(
                    text = "Track Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayerMetadataRow(label = "Title", value = track.title)
                    PlayerMetadataRow(label = "Artist", value = track.artistName)
                    PlayerMetadataRow(label = "Album", value = track.albumName)
                    track.trackNumber?.let { trackNumber ->
                        PlayerMetadataRow(label = "Track", value = trackNumber.toString())
                    }
                    track.formatLabel?.takeIf { it.isNotBlank() }?.let { formatLabel ->
                        PlayerMetadataRow(label = "Format", value = formatLabel)
                    }
                    track.bitRateKbps?.takeIf { it > 0 }?.let { bitRate ->
                        PlayerMetadataRow(label = "Bitrate", value = "$bitRate kbps")
                    }
                    PlayerMetadataRow(
                        label = "Length",
                        value = formatDurationMillis(resolvedDurationMs)
                    )
                }
            }
        )
    }
}

@Composable
private fun NavidromePlayerToolButton(
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerMetadataRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun playerOutputToolIcon(typeLabel: String?): ImageVector {
    val label = typeLabel?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        label.contains("bluetooth") -> Icons.Outlined.GraphicEq
        label.contains("wired") || label.contains("usb") -> Icons.Outlined.GraphicEq
        label.contains("speaker") || label.contains("phone") -> Icons.Outlined.VolumeUp
        else -> Icons.Outlined.SettingsVoice
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavidromePlayerOutputSheet(
    outputDevices: List<NavidromeOutputDevice>,
    selectedOutputDeviceId: Int?,
    onSelectOutput: (Int?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Output",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose where audio plays",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (outputDevices.isEmpty()) {
            Text(
                text = "No output devices detected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            outputDevices.forEach { device ->
                val selected = device.id == selectedOutputDeviceId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                            }
                        )
                        .clickable { onSelectOutput(device.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = playerOutputToolIcon(device.typeLabel),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.typeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
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

private fun formatDurationMillis(totalMs: Int): String {
    return formatDuration((totalMs.coerceAtLeast(0)) / 1000)
}

private fun formatTrackTechnicalDetails(track: NavidromeTrack): String {
    val parts = buildList {
        track.formatLabel?.takeIf { it.isNotBlank() }?.let(::add)
        track.bitRateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
    }
    return parts.joinToString(separator = " • ")
}

private fun formatNavidromeServerLabel(baseUrl: String): String {
    val normalized = baseUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("www.")
        .ifBlank { "Navidrome" }
    return normalized
}

@Composable
private fun NavidromePlayerProgressBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val touchTargetHeight = 30.dp
    val barHeight = 9.dp
    var widthPx by remember { mutableStateOf(0f) }
    var dragProgress by remember { mutableStateOf(clampedProgress) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(clampedProgress, isDragging) {
        if (!isDragging) {
            dragProgress = clampedProgress
        }
    }

    fun offsetToProgress(x: Float): Float {
        if (widthPx <= 0f) return clampedProgress
        return (x / widthPx).coerceIn(0f, 1f)
    }

    val draggableState = rememberDraggableState { delta ->
        if (widthPx <= 0f) return@rememberDraggableState
        isDragging = true
        dragProgress = (dragProgress + (delta / widthPx)).coerceIn(0f, 1f)
        onProgressChange(dragProgress)
    }
    val displayProgress = if (isDragging) dragProgress else clampedProgress

    BoxWithConstraints(
        modifier = modifier
            .height(touchTargetHeight)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx, clampedProgress) {
                detectTapGestures { offset ->
                    val tappedProgress = offsetToProgress(offset.x)
                    isDragging = false
                    onProgressChange(tappedProgress)
                    onProgressChangeFinished(tappedProgress)
                }
            }
            .draggable(
                state = draggableState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStarted = { offset ->
                    isDragging = true
                    dragProgress = offsetToProgress(offset.x)
                    onProgressChange(dragProgress)
                },
                onDragStopped = {
                    onProgressChangeFinished(dragProgress)
                    isDragging = false
                }
            )
    ) {
        val barShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomStart)
                .clip(barShape)
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress)
                .height(barHeight)
                .align(Alignment.BottomStart)
                .clip(barShape)
                .background(activeColor)
        )
    }
}

@Composable
private fun rememberDominantNavidromeCoverColor(
    coverUrl: String?,
    enabled: Boolean
): Color? {
    val context = LocalContext.current
    val dominantColorState = produceState<Color?>(initialValue = null, coverUrl, enabled) {
        if (!enabled || coverUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolvedCover = splitAuthenticatedUrl(coverUrl)
                val request = ImageRequest.Builder(context)
                    .data(resolvedCover.cleanUrl)
                    .apply {
                        resolvedCover.authToken?.takeIf { it.isNotBlank() }?.let { token ->
                            addHeader("Authorization", authorizationHeaderValue(token))
                        }
                    }
                    .allowHardware(false)
                    .size(64)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
                val bitmap = drawable.toBitmap(
                    width = 20,
                    height = 20,
                    config = Bitmap.Config.ARGB_8888
                )
                averageNavidromeBitmapColor(bitmap)
            }.getOrNull()
        }
    }
    return dominantColorState.value
}

private fun averageNavidromeBitmapColor(bitmap: Bitmap): Color {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            count += 1
        }
    }

    if (count == 0L) return Color(0xFF2B2D31)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt()
    )
}

private fun brightenAndSaturateNavidromeCardColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = (hsv[1] * 1.48f + 0.12f).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * 0.94f + 0.02f).coerceIn(0.22f, 0.86f)
    return Color(android.graphics.Color.HSVToColor(hsv))
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
    AlbumArt(
        url = url,
        width = size,
        height = size
    )
}

@Composable
private fun AlbumArt(
    url: String?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: Shape = RoundedCornerShape(18.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Outlined.Album
) {
    if (url.isNullOrBlank()) {
        val fallbackIconSize = if (width >= 160.dp || height >= 160.dp) 92.dp else 28.dp
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.size(fallbackIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape),
            contentScale = contentScale
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
