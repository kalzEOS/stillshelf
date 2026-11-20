package com.stillshelf.app.scrobbling

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListenBrainzInsightsClientTest {
    private val http = mockk<ListeningHttp>()
    private val client = ListenBrainzInsightsClient(http)
    private val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader / عربي", token = " private-token ", allowSubmissions = false)

    @Test fun readsUseEncodedAccountCalendarRangeAndExistingTokenWithoutSubmissions() = runTest {
        coEvery { http.text(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("GET", request.method)
            assertEquals("Token private-token", request.header("Authorization"))
            assertEquals(listOf("1", "stats", "user", connection.username, "releases"), request.url.pathSegments)
            assertEquals("this_month", request.url.queryParameter("range"))
            assertEquals("10", request.url.queryParameter("count"))
            assertFalse(request.url.toString().contains("private-token"))
            """{"payload":{"range":"this_month","releases":[{"release_name":"Album","artist_name":"Artist","listen_count":4}]}}""" to null
        }
        assertEquals(4L, client.fetch(connection, InsightPeriod.THIS_MONTH, InsightKind.ALBUMS)!!.rows.single().count)
    }

    @Test fun notCalculatedAndUnsupportedEndpointsAreNotNetworkErrors() = runTest {
        coEvery { http.text(any()) } returns ("" to null)
        assertNull(client.fetch(connection, InsightPeriod.ALL, InsightKind.DAILY))
        coEvery { http.text(any()) } throws ListeningApiException(404, false, "Unavailable")
        assertNull(client.fetch(connection, InsightPeriod.ALL, InsightKind.DAILY))
    }

    @Test fun otherProvidersNeverAccidentallyCallListenBrainz() = runTest {
        for (provider in ListeningProvider.entries.filterNot { it == ListeningProvider.LISTEN_BRAINZ }) {
            try { client.fetch(ListeningConnection(provider, username = "reader"), InsightPeriod.ALL, InsightKind.DAILY); fail("Provider guard") }
            catch (_: IllegalArgumentException) { }
        }
        coVerify(exactly = 0) { http.text(any()) }
    }
}
