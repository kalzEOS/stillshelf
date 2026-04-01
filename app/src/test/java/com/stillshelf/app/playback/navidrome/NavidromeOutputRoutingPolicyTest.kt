package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeOutputRoutingPolicyTest {

    @Test
    fun isAutoPreferredWiredRoute_returnsTrueForCarFriendlyWiredRoutes() {
        assertTrue(isAutoPreferredWiredRoute("wired"))
        assertTrue(isAutoPreferredWiredRoute("usb"))
        assertTrue(isAutoPreferredWiredRoute("hdmi"))
        assertTrue(isAutoPreferredWiredRoute("dock"))
        assertTrue(isAutoPreferredWiredRoute("line"))
    }

    @Test
    fun isAutoPreferredWiredRoute_returnsFalseForBluetoothAndSpeakerRoutes() {
        assertFalse(isAutoPreferredWiredRoute(null))
        assertFalse(isAutoPreferredWiredRoute("speaker"))
        assertFalse(isAutoPreferredWiredRoute("bt:car"))
    }

    @Test
    fun isNavidromeOutputSwitchInFlight_returnsTrueBeforeSuppressionDeadline() {
        assertTrue(isNavidromeOutputSwitchInFlight(nowElapsedMs = 999L, suppressRefreshRoutingUntilElapsedMs = 1_000L))
        assertFalse(isNavidromeOutputSwitchInFlight(nowElapsedMs = 1_000L, suppressRefreshRoutingUntilElapsedMs = 1_000L))
    }

    @Test
    fun navidromeTrackSnapshotRoundTrip_preservesStreamUrl() {
        val track = NavidromeTrack(
            id = "track-1",
            title = "Song",
            artistName = "Artist",
            albumName = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            trackNumber = 2,
            durationSeconds = 180,
            coverUrl = "https://example.com/cover.jpg",
            streamUrl = "https://example.com/stream.mp3",
            formatLabel = "MP3",
            bitRateKbps = 320
        )

        val parsed = track.toSnapshotPayload().toTrack()

        assertEquals(track, parsed)
    }

    @Test
    fun navidromeTrackSnapshotPayloadToTrack_returnsNullWhenTrackIdMissing() {
        val payload = NavidromeTrackSnapshotPayload(
            id = "",
            title = "Song",
            artistName = "Artist",
            albumName = "Album",
            albumId = null,
            artistId = null,
            trackNumber = null,
            durationSeconds = null,
            coverUrl = null,
            streamUrl = "https://example.com/stream.mp3",
            formatLabel = null,
            bitRateKbps = null
        )

        val parsed = payload.toTrack()

        assertNull(parsed)
    }
}
