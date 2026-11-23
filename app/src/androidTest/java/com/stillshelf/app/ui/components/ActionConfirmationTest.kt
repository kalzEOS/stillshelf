package com.stillshelf.app.ui.components

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelNeverMutatesAndConfirmRunsOnce() {
        var calls = 0
        compose.setContent { MaterialTheme {
            AppDropdownMenuItem(text = { Text("Remove Download") },
                confirmation = ActionConfirmation.RemoveDownload, onClick = { calls++ })
        } }
        compose.onNodeWithText("Remove Download").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Remove Download").performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Remove download?").assertDoesNotExist()
    }

    @Test fun changedPlayerTargetDismissesPendingConfirmation() {
        val target = mutableStateOf("first")
        var calls = 0
        compose.setContent { MaterialTheme {
            AppDropdownMenuItem(text = { Text("Mark as Finished") },
                confirmation = ActionConfirmation.ListeningProgress,
                confirmationTarget = target.value, onClick = { calls++ })
        } }
        compose.onNodeWithText("Mark as Finished").performClick()
        compose.runOnIdle { target.value = "second" }
        compose.onNodeWithText("Change listening progress?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun continueListeningRemovalExplainsServerResetAndCancelDoesNotReset() {
        var resets = 0
        compose.setContent { MaterialTheme {
            AppDropdownMenuItem(text = { Text("Remove from Continue Listening") },
                confirmation = ActionConfirmation.RemoveFromContinueListening,
                onClick = { resets++ })
        } }
        compose.onNodeWithText("Remove from Continue Listening").performClick()
        compose.onNodeWithText("resets its saved progress to 0%", substring = true).assertIsDisplayed()
        compose.onNodeWithText("syncs the reset to your Audiobookshelf server", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Remove from Continue Listening").performClick()
        compose.onNodeWithText("Reset and remove").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun nondestructiveDownloadRunsWithoutConfirmation() {
        var calls = 0
        compose.setContent { MaterialTheme {
            AppDropdownMenuItem(text = { Text("Download") }, onClick = { calls++ })
        } }
        compose.onNodeWithText("Download").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun confirmationSurvivesPopupAndCancelLeavesItsActionUntouched() {
        var calls = 0
        val expanded = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            AppDropdownMenu(expanded.value, { expanded.value = false }) {
                AppDropdownMenuItem(text = { Text("Reset progress") },
                    confirmation = ActionConfirmation.ResetProgress,
                    onClick = { calls++; expanded.value = false })
            }
        } }
        compose.onNodeWithText("Reset progress").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Reset progress").performClick()
        compose.onNodeWithText("Reset", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
