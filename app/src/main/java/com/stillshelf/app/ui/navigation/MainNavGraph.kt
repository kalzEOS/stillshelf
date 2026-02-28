package com.stillshelf.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.stillshelf.app.ui.screens.CollectionsBrowseScreen
import com.stillshelf.app.ui.screens.CustomizePlaceholderScreen
import com.stillshelf.app.ui.screens.DownloadsPlaceholderScreen
import com.stillshelf.app.ui.screens.GenreDetailScreen
import com.stillshelf.app.ui.screens.GenresBrowseScreen
import com.stillshelf.app.ui.screens.HomePlaceholderScreen
import com.stillshelf.app.ui.screens.NarratorsBrowseScreen
import com.stillshelf.app.ui.screens.NarratorDetailScreen
import com.stillshelf.app.ui.screens.PlayerPlaceholderScreen
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
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.MusicNote

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
    val tabsNavController = rememberNavController()
    val currentBackStackEntry by tabsNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentTab = MainTab.fromRoute(currentRoute)
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val miniPlayerState by miniPlayerViewModel.uiState.collectAsStateWithLifecycle()

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
            currentRoute != MainRoute.SERVERS
    ) { paddingValues ->
        MainTabsNavHost(
            paddingValues = paddingValues,
            navController = tabsNavController
        )
    }
}

@Composable
private fun MainTabsNavHost(
    paddingValues: PaddingValues,
    navController: androidx.navigation.NavHostController
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
        composable(MainTab.Downloads.route) { DownloadsPlaceholderScreen() }
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
                    animationSpec = tween(durationMillis = 280)
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
                    animationSpec = tween(durationMillis = 280)
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
                }
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
            CustomizePlaceholderScreen(onDone = { navController.popBackStack() })
        }

        composable(BrowseRoute.BOOKS) {
            BrowsePlaceholderScreen(
                onBackClick = { navController.popBackStack() },
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
                onSeriesClick = { seriesName ->
                    navController.navigate(DetailRoute.series(seriesName)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(BrowseRoute.COLLECTIONS) {
            CollectionsBrowseScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(BrowseRoute.GENRES) {
            GenresBrowseScreen(
                onBackClick = { navController.popBackStack() },
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
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(BrowseRoute.PLAYLISTS) {
            BrowseSectionPlaceholderScreen(
                title = "Playlists",
                emptyMessage = "Playlists you create will appear here.",
                icon = Icons.Outlined.MusicNote,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(BrowseRoute.DOWNLOADED) {
            DownloadsPlaceholderScreen(
                onBackClick = { navController.popBackStack() }
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
                onBookClick = { bookId ->
                    navController.navigate(DetailRoute.book(bookId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
