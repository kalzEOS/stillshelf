package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackExternalControlResolverTest {

    @Test
    fun normalizeLockScreenControlMode_defaultsToSkip() {
        assertEquals(LOCK_SCREEN_MODE_SKIP, normalizeLockScreenControlMode(null))
        assertEquals(LOCK_SCREEN_MODE_SKIP, normalizeLockScreenControlMode("something-else"))
    }

    @Test
    fun normalizeLockScreenControlMode_acceptsNextCaseInsensitively() {
        assertEquals(LOCK_SCREEN_MODE_NEXT, normalizeLockScreenControlMode("NeXt"))
    }

    @Test
    fun shouldGoToPreviousAfterRestart_requiresMatchingRestartContext() {
        val previous = rememberRestartState(
            bookId = "book-1",
            restartStartMs = 60_000L,
            chapterMode = true,
            triggeredAtElapsedMs = 1_000L
        )

        assertFalse(
            shouldGoToPreviousAfterRestart(
                previousRestartState = previous,
                bookId = "book-2",
                restartStartMs = 60_000L,
                chapterMode = true,
                currentPositionMs = 61_000L,
                nowElapsedMs = 2_000L
            )
        )
        assertFalse(
            shouldGoToPreviousAfterRestart(
                previousRestartState = previous,
                bookId = "book-1",
                restartStartMs = 0L,
                chapterMode = true,
                currentPositionMs = 61_000L,
                nowElapsedMs = 2_000L
            )
        )
        assertFalse(
            shouldGoToPreviousAfterRestart(
                previousRestartState = previous,
                bookId = "book-1",
                restartStartMs = 60_000L,
                chapterMode = false,
                currentPositionMs = 61_000L,
                nowElapsedMs = 2_000L
            )
        )
    }

    @Test
    fun shouldGoToPreviousAfterRestart_allowsQuickSecondPressNearRestartPoint() {
        val previous = rememberRestartState(
            bookId = "book-1",
            restartStartMs = 60_000L,
            chapterMode = true,
            triggeredAtElapsedMs = 1_000L
        )

        assertTrue(
            shouldGoToPreviousAfterRestart(
                previousRestartState = previous,
                bookId = "book-1",
                restartStartMs = 60_000L,
                chapterMode = true,
                currentPositionMs = 61_200L,
                nowElapsedMs = 3_000L
            )
        )
    }

    @Test
    fun shouldGoToPreviousAfterRestart_expiresAfterDoublePressWindow() {
        val previous = rememberRestartState(
            bookId = "book-1",
            restartStartMs = 60_000L,
            chapterMode = false,
            triggeredAtElapsedMs = 1_000L
        )

        assertFalse(
            shouldGoToPreviousAfterRestart(
                previousRestartState = previous,
                bookId = "book-1",
                restartStartMs = 60_000L,
                chapterMode = false,
                currentPositionMs = 60_500L,
                nowElapsedMs = 8_000L
            )
        )
    }
}
