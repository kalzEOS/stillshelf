package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.ServerConnectionRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelRoutingLabelTest {

    @Test
    fun resolveCurrentConnectionLabel_prefersActiveRouteWhenSwitchingIsEnabled() {
        val label = resolveCurrentConnectionLabel(
            serverPresent = true,
            switchingIsActive = true,
            currentRoute = ServerConnectionRoute.Remote,
            effectiveBaseUrl = "http://192.168.1.10:13378",
            localBaseUrl = "http://192.168.1.10:13378",
            remoteBaseUrl = "https://books.example.com"
        )

        assertEquals("Remote", label)
    }

    @Test
    fun resolveCurrentConnectionLabel_usesExplicitRemoteMatchWhenSwitchingIsDisabled() {
        val label = resolveCurrentConnectionLabel(
            serverPresent = true,
            switchingIsActive = false,
            currentRoute = ServerConnectionRoute.Default,
            effectiveBaseUrl = "https://books.example.com",
            localBaseUrl = "http://192.168.1.10:13378",
            remoteBaseUrl = "https://books.example.com"
        )

        assertEquals("Remote", label)
    }

    @Test
    fun resolveCurrentConnectionLabel_fallsBackToCurrentServerWhenNoRoleMatches() {
        val label = resolveCurrentConnectionLabel(
            serverPresent = true,
            switchingIsActive = false,
            currentRoute = ServerConnectionRoute.Default,
            effectiveBaseUrl = "https://saved.example.com",
            localBaseUrl = "http://192.168.1.10:13378",
            remoteBaseUrl = "https://books.example.com"
        )

        assertEquals("Current server", label)
    }
}
