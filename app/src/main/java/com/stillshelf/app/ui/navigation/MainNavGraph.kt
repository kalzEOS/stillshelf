package com.stillshelf.app.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.stillshelf.app.ui.components.MiniPlayerViewModel
import com.stillshelf.app.ui.components.RootScaffold
import com.stillshelf.app.ui.screens.AboutScreen
import com.stillshelf.app.ui.screens.AuthorsBrowseScreen
import com.stillshelf.app.ui.screens.AuthorDetailScreen
import com.stillshelf.app.ui.screens.BookDetailScreen
import com.stillshelf.app.ui.screens.BookmarksBrowseScreen
import com.stillshelf.app.ui.screens.BrowseScreen
import com.stillshelf.app.ui.screens.CollectionDetailScreen
import com.stillshelf.app.ui.screens.CollectionsBrowseScreen
import com.stillshelf.app.ui.screens.CustomizeScreen
import com.stillshelf.app.ui.screens.DownloadsScreen
import com.stillshelf.app.ui.screens.GenreDetailScreen
import com.stillshelf.app.ui.screens.GenresBrowseScreen
import com.stillshelf.app.ui.screens.HomeScreen
import com.stillshelf.app.ui.screens.NarratorsBrowseScreen
import com.stillshelf.app.ui.screens.NarratorDetailScreen
import com.stillshelf.app.ui.screens.PlayerScreen
import com.stillshelf.app.ui.screens.PlayerViewModel
import com.stillshelf.app.ui.screens.PlaylistsBrowseScreen
import com.stillshelf.app.ui.screens.PlaylistDetailScreen
import com.stillshelf.app.ui.screens.SearchScreen
import com.stillshelf.app.ui.screens.ServersManagementScreen
import com.stillshelf.app.ui.screens.ServerConnectionViewModel
import com.stillshelf.app.ui.screens.SeriesDetailScreen
import com.stillshelf.app.ui.screens.SeriesBrowseScreen
import com.stillshelf.app.ui.screens.SettingsScreen
import com.stillshelf.app.ui.screens.auth.AddServerRoute
import com.stillshelf.app.ui.screens.auth.LibraryPickerRoute
import com.stillshelf.app.ui.screens.auth.LoginRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

fun NavGraphBuilder.mainNavGraph(
    onHomeScreenReached: () -> Unit = {}
) {
    navigation(
        route = GraphRoute.MAIN,
        startDestination = MainRoute.SHELL
    ) {
        composable(MainRoute.SHELL) {
            MainShell(
                onHomeScreenReached = onHomeScreenReached
            )
        }
    }
}

