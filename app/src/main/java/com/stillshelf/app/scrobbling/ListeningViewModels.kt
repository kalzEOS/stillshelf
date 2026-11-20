package com.stillshelf.app.scrobbling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.data.repo.NavidromeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@HiltViewModel
class ListeningSettingsViewModel @Inject constructor(
    private val store: ListeningSettingsStore,
    private val clients: ListeningClients,
    private val lastFm: LastFmClient,
    private val queue: ListeningSubmissionQueue
) : ViewModel() {
    val settings = store.state
    val submissionStatus = queue.status
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableNetworkBusy = MutableStateFlow(false)
    val networkBusy = mutableNetworkBusy.asStateFlow()
    private val mutableAuthorizationUrl = MutableStateFlow<String?>(null)
    val authorizationUrl = mutableAuthorizationUrl.asStateFlow()
    private data class Authorization(val connection: ListeningConnection, val token: String, val statisticsOnly: Boolean,
        val editingId: String?, val selectStatistics: Boolean)
    private var authorization: Authorization? = null
    private val mutableAwaitingAuthorization = MutableStateFlow(false)
    val awaitingAuthorization = mutableAwaitingAuthorization.asStateFlow()
    private val mutableSavedConnection = MutableStateFlow<Pair<Long, String>?>(null)
    val savedConnection = mutableSavedConnection.asStateFlow()
    private var saveSequence = 0L

    init { action { store.load() } }

    private fun action(network: Boolean = false, block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableNetworkBusy.value = network
        mutableMessage.value = null
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { mutableMessage.value = e.message ?: "Check the configuration fields." }
            catch (e: ListeningApiException) { mutableMessage.value = e.message }
            catch (e: ListeningConnectionException) { mutableMessage.value = e.message }
            catch (_: java.net.UnknownHostException) { mutableMessage.value = "Server not found. Check the server URL and network connection." }
            catch (_: java.net.SocketTimeoutException) { mutableMessage.value = "Connection timed out. Check the server and try again." }
            catch (_: javax.net.ssl.SSLException) { mutableMessage.value = "Could not establish a secure connection. Check the server URL and HTTPS certificate." }
            catch (_: java.io.IOException) { mutableMessage.value = "Could not reach the service. Check your network and server URL, then try again." }
            catch (_: Exception) { mutableMessage.value = "Could not complete this action. Check the connection and secure storage, then try again." }
            finally { mutableBusy.value = false; mutableNetworkBusy.value = false }
        }
    }

    fun update(transform: (ListeningSettings) -> ListeningSettings) = action { store.update(transform); queue.schedule() }
    fun setScrobblingEnabled(enabled: Boolean) {
        if (!enabled) update { it.copy(scrobblingEnabled = false) }
        else store.state.value.sendingConnection?.let { enableDirect(it.revision) }
    }
    fun enableDirect(id: String) = action(network = true) {
        val saved = store.state.value.savedConnections.firstOrNull { it.revision == id }
            ?: throw IllegalArgumentException("Connect a service before enabling direct scrobbling.")
        require(saved.hasSubmissionCredentials) { "This connection can read statistics. Add scrobbling credentials in Manage Connection before sending listens." }
        val account = clients.forProvider(saved.provider).validate(saved)
        require(saved.username.isBlank() || saved.username.equals(account, ignoreCase = true)) { "The credentials belong to a different account. Edit and save the connection first." }
        store.update { current ->
            val permitted = saved.withSubmissionPermission(true)
            current.saveConnection(permitted, saved.revision).copy(method = ScrobblingMethod.STILL_SHELF,
                scrobblingEnabled = true, sendingConnectionId = id)
        }
        queue.schedule()
        mutableMessage.value = "Direct scrobbling enabled for ${saved.provider.label}."
    }
    fun remove(id: String? = store.state.value.connection?.revision) = action {
        store.removeConnection(id)
        queue.schedule()
        authorization = null
        mutableAwaitingAuthorization.value = false
        mutableMessage.value = "Local connection removed. Remote listening history was not changed."
    }
    fun save(connection: ListeningConnection) = saveConnection(connection, statisticsOnly = false)
    fun saveStatisticsConnection(connection: ListeningConnection) = saveConnection(connection, statisticsOnly = true)
    fun saveManaged(connection: ListeningConnection, editingId: String?, selectStatistics: Boolean) =
        saveConnection(connection, statisticsOnly = editingId == null, editingId = editingId, selectStatistics = selectStatistics)
    private fun saveConnection(connection: ListeningConnection, statisticsOnly: Boolean,
        editingId: String? = store.state.value.connection?.revision, selectStatistics: Boolean = statisticsOnly) = action(network = true) {
        val username = clients.forProvider(connection.provider).validate(connection)
        val validated = ListeningConnection(connection.provider, connection.serverUrl, username,
            connection.token, connection.apiKey, connection.apiSecret, allowSubmissions = !statisticsOnly && connection.allowSubmissions)
        store.update { it.saveConnection(validated, editingId, selectStatistics) }
        mutableSavedConnection.value = ++saveSequence to store.state.value.savedConnections.first { sameListeningAccount(it, validated) }.revision
        queue.schedule()
        mutableMessage.value = "Connection verified and saved securely."
    }
    fun verifyConnection(id: String? = store.state.value.connection?.revision) = action(network = true) {
        val connection = store.state.value.savedConnections.firstOrNull { it.revision == id } ?: return@action
        val client = clients.forProvider(connection.provider)
        val account = client.validate(connection)
        require(connection.username.isBlank() || connection.username.equals(account, ignoreCase = true)) {
            "The credentials belong to a different account. Edit and save the connection first."
        }
        val verified = ListeningConnection(connection.provider, connection.serverUrl, account, connection.token,
            connection.apiKey, connection.apiSecret, connection.revision, connection.allowSubmissions)
        val statistics = try { client.statistics(verified) }
        catch (e: ListeningApiException) { mutableMessage.value = "Credentials verified, but statistics could not be read: ${e.message}"; return@action }
        catch (e: ListeningConnectionException) { mutableMessage.value = "Credentials verified, but statistics could not be read: ${e.message}"; return@action }
        mutableMessage.value = if (statistics.note != null) "Credentials verified. Some statistics are temporarily unavailable; no test listen was sent."
        else if (connection.hasSubmissionCredentials)
            "Connection verified. Statistics access and scrobbling credentials checked; no test listen was sent."
            else "Statistics access verified. Scrobbling credentials are not configured."
    }
    fun authorize(connection: ListeningConnection) = authorizeConnection(connection, statisticsOnly = false)
    fun authorizeStatisticsConnection(connection: ListeningConnection) = authorizeConnection(connection, statisticsOnly = true)
    fun authorizeManaged(connection: ListeningConnection, editingId: String?, selectStatistics: Boolean) =
        authorizeConnection(connection, statisticsOnly = editingId == null, editingId = editingId, selectStatistics = selectStatistics)
    private fun authorizeConnection(connection: ListeningConnection, statisticsOnly: Boolean,
        editingId: String? = store.state.value.connection?.revision, selectStatistics: Boolean = statisticsOnly) = action(network = true) {
        val token = lastFm.authorizationToken(connection)
        authorization = Authorization(connection, token, statisticsOnly, editingId, selectStatistics)
        mutableAwaitingAuthorization.value = true
        mutableAuthorizationUrl.value = listeningUrl("https://www.last.fm", "api/auth/",
            mapOf("api_key" to connection.apiKey, "token" to token)).toString()
    }
    fun authorizationOpened() { mutableAuthorizationUrl.value = null }
    fun consumeMessage() { mutableMessage.value = null }
    fun cancelEditing() {
        authorization = null
        mutableAuthorizationUrl.value = null
        mutableAwaitingAuthorization.value = false
        mutableMessage.value = null
    }
    fun finishAuthorization() = action(network = true) {
        val (connection, token, statisticsOnly, editingId, selectStatistics) = authorization ?: return@action
        val authorized = lastFm.finishAuthorization(connection, token).withSubmissionPermission(!statisticsOnly && connection.allowSubmissions)
        store.update { it.saveConnection(authorized, editingId, selectStatistics) }
        mutableSavedConnection.value = ++saveSequence to store.state.value.savedConnections.first { sameListeningAccount(it, authorized) }.revision
        queue.schedule()
        authorization = null
        mutableAwaitingAuthorization.value = false
        mutableMessage.value = "Last.fm authorized and saved securely."
    }
}

