package com.stillshelf.app.scrobbling

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a separate physical-device database; never clears the user's real history. */
@RunWith(AndroidJUnit4::class)
class LocalListeningImportDeviceTest {
    @Test fun diskBackupRestoresIntoFreshDatabaseAndRepeatedImportPreservesNewerHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "history-roundtrip-${System.nanoTime()}").apply { mkdirs() }
        val backup = File(directory, "backup.zip")
        fun database(name: String) = Room.databaseBuilder(context, LocalListeningDatabase::class.java,
            File(directory, name).absolutePath).build()
        val original = database("original.db")
        val restored = database("restored.db")
        try {
            val source = LocalListeningRepository(original, original.listens())
            val target = LocalListeningRepository(restored, restored.listens())
            val listen = LocalListen("device-event", localIdentity("test-library"), "track", "artist", "album",
                "Test song ".repeat(500), "Test artist", "Test album", null, 120, 1_700_000_000, 75_000,
                "artist-key", "album-key", "track-key", 75_000, genresJson = "[\"Rock\"]", releaseYear = 2001)
            source.record(listen, 0)
            context.contentResolver.openOutputStream(android.net.Uri.fromFile(backup))!!.use { source.export(it) }
            assertEquals(0L, target.summary().count)
            assertEquals(LocalHistoryImportResult(1, 0), target.importArchive(backup))
            val row = restored.listens().recent().single()
            assertEquals(listen.id, row.id)
            assertEquals(listen.title, row.title)
            assertEquals(listen.startedAtSeconds, row.startedAtSeconds)
            assertEquals(listen.listenedMs, row.listenedMs)
            assertEquals(listen.genresJson, row.genresJson)
            assertEquals(listen.releaseYear, row.releaseYear)
            assertTrue(row.ended)
            assertTrue(row.imported)
            target.record(row.copy(listenedMs = 90_000), 0)
            assertEquals(LocalHistoryImportResult(0, 1), target.importArchive(backup))
            assertEquals(1L, target.summary().count)
            assertEquals(90_000L, target.summary().milliseconds)
            assertEquals(1L, target.statistics("test-library").totalScrobbles)
            assertFalse(backup.readBytes().isEmpty())
            target.delete() // Only this test's isolated database is cleared.
            val current = listen.copy(id = "current-event", startedAtSeconds = listen.startedAtSeconds + 1000)
            target.record(current, target.epoch.value)
            assertEquals(LocalHistoryImportResult(1, 0), target.importArchive(backup))
            assertEquals(current.id, target.resumable("test-library", "track", 75_000)?.id)
            target.record(current.copy(ended = true), target.epoch.value)
            assertNull(target.resumable("test-library", "track", 75_000))
            // Recording order remains authoritative after a backwards clock correction.
            val afterClockCorrection = current.copy(id = "after-clock-correction", startedAtSeconds = listen.startedAtSeconds)
            target.record(afterClockCorrection, target.epoch.value)
            assertEquals(afterClockCorrection.id, target.resumable("test-library", "track", 75_000)?.id)
        } finally {
            original.close()
            restored.close()
            directory.deleteRecursively()
        }
    }
}
