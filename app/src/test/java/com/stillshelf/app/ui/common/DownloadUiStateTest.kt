package com.stillshelf.app.ui.common

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUiStateTest {
    @Test
    fun containsDownloadedBook_respectsLibraryScopeForSameBookId() {
        val libraryOneBook = sampleBook(bookId = "shared-book", libraryId = "library-one")
        val libraryTwoBook = sampleBook(bookId = "shared-book", libraryId = "library-two")
        val completedKeys = setOfNotNull(libraryOneBook.downloadUiKey())

        assertTrue(completedKeys.containsDownloadedBook(libraryOneBook))
        assertFalse(completedKeys.containsDownloadedBook(libraryTwoBook))
    }

    @Test
    fun activeDownloadProgressByUiKey_keepsLibraryVariantsSeparate() {
        val downloads = listOf(
            sampleDownload(bookId = "shared-book", libraryId = "library-one", progressPercent = 35),
            sampleDownload(bookId = "shared-book", libraryId = "library-two", progressPercent = 80)
        )

        val progressByKey = downloads.activeDownloadProgressByUiKey()

        assertEquals(35, progressByKey.downloadProgressForBook(sampleBook("shared-book", "library-one")))
        assertEquals(80, progressByKey.downloadProgressForBook(sampleBook("shared-book", "library-two")))
    }

    private fun sampleBook(bookId: String, libraryId: String): BookSummary {
        return BookSummary(
            id = bookId,
            libraryId = libraryId,
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 120.0,
            coverUrl = null
        )
    }

    private fun sampleDownload(bookId: String, libraryId: String, progressPercent: Int): DownloadItem {
        return DownloadItem(
            serverId = "server-1",
            libraryId = libraryId,
            bookId = bookId,
            title = "Sample",
            authorName = "Author",
            coverUrl = null,
            durationSeconds = 120.0,
            status = DownloadStatus.Downloading,
            progressPercent = progressPercent
        )
    }
}
