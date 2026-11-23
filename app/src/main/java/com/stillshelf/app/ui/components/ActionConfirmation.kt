package com.stillshelf.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class ActionConfirmation(val title: String, val message: String, val confirmLabel: String) {
    companion object {
        val ListeningProgress = ActionConfirmation("Change listening progress?",
            "This changes the saved finished or played status and may change the playback position.", "Continue")
        val ResetProgress = ActionConfirmation("Reset progress?",
            "This will set the episode back to 0% and stop playback if it is playing.", "Reset")
        val RemoveDownload = ActionConfirmation("Remove download?",
            "This removes the downloaded files from this device. You will need a connection to stream or download them again.", "Remove")
        val RemoveFromList = ActionConfirmation("Remove from this list?",
            "This removes the item from this list. It does not delete it from your library.", "Remove")
        val RemoveFromContinueListening = ActionConfirmation("Remove from Continue Listening?",
            "Removing this book resets its saved progress to 0% and syncs the reset to your Audiobookshelf server. The book stays in your library.",
            "Reset and remove")
        val ClearSearch = ActionConfirmation("Clear recent searches?",
            "This removes your saved recent searches.", "Clear")
    }
}

/** Keep the selected action until confirmation; a changed target cancels the request. */
@Composable
fun rememberConfirmedAction(
    confirmation: ActionConfirmation?,
    targetKey: Any? = Unit,
    onConfirmed: () -> Unit
): () -> Unit {
    var pending by remember(targetKey) { mutableStateOf<Pair<ActionConfirmation, () -> Unit>?>(null) }
    pending?.let { (details, action) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(details.title) },
            text = { Text(details.message) },
            confirmButton = {
                TextButton(onClick = {
                    if (pending != null) {
                        pending = null
                        action()
                    }
                }) { Text(details.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
    return {
        if (confirmation == null) onConfirmed()
        else pending = confirmation to onConfirmed
    }
}
