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
fun LibraryPickerRoute(
    onLibrarySelected: () -> Unit,
    onManageServers: () -> Unit,
    viewModel: LibraryPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is LibraryPickerEvent.NavigateToMain) {
                onLibrarySelected()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LibraryPickerScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onLibrarySelected = viewModel::onLibrarySelected,
        onManageServers = onManageServers
    )
}

@Composable
private fun LibraryPickerScreen(
    uiState: LibraryPickerUiState,
    snackbarHostState: SnackbarHostState,
    onLibrarySelected: (String) -> Unit,
    onManageServers: () -> Unit
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
                text = "Choose Library",
                style = MaterialTheme.typography.headlineMedium
            )

            if (uiState.libraries.isEmpty()) {
                Text(
                    text = "No libraries found for this server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.libraries) { library ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLibrarySelected(library.id) }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = library.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (library.id == uiState.activeLibraryId) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onManageServers
            ) {
                Text("Manage Servers")
            }
        }
    }
}
