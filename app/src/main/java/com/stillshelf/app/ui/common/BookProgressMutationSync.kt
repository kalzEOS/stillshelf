package com.stillshelf.app.ui.common

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.util.hasMeaningfulStartedProgress
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState

fun BookProgressMutation.isResetToStart(): Boolean {
    return !isFinished && !hasMeaningfulStartedProgress(
        currentTimeSeconds = currentTimeSeconds,
        durationSeconds = durationSeconds,
        progressPercent = progressPercent,
        isFinished = false
    )
}

fun BookSummary.withBookProgressMutation(mutation: BookProgressMutation): BookSummary {
    if (id != mutation.bookId) return this
    return copy(
        isFinished = mutation.isFinished,
        durationSeconds = mutation.durationSeconds ?: durationSeconds,
        progressPercent = mutation.progressPercent,
        currentTimeSeconds = mutation.currentTimeSeconds
    )
}

fun ContinueListeningItem.withBookProgressMutation(mutation: BookProgressMutation): ContinueListeningItem {
    if (book.id != mutation.bookId) return this
    return copy(
        book = book.withBookProgressMutation(mutation),
        progressPercent = mutation.progressPercent,
        currentTimeSeconds = mutation.currentTimeSeconds
    )
}

fun BookmarkEntry.withBookProgressMutation(mutation: BookProgressMutation): BookmarkEntry {
    if (book.id != mutation.bookId) return this
    return copy(book = book.withBookProgressMutation(mutation))
}

fun PlaybackController.applyBookProgressMutation(mutation: BookProgressMutation) {
    when {
        mutation.isFinished -> stopAndResetBookToStart(mutation.bookId)
        mutation.isResetToStart() -> stopAndResetBookToBeginning(mutation.bookId)
        else -> stopAndRestoreBookProgress(
            bookId = mutation.bookId,
            currentTimeSeconds = mutation.currentTimeSeconds ?: 0.0,
            durationSeconds = mutation.durationSeconds,
            isFinished = false
        )
    }
}

fun PlaybackController.applyResolvedPlaybackProgress(
    bookId: String,
    progress: PlaybackProgress,
    isFinished: Boolean
) {
    applyBookProgressMutation(
        BookProgressMutation(
            bookId = bookId,
            progressPercent = progress.progressPercent,
            currentTimeSeconds = progress.currentTimeSeconds,
            durationSeconds = progress.durationSeconds,
            isFinished = isFinished
        )
    )
}

fun PlaybackUiState.toLiveBookProgressMutation(): BookProgressMutation? {
    val activeBook = book ?: return null
    val currentTimeSeconds = positionMs.coerceAtLeast(0L) / 1000.0
    val resolvedDurationSeconds = activeBook.durationSeconds?.takeIf { it > 0.0 }
        ?: durationMs.takeIf { it > 0L }?.div(1000.0)
    val resolvedProgressPercent = resolvedDurationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { duration -> (currentTimeSeconds / duration).coerceIn(0.0, 1.0) }
        ?: activeBook.progressPercent
    return BookProgressMutation(
        bookId = activeBook.id,
        progressPercent = resolvedProgressPercent,
        currentTimeSeconds = currentTimeSeconds,
        durationSeconds = resolvedDurationSeconds,
        isFinished = activeBook.isFinished || ((resolvedProgressPercent ?: 0.0) >= 0.995)
    )
}
