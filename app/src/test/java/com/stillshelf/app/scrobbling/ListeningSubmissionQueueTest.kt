package com.stillshelf.app.scrobbling

import android.content.Context
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningSubmissionQueueTest {
    private val records = mutableMapOf<String, String>()
    private val secrets = mockk<SecureTokenStorage> {
        coEvery { getNamedSecret(any()) } answers { records[firstArg()] }
        coEvery { saveNamedSecret(any(), any(), true, false) } answers { records[firstArg()] = secondArg() }
    }
    private val store = ListeningSettingsStore(secrets)
    private val client = mockk<ListeningProviderClient> { coEvery { submit(any(), any()) } returns Unit }
    private val clients = mockk<ListeningClients> { every { forProvider(any()) } returns client }
    private val repository = mockk<NavidromeRepository> { coEvery { currentPlaybackSessionKey() } returns "session" }
    private val navidrome = mockk<NavidromeListeningClient>()
    private val queue = ListeningSubmissionQueue(mockk<Context>(), secrets, store, clients, repository, navidrome)
    private val connection = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", token = "token", revision = "owner")

    private fun pending(session: String = "session", owner: String = "owner") {
        records["listening_pending_v1"] = JSONArray().put(JSONObject()
            .put("session", session).put("owner", owner).put("id", "track").put("title", "Song")
            .put("artist", "Artist").put("album", "Album").put("duration", 180)
            .put("time", System.currentTimeMillis() / 1000)).toString()
    }

    @Test fun selectingNavidromePreventsQueuedExternalSubmission() = runTest {
        store.update { it.copy(connection = connection, method = ScrobblingMethod.NAVIDROME, scrobblingEnabled = true) }
        pending()
        assertFalse(queue.drain())
        coVerify(exactly = 0) { client.submit(any(), any()) }
        assertEquals(0, JSONArray(records.getValue("listening_pending_v1")).length())
    }

    @Test fun disablingScrobblingPreventsQueuedSubmissionWhileKeepingStatisticsConnection() = runTest {
        store.update { it.copy(connection = connection, method = ScrobblingMethod.STILL_SHELF,
            scrobblingEnabled = false, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL) }
        pending()
        assertFalse(queue.drain())
        coVerify(exactly = 0) { client.submit(any(), any()) }
        assertEquals("token", store.state.value.connection?.token)
    }

    @Test fun nativeReportsContinueWithDirectOffAndWithDirectOn() = runTest {
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        coEvery { navidrome.report(any(), any(), true) } returns Unit
        for (enabled in listOf(false, true)) {
            store.update { it.copy(connection = connection, sendingConnectionId = connection.revision,
                scrobblingEnabled = enabled, method = ScrobblingMethod.STILL_SHELF) }
            pending(owner = "navidrome")
            assertFalse(queue.drain())
        }
        coVerify(exactly = 2) { navidrome.report(any(), any(), true) }
    }

    @Test fun externalAuthFailureDoesNotBlockNativePlaybackReporting() = runTest {
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        coEvery { navidrome.report(any(), any(), true) } returns Unit
        coEvery { client.submit(any(), any()) } throws ListeningApiException(401, false, "Invalid token")
        store.update { it.copy(connection = connection, sendingConnectionId = connection.revision, scrobblingEnabled = true) }
        pending()
        val external = JSONArray(records.getValue("listening_pending_v1")).getJSONObject(0)
        pending(owner = "navidrome")
        val native = JSONArray(records.getValue("listening_pending_v1")).getJSONObject(0)
        records["listening_pending_v1"] = JSONArray().put(external).put(native).toString()
        assertFalse(queue.drain())
        coVerify(exactly = 1) { navidrome.report(any(), any(), true) }
        assertEquals("owner", JSONArray(records.getValue("listening_pending_v1")).getJSONObject(0).getString("owner"))
    }

    @Test fun statisticsSourceSwitchDoesNotRedirectQueuedDirectSubmission() = runTest {
        val stats = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "other", token = "other-token")
        store.update { it.copy(connection = stats, connections = listOf(connection, stats), multipleConnections = true,
            sendingConnectionId = connection.revision, scrobblingEnabled = true, statisticsSource = StatisticsSource.EXTERNAL) }
        pending()
        assertFalse(queue.drain())
        coVerify(exactly = 1) { client.submit(connection, any()) }
        coVerify(exactly = 0) { client.submit(stats, any()) }
    }

    @Test fun accountAndConnectionChangesCannotReplayToAnotherAccount() = runTest {
        store.update { it.copy(connection = connection, sendingConnectionId = connection.revision, method = ScrobblingMethod.STILL_SHELF, scrobblingEnabled = true) }
        pending(session = "other-session")
        queue.drain()
        pending(owner = "old-owner")
        queue.drain()
        coVerify(exactly = 0) { client.submit(any(), any()) }
    }

    @Test fun successfulSubmissionIsRemovedAndNotRepeatedOnNextDrain() = runTest {
        store.update { it.copy(connection = connection, sendingConnectionId = connection.revision, method = ScrobblingMethod.STILL_SHELF, scrobblingEnabled = true) }
        pending()
        assertFalse(queue.drain())
        assertFalse(queue.drain())
        coVerify(exactly = 1) { client.submit(connection, any()) }
        assertEquals(0, JSONArray(records.getValue("listening_pending_v1")).length())
    }

    @Test fun transientFailureRetainsOriginalListenAndPermanentAuthFailureStopsRetrying() = runTest {
        store.update { it.copy(connection = connection, sendingConnectionId = connection.revision, method = ScrobblingMethod.STILL_SHELF, scrobblingEnabled = true) }
        pending()
        coEvery { client.submit(any(), any()) } throws ListeningApiException(503, true, "Temporary failure")
        assertTrue(queue.drain())
        assertEquals(1, JSONArray(records.getValue("listening_pending_v1")).length())
        coEvery { client.submit(any(), any()) } throws ListeningApiException(401, false, "Authorize again")
        assertFalse(queue.drain())
        assertEquals("Authorize again", queue.status.value)
        assertEquals(1, JSONArray(records.getValue("listening_pending_v1")).length())
    }
}
