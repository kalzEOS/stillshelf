package com.stillshelf.app.ui.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import com.stillshelf.app.ui.screens.AuthorsBrowseScreen
import com.stillshelf.app.ui.screens.AuthorDetailScreen
import com.stillshelf.app.ui.screens.BookDetailScreen
import com.stillshelf.app.ui.screens.BrowsePlaceholderScreen
import com.stillshelf.app.ui.screens.BrowseSectionPlaceholderScreen
import com.stillshelf.app.ui.screens.CollectionDetailScreen
import com.stillshelf.app.ui.screens.CollectionsBrowseScreen
import com.stillshelf.app.ui.screens.CustomizePlaceholderScreen
import com.stillshelf.app.ui.screens.DownloadsPlaceholderScreen
import com.stillshelf.app.ui.screens.GenreDetailScreen
import com.stillshelf.app.ui.screens.GenresBrowseScreen
import com.stillshelf.app.ui.screens.HomePlaceholderScreen
import com.stillshelf.app.ui.screens.NarratorsBrowseScreen
import com.stillshelf.app.ui.screens.NarratorDetailScreen
import com.stillshelf.app.ui.screens.PlayerPlaceholderScreen
import com.stillshelf.app.ui.screens.PlaylistsBrowseScreen
import com.stillshelf.app.ui.screens.PlaylistDetailScreen
import com.stillshelf.app.ui.screens.SearchPlaceholderScreen
import com.stillshelf.app.ui.screens.ServersManagementScreen
import com.stillshelf.app.ui.screens.SeriesDetailScreen
import com.stillshelf.app.ui.screens.SeriesBrowseScreen
import com.stillshelf.app.ui.screens.SettingsPlaceholderScreen
import com.stillshelf.app.ui.screens.auth.AddServerRoute
import com.stillshelf.app.ui.screens.auth.LibraryPickerRoute
import com.stillshelf.app.ui.screens.auth.LoginRoute
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder

fun NavGraphBuilder.mainNavGraph() {
    navigation(
        route = GraphRoute.MAIN,
        startDestination = MainRoute.SHELL
    ) {
        composable(MainRoute.SHELL) {
            MainShell()
        }
    }
}

@Composable
private fun MainShell() {
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
    val miniPlayerState by miniPlayerViewModel.uiState.collectAsStateWithLifecycle()
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
        onMiniPlayerHomeClick = if (currentTab != MainTab.Home) onHomeClick else null,
        onMiniPlayerRewind15 = miniPlayerViewModel::onRewindClick,
        onMiniPlayerPlayPause = miniPlayerViewModel::onPlayPauseClick,
        onMiniPlayerClick = {
            tabsNavController.navigate(MainRoute.player()) {
                launchSingleTop = true
            }
        },
        showMiniPlayer = currentRoute?.startsWith(MainRoute.PLAYER) != true &&
            currentRoute != MainTab.Search.route &&
            currentRoute != MainTab.Settings.route &&
            currentRoute != MainRoute.SETTINGS &&
            currentRoute != MainRoute.SERVERS &&
            currentRoute?.startsWith("auth/") != true
    ) { paddingValues ->
        MainTabsNavHost(
            paddingValues = paddingValues,
            navController = tabsNavController,
            onHomeClick = onHomeClick
        )
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
    onHomeClick: () -> Unit
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
            HomePlaceholderScreen(
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
                    navController.navigate(MainRoute.player(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainTab.Browse.route) {
            BrowsePlaceholderScreen(
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
            SearchPlaceholderScreen(
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
            DownloadsPlaceholderScreen(
                onHomeClick = onHomeClick,
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainTab.Settings.route) {
            SettingsPlaceholderScreen(
                onBackClick = { navController.popBackStack() },
                onManageServers = {
                    navController.navigate(MainRoute.SERVERS) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(MainRoute.SETTINGS) {
            SettingsPlaceholderScreen(
                onBackClick = { navController.popBackStack() },
                onManageServers = {
                    navController.navigate(MainRoute.SERVERS) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = MainRoute.PLAYER_PATTERN,
            arguments = listOf(
                navArgument(MainRoute.PLAYER_BOOK_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 280)
                )
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 240)
                )
            },
            popEnterTransition = {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 280)
                )
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 240)
                )
            }
        ) {
            PlayerPlaceholderScreen(
                onBackClick = { navController.popBackStack() }
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
        composable(AuthRoute.ADD_SERVER) {
            AddServerRoute(
                onContinue = { serverName, baseUrl ->
                    navController.navigate(AuthRoute.loginRoute(serverName, baseUrl)) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
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
                    navController.navigate(AuthRoute.LIBRARY_PICKER) {
                        launchSingleTop = true
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
            CustomizePlaceholderScreen(
                onDone = { navController.popBackStack() },
                onHomeClick = onHomeClick
            )
        }

        composable(BrowseRoute.BOOKS) {
            BrowsePlaceholderScreen(
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
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
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
            BrowseSectionPlaceholderScreen(
                title = "Bookmarks",
                emptyMessage = "Bookmarks you add will appear here.",
                icon = Icons.Outlined.BookmarkBorder,
                onBackClick = { navController.popBackStack() },
                onHomeClick = onHomeClick
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
            DownloadsPlaceholderScreen(
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
                    navController.navigate(MainRoute.player(bookId)) {
                        launchSingleTop = true
                    }
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
