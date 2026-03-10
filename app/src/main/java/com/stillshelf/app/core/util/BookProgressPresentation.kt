package com.stillshelf.app.core.util

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import java.util.Locale

private const val FinishedProgressThreshold = 0.995

data class BookProgressPresentation(
    val durationSeconds: Double?,
    val currentTimeSeconds: Double?,
    val normalizedProgressPercent: Double?,
    val startedSeconds: Double?,
    val remainingSeconds: Double?,
    val hasStarted: Boolean,
    val isFinished: Boolean
) {
    fun remainingTimeLabel(
        precise: Boolean = true,
        emptyFallback: String = ""
    ): String {
        if (!hasStarted || isFinished) return emptyFallback
        val remaining = remainingSeconds?.takeIf { it > 0.0 } ?: return emptyFallback
        val readable = if (precise) {
            formatHoursMinutesPrecise(remaining)
        } else {
            formatDurationHoursMinutes(remaining)
        }
        if (readable.isBlank()) return emptyFallback
        return "$readable left"
    }

    fun metadataLabel(
        durationFallback: Boolean = true,
        preciseTimeLeft: Boolean = true
    ): String {
        if (!hasStarted || isFinished) {
            return if (durationFallback) formatDurationHoursMinutes(durationSeconds) else ""
        }
        return remainingTimeLabel(precise = preciseTimeLeft)
    }

    fun progressPercentLabel(): String {
        val percent = ((normalizedProgressPercent ?: 0.0).coerceIn(0.0, 1.0) * 100.0)
        return if (percent in 0.0..<1.0) {
            String.format(Locale.getDefault(), "%.1f%%", percent)
        } else {
            "${percent.toInt().coerceIn(0, 100)}%"
        }
    }
}

fun BookSummary.progressPresentation(): BookProgressPresentation {
    return resolveBookProgressPresentation(
        durationSeconds = durationSeconds,
        currentTimeSeconds = currentTimeSeconds,
        progressPercent = progressPercent,
        isFinished = isFinished
    )
}

fun ContinueListeningItem.progressPresentation(): BookProgressPresentation {
    return resolveBookProgressPresentation(
        durationSeconds = book.durationSeconds,
        currentTimeSeconds = currentTimeSeconds ?: book.currentTimeSeconds,
        progressPercent = progressPercent ?: book.progressPercent,
        isFinished = book.isFinished
    )
}

fun BookSummary.hasStartedProgress(): Boolean = progressPresentation().hasStarted

fun BookSummary.hasFinishedProgress(): Boolean = progressPresentation().isFinished

fun BookSummary.remainingTimeLabel(): String = progressPresentation().remainingTimeLabel(precise = true)

fun BookSummary.searchMetadataLabel(): String {
    return progressPresentation().metadataLabel(
        durationFallback = true,
        preciseTimeLeft = true
    )
}

fun ContinueListeningItem.timeLeftLabel(fallback: String = "In progress"): String {
    return progressPresentation().remainingTimeLabel(
        precise = false,
        emptyFallback = fallback
    )
}

fun formatDurationHoursMinutes(durationSeconds: Double?): String {
    val totalSeconds = durationSeconds?.takeIf { it.isFinite() }?.toLong() ?: return ""
    if (totalSeconds <= 0L) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${maxOf(minutes, 1)}m"
    }
}

fun formatHoursMinutesPrecise(durationSeconds: Double?): String {
    val totalSeconds = durationSeconds?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val totalMinutes = (totalSeconds / 60.0).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}

fun formatTimeLeftLabel(durationSeconds: Double, positionSeconds: Double): String {
    val remainingSeconds = (durationSeconds - positionSeconds.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    val readable = formatDurationHoursMinutes(remainingSeconds)
    return if (readable.isBlank()) "0m left" else "$readable left"
}

private fun resolveBookProgressPresentation(
    durationSeconds: Double?,
    currentTimeSeconds: Double?,
    progressPercent: Double?,
    isFinished: Boolean
): BookProgressPresentation {
    val safeCurrent = currentTimeSeconds?.takeIf { it.isFinite() && it >= 0.0 }
    val safeProgress = progressPercent?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
    val resolvedDuration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
        ?: run {
            if (safeCurrent != null && safeProgress != null && safeProgress > 0.0) {
                (safeCurrent / safeProgress).takeIf { it.isFinite() && it > 0.0 }
            } else {
                null
            }
        }
    val resolvedCurrent = safeCurrent
        ?: if (resolvedDuration != null && safeProgress != null) {
            (resolvedDuration * safeProgress).takeIf { it.isFinite() && it >= 0.0 }
        } else {
            null
        }
    val normalizedProgress = safeProgress
        ?: if (resolvedDuration != null && resolvedCurrent != null && resolvedDuration > 0.0) {
            (resolvedCurrent / resolvedDuration).coerceIn(0.0, 1.0)
        } else {
            null
        }
    val finished = isFinished || ((normalizedProgress ?: 0.0) >= FinishedProgressThreshold)
    val startedSeconds = resolveStartedProgressSeconds(
        currentTimeSeconds = resolvedCurrent,
        durationSeconds = resolvedDuration,
        progressPercent = normalizedProgress
    )
    val hasStarted = hasMeaningfulStartedProgress(
        currentTimeSeconds = resolvedCurrent,
        durationSeconds = resolvedDuration,
        progressPercent = normalizedProgress,
        isFinished = finished
    )
    val remainingSeconds = when {
        finished || resolvedDuration == null -> null
        normalizedProgress != null -> resolvedDuration * (1.0 - normalizedProgress.coerceIn(0.0, 1.0))
        resolvedCurrent != null -> resolvedDuration - resolvedCurrent
        else -> resolvedDuration
    }?.coerceAtLeast(0.0)

    return BookProgressPresentation(
        durationSeconds = resolvedDuration,
        currentTimeSeconds = resolvedCurrent,
        normalizedProgressPercent = normalizedProgress,
        startedSeconds = startedSeconds,
        remainingSeconds = remainingSeconds,
        hasStarted = hasStarted,
        isFinished = finished
    )
}
