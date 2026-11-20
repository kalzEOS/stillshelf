package com.stillshelf.app.scrobbling

import org.junit.Assert.*
import org.junit.Test

class ListenAccumulatorTest {
    @Test fun seeksCannotSubstituteForListeningTimeAndOnlyOneListenIsEmitted() {
        val accumulator = ListenAccumulator()
        assertNull(accumulator.sample("a", true, 0, 100, 3000))
        assertNull(accumulator.sample("a", true, 1000, 101, 3000))
        assertNull(accumulator.sample("a", true, 2000, 102, 3000))
        assertEquals(100L, accumulator.sample("a", true, 3000, 103, 3000))
        assertNull(accumulator.sample("a", true, 4000, 104, 3000))
    }

    @Test fun pauseAndBufferingTimeAreNotCounted() {
        val accumulator = ListenAccumulator()
        accumulator.sample("a", true, 0, 100, 3000)
        accumulator.sample("a", false, 1000, 101, 3000)
        assertNull(accumulator.sample("a", false, 60000, 160, 3000))
        assertNull(accumulator.sample("a", true, 61000, 161, 3000))
        assertNull(accumulator.sample("a", true, 62000, 162, 3000))
        assertEquals(100L, accumulator.sample("a", false, 63000, 163, 3000))
    }

    @Test fun transitionResetAllowsARepeatedTrackAndDoesNotCarryTimeAcrossTracks() {
        val accumulator = ListenAccumulator()
        accumulator.sample("a", true, 0, 100, 2000)
        assertNull(accumulator.sample("b", true, 1000, 101, 2000))
        assertEquals(101L, accumulator.sample("b", true, 3000, 103, 2000))
        accumulator.reset()
        assertNull(accumulator.sample("b", true, 4000, 104, 2000))
        assertEquals(104L, accumulator.sample("b", true, 6000, 106, 2000))
    }

    @Test fun unknownDurationsAndLastFmShortTracksAreNotSubmitted() {
        assertNull(scrobbleThresholdMs(0, ListeningProvider.KOITO))
        assertNull(scrobbleThresholdMs(30, ListeningProvider.LAST_FM))
        assertEquals(15000L, scrobbleThresholdMs(30, ListeningProvider.LISTEN_BRAINZ))
        assertEquals(240000L, scrobbleThresholdMs(900, ListeningProvider.LAST_FM))
        assertEquals(90000L, scrobbleThresholdMs(180, ListeningProvider.MALOJA))
    }

    @Test fun queuedButPausedTrackUsesTheActualPlaybackStartTimestamp() {
        val accumulator = ListenAccumulator()
        accumulator.sample("a", false, 0, 100, 2000)
        accumulator.sample("a", true, 60000, 160, 2000)
        assertEquals(160L, accumulator.sample("a", true, 62000, 162, 2000))
    }

    @Test fun suspendedProcessGapDoesNotFabricateListenedTime() {
        val accumulator = ListenAccumulator()
        accumulator.sample("a", true, 0, 100, 2000)
        assertNull(accumulator.sample("a", true, 60000, 160, 2000))
        assertNull(accumulator.sample("a", true, 61000, 161, 2000))
        assertEquals(100L, accumulator.sample("a", true, 62000, 162, 2000))
    }
}
