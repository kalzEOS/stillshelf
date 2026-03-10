package com.stillshelf.app.ui.common

import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.util.hasMeaningfulStartedProgress
import com.stillshelf.app.playback.controller.PlaybackController

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
