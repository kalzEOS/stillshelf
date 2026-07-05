package com.stillshelf.app.ui.components

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.PodcastRepository
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the mini-player's offline podcast playback paths — the bug where a downloaded podcast
 * episode would fail to resume after the player was released (10-min timeout) or after an app
 * process kill, because the original code only had a server-fetch path with no offline fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniPlayerViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region alive-process path (player was released; playbackState.book != null, !hasActivePlayer)

    @Test
    fun aliveProcess_podcastEpisode_serverSucceeds_playsLocalDownload() = runTest(dispatcher) {
        val controller = testController(
            uiState = MutableStateFlow(PlaybackUiState(book = testPodcastBook(), positionMs = 5_000L))
        )
        every { controller.hasActivePlayer } returns false

        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returns
                AppResult.Success(testPlaybackSource())
        }
        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = testDownloadItem())
        )
        advanceUntilIdle()

        vm.onPlayPauseClick()
        advanceUntilIdle()

        val sourceSlot = slot<PlaybackSource>()
        verify(exactly = 1) { controller.playFromSource(capture(sourceSlot), any()) }
        assertEquals("file:///test/track.mp3", sourceSlot.captured.streamUrl)
    }

    @Test
    fun aliveProcess_podcastEpisode_serverFails_hasDownload_playsOffline() = runTest(dispatcher) {
        val controller = testController(
            uiState = MutableStateFlow(PlaybackUiState(book = testPodcastBook(), positionMs = 5_000L))
        )
        every { controller.hasActivePlayer } returns false

        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returns
                AppResult.Error("Network unavailable")
        }
        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = testDownloadItem())
        )
        advanceUntilIdle()

        vm.onPlayPauseClick()
        advanceUntilIdle()

        val sourceSlot = slot<PlaybackSource>()
        verify(exactly = 1) { controller.playFromSource(capture(sourceSlot), any()) }
        assertEquals("file:///test/track.mp3", sourceSlot.captured.streamUrl)
    }

    @Test
    fun aliveProcess_podcastEpisode_serverFails_noDownload_setsError() = runTest(dispatcher) {
        val controller = testController(
            uiState = MutableStateFlow(PlaybackUiState(book = testPodcastBook(), positionMs = 5_000L))
        )
        every { controller.hasActivePlayer } returns false

        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returns
                AppResult.Error("Network unavailable")
        }
        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = null)
        )
        advanceUntilIdle()

        vm.onPlayPauseClick()
        advanceUntilIdle()

        verify(exactly = 0) { controller.playFromSource(any(), any()) }
        assertNotNull(vm.uiState.value.errorMessage)
    }

    // endregion

    // region killed-process path (app was restarted; playbackState.book == null, item loaded from DataStore)

    @Test
    fun killedProcess_podcastEpisode_serverSucceeds_playsLocalDownload() = runTest(dispatcher) {
        val controller = testController() // no book — simulates fresh process
        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returns
                AppResult.Success(testPlaybackSource())
        }
        val prefs = testPrefs()
        prefs.setLastPlayedBookId("show1::ep1")

        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = testDownloadItem()),
            prefs = prefs
        )
        advanceUntilIdle() // refresh() loads item via loadPodcastEpisodeItem

        assertNotNull("item should be populated before play", vm.uiState.value.item)

        vm.onPlayPauseClick()
        advanceUntilIdle()

        val sourceSlot = slot<PlaybackSource>()
        verify { controller.playFromSource(capture(sourceSlot), any()) }
        assertEquals("file:///test/track.mp3", sourceSlot.captured.streamUrl)
    }

    @Test
    fun killedProcess_podcastEpisode_serverFails_hasDownload_playsOffline() = runTest(dispatcher) {
        val controller = testController()
        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returnsMany listOf(
                AppResult.Success(testPlaybackSource()), // first call: populates item in refresh()
                AppResult.Error("Network unavailable")  // second call: onPlayPauseClick triggers offline fallback
            )
        }
        val prefs = testPrefs()
        prefs.setLastPlayedBookId("show1::ep1")

        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = testDownloadItem()),
            prefs = prefs
        )
        advanceUntilIdle()

        assertNotNull("item should be populated before play", vm.uiState.value.item)

        vm.onPlayPauseClick()
        advanceUntilIdle()

        val sourceSlot = slot<PlaybackSource>()
        verify { controller.playFromSource(capture(sourceSlot), any()) }
        assertEquals("file:///test/track.mp3", sourceSlot.captured.streamUrl)
    }

    @Test
    fun killedProcess_podcastEpisode_serverFails_noDownload_setsError() = runTest(dispatcher) {
        val controller = testController()
        val podcastRepo = mockk<PodcastRepository>(relaxed = true) {
            coEvery { fetchPodcastEpisodePlaybackSource("show1", "ep1") } returnsMany listOf(
                AppResult.Success(testPlaybackSource()),
                AppResult.Error("Network unavailable")
            )
        }
        val prefs = testPrefs()
        prefs.setLastPlayedBookId("show1::ep1")

        val vm = buildViewModel(
            controller = controller,
            podcastRepo = podcastRepo,
            downloadManager = testDownloadManager(download = null),
            prefs = prefs
        )
        advanceUntilIdle()

        assertNotNull("item should be populated before play", vm.uiState.value.item)

        vm.onPlayPauseClick()
        advanceUntilIdle()

        verify(exactly = 0) { controller.playFromSource(any(), any()) }
        assertNotNull(vm.uiState.value.errorMessage)
    }

    // endregion

    // region non-podcast path is not affected

    @Test
    fun nonPodcastFallback_callsPlayBook() = runTest(dispatcher) {
        val controller = testController()
        val bookItem = ContinueListeningItem(
            book = BookSummary(
                id = "book1",
                libraryId = "lib1",
                title = "A Book",
                authorName = "Author",
                narratorName = null,
                durationSeconds = 3600.0,
                coverUrl = null
            ),
            progressPercent = 0.3,
            currentTimeSeconds = 1080.0
        )
        val sessionRepo = mockk<SessionRepository>(relaxed = true) {
            every { observeBookProgressMutations() } returns emptyFlow<BookProgressMutation>()
            every { observeSessionState() } returns emptyFlow<SessionState>()
            coEvery { fetchMiniPlayerItem() } returns AppResult.Success(bookItem)
        }
        val vm = MiniPlayerViewModel(
            sessionRepository = sessionRepo,
            playbackController = controller,
            sessionPreferences = testPrefs(),
            podcastRepository = mockk(relaxed = true),
            bookDownloadManager = testDownloadManager()
        )
        advanceUntilIdle()

        vm.onPlayPauseClick()
        advanceUntilIdle()

        verify(exactly = 1) { controller.playBookFromPosition("book1", any()) }
    }

    // endregion

    // region helpers

    private fun testController(
        uiState: StateFlow<PlaybackUiState> = MutableStateFlow(PlaybackUiState())
    ): PlaybackController = mockk(relaxed = true) {
        every { this@mockk.uiState } returns uiState
        every { hasActivePlayer } returns false
        every { getCachedContinueListeningItem() } returns null
    }

    private fun testDownloadManager(
        compoundId: String = "show1::ep1",
        download: DownloadItem? = null
    ): BookDownloadManager = mockk(relaxed = true) {
        every { getCompletedDownloadForPodcast(compoundId) } returns download
        every { activeItems } returns emptyFlow()
    }

    private fun testDownloadItem() = DownloadItem(
        serverId = "server1",
        libraryId = "pod-lib",
        bookId = "show1::ep1",
        title = "Episode 1",
        authorName = "Show 1",
        coverUrl = null,
        durationSeconds = 1800.0,
        status = DownloadStatus.Completed,
        progressPercent = 100,
        localPath = "file:///test/track.mp3"
    )

    private fun testPlaybackSource() = PlaybackSource(
        book = testPodcastBook(),
        streamUrl = "https://server/episode.mp3"
    )

    private fun testPodcastBook(id: String = "show1::ep1") = BookSummary(
        id = id,
        libraryId = "pod-lib",
        title = "Episode 1",
        authorName = "Show 1",
        narratorName = null,
        durationSeconds = 1800.0,
        coverUrl = null
    )

    private fun testPrefs(): SessionPreferences {
        val dir = createTempDirectory(prefix = "mini-player-vm-test").toFile()
        dir.deleteOnExit()
        return SessionPreferences(
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { File(dir, "mini_player_test.preferences_pb") }
            )
        )
    }

    private fun testSessionRepository(): SessionRepository = mockk(relaxed = true) {
        every { observeBookProgressMutations() } returns emptyFlow<BookProgressMutation>()
        every { observeSessionState() } returns emptyFlow<SessionState>()
    }

    private fun buildViewModel(
        controller: PlaybackController = testController(),
        podcastRepo: PodcastRepository = mockk(relaxed = true),
        downloadManager: BookDownloadManager = testDownloadManager(),
        prefs: SessionPreferences = testPrefs()
    ): MiniPlayerViewModel {
        return MiniPlayerViewModel(
            sessionRepository = testSessionRepository(),
            playbackController = controller,
            sessionPreferences = prefs,
            podcastRepository = podcastRepo,
            bookDownloadManager = downloadManager
        )
    }

    // endregion
}
