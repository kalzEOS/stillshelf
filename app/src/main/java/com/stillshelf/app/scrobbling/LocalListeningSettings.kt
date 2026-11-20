package com.stillshelf.app.scrobbling

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LocalListeningViewModel @Inject constructor(private val settings: ListeningSettingsStore,
    private val history: LocalListeningRepository, @ApplicationContext private val context: Context) : ViewModel() {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    val storageError = history.error
    fun clearMessage() { mutableMessage.value = null }
    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableMessage.value = "Could not save local history changes. Check available device storage and try again." }
            finally { mutableBusy.value = false }
        }
    }
    fun enabled(value: Boolean) = action { settings.update { it.copy(localRecordingEnabled = value) } }
    fun delete() = action { history.delete(); mutableMessage.value = "Local listening history deleted. External history is unchanged." }
    fun export(uri: Uri) = action {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Export destination unavailable")
            output.use { history.export(it) }
        }
        mutableMessage.value = "Local listening history exported."
    }
}

@Composable
internal fun LocalListeningSettings(settings: ListeningSettings, viewModel: LocalListeningViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    ListeningSettingsCard {
        Text("Local Scrobbling & Statistics", style = MaterialTheme.typography.titleMedium)
        ListeningToggle("Save listening history on this device", "No account required. Off by default.",
            settings.localRecordingEnabled, settings.loaded && !busy, viewModel::enabled)
        Text("Turning this off stops recording and keeps your saved history. External scrobbling and Navidrome reporting continue independently.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { export.launch("stillshelf-listening-history-${java.time.LocalDate.now()}.zip") },
            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export-local-history")) { Text("Export Local History") }
        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("delete-local-history")) { Text("Delete Local History") }
        Text("Stored only on this device. Export regularly: uninstalling StillShelf or losing your device may remove this history.", style = MaterialTheme.typography.bodySmall)
        Text("Exports include a complete StillShelf record and ListenBrainz-format listens. Direct ZIP upload compatibility is not guaranteed.", style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete local listening history?") },
        text = { Text("This permanently deletes the history saved on this device. Your connections and remote listening history will remain unchanged.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
