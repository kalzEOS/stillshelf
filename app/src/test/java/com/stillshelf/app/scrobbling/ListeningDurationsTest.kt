package com.stillshelf.app.scrobbling

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.test.runTest
import io.mockk.*
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningDurationsTest {
    @Test fun suppliedDurationsPreferActualPlayedTimeAndNeverUseCounts() {
        assertEquals(RecordedDuration(45, true), recordedDuration(JSONObject("""{"additional_info":{"duration_played":45,"duration_ms":180000}}""")))
        assertEquals(RecordedDuration(180, false), recordedDuration(JSONObject("""{"additional_info":{"duration_ms":180000}}""")))
        assertNull(recordedDuration(JSONObject("""{"listen_count":100,"duration_ms":-1}""")))
        assertNull(recordedDuration(JSONObject("""{"duration_ms":999999999999}""")))
    }
    @Test fun activeHoursExplicitlyNameAmPmIncludingNoonAndMidnight() {
        assertEquals("9–10 AM", activeHourLabel(9, Locale.US))
        assertEquals("12–1 PM", activeHourLabel(12, Locale.US))
        assertEquals("11 PM–12 AM", activeHourLabel(23, Locale.US))
    }
    @Test fun liveListenShapeProducesDurationChartDataWithoutMultiplyingCounts() = runTest {
        val today = LocalDate.of(2026, 11, 19)
        val stamp = today.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val http = mockk<ListeningHttp> {
            coEvery { json(any()) } returns JSONObject("""{"payload":{"listens":[
              {"listened_at":$stamp,"track_metadata":{"additional_info":{"duration_ms":180000}}},
              {"listened_at":${stamp + 300},"track_metadata":{"additional_info":{"duration_ms":120000}}}]}}""")
        }
        val result = ListeningTrendsClient(http).fetch(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader"), TrendRange.MONTH, today)
        assertTrue(result.complete)
        assertTrue(result.points.all { it.seconds != null })
        assertEquals(300L, result.points.sumOf { it.seconds!! })
        assertEquals("Based on recorded track lengths", result.durationNote)
        assertEquals(result, parseTrends(trendsJson(result)))
    }
    @Test fun allTimeDurationRequiresACompleteBoundedHistoryAndEveryRecordDuration() = runTest {
        val http = mockk<ListeningHttp>()
        var count = 2
        var missing = false
        coEvery { http.json(any()) } answers {
            val request = firstArg<Request>()
            when {
                request.url.encodedPath.endsWith("listen-count") -> JSONObject("""{"payload":{"count":$count}}""")
                request.url.encodedPath.endsWith("listens") -> {
                    assertEquals(if (count < 1000) "1000" else "50", request.url.queryParameter("count"))
                    JSONObject("""{"payload":{"listens":[
                      {"listened_at":1,"track_metadata":{"additional_info":{"duration_ms":180000}}},
                      {"listened_at":2,"track_metadata":{"additional_info":${if (missing) "{}" else "{\"duration_ms\":120000}"}}}]}}""")
                }
                else -> JSONObject("""{"payload":{"artists":[],"releases":[],"recordings":[]}}""")
            }
        }
        val client = ListenBrainzClient(http)
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader")
        assertEquals(300L, client.statistics(connection).listeningSeconds)
        missing = true
        assertNull(client.statistics(connection).listeningSeconds)
        missing = false; count = 5000
        assertNull(client.statistics(connection).listeningSeconds)
    }
}
