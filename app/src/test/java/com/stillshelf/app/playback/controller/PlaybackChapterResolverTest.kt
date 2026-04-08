package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackChapterResolverTest {

    @Test
    fun resolveCurrentChapterIndex_defaultsToFirstChapterBeforeStart() {
        assertEquals(
            0,
            resolveCurrentChapterIndex(
                chapterStartsMs = listOf(5_000L, 10_000L, 15_000L),
                positionMs = 0L
            )
        )
    }

    @Test
    fun resolveCurrentChapterIndex_advancesAcrossBoundaryWithinSeekTolerance() {
        assertEquals(
            1,
            resolveCurrentChapterIndex(
                chapterStartsMs = listOf(0L, 60_000L, 120_000L),
                positionMs = 59_850L
            )
        )
    }

    @Test
    fun resolveCurrentChapterIndex_staysOnCurrentChapterOutsideTolerance() {
        assertEquals(
            0,
            resolveCurrentChapterIndex(
                chapterStartsMs = listOf(0L, 60_000L, 120_000L),
                positionMs = 59_700L
            )
        )
    }
}
