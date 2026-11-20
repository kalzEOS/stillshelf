package com.stillshelf.app.scrobbling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.ui.screens.AppScreenHorizontalPadding

@Composable
fun ListeningStatisticsSection(
    viewModel: ListeningStatisticsViewModel = hiltViewModel(),
    onOpenArtist: ((String) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    onSeeAll: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topArtist = state.statistics?.topArtists?.firstOrNull()
    val recentEntry = state.statistics?.recent?.firstOrNull()
    LaunchedEffect(topArtist?.title) { topArtist?.let { viewModel.resolveEntry(it, "artist") } }
    LaunchedEffect(recentEntry?.title, recentEntry?.subtitle) { recentEntry?.let { viewModel.resolveEntry(it, "track") } }
    var details by rememberSaveable { mutableStateOf(false) }
    val navigation = rememberListeningEntryNavigation(viewModel, onOpenArtist, onOpenAlbum)
    if (!state.enabled) return
    Card(Modifier.fillMaxWidth().padding(horizontal = AppScreenHorizontalPadding),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Listening Statistics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (onSeeAll != null) onSeeAll() else details = true }) { Text("See all") }
            }
            Text(state.sourceLabel, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("statistics-source"))
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ListeningSummaryTiles(state.statistics, state.loading)
            state.statistics?.listeningTimeNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::refresh) { Text("Retry") }
            }
            state.statistics?.recent?.firstOrNull()?.let { entry ->
                ListeningEntryRow(entry, "track", !navigation.busy, onClick = { navigation.open(entry, "track") })
            }
        }
    }
    if (details) ListeningStatisticsDetails(state, onDismiss = { details = false }, onRefresh = viewModel::refresh,
        canOpen = { !navigation.busy }, onOpen = {}, onNavigate = navigation.open)
}

@Composable
internal fun ListeningSummaryTiles(stats: ListeningStatistics?, loading: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ListeningMetric("Scrobbles", stats?.totalScrobbles?.let { "%,d".format(it) } ?: if (loading) "—" else "Not available",
            Modifier.weight(1f).fillMaxHeight(), kind = "count")
        ListeningMetric("Listening Time", stats?.listeningSeconds?.let { if (it >= 3600) "${it / 3600} h" else "${it / 60} min" }
            ?: if (loading) "—" else "Not provided", Modifier.weight(1f).fillMaxHeight(), kind = "time")
        val artist = stats?.topArtists?.firstOrNull()
        ListeningMetric("Top Artist", artist?.title ?: if (loading) "—" else "Not available",
            Modifier.weight(1f).fillMaxHeight(), kind = "artist", artworkUrl = artist?.artworkUrl)
    }
}

@Composable
private fun ListeningMetric(label: String, value: String, modifier: Modifier, kind: String, artworkUrl: String? = null) {
    Surface(modifier.heightIn(min = 136.dp).testTag("metric-$label"), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListeningArtwork(artworkUrl, kind, Modifier.size(32.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
