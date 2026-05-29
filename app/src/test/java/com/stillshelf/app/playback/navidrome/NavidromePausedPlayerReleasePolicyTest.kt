package com.stillshelf.app.playback.navidrome

import androidx.media3.common.Player
import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromePausedPlayerReleasePolicyTest {

    @Test
    fun returnsFalseWhenPlaybackIsStillIntended() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun returnsTrueForActuallyPausedTrack() {
        assertTrue(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun returnsTrueForPausedRadioTrack() {
        assertTrue(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(id = "radio:station"),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun returnsTrueWhenQueueEndedNaturally() {
        assertTrue(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun returnsFalseWhenEndedWithRepeatModeAll() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )
    }

    @Test
    fun returnsFalseWhenEndedWithRepeatModeOne() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_ENDED,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_ONE
            )
        )
    }

    @Test
    fun returnsFalseWhenAppIsInForeground() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                appInForeground = true,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun returnsFalseWhenEndedButStillBuffering() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
                appInForeground = false,
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    private fun track(id: String = "track-1"): NavidromeTrack {
        return NavidromeTrack(
            id = id,
            title = "Track",
            artistName = "Artist",
            albumName = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            trackNumber = 1,
            durationSeconds = 180,
            coverUrl = null,
            streamUrl = "https://example.com/stream.mp3",
            formatLabel = null,
            bitRateKbps = null
        )
    }
}
