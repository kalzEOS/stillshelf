package com.stillshelf.app.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerBookNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun differentOpenBookGetsFreshDetailIdentityAndBackStillWorks() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = "start") {
                composable("start") {
                    Button(onClick = { nav.navigate(DetailRoute.book("browsing")) }) { Text("Browse other book") }
                }
                composable(DetailRoute.BOOK_PATTERN,
                    arguments = listOf(navArgument(DetailRoute.BOOK_ID_ARG) { type = NavType.StringType })) { entry ->
                    val model: BookIdentity = viewModel(viewModelStoreOwner = entry, factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            BookIdentity(entry.arguments!!.getString(DetailRoute.BOOK_ID_ARG)!!) as T
                    })
                    Text("Detail identity: ${model.bookId}")
                }
            }
        }
        compose.onNodeWithText("Browse other book").performClick()
        compose.onNodeWithText("Detail identity: browsing").assertExists()
        compose.runOnIdle { nav.navigateToPlayingBook("playing") }
        compose.waitUntil { nav.currentBackStackEntry?.arguments?.getString(DetailRoute.BOOK_ID_ARG) == "playing" }
        compose.onNodeWithText("Detail identity: playing").assertExists()
        compose.runOnIdle {
            val entryId = nav.currentBackStackEntry!!.id
            nav.navigateToPlayingBook("playing")
            assertEquals(entryId, nav.currentBackStackEntry!!.id)
            nav.popBackStack()
        }
        compose.onNodeWithText("Detail identity: browsing").assertExists()
    }

    class BookIdentity(val bookId: String) : ViewModel()
}
