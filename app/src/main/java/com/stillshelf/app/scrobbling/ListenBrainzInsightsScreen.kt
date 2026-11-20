package com.stillshelf.app.scrobbling

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ListenBrainzInsightsScreen(refresh: Int, viewModel: ListenBrainzInsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(InsightPeriod.THIS_MONTH) }
    var handledRefresh by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(period, refresh) {
        val force = handledRefresh != refresh
        handledRefresh = refresh
        viewModel.show(period, force)
    }
    DisposableEffect(viewModel) { onDispose { viewModel.hide() } }
    ListenBrainzInsightsDashboard(state, { period = it }, { viewModel.show(period, force = true) })
}

@Composable
internal fun ListenBrainzInsightsDashboard(state: InsightsUiState, onPeriod: (InsightPeriod) -> Unit, onRetry: () -> Unit = {}) {
    if (!state.enabled) return
    val loading = state.loading || state.results.values.any { it.loading }
    Column(Modifier.fillMaxWidth().testTag("listenbrainz-insights"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider()
        Text("ListenBrainz insights", style = MaterialTheme.typography.titleMedium)
        ListeningChoice("Statistics period", state.period, InsightPeriod.entries.associateWith { it.label }, true, onPeriod)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("insights-loading"))
        if (state.results.values.any { it.error }) {
            Text("Some additional statistics could not be refreshed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRetry, enabled = !loading) { Text("Retry additional statistics") }
        }
        if (state.results.values.any { it.cached }) Text("Showing cached additional statistics.", style = MaterialTheme.typography.bodySmall)
        val datasets = InsightKind.entries.mapNotNull { state.results[it]?.data?.takeIf(InsightDataset::meaningful) }
        if (!loading && datasets.isEmpty() && state.results.values.none { it.error })
            Text("No additional statistics are available for this period.", style = MaterialTheme.typography.bodySmall)
        datasets.forEach { data ->
            // All-time ranking lists already exist in Artists/Albums/Tracks; don't duplicate them here.
            if (state.period != InsightPeriod.ALL || data.kind !in setOf(InsightKind.ALBUMS, InsightKind.TRACKS)) {
                Column(Modifier.fillMaxWidth().testTag("insight-${data.kind.name}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(data.kind.title, style = MaterialTheme.typography.titleMedium)
                    when (data.kind) {
                        InsightKind.DAILY -> InsightHourGrid(data)
                        InsightKind.EVOLUTION -> InsightEvolution(data, state.period)
                        InsightKind.ARTIST_ALBUMS -> ArtistAlbumInsight(data)
                        InsightKind.ERAS -> EraInsight(data)
                        InsightKind.GENRES -> {
                            Text("Top genres by tagged listens · Genres can overlap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            InsightBars(data.rows, data.kind.name)
                        }
                        InsightKind.ORIGINS -> {
                            Text("Artists with known country information", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            InsightBars(data.rows.map { it.copy(label = countryName(it.label)) }, data.kind.name, unit = "artists")
                        }
                        else -> InsightBars(data.rows, data.kind.name)
                    }
                    InsightDates(data)
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumInsight(data: InsightDataset) {
    var selected by rememberSaveable(data) { mutableStateOf(data.rows.first().identity) }
    val choices = data.rows.associate { it.identity to it.detail }
    ListeningChoice("Artist", selected, choices, true) { selected = it }
    InsightBars(data.rows.filter { it.identity == selected }, data.kind.name)
}

@Composable
private fun EraInsight(data: InsightDataset) {
    var decade by rememberSaveable(data) { mutableStateOf<Int?>(null) }
    Text("Listens by original release year · Unmapped years excluded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (decade != null) TextButton(onClick = { decade = null }) { Text("All decades") }
    val rows = if (decade == null) data.rows.groupBy { it.index!! / 10 * 10 }.toSortedMap().map { (year, values) ->
        InsightRow("${year}s", values.sumOf(InsightRow::count), index = year)
    } else data.rows.filter { it.index!! / 10 * 10 == decade }
    InsightBars(rows, data.kind.name, onSelect = { if (decade == null) decade = it.index })
}

@Composable
private fun InsightDates(data: InsightDataset) {
    val format = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val coverage = if (data.from != null && data.to != null) {
        "${Instant.ofEpochSecond(data.from).atZone(ZoneOffset.UTC).format(format)} – ${Instant.ofEpochSecond(data.to).atZone(ZoneOffset.UTC).format(format)}"
    } else ""
    val updated = data.updated?.let { "Updated ${Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(format)}" }.orEmpty()
    if (coverage.isNotEmpty() || updated.isNotEmpty()) Text(listOf(coverage, updated).filter(String::isNotEmpty).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun countryName(code: String): String = Locale.getISOCountries().firstOrNull { short ->
    runCatching { Locale("", short).isO3Country == code || short == code }.getOrDefault(false)
}?.let { Locale("", it).getDisplayCountry(Locale.getDefault()) } ?: code
