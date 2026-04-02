package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateSummaryResolverTest {

    @Test
    fun shouldTreatPlaybackAsFinished_returnsTrueWhenBookAlreadyMarkedFinished() {
        assertTrue(
            shouldTreatPlaybackAsFinished(
                bookIsFinished = true,
                positionMs = 0L,
                durationMs = null,
                bookDurationSeconds = null
            )
        )
    }

    @Test
    fun shouldTreatPlaybackAsFinished_usesPlaybackDurationThreshold() {
        assertTrue(
            shouldTreatPlaybackAsFinished(
                bookIsFinished = false,
                positionMs = 995_000L,
                durationMs = 1_000_000L,
                bookDurationSeconds = null
            )
        )
    }

    @Test
    fun shouldTreatPlaybackAsFinished_returnsFalseBelowThreshold() {
        assertFalse(
            shouldTreatPlaybackAsFinished(
                bookIsFinished = false,
                positionMs = 994_000L,
                durationMs = 1_000_000L,
                bookDurationSeconds = null
            )
        )
    }

    @Test
    fun buildContinueListeningItem_usesFallbackDurationWhenBookSummaryIsMissingIt() {
        val item = buildContinueListeningItem(
            book = testBook(durationSeconds = null),
            positionMs = 150_000L,
            fallbackDurationMs = 300_000L
        )

        assertEquals(150.0, item.currentTimeSeconds)
        assertEquals(0.5, item.progressPercent)
    }

    @Test
    fun buildContinueListeningItem_clampsProgressPercentAtOneHundredPercent() {
        val item = buildContinueListeningItem(
            book = testBook(durationSeconds = 300.0),
            positionMs = 450_000L,
            fallbackDurationMs = null
        )

        assertEquals(1.0, item.progressPercent)
    }

    private fun testBook(durationSeconds: Double?) = BookSummary(
        id = "book",
        libraryId = "library",
        title = "Book",
        authorName = "Author",
        narratorName = null,
        durationSeconds = durationSeconds,
        coverUrl = null
    )
}
