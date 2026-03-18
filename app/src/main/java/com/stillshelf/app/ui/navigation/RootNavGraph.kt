package com.stillshelf.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.ui.screens.BackendSelectionScreen
import com.stillshelf.app.ui.screens.navidrome.NavidromeAppRoute
import com.stillshelf.app.ui.screens.navidrome.NavidromeLoginRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

@Composable
fun RootNavGraph(
    onHomeScreenReached: () -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val uiState by rootViewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        LoadingScreen()
        return
    }

    val startGraph = when (uiState.selectedBackend) {
        null -> GraphRoute.BACKEND_SELECTOR
        BackendProvider.NAVIDROME -> if (uiState.hasNavidromeSession) {
            GraphRoute.NAVIDROME
        } else {
            GraphRoute.NAVIDROME_AUTH
        }
        BackendProvider.AUDIOBOOKSHELF -> if (uiState.hasActiveServer && uiState.hasActiveLibrary) {
            GraphRoute.MAIN
        } else {
            GraphRoute.AUTH
        }
    }
    val authStartDestination = when {
        uiState.hasActiveServer -> AuthRoute.LIBRARY_PICKER
        uiState.hasAnyServer -> AuthRoute.SERVERS
        else -> AuthRoute.ADD_SERVER
    }

    key(startGraph, authStartDestination) {
        val navController = rememberNavController()
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
                BackendSelectionScreen(
                    hasAudiobookshelfSession = uiState.hasActiveServer,
                    onBackendSelected = rootViewModel::selectBackend
                )
            }
            composable(GraphRoute.NAVIDROME_AUTH) {
                NavidromeLoginRoute(
                    onSwitchMode = rootViewModel::clearSelectedBackend
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
            mainNavGraph(
                onHomeScreenReached = onHomeScreenReached
            )
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
