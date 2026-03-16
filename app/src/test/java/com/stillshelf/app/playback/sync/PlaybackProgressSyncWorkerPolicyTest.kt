package com.stillshelf.app.playback.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressSyncWorkerPolicyTest {

    @Test
    fun shouldRetryPlaybackCheckpointSync_requiresPendingCheckpoints() {
        assertFalse(
            shouldRetryPlaybackCheckpointSync(
                allowBackgroundRetry = true,
                hasPendingCheckpoints = false
            )
        )
    }

    @Test
    fun shouldRetryPlaybackCheckpointSync_requiresRetryAllowance() {
        assertFalse(
            shouldRetryPlaybackCheckpointSync(
                allowBackgroundRetry = false,
                hasPendingCheckpoints = true
            )
        )
    }

    @Test
    fun shouldRetryPlaybackCheckpointSync_allowsRetryWhenPlaybackBackstopIsStillPermitted() {
        assertTrue(
            shouldRetryPlaybackCheckpointSync(
                allowBackgroundRetry = true,
                hasPendingCheckpoints = true
            )
        )
    }
}
