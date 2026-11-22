package com.stillshelf.app.absstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.data.repo.SessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AbsStatisticsState(val snapshot: AbsStatisticsSnapshot = AbsStatisticsSnapshot(),
    val loading: Boolean = false, val message: String? = null, val year: Int = LocalDate.now().year)
data class AbsHistoryState(val sessions: List<AbsListeningSession> = emptyList(), val total: Int? = null,
    val loading: Boolean = false, val hasMore: Boolean = true, val message: String? = null)
data class AbsStatisticsScope(val libraryId: String? = null, val libraries: List<Library> = emptyList(), val accountName: String? = null) {
    val label: String get() = if (libraryId == null) "All libraries" else libraries.firstOrNull { it.id == libraryId }?.name ?: "Current library"
    val description: String get() {
        val kind = if (libraryId == null) "Books and podcasts" else libraries.firstOrNull { it.id == libraryId }?.let {
            if (it.isPodcastLibrary) "Podcast library" else "Audiobook library"
        } ?: "Selected library"
        return "$kind · ${accountName?.let { "account: $it" } ?: "this account only"}"
    }
}

@HiltViewModel
class AbsStatisticsViewModel @Inject constructor(private val repository: AbsStatisticsRepository,
    sessions: SessionRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AbsStatisticsState())
    val state = mutableState.asStateFlow()
    private var key: String? = null
    private var job: Job? = null
    private var generation = 0L
    private var libraryJob: Job? = null
    private val mutableHistory = MutableStateFlow(AbsHistoryState())
    val history = mutableHistory.asStateFlow()
    private var historyJob: Job? = null
    private var nextPage = 0
    private var historyGeneration = 0L
    private val mutableScope = MutableStateFlow(AbsStatisticsScope())
    val selection = mutableScope.asStateFlow()
    init {
        viewModelScope.launch { sessions.observeLibrariesForActiveServer().collect { libraries ->
            mutableScope.update { it.copy(libraries = libraries) }
        } }
        viewModelScope.launch { repository.scopes().collect { (account, library) ->
        job?.cancel()
        libraryJob?.cancel()
        key = account
        mutableScope.update { it.copy(libraryId = library, accountName = account?.let { repository.accountDisplayName(it) }) }
        resetHistory()
        mutableState.value = AbsStatisticsState(year = mutableState.value.year)
        refresh()
    } } }
    private fun resetHistory() {
        historyGeneration++
        historyJob?.cancel()
        nextPage = 0
        mutableHistory.value = AbsHistoryState()
    }
    fun refreshHistory() { resetHistory(); loadHistory() }
    fun library(id: String?) {
        if (id == mutableScope.value.libraryId) {
            if (libraryJob?.isActive == true) { libraryJob?.cancel(); libraryJob = null; refresh() }
            return
        }
        if (id != null && mutableScope.value.libraries.none { it.id == id }) return
        libraryJob?.cancel()
        job?.cancel()
        val request = ++generation
        val account = key ?: return
        val year = mutableState.value.year
        libraryJob = viewModelScope.launch {
            val cached = repository.cached(account, year, id)
            if (key != account || request != generation) return@launch
            // Publish the new label and matching cache together, without an empty frame.
            resetHistory()
            mutableState.value = AbsStatisticsState(cached, year = year)
            mutableScope.update { it.copy(libraryId = id) }
            refresh()
        }
    }
    fun loadHistory() {
        if (historyJob?.isActive == true || !mutableHistory.value.hasMore) return
        val scope = key ?: return
        val page = nextPage
        val request = historyGeneration
        val library = mutableScope.value.libraryId
        historyJob = viewModelScope.launch {
            mutableHistory.update { it.copy(loading = true, message = null) }
            try {
                val result = repository.history(scope, page, library)
                if (key != scope || request != historyGeneration) return@launch
                mutableHistory.update { it.copy(sessions = (it.sessions + result.sessions).distinctBy { row -> row.id },
                    total = result.total, hasMore = result.hasMore,
                    message = if (result.cached) "Showing saved listening history. The server could not be reached." else null) }
                nextPage++
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (key == scope && request == historyGeneration) mutableHistory.update { it.copy(message = "Could not load more history. Try again.") } }
            finally { if (key == scope && request == historyGeneration) mutableHistory.update { it.copy(loading = false) } }
        }
    }
    fun year(value: Int) {
        if (value == mutableState.value.year) return
        mutableState.value = mutableState.value.copy(year = value)
        refresh()
    }
    fun refresh(force: Boolean = false) {
        val request = ++generation
        job?.cancel()
        val scope = key ?: return
        val year = mutableState.value.year
        val library = mutableScope.value.libraryId
        job = viewModelScope.launch {
            val cached = repository.cached(scope, year, library)
            mutableState.value = AbsStatisticsState(cached, loading = true, year = year)
            try {
                val data = repository.refresh(scope, year, force, library)
                if (key != scope || request != generation) return@launch
                mutableScope.update { it.copy(accountName = repository.accountDisplayName(scope)) }
                mutableState.value = AbsStatisticsState(data, year = year, message = if (data.partial)
                    "Some statistics could not be refreshed. Saved data is shown where available." else null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (key == scope && request == generation) mutableState.value = AbsStatisticsState(cached, year = year,
                    message = "Could not refresh Audiobookshelf statistics. Check your connection and try again.")
            } finally {
                if (key == scope && request == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
