package com.stillshelf.app.scrobbling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ListeningSettingsScreen(onBack: () -> Unit, onHome: () -> Unit = {}, viewModel: ListeningSettingsViewModel = hiltViewModel(),
    localViewModel: LocalListeningViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val authorizationUrl by viewModel.authorizationUrl.collectAsStateWithLifecycle()
    val directStatus by viewModel.directSubmissionStatus.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var confirmDirect by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var manageOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var statisticsSetup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var manageId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(message, manageOpen) {
        if (!manageOpen) message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(authorizationUrl) {
        authorizationUrl?.let { runCatching { uriHandler.openUri(it) }; viewModel.authorizationOpened() }
    }
    if (manageOpen) {
        ListeningConnectionManager(
            settings = settings, statisticsOnly = statisticsSetup, initialId = manageId, viewModel = viewModel,
            onDismiss = {
                focusManager.clearFocus(); keyboard?.hide(); viewModel.cancelEditing()
                manageOpen = false; statisticsSetup = false
            })
        return
    }
    Box(Modifier.fillMaxSize()) {
        com.stillshelf.app.ui.screens.navidrome.StandardTopScreen(
            title = "Scrobbling & Statistics", onBack = onBack, onHome = onHome,
            imePaddingEnabled = true, lazyColumnModifier = Modifier.testTag("listening-settings-list")
        ) {
            item(key = "listening-statistics-settings") {
                if (!settings.loaded) Text(message ?: "Loading listening settings…")
                else ListeningSettingsCard {
                    Text("Listening Statistics", style = MaterialTheme.typography.titleMedium)
                    Text("Choose where StillShelf gets your listening statistics.", style = MaterialTheme.typography.bodyMedium)
                    ListeningToggle("Show Listening Statistics", "Display your listening history on Home", settings.showStatistics, !busy) { enabled ->
                        viewModel.update { it.copy(showStatistics = enabled) }
                    }
                    val connections = settings.savedConnections
                    val sources = linkedMapOf("navidrome" to "Navidrome history").apply {
                        if (settings.localRecordingEnabled) put("local", "Local Listening History")
                        connections.forEach { put(it.revision, it.choiceLabel(connections)) }
                        if (settings.statisticsSource == StatisticsSource.EXTERNAL && settings.connection == null) put("unavailable", "Connection unavailable")
                        put("add", "Add Connection…")
                    }
                    ListeningChoice("Statistics from", when (settings.statisticsSource) {
                        StatisticsSource.NAVIDROME -> "navidrome"
                        StatisticsSource.LOCAL -> "local"
                        StatisticsSource.EXTERNAL -> settings.connection?.revision ?: "unavailable"
                    }, sources, !busy) { source ->
                        when (source) {
                            "add", "unavailable" -> { statisticsSetup = true; manageId = null; manageOpen = true }
                            "navidrome" -> viewModel.update { it.copy(statisticsSource = StatisticsSource.NAVIDROME) }
                            "local" -> viewModel.update { it.copy(statisticsSource = StatisticsSource.LOCAL) }
                            else -> viewModel.update { current -> current.copy(statisticsSource = StatisticsSource.EXTERNAL,
                                connection = current.savedConnections.firstOrNull { it.revision == source }) }
                        }
                    }
                    Text("Use a connected service or your server's listening history", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        statisticsSetup = connections.isEmpty()
                        manageId = settings.connection?.revision ?: settings.sendingConnection?.revision ?: connections.firstOrNull()?.revision
                        manageOpen = true
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("manage-statistics-connection")) { Text("Manage Connection") }
                    androidx.compose.material3.HorizontalDivider()
                    Text("Direct Scrobbling (Optional)", style = MaterialTheme.typography.titleMedium)
                    ListeningToggle("Send scrobbles from StillShelf", "Off by default. Navidrome can handle scrobbling instead.", settings.canSubmit, !busy) { enabled ->
                        if (!enabled) viewModel.setScrobblingEnabled(false)
                        else {
                            val sender = settings.sendingConnection ?: connections.singleOrNull()
                            if (sender == null && connections.size > 1) scope.launch { snackbar.showSnackbar("Choose a connection in Send to before enabling direct scrobbling.") }
                            else if (sender?.hasSubmissionCredentials == true) confirmDirect = sender.revision
                            else { statisticsSetup = connections.isEmpty(); manageId = sender?.revision; manageOpen = true }
                        }
                    }
                    ListeningChoice("Send to", settings.sendingConnection?.revision ?: "choose",
                        linkedMapOf("choose" to "Choose a connection").apply {
                            connections.forEach { put(it.revision, it.choiceLabel(connections)) }
                            put("add", "Add Connection…")
                        }, !busy) { id ->
                        if (id == "add") { manageId = null; statisticsSetup = false; manageOpen = true }
                        val selected = connections.firstOrNull { it.revision == id }
                        if (selected != null) {
                            if (settings.scrobblingEnabled) {
                                if (selected.hasSubmissionCredentials) confirmDirect = id
                                else { manageId = id; statisticsSetup = false; manageOpen = true }
                            } else viewModel.update { it.copy(sendingConnectionId = id, multipleConnections = true) }
                        }
                    }
                    OutlinedButton(onClick = {
                        statisticsSetup = false
                        manageId = settings.sendingConnection?.revision
                        manageOpen = true
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("manage-sending-connection")) { Text("Manage Connection") }
                    Text(when {
                        settings.sendingConnection == null -> "Choose a saved connection in Send to, or add one in Manage Connection."
                        settings.sendingConnection?.hasSubmissionCredentials != true -> "Add scrobbling credentials in Manage Connection before enabling submissions."
                        settings.canSubmit -> "Direct scrobbling enabled for ${settings.sendingConnection!!.provider.label}."
                        else -> "Connection saved. Enable direct scrobbling to send listens."
                    }, style = MaterialTheme.typography.bodySmall)
                    directStatus?.takeIf { it.first == settings.sendingConnection?.revision }?.let {
                        Text(it.second, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("direct-submission-status"))
                    }
                }
            }
            item(key = "local-listening-settings") {
                if (settings.loaded) LocalListeningSettings(settings, localViewModel)
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp))
    }
    if (confirmDirect != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDirect = null }, title = { Text("Avoid Duplicate Scrobbles") },
        text = { Text(DuplicateScrobblesMessage) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            confirmDirect?.let(viewModel::enableDirect)
            confirmDirect = null
        }) { Text("Continue") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDirect = null }) { Text("Cancel") } }
    )
}

