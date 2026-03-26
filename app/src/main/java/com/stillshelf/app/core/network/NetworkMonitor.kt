package com.stillshelf.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class NetworkConnectionType {
    Wifi,
    Cellular,
    Other,
    Offline
}

data class NetworkConnectionState(
    val type: NetworkConnectionType,
    val identity: String
)

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val connectionState: StateFlow<NetworkConnectionState> = observeConnectionStateInternal()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = currentConnectionState()
        )

    fun observeConnectionState(): Flow<NetworkConnectionState> = connectionState

    fun observeConnectionType(): Flow<NetworkConnectionType> = connectionState.map { it.type }

    fun currentConnectionType(): NetworkConnectionType = currentConnectionState().type

    fun currentConnectionState(): NetworkConnectionState {
        val manager = connectivityManager ?: return NetworkConnectionState(
            type = NetworkConnectionType.Offline,
            identity = "offline"
        )
        return manager.resolvePreferredConnectionState()
    }

    private fun observeConnectionStateInternal(): Flow<NetworkConnectionState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkConnectionState(NetworkConnectionType.Offline, "offline"))
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentConnectionState())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentConnectionState())
            }

            override fun onLost(network: Network) {
                trySend(currentConnectionState())
            }
        }

        trySend(currentConnectionState())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        awaitClose {
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
}

private fun ConnectivityManager.resolvePreferredConnectionState(): NetworkConnectionState {
    val activeNetwork = activeNetwork
    val candidates = allNetworks.mapNotNull { network ->
        val capabilities = getNetworkCapabilities(network) ?: return@mapNotNull null
        network to capabilities
    }
    val preferred = candidates.maxByOrNull { (network, capabilities) ->
        capabilities.connectionPriority(isActive = network == activeNetwork)
    }
    if (
        preferred != null &&
        preferred.second.connectionPriority(isActive = preferred.first == activeNetwork) > 0
    ) {
        return NetworkConnectionState(
            type = preferred.second.toConnectionType(),
            identity = preferred.first.toString()
        )
    }
    activeNetwork ?: return NetworkConnectionState(
        type = NetworkConnectionType.Offline,
        identity = "offline"
    )
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return NetworkConnectionState(
        type = NetworkConnectionType.Offline,
        identity = activeNetwork.toString()
    )
    return NetworkConnectionState(
        type = capabilities.toConnectionType(),
        identity = activeNetwork.toString()
    )
}

private fun NetworkCapabilities.connectionPriority(isActive: Boolean): Int {
    if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return 0
    val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    if (!isValidated && !isActive) return 0

    val transportScore = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 300
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 200
        else -> 100
    }
    return when {
        else -> transportScore +
            if (isValidated) 40 else 0 +
            if (isActive) 20 else 0
    }
}

private fun NetworkCapabilities.toConnectionType(): NetworkConnectionType {
    return when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkConnectionType.Wifi
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectionType.Cellular
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkConnectionType.Other
        else -> NetworkConnectionType.Offline
    }
}
