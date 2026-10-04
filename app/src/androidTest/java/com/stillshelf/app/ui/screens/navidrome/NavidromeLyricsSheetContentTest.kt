package com.stillshelf.app.ui.screens.navidrome

import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillshelf.app.core.model.NavidromeLyricsLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavidromeLyricsSheetContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun controlLyricsFrameClock() {
        composeTestRule.mainClock.autoAdvance = false
    }

    private fun runOnIdle(block: () -> Unit) {
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.runOnIdle(block)
    }

    @Test
    fun keepScreenOn_targetsLyricsDialog_andClearsOnToggleAndDismissal() {
        val enabled = mutableStateOf(false)
        val visible = mutableStateOf(true)
        lateinit var lyricsWindow: Window
        composeTestRule.setContent {
            MaterialTheme {
                if (visible.value) {
                    Dialog(onDismissRequest = { visible.value = false }) {
                        val window = (LocalView.current.parent as DialogWindowProvider).window
                        SideEffect { lyricsWindow = window }
                        TestLyricsContent(enabled.value) { visible.value = false }
                    }
                }
            }
        }

        runOnIdle {
            assertFalse(lyricsWindow.keepsScreenOn())
            enabled.value = true
        }
        runOnIdle {
            assertTrue(lyricsWindow.keepsScreenOn())
            assertFalse(composeTestRule.activity.window.keepsScreenOn())
            enabled.value = false
        }
        runOnIdle {
            assertFalse(lyricsWindow.keepsScreenOn())
            enabled.value = true
        }
        runOnIdle {
            assertTrue(lyricsWindow.keepsScreenOn())
            visible.value = false
        }
        runOnIdle {
            assertFalse(lyricsWindow.keepsScreenOn())
            assertFalse(composeTestRule.activity.window.keepsScreenOn())
            visible.value = true
        }
        runOnIdle {
            assertTrue(lyricsWindow.keepsScreenOn())
            visible.value = false
        }
        runOnIdle {
            assertFalse(lyricsWindow.keepsScreenOn())
        }
    }

    @Test
    fun keepScreenOn_withoutDialog_clearsActivityWindowOnDisposal() {
        val visible = mutableStateOf(true)
        composeTestRule.setContent {
            MaterialTheme {
                if (visible.value) {
                    TestLyricsContent(keepScreenOn = true) { visible.value = false }
                }
            }
        }
        runOnIdle {
            assertTrue(composeTestRule.activity.window.keepsScreenOn())
            visible.value = false
        }
        runOnIdle {
            assertFalse(composeTestRule.activity.window.keepsScreenOn())
        }
    }

    private fun Window.keepsScreenOn(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    @Composable
    private fun TestLyricsContent(keepScreenOn: Boolean, onDismiss: () -> Unit) {
        NavidromeLyricsSheetContent(
            uiState = syncedLyricsUiState(trackId = "track-1"),
            playbackPositionMs = 6_000,
            isPlaying = false,
            isRadio = false,
            keepScreenOn = keepScreenOn,
            durationMs = 24_000,
            coverUrl = null,
            onPrevious = {},
            onPlayPause = {},
            onNext = {},
            onDismiss = onDismiss
        )
    }

    @Test
    fun manualScrollShowsSyncButton_andSyncHidesIt_withoutReplacingLyricsList() {
        composeTestRule.setContent {
            MaterialTheme {
                NavidromeLyricsSheetContent(
                    uiState = syncedLyricsUiState(trackId = "track-1"),
                    playbackPositionMs = 6_000,
                    isPlaying = false,
                    isRadio = false,
                    keepScreenOn = false,
                    durationMs = 24_000,
                    coverUrl = null,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()

        composeTestRule.onNodeWithTag("navidromeLyricsList").performTouchInput {
            swipeUp()
        }
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeSyncLyricsButton")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()
        composeTestRule.onNodeWithTag("navidromeSyncLyricsButton").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeSyncLyricsButton")
                .fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()
    }

    @Test
    fun nextFromTransport_thenFirstManualScrollStillEntersManualMode() {
        composeTestRule.setContent {
            MaterialTheme {
                var trackId by remember { mutableStateOf("track-1") }
                NavidromeLyricsSheetContent(
                    uiState = syncedLyricsUiState(trackId = trackId),
                    playbackPositionMs = 6_000,
                    isPlaying = false,
                    isRadio = false,
                    keepScreenOn = false,
                    durationMs = 24_000,
                    coverUrl = null,
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = { trackId = "track-2" },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithTag("navidromeLyricsList").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeLyricsList")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("navidromeLyricsList").performTouchInput {
            swipeUp()
        }
        composeTestRule.mainClock.advanceTimeBy(500)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("navidromeSyncLyricsButton")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun syncedLyricsUiState(trackId: String): NavidromeLyricsUiState {
        return NavidromeLyricsUiState(
            isVisible = true,
            trackId = trackId,
            trackTitle = if (trackId == "track-1") "Test Track" else "Next Track",
            albumName = "Test Album",
            artistName = "Test Artist",
            lyrics = List(24) { index ->
                NavidromeLyricsLine(
                    timestampMs = index * 1_000,
                    text = "Line ${index + 1}"
                )
            },
            isSynced = true
        )
    }
}
