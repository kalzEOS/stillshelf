package com.stillshelf.app.scrobbling

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocalListeningInsightsViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun disposingAnOlderLazyCompositionDoesNotCancelTheVisibleQuery() = runTest {
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
            localRecordingEnabled = true, statisticsSource = StatisticsSource.LOCAL))
        val store = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val history = mockk<LocalListeningRepository> { every { insightChanges } returns MutableStateFlow(1L) }
        val client = mockk<LocalListeningInsights>()
        coEvery { client.load(any(), any(), any()) } coAnswers {
            delay(100)
            LocalInsights(mapOf(InsightKind.TRACKS to InsightResult(InsightDataset(InsightKind.TRACKS, listOf(InsightRow("Current", 1))))))
        }
        val vm = LocalListeningInsightsViewModel(store, history, client)
        val previous = Any(); val current = Any()
        vm.show(InsightPeriod.THIS_MONTH, previous)
        vm.show(InsightPeriod.THIS_MONTH, current)
        vm.hide(previous)
        assertTrue(vm.state.value.loading)
        advanceTimeBy(101); runCurrent()
        assertFalse(vm.state.value.loading)
        assertEquals("Current", vm.state.value.results.getValue(InsightKind.TRACKS).data!!.rows.single().label)
        vm.hide(current)
    }
    @Test fun slowLocalQueryAndLeavingTheScreenAlwaysStopLoading() = runTest {
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
            localRecordingEnabled = true, statisticsSource = StatisticsSource.LOCAL))
        val store = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val history = mockk<LocalListeningRepository> { every { insightChanges } returns MutableStateFlow(1L) }
        val client = mockk<LocalListeningInsights>()
        coEvery { client.load(any(), any(), any()) } coAnswers { delay(30_000); LocalInsights() }
        val vm = LocalListeningInsightsViewModel(store, history, client)
        vm.show(InsightPeriod.THIS_MONTH)
        assertTrue(vm.state.value.loading)
        advanceTimeBy(15_001); runCurrent()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.results.values.any { it.error })
        vm.show(InsightPeriod.THIS_YEAR)
        assertTrue(vm.state.value.loading)
        vm.hide()
        assertFalse(vm.state.value.loading)
    }
    @Test fun leavingLocalSourceRejectsLateDataAndReturningLoadsCurrentRecords() = runTest {
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
            localRecordingEnabled = true, statisticsSource = StatisticsSource.LOCAL))
        val store = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val changes = MutableStateFlow(1L)
        val history = mockk<LocalListeningRepository> { every { insightChanges } returns changes }
        val client = mockk<LocalListeningInsights>()
        val gate = CompletableDeferred<LocalInsights>()
        coEvery { client.load(any(), any(), any()) } coAnswers { withContext(NonCancellable) { gate.await() } }
        val vm = LocalListeningInsightsViewModel(store, history, client)
        vm.show(InsightPeriod.THIS_MONTH)
        saved.value = saved.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        gate.complete(LocalInsights(mapOf(InsightKind.TRACKS to InsightResult(InsightDataset(InsightKind.TRACKS, listOf(InsightRow("Old", 1)))))))
        assertFalse(vm.state.value.enabled)
        assertTrue(vm.details.value.results.isEmpty())
        coEvery { client.load(any(), any(), any()) } returns LocalInsights(mapOf(InsightKind.TRACKS to
            InsightResult(InsightDataset(InsightKind.TRACKS, listOf(InsightRow("Current", 2))))))
        saved.value = saved.value.copy(statisticsSource = StatisticsSource.LOCAL)
        assertEquals("Current", vm.state.value.results.getValue(InsightKind.TRACKS).data!!.rows.single().label)
        vm.hide()
        changes.value++
        coVerify(exactly = 2) { client.load(any(), any(), any()) }
    }
}
