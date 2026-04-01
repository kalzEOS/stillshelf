package com.stillshelf.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.ui.components.UpdateNotesDialogContent
import com.stillshelf.app.ui.navigation.RootViewModel
import com.stillshelf.app.ui.navigation.RootNavGraph
import com.stillshelf.app.ui.screens.AppAppearanceViewModel
import com.stillshelf.app.ui.theme.StillShelfTheme

@Composable
fun StillShelfApp() {
    val appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val rootViewModel: RootViewModel = hiltViewModel()
    val rootUiState by rootViewModel.uiState.collectAsStateWithLifecycle()
    val startupViewModel: StartupViewModel = hiltViewModel()
    val startupUpdatePrompt by startupViewModel.startupUpdatePrompt.collectAsStateWithLifecycle()
    val upgradeMessagePrompt by startupViewModel.upgradeMessagePrompt.collectAsStateWithLifecycle()
    StillShelfTheme(
        themeMode = appearance.themeModeForBackend(rootUiState.selectedBackend),
        materialDesignEnabled = appearance.materialDesignEnabledForBackend(rootUiState.selectedBackend)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            RootNavGraph(
                onHomeScreenReached = startupViewModel::onHomeScreenReached,
                rootViewModel = rootViewModel
            )
            upgradeMessagePrompt?.let { prompt ->
                AlertDialog(
                    onDismissRequest = startupViewModel::dismissUpgradeMessagePrompt,
                    title = { Text("Welcome to StillShelf ${prompt.versionName}") },
                    text = {
                        Text(
                            "This update adds a full Navidrome frontend, including lyrics support.\n\n" +
                                "Your existing Audiobookshelf setup is still here.\n\n" +
                                "On the mode selection screen:\n" +
                                "Choose Audiobookshelf if you only use Audiobookshelf.\n" +
                                "Choose Navidrome if you want to add or use a Navidrome server."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = startupViewModel::dismissUpgradeMessagePrompt) {
                            Text("Got it")
                        }
                    }
                )
            } ?: startupUpdatePrompt?.let { release ->
                AlertDialog(
                    onDismissRequest = startupViewModel::dismissStartupUpdatePrompt,
                    title = { Text("Update available") },
                    text = {
                        UpdateNotesDialogContent(
                            versionName = release.versionName,
                            notes = release.body
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = startupViewModel::installStartupUpdate) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = startupViewModel::dismissStartupUpdatePrompt) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
