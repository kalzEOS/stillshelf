package com.stillshelf.app.playback.navidrome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromePlaybackCheckpointPolicyTest {
    @Test
    fun persistsWhenTrackChanges() {
        assertTrue(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-2",
                previousTrackId = "track-1",
                currentPositionMs = 0,
                previousPositionMs = 120_000,
                elapsedSinceLastPersistMs = 1_000
            )
        )
    }

    @Test
    fun skipsWhenCheckpointIntervalHasNotElapsed() {
        assertFalse(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-1",
                previousTrackId = "track-1",
                currentPositionMs = 25_000,
                previousPositionMs = 12_000,
                elapsedSinceLastPersistMs = 14_999
            )
        )
    }

    @Test
    fun skipsWhenPositionDeltaIsTooSmall() {
        assertFalse(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-1",
                previousTrackId = "track-1",
                currentPositionMs = 19_000,
                previousPositionMs = 10_000,
                elapsedSinceLastPersistMs = 15_000
            )
        )
    }

    @Test
    fun persistsWhenIntervalAndPositionDeltaPassThreshold() {
        assertTrue(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-1",
                previousTrackId = "track-1",
                currentPositionMs = 25_000,
                previousPositionMs = 10_000,
                elapsedSinceLastPersistMs = 15_000
            )
        )
    }

    @Test
    fun persistsWhenSameTrackRestartsNearBeginning() {
        assertTrue(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-1",
                previousTrackId = "track-1",
                currentPositionMs = 1_000,
                previousPositionMs = 195_000,
                elapsedSinceLastPersistMs = 2_000,
                isNearStartAfterRewind = true
            )
        )
    }

    @Test
    fun skipsNearBeginningWithoutConfirmedRestart() {
        assertFalse(
            shouldPersistNavidromePlaybackCheckpoint(
                currentTrackId = "track-1",
                previousTrackId = "track-1",
                currentPositionMs = 1_000,
                previousPositionMs = 195_000,
                elapsedSinceLastPersistMs = 2_000,
                isNearStartAfterRewind = false
            )
        )
    }
}
