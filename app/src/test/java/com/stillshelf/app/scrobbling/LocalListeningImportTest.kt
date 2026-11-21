package com.stillshelf.app.scrobbling

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class LocalListeningImportTest {
    private lateinit var db: LocalListeningDatabase
    private lateinit var history: LocalListeningRepository
    private val files = mutableListOf<File>()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalListeningDatabase::class.java).allowMainThreadQueries().build()
        history = LocalListeningRepository(db, db.listens())
    }
    @After fun close() { db.close(); files.forEach { it.delete() } }
    private fun file(bytes: ByteArray) = File.createTempFile("import-test-", ".zip", ApplicationProvider.getApplicationContext<Context>().cacheDir)
        .also { files += it; it.writeBytes(bytes) }
    private fun row(id: String = "event", milliseconds: Long = 35_000) = LocalListen(id, localIdentity("library"), "track", "artist-id", "album-id",
        "Song", "Artist", "Album", null, 60, 1_700_000_000, milliseconds, "artist", "album", "track", 35_000,
        genresJson = "[\"Rock\",\"Pop\"]", releaseYear = 1999)
    private suspend fun backup(): File = file(ByteArrayOutputStream().also { history.export(it) }.toByteArray())
    private fun archive(lines: String, count: Long = 1, version: Int = 1): File {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("stillshelf-history.jsonl")); zip.write(lines.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(JSONObject().put("format", "stillshelf-local-listening-history")
                .put("version", version).put("timestamp_unit", "unix_seconds").put("listening_duration_unit", "milliseconds")
                .put("listen_count", count).toString().toByteArray()); zip.closeEntry()
        }
        return file(bytes.toByteArray())
    }
    @Test fun roundTripRestoresMetadataDurationAndChartsAndRepeatedImportDoesNotDuplicate() = runTest {
        history.record(row(), 0)
        val backup = backup()
        history.delete()
        assertEquals(LocalHistoryImportResult(1, 0), history.importArchive(backup))
        val restored = db.listens().recent().single()
        assertEquals("event", restored.id)
        assertEquals(35_000, restored.listenedMs)
        assertEquals(row().startedAtSeconds, restored.startedAtSeconds)
        assertEquals(row().libraryKey, restored.libraryKey)
        assertEquals(row().albumId, restored.albumId)
        assertEquals(row().genresJson, restored.genresJson)
        assertEquals(1999, restored.releaseYear)
        assertTrue(restored.ended) // A restored backup never resumes an old playback session.
        assertEquals(LocalHistoryImportResult(0, 1), history.importArchive(backup))
        assertEquals(1L, history.statistics("library").totalScrobbles)
        val insights = LocalListeningInsights(db, db.listens()).load(InsightPeriod.ALL)
        assertEquals(2, insights.results.getValue(InsightKind.GENRES).data!!.rows.size)
        assertEquals(1999, insights.results.getValue(InsightKind.ERAS).data!!.rows.single().index)
    }
    @Test fun mergeKeepsNewerExistingListenAndUnrelatedHistory() = runTest {
        history.record(row(), 0)
        val backup = backup()
        history.record(row(milliseconds = 55_000), 0)
        history.record(row("other"), 0)
        assertEquals(LocalHistoryImportResult(0, 1), history.importArchive(backup))
        assertEquals(2L, history.summary().count)
        assertEquals(90_000L, history.summary().milliseconds)
    }
    @Test fun malformedRecordRollsBackAllEarlierRowsAndPreservesExistingData() = runTest {
        history.record(row("existing"), 0)
        val broken = archive(LocalListeningExport.native(row()).toString() + "\n{broken}\n", 2)
        try { history.importArchive(broken); fail("Should reject the backup") } catch (_: LocalHistoryImportException) { }
        assertEquals(listOf("existing"), db.listens().recent().map { it.id })
    }
    @Test fun wrongVersionCountNumbersAndOversizedLinesAreRejectedAtomically() = runTest {
        val json = LocalListeningExport.native(row()).toString()
        val bad = listOf(archive(json, version = 2), archive(json, count = 2), archive("x".repeat(65_537)),
            archive(LocalListeningExport.native(row()).put("listened_ms", 1.5).toString()))
        for (backup in bad) {
            try { history.importArchive(backup); fail("Should reject invalid data") } catch (_: LocalHistoryImportException) { }
            assertEquals(0L, history.summary().count)
        }
    }
    @Test fun olderBackupsWithoutTagsAndEmptyBackupsRemainSupported() = runTest {
        val old = LocalListeningExport.native(row()).apply { remove("genres"); remove("release_year"); remove("position_ms"); remove("ended") }
        assertEquals(LocalHistoryImportResult(1, 0), history.importArchive(archive(old.toString())))
        assertEquals("[]", db.listens().recent().single().genresJson)
        assertNull(db.listens().recent().single().releaseYear)
        assertEquals(LocalHistoryImportResult(0, 0), history.importArchive(archive("", count = 0)))
        assertEquals(1L, history.summary().count)
    }
    @Test fun duplicateEventsInsideOneBackupAreOnlyRestoredOnce() = runTest {
        val line = LocalListeningExport.native(row()).toString()
        assertEquals(LocalHistoryImportResult(1, 1), history.importArchive(archive("$line\n$line", count = 2)))
        assertEquals(1L, history.summary().count)
    }
    @Test fun unrelatedFilesAndExcessiveZipMembersAreRejectedWithoutChangingHistory() = runTest {
        history.record(row("existing"), 0)
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            repeat(17) { i -> zip.putNextEntry(ZipEntry("entry-$i")); zip.closeEntry() }
        }
        for (backup in listOf(file("{}".toByteArray()), file(bytes.toByteArray()))) {
            try { history.importArchive(backup); fail("Should reject unsupported backup") } catch (_: LocalHistoryImportException) { }
            assertEquals(listOf("existing"), db.listens().recent().map { it.id })
        }
    }
}
