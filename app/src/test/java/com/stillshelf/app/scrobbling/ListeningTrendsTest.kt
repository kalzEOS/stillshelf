package com.stillshelf.app.scrobbling

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningTrendsTest {
    private val http = mockk<ListeningHttp>()
    private val today = LocalDate.of(2026, 11, 19)
    @Test fun koitoMissingDaysAreUnknownAndDurationIsNotEstimated() = runTest {
        coEvery { http.json(any()) } answers {
            assertEquals("day", firstArg<Request>().url.queryParameter("step"))
            JSONObject("""{"activity":[{"start_time":"2026-11-19T00:00:00Z","listens":7}],"streak":2}""")
        }
        val data = ListeningTrendsClient(http).fetch(ListeningConnection(ListeningProvider.KOITO, "https://example.invalid", token = "saved"), TrendRange.WEEK, today)
        assertFalse(data.complete)
        assertNull(data.points.single().seconds)
        assertEquals(7L, data.points.single().count)
        assertEquals(2, data.streak)
    }
    @Test fun malojaUsesListenDurationNotTrackLengthAndAuthoritativeTotal() = runTest {
        coEvery { http.json(any()) } answers { JSONObject(if (firstArg<Request>().url.encodedPath.endsWith("numscrobbles")) """{"amount":1}""" else
            """{"status":"ok","list":[{"time":1795046400,"duration":90,"track":{"length":400}}]}""") }
        val data = ListeningTrendsClient(http).fetch(ListeningConnection(ListeningProvider.MALOJA, "https://example.invalid"), TrendRange.MONTH, today)
        assertEquals(90L, data.points.sumOf { it.seconds ?: 0 })
        assertTrue(data.complete)
    }
    @Test fun lastFmUsesSavedReadCredentialsAndExcludesNowPlaying() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertEquals("key", request.url.queryParameter("api_key"))
            assertEquals("user.getRecentTracks", request.url.queryParameter("method"))
            JSONObject("""{"recenttracks":{"@attr":{"total":"1"},"track":[{"date":{"uts":"1795046400"}},{"@attr":{"nowplaying":"true"}}]}}""")
        }
        val data = ListeningTrendsClient(http).fetch(ListeningConnection(ListeningProvider.LAST_FM, username = "reader", apiKey = "key"), TrendRange.MONTH, today)
        assertEquals(1L, data.points.sumOf { it.count })
        assertTrue(data.points.all { it.seconds == null })
    }
    @Test fun incompleteSamplesDoNotFillMissingDaysAndUtcBoundariesAreRespected() {
        val timestamp = OffsetDateTime.parse("2026-11-18T23:30:00-05:00").toEpochSecond()
        val data = recordTrends(listOf(timestamp to null), TrendRange.WEEK, today, false, ZoneOffset.UTC)
        assertEquals(today, data.points.single().date)
        assertFalse(data.complete)
        assertNull(data.hour)
        val local = recordTrends(listOf(timestamp to null), TrendRange.WEEK, today, false, ZoneId.of("America/New_York"))
        assertEquals(today.minusDays(1), local.points.single().date)
    }
    @Test fun bucketAggregationPreservesExactCountsDurationsAndCoverage() {
        val points = listOf(TrendPoint(today, 2, 60), TrendPoint(today.minusDays(1), 3, 90))
        val result = trendBuckets(points, TrendRange.YEAR).single()
        assertEquals(5L, result.count)
        assertEquals(150L, result.seconds)
        assertEquals(today.minusDays(1), result.date)
        assertEquals(today, result.end)
    }
    @Test fun listenBrainzUsesValidBoundedRequestsAndFiltersHistoryLocally() = runTest {
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            assertNull(request.url.queryParameter("min_ts"))
            JSONObject("""{"payload":{"listens":[{"listened_at":1794707594},{"listened_at":1794707370}]}}""")
        }
        val data = ListeningTrendsClient(http).fetch(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"), TrendRange.QUARTER, today)
        assertEquals(2L, data.points.sumOf { it.count })
        assertTrue(data.complete)
    }
    @Test fun navidromeUnsupportedHistoryIsHonestAndCachedDataRoundTrips() = runTest {
        assertTrue(ListeningTrendsClient(http).fetch(null, TrendRange.MONTH, today).points.isEmpty())
        val data = ListeningTrends(listOf(TrendPoint(today, 3)), true, false, "sample")
        assertEquals(data, parseTrends(trendsJson(data)))
    }
}
