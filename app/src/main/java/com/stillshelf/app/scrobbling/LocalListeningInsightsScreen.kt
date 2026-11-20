package com.stillshelf.app.scrobbling

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class LocalListeningInsightsViewModel @Inject constructor(private val settings: ListeningSettingsStore,
    history: LocalListeningRepository, private val insights: LocalListeningInsights) : ViewModel() {
    private val mutableState = MutableStateFlow(InsightsUiState())
    val state = mutableState.asStateFlow()
    private val mutableDetails = MutableStateFlow(LocalInsights())
    val details = mutableDetails.asStateFlow()
    private var period = InsightPeriod.THIS_MONTH
    private var active = false
    private var visible = false
    private var owner: Any? = null
    private var generation = 0L
    private var job: Job? = null
    init { viewModelScope.launch {
        try { settings.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { return@launch }
        combine(settings.state.map { it.loaded && it.showStatistics && it.localRecordingEnabled && it.statisticsSource == StatisticsSource.LOCAL }.distinctUntilChanged(),
            history.insightChanges.distinctUntilChanged()) { enabled, revision -> enabled to revision }
            .collect { (enabled, _) ->
                active = enabled
                if (!enabled) { generation++; job?.cancel(); mutableState.value = InsightsUiState(); mutableDetails.value = LocalInsights() }
                else if (visible) load()
            }
    } }
    fun show(value: InsightPeriod, owner: Any? = null) {
        this.owner = owner; visible = true; period = value; if (active) load()
    }
    fun hide(owner: Any? = null) {
        // Lazy layout measurement can replace a composition before disposing its predecessor.
        // That old composition must not cancel the currently displayed screen's query.
        if (owner != null && this.owner !== owner) return
        this.owner = null; visible = false; generation++; job?.cancel()
        mutableState.value = mutableState.value.copy(loading = false)
    }
    private fun load() {
        val version = ++generation
        job?.cancel()
        mutableState.value = InsightsUiState(enabled = true, period = period, loading = true)
        val selected = period
        job = viewModelScope.launch {
            try {
                val result = withTimeout(15_000) { insights.load(selected) }
                if (version == generation) { mutableDetails.value = result; mutableState.value = InsightsUiState(true, selected, result.results) }
            } catch (_: TimeoutCancellationException) {
                if (version == generation) mutableState.value = InsightsUiState(true, selected,
                    mapOf(InsightKind.DAILY to InsightResult(error = true)))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (version == generation) mutableState.value = InsightsUiState(true, selected,
                mapOf(InsightKind.DAILY to InsightResult(error = true))) }
            finally { if (version == generation) mutableState.value = mutableState.value.copy(loading = false) }
        }
    }
}

@Composable
internal fun LocalListeningInsightsScreen(refresh: Int, viewModel: LocalListeningInsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data by viewModel.details.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(InsightPeriod.THIS_MONTH) }
    val owner = remember { Any() }
    LaunchedEffect(viewModel, owner, period, refresh) { viewModel.show(period, owner) }
    DisposableEffect(viewModel, owner) { onDispose { viewModel.hide(owner) } }
    if (!state.enabled) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListenBrainzInsightsDashboard(state, { period = it }, { viewModel.show(period, owner) },
            heading = "Local listening insights", zone = ZoneId.systemDefault(), local = true)
        if (!state.loading) {
            if (period != InsightPeriod.ALL && data.artists.isNotEmpty()) {
                Text("Artists by period", style = MaterialTheme.typography.titleMedium)
                InsightBars(data.artists, "LOCAL_ARTISTS")
            }
            if (data.discovery.isNotEmpty()) {
                Text("Artist discovery", style = MaterialTheme.typography.titleMedium)
                Text("New artists first recorded on this device · Most recent 30 days with discoveries", style = MaterialTheme.typography.bodySmall)
                InsightBars(data.discovery, "LOCAL_DISCOVERY", unit = "artists")
            }
            if (data.coverage.isNotBlank()) Text(data.coverage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
