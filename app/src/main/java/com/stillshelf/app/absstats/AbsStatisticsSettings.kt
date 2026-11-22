package com.stillshelf.app.absstats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.ui.screens.CustomizeViewModel
import com.stillshelf.app.ui.screens.HomeSectionIds

@Composable
fun AbsStatisticsSettings(onBack: () -> Unit, onViewStatistics: () -> Unit, onManageServers: () -> Unit, onHome: (() -> Unit)? = null,
    customization: CustomizeViewModel = hiltViewModel(), localHistory: @Composable () -> Unit = { AbsLocalHistoryControls() }) {
    val state by customization.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.stillshelf.app.ui.screens.BackTitle(title = "Listening Statistics", onBackClick = onBack,
            onHomeClick = onHome, modifier = Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
        OutlinedCard(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Explore your audiobook and podcast listening using your Audiobookshelf history and listening recorded on this device.")
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Show Listening Statistics", style = MaterialTheme.typography.titleMedium)
                        Text("Display your listening history on Home", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(modifier = Modifier.semantics { contentDescription = "Show Audiobookshelf Listening Statistics" },
                        checked = HomeSectionIds.ABS_STATISTICS !in state.hiddenPersonalizedSectionIds,
                        onCheckedChange = { visible -> customization.setHiddenPersonalizedSectionIds(
                            if (visible) state.hiddenPersonalizedSectionIds - HomeSectionIds.ABS_STATISTICS
                            else state.hiddenPersonalizedSectionIds + HomeSectionIds.ABS_STATISTICS) })
                }
                HorizontalDivider()
                Text("Statistics from", style = MaterialTheme.typography.titleSmall)
                Text("Your Audiobookshelf account · current library")
                Text("Audiobook and podcast listening is kept per account and per library. Statistics start with your current library. All libraries combines only this account's history. People sharing a login share its server history.", style = MaterialTheme.typography.bodySmall)
                Text("Uses your existing server login. No additional account or music scrobbling service is needed.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onManageServers, modifier = Modifier.fillMaxWidth()) { Text("Manage Server Connection") }
                Button(onClick = onViewStatistics, modifier = Modifier.fillMaxWidth()) { Text("View Listening Statistics") }
            }
        }
        }
        item { Text("StillShelf reports time while audio is playing, including offline sessions once you reconnect. Hiding this card does not stop reporting or delete your history.", style = MaterialTheme.typography.bodySmall) }
        item { localHistory() }
        }
    }
}
