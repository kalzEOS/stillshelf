package com.stillshelf.app.playback.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCheckpointPolicyTest {

    @Test
    fun shouldPersistPlaybackCheckpoint_forcesImmediateSave() {
        assertTrue(
            shouldPersistPlaybackCheckpoint(
                force = true,
                positionMs = 1_000L,
                lastCheckpointPositionMs = 1_000L,
                elapsedNowMs = 1_000L,
                lastCheckpointSavedAtElapsedMs = 900L
            )
        )
    }

    @Test
    fun shouldPersistPlaybackCheckpoint_savesFirstCheckpoint() {
        assertTrue(
            shouldPersistPlaybackCheckpoint(
                force = false,
                positionMs = 500L,
                lastCheckpointPositionMs = -1L,
                elapsedNowMs = 1_000L,
                lastCheckpointSavedAtElapsedMs = 0L
            )
        )
    }

    @Test
    fun shouldPersistPlaybackCheckpoint_savesWhenPositionAdvancesEnough() {
        assertTrue(
            shouldPersistPlaybackCheckpoint(
                force = false,
                positionMs = 5_000L,
                lastCheckpointPositionMs = 2_000L,
                elapsedNowMs = 2_500L,
                lastCheckpointSavedAtElapsedMs = 2_000L
            )
        )
    }

    @Test
    fun shouldPersistPlaybackCheckpoint_skipsTinyMovementInsideTimeWindow() {
        assertFalse(
            shouldPersistPlaybackCheckpoint(
                force = false,
                positionMs = 2_500L,
                lastCheckpointPositionMs = 2_000L,
                elapsedNowMs = 2_500L,
                lastCheckpointSavedAtElapsedMs = 1_000L
            )
        )
    }

    @Test
    fun shouldSyncProgressOnBackground_skipsIdleZeroProgressState() {
        assertFalse(
            shouldSyncProgressOnBackground(
                isPlaying = false,
                currentPositionMs = 0L,
                bookCurrentTimeSeconds = 0.0,
                elapsedNowMs = 5_000L,
                lastBackgroundSyncAtElapsedMs = 0L,
                lastBackgroundSyncPositionMs = -1L
            )
        )
    }

    @Test
    fun shouldSyncProgressOnBackground_skipsRapidRepeatWithoutMeaningfulMovement() {
        assertFalse(
            shouldSyncProgressOnBackground(
                isPlaying = true,
                currentPositionMs = 10_000L,
                bookCurrentTimeSeconds = 10.0,
                elapsedNowMs = 2_500L,
                lastBackgroundSyncAtElapsedMs = 1_500L,
                lastBackgroundSyncPositionMs = 9_500L
            )
        )
    }

    @Test
    fun shouldSyncProgressOnBackground_allowsRapidRepeatWhenPositionMovedEnough() {
        assertTrue(
            shouldSyncProgressOnBackground(
                isPlaying = true,
                currentPositionMs = 13_000L,
                bookCurrentTimeSeconds = 13.0,
                elapsedNowMs = 2_500L,
                lastBackgroundSyncAtElapsedMs = 1_500L,
                lastBackgroundSyncPositionMs = 9_500L
            )
        )
    }
}
