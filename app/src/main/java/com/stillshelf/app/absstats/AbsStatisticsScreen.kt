package com.stillshelf.app.absstats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal fun absTotalTime(snapshot: AbsStatisticsSnapshot, selection: AbsStatisticsScope): String =
    if (snapshot.podcastHistoryIncomplete && snapshot.statistics?.seconds == 0.0 &&
        selection.libraries.any { it.id == selection.libraryId && it.isPodcastLibrary }) "Listening time unavailable"
    else absListeningTime(snapshot.statistics?.seconds)

@Composable
fun AbsStatisticsHomeCard(onSeeAll: () -> Unit, viewModel: AbsStatisticsViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    OutlinedCard(onClick = onSeeAll, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Listening Statistics", style = MaterialTheme.typography.titleMedium)
                Text("See all", color = MaterialTheme.colorScheme.primary)
            }
            Text("Audiobookshelf · ${selection.accountName?.let { "$it · " } ?: ""}${selection.label}", style = MaterialTheme.typography.bodySmall)
            if (state.snapshot.localAdditionalMs > 0) Text("Includes listening recorded on this device", style = MaterialTheme.typography.bodySmall)
            if (state.snapshot.localMergePending) Text("Saved server statistics. Local listening is safe and will be combined when server history is available.", style = MaterialTheme.typography.bodySmall)
            val data = state.snapshot.statistics
            if (data != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Listening time", absTotalTime(state.snapshot, selection))
                Metric("Active days", if (data.daysAvailable) data.days.count { it.value > 0 }.toString() else "Not available")
            } else Text(if (state.loading) "Loading your listening history…" else "Statistics are currently unavailable.")
        }
    }
}

