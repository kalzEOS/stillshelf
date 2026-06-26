package com.stillshelf.app.core.util

import com.stillshelf.app.core.model.PodcastEpisode

private const val FinishedProgressThreshold = 0.995

fun PodcastEpisode.resolvedProgressFraction(
    activePlaybackBookId: String? = null,
    activePlaybackPositionMs: Long? = null,
    activePlaybackDurationMs: Long? = null
): Double? {
    val episodeBookId = "$showId::$id"
    if (activePlaybackBookId == episodeBookId) {
        val liveDurationSeconds = activePlaybackDurationMs
            ?.takeIf { it > 0L }
            ?.toDouble()
            ?.div(1000.0)
            ?: durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
        val livePositionSeconds = activePlaybackPositionMs
            ?.takeIf { it >= 0L }
            ?.toDouble()
            ?.div(1000.0)

        if (liveDurationSeconds != null && livePositionSeconds != null) {
            return (livePositionSeconds / liveDurationSeconds)
                .takeIf { it.isFinite() }
                ?.coerceIn(0.0, 1.0)
        }
    }

    progressPercent?.takeIf { it.isFinite() }?.let { return it.coerceIn(0.0, 1.0) }

    val duration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
    val current = currentTimeSeconds?.takeIf { it.isFinite() && it >= 0.0 }
    if (duration != null && current != null) {
        return (current / duration).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
    }

    return if (isFinished) 1.0 else null
}

fun PodcastEpisode.hasPlaybackProgress(
    activePlaybackBookId: String? = null,
    activePlaybackPositionMs: Long? = null,
    activePlaybackDurationMs: Long? = null
): Boolean {
    return resolvedProgressFraction(
        activePlaybackBookId = activePlaybackBookId,
        activePlaybackPositionMs = activePlaybackPositionMs,
        activePlaybackDurationMs = activePlaybackDurationMs
    )?.let { it > 0.01 } == true
}

fun PodcastEpisode.isPlaybackComplete(
    activePlaybackBookId: String? = null,
    activePlaybackPositionMs: Long? = null,
    activePlaybackDurationMs: Long? = null
): Boolean {
    if (isFinished) return true
    val progress = resolvedProgressFraction(
        activePlaybackBookId = activePlaybackBookId,
        activePlaybackPositionMs = activePlaybackPositionMs,
        activePlaybackDurationMs = activePlaybackDurationMs
    ) ?: return false
    return progress >= FinishedProgressThreshold
}
