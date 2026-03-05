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
import com.stillshelf.app.ui.navigation.RootNavGraph
import com.stillshelf.app.ui.screens.AppAppearanceViewModel
import com.stillshelf.app.ui.theme.StillShelfTheme

@Composable
fun StillShelfApp() {
    val appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val startupViewModel: StartupViewModel = hiltViewModel()
    val startupUpdatePrompt by startupViewModel.startupUpdatePrompt.collectAsStateWithLifecycle()
    StillShelfTheme(
        themeMode = appearance.themeMode,
        materialDesignEnabled = appearance.materialDesignEnabled
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            RootNavGraph()
            startupUpdatePrompt?.let { release ->
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
