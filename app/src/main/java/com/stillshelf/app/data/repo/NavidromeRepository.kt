package com.stillshelf.app.data.repo

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.CachedNavidromeHomePayload
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.ActiveEndpointHealth
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.EndpointReachabilityStatus
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeHome
import com.stillshelf.app.core.model.NavidromeLibrary
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.NavidromeSession
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.network.NetworkConnectionType
import com.stillshelf.app.core.network.NetworkMonitor
import com.stillshelf.app.core.network.resolveActiveServerConnectionStatus
import com.stillshelf.app.core.network.shouldRetryLanOnWifi
import com.stillshelf.app.data.api.NavidromeAlbumDetailDto
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.data.api.NavidromeArtistDetailDto
import com.stillshelf.app.data.api.NavidromeAuth
import com.stillshelf.app.data.api.NavidromeRadioDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NavidromeAlbumSortOption(
    val key: String,
    val label: String
) {
    RECENT("recent", "Recent"),
    ALBUM_TITLE("album_title", "Album title"),
    ALBUM_ARTIST("album_artist", "Album artist"),
    RELEASE_YEAR("release_year", "Release year");

    companion object {
        fun fromKey(value: String?): NavidromeAlbumSortOption {
            return entries.firstOrNull { it.key == value } ?: RECENT
        }
    }
}

