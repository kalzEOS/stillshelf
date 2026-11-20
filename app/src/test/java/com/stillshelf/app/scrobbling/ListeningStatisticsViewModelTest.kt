package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningStatisticsViewModelTest {
    @Test fun identicalAlbumTitlesKeepSeparateMatchesForDifferentReleaseIdentities() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val one = "59f3c45f-0cfc-4b56-a04c-9479970992c6"
        val two = "a7d59a07-0b66-4b4c-adeb-57dbc879da3a"
        val entries = listOf(ListeningEntry("Daughtry", "Daughtry", 15, musicBrainzReleaseId = one),
            ListeningEntry("Daughtry", "Daughtry", 4, musicBrainzReleaseId = two))
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", topAlbums = entries)
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        val first = com.stillshelf.app.core.model.NavidromeAlbum("original", "Daughtry", "Daughtry", null, null, 12, null, null, null, one)
        coEvery { repository.search(any(), any()) } returns com.stillshelf.app.core.util.AppResult.Success(
            com.stillshelf.app.core.model.NavidromeSearchResults(emptyList(), listOf(first, first.copy(id = "deluxe", musicBrainzId = two)), emptyList()))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        entries.forEach { vm.resolveEntry(it, "album") }
        assertEquals(listOf("original", "deluxe"), vm.state.value.statistics!!.topAlbums.map { it.albumId })
        assertEquals(listOf(15L, 4L), vm.state.value.statistics!!.topAlbums.map { it.plays })
    }
    @Test fun targetedTrackRetryReadsOnlyThatChartAndKeepsAlbumsAndCount() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", 555,
            topAlbums = listOf(ListeningEntry("Daughtry", "Daughtry", 15)), unavailableSections = setOf(ListeningMetric.TRACKS))
        val tracks = listOf(ListeningEntry("It's Not Over", "Daughtry", 4))
        coEvery { external.chartPage(connection, ListeningChart.TRACKS, 0) } returns ListeningChartPage(tracks, false)
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        vm.retryChart(ListeningChart.TRACKS)
        assertEquals(tracks, vm.state.value.statistics!!.topTracks)
        assertEquals(555L, vm.state.value.statistics!!.totalScrobbles)
        assertEquals("Daughtry", vm.state.value.statistics!!.topAlbums.single().title)
        assertTrue(vm.state.value.statistics!!.unavailableSections.isEmpty())
        assertNull(vm.state.value.loadingMore)
    }
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val cache = mockk<ListeningStatisticsCache>(relaxed = true)
    init { coEvery { cache.get(any()) } returns null }
    private val connection = ListeningConnection(ListeningProvider.KOITO, "https://example.invalid", token = "test")
    private val settings = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true, connection = connection))
    private val store = mockk<ListeningSettingsStore> {
        every { state } returns settings
        coEvery { load() } returns Unit
    }
    private val external = mockk<ListeningProviderClient>()
    private val clients = mockk<ListeningClients> { every { forProvider(any()) } returns external }
    private val native = mockk<NavidromeListeningClient> {
        coEvery { statistics() } returns ListeningStatistics("Navidrome · Recorded history", totalScrobbles = 606)
    }
    private val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }

    @Test fun removingExternalConnectionDoesNotDeleteOrReattributeNativeHistory() = runTest {
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        assertEquals(606L, vm.state.value.statistics?.totalScrobbles)
        settings.value = settings.value.copy(connection = null)
        assertEquals(606L, vm.state.value.statistics?.totalScrobbles)
        assertEquals("Navidrome · Recorded history", vm.state.value.sourceLabel)
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        assertNull(vm.state.value.statistics)
        assertTrue(vm.state.value.sourceLabel.contains("Not configured"))
        assertNotNull(vm.state.value.error)
    }

    @Test fun removingSelectedExternalConnectionImmediatelyInvalidatesItsData() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        coEvery { external.statistics(any()) } returns ListeningStatistics("Koito · All time", totalScrobbles = 123)
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        assertEquals(123L, vm.state.value.statistics?.totalScrobbles)
        settings.value = settings.value.copy(connection = null)
        assertNull(vm.state.value.statistics)
        assertFalse(vm.state.value.loading)
        assertNotNull(vm.state.value.error)
    }

    @Test fun lateResponseFromOldProviderCannotOverwriteNewSource() = runTest {
        val gate = CompletableDeferred<Unit>()
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        coEvery { external.statistics(any()) } coAnswers {
            withContext(NonCancellable) { gate.await() }
            ListeningStatistics("Koito · All time", totalScrobbles = 123)
        }
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        assertTrue(vm.state.value.loading)
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.NAVIDROME)
        gate.complete(Unit)
        assertEquals(606L, vm.state.value.statistics?.totalScrobbles)
        assertEquals("Navidrome · Recorded history", vm.state.value.sourceLabel)
    }

    @Test fun visibilitySwitchHidesStatisticsWithoutChangingSavedConnectionOrMethod() = runTest {
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        val original = settings.value
        settings.value = original.copy(showStatistics = false)
        assertFalse(vm.state.value.enabled)
        assertNull(vm.state.value.statistics)
        assertSame(connection, settings.value.connection)
        assertEquals(original.method, settings.value.method)
    }

    @Test fun loadingNextChartPageAppendsAndKeepsOverviewAndOtherTabs() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val first = (1..50).map { ListeningEntry("Artist $it", plays = it.toLong()) }
        coEvery { external.statistics(any()) } returns ListeningStatistics("External", 606, topArtists = first,
            topAlbums = listOf(ListeningEntry("Album")), moreCharts = setOf(ListeningChart.ARTISTS))
        coEvery { external.chartPage(connection, ListeningChart.ARTISTS, 50) } returns ListeningChartPage(listOf(ListeningEntry("Artist 51", plays = 1)), false)
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        vm.loadMore(ListeningChart.ARTISTS)
        assertEquals(51, vm.state.value.statistics!!.topArtists.size)
        assertEquals(606L, vm.state.value.statistics!!.totalScrobbles)
        assertEquals("Album", vm.state.value.statistics!!.topAlbums.single().title)
        assertTrue(vm.state.value.statistics!!.moreCharts.isEmpty())
        assertNull(vm.state.value.loadingMore)
    }

    @Test fun resolvingVisibleExternalArtistUpdatesLibraryLinkAndImageButPreservesExternalPlays() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val entry = ListeningEntry("Artist", plays = 99, detailsUrl = "https://musicbrainz.org/search?query=Artist")
        coEvery { external.statistics(any()) } returns ListeningStatistics("External", topArtists = listOf(entry))
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        coEvery { repository.search(any(), any()) } returns com.stillshelf.app.core.util.AppResult.Success(
            com.stillshelf.app.core.model.NavidromeSearchResults(
                listOf(com.stillshelf.app.core.model.NavidromeArtist("library-id", "Artist", 4, "cover", "image")), emptyList(), emptyList()))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        vm.resolveEntry(entry, "artist")
        val resolved = vm.state.value.statistics!!.topArtists.single()
        assertEquals("library-id", resolved.artistId)
        assertEquals("image", resolved.artworkUrl)
        assertEquals(99L, resolved.plays)
        assertEquals(entry.detailsUrl, resolved.detailsUrl)
    }

    @Test fun offStillFetchesFreshStatisticsAndOutageRetainsLastSuccessfulData() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL, scrobblingEnabled = true)
        coEvery { external.statistics(any()) } returns ListeningStatistics("Koito", 42)
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        settings.value = settings.value.withSelection(ScrobblingSelection.OFF)
        coEvery { external.statistics(any()) } returns ListeningStatistics("Koito", 43)
        vm.refresh()
        assertTrue(vm.state.value.enabled)
        assertEquals(43L, vm.state.value.statistics?.totalScrobbles)
        assertFalse(vm.state.value.isCached)
        coEvery { external.statistics(any()) } throws ListeningConnectionException("Server unavailable")
        vm.refresh()
        assertEquals(43L, vm.state.value.statistics?.totalScrobbles)
        assertTrue(vm.state.value.isCached)
        assertTrue(vm.state.value.error!!.contains("cached statistics"))
        assertSame(connection, settings.value.connection)
        assertFalse(settings.value.canSubmit)
    }

    @Test fun refreshPreservesResolvedLibraryLinksEvenWhenProviderSuppliesItsOwnArtwork() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val entry = ListeningEntry("Artist", plays = 99, artworkUrl = "https://provider.invalid/image", detailsUrl = "https://listenbrainz.org")
        coEvery { external.statistics(any()) } returns ListeningStatistics("External", topArtists = listOf(entry))
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        coEvery { repository.search(any(), any()) } returns com.stillshelf.app.core.util.AppResult.Success(
            com.stillshelf.app.core.model.NavidromeSearchResults(
                listOf(com.stillshelf.app.core.model.NavidromeArtist("library-id", "Artist", 4, null, null)), emptyList(), emptyList()))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        vm.resolveEntry(entry, "artist")
        vm.refresh()
        assertEquals("library-id", vm.state.value.statistics!!.topArtists.single().artistId)
        assertEquals(99L, vm.state.value.statistics!!.topArtists.single().plays)
        assertEquals("library-id", vm.resolveForNavigation(entry, "artist")!!.artistId)
    }

    @Test fun tapRetriesPreviouslyFailedLibraryResolutionWithoutDependingOnArtwork() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val entry = ListeningEntry("Album", "Artist", plays = 6, artworkUrl = "https://provider.invalid/image")
        coEvery { external.statistics(any()) } returns ListeningStatistics("External", topAlbums = listOf(entry))
        coEvery { repository.currentAuth(any()) } returns mockk(relaxed = true)
        coEvery { repository.search(any(), false) } returns com.stillshelf.app.core.util.AppResult.Success(
            com.stillshelf.app.core.model.NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
        coEvery { repository.search(any(), true) } returns com.stillshelf.app.core.util.AppResult.Success(
            com.stillshelf.app.core.model.NavidromeSearchResults(emptyList(), listOf(
                com.stillshelf.app.core.model.NavidromeAlbum("album-id", "Album", "Artist", null, null, 1, null, null, null)), emptyList()))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        vm.resolveEntry(entry, "album")
        assertNull(vm.state.value.statistics!!.topAlbums.single().albumId)
        assertEquals("album-id", vm.resolveForNavigation(entry, "album")!!.albumId)
        assertEquals("album-id", vm.state.value.statistics!!.topAlbums.single().albumId)
    }

    @Test fun newScreenLoadsPersistedCacheDuringOutageThenReplacesItWhenReachable() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        coEvery { cache.get(any()) } returns ListeningStatistics("Koito", 42)
        coEvery { external.statistics(any()) } throws ListeningConnectionException("Unavailable")
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        assertEquals(42L, vm.state.value.statistics?.totalScrobbles)
        assertTrue(vm.state.value.isCached)
        coEvery { external.statistics(any()) } returns ListeningStatistics("Koito", 44)
        vm.refresh()
        assertEquals(44L, vm.state.value.statistics?.totalScrobbles)
        assertFalse(vm.state.value.isCached)
        assertNull(vm.state.value.error)
    }

    @Test fun partialAlbumOutagePreservesSavedAlbumsButSuccessfulEmptyResponseClearsThem() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        val albums = listOf(ListeningEntry("Saved Album", "Artist", 9, albumId = "library-album"))
        coEvery { cache.get(any()) } returns ListeningStatistics("ListenBrainz", 100, topAlbums = albums, moreCharts = setOf(ListeningChart.ALBUMS))
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", 101, unavailableSections = setOf(ListeningMetric.ALBUMS))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        assertEquals(albums, vm.state.value.statistics!!.topAlbums)
        assertEquals(101L, vm.state.value.statistics!!.totalScrobbles)
        assertTrue(vm.state.value.isCached)
        assertTrue(ListeningChart.ALBUMS in vm.state.value.statistics!!.moreCharts)
        vm.refresh()
        assertEquals(albums, vm.state.value.statistics!!.topAlbums)
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", 102)
        vm.refresh()
        assertTrue(vm.state.value.statistics!!.topAlbums.isEmpty())
        assertFalse(vm.state.value.isCached)
    }

    @Test fun partialFailureAfterSwitchingAccountsCannotReuseAnotherAccountsAlbums() = runTest {
        settings.value = settings.value.copy(statisticsSource = StatisticsSource.EXTERNAL)
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", topAlbums = listOf(ListeningEntry("Private Album")))
        val vm = ListeningStatisticsViewModel(store, clients, native, repository, cache)
        coEvery { external.statistics(any()) } returns ListeningStatistics("ListenBrainz", unavailableSections = setOf(ListeningMetric.ALBUMS))
        settings.value = settings.value.copy(connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "different"))
        assertTrue(vm.state.value.statistics!!.topAlbums.isEmpty())
        assertFalse(vm.state.value.isCached)
    }
}
