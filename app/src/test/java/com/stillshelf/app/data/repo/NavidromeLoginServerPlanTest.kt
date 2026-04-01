package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.NavidromeServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeLoginServerPlanTest {

    @Test
    fun planNavidromeLoginServer_rejectsTrueDuplicateWhenSavedPasswordExists() {
        val existing = NavidromeServer(
            id = "server-1",
            name = "Existing",
            baseUrl = "https://music.example.com",
            username = "alice",
            createdAt = 1L
        )

        val plan = planNavidromeLoginServer(
            existingServers = listOf(existing),
            normalizedServerName = "Existing",
            normalizedBaseUrl = "https://music.example.com",
            normalizedUsername = "alice",
            serverIdsWithSavedPassword = setOf(existing.id),
            nowMs = 10L,
            newServerId = "server-2"
        )

        assertTrue(plan is NavidromeLoginServerPlan.RejectDuplicate)
    }

    @Test
    fun planNavidromeLoginServer_reusesSessionlessDuplicateInsteadOfRejecting() {
        val existing = NavidromeServer(
            id = "server-1",
            name = "Old Name",
            baseUrl = "https://music.example.com",
            username = "old-user",
            createdAt = 1L
        )

        val plan = planNavidromeLoginServer(
            existingServers = listOf(existing),
            normalizedServerName = "New Name",
            normalizedBaseUrl = "https://music.example.com",
            normalizedUsername = "new-user",
            serverIdsWithSavedPassword = emptySet(),
            nowMs = 10L,
            newServerId = "server-2"
        )

        val reusePlan = plan as NavidromeLoginServerPlan.ReuseExisting
        assertEquals(existing.id, reusePlan.server.id)
        assertEquals("New Name", reusePlan.server.name)
        assertEquals("new-user", reusePlan.server.username)
        assertEquals("https://music.example.com", reusePlan.server.baseUrl)
    }

    @Test
    fun planNavidromeLoginServer_createsNewServerWhenBaseUrlIsNew() {
        val existing = NavidromeServer(
            id = "server-1",
            name = "Existing",
            baseUrl = "https://music.example.com",
            username = "alice",
            createdAt = 1L
        )

        val plan = planNavidromeLoginServer(
            existingServers = listOf(existing),
            normalizedServerName = "New Server",
            normalizedBaseUrl = "https://other.example.com",
            normalizedUsername = "bob",
            serverIdsWithSavedPassword = setOf(existing.id),
            nowMs = 10L,
            newServerId = "server-2"
        )

        val createPlan = plan as NavidromeLoginServerPlan.Create
        assertEquals("server-2", createPlan.server.id)
        assertEquals("New Server", createPlan.server.name)
        assertEquals("https://other.example.com", createPlan.server.baseUrl)
        assertEquals("bob", createPlan.server.username)
        assertEquals(10L, createPlan.server.createdAt)
    }
}
