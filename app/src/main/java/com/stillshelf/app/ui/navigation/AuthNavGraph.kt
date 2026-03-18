package com.stillshelf.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.stillshelf.app.ui.screens.auth.AddServerRoute
import com.stillshelf.app.ui.screens.auth.LibraryPickerRoute
import com.stillshelf.app.ui.screens.auth.LoginRoute
import com.stillshelf.app.ui.screens.auth.ServersRoute

fun NavGraphBuilder.authNavGraph(
    navController: NavHostController,
    startDestination: String,
    hasAnyServer: Boolean,
    onAuthCompleted: () -> Unit,
    onExitAuthFlow: () -> Unit
) {
    navigation(
        route = GraphRoute.AUTH,
        startDestination = startDestination
    ) {
        composable(AuthRoute.SERVERS) {
            ServersRoute(
                onAddServer = { navController.navigate(AuthRoute.ADD_SERVER) },
                onServerSelected = { navController.navigate(AuthRoute.LIBRARY_PICKER) },
                onReauthenticate = { serverName, baseUrl ->
                    navController.navigate(AuthRoute.loginRoute(serverName, baseUrl))
                },
                onBackToBackendSelection = onExitAuthFlow
            )
        }

        composable(AuthRoute.ADD_SERVER) {
            AddServerRoute(
                onContinue = { serverName, baseUrl ->
                    navController.navigate(AuthRoute.loginRoute(serverName, baseUrl))
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        onExitAuthFlow()
                    }
                },
                showBackButton = true
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
                onBack = {
                    if (!navController.popBackStack()) {
                        onExitAuthFlow()
                    }
                },
                onLoginSuccess = { navController.navigate(AuthRoute.LIBRARY_PICKER) }
            )
        }

        composable(AuthRoute.LIBRARY_PICKER) {
            LibraryPickerRoute(
                onLibrarySelected = onAuthCompleted,
                onManageServers = {
                    if (!navController.popBackStack(AuthRoute.SERVERS, inclusive = false)) {
                        val fallbackRoute = if (hasAnyServer) {
                            AuthRoute.SERVERS
                        } else {
                            AuthRoute.ADD_SERVER
                        }
                        navController.navigate(fallbackRoute) {
                            launchSingleTop = true
                        }
                    }
                },
                onBackToBackendSelection = onExitAuthFlow
            )
        }
    }
}