@Composable
fun AbsStatisticsScreen(onBack: () -> Unit, onOpenItem: (String, String?) -> Unit,
    viewModel: AbsStatisticsViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("Overview") }
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(tab, selection.libraryId) { if (tab == "History" && history.total == null) viewModel.loadHistory() }
    val data = state.snapshot.statistics
    var showRefreshProgress by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (state.loading) { delay(250); showRefreshProgress = true }
        else showRefreshProgress = false
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 140.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Text("Listening Statistics", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { viewModel.refresh(true); if (tab == "History") viewModel.refreshHistory() },
                    colors = ButtonDefaults.textButtonColors(disabledContentColor = MaterialTheme.colorScheme.primary),
                    enabled = !state.loading && !history.loading) { Text("Refresh") }
            }
            Text("Audiobookshelf · ${selection.accountName?.let { "Account: $it" } ?: "Your logged-in account"}", style = MaterialTheme.typography.bodySmall)
            var librariesOpen by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { librariesOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("${selection.label} ▾") }
                DropdownMenu(expanded = librariesOpen, onDismissRequest = { librariesOpen = false }) {
                    DropdownMenuItem(text = { Text("All libraries") }, onClick = { librariesOpen = false; viewModel.library(null) })
                    selection.libraries.forEach { library ->
                        DropdownMenuItem(text = { Text(library.name) }, onClick = { librariesOpen = false; viewModel.library(library.id) })
                    }
                }
            }
            Text(selection.description, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overview", "Activity", "History", "Year in books").forEach { name ->
                    FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (showRefreshProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
        } }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (state.snapshot.localMergePending) item {
            Text("Showing saved server statistics. Your local listening is safe and will be combined when complete server history is available.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.snapshot.localAdditionalMs > 0) item {
            Text(if (state.snapshot.localOnly) "Listening recorded on this device. Server history is temporarily unavailable."
                else "Includes listening recorded on this device. These additions may not appear on other devices.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (state.snapshot.podcastHistoryIncomplete) item {
            Text("Earlier podcast listening time is unavailable: ABS previously saved episode progress without matching listening sessions. Recorded totals may be incomplete. StillShelf now keeps new listening on this device when the server cannot record it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.snapshot.updatedAt?.let { time -> item {
            Text("${if (state.snapshot.cached && state.snapshot.partial) "Saved statistics · " else "Updated "}" + Instant.ofEpochMilli(time)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · h:mm a")), style = MaterialTheme.typography.bodySmall)
        } }
        if (data == null && !state.loading) item {
            Text("No listening statistics are available from this account yet. Your saved book progress is separate from recorded listening time.")
        }
        when (tab) {
            "Overview" -> if (data != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Your listening", style = MaterialTheme.typography.titleMedium)
                            Text(absTotalTime(state.snapshot, selection), style = MaterialTheme.typography.headlineLarge)
                            Text(when {
                                state.snapshot.localOnly -> "Listening recorded on this device"
                                state.snapshot.localAdditionalMs > 0 -> "Server history and listening recorded on this device"
                                else -> "Recorded by your Audiobookshelf server"
                            }, style = MaterialTheme.typography.bodySmall)
                            if (data.seconds == 0.0) Text("No listening sessions are recorded for this selection. Saved playback progress may still exist.",
                                style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (data.todaySeconds != null) Metric("Today", absListeningTime(data.todaySeconds))
                                Metric("Active days", if (data.daysAvailable) data.days.count { it.value > 0 }.toString() else "Not available")
                            }
                        }
                    }
                }
                if (data.items.isNotEmpty()) {
                    item { Title("Most listened", "Ranked by recorded listening time") }
                    items(data.items.take(10), key = { "item-${it.id}" }) { row ->
                        OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            if (row.author.isNotBlank()) Text(row.author, style = MaterialTheme.typography.bodySmall)
                            Text(absListeningTime(row.seconds), color = MaterialTheme.colorScheme.primary)
                        } }
                    }
                }
            }
            "Activity" -> if (data != null) {
                item { AbsDailyActivity(data.days) }
                item { AbsListeningCalendar(data.days) }
                if (data.days.any { it.value > 0 }) item { AbsListeningPatterns(data.days) }
            }
            "History" -> {
                item { Title("Recent listening", "Recorded sessions · books and podcasts") }
                val rows = history.sessions.ifEmpty { data?.recent.orEmpty() }
                if (rows.isEmpty() && history.total == 0) item { Text("No recent sessions are available.") }
                items(rows, key = { "session-${it.id}" }) { row ->
                    OutlinedCard(onClick = { if (row.itemId.isNotBlank()) onOpenItem(row.itemId, row.episodeId) },
                        modifier = Modifier.fillMaxWidth(), enabled = row.itemId.isNotBlank()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            if (row.author.isNotBlank()) Text(row.author, style = MaterialTheme.typography.bodySmall)
                            Text(absListeningTime(row.seconds), color = MaterialTheme.colorScheme.primary)
                            row.startedAt?.let { time -> Text(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                history.message?.let { item { Text(it) } }
                if (history.hasMore) item {
                    OutlinedButton(onClick = viewModel::loadHistory, enabled = !history.loading) {
                        Text(if (history.loading) "Loading history…" else "Load more history")
                    }
                }
            }
            "Year in books" -> {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text("${state.year} ▾") }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            (LocalDate.now().year downTo LocalDate.now().year - 5).forEach { year ->
                                DropdownMenuItem(text = { Text(year.toString()) }, onClick = { expanded = false; viewModel.year(year) })
                            }
                        }
                    }
                }
                val annual = state.snapshot.year?.takeIf { it.year == state.year }
                if (annual == null) item { Text("This year's summary is unavailable. Try refreshing later.") }
                else {
                    item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        annual.booksFinished?.let { Metric("Books finished", it.toString()) }
                        Metric("Recorded audiobook time", absListeningTime(annual.bookSeconds))
                        Metric("Recorded podcast time", if (state.snapshot.podcastHistoryIncomplete && annual.podcastSeconds == 0.0)
                            "Listening time unavailable" else absListeningTime(annual.podcastSeconds))
                    } } }
                    if (selection.libraryId != null) item { Text("Library summaries use this account's recorded sessions. Books-finished totals are provided by ABS for the whole account only.", style = MaterialTheme.typography.bodySmall) }
                    if (annual.authors.isNotEmpty()) item { AbsRankedTimes("Top authors", annual.authors) }
                    annual.narrator?.let { narrator -> item { AbsRankedTimes("Most listened narrator", listOf(narrator)) } }
                    if (annual.genres.isNotEmpty()) item { AbsRankedTimes("Top genres", annual.genres) }
                }
            }
        }
        item { Text("Listening time comes from recorded sessions. Book length and saved progress are not used to estimate missing history.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable internal fun Metric(label: String, value: String) {
    Column { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall) }
}
@Composable internal fun Title(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
}
@Composable private fun AbsRankedTimes(title: String, rows: List<Pair<String, Double>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(title, "Recorded listening time")
        val largest = rows.maxOf { it.second }.coerceAtLeast(1.0)
        rows.forEach { (name, seconds) ->
            Text(name); LinearProgressIndicator(progress = { (seconds / largest).toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(absListeningTime(seconds), style = MaterialTheme.typography.bodySmall)
        }
    }
}
