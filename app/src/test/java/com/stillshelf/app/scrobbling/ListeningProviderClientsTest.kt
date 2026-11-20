package com.stillshelf.app.scrobbling

import io.mockk.coEvery
import io.mockk.coVerify
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
class ListeningProviderClientsTest {
    @Test fun listenBrainzKoitoAndMalojaNowPlayingNeverContainCompletedListenTimestamp() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("POST", request.method)
            assertEquals("Token token", request.header("Authorization"))
            val json = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertEquals("playing_now", json.getString("listen_type"))
            assertFalse(json.getJSONArray("payload").getJSONObject(0).has("listened_at"))
            JSONObject("""{"status":"ok"}""")
        }
        val lb = ListenBrainzClient(http)
        lb.playingNow(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = "token"), listen)
        KoitoClient(http, lb).playingNow(ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", token = "token"), listen)
        MalojaClient(http, lb).playingNow(ListeningConnection(ListeningProvider.MALOJA, "https://maloja.invalid", token = "token"), listen)
        coVerify(exactly = 3) { http.json(any()) }
    }

    @Test fun directSubmissionsRejectStatisticsOnlyCredentialsWithoutPostingAnything() = runTest {
        try {
            ListenBrainzClient(http).submit(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = "token", allowSubmissions = false), listen)
            fail("Statistics permission must not authorize submissions")
        } catch (_: IllegalArgumentException) { }
        try {
            LastFmClient(http).submit(ListeningConnection(ListeningProvider.LAST_FM, token = "session", apiKey = "key", apiSecret = "secret", allowSubmissions = false), listen)
            fail("Statistics permission must not authorize Last.fm submissions")
        } catch (_: IllegalArgumentException) { }
        coVerify(exactly = 0) { http.json(any()) }
    }

    @Test fun lastFmNowPlayingIsSignedAndDoesNotSubmitACompletedListen() = runTest {
        coEvery { http.json(any()) } answers {
            val body = firstArg<Request>().body as okhttp3.FormBody
            val params = (0 until body.size).associate { body.name(it) to body.value(it) }
            assertEquals("track.updateNowPlaying", params["method"])
            assertEquals("session", params["sk"])
            assertFalse(params.containsKey("timestamp"))
            assertEquals(lastFmSignature(params, "secret"), params["api_sig"])
            JSONObject("""{"nowplaying":{}}""")
        }
        LastFmClient(http).playingNow(ListeningConnection(ListeningProvider.LAST_FM, token = "session", apiKey = "key", apiSecret = "secret"), listen)
    }
    private val http = mockk<ListeningHttp>()
    private val listen = TrackListen("track", "Song", "Artist", "Album", 180, 1234567890)

    @Test fun listenBrainzInvalidTokenIsAnAuthorizationErrorNotANetworkError() = runTest {
        coEvery { http.json(any()) } returns JSONObject("""{"valid":false}""")
        try {
            ListenBrainzClient(http).validate(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = "invalid"))
            fail("Expected invalid token")
        } catch (e: ListeningApiException) {
            assertEquals(401, e.status)
            assertTrue(e.message!!.contains("rejected"))
        }
    }

    @Test fun listenBrainzTrimsCopiedTokenWithoutPuttingItInUrl() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("Token saved-token", request.header("Authorization"))
            assertFalse(request.url.toString().contains("saved-token"))
            JSONObject("""{"valid":true,"user_name":"reader"}""")
        }
        assertEquals("reader", ListenBrainzClient(http).validate(
            ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = " saved-token\n")))
    }

    @Test fun listenBrainzKeepsAvailableCountWhenRecentAndChartsFail() = runTest {
        coEvery { http.json(any()) } answers {
            if (firstArg<Request>().url.encodedPath.endsWith("listen-count")) JSONObject("""{"payload":{"count":42}}""")
            else throw ListeningApiException(503, true, "Service unavailable")
        }
        val stats = ListenBrainzClient(http).statistics(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"))
        assertEquals(42L, stats.totalScrobbles)
        assertTrue(stats.recent.isEmpty())
        assertTrue(stats.topArtists.isEmpty())
        assertNotNull(stats.note)
        assertNull(stats.listeningSeconds)
        assertEquals(setOf(ListeningMetric.RECENT, ListeningMetric.ARTISTS, ListeningMetric.ALBUMS, ListeningMetric.TRACKS), stats.unavailableSections)
    }

    @Test fun listenBrainzSingularArtistIdentityIsPreservedForLibraryMatching() {
        val id = "0b91b4e7-5c97-4446-8665-27fe6026cd2f"
        val entry = listenBrainzEntry(JSONObject("""{"artist_name":"كاظم الساهر","artist_mbid":"$id","listen_count":7}"""), "artist", "artist_name")
        assertEquals(id, entry.musicBrainzArtistId)
        assertEquals("https://musicbrainz.org/artist/$id", entry.detailsUrl)
    }

    @Test fun listenBrainzTotalOutageReportsErrorInsteadOfEmptySuccessfulStatistics() = runTest {
        coEvery { http.json(any()) } throws ListeningConnectionException("ListenBrainz did not respond in time.")
        try {
            ListenBrainzClient(http).statistics(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"))
            fail("Expected service unavailable")
        } catch (e: ListeningConnectionException) { assertTrue(e.message!!.contains("did not respond")) }
    }

    @Test fun listenBrainzValidationTimesOutWithoutChangingCredentials() = runTest {
        coEvery { http.json(any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "saved-token")
        try {
            ListenBrainzClient(http).validate(connection)
            fail("Expected bounded timeout")
        } catch (e: ListeningConnectionException) {
            assertTrue(e.message!!.contains("did not respond in time"))
            assertEquals("saved-token", connection.token)
        }
    }

    @Test fun listenBrainzSubmissionUsesTokenHeaderAndOriginalListenTime() = runTest {
        var request: Request? = null
        coEvery { http.json(any()) } answers { request = firstArg(); JSONObject("{\"status\":\"ok\"}") }
        ListenBrainzClient(http).submit(ListeningConnection(ListeningProvider.KOITO, "https://example.invalid/base", token = "secret"), listen)
        val submitted = requireNotNull(request)
        assertEquals("/base/apis/listenbrainz/1/submit-listens", submitted.url.encodedPath)
        assertEquals("Token secret", submitted.header("Authorization"))
        assertFalse(submitted.url.toString().contains("secret"))
        val body = Buffer().also { submitted.body!!.writeTo(it) }.readUtf8()
        val payload = JSONObject(body).getJSONArray("payload").getJSONObject(0)
        assertEquals(1234567890L, payload.getLong("listened_at"))
        assertEquals("Artist", payload.getJSONObject("track_metadata").getString("artist_name"))
    }

    @Test fun listenBrainzStatisticsUseOnlyReadsAndDoNotInventListeningTime() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("GET", request.method)
            if (!request.url.encodedPath.endsWith("listen-count")) assertEquals(
                if (request.url.encodedPath.endsWith("listens")) "1000" else "50", request.url.queryParameter("count"))
            JSONObject(when {
                request.url.encodedPath.endsWith("listen-count") -> """{"payload":{"count":42}}"""
                request.url.encodedPath.endsWith("listens") -> """{"payload":{"listens":[{"listened_at":123,"track_metadata":{"track_name":"Song","artist_name":"Artist"}}]}}"""
                else -> """{"payload":{}}"""
            })
        }
        val stats = ListenBrainzClient(http).statistics(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"))
        assertEquals(42L, stats.totalScrobbles)
        assertNull(stats.listeningSeconds)
        assertEquals("Song", stats.recent.single().title)
        coVerify(exactly = 0) { http.text(any()) }
    }

    @Test fun listenBrainzPagesUseOffsetsAndPreserveMusicBrainzIdentityAndArt() = runTest {
        val mbid = "bf9e91ea-8029-4a04-a26a-224e00a83266"
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("50", request.url.queryParameter("offset"))
            assertEquals("50", request.url.queryParameter("count"))
            JSONObject("""{"payload":{"releases":[{"release_name":"Album","artist_name":"Artist","listen_count":7,"release_mbid":"$mbid"}]}}""")
        }
        val page = ListenBrainzClient(http).chartPage(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"), ListeningChart.ALBUMS, 50)
        assertEquals("https://musicbrainz.org/release/$mbid", page.entries.single().detailsUrl)
        assertEquals("https://coverartarchive.org/release/$mbid/front-250", page.entries.single().artworkUrl)
        assertEquals(7L, page.entries.single().plays)
        assertFalse(page.hasMore)
    }

    @Test fun listenBrainzRecentMappingRetainsCoverAndRecordingLinkAndUnmatchedSearchIsEncoded() {
        val mbid = "bf9e91ea-8029-4a04-a26a-224e00a83266"
        val entry = listenBrainzEntry(JSONObject("""{"track_name":"Song","artist_name":"Artist","mbid_mapping":{"recording_mbid":"$mbid","caa_release_mbid":"$mbid","caa_id":123}}"""), "track", "track_name")
        assertEquals("https://musicbrainz.org/recording/$mbid", entry.detailsUrl)
        assertEquals(mbid, entry.musicBrainzRecordingId)
        assertEquals("https://coverartarchive.org/release/$mbid/123-250.jpg", entry.artworkUrl)
        val unknown = listenBrainzEntry(JSONObject("""{"artist_name":"كاظم & الفنان"}"""), "artist", "artist_name")
        assertTrue(unknown.detailsUrl!!.startsWith("https://musicbrainz.org/search?"))
        assertNull(unknown.plays)
    }

    @Test fun koitoNativeStatisticsUseActualSummaryAndPaginatedListenShapes() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("GET", request.method)
            assertEquals("Token secret", request.header("Authorization"))
            JSONObject(if (request.url.encodedPath.endsWith("summary")) """{
              "plays":12,"minutes_listened":34,
              "top_artists":[{"rank":1,"item":{"name":"Artist","listen_count":8}}],
              "top_albums":[],"top_tracks":[]
            }""" else """{"items":[{"time":"2026-11-18T00:00:00Z","track":{"title":"Song","artists":[{"name":"Artist"}]}}]}""")
        }
        val stats = KoitoClient(http, ListenBrainzClient(http)).statistics(ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", token = "secret"))
        assertEquals(12L, stats.totalScrobbles)
        assertEquals(2040L, stats.listeningSeconds)
        assertEquals("Artist", stats.topArtists.single().title)
        assertEquals("Artist", stats.recent.single().subtitle)
        assertNotNull(stats.recent.single().timestampSeconds)
    }

    @Test fun malojaChartsAndRecentHistoryUseNativeReadEndpoints() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("GET", request.method)
            JSONObject(when {
                request.url.encodedPath.endsWith("numscrobbles") -> """{"amount":7}"""
                request.url.encodedPath.endsWith("artists") -> """{"list":[{"artist":"Artist","scrobbles":7}]}"""
                request.url.encodedPath.endsWith("scrobbles") -> """{"list":[{"time":123,"track":{"title":"Song","artists":["Artist"]}}]}"""
                else -> """{"list":[]}"""
            })
        }
        val stats = MalojaClient(http, ListenBrainzClient(http)).statistics(ListeningConnection(ListeningProvider.MALOJA, "https://maloja.invalid"))
        assertEquals(7L, stats.totalScrobbles)
        assertNull(stats.listeningSeconds)
        assertEquals(7L, stats.topArtists.single().plays)
        assertEquals(123L, stats.recent.single().timestampSeconds)
    }

    @Test fun lastFmSignsSortedUtf8ParametersAndExcludesFormat() {
        assertEquals("b87d61da3cda91a8b6746c4aef55d6f8", lastFmSignature(mapOf("token" to "yyyyyy", "api_key" to "xxxxxxxxxx", "method" to "auth.getSession", "format" to "json"), "ilovecher"))
    }

    @Test fun lastFmAuthorizationAndSubmissionsUseDifferentCredentials() = runTest {
        val requests = mutableListOf<String>()
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("POST", request.method)
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            requests += body
            JSONObject(when {
                body.contains("auth.getToken") -> """{"token":"request-token"}"""
                body.contains("auth.getSession") -> """{"session":{"name":"reader","key":"session-key"}}"""
                else -> """{"scrobbles":{"@attr":{"accepted":1,"ignored":0}}}"""
            })
        }
        val client = LastFmClient(http)
        val draft = ListeningConnection(ListeningProvider.LAST_FM, apiKey = "key", apiSecret = "secret")
        val token = client.authorizationToken(draft)
        val saved = client.finishAuthorization(draft, token)
        client.submit(saved, listen)
        assertEquals("session-key", saved.token)
        assertTrue(requests[2].contains("sk=session-key"))
        assertFalse(requests.any { it.contains("apiSecret") || it.contains("=secret") })
    }

    @Test fun urlsPreserveServerSubpathsAndRejectEmbeddedSecrets() {
        assertEquals("https://example.invalid/music/api/scrobble", listeningUrl("https://example.invalid/music", "api/scrobble").toString())
        try { listeningUrl("https://user:password@example.invalid", "test"); fail("Embedded credentials must be rejected") }
        catch (_: IllegalArgumentException) { }
    }
}
