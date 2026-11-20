package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ListenBrainzInsightsViewModelTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "saved", revision = "reader-one")
    private val state = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
        statisticsSource = StatisticsSource.EXTERNAL, connection = connection))
    private val settings = mockk<ListeningSettingsStore> {
        every { this@mockk.state } returns this@ListenBrainzInsightsViewModelTest.state
        coEvery { load() } returns Unit
    }
    private val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }
    private val cache = mockk<ListenBrainzInsightsCache>(relaxed = true) {
        coEvery { this@mockk.get(any()) } returns emptyMap()
    }
    private val client = mockk<ListenBrainzInsightsClient> {
        coEvery { fetch(any(), any(), any()) } coAnswers {
            InsightDataset(thirdArg(), listOf(InsightRow("Returned", 3)))
        }
    }
    private fun vm() = ListenBrainzInsightsViewModel(settings, repository, client, cache)

    @Test fun onlyVisibleListenBrainzTrendsFetchAndRevisitingUsesCache() = runTest {
        val vm = vm()
        coVerify(exactly = 0) { client.fetch(any(), any(), any()) }
        vm.show(InsightPeriod.THIS_MONTH)
        assertEquals(8, vm.state.value.results.size)
        assertTrue(vm.state.value.results.values.all { !it.loading && it.data != null })
        vm.hide(); vm.show(InsightPeriod.THIS_MONTH)
        coVerify(exactly = 8) { client.fetch(connection, InsightPeriod.THIS_MONTH, any()) }
        vm.show(InsightPeriod.ALL)
        coVerify(exactly = 0) { client.fetch(connection, InsightPeriod.ALL, InsightKind.ALBUMS) }
        coVerify(exactly = 0) { client.fetch(connection, InsightPeriod.ALL, InsightKind.TRACKS) }
    }

    @Test fun selectedProviderAndStatisticsVisibilityGateRequestsIndependentlyOfDirectMode() = runTest {
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        assertTrue(vm.state.value.enabled)
        val sender = ListeningConnection(ListeningProvider.KOITO, token = "different-service")
        state.value = state.value.copy(scrobblingEnabled = true, connections = listOf(connection, sender),
            multipleConnections = true, sendingConnectionId = sender.revision)
        coVerify(exactly = 8) { client.fetch(any(), any(), any()) }
        for (provider in ListeningProvider.entries.filterNot { it == ListeningProvider.LISTEN_BRAINZ }) {
            state.value = state.value.copy(connection = ListeningConnection(provider, username = "reader"))
            assertFalse(vm.state.value.enabled)
        }
        state.value = state.value.copy(connection = connection, showStatistics = false)
        assertFalse(vm.state.value.enabled)
        coVerify(exactly = 8) { client.fetch(any(), any(), any()) }
    }

    @Test fun oneSlowEndpointDoesNotBlockOthersAndProviderSwitchRejectsLateData() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { client.fetch(any(), any(), InsightKind.DAILY) } coAnswers {
            withContext(NonCancellable) { gate.await() }
            InsightDataset(InsightKind.DAILY, listOf(InsightRow("Old account", 99)))
        }
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        assertTrue(vm.state.value.results.getValue(InsightKind.DAILY).loading)
        assertNotNull(vm.state.value.results.getValue(InsightKind.ERAS).data)
        state.value = state.value.copy(statisticsSource = StatisticsSource.NAVIDROME)
        gate.complete(Unit)
        assertFalse(vm.state.value.enabled)
        assertTrue(vm.state.value.results.isEmpty())
    }

    @Test fun outageKeepsSameAccountCacheButNeverAnotherAccountsStatistics() = runTest {
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        coEvery { client.fetch(any(), any(), any()) } throws ListeningConnectionException("Unavailable")
        vm.show(InsightPeriod.THIS_MONTH, force = true)
        assertTrue(vm.state.value.results.values.all { it.cached && it.error && it.data?.rows?.single()?.count == 3L })
        state.value = state.value.copy(connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "other", token = "other"))
        assertTrue(vm.state.value.results.values.all { it.data == null })
        assertTrue(vm.state.value.results.values.all { it.error })
    }

    @Test fun persistedSnapshotReopensAndPartialFailuresAreCachedWithoutClaimingFreshness() = runTest {
        val snapshot = InsightCacheEntry(System.currentTimeMillis(), InsightDataset(InsightKind.ERAS, listOf(InsightRow("1990", 2, index = 1990))))
        coEvery { cache.get(any()) } returns mapOf(InsightKind.ERAS to snapshot)
        coEvery { client.fetch(any(), any(), InsightKind.GENRES) } throws ListeningConnectionException("Unavailable")
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        coVerify(exactly = 0) { client.fetch(any(), any(), InsightKind.ERAS) }
        assertEquals(snapshot.data, vm.state.value.results.getValue(InsightKind.ERAS).data)
        assertTrue(vm.state.value.results.getValue(InsightKind.GENRES).error)
        coVerify { cache.put(any(), match { it[InsightKind.GENRES]?.failed == true && it[InsightKind.ERAS]?.data == snapshot.data }) }
    }

    @Test fun slowOrQueuedRequestsAlwaysStopLoadingAndRepeatedRefreshDoesNotRestartThem() = runTest {
        coEvery { client.fetch(any(), any(), InsightKind.ERAS) } coAnswers { awaitCancellation() }
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        repeat(3) { vm.show(InsightPeriod.THIS_MONTH, force = true) }
        coVerify(exactly = 8) { client.fetch(any(), any(), any()) }
        assertTrue(vm.state.value.results.getValue(InsightKind.ERAS).loading)
        advanceTimeBy(20_001)
        runCurrent()
        assertTrue(vm.state.value.results.values.none { it.loading })
        assertTrue(vm.state.value.results.getValue(InsightKind.ERAS).error)
        assertNotNull(vm.state.value.results.getValue(InsightKind.DAILY).data)
    }

    @Test fun blockedCacheCannotLeaveAColdPeriodLoadingForeverAndHideClearsPendingState() = runTest {
        coEvery { cache.get(any()) } coAnswers { awaitCancellation() }
        coEvery { client.fetch(any(), any(), InsightKind.GENRES) } coAnswers { awaitCancellation() }
        val vm = vm()
        vm.show(InsightPeriod.THIS_MONTH)
        assertTrue(vm.state.value.loading)
        advanceTimeBy(2_001); runCurrent()
        assertNotNull(vm.state.value.results.getValue(InsightKind.DAILY).data)
        vm.hide()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.results.values.none { it.loading })
        vm.show(InsightPeriod.THIS_YEAR)
        advanceTimeBy(22_001); runCurrent()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.results.values.none { it.loading })
        assertTrue(vm.state.value.results.getValue(InsightKind.GENRES).error)
    }
}
