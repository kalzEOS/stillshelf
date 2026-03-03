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

    val navController = rememberNavController()
    val startGraph = if (uiState.hasActiveServer && uiState.hasActiveLibrary) {
        GraphRoute.MAIN
    } else {
        GraphRoute.AUTH
    }
    val authStartDestination = if (uiState.hasActiveServer) {
        AuthRoute.LIBRARY_PICKER
    } else {
        AuthRoute.ADD_SERVER
    }

    key(startGraph) {
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
            authNavGraph(
                navController = navController,
                startDestination = authStartDestination,
                hasAnyServer = uiState.hasAnyServer,
                onAuthCompleted = {
                    navController.navigate(GraphRoute.MAIN) {
                        popUpTo(GraphRoute.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
            mainNavGraph()
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
