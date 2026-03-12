package com.stillshelf.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginRoute(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var duplicateServerMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is LoginEvent.Success) {
                onLoginSuccess()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        if (message.contains("already exists", ignoreCase = true)) {
            duplicateServerMessage = message
        } else {
            snackbarHostState.showSnackbar(message)
        }
        viewModel.clearError()
    }

    LoginScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::onLoginClick,
        onContinueSessionOnlyClick = viewModel::onContinueSessionOnlyClick,
        onSaveLessSecurelyClick = viewModel::onSaveLessSecurelyClick,
        onDismissTokenStoragePrompt = viewModel::dismissTokenStoragePrompt,
        onBack = onBack
    )

    val dialogMessage = duplicateServerMessage
    if (dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { duplicateServerMessage = null },
            title = { Text("Server already added") },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { duplicateServerMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onContinueSessionOnlyClick: () -> Unit,
    onSaveLessSecurelyClick: () -> Unit,
    onDismissTokenStoragePrompt: () -> Unit,
    onBack: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    ) { paddingValues ->
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(if (imeVisible) Alignment.TopCenter else Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.padding(start = 10.dp)
                        ) {
                            Text(
                                text = "Login",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = uiState.serverName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = onUsernameChange,
                            label = { Text("Username") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii
                            )
                        )

                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = onPasswordChange,
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (passwordVisible) {
                                            "Hide password"
                                        } else {
                                            "Show password"
                                        }
                                    )
                                }
                            }
                        )

                        Button(
                            onClick = onLoginClick,
                            enabled = uiState.canSubmit && !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Login")
                            }
                        }

                        FilledTonalButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }

    if (uiState.showTokenStorageFallbackPrompt) {
        AlertDialog(
            onDismissRequest = onDismissTokenStoragePrompt,
            title = { Text("Secure storage unavailable") },
            text = {
                Text(
                    "StillShelf could not access secure token storage on this device. " +
                        "You can continue for this session only, or explicitly allow a less secure saved login."
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onContinueSessionOnlyClick) {
                        Text("Session Only")
                    }
                    TextButton(onClick = onSaveLessSecurelyClick) {
                        Text("Save Less Securely")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissTokenStoragePrompt) {
                    Text("Cancel")
                }
            }
        )
    }
}
