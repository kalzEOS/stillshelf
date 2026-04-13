package com.stillshelf.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LibraryDao

    // LibraryEntity has a FK on ServerEntity — insert a parent server before each test.
    private val server = ServerEntity(id = "srv1", name = "Server", baseUrl = "http://srv", createdAt = 1000L)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.libraryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertServer() {
        db.serverDao().insert(server)
    }

    @Test
    fun insert_and_getByServerAndId_returnsLibrary() = runTest {
        insertServer()
        val lib = LibraryEntity(id = "lib1", serverId = "srv1", name = "Audiobooks")
        dao.insert(lib)
        val result = dao.getByServerAndId("srv1", "lib1")
        assertEquals(lib, result)
    }

    @Test
    fun getByServerAndId_nonExistent_returnsNull() = runTest {
        insertServer()
        assertNull(dao.getByServerAndId("srv1", "no-such-lib"))
    }

    @Test
    fun insert_replacesOnConflict() = runTest {
        insertServer()
        dao.insert(LibraryEntity(id = "lib1", serverId = "srv1", name = "Old Name"))
        dao.insert(LibraryEntity(id = "lib1", serverId = "srv1", name = "New Name"))
        val result = dao.getByServerAndId("srv1", "lib1")
        assertEquals("New Name", result?.name)
    }

    @Test
    fun insertAll_insertsMultipleLibraries() = runTest {
        insertServer()
        val libs = listOf(
            LibraryEntity("lib1", "srv1", "Audiobooks"),
            LibraryEntity("lib2", "srv1", "Podcasts"),
            LibraryEntity("lib3", "srv1", "Music")
        )
        dao.insertAll(libs)
        assertEquals(3, dao.getLibraries("srv1").size)
    }

    @Test
    fun getLibraries_returnsInAlphabeticalOrder() = runTest {
        insertServer()
        dao.insertAll(
            listOf(
                LibraryEntity("l1", "srv1", "Zoe's Books"),
                LibraryEntity("l2", "srv1", "Audiobooks"),
                LibraryEntity("l3", "srv1", "Manga")
            )
        )
        val result = dao.getLibraries("srv1")
        assertEquals("Audiobooks", result[0].name)
        assertEquals("Manga", result[1].name)
        assertEquals("Zoe's Books", result[2].name)
    }

    @Test
    fun getLibraries_forUnknownServer_returnsEmptyList() = runTest {
        assertTrue(dao.getLibraries("unknown-server").isEmpty())
    }

    @Test
    fun observeLibraries_emitsCurrentLibraries() = runTest {
        insertServer()
        dao.insert(LibraryEntity("lib1", "srv1", "Audiobooks"))
        val result = dao.observeLibraries("srv1").first()
        assertEquals(1, result.size)
        assertEquals("lib1", result[0].id)
    }

    @Test
    fun observeLibraries_emptyTable_emitsEmptyList() = runTest {
        insertServer()
        assertTrue(dao.observeLibraries("srv1").first().isEmpty())
    }

    @Test
    fun deleteByServerId_removesAllLibrariesForServer() = runTest {
        insertServer()
        dao.insertAll(
            listOf(
                LibraryEntity("lib1", "srv1", "A"),
                LibraryEntity("lib2", "srv1", "B")
            )
        )
        dao.deleteByServerId("srv1")
        assertTrue(dao.getLibraries("srv1").isEmpty())
    }

    @Test
    fun deleteByServerId_doesNotAffectOtherServers() = runTest {
        val server2 = ServerEntity("srv2", "Server 2", "http://srv2", 2000L)
        db.serverDao().insert(server)
        db.serverDao().insert(server2)
        dao.insert(LibraryEntity("lib1", "srv1", "From Server 1"))
        dao.insert(LibraryEntity("lib2", "srv2", "From Server 2"))

        dao.deleteByServerId("srv1")

        assertTrue(dao.getLibraries("srv1").isEmpty())
        assertEquals(1, dao.getLibraries("srv2").size)
    }

    @Test
    fun serverDeletion_cascadesToLibraries() = runTest {
        insertServer()
        dao.insert(LibraryEntity("lib1", "srv1", "Audiobooks"))
        db.serverDao().deleteById("srv1")
        assertTrue(dao.getLibraries("srv1").isEmpty())
    }
}
