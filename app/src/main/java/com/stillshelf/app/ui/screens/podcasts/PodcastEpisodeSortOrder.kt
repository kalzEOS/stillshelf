package com.stillshelf.app.ui.screens.podcasts

import com.stillshelf.app.core.model.PodcastEpisode

enum class EpisodeSortOrder(val label: String, val hint: String) {
    Newest("Newest", "Newest first"),
    Oldest("Oldest", "Oldest first")
}

fun List<PodcastEpisode>.sortedByEpisodeSortOrder(order: EpisodeSortOrder): List<PodcastEpisode> {
    return when (order) {
        EpisodeSortOrder.Newest -> this
        EpisodeSortOrder.Oldest -> asReversed()
    }
}
