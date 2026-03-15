package com.stillshelf.app.core.network

import com.stillshelf.app.core.model.ActiveEndpointHealth
import com.stillshelf.app.core.model.EndpointReachabilityStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Singleton
class ActiveEndpointHealthMonitor @Inject constructor(
    activeServerEndpointResolver: ActiveServerEndpointResolver,
    networkMonitor: NetworkMonitor,
    okHttpClient: OkHttpClient
) {
    companion object {
        private const val PROBE_TIMEOUT_MS = 2_000L
        private const val PROBE_INTERVAL_MS = 30_000L
        private const val UNAVAILABLE_RETRY_INTERVAL_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val mutableHealth = MutableStateFlow<ActiveEndpointHealth?>(null)

    val health: StateFlow<ActiveEndpointHealth?> = mutableHealth

    init {
        scope.launch {
            combine(
                activeServerEndpointResolver.observeActiveConnectionStatus().filterNotNull(),
                networkMonitor.observeConnectionState()
            ) { status, networkType ->
                status to networkType
            }.collectLatest { (status, _) ->
                    mutableHealth.value = ActiveEndpointHealth(
                        serverId = status.serverId,
                        endpointUrl = status.effectiveBaseUrl,
                        reachabilityStatus = EndpointReachabilityStatus.Checking
                    )
                    while (currentCoroutineContext().isActive) {
                        val result = probe(
                            serverId = status.serverId,
                            endpointUrl = status.effectiveBaseUrl
                        )
                        mutableHealth.value = result
                        delay(
                            if (result.reachabilityStatus == EndpointReachabilityStatus.Reachable) {
                                PROBE_INTERVAL_MS
                            } else {
                                UNAVAILABLE_RETRY_INTERVAL_MS
                            }
                        )
                    }
                }
        }
    }

    fun observeHealth(): Flow<ActiveEndpointHealth?> = health

    private fun probe(
        serverId: String,
        endpointUrl: String
    ): ActiveEndpointHealth {
        val request = Request.Builder()
            .url("${endpointUrl.removeSuffix("/")}/status")
            .get()
            .build()
        val startedAtMs = System.currentTimeMillis()
        return runCatching {
            probeClient.newCall(request).execute().use { response ->
                val latencyMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                if (response.isSuccessful) {
                    ActiveEndpointHealth(
                        serverId = serverId,
                        endpointUrl = endpointUrl,
                        reachabilityStatus = EndpointReachabilityStatus.Reachable,
                        latencyMs = latencyMs
                    )
                } else {
                    ActiveEndpointHealth(
                        serverId = serverId,
                        endpointUrl = endpointUrl,
                        reachabilityStatus = EndpointReachabilityStatus.Unavailable
                    )
                }
            }
        }.getOrElse {
            ActiveEndpointHealth(
                serverId = serverId,
                endpointUrl = endpointUrl,
                reachabilityStatus = EndpointReachabilityStatus.Unavailable
            )
        }
    }
}
