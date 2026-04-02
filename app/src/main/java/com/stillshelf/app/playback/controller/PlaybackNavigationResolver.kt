package com.stillshelf.app.playback.controller

internal sealed interface PlaybackPositionCommand {
    data class SeekCurrentBook(
        val positionMs: Long,
        val shouldResume: Boolean
    ) : PlaybackPositionCommand

    data class StartBookAtPosition(
        val bookId: String,
        val positionMs: Long
    ) : PlaybackPositionCommand
}

internal fun secondsToPlaybackPositionMs(seconds: Double?): Long? {
    return seconds
        ?.coerceAtLeast(0.0)
        ?.times(1000.0)
        ?.toLong()
}

internal fun normalizeBookmarkTitle(title: String?): String? {
    return title?.trim().takeUnless { it.isNullOrBlank() }
}

internal fun resolvePlaybackPositionCommand(
    activeBookId: String?,
    targetBookId: String,
    targetPositionMs: Long,
    isPlaying: Boolean
): PlaybackPositionCommand {
    val safePositionMs = targetPositionMs.coerceAtLeast(0L)
    return if (activeBookId == targetBookId) {
        PlaybackPositionCommand.SeekCurrentBook(
            positionMs = safePositionMs,
            shouldResume = !isPlaying
        )
    } else {
        PlaybackPositionCommand.StartBookAtPosition(
            bookId = targetBookId,
            positionMs = safePositionMs
        )
    }
}

internal data class RestoredPlaybackProgressState(
    val targetMs: Long,
    val resolvedDurationMs: Long,
    val progressPercent: Double?
)

internal fun resolveRestoredPlaybackProgressState(
    currentTimeSeconds: Double,
    displayedDurationMs: Long?,
    uiDurationMs: Long?,
    requestedDurationSeconds: Double?,
    bookDurationSeconds: Double?,
    isFinished: Boolean
): RestoredPlaybackProgressState {
    val resolvedDurationMs = displayedDurationMs?.takeIf { it > 0L }
        ?: uiDurationMs?.takeIf { it > 0L }
        ?: requestedDurationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
        ?: bookDurationSeconds?.times(1000.0)?.toLong()?.coerceAtLeast(0L)
        ?: 0L
    val rawTargetMs = (currentTimeSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
    val targetMs = if (resolvedDurationMs > 0L) {
        rawTargetMs.coerceIn(0L, resolvedDurationMs)
    } else {
        rawTargetMs.coerceAtLeast(0L)
    }
    val progressPercent = when {
        isFinished -> 1.0
        resolvedDurationMs > 0L -> (targetMs.toDouble() / resolvedDurationMs.toDouble()).coerceIn(0.0, 1.0)
        else -> null
    }
    return RestoredPlaybackProgressState(
        targetMs = targetMs,
        resolvedDurationMs = resolvedDurationMs,
        progressPercent = progressPercent
    )
}
