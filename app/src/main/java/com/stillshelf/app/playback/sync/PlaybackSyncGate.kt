package com.stillshelf.app.playback.sync

import kotlin.math.abs

class PlaybackSyncGate(
    private val minimumDeltaMs: Long = 15_000L
) {
    private var lastSyncedPositionMs: Long = -1L

    fun reset() {
        lastSyncedPositionMs = -1L
    }

    fun shouldSync(currentPositionMs: Long, force: Boolean): Boolean {
        if (force) return true
        if (lastSyncedPositionMs < 0L) return true
        return abs(currentPositionMs - lastSyncedPositionMs) >= minimumDeltaMs
    }

    fun markSyncFinished(currentPositionMs: Long) {
        lastSyncedPositionMs = currentPositionMs
    }
}
