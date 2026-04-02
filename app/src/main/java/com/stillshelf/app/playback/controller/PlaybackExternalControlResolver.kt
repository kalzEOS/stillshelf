package com.stillshelf.app.playback.controller

internal const val LOCK_SCREEN_MODE_SKIP = "skip"
internal const val LOCK_SCREEN_MODE_NEXT = "next"

private const val LOCK_SCREEN_SECOND_PREVIOUS_POSITION_WINDOW_MS = 1_500L
private const val LOCK_SCREEN_PREVIOUS_DOUBLE_PRESS_WINDOW_MS = 6_000L

internal data class PreviousRestartState(
    val bookId: String,
    val restartStartMs: Long,
    val chapterMode: Boolean,
    val triggeredAtElapsedMs: Long
)

internal fun normalizeLockScreenControlMode(rawMode: String?): String {
    return if (rawMode.equals(LOCK_SCREEN_MODE_NEXT, ignoreCase = true)) {
        LOCK_SCREEN_MODE_NEXT
    } else {
        LOCK_SCREEN_MODE_SKIP
    }
}

internal fun shouldGoToPreviousAfterRestart(
    previousRestartState: PreviousRestartState?,
    bookId: String,
    restartStartMs: Long,
    chapterMode: Boolean,
    currentPositionMs: Long,
    nowElapsedMs: Long
): Boolean {
    val state = previousRestartState ?: return false
    if (state.bookId != bookId) return false
    if (state.restartStartMs != restartStartMs) return false
    if (state.chapterMode != chapterMode) return false
    val elapsedSinceTriggerMs = nowElapsedMs - state.triggeredAtElapsedMs
    if (elapsedSinceTriggerMs > LOCK_SCREEN_PREVIOUS_DOUBLE_PRESS_WINDOW_MS) return false
    return currentPositionMs <= (restartStartMs + LOCK_SCREEN_SECOND_PREVIOUS_POSITION_WINDOW_MS)
}

internal fun rememberRestartState(
    bookId: String,
    restartStartMs: Long,
    chapterMode: Boolean,
    triggeredAtElapsedMs: Long
): PreviousRestartState {
    return PreviousRestartState(
        bookId = bookId,
        restartStartMs = restartStartMs.coerceAtLeast(0L),
        chapterMode = chapterMode,
        triggeredAtElapsedMs = triggeredAtElapsedMs
    )
}
