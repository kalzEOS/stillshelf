package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListenBrainzInsightsSchedulingTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader")
    private val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
        statisticsSource = StatisticsSource.EXTERNAL, connection = connection))
    private val settings = mockk<ListeningSettingsStore> {
        every { state } returns saved
        coEvery { load() } returns Unit
    }
    private val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }
    private val cache = mockk<ListenBrainzInsightsCache>(relaxed = true) {
        coEvery { this@mockk.get(any()) } returns emptyMap()
    }
    private val http = mockk<ListeningHttp>()
    private fun viewModel() = ListenBrainzInsightsViewModel(settings, repository, ListenBrainzInsightsClient(http), cache)

    @Test fun healthySlowBatchGetsANetworkTimeoutPerSlotRatherThanWhileQueued() = runTest {
        var completed = 0
        coEvery { http.text(any()) } coAnswers { delay(6_000); completed++; "" to null }
        val vm = viewModel()
        vm.show(InsightPeriod.THIS_MONTH)
        advanceTimeBy(20_001); runCurrent()
        assertEquals(6, completed)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.results.values.none { it.error })
        advanceTimeBy(4_001); runCurrent()
        assertEquals(8, completed)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.results.values.none { it.error || it.loading })
        vm.hide(); vm.show(InsightPeriod.THIS_MONTH)
        coVerify(exactly = 8) { http.text(any()) }
        vm.hide()
    }

    @Test fun unresponsiveBatchStillFinishesAndChangingProviderCancelsQueuedRequests() = runTest {
        coEvery { http.text(any()) } coAnswers { awaitCancellation() }
        val vm = viewModel()
        vm.show(InsightPeriod.THIS_MONTH)
        advanceTimeBy(60_001); runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals(8, vm.state.value.results.values.count { it.error })
        vm.show(InsightPeriod.THIS_YEAR)
        coVerify(exactly = 10) { http.text(any()) }
        saved.value = saved.value.copy(statisticsSource = StatisticsSource.NAVIDROME)
        advanceTimeBy(80_000); runCurrent()
        assertFalse(vm.state.value.enabled)
        assertTrue(vm.state.value.results.isEmpty())
        coVerify(exactly = 10) { http.text(any()) }
    }
}
