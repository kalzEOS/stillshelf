package com.stillshelf.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.ui.navigation.RootNavGraph
import com.stillshelf.app.ui.screens.AppAppearanceViewModel
import com.stillshelf.app.ui.theme.StillShelfTheme

@Composable
fun StillShelfApp() {
    val appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    StillShelfTheme(
        themeMode = appearance.themeMode,
        materialDesignEnabled = appearance.materialDesignEnabled
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            RootNavGraph()
        }
    }
}
