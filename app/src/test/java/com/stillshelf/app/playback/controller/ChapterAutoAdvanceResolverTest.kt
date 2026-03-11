package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterAutoAdvanceResolverTest {

    @Test
    fun resolveNextChapterStartMs_advancesWhenFinishedDurationDriftsPastNextChapterStart() {
        val nextStartMs = ChapterAutoAdvanceResolver.resolveNextChapterStartMs(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = null),
                BookChapter(title = "Chapter 2", startSeconds = 100.0, endSeconds = null),
                BookChapter(title = "Chapter 3", startSeconds = 200.0, endSeconds = null)
            ),
            finishedStreamDurationMs = 100_500L,
            bookDurationMs = 300_000L
        )

        assertEquals(100_000L, nextStartMs)
    }

    @Test
    fun resolveNextChapterStartMs_advancesWhenFinishedDurationStopsShortOfBoundary() {
        val nextStartMs = ChapterAutoAdvanceResolver.resolveNextChapterStartMs(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 100.0),
                BookChapter(title = "Chapter 2", startSeconds = 100.0, endSeconds = 205.0)
            ),
            finishedStreamDurationMs = 99_300L,
            bookDurationMs = 205_000L
        )

        assertEquals(100_000L, nextStartMs)
    }

    @Test
    fun resolveNextChapterStartMs_returnsNullAtFinalChapterBoundary() {
        val nextStartMs = ChapterAutoAdvanceResolver.resolveNextChapterStartMs(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 100.0),
                BookChapter(title = "Chapter 2", startSeconds = 100.0, endSeconds = 230.0)
            ),
            finishedStreamDurationMs = 230_500L,
            bookDurationMs = 230_000L
        )

        assertNull(nextStartMs)
    }

    @Test
    fun resolveNextChapterStartMs_returnsNullWhenNoBoundaryMatchesCompletion() {
        val nextStartMs = ChapterAutoAdvanceResolver.resolveNextChapterStartMs(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 100.0),
                BookChapter(title = "Chapter 2", startSeconds = 100.0, endSeconds = 200.0),
                BookChapter(title = "Chapter 3", startSeconds = 200.0, endSeconds = 300.0)
            ),
            finishedStreamDurationMs = 150_000L,
            bookDurationMs = 300_000L
        )

        assertNull(nextStartMs)
    }
}
