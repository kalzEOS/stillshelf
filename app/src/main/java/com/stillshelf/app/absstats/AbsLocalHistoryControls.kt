package com.stillshelf.app.absstats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun AbsLocalHistoryControls(viewModel: AbsLocalHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerKey by rememberSaveable { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val key = pickerKey
        if (uri != null && key != null) viewModel.export(uri, key)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = pickerKey
        if (uri != null && key != null) viewModel.preview(uri, key)
    }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var accountConfirmed by remember { mutableStateOf(false) }
    OutlinedCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Listening recorded on this device", style = MaterialTheme.typography.titleMedium)
            Text("Keep listening time for streamed podcasts and offline playback, even when Audiobookshelf cannot save it. Statistics combine it with server history without counting the same session twice.")
            Row {
                Text("Save local listening history", Modifier.weight(1f))
                Switch(checked = state.recording, onCheckedChange = viewModel::recording, enabled = !state.busy && state.key != null)
            }
            Text("${state.account} · ${state.count} saved sessions", style = MaterialTheme.typography.bodySmall)
            Text("Local history is separated by account and library. Recording and backup controls apply to this account across all its libraries.", style = MaterialTheme.typography.bodySmall)
            Text("Local additions are kept on this device and may not appear on the ABS website or other devices. Removing StillShelf can delete them. Export a backup before changing phones.", style = MaterialTheme.typography.bodySmall)
            Text("Turning this off keeps existing history and does not stop normal server reporting.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { pickerKey = state.key; export.launch("stillshelf-abs-history.json") }, enabled = !state.busy && state.key != null) { Text("Export Local History") }
            OutlinedButton(onClick = { pickerKey = state.key; accountConfirmed = false; import.launch(arrayOf("application/json", "application/octet-stream")) }, enabled = !state.busy && state.key != null) { Text("Import Local History") }
            TextButton(onClick = { deleting = true }, enabled = !state.busy && state.key != null) { Text("Clear Local History") }
            Text("Clearing local history removes only records saved on this device and cancels their pending submissions. History already saved by Audiobookshelf is kept and will still appear in your statistics.",
                style = MaterialTheme.typography.bodySmall)
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.key == null && !state.busy) OutlinedButton(onClick = viewModel::reload) { Text("Retry Connection") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (state.preview != null) AlertDialog(onDismissRequest = viewModel::cancelImport,
        title = { Text("Import local listening history?") },
        text = { Column {
            Text("${state.preview!!.records.size} sessions will be merged into ${state.account}, using your currently logged-in ABS account. Existing sessions are preserved. This does not upload listens or change playback positions.")
            Row { Checkbox(checked = accountConfirmed, onCheckedChange = { accountConfirmed = it }); Text("I confirm this backup belongs to this ABS account and server.") }
        } },
        confirmButton = { TextButton(onClick = viewModel::confirmImport, enabled = accountConfirmed && !state.busy) { Text("Import") } },
        dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Clear local history?") },
        text = { Text("Remove this account's history saved on this device and cancel its pending submissions. This cannot be undone without a backup. Audiobookshelf server history will not be deleted.") },
        confirmButton = { TextButton(onClick = { deleting = false; viewModel.clear() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}
