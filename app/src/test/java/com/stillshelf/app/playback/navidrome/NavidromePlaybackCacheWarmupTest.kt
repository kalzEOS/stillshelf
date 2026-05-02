package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavidromePlaybackCacheWarmupTest {
    @Test
    fun normalizeNavidromePlaybackWarmupTracks_filtersInvalidAndRadioTracks() {
        val tracks = listOf(
            track(id = "track-1", streamUrl = "https://example.com/1.mp3"),
            track(id = "track-1", streamUrl = "https://example.com/1-duplicate.mp3"),
            track(id = "track-2", streamUrl = ""),
            track(id = "radio:stream", streamUrl = "https://example.com/live.mp3"),
            track(id = "track-3", streamUrl = "https://example.com/3.mp3")
        )

        val normalized = normalizeNavidromePlaybackWarmupTracks(tracks)

        assertEquals(listOf("track-1", "track-3"), normalized.map { it.id })
    }

    @Test
    fun buildNavidromePlaybackWarmupSignature_isOrderSensitive() {
        val forward = buildNavidromePlaybackWarmupSignature(
            listOf(track("track-1"), track("track-2"))
        )
        val reversed = buildNavidromePlaybackWarmupSignature(
            listOf(track("track-2"), track("track-1"))
        )

        assertEquals("track-1|track-2", forward)
        assertFalse(forward == reversed)
    }

    @Test
    fun buildNavidromePlaybackWarmupSignature_changesAcrossConnectionContexts() {
        val wifiSignature = buildNavidromePlaybackWarmupSignature(
            tracks = listOf(track("track-1"), track("track-2")),
            connectionSignature = "srv|https://lan.example.com|Local|Auto"
        )
        val cellularSignature = buildNavidromePlaybackWarmupSignature(
            tracks = listOf(track("track-1"), track("track-2")),
            connectionSignature = "srv|https://wan.example.com|Remote|Auto"
        )

        assertFalse(wifiSignature == cellularSignature)
    }

    @Test
    fun selectNavidromePlaybackWarmupTracks_capsToUpcomingWindow() {
        val tracks = (0 until 30).map { track("track-$it") }

        val selected = selectNavidromePlaybackWarmupTracks(
            tracks = tracks,
            currentIndex = 10,
            trackLimit = 5
        )

        assertEquals(listOf("track-10", "track-11", "track-12", "track-13", "track-14"), selected.map { it.id })
    }

    @Test
    fun chooseNavidromePlaybackUri_prefersLocalCacheWhenAllowed() {
        val resolved = chooseNavidromePlaybackUri(
            streamUrl = "https://example.com/stream.mp3",
            localPlaybackUri = "file:///data/user/0/app/cache/track.mp3",
            forceRemote = false
        )

        assertEquals("file:///data/user/0/app/cache/track.mp3", resolved)
    }

    @Test
    fun chooseNavidromePlaybackUri_respectsForcedRemotePlayback() {
        val resolved = chooseNavidromePlaybackUri(
            streamUrl = "https://example.com/stream.mp3",
            localPlaybackUri = "file:///data/user/0/app/cache/track.mp3",
            forceRemote = true
        )

        assertEquals("https://example.com/stream.mp3", resolved)
    }

    private fun track(id: String, streamUrl: String = "https://example.com/$id.mp3"): NavidromeTrack {
        return NavidromeTrack(
            id = id,
            title = "Title $id",
            artistName = "Artist",
            albumName = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            trackNumber = 1,
            durationSeconds = 180,
            coverUrl = null,
            streamUrl = streamUrl,
            formatLabel = "MP3",
            bitRateKbps = 320
        )
    }
}
