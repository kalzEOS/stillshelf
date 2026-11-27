package com.stillshelf.app.playback.navidrome

import android.support.v4.media.session.MediaSessionCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NavidromeContinuousPlaybackControllerTest {
    private lateinit var controller: NavidromePlayerController
    private lateinit var player: ExoPlayer
    private lateinit var repository: NavidromeRepository
    private val preferencesState = MutableStateFlow(SessionPreferenceState(
        activeServerId = null, activeLibraryId = null,
        selectedBackend = BackendProvider.NAVIDROME, activeNavidromeServerId = "server",
        navidromeContinuousPlaybackEnabled = true
    ))
    private val response = CompletableDeferred<AppResult<List<NavidromeTrack>>>()
    private val reference = track("last", "https://lan.example/last")
    private val next = track("next", "https://remote.example/next")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val preferences = mockk<SessionPreferences>(relaxed = true) {
            every { state } returns preferencesState
            coEvery { getCachedNavidromePlayback() } returns null
        }
        repository = mockk<NavidromeRepository>(relaxed = true) {
            every { observeActiveConnectionStatus() } returns flowOf(null)
            coEvery { currentPlaybackSessionKey() } returns null
            coEvery { refreshPlayableTracks(any()) } returns AppResult.Error("Cache warmup disabled in this test")
            coEvery { fetchContinuousPlaybackTracks(any(), any()) } coAnswers { response.await() }
        }
        controller = NavidromePlayerController(
            RuntimeEnvironment.getApplication(), preferences, mockk(relaxed = true),
            repository, mockk(relaxed = true), mockk(relaxed = true)
        )
        player = mockk(relaxed = true) {
            every { playbackState } returns Player.STATE_ENDED
            every { playWhenReady } returns true
            every { isPlaying } returns false
            every { playerError } returns null
            every { currentMediaItemIndex } returns 0
            every { currentPosition } returns 180_000
            every { duration } returns 180_000
        }
        setField("appInForeground", true)
    }

    @After
    fun tearDown() {
        field<CoroutineScope>("scope").cancel()
        field<MediaSessionCompat>("mediaSession").release()
        Dispatchers.resetMain()
    }

    @Test
    fun urlRefreshWhileLoadingDoesNotDiscardContinuation() = runTest {
        runCurrent() // Start preference observation before playback ends.
        endQueue()
        runCurrent()

        // Connection/cache refreshes replace track objects without editing the logical queue.
        val refreshed = reference.copy(streamUrl = "https://remote.example/last")
        setField("queueTracks", listOf(refreshed))
        playerState().value = controller.state.value.copy(queue = listOf(refreshed), currentTrack = refreshed)
        setField("playbackCacheWarmupSignature", buildNavidromePlaybackWarmupSignature(listOf(next)))
        response.complete(AppResult.Success(listOf(next)))
        runCurrent()

        assertEquals(listOf("next"), controller.state.value.queue.map { it.id })
        assertEquals(next.streamUrl, controller.state.value.currentTrack?.streamUrl)
        verify(exactly = 1) { player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), 0, 0L) }
    }

    @Test
    fun manualQueueAppendStillDiscardsPendingRecommendations() = runTest {
        runCurrent()
        endQueue()
        runCurrent()
        val manual = track("manual", "https://remote.example/manual")
        setField("playbackCacheWarmupSignature", buildNavidromePlaybackWarmupSignature(listOf(reference, manual)))
        controller.appendTracksToQueue(listOf(manual))
        response.complete(AppResult.Success(listOf(next)))
        runCurrent()

        assertEquals(listOf("last", "manual"), controller.state.value.queue.map { it.id })
        verify(exactly = 0) { player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), any(), any()) }
    }

    @Test
    fun disabledFeatureDoesNotFetchRecommendations() = runTest {
        preferencesState.value = preferencesState.value.copy(navidromeContinuousPlaybackEnabled = false)
        runCurrent()
        endQueue()
        runCurrent()
        coVerify(exactly = 0) { repository.fetchContinuousPlaybackTracks(any(), any()) }
    }

    @Test
    fun repeatModeDoesNotFetchRecommendations() = runTest {
        runCurrent()
        controller.cycleRepeatMode()
        endQueue()
        runCurrent()
        coVerify(exactly = 0) { repository.fetchContinuousPlaybackTracks(any(), any()) }
    }

    @Test
    fun pausedPlaybackDoesNotFetchRecommendations() = runTest {
        runCurrent()
        every { player.playWhenReady } returns false
        endQueue()
        runCurrent()
        coVerify(exactly = 0) { repository.fetchContinuousPlaybackTracks(any(), any()) }
    }

    @Test
    fun disablingFeatureWhileLoadingDiscardsRecommendations() = runTest {
        runCurrent()
        endQueue()
        runCurrent()
        preferencesState.value = preferencesState.value.copy(navidromeContinuousPlaybackEnabled = false)
        runCurrent()
        response.complete(AppResult.Success(listOf(next)))
        runCurrent()
        assertEquals(listOf("last"), controller.state.value.queue.map { it.id })
        verify(exactly = 0) { player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), any(), any()) }
    }

    @Test
    fun recommendationTimeoutLeavesTheFinishedQueueWithoutRetrying() = runTest {
        runCurrent()
        endQueue()
        runCurrent()
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(listOf("last"), controller.state.value.queue.map { it.id })
        coVerify(exactly = 1) { repository.fetchContinuousPlaybackTracks(any(), any()) }
        verify(exactly = 0) { player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), any(), any()) }
    }

    @Test
    fun stopStillDiscardsPendingRecommendations() = runTest {
        runCurrent()
        endQueue()
        runCurrent()
        controller.stop()
        response.complete(AppResult.Success(listOf(next)))
        runCurrent()

        assertTrue(controller.state.value.queue.isEmpty())
        verify(exactly = 0) { player.setMediaItems(any<List<androidx.media3.common.MediaItem>>(), any(), any()) }
    }

    private fun endQueue() {
        setField("queueTracks", listOf(reference))
        setField("player", player)
        setField("continuousPlaybackArmed", true)
        playerState().value = NavidromePlayerState(queue = listOf(reference), currentTrack = reference, currentIndex = 0)
        field<Player.Listener>("playerListener").onPlaybackStateChanged(Player.STATE_ENDED)
    }

    private fun playerState() = field<MutableStateFlow<NavidromePlayerState>>("mutableState")

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T = NavidromePlayerController::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(controller) as T

    private fun setField(name: String, value: Any) {
        NavidromePlayerController::class.java.getDeclaredField(name).apply { isAccessible = true }.set(controller, value)
    }

    private fun track(id: String, url: String) = NavidromeTrack(
        id, id, "Artist", "Album", "album", "artist", null, 180, null, url, "MP3", 320
    )
}
