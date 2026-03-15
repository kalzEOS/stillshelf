package com.stillshelf.app.core.network

import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveServerEndpointResolverTest {

    @Test
    fun resolveActiveServerConnectionStatus_usesLocalRouteOnWifiWhenLanIsReachable() = runTest {
        val status = resolveActiveServerConnectionStatus(
            serverId = "server-1",
            serverBaseUrl = "https://saved.example.com",
            config = ServerEndpointSwitchingConfig(
                enabled = true,
                lanBaseUrl = "http://192.168.1.10:13378",
                wanBaseUrl = "https://books.example.com"
            ),
            networkType = NetworkConnectionType.Wifi,
            isLanReachable = { true }
        )

        assertEquals(ServerConnectionRoute.Local, status.route)
        assertEquals("http://192.168.1.10:13378", status.effectiveBaseUrl)
        assertFalse(status.lanFallbackToRemote)
    }

    @Test
    fun resolveActiveServerConnectionStatus_fallsBackToRemoteOnWifiWhenLanIsNotReachable() = runTest {
        val status = resolveActiveServerConnectionStatus(
            serverId = "server-1",
            serverBaseUrl = "https://saved.example.com",
            config = ServerEndpointSwitchingConfig(
                enabled = true,
                lanBaseUrl = "http://192.168.1.10:13378",
                wanBaseUrl = "https://books.example.com"
            ),
            networkType = NetworkConnectionType.Wifi,
            isLanReachable = { false }
        )

        assertEquals(ServerConnectionRoute.Remote, status.route)
        assertEquals("https://books.example.com", status.effectiveBaseUrl)
        assertTrue(status.lanFallbackToRemote)
    }

    @Test
    fun resolveActiveServerConnectionStatus_confirmsFailureBeforeLeavingLocalRoute() = runTest {
        val previousStatus = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "http://192.168.1.10:13378",
            route = ServerConnectionRoute.Local,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        var probeCount = 0

        val status = resolveActiveServerConnectionStatus(
            serverId = "server-1",
            serverBaseUrl = "https://saved.example.com",
            config = ServerEndpointSwitchingConfig(
                enabled = true,
                lanBaseUrl = "http://192.168.1.10:13378",
                wanBaseUrl = "https://books.example.com"
            ),
            networkType = NetworkConnectionType.Wifi,
            previousStatus = previousStatus,
            isLanReachable = {
                probeCount += 1
                probeCount != 1
            }
        )

        assertEquals(2, probeCount)
        assertEquals(ServerConnectionRoute.Local, status.route)
        assertFalse(status.lanFallbackToRemote)
    }

    @Test
    fun resolveActiveServerConnectionStatus_usesSavedServerWhenSwitchingDisabled() = runTest {
        val status = resolveActiveServerConnectionStatus(
            serverId = "server-1",
            serverBaseUrl = "https://saved.example.com/",
            config = ServerEndpointSwitchingConfig(
                enabled = false,
                lanBaseUrl = "http://192.168.1.10:13378",
                wanBaseUrl = "https://books.example.com"
            ),
            networkType = NetworkConnectionType.Wifi,
            isLanReachable = { true }
        )

        assertEquals(ServerConnectionRoute.Default, status.route)
        assertEquals("https://saved.example.com", status.effectiveBaseUrl)
        assertFalse(status.switchingEnabled)
    }
}
