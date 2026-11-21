package com.stillshelf.app.scrobbling

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalListeningMigrationDeviceTest {
    @Test fun shippedDatabaseUpgradePreservesHistoryAndRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "history-migration-${System.nanoTime()}").apply { mkdirs() }
        val path = File(directory, "history.db").absolutePath
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE local_listens (id TEXT NOT NULL PRIMARY KEY, libraryKey TEXT NOT NULL, trackId TEXT NOT NULL, " +
                "artistId TEXT, albumId TEXT, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, recordingMbid TEXT, " +
                "durationSeconds INTEGER, startedAtSeconds INTEGER NOT NULL, listenedMs INTEGER NOT NULL, artistKey TEXT NOT NULL, " +
                "albumKey TEXT NOT NULL, trackKey TEXT NOT NULL, positionMs INTEGER, ended INTEGER NOT NULL DEFAULT 0, " +
                "genresJson TEXT NOT NULL DEFAULT '[]', releaseYear INTEGER)")
            listOf("startedAtSeconds", "artistKey", "albumKey", "trackKey").forEach { column ->
                old.execSQL("CREATE INDEX index_local_listens_$column ON local_listens ($column)")
            }
            old.execSQL("CREATE TABLE local_listen_genres (listenId TEXT NOT NULL, genre TEXT NOT NULL, PRIMARY KEY(listenId, genre), " +
                "FOREIGN KEY(listenId) REFERENCES local_listens(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("INSERT INTO local_listens (id,libraryKey,trackId,title,artist,album,startedAtSeconds,listenedMs," +
                "artistKey,albumKey,trackKey,positionMs,genresJson,releaseYear) VALUES " +
                "('live',?,'track','Song','Artist','Album',1700000000,35000,'artist','album','track',35000,'[\"Rock\"]',1999)",
                arrayOf(localIdentity("library")))
            old.execSQL("INSERT INTO local_listen_genres VALUES ('live','Rock')")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, LocalListeningDatabase::class.java, path)
            .addMigrations(LocalListeningModule.migration3To4).build()
        try {
            val history = LocalListeningRepository(db, db.listens())
            val row = db.listens().recent().single()
            assertFalse(row.imported)
            assertEquals("[\"Rock\"]", row.genresJson)
            assertEquals(1999, row.releaseYear)
            assertEquals(35_000L, row.listenedMs)
            assertEquals("live", history.resumable("library", "track", 35_000)?.id)
            assertEquals("Rock", LocalListeningInsights(db, db.listens()).load(InsightPeriod.ALL)
                .results.getValue(InsightKind.GENRES).data!!.rows.single().label)
        } finally { db.close(); directory.deleteRecursively() }
    }
}
