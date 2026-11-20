package com.stillshelf.app.scrobbling

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalListeningRepositoryTest {
    private lateinit var db: LocalListeningDatabase
    private lateinit var repo: LocalListeningRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalListeningDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = LocalListeningRepository(db, db.listens())
    }
    @After fun close() { db.close() }
    private fun row(id: String = "one", artist: String = "Artist", album: String = "Album", seconds: Long = 30) = LocalListen(
        id, localIdentity("library"), "track", "artist-id", "album-id", "Song", artist, album, null, 600,
        LocalDate.of(2026, 11, 19).atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond(), seconds * 1000,
        artist, "$artist|$album", "$artist|$album|Song")

    @Test fun duplicateWritesAreIdempotentAndDurationNeverMovesBackwards() = runTest {
        repo.record(row(seconds = 35), 0)
        repo.record(row(seconds = 30), 0)
        repo.record(row(seconds = 40), 0)
        val stats = repo.statistics("library")
        assertEquals(1L, stats.totalScrobbles)
        assertEquals(40L, stats.listeningSeconds)
        assertEquals("album-id", stats.topTracks.single().albumId)
        assertEquals("artist-id", stats.topArtists.single().artistId)
        assertNull(repo.statistics("other-library").topTracks.single().albumId)
        assertEquals("Song", stats.recent.single().title)
    }

    @Test fun deletionRejectsQueuedOldWritesAndDoesNotResetConfiguration() = runTest {
        repo.record(row(), 0)
        repo.delete()
        repo.record(row(id = "late"), 0)
        assertEquals(0L, repo.statistics(null).totalScrobbles)
        repo.record(row(id = "new"), repo.epoch.value)
        assertEquals(1L, repo.statistics(null).totalScrobbles)
    }

    @Test fun onlyAnUnfinishedListenAtItsRecordedPositionCanResume() = runTest {
        val listen = row().copy(positionMs = 35_000)
        repo.record(listen, 0)
        assertEquals(listen.id, repo.resumable("library", "track", 36_000)?.id)
        assertNull(repo.resumable("library", "track", 0))
        assertNull(repo.resumable("different-library", "track", 36_000))
        repo.record(listen.copy(ended = true), 0)
        repo.record(listen, 0) // A late earlier checkpoint cannot reopen a finished event.
        assertNull(repo.resumable("library", "track", 36_000))
    }

    @Test fun rankingsKeepSameAlbumNamesByDifferentArtistsSeparateAndPaginate() = runTest {
        repo.record(row(artist = "أم كلثوم"), 0)
        repo.record(row("two", artist = "Other Artist"), 0)
        repeat(103) { repo.record(row("extra-$it", artist = "Artist-$it"), 0) }
        val stats = repo.statistics(null)
        assertEquals(105L, stats.totalScrobbles)
        assertEquals(100, stats.topArtists.size)
        assertTrue(ListeningChart.ARTISTS in stats.moreCharts)
        val next = repo.chartPage(ListeningChart.ARTISTS, 100, null)
        assertEquals(5, next.entries.size)
        assertFalse(next.hasMore)
        assertEquals(105, (stats.topArtists + next.entries).map { it.title }.distinct().size)
    }

    @Test fun trendsUseRecordedDurationLocalDatesAndKnownZerosOnlySinceFirstRecord() = runTest {
        repo.record(row(seconds = 31), 0)
        repo.record(row("two", seconds = 29), 0)
        val trends = repo.trends(TrendRange.MONTH, LocalDate.of(2026, 11, 20))
        assertEquals(listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20)), trends.points.map { it.date })
        assertEquals(listOf(2L, 0L), trends.points.map { it.count })
        assertEquals(listOf(60L, 0L), trends.points.map { it.seconds })
        assertEquals(12, trends.hour)
        assertEquals(1, trends.streak)
        assertTrue(trends.complete)
        assertEquals(2, trends.heatmap.size)
    }

    @Test fun streakUsesAllRecordedDatesRegardlessOfChartRangeAndStopsAtGaps() = runTest {
        val today = LocalDate.of(2026, 11, 20)
        repeat(400) { offset ->
            val day = today.minusDays(offset.toLong())
            db.listens().insert(row("day-$offset").copy(startedAtSeconds = day.atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toEpochSecond()))
        }
        for (range in TrendRange.entries) assertEquals("Range $range", 400, repo.trends(range, today).streak)
        // No listen today is allowed; the streak can end yesterday.
        assertEquals(400, repo.trends(TrendRange.MONTH, today.plusDays(1)).streak)
        assertEquals(0, repo.trends(TrendRange.MONTH, today.plusDays(2)).streak)
        repo.delete()
        for (offset in listOf(0, 1, 3, 4)) db.listens().insert(row("gap-$offset").copy(
            startedAtSeconds = today.minusDays(offset.toLong()).atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond()))
        assertEquals(2, repo.trends(TrendRange.ALL, today).streak)
    }

    @Test fun localMidnightBoundariesAndDstDatesDoNotLoseOrDoubleCountRecords() = runTest {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.of(2026, 3, 8)
        val boundary = day.atStartOfDay(zone).toEpochSecond()
        val next = day.plusDays(1).atStartOfDay(zone).toEpochSecond()
        repo.record(row("before").copy(startedAtSeconds = boundary - 1), 0)
        repo.record(row("start").copy(startedAtSeconds = boundary), 0)
        repo.record(row("end").copy(startedAtSeconds = next - 1), 0)
        repo.record(row("next").copy(startedAtSeconds = next), 0)
        val points = repo.trends(TrendRange.WEEK, day.plusDays(1)).points
        assertEquals(listOf(day.minusDays(1), day, day.plusDays(1)), points.map { it.date })
        assertEquals(listOf(1L, 2L, 1L), points.map { it.count })
    }

    @Test fun zipContainsPortableNativeAndDocumentedListenBrainzJsonlWithoutInventedMetadata() = runTest {
        repo.record(row(seconds = 31), 0)
        val bytes = ByteArrayOutputStream().also { repo.export(it) }.toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break
            entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
        } }
        assertEquals(setOf("stillshelf-history.jsonl", "listens.jsonl", "manifest.json", "README.txt"), entries.keys)
        val native = JSONObject(entries.getValue("stillshelf-history.jsonl").trim())
        assertEquals(31_000L, native.getLong("listened_ms"))
        val listen = JSONObject(entries.getValue("listens.jsonl").trim())
        assertTrue(listen.get("listened_at") is Number)
        assertEquals(row().startedAtSeconds, listen.getLong("listened_at"))
        val metadata = listen.getJSONObject("track_metadata")
        assertEquals("Song", metadata.getString("track_name"))
        assertEquals(600_000L, metadata.getJSONObject("additional_info").getLong("duration_ms"))
        assertFalse(metadata.getJSONObject("additional_info").has("recording_mbid"))
        assertTrue(entries.getValue("README.txt").contains("not guaranteed"))
        assertFalse(entries.values.any { it.contains("https://server") || it.contains("Authorization") })
        assertEquals(1L, JSONObject(entries.getValue("manifest.json")).getLong("listen_count"))
    }

    @Test fun onDiskHistorySurvivesReopeningDatabase() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = java.io.File(context.noBackupFilesDir, "test-local-${java.util.UUID.randomUUID()}.db").absolutePath
        fun open() = Room.databaseBuilder(context, LocalListeningDatabase::class.java, path).build()
        val first = open()
        try { LocalListeningRepository(first, first.listens()).record(row(), 0) } finally { first.close() }
        val reopened = open()
        try { assertEquals(1L, LocalListeningRepository(reopened, reopened.listens()).statistics(null).totalScrobbles) }
        finally { reopened.close(); context.deleteDatabase(path) }
    }

    @Test fun metadataMigrationRetainsVersionTwoHistoryWithoutInventingTags() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = java.io.File(context.noBackupFilesDir, "test-migration-${java.util.UUID.randomUUID()}.db").absolutePath
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE local_listens (id TEXT NOT NULL PRIMARY KEY, libraryKey TEXT NOT NULL, trackId TEXT NOT NULL, " +
                "artistId TEXT, albumId TEXT, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, recordingMbid TEXT, " +
                "durationSeconds INTEGER, startedAtSeconds INTEGER NOT NULL, listenedMs INTEGER NOT NULL, artistKey TEXT NOT NULL, " +
                "albumKey TEXT NOT NULL, trackKey TEXT NOT NULL, positionMs INTEGER, ended INTEGER NOT NULL DEFAULT 0)")
            listOf("startedAtSeconds", "artistKey", "albumKey", "trackKey").forEach { column ->
                old.execSQL("CREATE INDEX index_local_listens_$column ON local_listens ($column)")
            }
            old.execSQL("INSERT INTO local_listens (id,libraryKey,trackId,title,artist,album,startedAtSeconds,listenedMs,artistKey,albumKey,trackKey) " +
                "VALUES ('old','library','track','Song','Artist','Album',1700000000,35000,'Artist','Album','Song')")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(context, LocalListeningDatabase::class.java, path)
            .addMigrations(LocalListeningModule.migration2To3).build()
        try {
            val history = LocalListeningRepository(migrated, migrated.listens())
            assertEquals(1L, history.statistics(null).totalScrobbles)
            val row = migrated.listens().recent().single()
            assertEquals("[]", row.genresJson)
            assertNull(row.releaseYear)
            assertEquals(35_000L, row.listenedMs)
        } finally { migrated.close(); context.deleteDatabase(path) }
    }
}
