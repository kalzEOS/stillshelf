package com.stillshelf.app.core.util

import kotlin.math.min

private const val UnfinishedProgressCap = 0.99
const val StartedProgressThresholdSeconds = 30.0

data class UnfinishedProgressState(
    val currentTimeSeconds: Double,
    val durationSeconds: Double?,
    val progressPercent: Double
)

fun resolveStartedProgressSeconds(
    currentTimeSeconds: Double?,
    durationSeconds: Double?,
    progressPercent: Double?
): Double? {
    currentTimeSeconds?.takeIf { it.isFinite() && it >= 0.0 }?.let { return it }
    val safeDuration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeProgress = progressPercent?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: return null
    return (safeDuration * safeProgress).takeIf { it.isFinite() && it >= 0.0 }
}

fun hasMeaningfulStartedProgress(
    currentTimeSeconds: Double?,
    durationSeconds: Double?,
    progressPercent: Double?,
    isFinished: Boolean = false
): Boolean {
    if (isFinished) return true
    val startedSeconds = resolveStartedProgressSeconds(
        currentTimeSeconds = currentTimeSeconds,
        durationSeconds = durationSeconds,
        progressPercent = progressPercent
    )
    return startedSeconds != null && startedSeconds >= StartedProgressThresholdSeconds
}

fun resolveUnfinishedProgressState(
    currentTimeSeconds: Double?,
    durationSeconds: Double?,
    progressPercent: Double?
): UnfinishedProgressState {
    val safeDuration = durationSeconds?.takeIf { it > 0.0 }
    val safeProgress = progressPercent?.coerceIn(0.0, 1.0)
    val derivedCurrentTime = if (safeDuration != null && safeProgress != null) {
        safeDuration * safeProgress
    } else {
        null
    }
    val rawCurrentTime = (currentTimeSeconds ?: derivedCurrentTime ?: 0.0).coerceAtLeast(0.0)
    val cappedCurrentTime = if (safeDuration != null) {
        val unfinishedMaxTime = min(
            safeDuration * UnfinishedProgressCap,
            (safeDuration - 1.0).coerceAtLeast(0.0)
        ).coerceAtLeast(0.0)
        rawCurrentTime.coerceIn(0.0, unfinishedMaxTime)
    } else {
        rawCurrentTime
    }
    val resolvedProgress = when {
        safeDuration != null && safeDuration > 0.0 -> {
            (cappedCurrentTime / safeDuration).coerceIn(0.0, UnfinishedProgressCap)
        }

        else -> safeProgress?.coerceIn(0.0, UnfinishedProgressCap) ?: 0.0
    }
    return UnfinishedProgressState(
        currentTimeSeconds = cappedCurrentTime,
        durationSeconds = safeDuration,
        progressPercent = resolvedProgress
    )
}

fun resolveListenActionLabel(isFinished: Boolean, hasProgress: Boolean): String {
    return when {
        isFinished -> "Listen Again"
        hasProgress -> "Continue Listening"
        else -> "Start Listening"
    }
}
