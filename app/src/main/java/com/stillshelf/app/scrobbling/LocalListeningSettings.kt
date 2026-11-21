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
import kotlinx.coroutines.ensureActive
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
        mutableMessage.value = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: LocalHistoryImportException) { mutableMessage.value = e.message }
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
    fun importBackup(uri: Uri) = action {
        val result = withContext(Dispatchers.IO) {
            val file = java.io.File.createTempFile("local-history-import-", ".zip", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Backup unavailable")
                input.use { source -> file.outputStream().use { target ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val size = source.read(buffer)
                        if (size < 0) break
                        total += size
                        if (total > 256L * 1024 * 1024) throw LocalHistoryImportException("This backup is too large to import. Existing history is unchanged.")
                        target.write(buffer, 0, size)
                    }
                } }
                history.importArchive(file)
            } finally { file.delete() }
        }
        mutableMessage.value = "Import complete: ${result.added} listens added, ${result.skipped} already saved."
    }
}

@Composable
internal fun LocalListeningSettings(settings: ListeningSettings, viewModel: LocalListeningViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
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
        OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
            enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("import-local-history")) { Text("Import Local History") }
        OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("delete-local-history")) { Text("Delete Local History") }
        Text("Stored only on this device. Export a backup before uninstalling or changing phones, then use Import Local History to restore it.", style = MaterialTheme.typography.bodySmall)
        Text("Exports include a complete StillShelf record and ListenBrainz-format listens. Direct ZIP upload compatibility is not guaranteed.", style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (pendingImport != null) AlertDialog(onDismissRequest = { pendingImport = null },
        title = { Text("Import local listening history?") },
        text = { Text("Add listens from a StillShelf backup ZIP to the history on this device. Existing listens are kept and duplicates are skipped. Nothing is sent to connected services or enabled automatically.") },
        confirmButton = { TextButton(onClick = { pendingImport?.let(viewModel::importBackup); pendingImport = null }, enabled = !busy) { Text("Import") } },
        dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete local listening history?") },
        text = { Text("This permanently deletes the history saved on this device. Your connections and remote listening history will remain unchanged.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
