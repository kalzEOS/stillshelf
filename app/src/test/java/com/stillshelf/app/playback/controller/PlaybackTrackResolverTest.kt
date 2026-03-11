package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTrackResolverTest {

    @Test
    fun resolvePlaybackTrackSelection_picksMatchingTrackAndLocalSeek() {
        val selection = resolvePlaybackTrackSelection(
            source = testSource(),
            positionMs = 65_000L
        )

        assertEquals("track-2", selection.streamUrl)
        assertEquals(60_000L, selection.trackStartOffsetMs)
        assertEquals(5_000L, selection.localSeekMs)
    }

    @Test
    fun resolveNextTrackStartMs_returnsFollowingTrackStart() {
        val nextTrackStartMs = resolveNextTrackStartMs(
            tracks = testSource().tracks,
            currentTrackStartOffsetMs = 60_000L
        )

        assertEquals(120_000L, nextTrackStartMs)
    }

    private fun testSource() = PlaybackSource(
        book = BookSummary(
            id = "book",
            libraryId = "library",
            title = "Book",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 180.0,
            coverUrl = null
        ),
        streamUrl = "track-1",
        tracks = listOf(
            PlaybackTrack(startOffsetSeconds = 0.0, durationSeconds = 60.0, streamUrl = "track-1"),
            PlaybackTrack(startOffsetSeconds = 60.0, durationSeconds = 60.0, streamUrl = "track-2"),
            PlaybackTrack(startOffsetSeconds = 120.0, durationSeconds = 60.0, streamUrl = "track-3")
        )
    )
}
