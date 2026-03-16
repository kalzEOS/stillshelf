package com.stillshelf.app.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookshelfRealtimeClientTest {

    @Test
    fun buildRealtimeSocketEndpoint_usesRootSocketPathForPlainBaseUrl() {
        val endpoint = buildRealtimeSocketEndpoint("https://example.com")

        assertEquals("https://example.com", endpoint.originUrl)
        assertEquals("/socket.io/", endpoint.socketPath)
    }

    @Test
    fun buildRealtimeSocketEndpoint_preservesBasePathForReverseProxyUrls() {
        val endpoint = buildRealtimeSocketEndpoint("https://example.com/audiobookshelf/")

        assertEquals("https://example.com", endpoint.originUrl)
        assertEquals("/audiobookshelf/socket.io/", endpoint.socketPath)
    }

    @Test
    fun shouldMaintainRealtimeConnection_requiresForegroundAppState() {
        assertFalse(
            shouldMaintainRealtimeConnection(
                isAppInForeground = false,
                hasAuthenticatedTarget = true
            )
        )
    }

    @Test
    fun shouldMaintainRealtimeConnection_requiresAuthenticatedTarget() {
        assertFalse(
            shouldMaintainRealtimeConnection(
                isAppInForeground = true,
                hasAuthenticatedTarget = false
            )
        )
    }

    @Test
    fun shouldMaintainRealtimeConnection_allowsForegroundSocketWhenTargetExists() {
        assertTrue(
            shouldMaintainRealtimeConnection(
                isAppInForeground = true,
                hasAuthenticatedTarget = true
            )
        )
    }
}
