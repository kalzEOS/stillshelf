package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackgroundPolicyTest {

    @Test
    fun shouldKeepPlaybackSessionActive_requiresLoadedBook() {
        assertFalse(
            shouldKeepPlaybackSessionActive(
                book = null,
                isPlaying = true
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_requiresActivePlayback() {
        assertFalse(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                isPlaying = false
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_allowsForegroundSessionOnlyDuringPlayback() {
        assertTrue(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                isPlaying = true
            )
        )
    }

    private fun sampleBook(): BookSummary {
        return BookSummary(
            id = "book-1",
            libraryId = "library-1",
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 3600.0,
            coverUrl = null
        )
    }
}
