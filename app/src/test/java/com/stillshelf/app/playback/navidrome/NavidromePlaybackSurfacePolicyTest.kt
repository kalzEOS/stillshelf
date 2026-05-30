package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromePlaybackSurfacePolicyTest {

    @Test
    fun keepsSurfaceActiveWhenTrackStillExists() {
        assertTrue(
            shouldKeepNavidromePlaybackSurfaceActive(
                currentTrack = track(),
                hasActivePlayer = false
            )
        )
    }

    @Test
    fun keepsSurfaceActiveWhenPlayerStillExists() {
        assertTrue(
            shouldKeepNavidromePlaybackSurfaceActive(
                currentTrack = null,
                hasActivePlayer = true
            )
        )
    }

    @Test
    fun clearsSurfaceOnlyWhenBothTrackAndPlayerAreGone() {
        assertFalse(
            shouldKeepNavidromePlaybackSurfaceActive(
                currentTrack = null,
                hasActivePlayer = false
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
