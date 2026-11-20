package com.stillshelf.app.scrobbling

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ListeningConnectionManager(
    settings: ListeningSettings, statisticsOnly: Boolean, viewModel: ListeningSettingsViewModel, onDismiss: () -> Unit,
    initialId: String? = settings.connection?.revision
) {
    var selectedId by rememberSaveable { mutableStateOf(initialId) }
    val connection = settings.savedConnections.firstOrNull { it.revision == selectedId }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val networkBusy by viewModel.networkBusy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val awaitingAuthorization by viewModel.awaitingAuthorization.collectAsStateWithLifecycle()
    val savedEvent by viewModel.savedConnection.collectAsStateWithLifecycle()
    val initialEvent = remember { savedEvent }
    var editing by rememberSaveable { mutableStateOf(connection == null) }
    var confirmRemoval by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }
    LaunchedEffect(savedEvent) {
        if (savedEvent != null && savedEvent != initialEvent) {
            focus.clearFocus(); keyboard?.hide()
            selectedId = savedEvent!!.second; editing = false
        }
    }
    LaunchedEffect(settings.savedConnections, editing) {
        if (!editing && settings.savedConnections.none { it.revision == selectedId }) selectedId = settings.savedConnections.firstOrNull()?.revision
    }
    fun cancelEdit() {
        focus.clearFocus(); keyboard?.hide(); viewModel.cancelEditing()
        if (connection == null) {
            selectedId = settings.savedConnections.firstOrNull()?.revision
            if (selectedId == null) onDismiss() else editing = false
        } else editing = false
    }
    BackHandler { if (!busy) { if (editing && connection != null) cancelEdit() else onDismiss() } }
    // Use the activity viewport: older Compose dialog windows can report stale IME bounds
    // on Android 17, leaving scrolled form actions behind the keyboard.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (editing && connection != null) cancelEdit() else onDismiss() }, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Manage Connection", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
            }
            if (networkBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Use a saved service for statistics and optional direct scrobbling.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!editing && settings.savedConnections.size > 1) {
                    ListeningChoice("Connection", selectedId.orEmpty(), settings.savedConnections.associate {
                        it.revision to it.choiceLabel(settings.savedConnections)
                    }, !busy) { selectedId = it }
                }
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (connection == null) "Add Connection" else "Edit Configuration", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = ::cancelEdit, enabled = !busy) { Text("Cancel") }
                    }
                    Text("Saving a connection does not enable direct scrobbling.", style = MaterialTheme.typography.bodySmall)
                    key(connection) {
                        ListeningConnectionEditor(connection, busy, awaitingAuthorization,
                            onSave = { viewModel.saveManaged(it, connection?.revision, statisticsOnly && connection == null) },
                            onAuthorize = { viewModel.authorizeManaged(it, connection?.revision, statisticsOnly && connection == null) },
                            onFinishAuthorization = viewModel::finishAuthorization)
                    }
                } else if (connection != null) {
                    Text(connection.provider.label, style = MaterialTheme.typography.titleLarge)
                    if (connection.username.isNotBlank()) Text("Account: ${connection.username}")
                    Text("Saved securely", style = MaterialTheme.typography.bodySmall)
                    if (connection.serverUrl.isNotBlank()) Text(connection.serverUrl, style = MaterialTheme.typography.bodySmall)
                    Text(if (connection.hasSubmissionCredentials) "Statistics and scrobbling credentials saved."
                        else "Statistics only · Scrobbling credentials needed", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { editing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Edit Configuration") }
                    OutlinedButton(onClick = { viewModel.verifyConnection(connection.revision) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Verify Connection") }
                    Button(onClick = { confirmRemoval = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                        Text("Remove Connection")
                    }
                }
                if (!editing) OutlinedButton(onClick = {
                    viewModel.cancelEditing(); selectedId = null; editing = true
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Add Connection") }
            }
            SnackbarHost(snackbar, Modifier.fillMaxWidth())
        }
    }
    if (confirmRemoval) AlertDialog(onDismissRequest = { confirmRemoval = false }, title = { Text("Remove service connection?") },
        text = { Text("Remove the saved connection and credentials from StillShelf? Remote listening history will remain untouched.") },
        confirmButton = { Button(onClick = { confirmRemoval = false; viewModel.remove(connection?.revision); selectedId = null },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") } })
}
