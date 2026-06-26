package com.stillshelf.app.ui.screens.podcasts

import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.util.isPlaybackComplete
import com.stillshelf.app.core.util.resolvedProgressFraction

enum class EpisodeStatusFilter(val label: String) {
    All("All"),
    Finished("Finished"),
    InProgress("In Progress"),
    NotStarted("Not Started")
}

fun PodcastEpisode.matchesStatusFilter(
    filter: EpisodeStatusFilter,
    activePlaybackBookId: String? = null,
    activePlaybackPositionMs: Long? = null,
    activePlaybackDurationMs: Long? = null
): Boolean {
    return when (filter) {
        EpisodeStatusFilter.All -> true
        EpisodeStatusFilter.Finished -> isPlaybackComplete(
            activePlaybackBookId = activePlaybackBookId,
            activePlaybackPositionMs = activePlaybackPositionMs,
            activePlaybackDurationMs = activePlaybackDurationMs
        )
        EpisodeStatusFilter.InProgress -> {
            val progress = resolvedProgressFraction(
                activePlaybackBookId = activePlaybackBookId,
                activePlaybackPositionMs = activePlaybackPositionMs,
                activePlaybackDurationMs = activePlaybackDurationMs
            )
            progress != null && progress > 0.01 && !isPlaybackComplete(
                activePlaybackBookId = activePlaybackBookId,
                activePlaybackPositionMs = activePlaybackPositionMs,
                activePlaybackDurationMs = activePlaybackDurationMs
            )
        }
        EpisodeStatusFilter.NotStarted -> {
            val progress = resolvedProgressFraction(
                activePlaybackBookId = activePlaybackBookId,
                activePlaybackPositionMs = activePlaybackPositionMs,
                activePlaybackDurationMs = activePlaybackDurationMs
            )
            progress == null || progress <= 0.01
        }
    }
}