@Composable
internal fun ListeningConnectionEditor(
    saved: ListeningConnection?, busy: Boolean, awaitingAuthorization: Boolean,
    onSave: (ListeningConnection) -> Unit, onAuthorize: (ListeningConnection) -> Unit, onFinishAuthorization: () -> Unit
) {
    var provider by remember { mutableStateOf(saved?.provider ?: ListeningProvider.KOITO) }
    ListeningChoice("Service", provider, ListeningProvider.entries.associateWith { it.label }, !busy) { provider = it }
    key(provider) {
        val old = saved?.takeIf { it.provider == provider }
        var url by remember { mutableStateOf(old?.serverUrl.orEmpty()) }
        var username by remember { mutableStateOf(old?.username.orEmpty()) }
        // Secrets are never saved in instance-state bundles or echoed back into input fields.
        var token by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        var apiSecret by remember { mutableStateOf("") }
        if (provider == ListeningProvider.KOITO || provider == ListeningProvider.MALOJA) {
            OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, singleLine = true, enabled = !busy,
                supportingText = { Text("Use the server root URL.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        }
        if (provider == ListeningProvider.LAST_FM || provider == ListeningProvider.LISTEN_BRAINZ) {
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        if (provider == ListeningProvider.LAST_FM) {
            ListeningSecretField("API key", apiKey, { apiKey = it }, old?.apiKey?.isNotBlank() == true, !busy)
            ListeningSecretField("Shared secret (for scrobbling)", apiSecret, { apiSecret = it }, old?.apiSecret?.isNotBlank() == true, !busy)
            Text(if (old?.token?.isNotBlank() == true) "Last.fm authorization saved." else
                "Statistics need an API key and username. To scrobble, also enter your API shared secret and authorize in your browser.", style = MaterialTheme.typography.bodySmall)
        } else {
            ListeningSecretField(if (provider == ListeningProvider.LISTEN_BRAINZ) "API token (for scrobbling)" else "API token",
                token, { token = it }, old?.token?.isNotBlank() == true, !busy)
            Text("Token optional for public statistics.", style = MaterialTheme.typography.bodySmall)
        }
        fun connection(): ListeningConnection {
            val keyValue = apiKey.ifBlank { old?.apiKey.orEmpty() }
            val secretValue = apiSecret.ifBlank { old?.apiSecret.orEmpty() }
            val retainSession = provider != ListeningProvider.LAST_FM || (old != null && username.trim() == old.username &&
                keyValue == old.apiKey && secretValue == old.apiSecret)
            return ListeningConnection(provider, url.trim().trimEnd('/'), username.trim(),
                token.ifBlank { if (retainSession) old?.token.orEmpty() else "" }, keyValue, secretValue,
                allowSubmissions = old?.allowSubmissions ?: true)
        }
        Button(onClick = { onSave(connection()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save Configuration") }
        if (provider == ListeningProvider.LAST_FM) {
            OutlinedButton(onClick = { onAuthorize(connection()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Authorize Last.fm in Browser") }
            if (awaitingAuthorization) Button(onClick = onFinishAuthorization, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Finish Authorization") }
        }
    }
}

@Composable
private fun ListeningSecretField(label: String, value: String, change: (String) -> Unit, saved: Boolean, enabled: Boolean) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        supportingText = if (saved) ({ Text("Saved securely. Leave blank to keep the saved value.") }) else null)
}

@Composable
internal fun ListeningSettingsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
internal fun ListeningToggle(title: String, description: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, change, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun <T> ListeningChoice(label: String, selected: T, choices: Map<T, String>, enabled: Boolean, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        // Only the field is the popup anchor, rather than the containing card or scroll column.
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }.testTag("choice-$label")) {
                Text(choices[selected].orEmpty(), modifier = Modifier.weight(1f))
                Text("▾", modifier = Modifier.padding(start = 8.dp))
            }
            AppDropdownMenu(expanded, onDismissRequest = { expanded = false },
                containerColorOverride = MaterialTheme.colorScheme.surface,
                preferBelowField = true,
                modifier = Modifier.width(with(density) { widthPx.toDp() }).heightIn(max = 280.dp).testTag("menu-$label")) {
                choices.forEach { (value, title) -> AppDropdownMenuItem(text = { Text(title) },
                    modifier = Modifier.testTag("option-$title"), onClick = { expanded = false; change(value) }) }
            }
        }
    }
}
