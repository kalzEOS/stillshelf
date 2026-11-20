package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningTrendsViewModelTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun providerSwitchRejectsLateResponseAndRangeRevisitUsesCache() = runTest {
        val state = MutableStateFlow(ListeningSettings(loaded = true, statisticsSource = StatisticsSource.EXTERNAL,
            connection = ListeningConnection(ListeningProvider.KOITO, revision = "first")))
        val settings = mockk<ListeningSettingsStore> { every { this@mockk.state } returns state; coEvery { load() } returns Unit }
        val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }
        val storage = mockk<SecureTokenStorage>(relaxed = true) { coEvery { getNamedSecret(any()) } returns null }
        val client = mockk<ListeningTrendsClient>()
        val gate = CompletableDeferred<Unit>()
        coEvery { client.fetch(any(), any(), any()) } coAnswers {
            if (firstArg<ListeningConnection>().provider == ListeningProvider.KOITO) withContext(NonCancellable) { gate.await() }
            ListeningTrends(listOf(TrendPoint(LocalDate.of(2026, 11, 19), if (firstArg<ListeningConnection>().provider == ListeningProvider.KOITO) 1 else 2)), true, true, "UTC")
        }
        val vm = ListeningTrendsViewModel(settings, repository, client, storage)
        state.value = state.value.copy(connection = ListeningConnection(ListeningProvider.MALOJA, revision = "second"))
        gate.complete(Unit)
        assertEquals(2L, vm.state.value.data!!.points.single().count)
        vm.load(TrendRange.MONTH)
        assertEquals(2L, vm.state.value.data!!.points.single().count)
        coVerify(exactly = 2) { client.fetch(any(), any(), any()) }
        coEvery { client.fetch(any(), any(), any()) } throws ListeningConnectionException("Unavailable")
        vm.load(TrendRange.MONTH, force = true)
        assertEquals(2L, vm.state.value.data!!.points.single().count)
        assertTrue(vm.state.value.cached)
        assertNotNull(vm.state.value.error)
    }
}
