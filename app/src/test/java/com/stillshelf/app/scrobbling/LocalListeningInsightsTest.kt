package com.stillshelf.app.scrobbling

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalListeningInsightsTest {
    private lateinit var db: LocalListeningDatabase
    private lateinit var history: LocalListeningRepository
    private lateinit var insights: LocalListeningInsights
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalListeningDatabase::class.java).allowMainThreadQueries().build()
        history = LocalListeningRepository(db, db.listens())
        insights = LocalListeningInsights(db, db.listens())
    }
    @After fun close() { db.close() }
    private suspend fun listen(id: String, day: LocalDate, artist: String = "Artist", genres: String = "[]", year: Int? = null) {
        history.record(LocalListen(id, "library", "track-$id", null, null, "Song $id", artist, "Album", null, 60,
            day.atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond(), 35_000, artist, "$artist|Album", "$artist|$id",
            genresJson = genres, releaseYear = year), history.epoch.value)
    }
    @Test fun localRecordsPopulateSharedChartsWithExactPeriodCountsAndMetadataCoverage() = runTest {
        listen("old", LocalDate.of(2026, 10, 1))
        listen("one", LocalDate.of(2026, 11, 19), genres = "[\"Pop\",\"Rock\"]", year = 1999)
        listen("two", LocalDate.of(2026, 11, 20), genres = "[\"Pop\"]", year = 2001)
        listen("three", LocalDate.of(2026, 11, 20), artist = "New Artist", genres = "[\"Rock\"]", year = 1999)
        val data = insights.load(InsightPeriod.THIS_MONTH, LocalDate.of(2026, 11, 20))
        assertEquals(3L, data.results.getValue(InsightKind.DAILY).data!!.rows.sumOf { it.count })
        assertEquals(168, data.results.getValue(InsightKind.DAILY).data!!.rows.size)
        assertEquals(listOf(2L, 1L), data.artists.map { it.count })
        assertEquals(3L, data.results.getValue(InsightKind.TRACKS).data!!.rows.sumOf { it.count })
        assertEquals(mapOf("Pop" to 2L, "Rock" to 2L), data.results.getValue(InsightKind.GENRES).data!!.rows.associate { it.label to it.count })
        assertEquals(mapOf(1999 to 2L, 2001 to 1L), data.results.getValue(InsightKind.ERAS).data!!.rows.associate { it.index to it.count })
        assertEquals(1L, data.discovery.sumOf { it.count }) // Existing Artist's first recorded listen was in October.
        assertTrue(data.coverage.contains("3 of 3"))
        val evolution = data.results.getValue(InsightKind.EVOLUTION).data!!
        assertEquals(3L, evolution.rows.sumOf { it.count })
        assertTrue(evolution.rows.any { it.count == 0L })
        assertTrue(evolution.meaningful())
    }
    @Test fun missingTagsAreNotInventedAndPastPeriodBoundsAreExclusive() = runTest {
        listen("october", LocalDate.of(2026, 10, 31))
        listen("november", LocalDate.of(2026, 11, 1))
        val data = insights.load(InsightPeriod.LAST_MONTH, LocalDate.of(2026, 11, 20))
        assertEquals(1L, data.results.getValue(InsightKind.DAILY).data!!.rows.sumOf { it.count })
        assertFalse(InsightKind.GENRES in data.results)
        assertFalse(InsightKind.ERAS in data.results)
        assertFalse(InsightKind.ORIGINS in data.results)
        assertTrue(data.coverage.contains("0 of 1"))
        history.delete()
        assertTrue(insights.load(InsightPeriod.ALL).results.isEmpty())
    }
    @Test fun allTimeArtistEvolutionUsesOrderedMonthlyBucketsAcrossYears() = runTest {
        listen("one", LocalDate.of(2025, 12, 15))
        listen("two", LocalDate.of(2026, 1, 15))
        val data = insights.load(InsightPeriod.ALL, LocalDate.of(2026, 1, 20)).results.getValue(InsightKind.EVOLUTION).data!!
        assertEquals(listOf(2025 * 12 + 12, 2026 * 12 + 1), data.rows.map { it.index })
        assertTrue(data.rows.first().bucket.contains("25"))
        assertTrue(data.rows.last().bucket.contains("26"))
    }

    @Test fun tiedArtistRankingsAndEvolutionUseTheExactSameTopTen() = runTest {
        val today = LocalDate.of(2026, 11, 20)
        repeat(11) { listen("tie-$it", today, artist = "Artist %02d".format(it)) }
        val data = insights.load(InsightPeriod.THIS_MONTH, today)
        assertEquals(10, data.artists.size)
        val totals = data.results.getValue(InsightKind.EVOLUTION).data!!.rows.groupBy { it.identity }
            .mapValues { (_, rows) -> rows.sumOf { it.count } }
        assertEquals(data.artists.associate { it.identity to it.count }, totals)
        assertTrue(totals.values.all { it == 1L })
    }
}