@Composable
private fun MainShell(
    onHomeScreenReached: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && activity != null) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val tabsNavController = rememberNavController()
    val currentBackStackEntry by tabsNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentTab = MainTab.fromRoute(currentRoute)
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val serverConnectionViewModel: ServerConnectionViewModel = hiltViewModel()
    val miniPlayerState by miniPlayerViewModel.uiState.collectAsStateWithLifecycle()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var playerVisible by rememberSaveable { mutableStateOf(false) }
    var pendingPlayerCloseAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val onHomeClick: () -> Unit = {
        if (!tabsNavController.popBackStack(MainTab.Home.route, inclusive = false)) {
            tabsNavController.navigate(MainTab.Home.route) {
                popUpTo(tabsNavController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarTopInset = remember(view, density) {
        with(density) {
            (
                ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())
                    ?.top
                    ?: 0
                ).toDp()
        }
    }
    fun showPlayer(bookId: String? = null, startSeconds: Double? = null) {
        playerViewModel.openPlayer(
            bookId = bookId,
            startSeconds = startSeconds
        )
        playerVisible = true
    }
    fun handleBookSelection(bookId: String? = null, startSeconds: Double? = null) {
        val hasActivePlayback = playerUiState.book != null
        if (hasActivePlayback || playerVisible) {
            playerViewModel.openPlayer(
                bookId = bookId,
                startSeconds = startSeconds
            )
        } else {
            showPlayer(bookId = bookId, startSeconds = startSeconds)
        }
    }
    fun closePlayer(afterClose: (() -> Unit)? = null) {
        pendingPlayerCloseAction = afterClose
        playerVisible = false
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == MainTab.Home.route) {
            onHomeScreenReached()
        }
    }
    LaunchedEffect(serverConnectionViewModel) {
        serverConnectionViewModel.messages.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(playerVisible, pendingPlayerCloseAction) {
        if (!playerVisible && pendingPlayerCloseAction != null) {
            delay(240L)
            pendingPlayerCloseAction?.invoke()
            pendingPlayerCloseAction = null
        }
    }

    val showMiniPlayer = !playerVisible &&
        currentRoute != MainTab.Search.route &&
        currentRoute != MainTab.Settings.route &&
        currentRoute != MainRoute.SETTINGS &&
        currentRoute != MainRoute.ABOUT &&
        currentRoute != MainRoute.SERVERS &&
        currentRoute != MainRoute.LIBRARY_PICKER &&
        currentRoute?.startsWith("auth/") != true
    val showMiniPlayerHomeButton = showMiniPlayer && currentRoute != MainTab.Home.route
    val screenHomeClick: (() -> Unit)? = if (showMiniPlayerHomeButton) null else onHomeClick

    Box(modifier = Modifier.fillMaxSize()) {
        RootScaffold(
            currentTab = currentTab,
            onTabSelected = { tab ->
                tabsNavController.navigate(tab.route) {
                    popUpTo(tabsNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            miniPlayerState = miniPlayerState,
            applyTopSafeInset = currentRoute?.startsWith("auth/") != true,
            onMiniPlayerHomeClick = if (showMiniPlayerHomeButton) onHomeClick else null,
            onMiniPlayerRewind15 = miniPlayerViewModel::onRewindClick,
            onMiniPlayerPlayPause = miniPlayerViewModel::onPlayPauseClick,
            onMiniPlayerClick = { showPlayer() },
            showMiniPlayer = showMiniPlayer
        ) { paddingValues ->
            MainTabsNavHost(
                paddingValues = paddingValues,
                navController = tabsNavController,
                onHomeClick = screenHomeClick,
                onOpenSelectedBook = ::handleBookSelection
            )
        }

        AnimatedVisibility(
            modifier = Modifier.fillMaxSize(),
            visible = playerVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 280)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 240)
            )
        ) {
            BackHandler(enabled = true) {
                closePlayer()
            }
            PlayerScreen(
                onBackClick = { closePlayer() },
                viewModel = playerViewModel,
                onGoToBook = { bookId ->
                    closePlayer(
                        afterClose = {
                            tabsNavController.navigate(DetailRoute.book(bookId)) {
                                launchSingleTop = true
                            }
                        }
                    )
                },
                topContentInset = statusBarTopInset,
                manageStatusBarAppearance = true
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun MainTabsNavHost(
    paddingValues: PaddingValues,
    navController: androidx.navigation.NavHostController,
    onHomeClick: (() -> Unit)?,
    onOpenSelectedBook: (String?, Double?) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = MainTab.Home.route,
        modifier = Modifier.padding(paddingValues),
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
        composable(MainTab.Home.route) {
            HomeScreen(
                onNavigateToRoute = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
                onOpenBook = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onOpenSeries = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                },
                onOpenAuthor = { authorName ->
                    navController.navigate(DetailRoute.author(authorName)) {
                        launchSingleTop = true
                    }
                },
                onOpenPlayer = { bookId ->
                    onOpenSelectedBook(bookId, null)
                }
            )
        }
        composable(MainTab.Browse.route) {
            BrowseScreen(
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainTab.Search.route) {
            SearchScreen(
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onAuthorClick = { authorName ->
                    navController.navigate(DetailRoute.author(authorName)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                },
                onNarratorClick = { narratorName ->
                    navController.navigate(DetailRoute.narrator(narratorName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainTab.Downloads.route) {
            DownloadsScreen(
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainTab.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onOpenAbout = {
                    navController.navigate(MainRoute.ABOUT) {
                        launchSingleTop = true
                    }
                },
                onManageServers = {
                    navController.navigate(MainRoute.SERVERS) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainRoute.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onOpenAbout = {
                    navController.navigate(MainRoute.ABOUT) {
                        launchSingleTop = true
                    }
                },
                onManageServers = {
                    navController.navigate(MainRoute.SERVERS) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainRoute.ABOUT) {
            AboutScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick
            )
        }
        composable(MainRoute.SERVERS) {
            ServersManagementScreen(
                onBackClick = { navController.popBackStack() },
                onAddServerClick = {
                    navController.navigate(AuthRoute.ADD_SERVER) {
                        launchSingleTop = true
                    }
                },
                onHomeClick = onHomeClick
            )
        }
        composable(MainRoute.LIBRARY_PICKER) {
            LibraryPickerRoute(
                onLibrarySelected = {
                    if (!navController.popBackStack(MainTab.Home.route, inclusive = false)) {
                        navController.navigate(MainTab.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onManageServers = {
                    navController.navigate(MainRoute.SERVERS) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AuthRoute.ADD_SERVER) {
            val canNavigateBack = navController.previousBackStackEntry != null
            AddServerRoute(
                onContinue = { serverName, baseUrl ->
                    navController.navigate(AuthRoute.loginRoute(serverName, baseUrl)) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                showBackButton = canNavigateBack
            )
        }
        composable(
            route = AuthRoute.LOGIN_PATTERN,
            arguments = listOf(
                navArgument(AuthRoute.SERVER_NAME_ARG) { type = NavType.StringType },
                navArgument(AuthRoute.BASE_URL_ARG) { type = NavType.StringType }
            )
        ) {
            LoginRoute(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    if (!navController.popBackStack(MainRoute.SERVERS, inclusive = false)) {
                        navController.navigate(MainRoute.SERVERS) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(AuthRoute.LIBRARY_PICKER) {
            LibraryPickerRoute(
                onLibrarySelected = {
                    if (!navController.popBackStack(MainRoute.SERVERS, inclusive = false)) {
                        navController.navigate(MainRoute.SERVERS) {
                            launchSingleTop = true
                        }
                    }
                },
                onManageServers = {
                    if (!navController.popBackStack(MainRoute.SERVERS, inclusive = false)) {
                        navController.navigate(MainRoute.SERVERS) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(MainRoute.CUSTOMIZE) {
            CustomizeScreen(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onHomeClick = onHomeClick
            )
        }

        composable(BrowseRoute.BOOKS) {
            BrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.AUTHORS) {
            AuthorsBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onAuthorClick = { authorName ->
                    navController.navigate(DetailRoute.author(authorName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.NARRATORS) {
            NarratorsBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onNarratorClick = { narratorName ->
                    navController.navigate(DetailRoute.narrator(narratorName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.SERIES) {
            SeriesBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onSeriesClick = { seriesName, seriesId ->
                    navController.navigate(DetailRoute.series(seriesName, seriesId))
                }
            )
        }
        composable(BrowseRoute.COLLECTIONS) {
            CollectionsBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onCollectionClick = { collection ->
                    navController.navigate(DetailRoute.collection(collection.id, collection.name)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.GENRES) {
            GenresBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onGenreClick = { genreName ->
                    navController.navigate(DetailRoute.genre(genreName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.BOOKMARKS) {
            BookmarksBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookmarkClick = { bookId, startSeconds ->
                    onOpenSelectedBook(bookId, startSeconds)
                }
            )
        }
        composable(BrowseRoute.PLAYLISTS) {
            PlaylistsBrowseScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onPlaylistClick = { playlist ->
                    navController.navigate(DetailRoute.playlist(playlist.id, playlist.name)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.DOWNLOADED) {
            DownloadsScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = DetailRoute.BOOK_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.BOOK_ID_ARG) {
                    type = NavType.StringType
                }
            )
        ) {
            BookDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onStartListening = { bookId ->
                    onOpenSelectedBook(bookId, null)
                },
                onOpenAuthor = { authorName ->
                    navController.navigate(DetailRoute.author(authorName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = DetailRoute.AUTHOR_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.AUTHOR_NAME_ARG) {
                    type = NavType.StringType
                }
            )
        ) {
            AuthorDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = DetailRoute.SERIES_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.SERIES_NAME_ARG) {
                    type = NavType.StringType
                },
                navArgument(DetailRoute.SERIES_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            SeriesDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName, seriesId ->
                    navController.navigate(DetailRoute.subseries(seriesName, seriesId))
                }
            )
        }
        composable(
            route = DetailRoute.SUBSERIES_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.SERIES_NAME_ARG) {
                    type = NavType.StringType
                },
                navArgument(DetailRoute.SERIES_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            SeriesDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesName, seriesId ->
                    navController.navigate(DetailRoute.subseries(seriesName, seriesId))
                }
            )
        }
        composable(
            route = DetailRoute.NARRATOR_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.NARRATOR_NAME_ARG) {
                    type = NavType.StringType
                }
            )
        ) {
            NarratorDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = DetailRoute.GENRE_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.GENRE_NAME_ARG) {
                    type = NavType.StringType
                }
            )
        ) {
            GenreDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = DetailRoute.COLLECTION_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.COLLECTION_ID_ARG) { type = NavType.StringType },
                navArgument(DetailRoute.COLLECTION_NAME_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) {
            CollectionDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = DetailRoute.PLAYLIST_PATTERN,
            arguments = listOf(
                navArgument(DetailRoute.PLAYLIST_ID_ARG) { type = NavType.StringType },
                navArgument(DetailRoute.PLAYLIST_NAME_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) {
            PlaylistDetailScreen(
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
