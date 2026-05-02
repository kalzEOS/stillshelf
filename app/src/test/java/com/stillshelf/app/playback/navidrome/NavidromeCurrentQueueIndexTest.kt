package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeCurrentQueueIndexTest {

    @Test
    fun prefersPlayerIndexWhenItIsValid() {
        val queue = (0 until 4).map(::track)

        val resolved = resolveNavidromeCurrentQueueIndex(
            queue = queue,
            playerIndex = 2,
            stateIndex = 1,
            currentTrackId = "track-0"
        )

        assertEquals(2, resolved)
    }

    @Test
    fun fallsBackToStateIndexWhenPlayerIndexIsInvalid() {
        val queue = (0 until 4).map(::track)

        val resolved = resolveNavidromeCurrentQueueIndex(
            queue = queue,
            playerIndex = 99,
            stateIndex = 1,
            currentTrackId = "track-0"
        )

        assertEquals(1, resolved)
    }

    @Test
    fun fallsBackToCurrentTrackIdWhenIndicesAreInvalid() {
        val queue = (0 until 4).map(::track)

        val resolved = resolveNavidromeCurrentQueueIndex(
            queue = queue,
            playerIndex = -1,
            stateIndex = -1,
            currentTrackId = "track-2"
        )

        assertEquals(2, resolved)
    }

    @Test
    fun doesNotGuessWhenTrackIdMatchesMultipleQueueEntries() {
        val duplicate = track(2)
        val queue = listOf(track(0), duplicate, duplicate, track(3))

        val resolved = resolveNavidromeCurrentQueueIndex(
            queue = queue,
            playerIndex = -1,
            stateIndex = -1,
            currentTrackId = duplicate.id
        )

        assertEquals(-1, resolved)
    }

    @Test
    fun returnsMinusOneWhenNothingMatches() {
        val queue = (0 until 2).map(::track)

        val resolved = resolveNavidromeCurrentQueueIndex(
            queue = queue,
            playerIndex = null,
            stateIndex = null,
            currentTrackId = "missing"
        )

        assertEquals(-1, resolved)
    }

    private fun track(index: Int): NavidromeTrack {
        return NavidromeTrack(
            id = "track-$index",
            title = "Track $index",
            artistName = "Artist",
            albumName = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            trackNumber = index + 1,
            durationSeconds = 180,
            coverUrl = null,
            streamUrl = "https://example.com/$index.mp3",
            formatLabel = "MP3",
            bitRateKbps = 320
        )
    }
}
