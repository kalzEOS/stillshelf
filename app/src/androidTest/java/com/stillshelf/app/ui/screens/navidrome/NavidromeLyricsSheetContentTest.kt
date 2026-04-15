package com.stillshelf.app.ui.screens.navidrome

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillshelf.app.core.model.NavidromeLyricsLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavidromeLyricsSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualScrollShowsSyncButton_andSyncHidesIt_withoutReplacingLyricsList() {
        composeTestRule.setContent {
            MaterialTheme {
                NavidromeLyricsSheetContent(
                    uiState = NavidromeLyricsUiState(
                        isVisible = true,
                        trackId = "track-1",
                        trackTitle = "Test Track",
                        albumName = "Test Album",
                        artistName = "Test Artist",
                        lyrics = List(24) { index ->
                            NavidromeLyricsLine(
                                timestampMs = index * 1_000,
                                text = "Line ${index + 1}"
                            )
                        },
                        isSynced = true
                    ),
                    playbackPositionMs = 6_000,
                    isPlaying = false,
                    isRadio = false,
                    durationMs = 24_000,
                    coverUrl = null,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()

        composeTestRule.onNodeWithTag("navidromeLyricsList").performTouchInput {
            swipeUp()
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeSyncLyricsButton")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("navidromeSyncLyricsButton").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeSyncLyricsButton")
                .fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()
    }
}
