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
class ServerDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ServerDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.serverDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_and_getById_returnsInsertedServer() = runTest {
        val server = ServerEntity(id = "s1", name = "Home Server", baseUrl = "http://home", createdAt = 1000L)
        dao.insert(server)
        assertEquals(server, dao.getById("s1"))
    }

    @Test
    fun getById_nonExistentId_returnsNull() = runTest {
        assertNull(dao.getById("does-not-exist"))
    }

    @Test
    fun insert_replacesExistingServerOnConflict() = runTest {
        dao.insert(ServerEntity(id = "s1", name = "Old Name", baseUrl = "http://old", createdAt = 1000L))
        dao.insert(ServerEntity(id = "s1", name = "New Name", baseUrl = "http://new", createdAt = 2000L))
        val result = dao.getById("s1")
        assertEquals("New Name", result?.name)
        assertEquals("http://new", result?.baseUrl)
    }

    @Test
    fun getAll_returnsServersNewestFirst() = runTest {
        dao.insert(ServerEntity(id = "s1", name = "Old", baseUrl = "http://old", createdAt = 1000L))
        dao.insert(ServerEntity(id = "s2", name = "New", baseUrl = "http://new", createdAt = 2000L))
        val result = dao.getAll()
        assertEquals(2, result.size)
        assertEquals("s2", result[0].id)
        assertEquals("s1", result[1].id)
    }

    @Test
    fun getAll_withNoServers_returnsEmptyList() = runTest {
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun update_modifiesNameAndUrl() = runTest {
        dao.insert(ServerEntity(id = "s1", name = "Old", baseUrl = "http://old", createdAt = 1000L))
        dao.update("s1", "Updated", "http://updated")
        val result = dao.getById("s1")
        assertEquals("Updated", result?.name)
        assertEquals("http://updated", result?.baseUrl)
    }

    @Test
    fun update_returnsAffectedRowCount() = runTest {
        dao.insert(ServerEntity(id = "s1", name = "Server", baseUrl = "http://example", createdAt = 1000L))
        val rowsAffected = dao.update("s1", "New Name", "http://new")
        assertEquals(1, rowsAffected)
    }

    @Test
    fun update_nonExistentId_returnsZero() = runTest {
        val rowsAffected = dao.update("ghost", "Name", "http://url")
        assertEquals(0, rowsAffected)
    }

    @Test
    fun deleteById_removesServer() = runTest {
        dao.insert(ServerEntity(id = "s1", name = "Server", baseUrl = "http://example", createdAt = 1000L))
        dao.deleteById("s1")
        assertNull(dao.getById("s1"))
    }

    @Test
    fun deleteAll_removesAllServers() = runTest {
        dao.insert(ServerEntity("s1", "A", "http://a", 1000L))
        dao.insert(ServerEntity("s2", "B", "http://b", 2000L))
        dao.deleteAll()
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun observeServers_emitsCurrentServers() = runTest {
        val server = ServerEntity(id = "s1", name = "Server", baseUrl = "http://example", createdAt = 1000L)
        dao.insert(server)
        val result = dao.observeServers().first()
        assertEquals(1, result.size)
        assertEquals("s1", result[0].id)
    }

    @Test
    fun observeServers_emptyTable_emitsEmptyList() = runTest {
        val result = dao.observeServers().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun observeServers_ordersNewestFirst() = runTest {
        dao.insert(ServerEntity("s1", "Old", "http://old", 1000L))
        dao.insert(ServerEntity("s2", "New", "http://new", 2000L))
        val result = dao.observeServers().first()
        assertEquals("s2", result[0].id)
        assertEquals("s1", result[1].id)
    }
}
