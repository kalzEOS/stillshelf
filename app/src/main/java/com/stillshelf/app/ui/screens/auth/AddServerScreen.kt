package com.stillshelf.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddServerRoute(
    onContinue: (serverName: String, baseUrl: String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AddServerScreen(
        uiState = uiState,
        onServerNameChange = viewModel::onServerNameChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onTestConnection = viewModel::onTestConnectionClick,
        onContinue = onContinue,
        onBack = onBack
    )
}

@Composable
private fun AddServerScreen(
    uiState: AddServerUiState,
    onServerNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onContinue: (serverName: String, baseUrl: String) -> Unit,
    onBack: () -> Unit
) {
    var showInsecureHttpWarning by remember { mutableStateOf(false) }
    val trimmedBaseUrl = uiState.baseUrl.trim()
    val trimmedServerName = uiState.serverName.trim()

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Server",
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = uiState.serverName,
                    onValueChange = onServerNameChange,
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.serverNameError != null,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                uiState.serverNameError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        enabled = uiState.baseUrl.isNotBlank() && !uiState.isTestingConnection,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(vertical = 1.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Test Connection")
                        }
                    }

                    Button(
                        onClick = {
                            if (isHttpUrl(trimmedBaseUrl)) {
                                showInsecureHttpWarning = true
                            } else {
                                onContinue(trimmedServerName, trimmedBaseUrl)
                            }
                        },
                        enabled = uiState.canContinue,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Continue")
                    }
                }

                uiState.connectionMessage?.let { message ->
                    Text(
                        text = message,
                        color = if (uiState.connectionSuccess == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            }
        }

        if (showInsecureHttpWarning) {
            AlertDialog(
                onDismissRequest = { showInsecureHttpWarning = false },
                title = { Text("Use insecure HTTP?") },
                text = {
                    Text(
                        "This server uses an unencrypted connection. " +
                            "Your username, password, and playback data could be exposed on the network."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showInsecureHttpWarning = false
                            onContinue(trimmedServerName, trimmedBaseUrl)
                        }
                    ) {
                        Text("Use HTTP")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInsecureHttpWarning = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun isHttpUrl(baseUrl: String): Boolean {
    return baseUrl.trim().startsWith("http://", ignoreCase = true)
}
