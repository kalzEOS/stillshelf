package com.stillshelf.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.playback.controller.PlaybackUiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke tests for PlayerScreen.
 *
 * These tests verify that the screen renders without crashing for a given UI state.
 * They do NOT exercise navigation or deep interaction — see the ViewModel unit tests
 * for that coverage.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerScreen_withNoActiveBook_rendersWithoutCrash() {
        val viewModel = stubPlayerViewModel()
        val collectionVm = stubCollectionPickerViewModel()
        val appearanceVm = stubAppearanceViewModel()

        composeTestRule.setContent {
            PlayerScreen(
                viewModel = viewModel,
                collectionPickerViewModel = collectionVm,
                appearanceViewModel = appearanceVm
            )
        }

        composeTestRule.waitForIdle()
        // If we reach here, the composable rendered without throwing an exception.
    }

    @Test
    fun playerScreen_withPreviewBook_showsBookTitle() {
        val book = testBook(title = "Dune")
        val viewModel = stubPlayerViewModel(
            previewItem = ContinueListeningItem(
                book = book,
                progressPercent = 0.25,
                currentTimeSeconds = 900.0
            )
        )

        composeTestRule.setContent {
            PlayerScreen(
                viewModel = viewModel,
                collectionPickerViewModel = stubCollectionPickerViewModel(),
                appearanceViewModel = stubAppearanceViewModel()
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Dune").assertIsDisplayed()
    }

    @Test
    fun playerScreen_withPlayingBook_showsBookTitle() {
        val book = testBook(title = "The Hobbit")
        val viewModel = stubPlayerViewModel(
            playbackUiState = PlaybackUiState(book = book, isPlaying = true)
        )

        composeTestRule.setContent {
            PlayerScreen(
                viewModel = viewModel,
                collectionPickerViewModel = stubCollectionPickerViewModel(),
                appearanceViewModel = stubAppearanceViewModel()
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("The Hobbit").assertIsDisplayed()
    }

    // region stubs

    private fun stubPlayerViewModel(
        playbackUiState: PlaybackUiState = PlaybackUiState(),
        previewItem: ContinueListeningItem? = null
    ): PlayerViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(playbackUiState)
        every { this@mockk.previewItem } returns MutableStateFlow(previewItem)
        every { chapters } returns MutableStateFlow(emptyList<BookChapter>())
        every { bookmarks } returns MutableStateFlow(emptyList<BookBookmark>())
        every { actionMessage } returns MutableStateFlow(null)
        every { controlPrefs } returns MutableStateFlow(PlayerControlPrefs())
        every { downloadedBookKeys } returns MutableStateFlow(emptySet())
        every { downloadProgressPercent } returns MutableStateFlow(null)
        every { markFinishedUndoEvents } returns MutableSharedFlow()
    }

    private fun stubCollectionPickerViewModel(): CollectionPickerViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(CollectionPickerUiState())
    }

    private fun stubAppearanceViewModel(): AppAppearanceViewModel = mockk(relaxed = true) {
        every { uiState } returns MutableStateFlow(AppAppearanceUiState())
    }

    private fun testBook(
        id: String = "book1",
        title: String = "Test Book"
    ) = BookSummary(
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
