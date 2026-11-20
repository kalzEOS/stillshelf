package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningPlaybackTrackerTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "reader", token = "token")
    private val state = MutableStateFlow(ListeningSettings(loaded = true, connection = connection))
    private val settings = mockk<ListeningSettingsStore> {
        every { this@mockk.state } returns this@ListeningPlaybackTrackerTest.state
        coEvery { load() } returns Unit
    }
    private val queue = mockk<ListeningSubmissionQueue>(relaxed = true)
    private val repository = mockk<NavidromeRepository> {
        every { observeSession() } returns MutableStateFlow(null)
        coEvery { currentPlaybackSessionKey() } returns "session"
        coEvery { currentAuth(any()) } returns mockk(relaxed = true)
    }
    private val native = mockk<NavidromeListeningClient>(relaxed = true)
    private val external = mockk<ListeningProviderClient>(relaxed = true)
    private val clients = mockk<ListeningClients> { every { forProvider(any()) } returns external }
    private val track = NavidromeTrack("track", "Song", "Artist", "Album", "album", "artist", null, 60, null, "stream", null, null)

    @Test fun localRecordingIsSampledIndependentlyAndNeverAddsNativeOrExternalSubmissions() = runTest {
        val local = mockk<LocalListeningRecorder>(relaxed = true)
        state.value = state.value.copy(localRecordingEnabled = true, scrobblingEnabled = false, showStatistics = false)
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients, local)
        repeat(40) { tracker.sample(track, true); ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        verify(exactly = 40) { local.sample(track, true, "session", true, any(), any(), null) }
        coVerify(exactly = 1) { queue.enqueue(any(), "session", "navidrome") }
        coVerify(exactly = 0) { queue.enqueue(any(), "session", connection.revision) }
        coVerify(exactly = 0) { external.playingNow(any(), any()) }
    }

    @Test fun progressCheckpointsDoNotResetEitherQualificationOrRepeatNowPlaying() = runTest {
        val session = com.stillshelf.app.core.model.NavidromeSession("server", "Server", "https://server.invalid", "user")
        val sessions = MutableSharedFlow<com.stillshelf.app.core.model.NavidromeSession?>(replay = 1).also { it.tryEmit(session) }
        every { repository.observeSession() } returns sessions
        state.value = state.value.copy(scrobblingEnabled = true)
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients)
        repeat(70) { second ->
            if (second > 0 && second % 15 == 0) sessions.emit(session)
            tracker.sample(track, true)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        }
        coVerify(exactly = 1) { queue.enqueue(any(), "session", "navidrome") }
        coVerify(exactly = 1) { queue.enqueue(any(), "session", connection.revision) }
        coVerify(exactly = 1) { native.report(any(), any(), false) }
        coVerify(exactly = 1) { external.playingNow(connection, any()) }
    }

    @Test fun changingLibraryWithTheSameSessionResetsBothAccumulators() = runTest {
        val sessions = MutableSharedFlow<com.stillshelf.app.core.model.NavidromeSession?>(replay = 1).also { it.tryEmit(null) }
        every { repository.observeSession() } returns sessions
        var key = "first-library"
        coEvery { repository.currentPlaybackSessionKey() } answers { key }
        state.value = state.value.copy(scrobblingEnabled = true)
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients)
        suspend fun sample(seconds: Int) { repeat(seconds) {
            tracker.sample(track, true); ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        } }
        sample(20)
        key = "second-library"
        sessions.emit(null)
        sample(20)
        coVerify(exactly = 0) { queue.enqueue(any(), any(), any()) }
        sample(15)
        coVerify(exactly = 1) { queue.enqueue(any(), key, "navidrome") }
        coVerify(exactly = 1) { queue.enqueue(any(), key, connection.revision) }
    }

    @Test fun directOffStillReportsNativeNowPlayingAndExactlyOneQualifiedListen() = runTest {
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients)
        repeat(40) { tracker.sample(track, true); ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        coVerify(exactly = 1) { native.report(any(), any(), false) }
        coVerify(exactly = 1) { queue.enqueue(any(), "session", "navidrome") }
        coVerify(exactly = 0) { external.playingNow(any(), any()) }
        coVerify(exactly = 0) { queue.enqueue(any(), any(), connection.revision) }
    }

    @Test fun disablingDirectMidTrackDoesNotResetNativeQualification() = runTest {
        state.value = state.value.copy(scrobblingEnabled = true)
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients)
        repeat(40) { index ->
            if (index == 15) state.value = state.value.copy(scrobblingEnabled = false)
            tracker.sample(track, true)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        }
        coVerify(exactly = 1) { external.playingNow(connection, any()) }
        coVerify(exactly = 1) { queue.enqueue(any(), "session", "navidrome") }
        coVerify(exactly = 0) { queue.enqueue(any(), any(), connection.revision) }
    }

    @Test fun statisticsSwitchKeepsSelectedSenderAndRepeatStartsANewOccurrence() = runTest {
        state.value = state.value.copy(connections = listOf(connection), multipleConnections = true,
            sendingConnectionId = connection.revision, scrobblingEnabled = true)
        val tracker = ListeningPlaybackTracker(settings, queue, repository, native, clients)
        repeat(2) {
            repeat(40) { index ->
                if (index == 10) state.value = state.value.copy(connection = null, statisticsSource = StatisticsSource.NAVIDROME)
                tracker.sample(track, true)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            }
            tracker.transition()
        }
        coVerify(exactly = 2) { queue.enqueue(any(), "session", "navidrome") }
        coVerify(exactly = 2) { queue.enqueue(any(), "session", connection.revision) }
        coVerify(exactly = 2) { external.playingNow(connection, any()) }
    }
}
