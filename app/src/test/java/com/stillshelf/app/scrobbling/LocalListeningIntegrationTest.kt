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
        coEvery { local.summary() } answers { LocalListenSummary(count, count * 30_000, null) }
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

    @Test fun durationUpdatesPreserveLoadedPagesAndInFlightLoadMore() = runTest {
        var seconds = 30L
        coEvery { local.summary() } answers { LocalListenSummary(300, seconds * 1000, null) }
        val rows = (0 until 300).map { ListeningEntry("Artist $it", plays = 1) }
        coEvery { local.statistics(any()) } returns ListeningStatistics("Local", 300, 30,
            topArtists = rows.take(100), moreCharts = setOf(ListeningChart.ARTISTS))
        coEvery { local.chartPage(ListeningChart.ARTISTS, 100, any()) } returns ListeningChartPage(rows.subList(100, 200), true)
        val gate = kotlinx.coroutines.CompletableDeferred<ListeningChartPage>()
        coEvery { local.chartPage(ListeningChart.ARTISTS, 200, any()) } coAnswers { gate.await() }
        val vm = ListeningStatisticsViewModel(store, mockk(relaxed = true), mockk(relaxed = true), repository,
            mockk(relaxed = true), localHistory = local)
        clearMocks(local, answers = false)
        vm.loadMore(ListeningChart.ARTISTS)
        assertEquals(200, vm.state.value.statistics!!.topArtists.size)
        vm.loadMore(ListeningChart.ARTISTS)
        seconds = 35; changes.value++
        assertEquals(200, vm.state.value.statistics!!.topArtists.size)
        assertEquals(35L, vm.state.value.statistics!!.listeningSeconds)
        assertEquals(ListeningChart.ARTISTS, vm.state.value.loadingMore)
        gate.complete(ListeningChartPage(rows.drop(200), false))
        assertEquals(300, vm.state.value.statistics!!.topArtists.size)
        seconds = 40; changes.value++
        assertEquals(300, vm.state.value.statistics!!.topArtists.size)
        assertEquals(40L, vm.state.value.statistics!!.listeningSeconds)
        coVerify(exactly = 0) { local.statistics(any()) }
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
