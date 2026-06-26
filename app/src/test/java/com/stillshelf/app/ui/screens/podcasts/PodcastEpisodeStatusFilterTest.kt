package com.stillshelf.app.ui.screens.podcasts

import com.stillshelf.app.core.model.PodcastEpisode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastEpisodeStatusFilterTest {

    @Test
    fun allFilter_matchesEveryEpisode() {
        assertTrue(sampleEpisode().matchesStatusFilter(EpisodeStatusFilter.All))
        assertTrue(sampleEpisode(progressPercent = 0.5).matchesStatusFilter(EpisodeStatusFilter.All))
        assertTrue(sampleEpisode(isFinished = true).matchesStatusFilter(EpisodeStatusFilter.All))
    }

    @Test
    fun finishedFilter_matchesFinishedEpisodes() {
        assertTrue(sampleEpisode(isFinished = true).matchesStatusFilter(EpisodeStatusFilter.Finished))
        assertTrue(sampleEpisode(progressPercent = 0.996).matchesStatusFilter(EpisodeStatusFilter.Finished))
        assertFalse(sampleEpisode(progressPercent = 0.5).matchesStatusFilter(EpisodeStatusFilter.Finished))
    }

    @Test
    fun inProgressFilter_matchesOnlyStartedEpisodesThatAreNotFinished() {
        assertTrue(sampleEpisode(progressPercent = 0.5).matchesStatusFilter(EpisodeStatusFilter.InProgress))
        assertFalse(sampleEpisode().matchesStatusFilter(EpisodeStatusFilter.InProgress))
        assertFalse(sampleEpisode(isFinished = true).matchesStatusFilter(EpisodeStatusFilter.InProgress))
    }

    @Test
    fun notStartedFilter_matchesUnplayedEpisodes() {
        assertTrue(sampleEpisode().matchesStatusFilter(EpisodeStatusFilter.NotStarted))
        assertTrue(sampleEpisode(progressPercent = 0.01).matchesStatusFilter(EpisodeStatusFilter.NotStarted))
        assertFalse(sampleEpisode(progressPercent = 0.5).matchesStatusFilter(EpisodeStatusFilter.NotStarted))
        assertFalse(sampleEpisode(isFinished = true).matchesStatusFilter(EpisodeStatusFilter.NotStarted))
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
