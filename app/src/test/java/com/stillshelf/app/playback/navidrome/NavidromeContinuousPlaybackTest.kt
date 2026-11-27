package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.*
import org.junit.Test

class NavidromeContinuousPlaybackTest {
    @Test
    fun prefersFreshSongsWithoutFillingTheBatchWithRecentSongs() {
        val result = selectNavidromeContinuationTracks(
            listOf(track("last"), track("recent"), track("fresh"), track("fresh")),
            referenceId = "last", recentIds = setOf("last", "recent")
        )
        assertEquals(listOf("fresh"), result.map { it.id })
    }

    @Test
    fun smallLibrariesCanRepeatOlderSongsButNeverTheReference() {
        val result = selectNavidromeContinuationTracks(
            listOf(track("last"), track("older")), "last", setOf("last", "older")
        )
        assertEquals(listOf("older"), result.map { it.id })
        assertTrue(selectNavidromeContinuationTracks(listOf(track("last")), "last", setOf("last")).isEmpty())
    }

    @Test
    fun rejectsUnplayableResultsAndBoundsQueueSize() {
        val candidates = (0..50).map { track("song-$it") } +
            listOf(track(""), track("radio:1"), track("missing-url").copy(streamUrl = ""))
        val result = selectNavidromeContinuationTracks(candidates, "last", emptySet())
        assertEquals(20, result.size)
        assertTrue(result.all { it.id.startsWith("song-") })
        assertEquals(20, result.distinctBy { it.id }.size)
    }

    @Test
    fun onlyOneRequestCanBePendingAndCanceledResponsesStayInvalid() {
        val request = NavidromeContinuationRequest()
        val stoppedRequest = requireNotNull(request.begin())
        assertNull(request.begin())
        request.invalidate()
        val newerRequest = requireNotNull(request.begin())
        assertFalse(request.isCurrent(stoppedRequest))
        request.finish(stoppedRequest)
        assertTrue(request.isCurrent(newerRequest))
        request.finish(newerRequest)
        val nextRequest = requireNotNull(request.begin())
        assertFalse(request.isCurrent(newerRequest))
        assertTrue(request.isCurrent(nextRequest))
    }

    private fun track(id: String) = NavidromeTrack(
        id = id, title = id, artistName = "Artist", albumName = "Album", albumId = "album",
        artistId = "artist", trackNumber = null, durationSeconds = 180, coverUrl = null,
        streamUrl = "https://example.com/$id", formatLabel = "MP3", bitRateKbps = 320
    )
}
