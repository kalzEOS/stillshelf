package com.stillshelf.app.playback.controller

import kotlin.math.abs

private const val LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS = 2_000L
private const val BACKGROUND_SYNC_MIN_INTERVAL_MS = 2_000L

internal fun shouldPersistPlaybackCheckpoint(
    force: Boolean,
    positionMs: Long,
    lastCheckpointPositionMs: Long,
    elapsedNowMs: Long,
    lastCheckpointSavedAtElapsedMs: Long
): Boolean {
    if (force) return true
    if (lastCheckpointPositionMs < 0L) return true
    if (abs(positionMs - lastCheckpointPositionMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS) return true
    return (elapsedNowMs - lastCheckpointSavedAtElapsedMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS
}

internal fun shouldSyncProgressOnBackground(
    isPlaying: Boolean,
    currentPositionMs: Long,
    bookCurrentTimeSeconds: Double?,
    elapsedNowMs: Long,
    lastBackgroundSyncAtElapsedMs: Long,
    lastBackgroundSyncPositionMs: Long
): Boolean {
    val positionAdvancedEnough =
        abs(currentPositionMs - lastBackgroundSyncPositionMs) >= LOCAL_PLAYBACK_CHECKPOINT_DELTA_MS
    val backgroundSyncRecentlyTriggered =
        (elapsedNowMs - lastBackgroundSyncAtElapsedMs) < BACKGROUND_SYNC_MIN_INTERVAL_MS
    if (backgroundSyncRecentlyTriggered && !positionAdvancedEnough) return false
    if (!isPlaying && currentPositionMs <= 0L && (bookCurrentTimeSeconds ?: 0.0) <= 0.0) return false
    return true
}
