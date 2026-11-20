package com.stillshelf.app.scrobbling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.data.repo.NavidromeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@HiltViewModel
class ListenBrainzInsightsViewModel @Inject constructor(
    private val settings: ListeningSettingsStore, repository: NavidromeRepository,
    private val client: ListenBrainzInsightsClient, private val cache: ListenBrainzInsightsCache
) : ViewModel() {
    private val mutableState = MutableStateFlow(InsightsUiState())
    val state = mutableState.asStateFlow()
    private var period = InsightPeriod.THIS_MONTH
    private var identity: String? = null
    private var connection: ListeningConnection? = null
    private var active = false
    private var generation = 0
    private var job: Job? = null
    private var pendingKey: String? = null
    private var pendingConnection: ListeningConnection? = null
    private val memory = linkedMapOf<String, Map<InsightKind, InsightCacheEntry>>()
    private val requests = Semaphore(2)

    init { viewModelScope.launch {
        try { settings.load() } catch (e: CancellationException) { throw e } catch (_: Exception) { return@launch }
        combine(settings.state, repository.observeSession()) { saved, session ->
            val selected = saved.connection?.takeIf { saved.showStatistics && saved.statisticsSource == StatisticsSource.EXTERNAL && it.provider == ListeningProvider.LISTEN_BRAINZ }
            selected to if (selected != null) listeningStatisticsCacheKey(saved, session) else null
        }.distinctUntilChanged().collect { (selected, key) ->
            val changedIdentity = identity != key
            val repaired = selected != null && connection != null && selected.token != connection?.token
            connection = selected; identity = key
            if (changedIdentity || selected == null) {
                generation++; job?.cancel(); pendingKey = null
                mutableState.value = InsightsUiState(enabled = selected != null, period = period)
            }
            if (active && selected != null && (changedIdentity || repaired)) load(force = repaired)
        }
    } }

    fun show(selected: InsightPeriod, force: Boolean = false) { active = true; period = selected; load(force) }
    fun hide() {
        active = false; generation++; job?.cancel(); pendingKey = null
        mutableState.value = mutableState.value.copy(loading = false,
            results = mutableState.value.results.mapValues { (_, result) -> result.copy(loading = false) })
    }

    private fun load(force: Boolean = false) {
        val selected = connection ?: return
        val key = identity?.let { "$it-insights-v1-${period.api}-${period.anchor(LocalDate.now(ZoneOffset.UTC)) ?: "all"}" } ?: return
        // Repeated Refresh taps must not keep restarting a slow batch before it can finish.
        if (job?.isActive == true && mutableState.value.loading && pendingKey == key && pendingConnection === selected) return
        val requested = period
        val version = ++generation
        job?.cancel(); pendingKey = key; pendingConnection = selected
        mutableState.value = InsightsUiState(enabled = true, period = requested, loading = true)
        job = viewModelScope.launch {
            try {
            // A previous disk write must not leave a new period waiting behind its cache mutex forever.
            val entries = (memory[key] ?: try { withTimeoutOrNull(2_000) { cache.get(key) }.orEmpty() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }).toMutableMap()
            if (version != generation) return@launch
            val now = System.currentTimeMillis()
            val needed = InsightKind.entries.filter { kind ->
                val entry = entries[kind]
                val ttl = if (entry?.failed == true) 60_000L else requested.reuseMillis
                !(requested == InsightPeriod.ALL && kind in setOf(InsightKind.ALBUMS, InsightKind.TRACKS)) &&
                    (force || entry == null || now - entry.checked !in 0 until ttl)
            }
            mutableState.value = InsightsUiState(true, requested, InsightKind.entries.associateWith { kind ->
                val saved = entries[kind]
                InsightResult(saved?.data, loading = kind in needed, error = saved?.failed == true, cached = saved?.data != null && (kind in needed || saved.stale))
            }, loading = needed.isNotEmpty())
            coroutineScope { needed.map { kind -> launch {
                val previous = entries[kind]?.data
                val result = try {
                    // Waiting for another endpoint is not a provider failure. Bound each active
                    // request, while queued requests remain cancellable on period/source changes.
                    val data = requests.withPermit {
                        withTimeout(20_000) { client.fetch(selected, requested, kind) }
                    }
                    InsightCacheEntry(System.currentTimeMillis(), data ?: previous, stale = data == null && previous != null)
                } catch (_: TimeoutCancellationException) {
                    InsightCacheEntry(System.currentTimeMillis(), previous, failed = true, stale = previous != null)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { InsightCacheEntry(System.currentTimeMillis(), previous, failed = true, stale = previous != null) }
                if (version != generation) return@launch
                entries[kind] = result
                memory[key] = entries.toMap()
                while (memory.size > 12) memory.remove(memory.keys.first())
                // Each endpoint publishes independently; a timeout cannot hide successful charts.
                val updated = mutableState.value.results + (kind to InsightResult(result.data, error = result.failed, cached = result.stale))
                mutableState.value = mutableState.value.copy(results = updated, loading = updated.values.any { it.loading })
            } }.joinAll() }
            if (version != generation) return@launch
            memory[key] = entries.toMap()
            while (memory.size > 12) memory.remove(memory.keys.first())
            try { cache.put(key, entries) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Fresh data stays usable without persistent cache. */ }
            } finally {
                if (version == generation) mutableState.value = mutableState.value.copy(loading = false,
                    results = mutableState.value.results.mapValues { (_, result) -> if (result.loading) result.copy(loading = false, error = true) else result })
            }
        }
    }
}
