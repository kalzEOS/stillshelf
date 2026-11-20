package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.api.NavidromeAuth
import com.stillshelf.app.data.api.NavidromeApi
import io.mockk.every
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavidromeListeningClientTest {
    private val http = mockk<ListeningHttp>()
    private val auth = NavidromeAuth(baseUrl = "https://navidrome.invalid/music", username = "reader", encPassword = "70617373")
    private val repository = mockk<NavidromeRepository> { coEvery { currentAuth(any()) } returns auth }
    private val api = mockk<NavidromeApi> { every { coverArtUrl(any(), any(), any()) } returns null }

    @Test fun nativeHistoryReusesExistingCredentialsAndPreservesMissingMetrics() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            if (request.url.encodedPath.endsWith("auth/login")) {
                assertEquals("POST", request.method)
                val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                assertEquals("reader", body.getString("username"))
                assertEquals("pass", body.getString("password"))
                JSONObject("""{"token":"native-token"}""")
            } else {
                assertEquals("Bearer native-token", request.header("X-ND-Authorization"))
                JSONObject("""{"title":"Song","artist":"Artist"}""")
            }
        }
        coEvery { http.text(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("GET", request.method)
            assertEquals("Bearer native-token", request.header("X-ND-Authorization"))
            if (request.url.encodedPath.endsWith("scrobble"))
                """[{"id":1,"mediaFileId":"track","submissionTime":123}]""" to "12"
            else "[]" to "0"
        }
        val stats = NavidromeListeningClient(http, repository, api).statistics()
        assertEquals(12L, stats.totalScrobbles)
        assertNull(stats.listeningSeconds)
        assertTrue(stats.topArtists.isEmpty())
        assertEquals("Song", stats.recent.single().title)
        assertEquals(123L, stats.recent.single().timestampSeconds)
    }

    @Test fun reportingUsesStandardSubmissionFlagAndMilliseconds() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("/music/rest/scrobble.view", request.url.encodedPath)
            assertEquals("false", request.url.queryParameter("submission"))
            assertEquals("123000", request.url.queryParameter("time"))
            JSONObject("""{"subsonic-response":{"status":"ok"}}""")
        }
        NavidromeListeningClient(http, repository, api).report(auth, TrackListen("track", "Song", "Artist", "Album", 180, 123), false)
    }

    @Test fun unsupportedNativeApiDoesNotReturnFakeHistory() = runTest {
        coEvery { http.json(any()) } returns JSONObject("""{"token":"native-token"}""")
        coEvery { http.text(any()) } throws ListeningApiException(404, false, "History unsupported")
        try { NavidromeListeningClient(http, repository, api).statistics(); fail("Should report unsupported history") }
        catch (e: ListeningApiException) { assertEquals(404, e.status) }
    }

    @Test fun artistStatisticsUseBoundedServerAnnotationsRatherThanExtrapolatingRecentListens() = runTest {
        coEvery { http.json(any()) } returns JSONObject("""{"token":"native-token"}""")
        coEvery { http.text(any()) } answers {
            val request = firstArg<Request>()
            if (request.url.encodedPath.endsWith("artist")) {
                assertEquals("20", request.url.queryParameter("_end"))
                assertEquals("play_count", request.url.queryParameter("_sort"))
                """[{"id":"artist-id","name":"Artist","playCount":42,"largeImageUrl":"https://example.invalid/artist.jpg"}]""" to "1"
            } else if (request.url.encodedPath.endsWith("scrobble")) "[]" to "606"
            else "[]" to "0"
        }
        val stats = NavidromeListeningClient(http, repository, api).statistics()
        assertEquals(606L, stats.totalScrobbles)
        assertEquals(42L, stats.topArtists.single().plays)
        assertEquals("artist-id", stats.topArtists.single().artistId)
        assertEquals("https://example.invalid/artist.jpg", stats.topArtists.single().artworkUrl)
        assertNull(stats.listeningSeconds)
    }
}
