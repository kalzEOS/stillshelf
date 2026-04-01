package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.util.AppResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryImplHelpersTest {
    @Test
    fun runDeduped_joinsConcurrentRefreshesForSameKey() = runTest {
        val deduper = DetailRefreshDeduper()
        val startedCount = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        val first = async {
            deduper.runDeduped("book:1") {
                startedCount.incrementAndGet()
                release.await()
                AppResult.Success(Unit)
            }
        }
        val second = async {
            deduper.runDeduped("book:1") {
                startedCount.incrementAndGet()
                AppResult.Success(Unit)
            }
        }

        testScheduler.advanceUntilIdle()
        assertEquals(1, startedCount.get())

        release.complete(Unit)
        assertEquals(AppResult.Success(Unit), first.await())
        assertEquals(AppResult.Success(Unit), second.await())
        assertEquals(1, startedCount.get())
    }

    @Test
    fun selectLocalProgressOverride_keepsLocalMutationWhenServerIsOlder() {
        val mutation = BookProgressMutation(
            bookId = "book-1",
            progressPercent = 0.5,
            currentTimeSeconds = 300.0,
            durationSeconds = 600.0,
            isFinished = false
        )

        val selected = selectLocalProgressOverride(
            mutation = mutation,
            fetchedProgressPercent = 0.2,
            fetchedCurrentTimeSeconds = 120.0,
            fetchedDurationSeconds = 600.0,
            fetchedIsFinished = false,
            progressEpsilon = 0.005,
            timeEpsilonSeconds = 1.0
        )

        assertEquals(mutation, selected)
    }

    @Test
    fun selectLocalProgressOverride_dropsMutationWhenServerMatchesIt() {
        val mutation = BookProgressMutation(
            bookId = "book-1",
            progressPercent = 0.5,
            currentTimeSeconds = 300.0,
            durationSeconds = 600.0,
            isFinished = false
        )

        val selected = selectLocalProgressOverride(
            mutation = mutation,
            fetchedProgressPercent = 0.5,
            fetchedCurrentTimeSeconds = 300.5,
            fetchedDurationSeconds = 600.0,
            fetchedIsFinished = false,
            progressEpsilon = 0.005,
            timeEpsilonSeconds = 1.0
        )

        assertNull(selected)
    }

    @Test
    fun didResolvedActiveConnectionRequireRefresh_ignoresServerSwitchHandoffs() {
        val previous = activeConnectionStatus(
            serverId = "server-local",
            effectiveBaseUrl = "https://local.example"
        )
        val current = activeConnectionStatus(
            serverId = "server-remote",
            effectiveBaseUrl = "https://remote.example"
        )

        assertFalse(didResolvedActiveConnectionRequireRefresh(previous, current))
    }

    @Test
    fun didResolvedActiveConnectionRequireRefresh_detectsResolvedRouteChangeForSameServer() {
        val previous = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://lan.example",
            route = ServerConnectionRoute.Local
        )
        val current = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://wan.example",
            route = ServerConnectionRoute.Remote,
            lanFallbackToRemote = true
        )

        assertTrue(didResolvedActiveConnectionRequireRefresh(previous, current))
    }

    @Test
    fun resolveResolvedActiveConnectionStatusToRefresh_returnsObservedStatusWhenRefreshIsNeeded() {
        val previousApplied = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://lan.example",
            route = ServerConnectionRoute.Local
        )
        val latestObserved = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://wan.example",
            route = ServerConnectionRoute.Remote,
            lanFallbackToRemote = true
        )

        assertEquals(
            latestObserved,
            resolveResolvedActiveConnectionStatusToRefresh(previousApplied, latestObserved)
        )
    }

    @Test
    fun resolveResolvedActiveConnectionStatusToRefresh_ignoresServerSwitches() {
        val previousApplied = activeConnectionStatus(
            serverId = "server-local",
            effectiveBaseUrl = "https://local.example"
        )
        val latestObserved = activeConnectionStatus(
            serverId = "server-remote",
            effectiveBaseUrl = "https://remote.example"
        )

        assertNull(
            resolveResolvedActiveConnectionStatusToRefresh(previousApplied, latestObserved)
        )
    }

    @Test
    fun resolveResolvedActiveConnectionStatusToRefresh_ignoresTransientBackgroundRouteChangesThatReturn() {
        val previousApplied = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://lan.example",
            route = ServerConnectionRoute.Local
        )
        val latestObserved = activeConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://lan.example",
            route = ServerConnectionRoute.Local
        )

        assertNull(
            resolveResolvedActiveConnectionStatusToRefresh(previousApplied, latestObserved)
        )
    }

    @Test
    fun matchesCachedHomeFeedSelection_requiresMatchingServerAndLibrary() {
        assertTrue(
            matchesCachedHomeFeedSelection(
                activeServerId = "server-1",
                activeLibraryId = "library-1",
                cachedServerId = "server-1",
                cachedLibraryId = "library-1"
            )
        )
        assertFalse(
            matchesCachedHomeFeedSelection(
                activeServerId = "server-1",
                activeLibraryId = "library-1",
                cachedServerId = "server-2",
                cachedLibraryId = "library-1"
            )
        )
    }

    @Test
    fun matchesCachedHomeFeedSelection_rejectsLegacyEntriesWithoutServerId() {
        assertFalse(
            matchesCachedHomeFeedSelection(
                activeServerId = "server-1",
                activeLibraryId = "library-1",
                cachedServerId = null,
                cachedLibraryId = "library-1"
            )
        )
    }

    private fun activeConnectionStatus(
        serverId: String,
        effectiveBaseUrl: String,
        route: ServerConnectionRoute = ServerConnectionRoute.Default,
        connectionMode: ServerConnectionMode = ServerConnectionMode.Auto,
        switchingEnabled: Boolean = true,
        lanFallbackToRemote: Boolean = false
    ): ActiveServerConnectionStatus {
        return ActiveServerConnectionStatus(
            serverId = serverId,
            effectiveBaseUrl = effectiveBaseUrl,
            route = route,
            connectionMode = connectionMode,
            switchingEnabled = switchingEnabled,
            lanFallbackToRemote = lanFallbackToRemote,
            lanBaseUrl = "https://lan.example",
            wanBaseUrl = "https://wan.example"
        )
    }
}
