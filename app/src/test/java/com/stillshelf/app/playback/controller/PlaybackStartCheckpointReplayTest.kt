package com.stillshelf.app.playback.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartCheckpointReplayTest {

    @Test
    fun shouldReplayLocalCheckpointAtStartup_requiresLocalSelection() {
        assertFalse(
            shouldReplayLocalCheckpointAtStartup(
                selectedSourceIsLocal = false,
                localCheckpointMatchesResolvedProgress = true
            )
        )
    }

    @Test
    fun shouldReplayLocalCheckpointAtStartup_allowsReplayWhenLocalWonAndMatches() {
        assertTrue(
            shouldReplayLocalCheckpointAtStartup(
                selectedSourceIsLocal = true,
                localCheckpointMatchesResolvedProgress = true
            )
        )
    }
}
