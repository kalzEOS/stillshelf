package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackControlResolverTest {

    @Test
    fun resolveCycledPlaybackSpeed_wrapsToBeginningAfterHighestStep() {
        assertEquals(
            0.5f,
            resolveCycledPlaybackSpeed(
                currentSpeed = 2.0f,
                steps = listOf(0.5f, 1.0f, 2.0f)
            )
        )
    }

    @Test
    fun resolveIncreasedPlaybackSpeed_usesNextConfiguredStep() {
        assertEquals(
            1.2f,
            resolveIncreasedPlaybackSpeed(
                currentSpeed = 1.0f,
                steps = listOf(0.5f, 1.0f, 1.2f, 1.5f)
            )
        )
    }

    @Test
    fun resolveDecreasedPlaybackSpeed_fallsBackToFirstStep() {
        assertEquals(
            0.5f,
            resolveDecreasedPlaybackSpeed(
                currentSpeed = 0.5f,
                steps = listOf(0.5f, 1.0f, 1.5f)
            )
        )
    }

    @Test
    fun resolveNextChapterBoundaryMs_skipsBoundaryInsideToleranceWindow() {
        assertEquals(
            120_000L,
            resolveNextChapterBoundaryMs(
                boundariesMs = listOf(60_000L, 120_000L),
                positionMs = 59_900L
            )
        )
    }

    @Test
    fun resolveNextChapterBoundaryMs_returnsNullWhenNoFutureBoundaryExists() {
        assertNull(
            resolveNextChapterBoundaryMs(
                boundariesMs = listOf(60_000L, 120_000L),
                positionMs = 150_000L
            )
        )
    }

    @Test
    fun resolveRemainingToChapterBoundaryMs_returnsZeroInsideToleranceWindow() {
        assertEquals(
            0L,
            resolveRemainingToChapterBoundaryMs(
                positionMs = 59_900L,
                targetBoundaryMs = 60_000L
            )
        )
    }
}
