package com.stillshelf.app.data.realtime

import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.RealtimeInvalidation
import com.stillshelf.app.core.network.ActiveServerEndpointResolver
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

internal data class RealtimeSocketEndpoint(
    val originUrl: String,
    val socketPath: String
)

internal fun buildRealtimeSocketEndpoint(baseUrl: String): RealtimeSocketEndpoint {
    val normalized = baseUrl.trim().trimEnd('/')
    require(normalized.isNotBlank()) { "Base URL is required." }
    val baseUri = URI(normalized)
    val pathPrefix = baseUri.path.orEmpty().trim().trimEnd('/')
    val socketPath = buildString {
        append(if (pathPrefix.startsWith("/")) pathPrefix else "/$pathPrefix")
        if (endsWith("/").not()) append('/')
        append("socket.io/")
    }.replace("//", "/")
    val originUrl = URI(
        baseUri.scheme,
        baseUri.userInfo,
        baseUri.host,
        baseUri.port,
        null,
        null,
        null
    ).toString()
    return RealtimeSocketEndpoint(
        originUrl = originUrl,
        socketPath = if (socketPath.startsWith("/")) socketPath else "/$socketPath"
    )
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookshelfRealtimeClient @Inject constructor(
    serverDao: ServerDao,
    sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val activeServerEndpointResolver: ActiveServerEndpointResolver,
    okHttpClient: OkHttpClient
) {
    private data class RealtimeTarget(
        val serverId: String,
        val baseUrl: String,
        val token: String
    )

    companion object {
        private const val INVALIDATION_DEBOUNCE_MS = 750L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeHttpClient = okHttpClient.newBuilder()
        .readTimeout(1, TimeUnit.MINUTES)
        .build()
    private val mutableInvalidations = MutableSharedFlow<RealtimeInvalidation>(extraBufferCapacity = 32)
    val invalidations: Flow<RealtimeInvalidation> = mutableInvalidations.asSharedFlow()

    @Volatile
    private var activeSocket: Socket? = null

    @Volatile
    private var activeServerId: String? = null

    @Volatile
    private var lastInvalidationAtMs: Long = 0L

    init {
        scope.launch {
            combine(
                serverDao.observeServers(),
                sessionPreferences.state,
                activeServerEndpointResolver.observeActiveConnectionStatus()
            ) { servers, pref, connectionStatus ->
                Triple(servers, pref, connectionStatus)
            }
                .mapLatest { (servers, pref, connectionStatus) ->
                    val server = servers.firstOrNull { it.id == pref.activeServerId }
                    if (server == null) {
                        null
                    } else {
                        val token = secureTokenStorage.getToken(server.id)
                        val resolvedStatus = connectionStatus?.takeIf { it.serverId == server.id }
                            ?: activeServerEndpointResolver.resolveForServer(server)
                        token?.takeIf { it.isNotBlank() }?.let {
                            RealtimeTarget(
                                serverId = server.id,
                                baseUrl = resolvedStatus.effectiveBaseUrl,
                                token = it
                            )
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { target ->
                    reconnect(target)
                }
        }
    }

    private fun reconnect(target: RealtimeTarget?) {
        disconnect()
        if (target == null) return

        val endpoint = buildRealtimeSocketEndpoint(target.baseUrl)
        val options = IO.Options.builder()
            .setForceNew(true)
            .setReconnection(true)
            .setPath(endpoint.socketPath)
            .setAuth(
                mapOf(
                    "token" to target.token,
                    "authorization" to "Bearer ${target.token}"
                )
            )
            .setExtraHeaders(
                mapOf(
                    "Authorization" to listOf("Bearer ${target.token}")
                )
            )
            .build()
        options.callFactory = realtimeHttpClient
        options.webSocketFactory = realtimeHttpClient

        val socket = IO.socket(endpoint.originUrl, options)
        activeServerId = target.serverId
        socket.on(Socket.EVENT_CONNECT) {
            emitInvalidation(target.serverId, bypassDebounce = true)
        }
        socket.onAnyIncoming { _ ->
            emitInvalidation(target.serverId, bypassDebounce = false)
        }
        socket.connect()
        activeSocket = socket
    }

    private fun disconnect() {
        activeSocket?.disconnect()
        activeSocket?.close()
        activeSocket = null
        activeServerId = null
    }

    private fun emitInvalidation(serverId: String, bypassDebounce: Boolean) {
        val now = System.currentTimeMillis()
        if (!bypassDebounce && now - lastInvalidationAtMs < INVALIDATION_DEBOUNCE_MS) {
            return
        }
        lastInvalidationAtMs = now
        mutableInvalidations.tryEmit(
            RealtimeInvalidation(
                serverId = serverId,
                receivedAtMs = now
            )
        )
    }
}
