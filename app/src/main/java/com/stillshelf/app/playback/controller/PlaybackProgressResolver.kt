package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.datastore.PlaybackCheckpointSnapshot
import com.stillshelf.app.core.model.PlaybackProgress
import kotlin.math.abs

private const val PROGRESS_MATCH_EPSILON_SECONDS = 1.0
private const val RESTART_FROM_BEGINNING_THRESHOLD = 0.995

internal data class PreferredPlaybackProgress(
    val progress: PlaybackProgress?,
    val source: PlaybackProgressSource
)

internal enum class PlaybackProgressSource {
    None,
    Server,
    Local
}

internal fun resolvePreferredPlaybackProgress(
    serverProgress: PlaybackProgress?,
    localCheckpoint: PlaybackCheckpointSnapshot?
): PreferredPlaybackProgress {
    if (localCheckpoint == null) {
        return PreferredPlaybackProgress(
            progress = serverProgress,
            source = if (serverProgress != null) PlaybackProgressSource.Server else PlaybackProgressSource.None
        )
    }
    val localProgress = localCheckpoint.toPlaybackProgress()
    if (serverProgress == null) {
        return PreferredPlaybackProgress(
            progress = localProgress,
            source = PlaybackProgressSource.Local
        )
    }

    val localUpdatedAtMs = localCheckpoint.savedAtMs.takeIf { it > 0L }
    val serverUpdatedAtMs = serverProgress.updatedAtMs?.takeIf { it > 0L }
    if (localUpdatedAtMs != null && serverUpdatedAtMs != null && localUpdatedAtMs != serverUpdatedAtMs) {
        return if (localUpdatedAtMs > serverUpdatedAtMs) {
            PreferredPlaybackProgress(progress = localProgress, source = PlaybackProgressSource.Local)
        } else {
            PreferredPlaybackProgress(progress = serverProgress, source = PlaybackProgressSource.Server)
        }
    }

    val localSeconds = localProgress.currentTimeSeconds
    val serverSeconds = serverProgress.currentTimeSeconds
    if (localSeconds != null && serverSeconds != null) {
        return when {
            abs(localSeconds - serverSeconds) <= PROGRESS_MATCH_EPSILON_SECONDS -> {
                if ((localUpdatedAtMs ?: 0L) >= (serverUpdatedAtMs ?: 0L)) {
                    PreferredPlaybackProgress(progress = localProgress, source = PlaybackProgressSource.Local)
                } else {
                    PreferredPlaybackProgress(progress = serverProgress, source = PlaybackProgressSource.Server)
                }
            }

            localSeconds > serverSeconds -> {
                PreferredPlaybackProgress(progress = localProgress, source = PlaybackProgressSource.Local)
            }

            else -> {
                PreferredPlaybackProgress(progress = serverProgress, source = PlaybackProgressSource.Server)
            }
        }
    }

    return if (localSeconds != null) {
        PreferredPlaybackProgress(progress = localProgress, source = PlaybackProgressSource.Local)
    } else {
        PreferredPlaybackProgress(progress = serverProgress, source = PlaybackProgressSource.Server)
    }
}

internal fun localCheckpointMatchesResolvedProgress(
    localCheckpoint: PlaybackCheckpointSnapshot,
    resolvedProgress: PlaybackProgress?
): Boolean {
    if (resolvedProgress == null) return false
    if (localCheckpoint.isFinished) {
        val resolvedPercent = resolvedProgress.progressPercent ?: 0.0
        return resolvedPercent >= RESTART_FROM_BEGINNING_THRESHOLD
    }
    val resolvedSeconds = resolvedProgress.currentTimeSeconds ?: return false
    return abs(resolvedSeconds - localCheckpoint.currentTimeSeconds) <= PROGRESS_MATCH_EPSILON_SECONDS
}

internal fun PlaybackCheckpointSnapshot.toPlaybackProgress(): PlaybackProgress {
    val progressPercent = durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { duration -> (currentTimeSeconds / duration).coerceIn(0.0, 1.0) }
    return PlaybackProgress(
        progressPercent = progressPercent,
        currentTimeSeconds = currentTimeSeconds,
        durationSeconds = durationSeconds,
        updatedAtMs = savedAtMs.takeIf { it > 0L }
    )
}

internal fun shouldRestartFromBeginning(
    progress: PlaybackProgress?,
    defaultDurationSeconds: Double?
): Boolean {
    progress ?: return false
    val progressPercent = progress.progressPercent
    if (progressPercent != null && progressPercent >= RESTART_FROM_BEGINNING_THRESHOLD) {
        return true
    }
    val current = progress.currentTimeSeconds ?: return false
    val duration = (progress.durationSeconds ?: defaultDurationSeconds)?.takeIf { it > 0.0 } ?: return false
    return (current / duration) >= RESTART_FROM_BEGINNING_THRESHOLD
}
