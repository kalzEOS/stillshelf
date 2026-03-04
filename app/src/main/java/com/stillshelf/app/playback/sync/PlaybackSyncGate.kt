package com.stillshelf.app.playback.sync

import kotlin.math.abs

class PlaybackSyncGate(
    private val minimumDeltaMs: Long = 15_000L
) {
    private var lastSyncedPositionMs: Long = -1L
    private var syncInFlight: Boolean = false

    fun reset() {
        lastSyncedPositionMs = -1L
        syncInFlight = false
    }

    fun shouldSync(currentPositionMs: Long, force: Boolean): Boolean {
        if (syncInFlight) return false
        if (force) return true
        if (lastSyncedPositionMs < 0L) return true
        return abs(currentPositionMs - lastSyncedPositionMs) >= minimumDeltaMs
    }

    fun markSyncStarted() {
        syncInFlight = true
    }

    fun markSyncFinished(currentPositionMs: Long) {
        lastSyncedPositionMs = currentPositionMs
        syncInFlight = false
    }

    fun markSyncFailed() {
        syncInFlight = false
    }
}
