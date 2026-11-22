package com.stillshelf.app.absstats

import androidx.lifecycle.ViewModelStore
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.data.repo.SessionRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AbsStatisticsViewModelTest {
    @Test fun cachedLibraryReplacementDoesNotPublishAnEmptySnapshot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            fun data(seconds: Double) = AbsStatisticsSnapshot(statistics = AbsListeningStatistics(seconds, null, emptyMap(), emptyList(), emptyList()))
            val cached = CompletableDeferred<AbsStatisticsSnapshot>()
            val repo = mockk<AbsStatisticsRepository> {
                every { accountDisplayName(any()) } returns "Reader"
                every { scopes() } returns MutableStateFlow("account" to "mine")
                coEvery { cached(any(), any(), "mine") } returns data(3600.0)
                coEvery { cached(any(), any(), "other") } coAnswers { cached.await() }
                coEvery { refresh(any(), any(), any(), "mine") } returns data(3600.0)
                coEvery { refresh(any(), any(), any(), "other") } coAnswers { awaitCancellation() }
            }
            val sessions = mockk<SessionRepository> { every { observeLibrariesForActiveServer() } returns MutableStateFlow(listOf(
                Library("mine", "server", "Mine"), Library("other", "server", "Other"))) }
            val vm = AbsStatisticsViewModel(repo, sessions)
            store.put("vm", vm)
            runCurrent()
            vm.library("other")
            runCurrent()
            assertEquals("mine", vm.selection.value.libraryId)
            assertEquals(3600.0, vm.state.value.snapshot.statistics!!.seconds!!, 0.0)
            cached.complete(data(7200.0))
            runCurrent()
            assertEquals("other", vm.selection.value.libraryId)
            assertEquals(7200.0, vm.state.value.snapshot.statistics!!.seconds!!, 0.0)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun switchingLibraryClearsOtherLibraryDataWithoutChangingPlaybackSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val scopes = MutableStateFlow("account" to "mine")
            val repo = mockk<AbsStatisticsRepository> {
                every { accountDisplayName(any()) } returns "Reader"
                every { scopes() } returns scopes
                coEvery { cached(any(), any(), any()) } returns AbsStatisticsSnapshot()
                coEvery { refresh(any(), any(), any(), "mine") } returns AbsStatisticsSnapshot(statistics =
                    AbsListeningStatistics(3600.0, null, emptyMap(), emptyList(), emptyList()))
                coEvery { refresh(any(), any(), any(), "other") } coAnswers { awaitCancellation() }
            }
            val sessions = mockk<SessionRepository>(relaxed = true) {
                every { observeLibrariesForActiveServer() } returns MutableStateFlow(listOf(
                    Library("mine", "server", "Mine"), Library("other", "server", "Other")))
            }
            val vm = AbsStatisticsViewModel(repo, sessions)
            store.put("vm", vm)
            runCurrent()
            assertEquals(3600.0, vm.state.value.snapshot.statistics!!.seconds!!, 0.0)
            vm.library("other")
            runCurrent()
            assertNull(vm.state.value.snapshot.statistics)
            assertEquals("other", vm.selection.value.libraryId)
            assertTrue(vm.state.value.loading)
            coVerify(exactly = 0) { sessions.setActiveLibraryAndPrimeHomeFeed(any(), any(), any(), any()) }
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancelledRefreshCannotClearItsReplacementsLoadingState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val gate = CompletableDeferred<AbsStatisticsSnapshot>()
            var calls = 0
            val repo = mockk<AbsStatisticsRepository> {
                every { accountDisplayName(any()) } returns "Reader"
                every { scopes() } returns MutableStateFlow("account" to null)
                coEvery { cached(any(), any(), any()) } returns AbsStatisticsSnapshot()
                coEvery { refresh(any(), any(), any(), any()) } coAnswers {
                    if (++calls == 1) awaitCancellation() else gate.await()
                }
            }
            val sessions = mockk<SessionRepository> { every { observeLibrariesForActiveServer() } returns MutableStateFlow(emptyList()) }
            val vm = AbsStatisticsViewModel(repo, sessions)
            store.put("vm", vm)
            runCurrent()
            vm.refresh(true)
            runCurrent()
            assertTrue(vm.state.value.loading)
            gate.complete(AbsStatisticsSnapshot())
            runCurrent()
            assertFalse(vm.state.value.loading)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