internal fun retainAccountRevision(old: ListeningConnection?, updated: ListeningConnection): ListeningConnection {
    val sameAccount = old != null && sameListeningAccount(old, updated)
    return if (!sameAccount) updated else ListeningConnection(updated.provider, updated.serverUrl, updated.username,
        updated.token, updated.apiKey, updated.apiSecret, old!!.revision, updated.allowSubmissions)
}

data class ListeningStatisticsUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val statistics: ListeningStatistics? = null,
    val error: String? = null,
    val sourceLabel: String = "",
    val loadingMore: ListeningChart? = null,
    val pageError: String? = null,
    val isCached: Boolean = false
)

@HiltViewModel
class ListeningStatisticsViewModel @Inject constructor(
    private val store: ListeningSettingsStore,
    private val clients: ListeningClients,
    private val navidrome: NavidromeListeningClient,
    private val repository: NavidromeRepository,
    private val cache: ListeningStatisticsCache,
    artistAliases: ListeningArtistAliases? = null,
    private val localHistory: LocalListeningRepository? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(ListeningStatisticsUiState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var requestGeneration = 0L
    private var activeCacheKey: String? = null
    private var currentSession: com.stillshelf.app.core.model.NavidromeSession? = null
    private val libraryResolver = ListeningLibraryResolver(repository, artistAliases)
    private data class LibraryEntryKey(val kind: String, val title: String, val subtitle: String, val album: String, val artistMbid: String?, val releaseMbid: String?, val recordingMbid: String?)
    private val resolving = mutableSetOf<LibraryEntryKey>()
    private val libraryMatches = mutableMapOf<LibraryEntryKey, ListeningEntry>()

    private fun entryKey(entry: ListeningEntry, kind: String) = LibraryEntryKey(kind, entry.title, entry.subtitle, entry.albumName, entry.musicBrainzArtistId, entry.musicBrainzReleaseId, entry.musicBrainzRecordingId)

    private suspend fun localArtwork(stats: ListeningStatistics): ListeningStatistics {
        fun artId(row: ListeningEntry, artist: Boolean) = if (artist) row.artistId?.let { "ar-$it" } else row.albumId?.let { "al-$it" }
        val ids = (stats.topArtists.mapNotNull { artId(it, true) } +
            (stats.topAlbums + stats.topTracks + stats.recent).mapNotNull { artId(it, false) }).toSet()
        // Authenticated image URLs are constructed for the active account only, never persisted
        // in the local history database or included in its portable export.
        val urls = repository.listeningCoverArtUrls(ids)
        fun rows(entries: List<ListeningEntry>, artist: Boolean = false) = entries.map { row ->
            row.copy(artworkUrl = artId(row, artist)?.let(urls::get)) }
        return stats.copy(topArtists = rows(stats.topArtists, true), topAlbums = rows(stats.topAlbums),
            topTracks = rows(stats.topTracks), recent = rows(stats.recent))
    }

    private fun withLibraryMatches(stats: ListeningStatistics): ListeningStatistics {
        fun rows(entries: List<ListeningEntry>, kind: String) = entries.map { entry ->
            libraryMatches[entryKey(entry, kind)]?.let { match -> entry.copy(
                artworkUrl = match.artworkUrl ?: entry.artworkUrl, artistId = match.artistId, albumId = match.albumId) } ?: entry
        }
        return stats.copy(topArtists = rows(stats.topArtists, "artist"), topAlbums = rows(stats.topAlbums, "album"),
            topTracks = rows(stats.topTracks, "track"), recent = rows(stats.recent, "track"))
    }

    /** A tap retries a missing match instead of automatically leaving the app for a provider URL. */
    suspend fun resolveForNavigation(entry: ListeningEntry, kind: String): ListeningEntry? {
        if (entry.artistId != null || entry.albumId != null) return entry
        val generation = requestGeneration
        val key = entryKey(entry, kind)
        val match = libraryMatches[key] ?: try { libraryResolver.resolve(entry, kind, forceRefresh = true) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { entry }
        if (generation != requestGeneration) return null
        if (match.artistId != null || match.albumId != null) {
            libraryMatches[key] = match
            mutableState.value.statistics?.let { mutableState.value = mutableState.value.copy(statistics = withLibraryMatches(it)) }
        }
        return match
    }

    fun resolveEntry(entry: ListeningEntry, kind: String) {
        if (store.state.value.statisticsSource == StatisticsSource.NAVIDROME || entry.artistId != null || entry.albumId != null) return
        val key = entryKey(entry, kind)
        if (!resolving.add(key)) return
        val generation = requestGeneration
        viewModelScope.launch {
            val resolved = try { libraryResolver.resolve(entry, kind) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { entry }
            if (generation != requestGeneration) return@launch
            if (resolved.artistId == null && resolved.albumId == null) { resolving.remove(key); return@launch }
            libraryMatches[key] = resolved
            val current = mutableState.value
            val stats = current.statistics ?: return@launch
            fun replace(entries: List<ListeningEntry>) = entries.map { if (entryKey(it, kind) == key)
                it.copy(artworkUrl = resolved.artworkUrl, artistId = resolved.artistId, albumId = resolved.albumId) else it }
            mutableState.value = current.copy(statistics = when(kind) {
                "artist" -> stats.copy(topArtists = replace(stats.topArtists))
                "album" -> stats.copy(topAlbums = replace(stats.topAlbums))
                else -> stats.copy(topTracks = replace(stats.topTracks), recent = replace(stats.recent))
            })
        }
    }

    fun retryChart(chart: ListeningChart) {
        val settings = store.state.value
        val connection = settings.connection ?: return
        if (settings.statisticsSource != StatisticsSource.EXTERNAL || mutableState.value.loadingMore != null) return
        val generation = requestGeneration
        mutableState.value = mutableState.value.copy(loadingMore = chart, pageError = null)
        viewModelScope.launch {
            try {
                val page = clients.forProvider(connection.provider).chartPage(connection, chart, 0) ?: return@launch
                if (generation != requestGeneration) return@launch
                val current = mutableState.value
                val stats = current.statistics ?: return@launch
                val missing = stats.unavailableSections - ListeningMetric.valueOf(chart.name)
                val updated = stats.copy(unavailableSections = missing, note = if (missing.isEmpty()) null else stats.note,
                    moreCharts = if (page.hasMore) stats.moreCharts + chart else stats.moreCharts - chart)
                val linked = withLibraryMatches(when (chart) {
                    ListeningChart.ARTISTS -> updated.copy(topArtists = page.entries)
                    ListeningChart.ALBUMS -> updated.copy(topAlbums = page.entries)
                    ListeningChart.TRACKS -> updated.copy(topTracks = page.entries)
                })
                mutableState.value = current.copy(statistics = linked, isCached = current.isCached && missing.isNotEmpty())
                activeCacheKey?.let {
                    try { cache.put(it, linked) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Fresh rows remain usable. */ }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == requestGeneration) mutableState.value = mutableState.value.copy(pageError = "Could not refresh ${chart.name.lowercase()}. Try again later.") }
            finally { if (generation == requestGeneration) mutableState.value = mutableState.value.copy(loadingMore = null) }
        }
    }

    fun loadMore(chart: ListeningChart) {
        val settings = store.state.value
        val connection = settings.connection
        val current = mutableState.value
        val stats = current.statistics ?: return
        if (settings.statisticsSource == StatisticsSource.NAVIDROME || chart !in stats.moreCharts || current.loadingMore != null) return
        val generation = requestGeneration
        val offset = when(chart) { ListeningChart.ARTISTS -> stats.topArtists.size; ListeningChart.ALBUMS -> stats.topAlbums.size; ListeningChart.TRACKS -> stats.topTracks.size }
        mutableState.value = current.copy(loadingMore = chart, pageError = null)
        viewModelScope.launch {
            try {
                val page = if (settings.statisticsSource == StatisticsSource.LOCAL)
                    localHistory?.chartPage(chart, offset, repository.currentPlaybackSessionKey()) ?: return@launch
                else clients.forProvider(connection?.provider ?: return@launch).chartPage(connection, chart, offset) ?: return@launch
                if (generation != requestGeneration) return@launch
                val now = mutableState.value
                val data = now.statistics ?: return@launch
                val more = if (page.hasMore) data.moreCharts else data.moreCharts - chart
                mutableState.value = now.copy(loadingMore = null, statistics = when(chart) {
                    ListeningChart.ARTISTS -> withLibraryMatches(data.copy(topArtists = data.topArtists + page.entries, moreCharts = more))
                    ListeningChart.ALBUMS -> withLibraryMatches(data.copy(topAlbums = data.topAlbums + page.entries, moreCharts = more))
                    ListeningChart.TRACKS -> withLibraryMatches(data.copy(topTracks = data.topTracks + page.entries, moreCharts = more))
                })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == requestGeneration) mutableState.value = mutableState.value.copy(pageError = "Could not load more statistics. Try again.") }
            finally { if (generation == requestGeneration) mutableState.value = mutableState.value.copy(loadingMore = null) }
        }
    }

    init {
        localHistory?.let { history -> viewModelScope.launch {
            history.changes.collect { if (store.state.value.statisticsSource == StatisticsSource.LOCAL) refresh() }
        } }
        viewModelScope.launch {
            try { store.load() } catch (_: Exception) {
                mutableState.value = ListeningStatisticsUiState(error = "Secure listening settings could not be loaded.")
                return@launch
            }
            combine(store.state, repository.observeSession()) { settings, session ->
                currentSession = session
                // Submission settings do not affect statistics identity.
                Triple(settings.showStatistics, settings.statisticsSource,
                    if (settings.statisticsSource == StatisticsSource.EXTERNAL) settings.connection to session else session)
            }.distinctUntilChanged().collectLatest { refresh() }
        }
    }

    fun refresh() {
        val generation = ++requestGeneration
        resolving.clear()
        refreshJob?.cancel()
        val settings = store.state.value
        if (!settings.showStatistics) { mutableState.value = ListeningStatisticsUiState(); return }
        val sourceLabel = when (settings.statisticsSource) {
            StatisticsSource.NAVIDROME -> "Navidrome · Recorded history"
            StatisticsSource.LOCAL -> "Local Listening History · On this device"
            StatisticsSource.EXTERNAL -> settings.connection?.provider?.label ?: "External Scrobbling Service · Not configured"
        }
        // Local history is the durable source of truth, not a network snapshot. Never cache it
        // in the external-provider cache, which could outlive an explicit history deletion.
        val identityKey = listeningStatisticsCacheKey(settings, currentSession)
        val key = identityKey.takeUnless { settings.statisticsSource == StatisticsSource.LOCAL }
        if (identityKey != activeCacheKey) libraryMatches.clear()
        val previous = mutableState.value.statistics.takeIf { identityKey != null && identityKey == activeCacheKey }
        activeCacheKey = identityKey
        mutableState.value = ListeningStatisticsUiState(enabled = true, loading = true, sourceLabel = sourceLabel, statistics = previous)
        refreshJob = viewModelScope.launch {
            try {
                if (key != null && previous == null) {
                    val saved = try { cache.get(key) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (generation != requestGeneration) return@launch
                    if (saved != null) mutableState.value = mutableState.value.copy(statistics = withLibraryMatches(saved), sourceLabel = saved.sourceLabel, isCached = true)
                }
                val raw = when (settings.statisticsSource) {
                    StatisticsSource.NAVIDROME -> navidrome.statistics()
                    StatisticsSource.LOCAL -> {
                        require(settings.localRecordingEnabled) { "Enable local recording in Settings to use local statistics." }
                        checkNotNull(localHistory).statistics(repository.currentPlaybackSessionKey())
                    }
                    StatisticsSource.EXTERNAL -> {
                        val connection = settings.connection ?: throw IllegalArgumentException("Configure an external service in Scrobbling & Statistics.")
                        clients.forProvider(connection.provider).statistics(connection)
                    }
                }
                val stats = if (settings.statisticsSource == StatisticsSource.LOCAL) localArtwork(raw) else raw
                coroutineContext.ensureActive()
                val current = store.state.value
                if (generation != requestGeneration || !current.showStatistics || current.statisticsSource != settings.statisticsSource ||
                    (settings.statisticsSource == StatisticsSource.EXTERNAL && current.connection !== settings.connection)) return@launch
                // The current state contains only this cache identity, including a persisted snapshot.
                val persisted = if (key != null) try { cache.get(key) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null } else null
                if (generation != requestGeneration) return@launch
                val saved = mutableState.value.statistics?.retainingUnavailable(persisted) ?: persisted
                val linked = withLibraryMatches(stats.retainingUnavailable(saved))
                mutableState.value = ListeningStatisticsUiState(enabled = true, statistics = linked, sourceLabel = stats.sourceLabel,
                    isCached = saved != null && stats.unavailableSections.isNotEmpty())
                if (key != null) try { cache.put(key, linked) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Keep fresh data when cache storage is unavailable. */ }
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { failedRefresh(generation, sourceLabel, e.message) }
            catch (e: ListeningApiException) { failedRefresh(generation, sourceLabel, e.message) }
            catch (e: ListeningConnectionException) { failedRefresh(generation, sourceLabel, e.message) }
            catch (_: Exception) { failedRefresh(generation, sourceLabel, "Listening statistics are unavailable. Check your service connection and API support in Settings.") }
        }
    }

    private fun failedRefresh(generation: Long, sourceLabel: String, message: String?) {
        if (generation != requestGeneration) return
        val current = mutableState.value
        val cached = current.statistics != null
        mutableState.value = current.copy(enabled = true, loading = false, isCached = cached,
            sourceLabel = if (cached) current.statistics!!.sourceLabel else sourceLabel,
            error = if (cached) "Showing cached statistics. ${message.orEmpty()}" else message)
    }
}
