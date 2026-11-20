package com.stillshelf.app.scrobbling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ListeningStatisticsDetails(
    state: ListeningStatisticsUiState, onDismiss: () -> Unit, onRefresh: () -> Unit,
    canOpen: (ListeningEntry) -> Boolean, onOpen: (ListeningEntry) -> Unit,
    onNavigate: ((ListeningEntry, String) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ListeningStatisticsPage(state, onDismiss, onRefresh, canOpen, onOpen, onNavigate = onNavigate)
    }
}

@Composable
fun ListeningStatisticsScreen(
    onBack: () -> Unit, onOpenArtist: ((String) -> Unit)? = null, onOpenAlbum: ((String) -> Unit)? = null,
    viewModel: ListeningStatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigation = rememberListeningEntryNavigation(viewModel, onOpenArtist, onOpenAlbum)
    ListeningStatisticsPage(state, onBack, viewModel::refresh,
        canOpen = { !navigation.busy }, onOpen = {}, bottomPadding = 120.dp,
        onResolve = viewModel::resolveEntry, onLoadMore = viewModel::loadMore,
        onRetryChart = viewModel::retryChart,
        onNavigate = navigation.open)
}

@Composable
internal fun ListeningStatisticsPage(
    state: ListeningStatisticsUiState, onDismiss: () -> Unit, onRefresh: () -> Unit,
    canOpen: (ListeningEntry) -> Boolean, onOpen: (ListeningEntry) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    onResolve: (ListeningEntry, String) -> Unit = { _, _ -> },
    onLoadMore: (ListeningChart) -> Unit = {},
    onRetryChart: (ListeningChart) -> Unit = {},
    onNavigate: ((ListeningEntry, String) -> Unit)? = null
) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 18.dp)) {
                var tab by rememberSaveable { mutableStateOf("Overview") }
                var trendsRefresh by remember { mutableStateOf(0) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    Text("Listening Statistics", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { if (tab == "Trends") trendsRefresh++ else onRefresh() }, enabled = !state.loading) { Text("Refresh") }
                }
                Text(state.sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Overview", "Artists", "Albums", "Tracks", "Trends", "Recent").forEach { label ->
                        FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) }, modifier = Modifier.testTag("statistics-tab-$label"))
                    }
                }
                val tabStates = rememberSaveableStateHolder()
                tabStates.SaveableStateProvider(tab) {
                LazyColumn(Modifier.weight(1f).testTag("statistics-list"), state = rememberLazyListState(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.error?.let { item { ListeningUnavailable(if (state.isCached) "Showing cached statistics" else "Statistics unavailable", it) } }
                    if (tab == "Trends") item { ListeningTrendsScreen(trendsRefresh) }
                    else state.statistics?.let { stats ->
                        if (tab == "Overview") {
                            item { ListeningSummaryTiles(stats) }
                            stats.listeningTimeNote?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            item { ListeningEntryGroup("Top Artists", stats.topArtists.take(3), "artist", canOpen, { if (onNavigate != null) onNavigate(it, "artist") else onOpen(it) }, onResolve) }
                            item { ListeningEntryGroup("Top Albums", stats.topAlbums.take(3), "album", canOpen, { if (onNavigate != null) onNavigate(it, "album") else onOpen(it) }, onResolve) }
                            item { ListeningEntryGroup("Top Tracks", stats.topTracks.take(3), "track", canOpen, { if (onNavigate != null) onNavigate(it, "track") else onOpen(it) }, onResolve) }
                            item { ListeningEntryGroup("Recent Listening", stats.recent.take(3), "track", canOpen, { if (onNavigate != null) onNavigate(it, "track") else onOpen(it) }, onResolve) }
                        } else {
                            val entries = when (tab) { "Artists" -> stats.topArtists; "Albums" -> stats.topAlbums; "Tracks" -> stats.topTracks; else -> stats.recent }
                            val kind = when (tab) { "Artists" -> "artist"; "Albums" -> "album"; else -> "track" }
                            val chart = when(tab) { "Artists" -> ListeningChart.ARTISTS; "Albums" -> ListeningChart.ALBUMS; "Tracks" -> ListeningChart.TRACKS; else -> null }
                            val unavailable = (chart?.let { ListeningMetric.valueOf(it.name) }
                                ?: ListeningMetric.RECENT) in stats.unavailableSections
                            item { Text(if (unavailable && entries.isEmpty()) "$tab · Unavailable" else if (tab == "Recent") "Recent Listening · ${entries.size}" else "$tab · ${entries.size}", style = MaterialTheme.typography.titleMedium) }
                            if (entries.isEmpty()) item { ListeningUnavailable(if (unavailable) "$tab temporarily unavailable" else "No $tab statistics available",
                                if (unavailable) "${stats.sourceLabel.substringBefore(" ·")} could not refresh this section. Try refreshing again later."
                                else if (tab == "Artists") stats.artistsUnavailableReason ?: "This provider has not returned artist charts yet."
                                else "This source has not provided these statistics. Try refreshing or choose another statistics source in Settings.") }
                            if (unavailable && chart != null) item {
                                OutlinedButton(onClick = { onRetryChart(chart) }, enabled = state.loadingMore == null) {
                                    Text(if (state.loadingMore == chart) "Retrying…" else "Retry $tab")
                                }
                            }
                            items(entries.size) { index ->
                                val entry = entries[index]
                                LaunchedEffect(kind, entry.title, entry.subtitle, entry.albumName) { onResolve(entry, kind) }
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
                                    ListeningEntryRow(entry, kind, canOpen(entry)) { if (onNavigate != null) onNavigate(entry, kind) else onOpen(entry) }
                                }
                            }
                            if (chart != null && chart in stats.moreCharts) item {
                                OutlinedButton(onClick = { onLoadMore(chart) }, enabled = state.loadingMore == null, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (state.loadingMore == chart) "Loading…" else "Load more $tab")
                                }
                            }
                        }
                        state.pageError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        stats.note?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                }
                }
            }
        }
}

@Composable
private fun ListeningEntryGroup(title: String, entries: List<ListeningEntry>, kind: String,
    canOpen: (ListeningEntry) -> Boolean, onOpen: (ListeningEntry) -> Unit,
    onResolve: (ListeningEntry, String) -> Unit = { _, _ -> }) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
        Column {
            entries.forEachIndexed { index, entry ->
                LaunchedEffect(kind, entry.title, entry.subtitle, entry.albumName) { onResolve(entry, kind) }
                ListeningEntryRow(entry, kind, canOpen(entry), onClick = { onOpen(entry) })
                if (index != entries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
    }
}

@Composable
internal fun ListeningEntryRow(entry: ListeningEntry, kind: String, navigable: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = navigable, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ListeningArtwork(entry.artworkUrl, kind, Modifier.size(48.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.timestampSeconds?.let { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it * 1000)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        entry.plays?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text("%,d".format(it), style = MaterialTheme.typography.titleSmall)
                Text("plays", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (navigable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open details", modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ListeningArtwork(url: String?, kind: String, modifier: Modifier) {
    val shape = if (kind == "artist") CircleShape else RoundedCornerShape(8.dp)
    Surface(modifier.clip(shape), shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            Icon(when (kind) { "artist" -> Icons.Outlined.Person; "album" -> Icons.Outlined.Album;
                "count" -> Icons.Outlined.Headphones; "time" -> Icons.Outlined.Schedule; else -> Icons.Outlined.MusicNote },
                contentDescription = null, modifier = Modifier.fillMaxSize().padding(6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun ListeningUnavailable(title: String, description: String) {
    Surface(Modifier.fillMaxWidth().testTag("statistics-unavailable"), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
