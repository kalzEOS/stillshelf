package com.stillshelf.app.absstats

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AbsPodcastHistoryTest {
    @Test fun nativeEpisodeResolutionRequiresExactIdentityAndPreservesMeasuredCheckpoint() {
        val rows = JSONArray("""[{"id":"native-id","guid":"rss-guid","enclosure":{"url":"https://example.invalid/audio"},"title":"Same title"}]""")
        assertEquals("native-id", nativePodcastEpisodeId(rows, "native-id"))
        assertEquals("native-id", nativePodcastEpisodeId(rows, "rss-guid"))
        assertEquals("native-id", nativePodcastEpisodeId(rows, "https://example.invalid/audio"))
        assertNull(nativePodcastEpisodeId(rows, "Same title"))
        val checkpoint = JSONObject("""{"id":"session","episodeId":"rss-guid","startedAt":1000,"updatedAt":2000,"currentTime":130,"timeListening":30}""")
        val mapped = nativePodcastSession(checkpoint, "native-id")
        assertEquals("native-id", mapped.getString("episodeId"))
        for (field in listOf("id", "startedAt", "updatedAt", "currentTime", "timeListening")) assertEquals(checkpoint.get(field), mapped.get(field))
        rows.put(JSONObject("""{"id":"other-native-id","guid":"rss-guid"}"""))
        assertNull(nativePodcastEpisodeId(rows, "rss-guid"))
    }
    private val progress = JSONArray("""[{"libraryItemId":"show","episodeId":"episode","currentTime":1200}]""")
    @Test fun savedPositionsIdentifyMissingHistoryWithoutInventingTime() {
        val gap = missingPodcastHistory(progress, JSONArray(), emptyList(), 1000).single()
        assertEquals("episode", gap.episodeId)
        assertEquals(1000L, gap.detectedAt)
        assertTrue(missingPodcastHistory(JSONArray("""[{"libraryItemId":"book","currentTime":1200}]"""), JSONArray(), emptyList(), 1000).isEmpty())
        assertEquals(listOf(gap), podcastGaps(JSONObject().put("gaps", JSONArray().put(gap.json()))))
    }
    @Test fun newPlaybackDoesNotEraseKnownOlderGapOrDoubleCountEpisodes() {
        val old = AbsPodcastGap("show", "episode", "podcasts", 1000)
        val sessions = JSONArray("""[{"libraryItemId":"show","episodeId":"episode","startedAt":2000,"timeListening":30}]""")
        assertEquals(listOf(old), missingPodcastHistory(progress, sessions, listOf(old), 3000))
        assertTrue(missingPodcastHistory(progress, sessions, emptyList(), 3000).isEmpty())
        sessions.getJSONObject(0).put("startedAt", 500)
        assertTrue(missingPodcastHistory(progress, sessions, listOf(old), 3000).isEmpty())
    }
    @Test fun unknownPodcastTimeIsNotPresentedAsZeroAndKnownRecordedTimeIsPreserved() {
        val data = AbsListeningStatistics(0.0, null, emptyMap(), emptyList(), emptyList())
        val snapshot = AbsStatisticsSnapshot(statistics = data, podcastHistoryIncomplete = true)
        val library = com.stillshelf.app.core.model.Library("podcasts", "server", "Podcasts", "podcast")
        assertEquals("Listening time unavailable", absTotalTime(snapshot, AbsStatisticsScope("podcasts", listOf(library))))
        assertEquals("30 sec", absTotalTime(snapshot.copy(statistics = data.copy(seconds = 30.0)), AbsStatisticsScope("podcasts", listOf(library))))
        assertEquals("0 min", absTotalTime(snapshot.copy(podcastHistoryIncomplete = false), AbsStatisticsScope("podcasts", listOf(library))))
    }
}
