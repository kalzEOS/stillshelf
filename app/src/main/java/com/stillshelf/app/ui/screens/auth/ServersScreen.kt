package com.stillshelf.app.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ServersRoute(
    onAddServer: () -> Unit,
    onServerSelected: () -> Unit,
    viewModel: ServersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ServersEvent.NavigateToLibraryPicker) {
                onServerSelected()
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
        onServerSelected = viewModel::onServerSelected
    )
}

@Composable
private fun ServersScreen(
    uiState: ServersUiState,
    snackbarHostState: SnackbarHostState,
    onAddServer: () -> Unit,
    onServerSelected: (String) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Servers",
                style = MaterialTheme.typography.headlineMedium
            )

            if (uiState.servers.isEmpty()) {
                Text(
                    text = "No servers added yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.servers) { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onServerSelected(server.id) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = server.baseUrl,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (server.id == uiState.activeServerId) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddServer
            ) {
                Text("Add Server")
            }
        }
    }
}
