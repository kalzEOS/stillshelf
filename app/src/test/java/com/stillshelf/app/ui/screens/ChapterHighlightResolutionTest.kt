package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterHighlightResolutionTest {

    @Test
    fun findActiveChapterIndex_doesNotAdvanceBeforeBoundary() {
        val index = findActiveChapterIndex(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 60.0),
                BookChapter(title = "Chapter 2", startSeconds = 60.0, endSeconds = 120.0)
            ),
            positionSeconds = 59.3
        )

        assertEquals(0, index)
    }

    @Test
    fun findActiveChapterIndex_prefersNewlyStartedChapterAfterBoundary() {
        val index = findActiveChapterIndex(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 61.0),
                BookChapter(title = "Chapter 2", startSeconds = 60.0, endSeconds = 120.0)
            ),
            positionSeconds = 60.1
        )

        assertEquals(1, index)
    }

    @Test
    fun findActiveChapterIndex_advancesWhenOverlappingChapterActuallyEnds() {
        val index = findActiveChapterIndex(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 61.0),
                BookChapter(title = "Chapter 2", startSeconds = 60.0, endSeconds = 120.0)
            ),
            positionSeconds = 61.0
        )

        assertEquals(1, index)
    }
}
