package com.stillshelf.app.ui.screens.podcasts

import com.stillshelf.app.core.model.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastEpisodeSortOrderTest {

    @Test
    fun newestOrder_keepsExistingFeedOrder() {
        assertEquals(
            listOf("episode-1", "episode-2", "episode-3"),
            sampleEpisodes().sortedByEpisodeSortOrder(EpisodeSortOrder.Newest).map { it.id }
        )
    }

    @Test
    fun oldestOrder_reversesFeedOrder() {
        assertEquals(
            listOf("episode-3", "episode-2", "episode-1"),
            sampleEpisodes().sortedByEpisodeSortOrder(EpisodeSortOrder.Oldest).map { it.id }
        )
    }

    private fun sampleEpisodes(): List<PodcastEpisode> {
        return listOf(
            sampleEpisode(id = "episode-1"),
            sampleEpisode(id = "episode-2"),
            sampleEpisode(id = "episode-3")
        )
    }

    private fun sampleEpisode(id: String): PodcastEpisode {
        return PodcastEpisode(
            id = id,
            showId = "show-1",
            title = id,
            subtitle = null,
            description = null,
            pubDate = null,
            durationSeconds = 120.0,
            season = null,
            episode = null,
            audioUrl = "https://example.com/$id.mp3",
            enclosureUrl = "https://example.com/$id.mp3"
        )
    }
}