@Singleton
class NavidromeRepository @Inject constructor(
    private val navidromeApi: NavidromeApi,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val networkMonitor: NetworkMonitor
) {
    private companion object {
        const val NAVIDROME_PASSWORD_KEY = "navidrome_password"
        private const val ACTIVE_CONNECTION_CACHE_MAX_AGE_MS: Long = 30 * 1000L
        private const val HOME_CACHE_MAX_AGE_MS: Long = 10 * 60 * 1000L
        private const val PERSISTED_HOME_CACHE_MAX_AGE_MS: Long = 15 * 60 * 1000L
        private const val CONTENT_CACHE_MAX_AGE_MS: Long = 20 * 60 * 1000L
        private const val DETAIL_CACHE_MAX_AGE_MS: Long = 30 * 60 * 1000L
        private const val SEARCH_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
        private const val LAN_RETRY_INTERVAL_MS = 3_000L
        private const val LAN_FALLBACK_CONFIRMATION_DELAY_MS = 750L
        private const val HEALTH_PROBE_INTERVAL_MS = 30_000L
        private const val HEALTH_RETRY_INTERVAL_MS = 3_000L
    }

    private data class TimedCacheEntry<T>(
        val value: T,
        val savedAtMs: Long
    )

    private data class ResolvedNavidromeConnection(
        val auth: NavidromeAuth,
        val status: ActiveServerConnectionStatus,
        val checkedAtMs: Long
    )

    private val cacheMutex = Mutex()
    private val migrationMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val homeCache = mutableMapOf<String, TimedCacheEntry<NavidromeHome>>()
    private val librariesCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeLibrary>>>()
    private val albumsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeAlbum>>>()
    private val artistsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeArtist>>>()
    private val playlistsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromePlaylist>>>()
    private val radiosCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeRadio>>>()
    private val songsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeTrack>>>()
    private val artistDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeArtistDetail>>()
    private val albumDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeAlbumDetail>>()
    private val searchCache = mutableMapOf<String, TimedCacheEntry<NavidromeSearchResults>>()
    private val mutableActiveConnectionStatus = MutableStateFlow<ActiveServerConnectionStatus?>(null)
    private val mutableEndpointHealth = MutableStateFlow<ActiveEndpointHealth?>(null)
    private val resolvedConnectionsByServerId = mutableMapOf<String, ResolvedNavidromeConnection>()
    private val foregroundRefreshNonce = MutableStateFlow(0L)
    private val appInForeground = MutableStateFlow(
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(
            androidx.lifecycle.Lifecycle.State.STARTED
        )
    )
    @Volatile
    private var lastObservedResolvedStatus: ActiveServerConnectionStatus? = null
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appInForeground.value = true
            foregroundRefreshNonce.value += 1L
        }

        override fun onStop(owner: LifecycleOwner) {
            appInForeground.value = false
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        repositoryScope.launch {
            ensureMigratedLegacySession()
        }
        repositoryScope.launch {
            startObservingActiveConnectionRouting()
        }
        repositoryScope.launch {
            startObservingEndpointHealth()
        }
    }

    fun observeServers(): Flow<List<NavidromeServer>> = sessionPreferences.state.map { state ->
        state.navidromeServers.sortedBy { it.createdAt }
    }

    fun observeSession(): Flow<NavidromeSession?> = sessionPreferences.state.map { state ->
        val activeServer = resolveActiveServer(state) ?: return@map null
        NavidromeSession(
            serverId = activeServer.id,
            serverName = activeServer.name,
            baseUrl = activeServer.baseUrl,
            username = activeServer.username
        )
    }

    fun observeActiveLibraryId(): Flow<String?> = sessionPreferences.state.map { state ->
        val activeServer = resolveActiveServer(state) ?: return@map null
        state.navidromeActiveLibraryIds[activeServer.id]?.trim()?.takeIf { it.isNotBlank() }
    }

    fun observeActiveConnectionStatus(): Flow<ActiveServerConnectionStatus?> =
        mutableActiveConnectionStatus.asStateFlow()

    fun observeEndpointHealth(): Flow<ActiveEndpointHealth?> =
        mutableEndpointHealth.asStateFlow()

    suspend fun testServerConnection(baseUrl: String): AppResult<String> {
        val normalizedBaseUrl = navidromeApi.normalizeBaseUrl(baseUrl)
        if (normalizedBaseUrl.isBlank()) return AppResult.Error("Navidrome URL is required.")
        val result = navidromeApi.testServerConnection(normalizedBaseUrl)
        return if (result.isSuccess) {
            AppResult.Success("Server reachable. Continue to sign in.")
        } else {
            AppResult.Error(
                result.exceptionOrNull()?.message ?: "Unable to reach this Navidrome server.",
                result.exceptionOrNull()
            )
        }
    }

    suspend fun login(
        serverName: String,
        baseUrl: String,
        username: String,
        password: String
    ): AppResult<Unit> {
        ensureMigratedLegacySession()
        val normalizedServerName = serverName.trim()
        val normalizedBaseUrl = navidromeApi.normalizeBaseUrl(baseUrl)
        val normalizedUsername = username.trim()
        if (normalizedServerName.isBlank()) return AppResult.Error("Server name is required.")
        if (normalizedBaseUrl.isBlank()) return AppResult.Error("Navidrome URL is required.")
        if (normalizedUsername.isBlank()) return AppResult.Error("Username is required.")
        if (password.isBlank()) return AppResult.Error("Password is required.")

        val ping = navidromeApi.ping(normalizedBaseUrl, normalizedUsername, password)
        if (ping.isSuccess) {
            return try {
                val state = sessionPreferences.state.first()
                val existingServer = state.navidromeServers.firstOrNull { server ->
                    server.baseUrl.equals(normalizedBaseUrl, ignoreCase = true) &&
                        server.username.equals(normalizedUsername, ignoreCase = true)
                }
                val targetServer = existingServer?.copy(
                    name = normalizedServerName,
                    baseUrl = normalizedBaseUrl,
                    username = normalizedUsername
                ) ?: NavidromeServer(
                    id = UUID.randomUUID().toString(),
                    name = normalizedServerName,
                    baseUrl = normalizedBaseUrl,
                    username = normalizedUsername,
                    createdAt = System.currentTimeMillis()
                )
                val updatedServers = buildList {
                    add(targetServer)
                    state.navidromeServers
                        .filterNot { it.id == targetServer.id }
                        .forEach(::add)
                }
                clearCaches()
                sessionPreferences.clearCachedNavidromeHome()
                secureTokenStorage.saveNamedSecret(
                    key = passwordKey(targetServer.id),
                    value = password
                )
                secureTokenStorage.saveNamedSecret(
                    key = NAVIDROME_PASSWORD_KEY,
                    value = password
                )
                sessionPreferences.setNavidromeServers(updatedServers)
                sessionPreferences.setActiveNavidromeServerId(targetServer.id)
                sessionPreferences.setNavidromeSession(
                    serverName = targetServer.name,
                    baseUrl = targetServer.baseUrl,
                    username = targetServer.username
                )
                mutableActiveConnectionStatus.value = ActiveServerConnectionStatus(
                    serverId = targetServer.id,
                    effectiveBaseUrl = targetServer.baseUrl,
                    route = ServerConnectionRoute.Default,
                    connectionMode = ServerConnectionMode.Auto,
                    switchingEnabled = false
                )
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Error("Unable to save Navidrome session.", t)
            }
        }
        return AppResult.Error(
            ping.exceptionOrNull()?.message ?: "Unable to sign in to Navidrome.",
            ping.exceptionOrNull()
        )
    }

    suspend fun signOut() {
        ensureMigratedLegacySession()
        clearCaches()
        sessionPreferences.clearCachedNavidromeHome()
        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state)
        if (activeServer != null) {
            secureTokenStorage.clearNamedSecret(passwordKey(activeServer.id))
            resolvedConnectionsByServerId.remove(activeServer.id)
        } else {
            secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
        }
        secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
        sessionPreferences.clearNavidromeSession()
        mutableActiveConnectionStatus.value = null
        mutableEndpointHealth.value = null
    }

    suspend fun updateServer(
        serverId: String,
        name: String,
        baseUrl: String
    ): AppResult<Unit> {
        ensureMigratedLegacySession()
        val normalizedName = name.trim()
        val normalizedBaseUrl = navidromeApi.normalizeBaseUrl(baseUrl)
        if (normalizedName.length < 2) return AppResult.Error("Server name must be at least 2 characters.")
        if (normalizedBaseUrl.isBlank()) return AppResult.Error("Navidrome URL is required.")
        if (!normalizedBaseUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            return AppResult.Error("Base URL must start with http:// or https://")
        }
        return try {
            val state = sessionPreferences.state.first()
            val target = state.navidromeServers.firstOrNull { it.id == serverId }
                ?: return AppResult.Error("Server not found.")
            val duplicate = state.navidromeServers.firstOrNull { server ->
                server.id != serverId &&
                    server.baseUrl.equals(normalizedBaseUrl, ignoreCase = true) &&
                    server.username.equals(target.username, ignoreCase = true)
            }
            if (duplicate != null) {
                return AppResult.Error("A server with this URL already exists.")
            }
            val updatedServers = state.navidromeServers.map { server ->
                if (server.id == serverId) server.copy(name = normalizedName, baseUrl = normalizedBaseUrl) else server
            }
            sessionPreferences.setNavidromeServers(updatedServers)
            if (state.activeNavidromeServerId == serverId) {
                sessionPreferences.setNavidromeSession(
                    serverName = normalizedName,
                    baseUrl = normalizedBaseUrl,
                    username = target.username
                )
            }
            clearCaches()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to update server.", t)
        }
    }

    suspend fun deleteServer(serverId: String): AppResult<Unit> {
        ensureMigratedLegacySession()
        return try {
            val state = sessionPreferences.state.first()
            val target = state.navidromeServers.firstOrNull { it.id == serverId }
                ?: return AppResult.Error("Server not found.")
            val remainingServers = state.navidromeServers.filterNot { it.id == serverId }
            val nextActiveServer = if (state.activeNavidromeServerId == serverId) {
                remainingServers.firstOrNull()
            } else {
                remainingServers.firstOrNull { it.id == state.activeNavidromeServerId }
                    ?: remainingServers.firstOrNull()
            }
            sessionPreferences.setNavidromeServers(remainingServers)
            sessionPreferences.setActiveNavidromeServerId(nextActiveServer?.id)
            sessionPreferences.removeActiveNavidromeLibraryId(serverId)
            if (nextActiveServer != null) {
                sessionPreferences.setNavidromeSession(
                    serverName = nextActiveServer.name,
                    baseUrl = nextActiveServer.baseUrl,
                    username = nextActiveServer.username
                )
                secureTokenStorage.getNamedSecret(passwordKey(nextActiveServer.id))?.let { password ->
                    secureTokenStorage.saveNamedSecret(NAVIDROME_PASSWORD_KEY, password)
                } ?: secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
            } else {
                sessionPreferences.clearNavidromeSession()
                secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
            }
            sessionPreferences.removeServerEndpointSwitchingConfig(serverId)
            secureTokenStorage.clearNamedSecret(passwordKey(target.id))
            resolvedConnectionsByServerId.remove(serverId)
            clearCaches()
            if (nextActiveServer == null) {
                mutableActiveConnectionStatus.value = null
                mutableEndpointHealth.value = null
            }
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to delete server.", t)
        }
    }

    suspend fun setActiveServer(serverId: String): AppResult<Unit> {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val server = state.navidromeServers.firstOrNull { it.id == serverId }
            ?: return AppResult.Error("Server not found.")
        val password = secureTokenStorage.getNamedSecret(passwordKey(server.id))
            ?.trim()
            .orEmpty()
        if (password.isBlank()) {
            return AppResult.Error("No saved session for this server. Please log in again.")
        }
        val resolution = resolveConnection(server = server, password = password, config = serverSwitchingConfig(state, server.id))
        return if (resolution is AppResult.Success) {
            sessionPreferences.setActiveNavidromeServerId(server.id)
            sessionPreferences.setNavidromeSession(
                serverName = server.name,
                baseUrl = server.baseUrl,
                username = server.username
            )
            secureTokenStorage.saveNamedSecret(
                key = NAVIDROME_PASSWORD_KEY,
                value = password
            )
            AppResult.Success(Unit)
        } else {
            AppResult.Error(
                (resolution as AppResult.Error).message,
                resolution.cause
            )
        }
    }

    suspend fun updateServerEndpointSwitchingConfig(
        serverId: String,
        config: ServerEndpointSwitchingConfig
    ): AppResult<Unit> {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        if (state.navidromeServers.none { it.id == serverId }) {
            return AppResult.Error("Server not found.")
        }
        val normalizedLanBaseUrl = config.lanBaseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let(navidromeApi::normalizeBaseUrl)
        val normalizedWanBaseUrl = config.wanBaseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?.let(navidromeApi::normalizeBaseUrl)
        if (!normalizedLanBaseUrl.isNullOrBlank() &&
            !normalizedLanBaseUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedLanBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            return AppResult.Error("Local server URL must start with http:// or https://")
        }
        if (!normalizedWanBaseUrl.isNullOrBlank() &&
            !normalizedWanBaseUrl.startsWith("http://", ignoreCase = true) &&
            !normalizedWanBaseUrl.startsWith("https://", ignoreCase = true)
        ) {
            return AppResult.Error("Remote server URL must start with http:// or https://")
        }
        if (config.enabled && normalizedLanBaseUrl.isNullOrBlank()) {
            return AppResult.Error("Local server is required.")
        }
        if (config.enabled && normalizedWanBaseUrl.isNullOrBlank()) {
            return AppResult.Error("Remote server is required.")
        }
        if (config.connectionMode == ServerConnectionMode.Local && normalizedLanBaseUrl.isNullOrBlank()) {
            return AppResult.Error("Set the local server URL before using Local mode.")
        }
        if (config.connectionMode == ServerConnectionMode.Remote && normalizedWanBaseUrl.isNullOrBlank()) {
            return AppResult.Error("Set the remote server URL before using Remote mode.")
        }
        return try {
            sessionPreferences.setServerEndpointSwitchingConfig(
                serverId,
                config.copy(
                    lanBaseUrl = normalizedLanBaseUrl,
                    wanBaseUrl = normalizedWanBaseUrl
                )
            )
            resolvedConnectionsByServerId.remove(serverId)
            clearCaches()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to save advanced server settings.", t)
        }
    }

    suspend fun setServerEndpointConnectionMode(
        serverId: String,
        connectionMode: ServerConnectionMode
    ): AppResult<Unit> {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val currentConfig = state.serverEndpointSwitchingConfigs[serverId] ?: ServerEndpointSwitchingConfig()
        return updateServerEndpointSwitchingConfig(
            serverId = serverId,
            config = currentConfig.copy(connectionMode = connectionMode)
        )
    }

    suspend fun refreshActiveConnectionStatus(): AppResult<ActiveServerConnectionStatus?> {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state) ?: run {
            mutableActiveConnectionStatus.value = null
            return AppResult.Success(null)
        }
        val status = resolveActiveServerStatus(
            state = state,
            activeServer = activeServer,
            networkType = networkMonitor.currentConnectionType(),
            previousStatus = lastObservedResolvedStatus?.takeIf { it.serverId == activeServer.id }
        )
        mutableActiveConnectionStatus.value = status
        lastObservedResolvedStatus = status
        return AppResult.Success(status)
    }

    suspend fun fetchLibraries(forceRefresh: Boolean = false): AppResult<List<NavidromeLibrary>> = withAuth { auth ->
        val cacheKey = cacheKey(auth.copy(musicFolderId = null), "libraries")
        if (!forceRefresh) {
            getFreshCache(librariesCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(librariesCache, cacheKey)
        val result = navidromeApi.getMusicFolders(auth.copy(musicFolderId = null))
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load libraries.")
        }
        val libraries = result.getOrThrow()
            .map { NavidromeLibrary(id = it.id, name = it.name) }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        putCache(librariesCache, cacheKey, libraries)

        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state)
        if (activeServer != null && libraries.isNotEmpty()) {
            val selectedLibraryId = state.navidromeActiveLibraryIds[activeServer.id]
            if (selectedLibraryId.isNullOrBlank() || libraries.none { it.id == selectedLibraryId }) {
                sessionPreferences.setActiveNavidromeLibraryId(activeServer.id, libraries.first().id)
            }
        }
        AppResult.Success(libraries)
    }

    suspend fun setActiveLibrary(libraryId: String?): AppResult<Unit> {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state) ?: return AppResult.Error("Server not found.")
        sessionPreferences.setActiveNavidromeLibraryId(activeServer.id, libraryId)
        clearCaches()
        return AppResult.Success(Unit)
    }

    private suspend fun startObservingActiveConnectionRouting() {
        combine(
            sessionPreferences.state,
            networkMonitor.observeConnectionState(),
            foregroundRefreshNonce
        ) { state, networkState, refreshNonce ->
            Triple(state, networkState.type, refreshNonce)
        }.collectLatest { (state, networkType, _) ->
            val activeServer = resolveActiveServer(state)
            if (activeServer == null) {
                mutableActiveConnectionStatus.value = null
                lastObservedResolvedStatus = null
                return@collectLatest
            }
            var resolvedStatus = resolveActiveServerStatus(
                state = state,
                activeServer = activeServer,
                networkType = networkType,
                previousStatus = lastObservedResolvedStatus?.takeIf { it.serverId == activeServer.id }
            )
            mutableActiveConnectionStatus.value = resolvedStatus
            lastObservedResolvedStatus = resolvedStatus

            while (
                currentCoroutineContext().isActive &&
                shouldRetryLanOnWifi(
                    status = resolvedStatus,
                    networkType = networkType,
                    isAppInForeground = appInForeground.value
                )
            ) {
                delay(LAN_RETRY_INTERVAL_MS)
                resolvedStatus = resolveActiveServerStatus(
                    state = state,
                    activeServer = activeServer,
                    networkType = networkType,
                    previousStatus = resolvedStatus
                )
                mutableActiveConnectionStatus.value = resolvedStatus
                lastObservedResolvedStatus = resolvedStatus
            }
        }
    }

    private suspend fun startObservingEndpointHealth() {
        combine(
            mutableActiveConnectionStatus,
            sessionPreferences.state,
            networkMonitor.observeConnectionState()
        ) { status, state, networkState ->
            Triple(status, state, networkState.type)
        }.collectLatest { (status, state, _) ->
            val resolvedStatus = status ?: run {
                mutableEndpointHealth.value = null
                return@collectLatest
            }
            val activeServer = resolveActiveServer(state)?.takeIf { it.id == resolvedStatus.serverId } ?: run {
                mutableEndpointHealth.value = null
                return@collectLatest
            }
            val password = secureTokenStorage.getNamedSecret(passwordKey(activeServer.id))
                ?.trim()
                .orEmpty()
            if (password.isBlank()) {
                mutableEndpointHealth.value = null
                return@collectLatest
            }
            val auth = NavidromeAuth(
                serverId = activeServer.id,
                musicFolderId = state.navidromeActiveLibraryIds[activeServer.id]?.trim()?.takeIf { it.isNotBlank() },
                baseUrl = resolvedStatus.effectiveBaseUrl,
                canonicalBaseUrl = activeServer.baseUrl,
                username = activeServer.username,
                encPassword = navidromeApi.encodePassword(password)
            )
            mutableEndpointHealth.value = ActiveEndpointHealth(
                serverId = activeServer.id,
                endpointUrl = resolvedStatus.effectiveBaseUrl,
                reachabilityStatus = EndpointReachabilityStatus.Checking
            )
            while (currentCoroutineContext().isActive) {
                val health = probeEndpointHealth(auth)
                mutableEndpointHealth.value = health
                delay(
                    if (health.reachabilityStatus == EndpointReachabilityStatus.Reachable) {
                        HEALTH_PROBE_INTERVAL_MS
                    } else {
                        HEALTH_RETRY_INTERVAL_MS
                    }
                )
            }
        }
    }

    private suspend fun resolveActiveServerStatus(
        state: com.stillshelf.app.core.datastore.SessionPreferenceState,
        activeServer: NavidromeServer,
        networkType: NetworkConnectionType,
        previousStatus: ActiveServerConnectionStatus?
    ): ActiveServerConnectionStatus {
        return resolveActiveServerConnectionStatus(
            serverId = activeServer.id,
            serverBaseUrl = activeServer.baseUrl,
            config = serverSwitchingConfig(state, activeServer.id),
            networkType = networkType,
            previousStatus = previousStatus,
            confirmLocalFailureDelayMs = LAN_FALLBACK_CONFIRMATION_DELAY_MS,
            isLanReachable = ::isEndpointReachable
        )
    }

    private suspend fun probeEndpointHealth(auth: NavidromeAuth): ActiveEndpointHealth {
        val result = navidromeApi.measurePing(auth)
        return if (result.isSuccess) {
            ActiveEndpointHealth(
                serverId = auth.serverId,
                endpointUrl = auth.baseUrl,
                reachabilityStatus = EndpointReachabilityStatus.Reachable,
                latencyMs = result.getOrNull()
            )
        } else {
            ActiveEndpointHealth(
                serverId = auth.serverId,
                endpointUrl = auth.baseUrl,
                reachabilityStatus = EndpointReachabilityStatus.Unavailable
            )
        }
    }

    suspend fun fetchHome(forceRefresh: Boolean = false): AppResult<NavidromeHome> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "home")
        val sessionKey = cacheKey(auth, "persisted-home")
        if (!forceRefresh) {
            getFreshCache(homeCache, cacheKey, HOME_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
            getFreshPersistedHomeSnapshot(sessionKey)?.let { cached ->
                putCache(homeCache, cacheKey, cached)
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) {
            null
        } else {
            getAnyCache(homeCache, cacheKey) ?: getAnyPersistedHomeSnapshot(sessionKey)
        }
        coroutineScope {
            val recentAlbums = async { navidromeApi.getAlbumList(auth, type = "newest", size = 18) }
            val artists = async { navidromeApi.getArtists(auth) }
            val playlists = async { navidromeApi.getPlaylists(auth) }

            val albumResult = recentAlbums.await()
            val artistResult = artists.await()
            val playlistResult = playlists.await()

            if (albumResult.isFailure || artistResult.isFailure || playlistResult.isFailure) {
                staleCache?.let { return@coroutineScope AppResult.Success(it) }
                throw (
                    albumResult.exceptionOrNull()
                        ?: artistResult.exceptionOrNull()
                        ?: playlistResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to load Navidrome home.")
                    )
            }

            val albums = albumResult.getOrThrow()
            val artistItems = artistResult.getOrThrow()
            val playlistItems = playlistResult.getOrThrow()

            val home = NavidromeHome(
                recentAlbums = albums.map { it.toModel(auth) },
                artists = artistItems.map { it.toModel(auth) },
                playlists = playlistItems.map { it.toModel() },
                radios = emptyList()
            )
            putCache(homeCache, cacheKey, home)
            putCache(playlistsCache, cacheKey(auth, "playlists"), home.playlists)
            sessionPreferences.setCachedNavidromeHome(
                sessionKey = sessionKey,
                payload = serializeHome(home),
                savedAtMs = System.currentTimeMillis()
            )

            AppResult.Success(
                home
            )
        }
    }

    suspend fun fetchAlbums(
        sort: NavidromeAlbumSortOption = NavidromeAlbumSortOption.RECENT,
        forceRefresh: Boolean = false
    ): AppResult<List<NavidromeAlbum>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "albums:${sort.key}")
        if (!forceRefresh) {
            getFreshCache(albumsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(albumsCache, cacheKey)
        val apiType = when (sort) {
            NavidromeAlbumSortOption.RECENT -> "newest"
            NavidromeAlbumSortOption.ALBUM_TITLE,
            NavidromeAlbumSortOption.ALBUM_ARTIST,
            NavidromeAlbumSortOption.RELEASE_YEAR -> "alphabeticalByName"
        }
        val result = navidromeApi.getAlbumList(auth, type = apiType, size = 200)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load albums.")
        }
        val albums = result.getOrThrow()
            .map { it.toModel(auth) }

        val sorted = when (sort) {
            NavidromeAlbumSortOption.RECENT -> albums
            NavidromeAlbumSortOption.ALBUM_TITLE -> albums.sortedBy { it.name.lowercase() }
            NavidromeAlbumSortOption.ALBUM_ARTIST -> albums.sortedWith(
                compareBy<NavidromeAlbum> { it.artistName.lowercase() }
                    .thenBy { it.name.lowercase() }
            )

            NavidromeAlbumSortOption.RELEASE_YEAR -> albums.sortedWith(
                compareByDescending<NavidromeAlbum> { it.year ?: Int.MIN_VALUE }
                    .thenBy { it.artistName.lowercase() }
                    .thenBy { it.name.lowercase() }
            )
        }
        putCache(albumsCache, cacheKey, sorted)
        AppResult.Success(sorted)
    }

    suspend fun fetchArtists(forceRefresh: Boolean = false): AppResult<List<NavidromeArtist>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "artists")
        if (!forceRefresh) {
            getFreshCache(artistsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(artistsCache, cacheKey)
        val result = navidromeApi.getArtists(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load artists.")
        }
        val artists = result.getOrThrow()
            .map { it.toModel(auth) }
            .sortedBy { it.name.lowercase() }
        putCache(artistsCache, cacheKey, artists)
        AppResult.Success(artists)
    }

    suspend fun fetchPlaylists(forceRefresh: Boolean = false): AppResult<List<NavidromePlaylist>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "playlists")
        if (!forceRefresh) {
            getFreshCache(playlistsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(playlistsCache, cacheKey)
        val result = navidromeApi.getPlaylists(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load playlists.")
        }
        val playlists = result.getOrThrow().map { it.toModel() }
        putCache(playlistsCache, cacheKey, playlists)
        AppResult.Success(playlists)
    }

    suspend fun fetchRadios(forceRefresh: Boolean = false): AppResult<List<NavidromeRadio>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "radios")
        if (!forceRefresh) {
            getFreshCache(radiosCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(radiosCache, cacheKey)
        val result = navidromeApi.getRadios(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load radios.")
        }
        val radios = result.getOrThrow()
            .map { it.toModel() }
            .sortedBy { it.name.lowercase() }
        putCache(radiosCache, cacheKey, radios)
        AppResult.Success(radios)
    }

    suspend fun fetchSongs(forceRefresh: Boolean = false): AppResult<List<NavidromeTrack>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "songs")
        if (!forceRefresh) {
            getFreshCache(songsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(songsCache, cacheKey)
        val result = navidromeApi.getSongs(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load songs.")
        }
        val songs = result.getOrThrow()
            .map { it.toModel(auth) }
            .sortedWith(
                compareBy<NavidromeTrack> { it.artistName.lowercase() }
                    .thenBy { it.albumName.lowercase() }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        putCache(songsCache, cacheKey, songs)
        AppResult.Success(songs)
    }

    suspend fun fetchArtistDetail(
        artistId: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeArtistDetail> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "artist:$artistId")
        if (!forceRefresh) {
            getFreshCache(artistDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(artistDetailCache, cacheKey)
        val result = navidromeApi.getArtist(auth, artistId)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load artist.")
        }
        val detail = result.getOrThrow().toModel(auth)
        putCache(artistDetailCache, cacheKey, detail)
        AppResult.Success(detail)
    }

    suspend fun fetchAlbumDetail(
        albumId: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeAlbumDetail> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "album:$albumId")
        if (!forceRefresh) {
            getFreshCache(albumDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(albumDetailCache, cacheKey)
        val result = navidromeApi.getAlbum(auth, albumId)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load album.")
        }
        val detail = result.getOrThrow().toModel(auth)
        putCache(albumDetailCache, cacheKey, detail)
        AppResult.Success(detail)
    }

    suspend fun search(
        query: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeSearchResults> = withAuth { auth ->
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return@withAuth AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
        }
        val cacheKey = cacheKey(auth, "search:${normalizedQuery.lowercase()}")
        if (!forceRefresh) {
            getFreshCache(searchCache, cacheKey, SEARCH_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(searchCache, cacheKey)
        val result = navidromeApi.search(auth, normalizedQuery)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to search Navidrome.")
        }
        val apiResults = result.getOrThrow()
        val results = NavidromeSearchResults(
            artists = apiResults.artists.map { it.toModel(auth) },
            albums = apiResults.albums.map { it.toModel(auth) },
            tracks = apiResults.tracks.map { it.toModel(auth) }
        )
        putCache(searchCache, cacheKey, results)
        AppResult.Success(results)
    }

    private suspend fun currentAuth(): NavidromeAuth? {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state) ?: return null
        val password = secureTokenStorage.getNamedSecret(passwordKey(activeServer.id))
            ?.trim()
            .orEmpty()
        if (password.isBlank()) return null
        val musicFolderId = state.navidromeActiveLibraryIds[activeServer.id]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        mutableActiveConnectionStatus.value
            ?.takeIf { it.serverId == activeServer.id }
            ?.let { status ->
                return NavidromeAuth(
                    serverId = activeServer.id,
                    musicFolderId = musicFolderId,
                    baseUrl = status.effectiveBaseUrl,
                    canonicalBaseUrl = activeServer.baseUrl,
                    username = activeServer.username,
                    encPassword = navidromeApi.encodePassword(password)
                )
            }
        return when (
            val result = resolveConnection(
                server = activeServer,
                password = password,
                config = serverSwitchingConfig(state, activeServer.id)
            )
        ) {
            is AppResult.Success -> result.value.auth.copy(musicFolderId = musicFolderId)
            is AppResult.Error -> null
        }
    }

    private suspend fun <T> withAuth(
        block: suspend (NavidromeAuth) -> AppResult<T>
    ): AppResult<T> {
        val auth = currentAuth() ?: return AppResult.Error("Navidrome session expired. Please sign in again.")
        return try {
            block(auth)
        } catch (t: Throwable) {
            AppResult.Error(t.message ?: "Navidrome request failed.", t)
        }
    }

    private fun cacheKey(auth: NavidromeAuth, suffix: String): String {
        val libraryId = auth.musicFolderId?.trim()?.takeIf { it.isNotBlank() } ?: "_all"
        return "${auth.serverId.lowercase()}|${libraryId.lowercase()}|$suffix"
    }

    private suspend fun ensureMigratedLegacySession() {
        migrationMutex.withLock {
            val state = sessionPreferences.state.first()
            if (state.navidromeServers.isNotEmpty()) return
            val baseUrl = state.navidromeBaseUrl?.trim().orEmpty()
            val username = state.navidromeUsername?.trim().orEmpty()
            if (baseUrl.isBlank() || username.isBlank()) return
            val server = NavidromeServer(
                id = UUID.randomUUID().toString(),
                name = state.navidromeServerName?.trim()?.takeIf { it.isNotBlank() }
                    ?: formatFallbackServerName(baseUrl),
                baseUrl = navidromeApi.normalizeBaseUrl(baseUrl),
                username = username,
                createdAt = System.currentTimeMillis()
            )
            sessionPreferences.setNavidromeServers(listOf(server))
            sessionPreferences.setActiveNavidromeServerId(server.id)
            val legacyPassword = secureTokenStorage.getNamedSecret(NAVIDROME_PASSWORD_KEY)
            if (!legacyPassword.isNullOrBlank()) {
                secureTokenStorage.saveNamedSecret(passwordKey(server.id), legacyPassword)
            }
        }
    }

    private fun resolveActiveServer(
        state: com.stillshelf.app.core.datastore.SessionPreferenceState
    ): NavidromeServer? {
        val requestedId = state.activeNavidromeServerId
        return state.navidromeServers.firstOrNull { it.id == requestedId }
            ?: state.navidromeServers.firstOrNull()
            ?: legacyServerFromState(state)
    }

    private fun legacyServerFromState(
        state: com.stillshelf.app.core.datastore.SessionPreferenceState
    ): NavidromeServer? {
        val baseUrl = state.navidromeBaseUrl?.trim().orEmpty()
        val username = state.navidromeUsername?.trim().orEmpty()
        if (baseUrl.isBlank() || username.isBlank()) return null
        return NavidromeServer(
            id = "legacy",
            name = state.navidromeServerName?.trim()?.takeIf { it.isNotBlank() }
                ?: formatFallbackServerName(baseUrl),
            baseUrl = baseUrl,
            username = username,
            createdAt = 0L
        )
    }

    private fun serverSwitchingConfig(
        state: com.stillshelf.app.core.datastore.SessionPreferenceState,
        serverId: String
    ): ServerEndpointSwitchingConfig {
        return state.serverEndpointSwitchingConfigs[serverId] ?: ServerEndpointSwitchingConfig()
    }

    private suspend fun resolveConnection(
        server: NavidromeServer,
        password: String,
        config: ServerEndpointSwitchingConfig
    ): AppResult<ResolvedNavidromeConnection> {
        val now = System.currentTimeMillis()
        val cached = resolvedConnectionsByServerId[server.id]
        if (cached != null && (now - cached.checkedAtMs) <= ACTIVE_CONNECTION_CACHE_MAX_AGE_MS) {
            mutableActiveConnectionStatus.value = cached.status
            return AppResult.Success(cached)
        }

        val normalizedDefaultBaseUrl = navidromeApi.normalizeBaseUrl(server.baseUrl)
        val normalizedLocalBaseUrl = config.lanBaseUrl?.let(navidromeApi::normalizeBaseUrl)
        val normalizedRemoteBaseUrl = config.wanBaseUrl?.let(navidromeApi::normalizeBaseUrl)
        val switchingEnabled = config.enabled &&
            !normalizedLocalBaseUrl.isNullOrBlank() &&
            !normalizedRemoteBaseUrl.isNullOrBlank()

        val resolved = when (config.connectionMode) {
            ServerConnectionMode.Local -> {
                val localBaseUrl = normalizedLocalBaseUrl
                    ?: return AppResult.Error("Set the local server URL before using Local mode.")
                buildResolvedConnection(
                    server = server,
                    effectiveBaseUrl = localBaseUrl,
                    route = ServerConnectionRoute.Local,
                    connectionMode = config.connectionMode,
                    switchingEnabled = switchingEnabled,
                    lanBaseUrl = normalizedLocalBaseUrl,
                    wanBaseUrl = normalizedRemoteBaseUrl,
                    password = password
                )
            }

            ServerConnectionMode.Remote -> {
                val remoteBaseUrl = normalizedRemoteBaseUrl ?: normalizedDefaultBaseUrl
                buildResolvedConnection(
                    server = server,
                    effectiveBaseUrl = remoteBaseUrl,
                    route = if (normalizedRemoteBaseUrl.isNullOrBlank()) {
                        ServerConnectionRoute.Default
                    } else {
                        ServerConnectionRoute.Remote
                    },
                    connectionMode = config.connectionMode,
                    switchingEnabled = switchingEnabled,
                    lanBaseUrl = normalizedLocalBaseUrl,
                    wanBaseUrl = normalizedRemoteBaseUrl,
                    password = password
                )
            }

            ServerConnectionMode.Auto -> {
                if (!switchingEnabled) {
                    buildResolvedConnection(
                        server = server,
                        effectiveBaseUrl = normalizedDefaultBaseUrl,
                        route = ServerConnectionRoute.Default,
                        connectionMode = config.connectionMode,
                        switchingEnabled = false,
                        lanBaseUrl = normalizedLocalBaseUrl,
                        wanBaseUrl = normalizedRemoteBaseUrl,
                        password = password
                    )
                } else if (isEndpointReachable(normalizedLocalBaseUrl!!)) {
                    buildResolvedConnection(
                        server = server,
                        effectiveBaseUrl = normalizedLocalBaseUrl,
                        route = ServerConnectionRoute.Local,
                        connectionMode = config.connectionMode,
                        switchingEnabled = true,
                        lanBaseUrl = normalizedLocalBaseUrl,
                        wanBaseUrl = normalizedRemoteBaseUrl,
                        password = password
                    )
                } else {
                    buildResolvedConnection(
                        server = server,
                        effectiveBaseUrl = normalizedRemoteBaseUrl!!,
                        route = ServerConnectionRoute.Remote,
                        connectionMode = config.connectionMode,
                        switchingEnabled = true,
                        lanFallbackToRemote = true,
                        lanBaseUrl = normalizedLocalBaseUrl,
                        wanBaseUrl = normalizedRemoteBaseUrl,
                        password = password
                    )
                }
            }
        }

        return if (resolved is AppResult.Success) {
            resolvedConnectionsByServerId[server.id] = resolved.value
            mutableActiveConnectionStatus.value = resolved.value.status
            resolved
        } else {
            resolved
        }
    }

    private suspend fun buildResolvedConnection(
        server: NavidromeServer,
        effectiveBaseUrl: String,
        route: ServerConnectionRoute,
        connectionMode: ServerConnectionMode,
        switchingEnabled: Boolean,
        lanFallbackToRemote: Boolean = false,
        lanBaseUrl: String?,
        wanBaseUrl: String?,
        password: String
    ): AppResult<ResolvedNavidromeConnection> {
        val ping = navidromeApi.ping(effectiveBaseUrl, server.username, password)
        if (ping.isFailure) {
            return AppResult.Error(
                ping.exceptionOrNull()?.message ?: "Unable to reach this Navidrome server.",
                ping.exceptionOrNull()
            )
        }
        return AppResult.Success(
            ResolvedNavidromeConnection(
                auth = NavidromeAuth(
                    serverId = server.id,
                    canonicalBaseUrl = server.baseUrl,
                    baseUrl = effectiveBaseUrl,
                    username = server.username,
                    encPassword = navidromeApi.encodePassword(password)
                ),
                status = ActiveServerConnectionStatus(
                    serverId = server.id,
                    effectiveBaseUrl = effectiveBaseUrl,
                    route = route,
                    connectionMode = connectionMode,
                    switchingEnabled = switchingEnabled,
                    lanFallbackToRemote = lanFallbackToRemote,
                    lanBaseUrl = lanBaseUrl,
                    wanBaseUrl = wanBaseUrl
                ),
                checkedAtMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun isEndpointReachable(baseUrl: String): Boolean {
        return navidromeApi.testServerConnection(baseUrl).isSuccess
    }

    private fun passwordKey(serverId: String): String = "${NAVIDROME_PASSWORD_KEY}_$serverId"

    private fun formatFallbackServerName(baseUrl: String): String {
        return runCatching {
            java.net.URI(baseUrl).host?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Navidrome"
    }

    private suspend fun <T> getFreshCache(
        source: Map<String, TimedCacheEntry<T>>,
        key: String,
        maxAgeMs: Long
    ): T? = cacheMutex.withLock {
        val entry = source[key] ?: return@withLock null
        if ((System.currentTimeMillis() - entry.savedAtMs) <= maxAgeMs) {
            entry.value
        } else {
            null
        }
    }

    private suspend fun <T> getAnyCache(
        source: Map<String, TimedCacheEntry<T>>,
        key: String
    ): T? = cacheMutex.withLock {
        source[key]?.value
    }

    private suspend fun <T> putCache(
        destination: MutableMap<String, TimedCacheEntry<T>>,
        key: String,
        value: T
    ) {
        cacheMutex.withLock {
            destination[key] = TimedCacheEntry(
                value = value,
                savedAtMs = System.currentTimeMillis()
            )
        }
    }

    private suspend fun clearCaches() {
        cacheMutex.withLock {
            homeCache.clear()
            librariesCache.clear()
            albumsCache.clear()
            artistsCache.clear()
            playlistsCache.clear()
            radiosCache.clear()
            songsCache.clear()
            artistDetailCache.clear()
            albumDetailCache.clear()
            searchCache.clear()
        }
    }

    private suspend fun getFreshPersistedHomeSnapshot(sessionKey: String): NavidromeHome? {
        val cached = sessionPreferences.getCachedNavidromeHome() ?: return null
        if (cached.sessionKey != sessionKey) return null
        val ageMs = (System.currentTimeMillis() - cached.savedAtMs).coerceAtLeast(0L)
        if (ageMs > PERSISTED_HOME_CACHE_MAX_AGE_MS) return null
        return parseHome(cached)
    }

    private suspend fun getAnyPersistedHomeSnapshot(sessionKey: String): NavidromeHome? {
        val cached = sessionPreferences.getCachedNavidromeHome() ?: return null
        if (cached.sessionKey != sessionKey) return null
        return parseHome(cached)
    }

    private fun serializeHome(home: NavidromeHome): String {
        return JSONObject()
            .put("recentAlbums", JSONArray().apply {
                home.recentAlbums.forEach { album ->
                    put(
                        JSONObject()
                            .put("id", album.id)
                            .put("name", album.name)
                            .put("artistName", album.artistName)
                            .put("artistId", album.artistId)
                            .put("year", album.year)
                            .put("songCount", album.songCount)
                            .put("durationSeconds", album.durationSeconds)
                            .put("coverUrl", album.coverUrl)
                            .put("genre", album.genre)
                    )
                }
            })
            .put("artists", JSONArray().apply {
                home.artists.forEach { artist ->
                    put(
                        JSONObject()
                            .put("id", artist.id)
                            .put("name", artist.name)
                            .put("albumCount", artist.albumCount)
                            .put("coverUrl", artist.coverUrl)
                            .put("imageUrl", artist.imageUrl)
                    )
                }
            })
            .put("playlists", JSONArray().apply {
                home.playlists.forEach { playlist ->
                    put(
                        JSONObject()
                            .put("id", playlist.id)
                            .put("name", playlist.name)
                            .put("songCount", playlist.songCount)
                            .put("durationSeconds", playlist.durationSeconds)
                    )
                }
            })
            .put("radios", JSONArray().apply {
                home.radios.forEach { radio ->
                    put(
                        JSONObject()
                            .put("id", radio.id)
                            .put("name", radio.name)
                            .put("streamUrl", radio.streamUrl)
                            .put("homePageUrl", radio.homePageUrl)
                    )
                }
            })
            .toString()
    }

    private fun parseHome(cached: CachedNavidromeHomePayload): NavidromeHome? {
        val root = runCatching { org.json.JSONObject(cached.payload) }.getOrNull() ?: return null
        return NavidromeHome(
            recentAlbums = buildList {
                val source = root.optJSONArray("recentAlbums") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromeAlbum(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            artistName = item.optString("artistName"),
                            artistId = item.optString("artistId").ifBlank { null },
                            year = item.optInt("year").takeIf { item.has("year") && !item.isNull("year") },
                            songCount = item.optInt("songCount"),
                            durationSeconds = item.optInt("durationSeconds").takeIf {
                                item.has("durationSeconds") && !item.isNull("durationSeconds")
                            },
                            coverUrl = item.optString("coverUrl").ifBlank { null },
                            genre = item.optString("genre").ifBlank { null }
                        )
                    )
                }
            },
            artists = buildList {
                val source = root.optJSONArray("artists") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromeArtist(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            albumCount = item.optInt("albumCount"),
                            coverUrl = item.optString("coverUrl").ifBlank { null },
                            imageUrl = item.optString("imageUrl").ifBlank { null }
                        )
                    )
                }
            },
            playlists = buildList {
                val source = root.optJSONArray("playlists") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromePlaylist(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            songCount = item.optInt("songCount").takeIf {
                                item.has("songCount") && !item.isNull("songCount")
                            },
                            durationSeconds = item.optInt("durationSeconds").takeIf {
                                item.has("durationSeconds") && !item.isNull("durationSeconds")
                            }
                        )
                    )
                }
            },
            radios = buildList {
                val source = root.optJSONArray("radios") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    val streamUrl = item.optString("streamUrl")
                        .ifBlank { item.optString("url") }
                        .ifBlank { null }
                        ?: continue
                    add(
                        NavidromeRadio(
                            id = item.optString("id"),
                            name = item.optString("name").ifBlank { "Radio" },
                            streamUrl = streamUrl,
                            homePageUrl = item.optString("homePageUrl")
                                .ifBlank { item.optString("homepageUrl") }
                                .ifBlank { null }
                        )
                    )
                }
            }
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeArtistDto.toModel(
        auth: NavidromeAuth
    ): NavidromeArtist {
        return NavidromeArtist(
            id = id,
            name = name,
            albumCount = albumCount,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 400),
            imageUrl = artistImageUrl
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeAlbumDto.toModel(
        auth: NavidromeAuth
    ): NavidromeAlbum {
        return NavidromeAlbum(
            id = id,
            name = name,
            artistName = artistName,
            artistId = artistId,
            year = year,
            songCount = songCount,
            durationSeconds = durationSeconds,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 600),
            genre = genre
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeTrackDto.toModel(
        auth: NavidromeAuth
    ): NavidromeTrack {
        return NavidromeTrack(
            id = id,
            title = title,
            artistName = artistName,
            albumName = albumName,
            albumId = albumId,
            artistId = artistId,
            trackNumber = trackNumber,
            durationSeconds = durationSeconds,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 400),
            streamUrl = navidromeApi.streamUrl(auth, id),
            formatLabel = suffix?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
                ?: contentType
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.uppercase(),
            bitRateKbps = bitRateKbps
        )
    }

    private fun com.stillshelf.app.data.api.NavidromePlaylistDto.toModel(): NavidromePlaylist {
        return NavidromePlaylist(
            id = id,
            name = name,
            songCount = songCount,
            durationSeconds = durationSeconds
        )
    }

    private fun NavidromeRadioDto.toModel(): NavidromeRadio {
        return NavidromeRadio(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl
        )
    }

    private fun NavidromeArtistDetailDto.toModel(auth: NavidromeAuth): NavidromeArtistDetail {
        return NavidromeArtistDetail(
            artist = artist.toModel(auth),
            albums = albums.map { it.toModel(auth) }
        )
    }

    private fun NavidromeAlbumDetailDto.toModel(auth: NavidromeAuth): NavidromeAlbumDetail {
        return NavidromeAlbumDetail(
            album = album.toModel(auth),
            tracks = tracks.map { it.toModel(auth) }
        )
    }
}
