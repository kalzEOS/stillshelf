package com.stillshelf.app.core.util

import com.stillshelf.app.core.model.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastEpisodePresentationTest {

    @Test
    fun resolvedProgressFraction_prefersStoredProgressPercent() {
        val episode = sampleEpisode(
            progressPercent = 0.42,
            currentTimeSeconds = 12.0,
            durationSeconds = 100.0
        )

        assertEquals(0.42, episode.resolvedProgressFraction()!!, 0.0)
        assertTrue(episode.hasPlaybackProgress())
    }

    @Test
    fun resolvedProgressFraction_fallsBackToTimeAndDuration() {
        val episode = sampleEpisode(
            progressPercent = null,
            currentTimeSeconds = 45.0,
            durationSeconds = 120.0
        )

        assertEquals(0.375, episode.resolvedProgressFraction()!!, 0.0001)
        assertTrue(episode.hasPlaybackProgress())
    }

    @Test
    fun resolvedProgressFraction_usesLivePlayerStateForCurrentEpisode() {
        val episode = sampleEpisode(
            progressPercent = null,
            currentTimeSeconds = null,
            durationSeconds = null
        )

        assertEquals(
            0.53,
            episode.resolvedProgressFraction(
                activePlaybackBookId = "show-1::episode-1",
                activePlaybackPositionMs = 53_000L,
                activePlaybackDurationMs = 100_000L
            )!!,
            0.0001
        )
    }

    @Test
    fun isPlaybackComplete_usesFinishedFlagAndHighProgress() {
        val finishedByFlag = sampleEpisode(
            progressPercent = null,
            currentTimeSeconds = null,
            durationSeconds = null,
            isFinished = true
        )
        val finishedByProgress = sampleEpisode(
            progressPercent = 0.996,
            isFinished = false
        )
        val notFinished = sampleEpisode(
            progressPercent = 0.5,
            isFinished = false
        )

        assertTrue(finishedByFlag.isPlaybackComplete())
        assertTrue(finishedByProgress.isPlaybackComplete())
        assertFalse(notFinished.isPlaybackComplete())
    }

    @Test
    fun isPlaybackComplete_usesLivePlayerStateForCurrentEpisode() {
        val episode = sampleEpisode(
            progressPercent = null,
            currentTimeSeconds = null,
            durationSeconds = null
        )

        assertTrue(
            episode.isPlaybackComplete(
                activePlaybackBookId = "show-1::episode-1",
                activePlaybackPositionMs = 99_600L,
                activePlaybackDurationMs = 100_000L
            )
        )
    }

    private fun sampleEpisode(
        progressPercent: Double? = null,
        currentTimeSeconds: Double? = null,
        durationSeconds: Double? = 120.0,
        isFinished: Boolean = false
    ): PodcastEpisode {
        return PodcastEpisode(
            id = "episode-1",
            showId = "show-1",
            title = "Episode",
            subtitle = null,
            description = null,
            pubDate = null,
            durationSeconds = durationSeconds,
            season = null,
            episode = null,
            audioUrl = "https://example.com/audio.mp3",
            enclosureUrl = "https://example.com/audio.mp3",
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            isFinished = isFinished
        )
    }
}
