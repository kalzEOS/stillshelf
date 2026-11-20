package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocalListeningIntegrationTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val settings = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
        statisticsSource = StatisticsSource.LOCAL, localRecordingEnabled = true))
    private val store = mockk<ListeningSettingsStore> {
        every { state } returns settings
        coEvery { load() } returns Unit
    }
    private val changes = MutableStateFlow(0L)
    private val local = mockk<LocalListeningRepository> { every { this@mockk.changes } returns this@LocalListeningIntegrationTest.changes }
    private val repository = mockk<NavidromeRepository> {
        every { observeSession() } returns MutableStateFlow(null)
        coEvery { currentPlaybackSessionKey() } returns "library"
        coEvery { listeningCoverArtUrls(any()) } returns emptyMap()
    }

    @Test fun localRefreshesFromDatabaseAndDeletionCannotRestoreNetworkCache() = runTest {
        var count = 1L
        coEvery { local.statistics("library") } answers { ListeningStatistics("Local", count, count * 30) }
        val cache = mockk<ListeningStatisticsCache>(relaxed = true)
        val clients = mockk<ListeningClients>(relaxed = true)
        val native = mockk<NavidromeListeningClient>(relaxed = true)
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache, localHistory = local)
        assertEquals(1L, vm.state.value.statistics?.totalScrobbles)
        count = 0
        changes.value++
        assertEquals(0L, vm.state.value.statistics?.totalScrobbles)
        coVerify(exactly = 0) { cache.get(any()) }
        coVerify(exactly = 0) { cache.put(any(), any()) }
        coVerify(exactly = 0) { native.statistics() }
        verify(exactly = 0) { clients.forProvider(any()) }
    }

    @Test fun localTrendsBypassProviderCacheAndKeepTheRequestedRange() = runTest {
        var count = 1L
        coEvery { local.trends(any(), any(), any()) } answers {
            ListeningTrends(listOf(TrendPoint(LocalDate.now(), count, 30)), true, true, "")
        }
        val cache = mockk<SecureTokenStorage>(relaxed = true)
        val client = mockk<ListeningTrendsClient>(relaxed = true)
        val vm = ListeningTrendsViewModel(store, repository, client, cache, local)
        vm.load(TrendRange.YEAR)
        assertEquals(1L, vm.state.value.data?.points?.single()?.count)
        count = 0
        changes.value++
        assertEquals(0L, vm.state.value.data?.points?.single()?.count)
        coVerify { local.trends(TrendRange.YEAR, any(), any()) }
        coVerify(exactly = 0) { client.fetch(any(), any(), any()) }
        coVerify(exactly = 0) { cache.getNamedSecret(any()) }
        coVerify(exactly = 0) { cache.saveNamedSecret(any(), any(), any(), any()) }
    }
}
