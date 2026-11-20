package com.stillshelf.app.scrobbling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.data.repo.NavidromeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import org.json.JSONObject

data class TrendsUiState(val data: ListeningTrends? = null, val loading: Boolean = false, val error: String? = null, val cached: Boolean = false)
@HiltViewModel
class ListeningTrendsViewModel @Inject constructor(private val settings: ListeningSettingsStore,
    private val repository: NavidromeRepository, private val client: ListeningTrendsClient, private val storage: SecureTokenStorage,
    private val localHistory: LocalListeningRepository? = null) : ViewModel() {
    private val mutableState = MutableStateFlow(TrendsUiState())
    val state = mutableState.asStateFlow()
    private var range = TrendRange.MONTH
    private var identity: String? = null
    private var job: Job? = null
    private var generation = 0
    private val memory = linkedMapOf<String, Pair<Long, ListeningTrends>>()
    init {
        localHistory?.let { history -> viewModelScope.launch {
            history.changes.collect { if (settings.state.value.statisticsSource == StatisticsSource.LOCAL) load(range, true) }
        } }
        viewModelScope.launch {
        try { settings.load() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            mutableState.value = TrendsUiState(error = "Secure statistics settings could not be loaded."); return@launch
        }
        combine(settings.state, repository.observeSession()) { s, session -> listeningStatisticsCacheKey(s, session) }
            .distinctUntilChanged().collect { identity = it; load(range) }
    } }
    fun load(selected: TrendRange, force: Boolean = false) {
        range = selected
        val key = identity?.let { "$it-trends-v2-${ZoneId.systemDefault().id}-${selected.name}" }
        val version = ++generation
        job?.cancel()
        if (key == null) { mutableState.value = TrendsUiState(error = "Connect the selected statistics source in Settings."); return }
        val cached = memory[key]
        mutableState.value = TrendsUiState(data = cached?.second, loading = true, cached = cached != null)
        job = viewModelScope.launch {
            var previous = cached?.second
            try {
                if (settings.state.value.statisticsSource == StatisticsSource.LOCAL) {
                    require(settings.state.value.localRecordingEnabled)
                    val data = checkNotNull(localHistory).trends(selected)
                    if (version == generation) mutableState.value = TrendsUiState(data)
                    return@launch
                }
                if (previous == null) {
                    val saved = runCatching { storage.getNamedSecret("trends-$key")?.let(::JSONObject) }.getOrNull()
                    if (saved != null) {
                        previous = runCatching { parseTrends(saved.getJSONObject("data")) }.getOrNull()
                        previous?.let { memory[key] = saved.optLong("time") to it }
                    }
                }
                if (version != generation) return@launch
                mutableState.value = TrendsUiState(previous, true, cached = previous != null)
                val entry = memory[key]
                if (!force && entry != null && System.currentTimeMillis() - entry.first in 0..599_999 &&
                    Instant.ofEpochMilli(entry.first).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()) {
                    mutableState.value = TrendsUiState(entry.second); return@launch
                }
                val s = settings.state.value
                val data = client.fetch(if (s.statisticsSource == StatisticsSource.EXTERNAL) s.connection else null, selected)
                if (version != generation) return@launch
                memory[key] = System.currentTimeMillis() to data
                mutableState.value = TrendsUiState(data)
                // Keep one persisted snapshot per provider/range; date is in the envelope, not the storage key.
                runCatching { storage.saveNamedSecret("trends-$key", JSONObject().put("time", System.currentTimeMillis()).put("data", trendsJson(data)).toString(), true, false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (version == generation) mutableState.value = TrendsUiState(previous, error = if (previous == null) "Trends unavailable. Try refreshing later." else "Offline or unavailable. Showing cached Trends.", cached = previous != null) }
        }
    }
}
