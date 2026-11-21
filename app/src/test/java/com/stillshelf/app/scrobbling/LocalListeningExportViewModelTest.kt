package com.stillshelf.app.scrobbling

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stillshelf.app.core.datastore.SecureTokenStorage
import io.mockk.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalListeningExportViewModelTest {
    private lateinit var db: LocalListeningDatabase
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LocalListeningDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun cleanup() { db.close(); Dispatchers.resetMain() }

    @Test fun selectedDocumentStreamReceivesAnActualZipAndBusyStateFinishes() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val history = LocalListeningRepository(db, db.listens())
        history.record(LocalListen("event", "opaque-library", "track", null, null, "Song", "Artist", "Album", null,
            60, 1_700_000_000, 35_000, "artist", "album", "track"), 0)
        val settings = mockk<ListeningSettingsStore>(relaxed = true)
        val vm = LocalListeningViewModel(settings, history, context)
        val destination = File.createTempFile("local-export-test", ".zip", context.cacheDir)
        try {
            vm.export(Uri.fromFile(destination))
            vm.busy.first { !it }
            ZipFile(destination).use { zip ->
                assertNotNull(zip.getEntry("manifest.json"))
                assertEquals(1, zip.getInputStream(zip.getEntry("listens.jsonl")).bufferedReader().readLines().size)
            }
            assertEquals("Local listening history exported.", vm.message.value)
            assertEquals(1L, history.statistics(null).totalScrobbles)
            history.delete()
            vm.importBackup(Uri.fromFile(destination))
            vm.busy.first { !it }
            assertEquals(1L, history.summary().count)
            assertEquals("Import complete: 1 listens added, 0 already saved.", vm.message.value)
            vm.importBackup(Uri.fromFile(destination))
            vm.busy.first { !it }
            assertEquals("Import complete: 0 listens added, 1 already saved.", vm.message.value)
            assertEquals(1L, history.summary().count)
            coVerify(exactly = 0) { settings.update(any()) }
        } finally { destination.delete() }
    }
}
