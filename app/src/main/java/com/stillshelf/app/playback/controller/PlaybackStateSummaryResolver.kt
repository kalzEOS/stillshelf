package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem

private const val PLAYBACK_FINISHED_THRESHOLD = 0.995

internal fun shouldTreatPlaybackAsFinished(
    bookIsFinished: Boolean,
    positionMs: Long,
    durationMs: Long?,
    bookDurationSeconds: Double?
): Boolean {
    if (bookIsFinished) return true
    val resolvedDurationMs = durationMs?.takeIf { it > 0L }
        ?: bookDurationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
        ?: return false
    if (resolvedDurationMs <= 0L) return false
    return positionMs.coerceAtLeast(0L) >= (resolvedDurationMs * PLAYBACK_FINISHED_THRESHOLD).toLong()
}

internal fun buildContinueListeningItem(
    book: BookSummary,
    positionMs: Long,
    fallbackDurationMs: Long?
): ContinueListeningItem {
    val durationSeconds = book.durationSeconds
        ?: fallbackDurationMs?.takeIf { it > 0L }?.div(1000.0)
    val currentSeconds = positionMs.coerceAtLeast(0L) / 1000.0
    val progressPercent = durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { (currentSeconds / it).coerceIn(0.0, 1.0) }
    return ContinueListeningItem(
        book = book,
        progressPercent = progressPercent,
        currentTimeSeconds = currentSeconds
    )
}
