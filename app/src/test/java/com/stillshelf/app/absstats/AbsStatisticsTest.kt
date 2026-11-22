package com.stillshelf.app.absstats

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AbsStatisticsTest {
    @Test fun cachedSessionKeepsStatisticsAndDropsUnusedPrivateAndLargeFields() {
        val raw = JSONObject("""{"id":"s","libraryId":"mine","libraryItemId":"book","mediaType":"book","timeListening":60,"date":"2026-01-01",
            "displayTitle":"Book","deviceInfo":{"ipAddress":"private"},"chapters":[{"title":"Chapter"}],
            "mediaMetadata":{"title":"Book","description":"Unneeded description","authors":[{"name":"Author","id":"author-id"}],
            "genres":["Fiction"],"narrators":["Narrator"]}}""")
        val saved = absStatisticsSession(raw)
        assertFalse(saved.has("deviceInfo"))
        assertFalse(saved.has("chapters"))
        assertFalse(saved.getJSONObject("mediaMetadata").has("description"))
        val original = absLibraryStatistics(org.json.JSONArray().put(raw), "mine", 2026)
        assertEquals(original, absLibraryStatistics(org.json.JSONArray().put(saved), "mine", 2026))
    }
    @Test fun parsesNativeStatisticsWithoutInventingMissingTime() {
        val data = parseAbsStatistics(JSONObject("""{"totalTime":7200.25,"today":60,"days":{"2026-01-01":3600,"2026-01-02":-1,"bad-date":2},
            "items":{"book":{"timeListening":7200,"mediaMetadata":{"title":"Example book","authorName":"Example author"}}},
            "recentSessions":[{"id":"s","libraryItemId":"show","episodeId":"episode","displayTitle":"Episode","timeListening":4.5,"startedAt":1767225600000}]}"""))
        assertEquals(7200.25, data.seconds!!, 0.001)
        assertEquals(mapOf(LocalDate.of(2026, 1, 1) to 3600.0), data.days)
        assertEquals("Example author", data.items.single().author)
        assertEquals("episode", data.recent.single().episodeId)
        val partial = parseAbsStatistics(JSONObject("""{"items":{},"totalTime":null}"""))
        assertNull(partial.seconds)
        assertFalse(partial.daysAvailable)
        assertEquals("Not available", absListeningTime(partial.seconds))
        assertEquals("0 min", absListeningTime(0.0))
    }
    @Test fun annualSummaryKeepsBooksAndPodcastsSeparateAndSkipsIncompleteRanks() {
        val summary = parseAbsYear(JSONObject("""{"numBooksFinished":3,"totalBookListeningTime":3600,
            "totalPodcastListeningTime":600,"topAuthors":[{"name":"Author","time":3600},{"name":"Missing time"}],
            "topGenres":[{"genre":"Fiction","time":3600}],"mostListenedNarrator":{"name":"Narrator","time":1200}}"""), 2026)
        assertEquals(3L, summary.booksFinished)
        assertEquals(3600.0, summary.bookSeconds!!, 0.0)
        assertEquals(600.0, summary.podcastSeconds!!, 0.0)
        assertEquals(listOf("Author" to 3600.0), summary.authors)
        assertEquals("Fiction", summary.genres.single().first)
        assertNull(parseAbsYear(JSONObject("""{"totalListeningTime":0}"""), 2025).booksFinished)
    }
    @Test fun historyPaginationAndInvalidRowsAreHandled() {
        val page = parseAbsHistory(JSONObject("""{"page":0,"itemsPerPage":30,"total":31,"sessions":[
            {"id":"one","libraryItemId":"book","timeListening":60}, {"id":"broken","timeListening":-1}]}"""))
        assertEquals(1, page.sessions.size)
        assertTrue(page.hasMore)
        assertFalse(parseAbsHistory(JSONObject("""{"page":1,"itemsPerPage":30,"total":31,"sessions":[]}""")).hasMore)
    }
    @Test fun patternsUseOnlyRecordedDaysAndLabelAverageAsActiveDayAverage() {
        val start = LocalDate.of(2026, 1, 1)
        val patterns = absListeningPatterns(mapOf(start to 60.0, start.plusDays(1) to 120.0, start.plusDays(3) to 0.0, start.plusDays(5) to 60.0))
        assertEquals(3, patterns.activeDays)
        assertEquals(2, patterns.longestStreak)
        assertEquals(80.0, patterns.averageActiveDaySeconds!!, 0.0)
        assertEquals(0, absListeningPatterns(emptyMap()).longestStreak)
    }
    @Test fun cachesNeverShareServerOrAccountIdentities() {
        assertNotEquals(absAccountKey("server-a", "token-a"), absAccountKey("server-b", "token-a"))
        assertNotEquals(absAccountKey("server-a", "token-a"), absAccountKey("server-a", "token-b"))
        assertFalse(absAccountKey("server-a", "private-token").contains("private-token"))
    }
    @Test fun libraryAggregationSeparatesDaysAuthorsGenresAndYearsWithoutGuessingCompletions() {
        val rows = org.json.JSONArray("""[
            {"id":"a","libraryId":"mine","libraryItemId":"book","mediaType":"book","date":"2026-01-01","timeListening":60,
             "mediaMetadata":{"title":"Book","authors":[{"name":"Author"}],"genres":["Fiction"],"narrators":["Narrator"]}},
            {"id":"b","libraryId":"other","libraryItemId":"other-book","date":"2026-01-01","timeListening":9999},
            {"id":"c","libraryId":"mine","libraryItemId":"podcast","episodeId":"episode","mediaType":"podcast","date":"2025-01-01","timeListening":30}
        ]""")
        val (stats, year) = absLibraryStatistics(rows, "mine", 2026)
        assertEquals(90.0, stats.seconds!!, 0.0)
        assertEquals(2, stats.days.size)
        assertEquals(2, stats.recent.size)
        assertEquals("Author", stats.items.first().author)
        assertEquals(60.0, year.bookSeconds!!, 0.0)
        assertEquals(0.0, year.podcastSeconds!!, 0.0)
        assertEquals(listOf("Fiction" to 60.0), year.genres)
        assertNull(year.booksFinished)
        assertNull(stats.todaySeconds)
    }
}
