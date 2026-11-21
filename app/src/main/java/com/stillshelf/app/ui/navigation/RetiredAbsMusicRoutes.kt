package com.stillshelf.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

/** Keep old saved back stacks restorable, without exposing any music UI or view models. */
internal fun NavGraphBuilder.retiredAbsMusicRoutes(navController: NavHostController) {
    listOf("main/settings/listening", "main/listening-statistics").forEach { retiredRoute ->
        composable(retiredRoute) {
            LaunchedEffect(Unit) {
                navController.navigate(MainRoute.SETTINGS) {
                    popUpTo(retiredRoute) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }
}
