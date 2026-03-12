package com.stillshelf.app.domain.usecase

import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.util.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookProgressActionCoordinatorTest {
    @Test
    fun `mark finished returns default message and reconciles playback`() = runTest {
        val expectedProgress = PlaybackProgress(
            progressPercent = 1.0,
            currentTimeSeconds = 3600.0,
            durationSeconds = 3600.0
        )
        val repositoryCalls = mutableListOf<Triple<String, Boolean, Boolean>>()
        var playbackCall: Triple<String, PlaybackProgress, Boolean>? = null
        val coordinator = BookProgressActionCoordinator(
            markBookFinished = { bookId, finished, resetProgressWhenUnfinished ->
                repositoryCalls += Triple(bookId, finished, resetProgressWhenUnfinished)
                AppResult.Success(expectedProgress)
            },
            reconcilePlaybackProgress = { bookId, progress, isFinished ->
                playbackCall = Triple(bookId, progress, isFinished)
            }
        )

        val result = coordinator(bookId = "book-1", action = BookProgressAction.MarkFinished)

        assertTrue(result is AppResult.Success)
        val success = result as AppResult.Success
        assertEquals(BookProgressAction.MarkFinished, success.value.action)
        assertEquals("Marked as finished. Progress is now 100%.", success.value.message)
        assertEquals(expectedProgress, success.value.resolvedProgress)
        assertEquals(true, success.value.finishedState)
        assertEquals(listOf(Triple("book-1", true, false)), repositoryCalls)
        assertEquals(Triple("book-1", expectedProgress, true), playbackCall)
    }

    @Test
    fun `reset progress uses unfinished reset mutation flags`() = runTest {
        val expectedProgress = PlaybackProgress(
            progressPercent = 0.0,
            currentTimeSeconds = 0.0,
            durationSeconds = 3600.0
        )
        val repositoryCalls = mutableListOf<Triple<String, Boolean, Boolean>>()
        val coordinator = BookProgressActionCoordinator(
            markBookFinished = { bookId, finished, resetProgressWhenUnfinished ->
                repositoryCalls += Triple(bookId, finished, resetProgressWhenUnfinished)
                AppResult.Success(expectedProgress)
            },
            reconcilePlaybackProgress = { _, _, _ -> }
        )

        val result = coordinator(bookId = "book-2", action = BookProgressAction.ResetProgress)

        assertTrue(result is AppResult.Success)
        val success = result as AppResult.Success
        assertEquals(BookProgressAction.ResetProgress, success.value.action)
        assertEquals("Book progress reset.", success.value.message)
        assertEquals(false, success.value.finishedState)
        assertEquals(listOf(Triple("book-2", false, true)), repositoryCalls)
    }

    @Test
    fun `error result is returned without reconciling playback`() = runTest {
        val coordinator = BookProgressActionCoordinator(
            markBookFinished = { _, _, _ -> AppResult.Error("Unable to update progress.") },
            reconcilePlaybackProgress = { _, _, _ ->
                throw AssertionError("Playback should not be reconciled for failed mutations.")
            }
        )

        val result = coordinator(bookId = "book-3", action = BookProgressAction.MarkUnfinished)

        assertTrue(result is AppResult.Error)
        assertEquals("Unable to update progress.", (result as AppResult.Error).message)
        assertNull((result as? AppResult.Success)?.value)
    }
}
