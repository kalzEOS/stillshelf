package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.datastore.PlaybackCheckpointSnapshot
import com.stillshelf.app.core.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressResolverTest {

    @Test
    fun resolvePreferredPlaybackProgress_prefersNewerLocalCheckpoint() {
        val preferred = resolvePreferredPlaybackProgress(
            serverProgress = playbackProgress(seconds = 120.0, updatedAtMs = 1_000L),
            localCheckpoint = checkpoint(seconds = 90.0, savedAtMs = 2_000L)
        )

        assertEquals(PlaybackProgressSource.Local, preferred.source)
        assertEquals(90.0, preferred.progress?.currentTimeSeconds)
    }

    @Test
    fun resolvePreferredPlaybackProgress_prefersServerWhenItIsNewer() {
        val preferred = resolvePreferredPlaybackProgress(
            serverProgress = playbackProgress(seconds = 120.0, updatedAtMs = 3_000L),
            localCheckpoint = checkpoint(seconds = 150.0, savedAtMs = 2_000L)
        )

        assertEquals(PlaybackProgressSource.Server, preferred.source)
        assertEquals(120.0, preferred.progress?.currentTimeSeconds)
    }

    @Test
    fun resolvePreferredPlaybackProgress_prefersFartherLocalProgressWithoutComparableTimestamps() {
        val preferred = resolvePreferredPlaybackProgress(
            serverProgress = playbackProgress(seconds = 120.0, updatedAtMs = null),
            localCheckpoint = checkpoint(seconds = 140.5, savedAtMs = 0L)
        )

        assertEquals(PlaybackProgressSource.Local, preferred.source)
        assertEquals(140.5, preferred.progress?.currentTimeSeconds)
    }

    @Test
    fun localCheckpointMatchesResolvedProgress_acceptsFinishedProgressNearCompletion() {
        assertTrue(
            localCheckpointMatchesResolvedProgress(
                localCheckpoint = checkpoint(
                    seconds = 3_590.0,
                    durationSeconds = 3_600.0,
                    isFinished = true
                ),
                resolvedProgress = playbackProgress(
                    seconds = 3_590.0,
                    durationSeconds = 3_600.0,
                    progressPercent = 0.996
                )
            )
        )
    }

    @Test
    fun localCheckpointMatchesResolvedProgress_rejectsUnfinishedProgressOutsideTolerance() {
        assertFalse(
            localCheckpointMatchesResolvedProgress(
                localCheckpoint = checkpoint(seconds = 120.0),
                resolvedProgress = playbackProgress(seconds = 122.5)
            )
        )
    }

    @Test
    fun shouldRestartFromBeginning_usesDurationFallbackWhenPercentMissing() {
        assertTrue(
            shouldRestartFromBeginning(
                progress = playbackProgress(
                    seconds = 597.5,
                    durationSeconds = null,
                    progressPercent = null
                ),
                defaultDurationSeconds = 600.0
            )
        )
    }

    @Test
    fun shouldRestartFromBeginning_returnsFalseWhenProgressIsNotNearEnd() {
        assertFalse(
            shouldRestartFromBeginning(
                progress = playbackProgress(
                    seconds = 420.0,
                    durationSeconds = 600.0,
                    progressPercent = 0.7
                ),
                defaultDurationSeconds = 600.0
            )
        )
    }

    private fun checkpoint(
        seconds: Double,
        durationSeconds: Double? = 3_600.0,
        isFinished: Boolean = false,
        savedAtMs: Long = 1_000L
    ) = PlaybackCheckpointSnapshot(
        serverId = "server",
        bookId = "book",
        currentTimeSeconds = seconds,
        durationSeconds = durationSeconds,
        isFinished = isFinished,
        savedAtMs = savedAtMs
    )

    private fun playbackProgress(
        seconds: Double,
        durationSeconds: Double? = 3_600.0,
        progressPercent: Double? = durationSeconds?.let { seconds / it },
        updatedAtMs: Long? = 1_000L
    ) = PlaybackProgress(
        progressPercent = progressPercent,
        currentTimeSeconds = seconds,
        durationSeconds = durationSeconds,
        updatedAtMs = updatedAtMs
    )
}
