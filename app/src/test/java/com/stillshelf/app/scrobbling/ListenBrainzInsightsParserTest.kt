package com.stillshelf.app.scrobbling

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListenBrainzInsightsParserTest {
    @Test fun hourlyGridPreservesExplicitZerosButDoesNotInventMissingHours() {
        val data = parseInsight(InsightKind.DAILY, JSONObject("""{"payload":{"range":"week","from_ts":1,"to_ts":2,"last_updated":3,
          "daily_activity":{"Monday":[{"hour":0,"listen_count":0},{"hour":8,"listen_count":4},{"hour":25,"listen_count":100}],
          "Tuesday":[{"hour":7,"listen_count":-1}]}}}"""), InsightPeriod.LAST_WEEK)
        assertEquals(listOf(0L, 4L), data.rows.map { it.count })
        assertEquals(listOf(0, 8), data.rows.map { it.index })
        assertEquals(3L, data.updated)
        assertTrue(data.meaningful())
        assertFalse(data.rows.any { it.label == "Sunday" })
    }

    @Test fun evolutionOrdersCalendarBucketsAndPreservesIdentityAndMissingPeriods() {
        val data = parseInsight(InsightKind.EVOLUTION, JSONObject("""{"payload":{"range":"week","artist_evolution_activity":[
          {"artist_name":"فنان","artist_mbid":"one","time_unit":"Sunday","listen_count":3},
          {"artist_name":"فنان","artist_mbid":"one","time_unit":"Monday","listen_count":2},
          {"artist_name":"Artist","artist_mbid":"two","time_unit":"Monday","listen_count":5},
          {"artist_name":"Ignored","time_unit":"Bad","listen_count":5}]}}"""), InsightPeriod.LAST_WEEK)
        assertEquals(3, data.rows.size)
        assertEquals(setOf(1, 7), data.rows.map { it.index }.toSet())
        assertTrue(data.meaningful())
        assertFalse(data.copy(rows = data.rows.filter { it.index == 1 }).meaningful())
        assertEquals(12, evolutionOrder(InsightPeriod.LAST_YEAR, "December"))
        assertEquals(31, evolutionOrder(InsightPeriod.LAST_MONTH, "31"))
        assertNull(evolutionOrder(InsightPeriod.ALL, "Monday"))
    }

    @Test fun artistActivityContainsRealAlbumCountsRatherThanInventedResiduals() {
        val data = parseInsight(InsightKind.ARTIST_ALBUMS, JSONObject("""{"payload":{"artist_activity":[
          {"name":"Artist","artist_mbid":"id","listen_count":20,"albums":[{"name":"Album A","listen_count":7},{"name":"Album B","listen_count":4}, {"name":"Unknown"}]}]}}"""), InsightPeriod.ALL)
        assertEquals(listOf(7L, 4L), data.rows.map { it.count })
        assertTrue(data.rows.all { it.detail == "Artist" && it.identity == "id" })
        assertEquals(11L, data.rows.sumOf { it.count }) // No synthetic "other" album accounting for the difference.
    }

    @Test fun genresAcceptActualAndLegacySchemasWithoutCalculatingFalsePercentages() {
        val json = JSONObject("""{"payload":{"genre_activity":[{"genre":"rock","hour":8,"listen_count":4},
          {"genre":"rock","hour":9,"listen_count":3},{"genre":"folk","hour":8,"listen_count":2},{"genre":"","listen_count":1}]}}""")
        val data = parseInsight(InsightKind.GENRES, json, InsightPeriod.ALL)
        assertEquals(listOf(7L, 2L), data.rows.map { it.count })
        assertEquals(listOf("rock", "folk"), data.rows.map { it.label })
        val legacy = JSONObject().put("result", json.getJSONObject("payload").getJSONArray("genre_activity"))
        assertEquals(data.rows, parseInsight(InsightKind.GENRES, legacy, InsightPeriod.ALL).rows)
    }

    @Test fun erasUseReleaseYearsAndOriginsUseArtistCountsNotListeningCounts() {
        val eras = parseInsight(InsightKind.ERAS, JSONObject("""{"payload":{"era_activity":[
          {"year":1997,"listen_count":9},{"year":1997,"listen_count":1},{"year":2024,"listen_count":3},{"year":null,"listen_count":7},{"year":0,"listen_count":4}]}}"""), InsightPeriod.ALL)
        assertEquals(listOf(1997, 2024), eras.rows.map { it.index })
        assertEquals(listOf(10L, 3L), eras.rows.map { it.count })
        val origins = parseInsight(InsightKind.ORIGINS, JSONObject("""{"payload":{"artist_map":[
          {"country":"USA","artist_count":3,"listen_count":200},{"country":"GBR","artist_count":1}, {"country":"unknown","artist_count":5}]}}"""), InsightPeriod.ALL)
        assertEquals(listOf(3L, 1L), origins.rows.map { it.count })
    }

    @Test fun emptyInvalidAndWrongRangeResponsesDoNotProduceMadeUpCharts() {
        assertFalse(parseInsight(InsightKind.ALBUMS, JSONObject("""{"payload":{"releases":[]}}"""), InsightPeriod.THIS_MONTH).meaningful())
        assertFalse(parseInsight(InsightKind.TRACKS, JSONObject("""{"payload":{"recordings":[{"track_name":"Missing count"}]}}"""), InsightPeriod.THIS_MONTH).meaningful())
        try { parseInsight(InsightKind.GENRES, JSONObject("""{"payload":{"range":"all_time","genre_activity":[]}}"""), InsightPeriod.THIS_MONTH); fail("Wrong range") }
        catch (_: IllegalArgumentException) { }
        try { parseInsight(InsightKind.GENRES, JSONObject("""{"payload":{}}"""), InsightPeriod.ALL); fail("Missing schema") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun normalizedCacheRoundTripRetainsMetadataAndDoesNotContainCredentials() {
        val data = InsightDataset(InsightKind.ALBUMS, listOf(InsightRow("Album", 7, "Artist")), 1, 2, 3)
        assertEquals(data, insightFromJson(insightJson(data)))
        assertFalse(insightJson(data).toString().contains("token"))
    }

    @Test fun relativePeriodsGetNewCacheIdentitiesAtCalendarBoundaries() {
        assertEquals(LocalDate.of(2026, 11, 9), InsightPeriod.LAST_WEEK.anchor(LocalDate.of(2026, 11, 19)))
        assertEquals(LocalDate.of(2026, 11, 16), InsightPeriod.LAST_WEEK.anchor(LocalDate.of(2026, 11, 23)))
        assertEquals(LocalDate.of(2025, 12, 1), InsightPeriod.LAST_MONTH.anchor(LocalDate.of(2026, 1, 3)))
        assertEquals(LocalDate.of(2025, 1, 1), InsightPeriod.LAST_YEAR.anchor(LocalDate.of(2026, 1, 3)))
        assertNull(InsightPeriod.ALL.anchor(LocalDate.of(2026, 1, 3)))
    }
}
