package com.stillshelf.app.core.network

import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.database.ServerEntity
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal suspend fun resolveActiveServerConnectionStatus(
    serverId: String,
    serverBaseUrl: String,
    config: ServerEndpointSwitchingConfig,
    networkType: NetworkConnectionType,
    previousStatus: ActiveServerConnectionStatus? = null,
    confirmLocalFailureDelayMs: Long = 0L,
    isLanReachable: suspend (String) -> Boolean
): ActiveServerConnectionStatus {
    val normalizedServerBaseUrl = serverBaseUrl.trim().removeSuffix("/")
    val normalizedLanBaseUrl = config.lanBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null }
    val normalizedWanBaseUrl = config.wanBaseUrl?.trim()?.removeSuffix("/")?.ifBlank { null }

    if (!config.enabled) {
        return ActiveServerConnectionStatus(
            serverId = serverId,
            effectiveBaseUrl = normalizedServerBaseUrl,
            route = ServerConnectionRoute.Default,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = false,
            lanFallbackToRemote = false,
            lanBaseUrl = normalizedLanBaseUrl,
            wanBaseUrl = normalizedWanBaseUrl
        )
    }

    var lanFallbackToRemote = false
    if (normalizedLanBaseUrl.isNullOrBlank() || normalizedWanBaseUrl.isNullOrBlank()) {
        return ActiveServerConnectionStatus(
            serverId = serverId,
            effectiveBaseUrl = normalizedServerBaseUrl,
            route = ServerConnectionRoute.Default,
            connectionMode = config.connectionMode,
            switchingEnabled = false,
            lanFallbackToRemote = false,
            lanBaseUrl = normalizedLanBaseUrl,
            wanBaseUrl = normalizedWanBaseUrl
        )
    }

    val route = when (config.connectionMode) {
        ServerConnectionMode.Local -> ServerConnectionRoute.Local
        ServerConnectionMode.Remote -> ServerConnectionRoute.Remote
        ServerConnectionMode.Auto -> {
            when (networkType) {
                NetworkConnectionType.Cellular -> ServerConnectionRoute.Remote
                NetworkConnectionType.Wifi -> {
                    val localInitiallyReachable = isLanReachable(normalizedLanBaseUrl)
                    val keepLocalOnTransientFailure = previousStatus?.route == ServerConnectionRoute.Local
                    val confirmedLocalReachable = if (
                        !localInitiallyReachable &&
                        keepLocalOnTransientFailure
                    ) {
                        if (confirmLocalFailureDelayMs > 0L) {
                            delay(confirmLocalFailureDelayMs)
                        }
                        isLanReachable(normalizedLanBaseUrl)
                    } else {
                        localInitiallyReachable
                    }
                    if (confirmedLocalReachable) {
                        ServerConnectionRoute.Local
                    } else {
                        lanFallbackToRemote = true
                        ServerConnectionRoute.Remote
                    }
                }
                NetworkConnectionType.Other,
                NetworkConnectionType.Offline -> ServerConnectionRoute.Remote
            }
        }
    }

    val effectiveBaseUrl = when (route) {
        ServerConnectionRoute.Local -> normalizedLanBaseUrl
        ServerConnectionRoute.Remote -> normalizedWanBaseUrl
        ServerConnectionRoute.Default -> normalizedServerBaseUrl
    }

    return ActiveServerConnectionStatus(
        serverId = serverId,
        effectiveBaseUrl = effectiveBaseUrl,
        route = route,
        connectionMode = config.connectionMode,
        switchingEnabled = true,
        lanFallbackToRemote = lanFallbackToRemote,
        lanBaseUrl = normalizedLanBaseUrl,
        wanBaseUrl = normalizedWanBaseUrl
    )
}

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActiveServerEndpointResolver @Inject constructor(
    serverDao: ServerDao,
    private val sessionPreferences: SessionPreferences,
    private val networkMonitor: NetworkMonitor,
    okHttpClient: OkHttpClient
) {
    companion object {
        private const val LAN_PROBE_TIMEOUT_MS = 1_200L
        private const val LAN_RETRY_INTERVAL_MS = 3_000L
        private const val LAN_FALLBACK_CONFIRMATION_DELAY_MS = 750L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(LAN_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(LAN_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(LAN_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(LAN_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    @Volatile
    private var lastObservedResolvedStatus: ActiveServerConnectionStatus? = null

    val activeConnectionStatus: StateFlow<ActiveServerConnectionStatus?> = combine(
        serverDao.observeServers(),
        sessionPreferences.state,
        networkMonitor.observeConnectionState()
    ) { servers, prefState, networkState ->
        Triple(servers, prefState, networkState)
    }
        .transformLatest { (servers, prefState, networkState) ->
            val activeServerId = prefState.activeServerId ?: return@transformLatest
            val activeServer = servers.firstOrNull { it.id == activeServerId } ?: return@transformLatest
            val previousStatus = lastObservedResolvedStatus?.takeIf { it.serverId == activeServerId }
            var resolvedStatus = resolveForServer(
                server = activeServer,
                prefState = prefState,
                networkType = networkState.type,
                previousStatus = previousStatus
            )
            emit(resolvedStatus)
            lastObservedResolvedStatus = resolvedStatus
            while (
                currentCoroutineContext().isActive &&
                shouldRetryLanOnWifi(
                    status = resolvedStatus,
                    networkType = networkState.type
                )
            ) {
                delay(LAN_RETRY_INTERVAL_MS)
                resolvedStatus = resolveForServer(
                    server = activeServer,
                    prefState = prefState,
                    networkType = networkState.type,
                    previousStatus = resolvedStatus
                )
                emit(resolvedStatus)
                lastObservedResolvedStatus = resolvedStatus
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun observeActiveConnectionStatus(): Flow<ActiveServerConnectionStatus?> = activeConnectionStatus

    suspend fun resolveForServer(
        server: ServerEntity
    ): ActiveServerConnectionStatus {
        activeConnectionStatus.value
            ?.takeIf { it.serverId == server.id }
            ?.let { return it }
        return resolveForServer(
            server = server,
            prefState = sessionPreferences.state.first(),
            networkType = networkMonitor.currentConnectionType(),
            previousStatus = lastObservedResolvedStatus?.takeIf { it.serverId == server.id }
        )
    }

    suspend fun resolveForServer(
        server: ServerEntity,
        prefState: SessionPreferenceState,
        networkType: NetworkConnectionType,
        previousStatus: ActiveServerConnectionStatus? = null
    ): ActiveServerConnectionStatus {
        val config = prefState.serverEndpointSwitchingConfigs[server.id]
            ?: ServerEndpointSwitchingConfig()
        return resolveActiveServerConnectionStatus(
            serverId = server.id,
            serverBaseUrl = server.baseUrl,
            config = config,
            networkType = networkType,
            previousStatus = previousStatus,
            confirmLocalFailureDelayMs = LAN_FALLBACK_CONFIRMATION_DELAY_MS,
            isLanReachable = ::isServerReachable
        )
    }

    private suspend fun isServerReachable(baseUrl: String): Boolean {
        val request = Request.Builder()
            .url("${baseUrl.removeSuffix("/")}/status")
            .get()
            .build()
        return runCatching {
            probeClient.newCall(request).execute().use { response: Response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }
    private fun shouldRetryLanOnWifi(
        status: ActiveServerConnectionStatus,
        networkType: NetworkConnectionType
    ): Boolean {
        return networkType == NetworkConnectionType.Wifi &&
            status.connectionMode == ServerConnectionMode.Auto &&
            status.lanFallbackToRemote &&
            status.switchingEnabled
    }
}
