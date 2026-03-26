package com.stillshelf.app.ui.screens.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeQueuePreviewTest {

    @Test
    fun previewStartsAtCurrentTrackAndCapsUpcomingItems() {
        val queue = (0 until 80).map(::testTrack)

        val preview = buildNavidromeQueuePreview(
            queue = queue,
            currentTrack = queue[10],
            currentIndex = 10
        )

        assertEquals(70, preview.totalCount)
        assertEquals(50, preview.items.size)
        assertEquals(10, preview.items.first().queueIndex)
        assertEquals("track-10", preview.items.first().track.id)
        assertEquals(59, preview.items.last().queueIndex)
    }

    @Test
    fun previewFallsBackToCurrentTrackWhenQueueIsEmpty() {
        val currentTrack = testTrack(7)

        val preview = buildNavidromeQueuePreview(
            queue = emptyList(),
            currentTrack = currentTrack,
            currentIndex = -1
        )

        assertEquals(1, preview.totalCount)
        assertEquals(1, preview.items.size)
        assertEquals("track-7", preview.items.single().track.id)
    }

    @Test
    fun previewRecoversFromStaleCurrentIndexByMatchingCurrentTrack() {
        val queue = (0 until 8).map(::testTrack)

        val preview = buildNavidromeQueuePreview(
            queue = queue,
            currentTrack = queue[5],
            currentIndex = 999
        )

        assertEquals(3, preview.totalCount)
        assertEquals(listOf(5, 6, 7), preview.items.map { it.queueIndex })
        assertTrue(preview.items.first().track.id == "track-5")
    }

    private fun testTrack(index: Int): NavidromeTrack {
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
