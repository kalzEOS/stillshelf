package com.stillshelf.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.ui.screens.BackendSelectionScreen
import com.stillshelf.app.ui.screens.navidrome.NavidromeAppRoute
import com.stillshelf.app.ui.screens.navidrome.NavidromeLoginRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

@Composable
fun RootNavGraph(
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val uiState by rootViewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        LoadingScreen()
        return
    }

    val desiredStartGraph = resolveDesiredRootStartGraph(
        selectedBackend = uiState.selectedBackend,
        hasNavidromeSession = uiState.hasNavidromeSession,
        hasActiveServer = uiState.hasActiveServer,
        hasActiveLibrary = uiState.hasActiveLibrary,
        hasPendingActiveLibrary = uiState.hasPendingActiveLibrary
    )
    var startGraph by remember(uiState.selectedBackend) {
        mutableStateOf(desiredStartGraph)
    }
    LaunchedEffect(
        desiredStartGraph,
        uiState.selectedBackend,
        uiState.hasActiveServer,
        uiState.hasPendingActiveLibrary
    ) {
        startGraph = resolveDisplayedRootStartGraph(
            previousStartGraph = startGraph,
            desiredStartGraph = desiredStartGraph,
            selectedBackend = uiState.selectedBackend,
            hasActiveServer = uiState.hasActiveServer,
            hasPendingActiveLibrary = uiState.hasPendingActiveLibrary
        )
    }
    val authStartDestination = resolveAuthStartDestination(
        hasAnyServer = uiState.hasAnyServer
    )

    key(startGraph) {
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route
        NavHost(
            navController = navController,
            startDestination = startGraph,
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
            composable(GraphRoute.BACKEND_SELECTOR) {
                LaunchedEffect(Unit) {
                    rootViewModel.stopPlaybackForBackendSelection()
                }
                BackendSelectionScreen(
                    hasAudiobookshelfSession = uiState.hasActiveServer,
                    hasNavidromeSession = uiState.hasNavidromeSession,
                    onBackendSelected = rootViewModel::selectBackend
                )
            }
            composable(GraphRoute.NAVIDROME_AUTH) {
                NavidromeLoginRoute(
                    onSwitchMode = rootViewModel::clearSelectedBackend,
                    onLoginSuccess = {
                        navController.navigate(GraphRoute.NAVIDROME) {
                            popUpTo(GraphRoute.NAVIDROME_AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            authNavGraph(
                navController = navController,
                startDestination = authStartDestination,
                hasAnyServer = uiState.hasAnyServer,
                onAuthCompleted = {
                    navController.navigate(GraphRoute.MAIN) {
                        popUpTo(GraphRoute.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onExitAuthFlow = rootViewModel::clearSelectedBackend
            )
            mainNavGraph()
            composable(GraphRoute.NAVIDROME) {
                NavidromeAppRoute(
                    onSwitchMode = rootViewModel::clearSelectedBackend
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

internal fun resolveDisplayedRootStartGraph(
    previousStartGraph: String,
    desiredStartGraph: String,
    selectedBackend: BackendProvider?,
    hasActiveServer: Boolean,
    hasPendingActiveLibrary: Boolean
): String {
    return when {
        desiredStartGraph == GraphRoute.MAIN -> GraphRoute.MAIN
        previousStartGraph == GraphRoute.MAIN &&
            selectedBackend == BackendProvider.AUDIOBOOKSHELF &&
            hasActiveServer &&
            hasPendingActiveLibrary -> GraphRoute.MAIN
        else -> desiredStartGraph
    }
}

internal fun resolveAuthStartDestination(hasAnyServer: Boolean): String {
    return if (hasAnyServer) {
        AuthRoute.SERVERS
    } else {
        AuthRoute.ADD_SERVER
    }
}

internal fun resolveDesiredRootStartGraph(
    selectedBackend: BackendProvider?,
    hasNavidromeSession: Boolean,
    hasActiveServer: Boolean,
    hasActiveLibrary: Boolean,
    hasPendingActiveLibrary: Boolean
): String {
    return when (selectedBackend) {
        null -> GraphRoute.BACKEND_SELECTOR
        BackendProvider.NAVIDROME -> if (hasNavidromeSession) {
            GraphRoute.NAVIDROME
        } else {
            GraphRoute.NAVIDROME_AUTH
        }
        BackendProvider.AUDIOBOOKSHELF -> if (
            hasActiveServer && (hasActiveLibrary || hasPendingActiveLibrary)
        ) {
            GraphRoute.MAIN
        } else {
            GraphRoute.AUTH
        }
    }
}
