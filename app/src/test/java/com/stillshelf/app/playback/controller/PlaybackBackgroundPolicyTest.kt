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
                hasActivePlayer = true
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_requiresActivePlayer() {
        assertFalse(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                hasActivePlayer = false
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_allowsForegroundSessionWhilePlayerExists() {
        assertTrue(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                hasActivePlayer = true
            )
        )
    }

    @Test
    fun shouldContinuePlaybackSyncRetry_requiresPlaybackToStillBeActiveForSameBook() {
        assertFalse(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-1",
                isPlaybackActive = false
            )
        )
        assertFalse(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-2",
                isPlaybackActive = true
            )
        )
        assertTrue(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-1",
                isPlaybackActive = true
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
