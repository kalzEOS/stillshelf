package com.stillshelf.app.data.repo

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.CachedNavidromeHomePayload
import com.stillshelf.app.core.datastore.CachedNavidromeLyricsPayload
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
import com.stillshelf.app.core.model.NavidromeLibraryResyncProgress
import com.stillshelf.app.core.model.NavidromeLyrics
import com.stillshelf.app.core.model.NavidromeLyricsLine
import com.stillshelf.app.core.model.NavidromeLyricsSource
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlaylistDetail
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.NavidromeServerScanStatus
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
import com.stillshelf.app.data.api.NavidromePlaylistDetailDto
import com.stillshelf.app.data.api.NavidromeGenreDto
import com.stillshelf.app.data.api.NavidromeRadioDto
import com.stillshelf.app.data.api.NavidromeStructuredLyricsDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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

data class NavidromePlaylistAddOutcome(
    val addedCount: Int,
    val duplicateCount: Int
)

private data class NavidromeLyricsLookup(
    val artistName: String,
    val trackName: String,
    val albumName: String? = null,
    val durationSeconds: Int? = null
)

internal data class NavidromeLyricsCacheKeys(
    val sourceScopedKey: String,
    val fallbackKey: String
)

internal fun buildNavidromeLyricsCacheKeys(
    cachePrefix: String,
    trackId: String,
    activeLyricsSourceId: String?
): NavidromeLyricsCacheKeys {
    val normalizedSourceId = activeLyricsSourceId?.trim().orEmpty().ifBlank { "none" }
    return NavidromeLyricsCacheKeys(
        sourceScopedKey = "${cachePrefix}lyrics:${trackId}:source:$normalizedSourceId",
        fallbackKey = "${cachePrefix}lyrics:${trackId}:fallback"
    )
}

internal fun buildNavidromeLyricsCachePrefixForTrack(cachePrefix: String, trackId: String): String {
    return "${cachePrefix}lyrics:${trackId}:"
}

internal sealed interface NavidromeLoginServerPlan {
    data class Create(
        val server: NavidromeServer
    ) : NavidromeLoginServerPlan

    data class ReuseExisting(
        val server: NavidromeServer
    ) : NavidromeLoginServerPlan

    data object RejectDuplicate : NavidromeLoginServerPlan
}

internal fun planNavidromeLoginServer(
    existingServers: List<NavidromeServer>,
    normalizedServerName: String,
    normalizedBaseUrl: String,
    normalizedUsername: String,
    serverIdsWithSavedPassword: Set<String>,
    nowMs: Long,
    newServerId: String
): NavidromeLoginServerPlan {
    val duplicateServer = existingServers.firstOrNull { server ->
        server.baseUrl.equals(normalizedBaseUrl, ignoreCase = true)
    }
    if (duplicateServer != null) {
        return if (serverIdsWithSavedPassword.contains(duplicateServer.id)) {
            NavidromeLoginServerPlan.RejectDuplicate
        } else {
            NavidromeLoginServerPlan.ReuseExisting(
                server = duplicateServer.copy(
                    name = normalizedServerName,
                    baseUrl = normalizedBaseUrl,
                    username = normalizedUsername
                )
            )
        }
    }

    return NavidromeLoginServerPlan.Create(
        server = NavidromeServer(
            id = newServerId,
            name = normalizedServerName,
            baseUrl = normalizedBaseUrl,
            username = normalizedUsername,
            createdAt = nowMs
        )
    )
}

