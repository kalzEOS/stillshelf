package com.stillshelf.app.domain.usecase

import com.stillshelf.app.core.model.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipIntroOutroUseCaseTest {

    @Test
    fun resolve_returnsNoChaptersWhenEmpty() {
        val result = SkipIntroOutroUseCase.resolve(
            chapters = emptyList(),
            currentPositionSeconds = 12.0,
            bookDurationSeconds = 300.0
        )

        assertNull(result.targetSeconds)
        assertEquals(SkipIntroOutroFailureReason.NoChapters, result.failureReason)
    }

    @Test
    fun resolve_returnsNoMarkerWhenCurrentChapterIsNotIntroOutro() {
        val result = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 120.0),
                BookChapter(title = "Chapter 2", startSeconds = 120.0, endSeconds = 240.0)
            ),
            currentPositionSeconds = 60.0,
            bookDurationSeconds = 240.0
        )

        assertNull(result.targetSeconds)
        assertEquals(SkipIntroOutroFailureReason.NoMarkerInCurrentChapter, result.failureReason)
    }

    @Test
    fun resolve_returnsNextChapterStartForIntroChapter() {
        val result = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Introduction", startSeconds = 0.0, endSeconds = 40.0),
                BookChapter(title = "Chapter 1", startSeconds = 40.0, endSeconds = 210.0)
            ),
            currentPositionSeconds = 12.0,
            bookDurationSeconds = 210.0
        )

        assertEquals(40.0, result.targetSeconds ?: -1.0, 0.001)
        assertNull(result.failureReason)
    }

    @Test
    fun resolve_returnsDurationForLastOutroChapter() {
        val result = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 180.0),
                BookChapter(title = "Outro", startSeconds = 180.0, endSeconds = null)
            ),
            currentPositionSeconds = 200.0,
            bookDurationSeconds = 245.0
        )

        assertEquals(245.0, result.targetSeconds ?: -1.0, 0.001)
        assertNull(result.failureReason)
    }

    @Test
    fun resolve_treatsForewordAndAfterwordAsSkippableMarkers() {
        val forewordResult = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Foreword", startSeconds = 0.0, endSeconds = 25.0),
                BookChapter(title = "Chapter 1", startSeconds = 25.0, endSeconds = 100.0)
            ),
            currentPositionSeconds = 5.0,
            bookDurationSeconds = 100.0
        )
        assertEquals(25.0, forewordResult.targetSeconds ?: -1.0, 0.001)
        assertNull(forewordResult.failureReason)

        val afterwordResult = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Chapter 1", startSeconds = 0.0, endSeconds = 100.0),
                BookChapter(title = "Afterword", startSeconds = 100.0, endSeconds = null)
            ),
            currentPositionSeconds = 120.0,
            bookDurationSeconds = 160.0
        )
        assertEquals(160.0, afterwordResult.targetSeconds ?: -1.0, 0.001)
        assertNull(afterwordResult.failureReason)
    }

    @Test
    fun resolve_returnsMissingDurationWhenNoNextChapterAndNoBookDuration() {
        val result = SkipIntroOutroUseCase.resolve(
            chapters = listOf(
                BookChapter(title = "Epilogue", startSeconds = 120.0, endSeconds = null)
            ),
            currentPositionSeconds = 140.0,
            bookDurationSeconds = null
        )

        assertNull(result.targetSeconds)
        assertEquals(SkipIntroOutroFailureReason.MissingDuration, result.failureReason)
    }
}
