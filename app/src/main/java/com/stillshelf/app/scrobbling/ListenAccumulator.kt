package com.stillshelf.app.scrobbling

/** Measures elapsed playing time, never seek distance. Lives on the player's main thread. */
internal class ListenAccumulator {
    private var trackId: String? = null
    private var lastElapsedMs = 0L
    private var wasPlaying = false
    var listenedMs = 0L
        private set
    private var emitted = false
    private var startedAt: Long? = null

    fun reset() {
        trackId = null
        wasPlaying = false
        listenedMs = 0
        emitted = false
        startedAt = null
    }

    fun sample(id: String?, playing: Boolean, elapsedMs: Long, wallSeconds: Long, thresholdMs: Long?): Long? {
        if (id != trackId) {
            reset()
            trackId = id
            lastElapsedMs = elapsedMs
        }
        if (playing && id != null && startedAt == null) startedAt = wallSeconds
        // Ignore a large gap if the process was suspended: it is not proof audio kept playing.
        val delta = elapsedMs - lastElapsedMs
        if (wasPlaying && id != null && delta in 0..2_000) listenedMs += delta
        wasPlaying = playing
        lastElapsedMs = elapsedMs
        if (id == null || thresholdMs == null || emitted || listenedMs < thresholdMs) return null
        emitted = true
        return startedAt
    }
}