@Singleton
class NavidromeRepository @Inject constructor(
    private val navidromeApi: NavidromeApi,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val networkMonitor: NetworkMonitor,
    private val okHttpClient: OkHttpClient
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
    private val genresCache = mutableMapOf<String, TimedCacheEntry<List<String>>>()
    private val artistDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeArtistDetail>>()
    private val albumDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeAlbumDetail>>()
    private val playlistDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromePlaylistDetail>>()
    private val searchCache = mutableMapOf<String, TimedCacheEntry<NavidromeSearchResults>>()
    private val lyricsCache = mutableMapOf<String, TimedCacheEntry<NavidromeLyrics>>()
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
        val state = sessionPreferences.state.first()
        val loginPlan = planNavidromeLoginServer(
            existingServers = state.navidromeServers,
            normalizedServerName = normalizedServerName,
            normalizedBaseUrl = normalizedBaseUrl,
            normalizedUsername = normalizedUsername,
            serverIdsWithSavedPassword = state.navidromeServers
                .mapNotNull { server ->
                    server.id.takeIf {
                        secureTokenStorage.getNamedSecret(passwordKey(server.id))
                            ?.trim()
                            ?.isNotBlank() == true
                    }
                }
                .toSet(),
            nowMs = System.currentTimeMillis(),
            newServerId = UUID.randomUUID().toString()
        )
        if (loginPlan is NavidromeLoginServerPlan.RejectDuplicate) {
            return AppResult.Error("This server already exists.")
        }

        val ping = navidromeApi.ping(normalizedBaseUrl, normalizedUsername, password)
        if (ping.isSuccess) {
            return try {
                val targetServer = when (loginPlan) {
                    is NavidromeLoginServerPlan.Create -> loginPlan.server
                    is NavidromeLoginServerPlan.ReuseExisting -> loginPlan.server
                    NavidromeLoginServerPlan.RejectDuplicate -> error("duplicate plan handled above")
                }
                val updatedServers: List<NavidromeServer> = when (loginPlan) {
                    is NavidromeLoginServerPlan.Create -> buildList {
                        add(targetServer)
                        state.navidromeServers.forEach(::add)
                    }
                    is NavidromeLoginServerPlan.ReuseExisting -> state.navidromeServers.map { server ->
                        if (server.id == targetServer.id) targetServer else server
                    }
                    NavidromeLoginServerPlan.RejectDuplicate -> error("duplicate plan handled above")
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
                    server.baseUrl.equals(normalizedBaseUrl, ignoreCase = true)
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

    suspend fun resyncLibrary(
        onProgress: suspend (NavidromeLibraryResyncProgress) -> Unit
    ): AppResult<List<NavidromeLibrary>> = withAuth {
        val steps = listOf(
            "Refreshing libraries" to "Loading available Navidrome libraries.",
            "Refreshing home" to "Updating recent albums, artists, and playlists.",
            "Refreshing albums" to "Loading album data from the server.",
            "Refreshing artists" to "Loading artist data from the server.",
            "Refreshing songs" to "Refreshing the full song list."
        )

        suspend fun publishStep(index: Int) {
            val (title, detail) = steps[index]
            onProgress(
                NavidromeLibraryResyncProgress(
                    title = title,
                    detail = detail,
                    completedSteps = index,
                    totalSteps = steps.size
                )
            )
        }

        publishStep(0)
        val libraries = when (val result = fetchLibraries(forceRefresh = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return@withAuth result
        }

        publishStep(1)
        when (val result = fetchHome(forceRefresh = true)) {
            is AppResult.Success -> Unit
            is AppResult.Error -> return@withAuth result
        }

        publishStep(2)
        when (val result = fetchAlbums(
            sort = NavidromeAlbumSortOption.RECENT,
            forceRefresh = true
        )) {
            is AppResult.Success -> Unit
            is AppResult.Error -> return@withAuth result
        }

        publishStep(3)
        when (val result = fetchArtists(forceRefresh = true)) {
            is AppResult.Success -> Unit
            is AppResult.Error -> return@withAuth result
        }

        publishStep(4)
        when (val result = fetchSongs(forceRefresh = true)) {
            is AppResult.Success -> Unit
            is AppResult.Error -> return@withAuth result
        }

        invalidateArtistAndAlbumDetailCaches()
        AppResult.Success(libraries)
    }

    suspend fun fetchServerScanStatus(): AppResult<NavidromeServerScanStatus> = withAuth { auth ->
        val result = navidromeApi.getScanStatus(auth)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to check scan status.")
        }
        AppResult.Success(result.getOrThrow().toModel())
    }

    suspend fun triggerServerScan(fullScan: Boolean = false): AppResult<NavidromeServerScanStatus> = withAuth { auth ->
        val result = navidromeApi.startScan(auth, fullScan = fullScan)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to start server scan.")
        }
        AppResult.Success(result.getOrThrow().toModel())
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
                if (cached.discoverAlbums.isEmpty()) return@let null
                val hydrated = hydrateHomePlaylists(auth, cached, forceRefreshArtwork = false)
                if (hydrated != cached) {
                    putCache(homeCache, cacheKey, hydrated)
                    sessionPreferences.setCachedNavidromeHome(
                        sessionKey = sessionKey,
                        payload = serializeHome(hydrated),
                        savedAtMs = System.currentTimeMillis()
                    )
                }
                return@withAuth AppResult.Success(hydrated)
            }
            getFreshPersistedHomeSnapshot(sessionKey)?.let { cached ->
                if (cached.discoverAlbums.isEmpty()) return@let null
                val hydrated = hydrateHomePlaylists(auth, cached, forceRefreshArtwork = false)
                putCache(homeCache, cacheKey, hydrated)
                if (hydrated != cached) {
                    sessionPreferences.setCachedNavidromeHome(
                        sessionKey = sessionKey,
                        payload = serializeHome(hydrated),
                        savedAtMs = System.currentTimeMillis()
                    )
                }
                return@withAuth AppResult.Success(hydrated)
            }
        }
        val staleCache = if (forceRefresh) {
            null
        } else {
            getAnyCache(homeCache, cacheKey)?.let {
                hydrateHomePlaylists(auth, it, forceRefreshArtwork = false)
            } ?: getAnyPersistedHomeSnapshot(sessionKey)?.let {
                hydrateHomePlaylists(auth, it, forceRefreshArtwork = false)
            }
        }
        coroutineScope {
            val recentAlbums = async { navidromeApi.getAlbumList(auth, type = "newest", size = 18) }
            val discoverAlbums = async { navidromeApi.getAlbumList(auth, type = "alphabeticalByName", size = 200) }
            val artists = async { navidromeApi.getArtists(auth) }
            val playlists = async { navidromeApi.getPlaylists(auth) }

            val albumResult = recentAlbums.await()
            val discoverAlbumResult = discoverAlbums.await()
            val artistResult = artists.await()
            val playlistResult = playlists.await()

            if (
                albumResult.isFailure ||
                discoverAlbumResult.isFailure ||
                artistResult.isFailure ||
                playlistResult.isFailure
            ) {
                staleCache?.let {
                    val fallbackDiscoverAlbums = if (it.discoverAlbums.isNotEmpty()) {
                        it.discoverAlbums
                    } else {
                        it.recentAlbums.shuffled().take(12)
                    }
                    return@coroutineScope AppResult.Success(
                        it.copy(discoverAlbums = fallbackDiscoverAlbums)
                    )
                }
                throw (
                    albumResult.exceptionOrNull()
                        ?: discoverAlbumResult.exceptionOrNull()
                        ?: artistResult.exceptionOrNull()
                        ?: playlistResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to load Navidrome home.")
                    )
            }

            val albums = albumResult.getOrThrow()
            val discoverAlbumItems = discoverAlbumResult.getOrThrow()
            val artistItems = artistResult.getOrThrow()
            val playlistItems = playlistResult.getOrThrow()
            val hydratedPlaylists = hydratePlaylistArtwork(
                auth = auth,
                playlists = playlistItems.map { it.toModel() },
                forceRefreshArtwork = true
            )

            val home = NavidromeHome(
                recentAlbums = albums.map { it.toModel(auth) },
                discoverAlbums = discoverAlbumItems.map { it.toModel(auth) }.shuffled().take(12),
                artists = artistItems.map { it.toModel(auth) },
                playlists = hydratedPlaylists,
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
        val pageSize = 100
        val fetchedAlbums = mutableListOf<com.stillshelf.app.data.api.NavidromeAlbumDto>()
        val seenAlbumIds = linkedSetOf<String>()
        var offset = 0
        while (true) {
            val result = navidromeApi.getAlbumList(auth, type = apiType, size = pageSize, offset = offset)
            if (result.isFailure) {
                staleCache?.let { return@withAuth AppResult.Success(it) }
                throw result.exceptionOrNull() ?: IllegalStateException("Unable to load albums.")
            }
            val page = result.getOrThrow()
            page.forEach { album ->
                if (seenAlbumIds.add(album.id)) {
                    fetchedAlbums += album
                }
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        val albums = fetchedAlbums
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
                val hydrated = hydratePlaylistArtwork(auth, cached, forceRefreshArtwork = false)
                if (hydrated != cached) {
                    putCache(playlistsCache, cacheKey, hydrated)
                }
                return@withAuth AppResult.Success(hydrated)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(playlistsCache, cacheKey)
        val result = navidromeApi.getPlaylists(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load playlists.")
        }
        val playlists = hydratePlaylistArtwork(
            auth = auth,
            playlists = result.getOrThrow().map { it.toModel() },
            forceRefreshArtwork = true
        )
        putCache(playlistsCache, cacheKey, playlists)
        AppResult.Success(playlists)
    }

    suspend fun fetchPlaylistDetail(
        playlistId: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromePlaylistDetail> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "playlist:$playlistId")
        if (!forceRefresh) {
            getFreshCache(playlistDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(playlistDetailCache, cacheKey)
        val result = navidromeApi.getPlaylist(auth, playlistId)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load playlist.")
        }
        val detail = result.getOrThrow().toModel(auth)
        putCache(playlistDetailCache, cacheKey, detail)
        updatePlaylistSummaryCaches(auth, detail)
        AppResult.Success(detail)
    }

    suspend fun createPlaylist(name: String): AppResult<NavidromePlaylist> = withAuth { auth ->
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return@withAuth AppResult.Error("Playlist name is required.")
        }
        val previousPlaylistIds = navidromeApi.getPlaylists(auth)
            .getOrNull()
            ?.mapNotNullTo(linkedSetOf()) { playlist ->
                playlist.id.trim().takeIf { it.isNotBlank() }
            }
        val result = navidromeApi.createPlaylist(auth, normalizedName)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to create playlist.")
        }
        val created = result.getOrThrow().toModel()
        val resolved = if (created.id.isNotBlank()) {
            created
        } else {
            resolveCreatedPlaylist(
                auth = auth,
                playlistName = normalizedName,
                previousPlaylistIds = previousPlaylistIds
            ) ?: return@withAuth AppResult.Error(
                "Playlist was created, but the app could not confirm which playlist was new. Please refresh and try again."
            )
        }
        invalidatePlaylistCaches(auth)
        AppResult.Success(resolved)
    }

    suspend fun renamePlaylist(playlistId: String, name: String): AppResult<Unit> = withAuth { auth ->
        val normalizedId = playlistId.trim()
        val normalizedName = name.trim()
        if (normalizedId.isBlank()) return@withAuth AppResult.Error("Invalid playlist id.")
        if (normalizedName.isBlank()) return@withAuth AppResult.Error("Playlist name is required.")
        val result = navidromeApi.updatePlaylist(
            auth = auth,
            playlistId = normalizedId,
            name = normalizedName
        )
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to rename playlist.")
        }
        invalidatePlaylistCaches(auth, normalizedId)
        AppResult.Success(Unit)
    }

    suspend fun addTracksToPlaylist(
        playlistId: String,
        trackIds: List<String>
    ): AppResult<NavidromePlaylistAddOutcome> = withAuth { auth ->
        val normalizedId = playlistId.trim()
        val normalizedTrackIds = trackIds.map(String::trim).filter { it.isNotBlank() }.distinct()
        if (normalizedId.isBlank()) return@withAuth AppResult.Error("Invalid playlist id.")
        if (normalizedTrackIds.isEmpty()) return@withAuth AppResult.Error("No songs selected.")
        val detail = when (val result = fetchPlaylistDetail(normalizedId, forceRefresh = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return@withAuth result
        }
        val existingTrackIds = detail.tracks.map(NavidromeTrack::id).toHashSet()
        val tracksToAdd = normalizedTrackIds.filterNot(existingTrackIds::contains)
        val duplicateCount = normalizedTrackIds.size - tracksToAdd.size
        if (tracksToAdd.isEmpty()) {
            return@withAuth AppResult.Success(
                NavidromePlaylistAddOutcome(
                    addedCount = 0,
                    duplicateCount = duplicateCount
                )
            )
        }
        val result = navidromeApi.updatePlaylist(
            auth = auth,
            playlistId = normalizedId,
            name = detail.playlist.name,
            songIdsToAdd = tracksToAdd
        )
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to add songs to playlist.")
        }
        invalidatePlaylistCaches(auth, normalizedId)
        AppResult.Success(
            NavidromePlaylistAddOutcome(
                addedCount = tracksToAdd.size,
                duplicateCount = duplicateCount
            )
        )
    }

    suspend fun removeTrackFromPlaylist(
        playlistId: String,
        index: Int
    ): AppResult<Unit> = withAuth { auth ->
        val normalizedId = playlistId.trim()
        if (normalizedId.isBlank()) return@withAuth AppResult.Error("Invalid playlist id.")
        if (index < 0) return@withAuth AppResult.Error("Invalid playlist item.")
        val detail = when (val result = fetchPlaylistDetail(normalizedId, forceRefresh = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return@withAuth result
        }
        if (index !in detail.tracks.indices) {
            return@withAuth AppResult.Error("Invalid playlist item.")
        }
        val result = navidromeApi.updatePlaylist(
            auth = auth,
            playlistId = normalizedId,
            name = detail.playlist.name,
            songIndicesToRemove = listOf(index)
        )
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to remove song from playlist.")
        }
        invalidatePlaylistCaches(auth, normalizedId)
        AppResult.Success(Unit)
    }

    suspend fun deletePlaylist(playlistId: String): AppResult<Unit> = withAuth { auth ->
        val normalizedId = playlistId.trim()
        if (normalizedId.isBlank()) return@withAuth AppResult.Error("Invalid playlist id.")
        val result = navidromeApi.deletePlaylist(auth, normalizedId)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to delete playlist.")
        }
        invalidatePlaylistCaches(auth, normalizedId)
        AppResult.Success(Unit)
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
        val pageSize = 500
        val fetchedTracks = mutableListOf<com.stillshelf.app.data.api.NavidromeTrackDto>()
        val seenTrackIds = linkedSetOf<String>()
        var offset = 0
        while (true) {
            val result = navidromeApi.getSongs(
                auth = auth,
                songCount = pageSize,
                songOffset = offset
            )
            if (result.isFailure) {
                staleCache?.let { return@withAuth AppResult.Success(it) }
                throw result.exceptionOrNull() ?: IllegalStateException("Unable to load songs.")
            }
            val page = result.getOrThrow()
            page.forEach { track ->
                if (seenTrackIds.add(track.id)) {
                    fetchedTracks += track
                }
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        val songs = fetchedTracks
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

    suspend fun fetchRandomSongs(
        count: Int = 100,
        genre: String? = null
    ): AppResult<List<NavidromeTrack>> = withAuth { auth ->
        val result = navidromeApi.getRandomSongs(auth, size = count.coerceIn(1, 500), genre = genre)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load songs.")
        }
        AppResult.Success(result.getOrThrow().map { it.toModel(auth) })
    }

    suspend fun fetchGenres(): AppResult<List<String>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "genres")
        getFreshCache(genresCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
            return@withAuth AppResult.Success(cached)
        }
        val staleCache = getAnyCache(genresCache, cacheKey)
        val result = navidromeApi.getGenres(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load genres.")
        }
        val genres = result.getOrThrow().map { it.name }
        putCache(genresCache, cacheKey, genres)
        AppResult.Success(genres)
    }

    suspend fun fetchLyrics(track: NavidromeTrack): AppResult<NavidromeLyrics> = withAuth { auth ->
        if (track.id.startsWith("radio:")) {
            return@withAuth AppResult.Error("Lyrics are not available for radio stations.")
        }

        val preferencesState = sessionPreferences.state.first()
        val activeLyricsSourceId = preferencesState.activeNavidromeLyricsSourceId
        val cacheKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = cachePrefix(auth),
            trackId = track.id,
            activeLyricsSourceId = activeLyricsSourceId
        )
        val sourceScopedCacheKey = cacheKeys.sourceScopedKey
        val fallbackCacheKey = cacheKeys.fallbackKey
        getAnyCache(lyricsCache, sourceScopedCacheKey)?.let { cached ->
            if (sourceScopedCacheKey != fallbackCacheKey) {
                cacheResolvedLyrics(fallbackCacheKey, cached)
            }
            return@withAuth AppResult.Success(cached)
        }
        sessionPreferences.getCachedNavidromeLyrics(sourceScopedCacheKey)
            ?.let(::parseLyricsCachePayload)
            ?.also { cached ->
                putCache(lyricsCache, sourceScopedCacheKey, cached)
                if (sourceScopedCacheKey != fallbackCacheKey) {
                    cacheResolvedLyrics(fallbackCacheKey, cached)
                }
                return@withAuth AppResult.Success(cached)
            }
        getAnyCache(lyricsCache, fallbackCacheKey)?.let { cached ->
            return@withAuth AppResult.Success(cached)
        }
        sessionPreferences.getCachedNavidromeLyrics(fallbackCacheKey)
            ?.let(::parseLyricsCachePayload)
            ?.also { cached ->
                putCache(lyricsCache, fallbackCacheKey, cached)
                return@withAuth AppResult.Success(cached)
            }

        navidromeApi.getLyricsBySongId(auth, track.id)
            .getOrNull()
            .orEmpty()
            .toModel()
            ?.copy(sourceLabel = "Embedded lyrics")
            ?.let {
                cacheResolvedLyrics(sourceScopedCacheKey, it)
                cacheResolvedLyrics(fallbackCacheKey, it)
                return@withAuth AppResult.Success(it)
            }

        navidromeApi.getLyrics(auth, track.artistName, track.title)
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::plainLyricsToModel)
            ?.copy(sourceLabel = "Embedded lyrics")
            ?.let {
                cacheResolvedLyrics(sourceScopedCacheKey, it)
                cacheResolvedLyrics(fallbackCacheKey, it)
                return@withAuth AppResult.Success(it)
            }

        val externalSource = resolveActiveLyricsSource(preferencesState)
        val externalLyrics = externalSource?.let { source ->
            fetchExternalLyrics(
                source = source,
                track = track
            )
        }
        if (externalLyrics != null) {
            cacheResolvedLyrics(sourceScopedCacheKey, externalLyrics)
            cacheResolvedLyrics(fallbackCacheKey, externalLyrics)
            return@withAuth AppResult.Success(externalLyrics)
        }
        if (externalSource == null) {
            return@withAuth AppResult.Error("No lyrics found.")
        }
        val netEaseLyrics = fetchNetEaseLyrics(track)
            ?: return@withAuth AppResult.Error("No lyrics found.")
        cacheResolvedLyrics(sourceScopedCacheKey, netEaseLyrics)
        cacheResolvedLyrics(fallbackCacheKey, netEaseLyrics)
        AppResult.Success(netEaseLyrics)
    }

    suspend fun clearLyricsCache(track: NavidromeTrack? = null) {
        if (track == null) {
            clearAllLyricsCache()
            return
        }
        val auth = currentAuth() ?: run {
            clearAllLyricsCache()
            return
        }
        val trackCachePrefix = buildNavidromeLyricsCachePrefixForTrack(
            cachePrefix = cachePrefix(auth),
            trackId = track.id
        )
        cacheMutex.withLock {
            lyricsCache.keys.removeAll { key -> key.startsWith(trackCachePrefix) }
        }
        sessionPreferences.clearCachedNavidromeLyricsByPrefix(trackCachePrefix)
    }

    private suspend fun clearAllLyricsCache() {
        cacheMutex.withLock {
            lyricsCache.clear()
        }
        sessionPreferences.clearCachedNavidromeLyrics()
    }

    suspend fun refreshPlayableTracks(tracks: List<NavidromeTrack>): AppResult<List<NavidromeTrack>> {
        val auth = currentAuth(requireFreshConnection = false)
            ?: return AppResult.Error("Navidrome session expired. Please sign in again.")
        return try {
            AppResult.Success(
                tracks.map { track ->
                    if (track.id.startsWith("radio:")) {
                        track
                    } else {
                        track.copy(streamUrl = navidromeApi.streamUrl(auth, track.id))
                    }
                }
            )
        } catch (t: Throwable) {
            AppResult.Error(t.message ?: "Navidrome request failed.", t)
        }
    }

    suspend fun currentPlaybackSessionKey(): String? {
        ensureMigratedLegacySession()
        val state = sessionPreferences.state.first()
        val activeServer = resolveActiveServer(state) ?: return null
        val libraryId = state.navidromeActiveLibraryIds[activeServer.id]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "_all"
        return "${activeServer.id.lowercase()}|${libraryId.lowercase()}"
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

    private suspend fun currentAuth(requireFreshConnection: Boolean = false): NavidromeAuth? {
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
        if (!requireFreshConnection) {
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
        }
        return when (
            val result = resolveConnection(
                server = activeServer,
                password = password,
                config = serverSwitchingConfig(state, activeServer.id),
                ignoreResolvedCache = requireFreshConnection
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
        return "${cachePrefix(auth)}$suffix"
    }

    private fun cachePrefix(auth: NavidromeAuth): String {
        val libraryId = auth.musicFolderId?.trim()?.takeIf { it.isNotBlank() } ?: "_all"
        return "${auth.serverId.lowercase()}|${libraryId.lowercase()}|"
    }

    private suspend fun ensureMigratedLegacySession() {
        migrationMutex.withLock {
            val state = sessionPreferences.state.first()
            if (state.navidromeServers.isNotEmpty()) {
                sessionPreferences.setNavidromeLegacySessionMigrated(true)
                return
            }
            if (sessionPreferences.isNavidromeLegacySessionMigrated()) return
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
            sessionPreferences.setNavidromeLegacySessionMigrated(true)
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
        config: ServerEndpointSwitchingConfig,
        ignoreResolvedCache: Boolean = false
    ): AppResult<ResolvedNavidromeConnection> {
        val now = System.currentTimeMillis()
        val cached = resolvedConnectionsByServerId[server.id]
        if (
            !ignoreResolvedCache &&
            cached != null &&
            (now - cached.checkedAtMs) <= ACTIVE_CONNECTION_CACHE_MAX_AGE_MS
        ) {
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
                } else if (
                    networkMonitor.currentConnectionType() == NetworkConnectionType.Wifi &&
                    isEndpointReachable(normalizedLocalBaseUrl!!)
                ) {
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
                        lanFallbackToRemote = networkMonitor.currentConnectionType() == NetworkConnectionType.Wifi,
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

    private fun resolveActiveLyricsSource(
        state: com.stillshelf.app.core.datastore.SessionPreferenceState
    ): NavidromeLyricsSource? {
        val requestedId = state.activeNavidromeLyricsSourceId
        return state.navidromeLyricsSources.firstOrNull { it.id == requestedId }
            ?: state.navidromeLyricsSources.firstOrNull()
    }

    private fun List<NavidromeStructuredLyricsDto>.toModel(): NavidromeLyrics? {
        val primary = firstOrNull { it.lines.isNotEmpty() && it.synced }
            ?: firstOrNull { it.lines.isNotEmpty() }
            ?: return null
        val lines = primary.lines.map { line ->
            val baseTimestamp = line.startMs?.coerceAtLeast(0)
            val offset = primary.offsetMs?.takeIf { it >= 0 }
            NavidromeLyricsLine(
                timestampMs = if (baseTimestamp != null) {
                    (baseTimestamp + (offset ?: 0)).coerceAtLeast(0)
                } else {
                    null
                },
                text = line.value.trim()
            )
        }.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return null
        return NavidromeLyrics(
            lines = lines,
            sourceLabel = "Lyrics",
            isSynced = primary.synced && lines.any { it.timestampMs != null }
        )
    }

    private fun plainLyricsToModel(text: String): NavidromeLyrics? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { NavidromeLyricsLine(timestampMs = null, text = it) }
        if (lines.isEmpty()) return null
        return NavidromeLyrics(
            lines = lines,
            sourceLabel = "Lyrics",
            isSynced = false
        )
    }

    private suspend fun fetchExternalLyrics(
        source: NavidromeLyricsSource,
        track: NavidromeTrack
    ): NavidromeLyrics? {
        val normalizedArtist = normalizeLyricsArtist(track.artistName)
        val normalizedTrack = normalizeLyricsTrack(track.title)
        val normalizedAlbum = normalizeLyricsAlbum(track.albumName)
        val compactArtist = compactLyricsField(normalizedArtist)
        val compactTrack = compactLyricsField(normalizedTrack)
        val compactAlbum = compactLyricsField(normalizedAlbum)
        val lookups = buildList {
            add(
                NavidromeLyricsLookup(
                    artistName = track.artistName,
                    trackName = track.title,
                    albumName = track.albumName,
                    durationSeconds = track.durationSeconds
                )
            )
            add(
                NavidromeLyricsLookup(
                    artistName = track.artistName,
                    trackName = track.title,
                    albumName = track.albumName,
                    durationSeconds = null
                )
            )
            add(
                NavidromeLyricsLookup(
                    artistName = track.artistName,
                    trackName = track.title,
                    albumName = null,
                    durationSeconds = null
                )
            )
            if (normalizedArtist != track.artistName || normalizedTrack != track.title || normalizedAlbum != track.albumName) {
                add(
                    NavidromeLyricsLookup(
                        artistName = normalizedArtist,
                        trackName = normalizedTrack,
                        albumName = normalizedAlbum,
                        durationSeconds = track.durationSeconds
                    )
                )
                add(
                    NavidromeLyricsLookup(
                        artistName = normalizedArtist,
                        trackName = normalizedTrack,
                        albumName = normalizedAlbum,
                        durationSeconds = null
                    )
                )
                add(
                    NavidromeLyricsLookup(
                        artistName = normalizedArtist,
                        trackName = normalizedTrack,
                        albumName = null,
                        durationSeconds = null
                    )
                )
            }
            if (compactArtist.isNotBlank() && compactTrack.isNotBlank()) {
                add(
                    NavidromeLyricsLookup(
                        artistName = compactArtist,
                        trackName = compactTrack,
                        albumName = compactAlbum.ifBlank { null },
                        durationSeconds = track.durationSeconds
                    )
                )
                add(
                    NavidromeLyricsLookup(
                        artistName = compactArtist,
                        trackName = compactTrack,
                        albumName = compactAlbum.ifBlank { null },
                        durationSeconds = null
                    )
                )
                add(
                    NavidromeLyricsLookup(
                        artistName = compactArtist,
                        trackName = compactTrack,
                        albumName = null,
                        durationSeconds = null
                    )
                )
            }
        }.distinct()

        lookups.forEach { lookup ->
            fetchExactExternalLyrics(source, lookup)?.let { return it }
        }
        lookups.forEach { lookup ->
            fetchSearchedExternalLyrics(source, lookup)?.let { return it }
        }
        return null
    }

    private suspend fun fetchExactExternalLyrics(
        source: NavidromeLyricsSource,
        lookup: NavidromeLyricsLookup
    ): NavidromeLyrics? {
        val requestUrl = source.baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/get")
            ?.addQueryParameter("artist_name", lookup.artistName)
            ?.addQueryParameter("track_name", lookup.trackName)
            ?.apply {
                lookup.albumName?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
            }
            ?.apply {
                lookup.durationSeconds?.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) }
            }
            ?.build()
            ?: return null
        return executeExternalLyricsRequest(source, requestUrl.toString(), expectArray = false)
    }

    private suspend fun fetchSearchedExternalLyrics(
        source: NavidromeLyricsSource,
        lookup: NavidromeLyricsLookup
    ): NavidromeLyrics? {
        val requestUrl = source.baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/search")
            ?.addQueryParameter("track_name", lookup.trackName)
            ?.addQueryParameter("artist_name", lookup.artistName)
            ?.apply {
                lookup.albumName?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
            }
            ?.build()
            ?: return null
        return executeExternalLyricsRequest(source, requestUrl.toString(), expectArray = true)
    }

    private suspend fun executeExternalLyricsRequest(
        source: NavidromeLyricsSource,
        requestUrl: String,
        expectArray: Boolean
    ): NavidromeLyrics? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("User-Agent", "StillShelf Navidrome Client")
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 404) return@use null
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                if (expectArray) {
                    parseExternalLyricsSearchResponse(source, body)
                } else {
                    parseExternalLyricsResponse(source, body)
                }
            }
        }.getOrNull()
    }

    private fun parseExternalLyricsResponse(
        source: NavidromeLyricsSource,
        body: String
    ): NavidromeLyrics? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.isInstrumentalEntry()) return null
        val syncedLyrics = root.optString("syncedLyrics").trim().takeIf { it.isNotBlank() }
        if (syncedLyrics != null) {
            val syncedLines = syncedLyrics.lines()
                .mapNotNull(::parseSyncedLyricsLine)
            if (syncedLines.isNotEmpty()) {
                return NavidromeLyrics(
                    lines = syncedLines,
                    sourceLabel = source.name,
                    isSynced = true
                )
            }
        }
        val plainLyrics = root.optString("plainLyrics").trim().takeIf { it.isNotBlank() } ?: return null
        return plainLyricsToModel(plainLyrics)?.copy(sourceLabel = source.name)
    }

    private fun parseExternalLyricsSearchResponse(
        source: NavidromeLyricsSource,
        body: String
    ): NavidromeLyrics? {
        val results = runCatching { JSONArray(body) }.getOrNull() ?: return null
        for (index in 0 until results.length()) {
            val entry = results.optJSONObject(index) ?: continue
            if (entry.isInstrumentalEntry()) continue
            parseExternalLyricsResponse(source, entry.toString())?.let { return it }
        }
        return null
    }

    private fun JSONObject.isInstrumentalEntry(): Boolean {
        return when (val instrumental = opt("instrumental")) {
            is Boolean -> instrumental
            is Number -> instrumental.toInt() != 0
            is String -> instrumental.equals("true", ignoreCase = true) || instrumental == "1"
            else -> false
        }
    }

    private fun normalizeLyricsArtist(raw: String): String {
        return raw
            .substringBefore(" feat.", missingDelimiterValue = raw)
            .substringBefore(" ft.", missingDelimiterValue = raw)
            .substringBefore(" featuring ", missingDelimiterValue = raw)
            .substringBefore(" with ", missingDelimiterValue = raw)
            .substringBefore(" x ", missingDelimiterValue = raw)
            .substringBefore(";")
            .substringBefore(",")
            .replace("&", "and")
            .normalizeLyricsField()
    }

    private fun normalizeLyricsTrack(raw: String): String {
        return raw
            .replace(Regex("""^\d+\s*[-.:]\s*"""), "")
            .replace(Regex("""^\d+\s+"""), "")
            .substringBefore(" - ")
            .substringBefore(" — ")
            .substringBefore(" – ")
            .replace(Regex("""\s*\(([^)]*(live|remaster|remastered|version|edit|mono|stereo|deluxe|explicit)[^)]*)\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\[([^\]]*(live|remaster|remastered|version|edit|mono|stereo|deluxe|explicit)[^\]]*)]""", RegexOption.IGNORE_CASE), "")
            .normalizeLyricsField()
    }

    private fun normalizeLyricsAlbum(raw: String): String {
        return raw.normalizeLyricsField()
    }

    private fun String.normalizeLyricsField(): String {
        return trim()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace(Regex("""\s+"""), " ")
    }

    private fun compactLyricsField(raw: String): String {
        return raw
            .lowercase()
            .replace("&", "and")
            .replace(Regex("""[^a-z0-9]+"""), "")
    }

    private suspend fun fetchNetEaseLyrics(track: NavidromeTrack): NavidromeLyrics? = withContext(Dispatchers.IO) {
        val normalizedArtist = normalizeLyricsArtist(track.artistName)
        val normalizedTrack = normalizeLyricsTrack(track.title)
        val searchQueries = buildList {
            add("${track.title} ${track.artistName}".trim())
            if (normalizedTrack != track.title || normalizedArtist != track.artistName) {
                add("$normalizedTrack $normalizedArtist".trim())
            }
            val compactTrack = compactLyricsField(normalizedTrack)
            val compactArtist = compactLyricsField(normalizedArtist)
            if (compactTrack.isNotBlank() && compactArtist.isNotBlank()) {
                add("$compactTrack $compactArtist")
            }
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        searchQueries.forEach { query ->
            val searchUrl = "https://music.163.com/api/search/get?s=${java.net.URLEncoder.encode(query, "UTF-8")}&type=1&limit=1"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
            val songId = runCatching {
                okHttpClient.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val root = JSONObject(body)
                    root.optJSONObject("result")
                        ?.optJSONArray("songs")
                        ?.optJSONObject(0)
                        ?.optLong("id")
                        ?.takeIf { it > 0L }
                }
            }.getOrNull() ?: return@forEach

            val lyricsRequest = Request.Builder()
                .url("https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1")
                .get()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
            runCatching {
                okHttpClient.newCall(lyricsRequest).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    parseNetEaseLyricsResponse(body)
                }
            }.getOrNull()?.let { return@withContext it }
        }

        null
    }

    private fun parseNetEaseLyricsResponse(body: String): NavidromeLyrics? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val rawLyrics = root.optJSONObject("lrc")
            ?.optString("lyric")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val lines = rawLyrics.lines()
            .mapNotNull(::parseNetEaseLyricsLine)
        if (lines.isEmpty()) return null
        return NavidromeLyrics(
            lines = lines,
            sourceLabel = "NetEase",
            isSynced = true
        )
    }

    private fun parseNetEaseLyricsLine(rawLine: String): NavidromeLyricsLine? {
        val line = rawLine.trim()
        if (line.isBlank() || !line.startsWith("[")) return null
        val closingBracket = line.indexOf(']')
        if (closingBracket <= 1) return null
        val timestampLabel = line.substring(1, closingBracket)
        val text = line.substring(closingBracket + 1).trim()
        if (text.isBlank()) return null
        val timestampMs = parseLyricsTimestamp(timestampLabel) ?: return null
        return NavidromeLyricsLine(
            timestampMs = timestampMs,
            text = text
        )
    }

    private fun parseSyncedLyricsLine(rawLine: String): NavidromeLyricsLine? {
        val line = rawLine.trim()
        if (line.isBlank() || !line.startsWith("[")) return null
        val closingBracket = line.indexOf(']')
        if (closingBracket <= 1) return null
        val timestampLabel = line.substring(1, closingBracket)
        val text = line.substring(closingBracket + 1).trim()
        if (text.isBlank()) return null
        val timestampMs = parseLyricsTimestamp(timestampLabel) ?: return null
        return NavidromeLyricsLine(
            timestampMs = timestampMs,
            text = text
        )
    }

    private fun parseLyricsTimestamp(raw: String): Int? {
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return null
        val minutes = parts[0].toIntOrNull() ?: return null
        val secondParts = parts[1].split(".", limit = 2)
        val seconds = secondParts[0].toIntOrNull() ?: return null
        val hundredths = secondParts.getOrNull(1)
            ?.take(2)
            ?.padEnd(2, '0')
            ?.toIntOrNull()
            ?: 0
        return ((minutes * 60) + seconds) * 1000 + (hundredths * 10)
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
            genresCache.clear()
            artistDetailCache.clear()
            albumDetailCache.clear()
            playlistDetailCache.clear()
            searchCache.clear()
            lyricsCache.clear()
        }
        sessionPreferences.clearCachedNavidromeLyrics()
    }

    internal suspend fun invalidateArtistAndAlbumDetailCaches() {
        cacheMutex.withLock {
            artistDetailCache.clear()
            albumDetailCache.clear()
        }
    }

    private suspend fun cacheResolvedLyrics(cacheKey: String, lyrics: NavidromeLyrics) {
        putCache(lyricsCache, cacheKey, lyrics)
        sessionPreferences.setCachedNavidromeLyrics(
            cacheKey = cacheKey,
            payload = encodeLyricsCachePayload(lyrics),
            savedAtMs = System.currentTimeMillis()
        )
    }

    private fun encodeLyricsCachePayload(lyrics: NavidromeLyrics): String {
        return JSONObject()
            .put("sourceLabel", lyrics.sourceLabel)
            .put("isSynced", lyrics.isSynced)
            .put(
                "lines",
                JSONArray().apply {
                    lyrics.lines.forEach { line ->
                        put(
                            JSONObject()
                                .put("text", line.text)
                                .apply {
                                    line.timestampMs?.let { put("timestampMs", it) }
                                }
                        )
                    }
                }
            )
            .toString()
    }

    private fun parseLyricsCachePayload(
        cached: CachedNavidromeLyricsPayload
    ): NavidromeLyrics? {
        val root = runCatching { JSONObject(cached.payload) }.getOrNull() ?: return null
        val linesArray = root.optJSONArray("lines") ?: return null
        val lines = buildList {
            for (index in 0 until linesArray.length()) {
                val node = linesArray.optJSONObject(index) ?: continue
                val text = node.optString("text").trim()
                if (text.isBlank()) continue
                add(
                    NavidromeLyricsLine(
                        timestampMs = node.takeIf { it.has("timestampMs") }
                            ?.optLong("timestampMs")
                            ?.takeIf { it >= 0L }
                            ?.toInt(),
                        text = text
                    )
                )
            }
        }
        if (lines.isEmpty()) return null
        return NavidromeLyrics(
            lines = lines,
            sourceLabel = root.optString("sourceLabel").trim().ifBlank { "Lyrics" },
            isSynced = root.optBoolean("isSynced")
        )
    }

    private suspend fun invalidatePlaylistCaches(
        auth: NavidromeAuth,
        playlistId: String? = null
    ) {
        val prefix = cachePrefix(auth)
        cacheMutex.withLock {
            homeCache.remove(cacheKey(auth, "home"))
            playlistsCache.remove(cacheKey(auth, "playlists"))
            if (playlistId != null) {
                playlistDetailCache.remove(cacheKey(auth, "playlist:$playlistId"))
            }
            playlistDetailCache.keys.removeAll { key ->
                key.startsWith(prefix) && key.substringAfterLast('|').startsWith("playlist:")
            }
        }
        sessionPreferences.clearCachedNavidromeHome()
    }

    private suspend fun resolvePlaylistByName(
        auth: NavidromeAuth,
        playlistName: String
    ): NavidromePlaylist? {
        val result = navidromeApi.getPlaylists(auth)
        if (result.isFailure) return null
        return result.getOrThrow()
            .map { it.toModel() }
            .lastOrNull { it.name.equals(playlistName, ignoreCase = true) }
    }

    private suspend fun resolveCreatedPlaylist(
        auth: NavidromeAuth,
        playlistName: String,
        previousPlaylistIds: Set<String>?
    ): NavidromePlaylist? {
        val result = navidromeApi.getPlaylists(auth)
        if (result.isFailure) return null
        return resolveCreatedPlaylistCandidate(
            playlists = result.getOrThrow().map { it.toModel() },
            playlistName = playlistName,
            previousPlaylistIds = previousPlaylistIds
        )
    }

    private suspend fun fetchPlaylistName(
        auth: NavidromeAuth,
        playlistId: String
    ): String? {
        getAnyCache(playlistDetailCache, cacheKey(auth, "playlist:$playlistId"))
            ?.playlist
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        getAnyCache(playlistsCache, cacheKey(auth, "playlists"))
            ?.firstOrNull { it.id == playlistId }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val result = navidromeApi.getPlaylist(auth, playlistId)
        if (result.isFailure) return null
        return result.getOrThrow().playlist.name
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
            .put("discoverAlbums", JSONArray().apply {
                home.discoverAlbums.forEach { album ->
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
                            .put("artworkUrls", JSONArray().apply {
                                playlist.artworkUrls.forEach { url -> put(url) }
                            })
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
        val recentAlbums = buildList {
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
        }
        val discoverAlbums = buildList {
            val source = root.optJSONArray("discoverAlbums") ?: JSONArray()
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
        }
        return NavidromeHome(
            recentAlbums = recentAlbums,
            discoverAlbums = discoverAlbums,
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
                            },
                            artworkUrls = buildList {
                                val artworkArray = item.optJSONArray("artworkUrls") ?: JSONArray()
                                for (artworkIndex in 0 until artworkArray.length()) {
                                    artworkArray.optString(artworkIndex)
                                        .trim()
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                }
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
            bitRateKbps = bitRateKbps,
            sizeBytes = sizeBytes
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

    private fun com.stillshelf.app.data.api.NavidromeScanStatusDto.toModel(): NavidromeServerScanStatus {
        return NavidromeServerScanStatus(
            scanning = scanning,
            scannedCount = scannedCount,
            folderCount = folderCount,
            lastScanLabel = lastScanLabel
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

    private fun NavidromePlaylistDetailDto.toModel(auth: NavidromeAuth): NavidromePlaylistDetail {
        val tracksModel = tracks.map { it.toModel(auth) }
        return NavidromePlaylistDetail(
            playlist = playlist.toModel().copy(
                artworkUrls = playlistArtworkUrls(tracksModel)
            ),
            tracks = tracksModel
        )
    }

    private suspend fun hydratePlaylistArtwork(
        auth: NavidromeAuth,
        playlists: List<NavidromePlaylist>,
        forceRefreshArtwork: Boolean
    ): List<NavidromePlaylist> = coroutineScope {
        playlists.map { playlist ->
            async {
                if (!playlist.needsArtworkHydration()) {
                    playlist
                } else {
                    playlist.copy(
                        artworkUrls = resolvePlaylistArtworkUrls(
                            auth = auth,
                            playlistId = playlist.id,
                            forceRefresh = forceRefreshArtwork
                        )
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun resolvePlaylistArtworkUrls(
        auth: NavidromeAuth,
        playlistId: String,
        forceRefresh: Boolean
    ): List<String> {
        val cacheKey = cacheKey(auth, "playlist:$playlistId")
        val cachedDetail = when {
            forceRefresh -> null
            else -> getFreshCache(playlistDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)
                ?: getAnyCache(playlistDetailCache, cacheKey)
        }
        if (cachedDetail != null) {
            return playlistArtworkUrls(cachedDetail.tracks)
        }
        val detailResult = navidromeApi.getPlaylist(auth, playlistId)
        if (detailResult.isFailure) return emptyList()
        val detail = detailResult.getOrThrow().toModel(auth)
        putCache(playlistDetailCache, cacheKey, detail)
        return playlistArtworkUrls(detail.tracks)
    }

    private fun updatePlaylistSummaryCaches(
        auth: NavidromeAuth,
        detail: NavidromePlaylistDetail
    ) {
        val cacheKey = cacheKey(auth, "playlists")
        val artworkUrls = playlistArtworkUrls(detail.tracks)
        val current = playlistsCache[cacheKey] ?: return
        val updatedPlaylists = current.value.map { playlist ->
            if (playlist.id == detail.playlist.id) {
                playlist.copy(
                    songCount = detail.playlist.songCount ?: detail.tracks.size,
                    durationSeconds = detail.playlist.durationSeconds,
                    artworkUrls = artworkUrls
                )
            } else {
                playlist
            }
        }
        playlistsCache[cacheKey] = current.copy(value = updatedPlaylists)
    }

    private fun playlistArtworkUrls(tracks: List<NavidromeTrack>): List<String> {
        return tracks.take(4).mapNotNull { track ->
            track.coverUrl?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun NavidromePlaylist.needsArtworkHydration(): Boolean {
        return (songCount ?: 0) > 0 && artworkUrls.isEmpty()
    }

    private suspend fun hydrateHomePlaylists(
        auth: NavidromeAuth,
        home: NavidromeHome,
        forceRefreshArtwork: Boolean
    ): NavidromeHome {
        val hydratedPlaylists = hydratePlaylistArtwork(
            auth = auth,
            playlists = home.playlists,
            forceRefreshArtwork = forceRefreshArtwork
        )
        return if (hydratedPlaylists == home.playlists) {
            home
        } else {
            home.copy(playlists = hydratedPlaylists)
        }
    }
}

internal fun resolveCreatedPlaylistCandidate(
    playlists: List<NavidromePlaylist>,
    playlistName: String,
    previousPlaylistIds: Set<String>?
): NavidromePlaylist? {
    if (!previousPlaylistIds.isNullOrEmpty()) {
        val newPlaylists = playlists.filter { playlist ->
            playlist.id.isNotBlank() && playlist.id !in previousPlaylistIds
        }
        newPlaylists.lastOrNull { it.name.equals(playlistName, ignoreCase = true) }?.let { return it }
        if (newPlaylists.size == 1) return newPlaylists.single()
    }
    return playlists.lastOrNull { it.name.equals(playlistName, ignoreCase = true) }
}
