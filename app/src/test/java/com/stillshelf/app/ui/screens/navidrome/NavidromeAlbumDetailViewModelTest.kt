package com.stillshelf.app.ui.screens.navidrome

import androidx.lifecycle.SavedStateHandle
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.ui.navigation.NavidromeRoute
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
class NavidromeAlbumDetailViewModelTest {
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
    fun activeConnectionChange_winsOverSlowerInitialLoad() = runTest(dispatcher) {
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
        val initialFetchStarted = CompletableDeferred<Unit>()
        val releaseInitialFetch = CompletableDeferred<Unit>()

        val initialDetail = albumDetail(
            albumName = "Old Album",
            trackName = "Old Track"
        )
        val refreshedDetail = albumDetail(
            albumName = "New Album",
            trackName = "New Track"
        )

        coEvery { repository.fetchAlbumDetail("album-1", false) } coAnswers {
            initialFetchStarted.complete(Unit)
            releaseInitialFetch.await()
            AppResult.Success(initialDetail)
        }
        coEvery { repository.fetchAlbumDetail("album-1", true) } returns AppResult.Success(refreshedDetail)

        val viewModel = NavidromeAlbumDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(NavidromeRoute.ALBUM_ID_ARG to "album-1")),
            navidromeRepository = repository
        )

        testScheduler.runCurrent()
        initialFetchStarted.await()

        connectionState.value = ActiveServerConnectionStatus(
            serverId = "server-1",
            effectiveBaseUrl = "https://remote.example",
            route = ServerConnectionRoute.Remote,
            connectionMode = ServerConnectionMode.Auto,
            switchingEnabled = true
        )
        advanceUntilIdle()

        assertEquals("New Album", viewModel.uiState.value.detail?.album?.name)

        releaseInitialFetch.complete(Unit)
        advanceUntilIdle()

        assertEquals("New Album", viewModel.uiState.value.detail?.album?.name)
        coVerify(exactly = 1) { repository.fetchAlbumDetail("album-1", false) }
        coVerify(exactly = 1) { repository.fetchAlbumDetail("album-1", true) }
    }

    private fun albumDetail(
        albumName: String,
        trackName: String
    ): NavidromeAlbumDetail {
        return NavidromeAlbumDetail(
            album = NavidromeAlbum(
                id = "album-1",
                name = albumName,
                artistName = "Artist",
                artistId = "artist-1",
                year = null,
                songCount = 1,
                durationSeconds = null,
                coverUrl = null,
                genre = null
            ),
            tracks = listOf(
                NavidromeTrack(
                    id = "track-1",
                    title = trackName,
                    artistName = "Artist",
                    albumName = albumName,
                    albumId = "album-1",
                    artistId = "artist-1",
                    trackNumber = 1,
                    durationSeconds = 180,
                    coverUrl = null,
                    streamUrl = "https://example.invalid/stream",
                    formatLabel = null,
                    bitRateKbps = null
                )
            )
        )
    }
}
