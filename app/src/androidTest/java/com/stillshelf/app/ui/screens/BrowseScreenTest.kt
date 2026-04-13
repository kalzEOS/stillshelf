package com.stillshelf.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillshelf.app.core.model.BookSummary
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke tests for BrowseScreen (backed by BooksBrowseViewModel).
 *
 * These tests verify that the screen renders without crashing for a given UI state.
 * See BooksBrowseViewModelTest for unit-level behavior coverage.
 */
@RunWith(AndroidJUnit4::class)
class BrowseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun browseScreen_withEmptyBooks_rendersWithoutCrash() {
        val viewModel = stubBrowseViewModel(books = emptyList())
        val collectionVm = stubCollectionPickerViewModel()

        composeTestRule.setContent {
            BrowseScreen(
                onBookClick = {},
                viewModel = viewModel,
                collectionPickerViewModel = collectionVm
            )
        }

        composeTestRule.waitForIdle()
        // Reaching here means no crash during rendering.
    }

    @Test
    fun browseScreen_withBooks_showsBookTitles() {
        val books = listOf(
            testBook(id = "b1", title = "Dune"),
            testBook(id = "b2", title = "Foundation")
        )
        val viewModel = stubBrowseViewModel(books = books)

        composeTestRule.setContent {
            BrowseScreen(
                onBookClick = {},
                viewModel = viewModel,
                collectionPickerViewModel = stubCollectionPickerViewModel()
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dune").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foundation").assertIsDisplayed()
    }

    @Test
    fun browseScreen_withLoadingState_rendersWithoutCrash() {
        val viewModel = stubBrowseViewModel(
            state = BooksBrowseUiState(
                isBootstrapping = false,
                isLoading = true,
                books = emptyList()
            )
        )

        composeTestRule.setContent {
            BrowseScreen(
                onBookClick = {},
                viewModel = viewModel,
                collectionPickerViewModel = stubCollectionPickerViewModel()
            )
        }

        composeTestRule.waitForIdle()
    }

    // region stubs

    private fun stubBrowseViewModel(
        books: List<BookSummary> = emptyList(),
        state: BooksBrowseUiState? = null
    ): BooksBrowseViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(
            state ?: BooksBrowseUiState(
                isBootstrapping = false,
                isLoading = false,
                books = books
            )
        )
    }

    private fun stubCollectionPickerViewModel(): CollectionPickerViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(CollectionPickerUiState())
    }

    private fun testBook(id: String, title: String) = BookSummary(
        id = id,
        libraryId = "lib1",
        title = title,
        authorName = "Author",
        narratorName = null,
        durationSeconds = 3600.0,
        coverUrl = null
    )

    // endregion
}
