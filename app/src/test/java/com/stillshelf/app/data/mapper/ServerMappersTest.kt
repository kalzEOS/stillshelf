package com.stillshelf.app.data.mapper

import com.stillshelf.app.core.database.LibraryEntity
import com.stillshelf.app.core.database.ServerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerMappersTest {

    @Test
    fun serverEntity_toModel_mapsAllFields() {
        val entity = ServerEntity(
            id = "server-1",
            name = "My Server",
            baseUrl = "http://example.com",
            createdAt = 1_700_000_000L
        )
        val model = entity.toModel()
        assertEquals("server-1", model.id)
        assertEquals("My Server", model.name)
        assertEquals("http://example.com", model.baseUrl)
        assertEquals(1_700_000_000L, model.createdAt)
    }

    @Test
    fun serverEntity_toModel_preservesEmptyFields() {
        val entity = ServerEntity(id = "", name = "", baseUrl = "", createdAt = 0L)
        val model = entity.toModel()
        assertEquals("", model.id)
        assertEquals("", model.name)
        assertEquals("", model.baseUrl)
        assertEquals(0L, model.createdAt)
    }

    @Test
    fun serverEntity_toModel_preservesTrailingSlashInUrl() {
        val entity = ServerEntity(
            id = "s1",
            name = "Server",
            baseUrl = "http://example.com/audiobookshelf/",
            createdAt = 100L
        )
        assertEquals("http://example.com/audiobookshelf/", entity.toModel().baseUrl)
    }

    @Test
    fun libraryEntity_toModel_mapsAllFields() {
        val entity = LibraryEntity(id = "lib-1", serverId = "server-1", name = "My Library")
        val model = entity.toModel()
        assertEquals("lib-1", model.id)
        assertEquals("server-1", model.serverId)
        assertEquals("My Library", model.name)
    }

    @Test
    fun libraryEntity_toModel_preservesServerId() {
        val entity = LibraryEntity(id = "lib-abc", serverId = "srv-xyz", name = "Audiobooks")
        assertEquals("srv-xyz", entity.toModel().serverId)
    }

    @Test
    fun libraryEntity_toModel_preservesEmptyName() {
        val entity = LibraryEntity(id = "lib-1", serverId = "s1", name = "")
        assertEquals("", entity.toModel().name)
    }
}
