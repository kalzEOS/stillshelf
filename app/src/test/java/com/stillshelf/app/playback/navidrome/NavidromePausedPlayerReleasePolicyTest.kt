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
                playbackState = Player.STATE_READY
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
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun returnsFalseForRadioTrack() {
        assertFalse(
            shouldScheduleNavidromePausedPlayerRelease(
                currentTrack = track(id = "radio:station"),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY
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
