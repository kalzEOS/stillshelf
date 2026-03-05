package com.stillshelf.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.core.model.Server

@Composable
fun ServersRoute(
    onAddServer: () -> Unit,
    onServerSelected: () -> Unit,
    onReauthenticate: (serverName: String, baseUrl: String) -> Unit,
    viewModel: ServersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ServersEvent.NavigateToLibraryPicker -> onServerSelected()
                is ServersEvent.NavigateToLogin -> onReauthenticate(event.serverName, event.baseUrl)
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    ServersScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAddServer = onAddServer,
        onServerSelected = viewModel::onServerSelected,
        onUpdateServer = viewModel::updateServer,
        onDeleteServer = viewModel::deleteServer
    )
}

@Composable
private fun ServersScreen(
    uiState: ServersUiState,
    snackbarHostState: SnackbarHostState,
    onAddServer: () -> Unit,
    onServerSelected: (String) -> Unit,
    onUpdateServer: (serverId: String, name: String, baseUrl: String) -> Unit,
    onDeleteServer: (serverId: String) -> Unit
) {
    var openMenuServerId by remember { mutableStateOf<String?>(null) }
    var editingServer by remember { mutableStateOf<Server?>(null) }
    var deletingServer by remember { mutableStateOf<Server?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var editingBaseUrl by rememberSaveable { mutableStateOf("") }
    val panelShape = RoundedCornerShape(22.dp)
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = panelShape,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "Servers",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose where to connect. You can keep multiple servers saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (uiState.isBusy) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(16.dp)
                            )
                            Text(
                                text = "Applying changes...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.servers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                    Text(
                        text = "No servers added yet.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "Add your Audiobookshelf server to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAddServer,
                    enabled = !uiState.isBusy
                ) {
                    Text("Add Server")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.servers) { server ->
                        val isActive = server.id == uiState.activeServerId
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = if (isActive) 3.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isActive) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        }
                                    )
                                    .clickable(enabled = !uiState.isBusy) { onServerSelected(server.id) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.padding(start = 10.dp)
                                    ) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = server.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (isActive) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    enabled = !uiState.isBusy,
                                    onClick = { openMenuServerId = server.id }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreVert,
                                        contentDescription = "Server actions"
                                    )
                                }
                                AppDropdownMenu(
                                    expanded = openMenuServerId == server.id,
                                    onDismissRequest = { openMenuServerId = null }
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            openMenuServerId = null
                                            editingServer = server
                                            editingName = server.name
                                            editingBaseUrl = server.baseUrl
                                        },
                                        enabled = !uiState.isBusy
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Remove") },
                                        onClick = {
                                            openMenuServerId = null
                                            deletingServer = server
                                        },
                                        enabled = !uiState.isBusy
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAddServer,
                    enabled = !uiState.isBusy
                ) {
                    Text("Add Server")
                }
            }
        }
    }

    val currentEditingServer = editingServer
    if (currentEditingServer != null) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isBusy) {
                    editingServer = null
                }
            },
            title = { Text("Edit server") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Server name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editingBaseUrl,
                        onValueChange = { editingBaseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isBusy &&
                        editingName.trim().isNotBlank() &&
                        editingBaseUrl.trim().isNotBlank(),
                    onClick = {
                        onUpdateServer(
                            currentEditingServer.id,
                            editingName.trim(),
                            editingBaseUrl.trim()
                        )
                        editingServer = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isBusy,
                    onClick = { editingServer = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val currentDeletingServer = deletingServer
    if (currentDeletingServer != null) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isBusy) {
                    deletingServer = null
                }
            },
            title = { Text("Remove server") },
            text = { Text("Remove \"${currentDeletingServer.name}\" from your saved servers?") },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isBusy,
                    onClick = {
                        onDeleteServer(currentDeletingServer.id)
                        deletingServer = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isBusy,
                    onClick = { deletingServer = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
