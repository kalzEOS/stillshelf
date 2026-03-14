package com.stillshelf.app.ui.common

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus

internal fun buildLibraryScopedDownloadKey(
    libraryId: String?,
    bookId: String
): String? {
    val normalizedLibraryId = libraryId?.trim().orEmpty()
    val normalizedBookId = bookId.trim()
    if (normalizedLibraryId.isBlank() || normalizedBookId.isBlank()) return null
    return "$normalizedLibraryId|$normalizedBookId"
}

internal fun BookSummary.downloadUiKey(): String? = buildLibraryScopedDownloadKey(
    libraryId = libraryId,
    bookId = id
)

internal fun DownloadItem.downloadUiKey(): String? = buildLibraryScopedDownloadKey(
    libraryId = libraryId,
    bookId = bookId
)

internal fun List<DownloadItem>.completedDownloadUiKeys(): Set<String> {
    return asSequence()
        .filter { it.status == DownloadStatus.Completed }
        .mapNotNull { it.downloadUiKey() }
        .toSet()
}

internal fun List<DownloadItem>.activeDownloadProgressByUiKey(): Map<String, Int> {
    return asSequence()
        .filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
        .mapNotNull { item ->
            item.downloadUiKey()?.let { key ->
                key to item.progressPercent.coerceIn(0, 100)
            }
        }
        .toMap()
}

internal fun Set<String>.containsDownloadedBook(book: BookSummary?): Boolean {
    if (book == null) return false
    val scopedKey = book.downloadUiKey()
    return when {
        scopedKey != null && contains(scopedKey) -> true
        else -> contains(book.id.trim())
    }
}

internal fun Map<String, Int>.downloadProgressForBook(book: BookSummary?): Int? {
    if (book == null) return null
    val scopedKey = book.downloadUiKey()
    return when {
        scopedKey != null && containsKey(scopedKey) -> this[scopedKey]
        else -> this[book.id.trim()]
    }
}
