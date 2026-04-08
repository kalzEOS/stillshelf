package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNavigationResolverTest {

    @Test
    fun secondsToPlaybackPositionMs_clampsNegativeValues() {
        assertEquals(0L, secondsToPlaybackPositionMs(-15.0))
    }

    @Test
    fun normalizeBookmarkTitle_trimsAndDropsBlankTitles() {
        assertEquals("Chapter marker", normalizeBookmarkTitle("  Chapter marker  "))
        assertNull(normalizeBookmarkTitle("   "))
    }

    @Test
    fun resolvePlaybackPositionCommand_seeksCurrentBookAndResumesWhenPaused() {
        val command = resolvePlaybackPositionCommand(
            activeBookId = "book",
            targetBookId = "book",
            targetPositionMs = 30_000L,
            isPlaying = false
        )

        assertTrue(command is PlaybackPositionCommand.SeekCurrentBook)
        command as PlaybackPositionCommand.SeekCurrentBook
        assertEquals(30_000L, command.positionMs)
        assertTrue(command.shouldResume)
    }

    @Test
    fun resolvePlaybackPositionCommand_startsTargetBookWhenDifferentBookIsActive() {
        val command = resolvePlaybackPositionCommand(
            activeBookId = "book-a",
            targetBookId = "book-b",
            targetPositionMs = 45_000L,
            isPlaying = true
        )

        assertEquals(
            PlaybackPositionCommand.StartBookAtPosition(
                bookId = "book-b",
                positionMs = 45_000L
            ),
            command
        )
    }

    @Test
    fun resolveRestoredPlaybackProgressState_clampsToResolvedDuration() {
        val state = resolveRestoredPlaybackProgressState(
            currentTimeSeconds = 500.0,
            displayedDurationMs = 300_000L,
            uiDurationMs = null,
            requestedDurationSeconds = null,
            bookDurationSeconds = null,
            isFinished = false
        )

        assertEquals(300_000L, state.targetMs)
        assertEquals(300_000L, state.resolvedDurationMs)
        assertEquals(1.0, state.progressPercent)
    }

    @Test
    fun resolveRestoredPlaybackProgressState_marksFinishedBooksAtOneHundredPercent() {
        val state = resolveRestoredPlaybackProgressState(
            currentTimeSeconds = 120.0,
            displayedDurationMs = null,
            uiDurationMs = null,
            requestedDurationSeconds = 300.0,
            bookDurationSeconds = null,
            isFinished = true
        )

        assertEquals(120_000L, state.targetMs)
        assertEquals(300_000L, state.resolvedDurationMs)
        assertEquals(1.0, state.progressPercent)
    }
}
