package com.stillshelf.app.ui.screens.navidrome

import androidx.lifecycle.SavedStateHandle
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavidromeArtistDetailViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun syncTimestampChange_triggersForcedArtistRefresh() = runTest(dispatcher) {
        val syncState = MutableStateFlow(
            SessionPreferenceState(
                activeServerId = null,
                activeLibraryId = null,
                lastLibrarySyncAtMs = null
            )
        )
        val connectionState = MutableStateFlow<ActiveServerConnectionStatus?>(null)
        val repository = mockk<NavidromeRepository>()
        val sessionPreferences = mockk<SessionPreferences>() {
            every { state } returns syncState
        }
        every { repository.observeActiveConnectionStatus() } returns connectionState

        val initialDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "Old Album"
        )
        val refreshedDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "New Album"
        )

        coEvery { repository.fetchArtistDetail("artist-1", false) } returns AppResult.Success(initialDetail)
        coEvery { repository.fetchArtistDetail("artist-1", true) } returns AppResult.Success(refreshedDetail)

        val viewModel = NavidromeArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ARTIST_ID_ARG to "artist-1")),
            navidromeRepository = repository,
            sessionPreferences = sessionPreferences
        )

        advanceUntilIdle()
        assertEquals("Old Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)

        syncState.value = syncState.value.copy(lastLibrarySyncAtMs = 123L)
        advanceUntilIdle()

        assertEquals("New Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", false) }
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", true) }
    }

    @Test
    fun activeConnectionChange_triggersForcedArtistRefresh() = runTest(dispatcher) {
        val connectionState = MutableStateFlow<ActiveServerConnectionStatus?>(null)
        val repository = mockk<NavidromeRepository>()
        val sessionPreferences = mockk<SessionPreferences>() {
            every { state } returns MutableStateFlow(
                SessionPreferenceState(
                    activeServerId = null,
                    activeLibraryId = null,
                    lastLibrarySyncAtMs = null
                )
            )
        }
        every { repository.observeActiveConnectionStatus() } returns connectionState

        val initialDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "Old Album"
        )
        val refreshedDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "New Album"
        )

        coEvery { repository.fetchArtistDetail("artist-1", false) } returns AppResult.Success(initialDetail)
        coEvery { repository.fetchArtistDetail("artist-1", true) } returns AppResult.Success(refreshedDetail)

        val viewModel = NavidromeArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ARTIST_ID_ARG to "artist-1")),
            navidromeRepository = repository,
            sessionPreferences = sessionPreferences
        )

        advanceUntilIdle()
        assertEquals("Old Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)

        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        advanceUntilIdle()

        assertEquals("New Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", false) }
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", true) }
    }

    @Test
    fun newerSyncRefresh_winsOverSlowerInitialLoad() = runTest(dispatcher) {
        val syncState = MutableStateFlow(
            SessionPreferenceState(
                activeServerId = null,
                activeLibraryId = null,
                lastLibrarySyncAtMs = null
            )
        )
        val connectionState = MutableStateFlow<ActiveServerConnectionStatus?>(null)
        val repository = mockk<NavidromeRepository>()
        val sessionPreferences = mockk<SessionPreferences>() {
            every { state } returns syncState
        }
        every { repository.observeActiveConnectionStatus() } returns connectionState
        val initialFetchStarted = CompletableDeferred<Unit>()
        val releaseInitialFetch = CompletableDeferred<Unit>()

        val initialDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "Old Album"
        )
        val refreshedDetail = artistDetail(
            artistName = "Initial Artist",
            albumName = "New Album"
        )

        coEvery { repository.fetchArtistDetail("artist-1", false) } coAnswers {
            initialFetchStarted.complete(Unit)
            releaseInitialFetch.await()
            AppResult.Success(initialDetail)
        }
        coEvery { repository.fetchArtistDetail("artist-1", true) } returns AppResult.Success(refreshedDetail)

        val viewModel = NavidromeArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ARTIST_ID_ARG to "artist-1")),
            navidromeRepository = repository,
            sessionPreferences = sessionPreferences
        )

        testScheduler.runCurrent()
        initialFetchStarted.await()
        syncState.value = syncState.value.copy(lastLibrarySyncAtMs = 123L)
        advanceUntilIdle()
        assertEquals("New Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)

        releaseInitialFetch.complete(Unit)
        advanceUntilIdle()

        assertEquals("New Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", false) }
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", true) }
    }

    @Test
    fun failedConnectionChange_followedBySecondConnectionChange_retriesAndRecovers() = runTest(dispatcher) {
        val connectionState = MutableStateFlow<ActiveServerConnectionStatus?>(null)
        val repository = mockk<NavidromeRepository>()
        val sessionPreferences = mockk<SessionPreferences>() {
            every { state } returns MutableStateFlow(
                SessionPreferenceState(
                    activeServerId = null,
                    activeLibraryId = null,
                    lastLibrarySyncAtMs = null
                )
            )
        }
        every { repository.observeActiveConnectionStatus() } returns connectionState

        val initialDetail = artistDetail(artistName = "Artist", albumName = "Initial Album")
        val recoveredDetail = artistDetail(artistName = "Artist", albumName = "Recovered Album")

        coEvery { repository.fetchArtistDetail("artist-1", false) } returns AppResult.Success(initialDetail)
        coEvery { repository.fetchArtistDetail("artist-1", true) } returnsMany listOf(
            AppResult.Error("Network error during transition"),
            AppResult.Success(recoveredDetail)
        )

        val viewModel = NavidromeArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ARTIST_ID_ARG to "artist-1")),
            navidromeRepository = repository,
            sessionPreferences = sessionPreferences
        )

        advanceUntilIdle()
        assertEquals("Initial Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)

        // First connection change — refresh fails.
        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        advanceUntilIdle()
        assertEquals("Network error during transition", viewModel.uiState.value.errorMessage)

        // Second connection change (route settles to a new endpoint) — refresh succeeds.
        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote-stable.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        advanceUntilIdle()
        assertEquals("Recovered Album", viewModel.uiState.value.detail?.albums?.firstOrNull()?.name)
        coVerify(exactly = 2) { repository.fetchArtistDetail("artist-1", true) }
    }

    @Test
    fun metadataOnlyConnectionChange_doesNotTriggerRefresh() = runTest(dispatcher) {
        val connectionState = MutableStateFlow<ActiveServerConnectionStatus?>(null)
        val repository = mockk<NavidromeRepository>()
        val sessionPreferences = mockk<SessionPreferences>() {
            every { state } returns MutableStateFlow(
                SessionPreferenceState(
                    activeServerId = null,
                    activeLibraryId = null,
                    lastLibrarySyncAtMs = null
                )
            )
        }
        every { repository.observeActiveConnectionStatus() } returns connectionState

        val detail = artistDetail(artistName = "Artist", albumName = "Album")
        coEvery { repository.fetchArtistDetail("artist-1", false) } returns AppResult.Success(detail)
        coEvery { repository.fetchArtistDetail("artist-1", true) } returns AppResult.Success(detail)

        val viewModel = NavidromeArtistDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ARTIST_ID_ARG to "artist-1")),
            navidromeRepository = repository,
            sessionPreferences = sessionPreferences
        )

        advanceUntilIdle()

        // Establish a connection status.
        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", true) }

        // Metadata-only update: same serverId and effectiveBaseUrl, only lanFallbackToRemote flips.
        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true,
            lanFallbackToRemote = true
        )
        advanceUntilIdle()

        // No additional forced refresh — the endpoint didn't change.
        coVerify(exactly = 1) { repository.fetchArtistDetail("artist-1", true) }
    }

    private fun artistDetail(
        artistName: String,
        albumName: String
    ): NavidromeArtistDetail {
        return NavidromeArtistDetail(
            artist = NavidromeArtist(
                id = "artist-1",
                name = artistName,
                albumCount = 1,
                coverUrl = null,
                imageUrl = null
            ),
            albums = listOf(
                NavidromeAlbum(
                    id = "album-1",
                    name = albumName,
                    artistName = artistName,
                    artistId = "artist-1",
                    year = null,
                    songCount = 1,
                    durationSeconds = null,
                    coverUrl = null,
                    genre = null
                )
            )
        )
    }
}
