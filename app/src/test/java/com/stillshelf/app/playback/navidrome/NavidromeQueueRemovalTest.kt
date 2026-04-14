package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromeQueueRemovalTest {

    @Test
    fun removesTrackAfterCurrentWithoutChangingCurrentIndex() {
        val queue = (0 until 5).map(::track)

        val result = removeNavidromeTrackFromQueue(
            queue = queue,
            currentIndex = 2,
            removeIndex = 4
        )

        requireNotNull(result)
        assertEquals(listOf("track-0", "track-1", "track-2", "track-3"), result.queue.map { it.id })
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun removesTrackBeforeCurrentAndShiftsCurrentIndexBackOne() {
        val queue = (0 until 5).map(::track)

        val result = removeNavidromeTrackFromQueue(
            queue = queue,
            currentIndex = 3,
            removeIndex = 1
        )

        requireNotNull(result)
        assertEquals(listOf("track-0", "track-2", "track-3", "track-4"), result.queue.map { it.id })
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun rejectsRemovingCurrentTrack() {
        val queue = (0 until 4).map(::track)

        val result = removeNavidromeTrackFromQueue(
            queue = queue,
            currentIndex = 2,
            removeIndex = 2
        )

        assertNull(result)
    }

    @Test
    fun removesByIndexEvenWhenQueueContainsDuplicateTracks() {
        val duplicate = track(7)
        val queue = listOf(track(0), duplicate, duplicate, track(3))

        val result = removeNavidromeTrackFromQueue(
            queue = queue,
            currentIndex = 3,
            removeIndex = 1
        )

        requireNotNull(result)
        assertEquals(listOf("track-0", "track-7", "track-3"), result.queue.map { it.id })
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun rejectsInvalidRemovalIndex() {
        val queue = listOf(track(0), track(1))

        val result = removeNavidromeTrackFromQueue(
            queue = queue,
            currentIndex = 0,
            removeIndex = 99
        )

        assertNull(result)
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
