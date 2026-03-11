package com.stillshelf.app.playback.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSyncMergeTest {

    @Test
    fun resolveMergedProgressSyncFinishedState_keepsLatestResetIntent() {
        assertFalse(
            resolveMergedProgressSyncFinishedState(
                existingIsFinished = true,
                incomingIsFinished = false
            )
        )
    }

    @Test
    fun resolveMergedProgressSyncFinishedState_keepsLatestFinishedIntent() {
        assertTrue(
            resolveMergedProgressSyncFinishedState(
                existingIsFinished = false,
                incomingIsFinished = true
            )
        )
    }
}
