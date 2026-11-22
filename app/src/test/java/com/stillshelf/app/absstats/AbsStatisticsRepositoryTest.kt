package com.stillshelf.app.absstats

import android.app.Application
import com.stillshelf.app.core.database.*
import com.stillshelf.app.core.datastore.*
import com.stillshelf.app.core.model.*
import com.stillshelf.app.core.network.ActiveServerEndpointResolver
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AbsStatisticsRepositoryTest {
    private val prefs = MutableStateFlow(SessionPreferenceState("server", null))
    private var token = "test-token"
    private var userId = "verified-user"
    private var failed = false
    private var annualFailed = false
    private var failedPage: Int? = null
    private var historyPages = emptyMap<Int, String>()
    private var progressResponse = """{"mediaProgress":[]}"""
    private val requests = mutableListOf<Request>()
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        synchronized(requests) { requests.add(request) }
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("/me") -> """{"id":"$userId","username":"Reader"}"""
            path.endsWith("listening-stats") -> """{"totalTime":3600,"days":{"2026-01-01":3600},"items":{}}"""
            path.contains("/stats/year/") -> """{"numBooksFinished":2,"totalListeningTime":3600}"""
            path.endsWith("listening-sessions") -> historyPages[request.url.queryParameter("page")?.toInt()]
                ?: """{"page":0,"itemsPerPage":30,"total":0,"sessions":[]}"""
            path.endsWith("/progress") -> progressResponse
            path.endsWith("/items/show") -> """{"libraryId":"podcasts"}"""
            else -> "OK"
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (failed || annualFailed && path.contains("/stats/year/") ||
                failedPage != null && path.endsWith("listening-sessions") && request.url.queryParameter("page") == failedPage.toString()) 503 else 200).message("test")
            .body(body.toResponseBody()).build()
    }.build()
    private val preferences = mockk<SessionPreferences> { every { state } returns prefs }
    private val secrets = mockk<SecureTokenStorage> { coEvery { getToken("server") } answers { token } }
    private val server = ServerEntity("server", "Test", "https://example.invalid", 0)
    private val servers = mockk<ServerDao> { coEvery { getById("server") } returns server }
    private val endpoints = mockk<ActiveServerEndpointResolver> {
        coEvery { resolveForServer(server) } returns ActiveServerConnectionStatus("server", server.baseUrl,
            ServerConnectionRoute.Default, ServerConnectionMode.Auto, false)
    }
    private fun repository() = AbsStatisticsRepository(preferences, servers, secrets, endpoints, http, RuntimeEnvironment.getApplication())

    @Test fun queuedPlaybackRecordedWithRetentionOffIsNeverBackfilledAfterReenablingIt() = runTest {
        val repo = repository()
        val account = repo.account()!!
        repo.localToolsAccount(account.key)
        val session = AbsRecordedSession("server", account.key, java.util.UUID.randomUUID().toString(), "book",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "books", "Book", "Author", retainLocally = false)
        val queued = AbsRecordedSession.from(session.stored())
        assertEquals(false, queued.retainLocally)
        repo.localAccounts.setRecording(account.key, true)
        repo.retainLocal(queued)
        assertTrue(repo.localRecords(account.key).isEmpty())
        repo.submitSession(account, queued.payload())
        assertFalse(queued.payload().has("retainLocally"))
    }

    @Test fun reloginDefersRetentionUntilIdentityRecoversOptOutAndNewUsersKeepTheirOwnDefault() = runTest {
        val repo = repository()
        val original = repo.account()!!
        repo.localToolsAccount(original.key)
        repo.localAccounts.setRecording(original.key, false)
        token = "renewed-credentials"
        val renewed = repo.account()!!
        val session = AbsRecordedSession("server", renewed.key, java.util.UUID.randomUUID().toString(), "book",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "books", "Book", "Author")
        assertFalse(repo.localAccounts.recording(renewed.key))
        repo.retainLocal(session)
        assertTrue(repo.localRecords(renewed.key).isEmpty())
        failed = true
        repo.resolveRecordingAccount("server")
        assertFalse(repo.localAccounts.recording(renewed.key))
        failed = false
        repo.resolveRecordingAccount("server")
        assertFalse(repo.localAccounts.recording(renewed.key))
        repo.retainLocal(session)
        assertTrue(repo.localRecords(renewed.key).isEmpty())
        repo.submitSession(renewed, session.payload()) // Opt-out does not disable ordinary reporting.
        token = "new-users-credentials"
        userId = "genuinely-different-user"
        val other = repo.account()!!
        assertFalse(repo.localAccounts.recording(other.key))
        repo.resolveRecordingAccount("server")
        assertTrue(repo.localAccounts.recording(other.key))
        repo.retainLocal(session.copy(owner = other.key))
        assertEquals(1, repo.localRecords(other.key).size)
        assertTrue(repo.localRecords(renewed.key).isEmpty())
    }

    @Test fun verifiedAccountsStaySeparateEvenWithTheSameLibraryAndSharedLoginCanFilterEachLibrary() = runTest {
        val repo = repository()
        val first = repo.account()!!
        repo.localToolsAccount(first.key)
        assertEquals("Reader", repo.accountDisplayName(first.key))
        fun listen(owner: String, library: String, ms: Long) = AbsRecordedSession("server", owner,
            java.util.UUID.randomUUID().toString(), "show::episode", 1767225600000, 1767225620000,
            0, ms, ms, library, "Episode", "Host")
        repo.retainLocal(listen(first.key, "mine", 10_000))
        repo.retainLocal(listen(first.key, "family", 20_000))
        assertEquals(10.0, repo.refresh(first.key, 2026, true, "mine").statistics!!.seconds!!, 0.0)
        assertEquals(20.0, repo.refresh(first.key, 2026, false, "family").statistics!!.seconds!!, 0.0)
        assertEquals(30.0, repo.refresh(first.key, 2026, false).statistics!!.seconds!!, 0.0)
        token = "second-account-token"
        userId = "different-verified-user"
        val second = repo.account()!!
        repo.localToolsAccount(second.key)
        assertTrue(repo.localRecords(second.key).isEmpty())
        repo.retainLocal(listen(second.key, "family", 7_000))
        assertEquals(7.0, repo.refresh(second.key, 2026, true, "family").statistics!!.seconds!!, 0.0)
        assertEquals(0.0, repo.refresh(second.key, 2026, false, "mine").statistics!!.seconds!!, 0.0)
        assertEquals(20.0, repo.cached(first.key, 2026, "family").statistics!!.seconds!!, 0.0)
    }

    @Test fun uploadLockDoesNotBlockDurableCheckpointsOrAllowStaleAcknowledgementToEraseThem() = runTest {
        val repo = repository()
        val queue = AbsSessionQueue(RuntimeEnvironment.getApplication())
        val account = repo.account()!!
        val session = AbsRecordedSession("server", account.key, java.util.UUID.randomUUID().toString(), "book",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "books", "Book", "Author")
        queue.syncMutex.lock() // Simulate an upload stalled on the network.
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(5_000) { repo.checkpoint(queue, session) }
            }
            assertEquals(session, AbsSessionQueue(RuntimeEnvironment.getApplication()).pending().single())
            val newer = session.copy(updatedAt = session.updatedAt + 1000, listeningMs = 21_000)
            repo.checkpoint(queue, newer)
            repo.localJournal.acknowledge(session)
            queue.acknowledge(session)
            assertEquals(newer, queue.pending().single())
            assertEquals(21_000L, repo.localRecords(account.key).single().session.listeningMs)
        } finally { queue.syncMutex.unlock() }
    }

    @Test fun firstLocalRecordPreservesCachedNativeSummaryUntilCompleteHistoryCanMergeWithoutOverlap() = runTest {
        val repo = repository()
        val key = repo.account()!!.key
        val original = repo.refresh(key, 2026, true)
        assertEquals(3600.0, original.statistics!!.seconds!!, 0.0)
        val session = AbsRecordedSession("server", key, java.util.UUID.randomUUID().toString(), "book",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "books", "Book", "Author")
        repo.retainLocal(session)
        failed = true
        val offline = repository().refresh(key, 2026, true)
        assertEquals(original.statistics, offline.statistics)
        assertEquals(original.year, offline.year)
        assertEquals(original.updatedAt, offline.updatedAt)
        assertTrue(offline.partial && offline.cached && offline.localMergePending)
        assertFalse(offline.localOnly)
        assertEquals(0L, offline.localAdditionalMs) // Cannot blindly add overlapping local time to a summary.
        failed = false
        historyPages = mapOf(0 to """{"total":1,"page":0,"itemsPerPage":100,"sessions":[{"id":"${session.id}","libraryId":"books","libraryItemId":"book","timeListening":20,"startedAt":1767225600000,"date":"2026-01-01"}]}""")
        val merged = repo.refresh(key, 2026, true)
        assertFalse(merged.localMergePending)
        assertEquals(20.0, merged.statistics!!.seconds!!, 0.0) // Full native index replaces the summary coherently.
        assertEquals(0L, merged.localAdditionalMs)
    }

    @Test fun firstOfflineListenShowsLocalOnlyAndDisablingRetentionKeepsNativeSubmissionAvailable() = runTest {
        val repo = repository()
        val account = repo.account()!!
        val first = AbsRecordedSession("server", account.key, java.util.UUID.randomUUID().toString(), "book",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "books", "Book", "Author")
        repo.retainLocal(first)
        failed = true
        val offline = repo.refresh(account.key, 2026, true, "books")
        assertTrue(offline.localOnly && offline.partial)
        assertEquals(20.0, offline.statistics!!.seconds!!, 0.0)
        repo.localAccounts.setRecording(account.key, false)
        val next = first.copy(id = java.util.UUID.randomUUID().toString())
        repo.retainLocal(next)
        assertEquals(1, repo.localRecords(account.key).size)
        failed = false
        repo.submitSession(account, next.payload())
        assertTrue(requests.any { it.method == "POST" && it.url.encodedPath == "/api/session/local" })
    }

    @Test fun streamedLocalTimeSurvivesOutageAndMergesCumulativeServerTimeAcrossTabs() = runTest {
        val repo = repository()
        val key = repo.account()!!.key
        val session = AbsRecordedSession("server", key, java.util.UUID.randomUUID().toString(), "show::rss",
            1767225600000, 1767225620000, 0, 20_000, 20_000, "podcasts", "Episode", "Host")
        repo.retainLocal(session)
        historyPages = mapOf(0 to """{"page":0,"total":1,"itemsPerPage":100,"sessions":[{"id":"${session.id}","libraryId":"podcasts","libraryItemId":"show","episodeId":"native-id","mediaType":"podcast","timeListening":10,"startedAt":1767225600000,"date":"2026-01-01"}]}""")
        val refreshed = repo.refresh(key, 2026, true, "podcasts")
        assertEquals(20.0, refreshed.statistics!!.seconds!!, 0.0)
        assertEquals(10_000L, refreshed.localAdditionalMs)
        assertEquals(20.0, refreshed.year!!.podcastSeconds!!, 0.0)
        assertEquals(20.0, repo.history(key, 0, "podcasts").sessions.single().seconds, 0.0)
        assertEquals("native-id", repo.history(key, 0).sessions.single().episodeId)
        failed = true
        val cached = repository().refresh(key, 2026, true, "podcasts")
        assertEquals(20.0, cached.statistics!!.seconds!!, 0.0)
        assertTrue(cached.partial)
        assertEquals(0.0, repo.cached(key, 2026, "other-library").statistics!!.seconds!!, 0.0)
        token = "replacement-token-for-same-user"
        failed = false
        val changedKey = repo.account()!!.key
        assertNull(repo.cached(changedKey, 2026).statistics) // Unverified credentials cannot see the old identity.
        // Verify identity through the explicit local account controls before reading old observations.
        repo.localToolsAccount(changedKey)
        assertEquals(1, repo.localRecords(changedKey).size)
    }

    @Test fun podcastGapEvidenceIsCachedPerAccountAndActualLibraryDuringOutages() = runTest {
        progressResponse = """{"mediaProgress":[{"libraryItemId":"show","episodeId":"episode","currentTime":1200}]}"""
        val repo = repository()
        val key = repo.account()!!.key
        val podcast = repo.refresh(key, 2026, true, "podcasts")
        assertTrue(podcast.podcastHistoryIncomplete)
        assertEquals(0.0, podcast.statistics!!.seconds!!, 0.0) // No fabricated duration.
        assertFalse(repo.cached(key, 2026, "books").podcastHistoryIncomplete)
        assertTrue(repo.cached(key, 2026).podcastHistoryIncomplete)
        failed = true
        assertTrue(repository().refresh(key, 2026, true, "podcasts").podcastHistoryIncomplete)
        token = "another-account"
        assertFalse(repo.cached(repo.account()!!.key, 2026, "podcasts").podcastHistoryIncomplete)
    }

    @Test fun successfulCacheSurvivesOutageReopeningAndSeparatesNewAccount() = runTest {
        val repo = repository()
        val account = repo.account()!!
        val first = repo.refresh(account.key, 2026, true)
        assertEquals(3600.0, first.statistics!!.seconds!!, 0.0)
        assertFalse(first.partial)
        failed = true
        val offline = repository().refresh(account.key, 2026, true)
        assertTrue(offline.cached)
        assertTrue(offline.partial)
        assertEquals(first.statistics, offline.statistics)
        token = "different-account"
        assertNull(repo.cached(repo.account()!!.key, 2026).statistics)
    }
    @Test fun recentCacheAvoidsRequestsButAnnualFailureDoesNotDiscardCore() = runTest {
        val repo = repository()
        val key = repo.account()!!.key
        repo.refresh(key, 2026, true)
        val count = requests.size
        repo.refresh(key, 2026, false)
        assertEquals(count, requests.size)
        annualFailed = true
        val partial = repo.refresh(key, 2026, true)
        assertTrue(partial.partial)
        assertFalse(partial.cached)
        assertNotNull(partial.statistics)
        assertEquals(2L, partial.year!!.booksFinished)
    }
    @Test fun nativeSubmissionUsesExistingCredentialAndStablePayload() = runTest {
        val repo = repository()
        val account = repo.account()!!
        val session = AbsRecordedSession("server", account.key, java.util.UUID.randomUUID().toString(), "book",
            1000, 2000, 0, 1000, 1000)
        repo.submitSession(account, session.payload())
        repo.submitSession(account, session.payload())
        assertTrue(requests.all { it.method == "POST" && it.url.encodedPath == "/api/session/local" })
        assertTrue(requests.all { it.header("Authorization") == "Bearer test-token" })
        val bodies = requests.map { request -> okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8() }
        assertEquals(bodies[0], bodies[1])
        token = "replacement-account"
        assertTrue(runCatching { repo.submitSession(account, session.payload()) }.isFailure)
        assertEquals(2, requests.size)
    }
    @Test fun historyUsesSelfUserEndpointAndKeepsItsCacheDuringFailure() = runTest {
        val repo = repository()
        val key = repo.account()!!.key
        assertEquals(0, repo.history(key, 0).total)
        assertEquals("itemsPerPage=30&page=0", requests.single().url.query)
        failed = true
        assertTrue(repository().history(key, 0).cached)
        assertTrue(requests.all { it.url.encodedPath.startsWith("/api/me/") })
    }
    @Test fun libraryTotalsRequireEveryPageAndPartialRefreshPreservesLastCompleteLibrarySnapshot() = runTest {
        fun row(id: Int, library: String, seconds: Int) = org.json.JSONObject().put("id", "s$id").put("libraryId", library)
            .put("libraryItemId", "book-$library").put("mediaType", "book").put("date", "2026-01-01").put("timeListening", seconds)
        fun page(index: Int, rows: List<org.json.JSONObject>) = org.json.JSONObject().put("page", index).put("itemsPerPage", 100)
            .put("total", 101).put("sessions", org.json.JSONArray(rows)).toString()
        historyPages = mapOf(0 to page(0, (0 until 100).map { row(it, if (it % 2 == 0) "mine" else "other", 10) }),
            1 to page(1, listOf(row(100, "other", 900))))
        val repo = repository()
        val key = repo.account()!!.key
        val mine = repo.refresh(key, 2026, true, "mine")
        assertEquals(500.0, mine.statistics!!.seconds!!, 0.0)
        assertFalse(mine.partial)
        assertEquals(1400.0, repo.cached(key, 2026, "other").statistics!!.seconds!!, 0.0)
        assertEquals(0.0, repo.cached(key, 2026, "empty-library").statistics!!.seconds!!, 0.0)
        assertNull(mine.year!!.booksFinished)
        assertEquals(30, repo.history(key, 0, "mine").sessions.size)
        assertEquals(20, repo.history(key, 1, "mine").sessions.size)
        failedPage = 1
        val failedRefresh = repo.refresh(key, 2026, true, "mine")
        assertTrue(failedRefresh.partial)
        assertTrue(failedRefresh.cached)
        assertEquals(500.0, failedRefresh.statistics!!.seconds!!, 0.0)
        assertTrue(requests.none { it.url.encodedPath.contains("/users/") })
    }
}
