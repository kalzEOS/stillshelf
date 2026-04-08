package com.stillshelf.app.playback.controller

private const val PLAYBACK_SPEED_EPSILON = 0.01f
private const val CHAPTER_BOUNDARY_TOLERANCE_MS = 200L

internal fun resolveCycledPlaybackSpeed(currentSpeed: Float, steps: List<Float>): Float {
    val normalizedSteps = normalizePlaybackSpeedSteps(steps)
    if (normalizedSteps.isEmpty()) return currentSpeed
    val nextIndex = normalizedSteps.indexOfFirst { step -> step > (currentSpeed + PLAYBACK_SPEED_EPSILON) }
        .takeIf { it >= 0 }
        ?: 0
    return normalizedSteps[nextIndex]
}

internal fun resolveIncreasedPlaybackSpeed(currentSpeed: Float, steps: List<Float>): Float {
    val normalizedSteps = normalizePlaybackSpeedSteps(steps)
    if (normalizedSteps.isEmpty()) return currentSpeed
    return normalizedSteps.firstOrNull { it > (currentSpeed + PLAYBACK_SPEED_EPSILON) }
        ?: normalizedSteps.last()
}

internal fun resolveDecreasedPlaybackSpeed(currentSpeed: Float, steps: List<Float>): Float {
    val normalizedSteps = normalizePlaybackSpeedSteps(steps)
    if (normalizedSteps.isEmpty()) return currentSpeed
    return normalizedSteps.lastOrNull { it < (currentSpeed - PLAYBACK_SPEED_EPSILON) }
        ?: normalizedSteps.first()
}

internal fun resolveNextChapterBoundaryMs(
    boundariesMs: List<Long>,
    positionMs: Long
): Long? {
    val safePositionMs = positionMs.coerceAtLeast(0L)
    return boundariesMs.firstOrNull { boundaryMs ->
        boundaryMs > (safePositionMs + CHAPTER_BOUNDARY_TOLERANCE_MS)
    }
}

internal fun resolveRemainingToChapterBoundaryMs(
    positionMs: Long,
    targetBoundaryMs: Long?
): Long {
    if (targetBoundaryMs == null) return 0L
    val safePositionMs = positionMs.coerceAtLeast(0L)
    if (safePositionMs >= targetBoundaryMs - CHAPTER_BOUNDARY_TOLERANCE_MS) {
        return 0L
    }
    return (targetBoundaryMs - safePositionMs).coerceAtLeast(0L)
}

private fun normalizePlaybackSpeedSteps(steps: List<Float>): List<Float> {
    return steps
        .map { it.coerceIn(0.5f, 2.0f) }
        .distinct()
        .sorted()
}
