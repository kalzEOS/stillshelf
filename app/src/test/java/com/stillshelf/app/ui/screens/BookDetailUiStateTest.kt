package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailUiStateTest {
    @Test
    fun beginRefresh_keepsCachedContentVisibleWhileRefreshing() {
        val initial = BookDetailUiState(detail = sampleDetail(), isLoading = false)

        val updated = initial.beginRefresh(hasLocalDetail = true, silent = false)

        assertFalse(updated.isLoading)
        assertTrue(updated.isRefreshing)
        assertNotNull(updated.detail)
    }

    @Test
    fun beginRefresh_usesBlockingLoaderOnlyOnCacheMiss() {
        val initial = BookDetailUiState(isLoading = false, detail = null)

        val updated = initial.beginRefresh(hasLocalDetail = false, silent = false)

        assertTrue(updated.isLoading)
        assertFalse(updated.isRefreshing)
    }

    @Test
    fun applyPersistedDetail_populatesCachedContentImmediately() {
        val detail = sampleDetail()

        val updated = BookDetailUiState(isLoading = true).applyPersistedDetail(detail)

        assertFalse(updated.isLoading)
        assertEquals(detail, updated.detail)
        assertEquals(detail.book.progressPercent, updated.progressPercent)
        assertEquals(detail.book.currentTimeSeconds, updated.currentTimeSeconds)
    }

    @Test
    fun applyPersistedDetail_nullDoesNotClearExistingCachedDetail() {
        val detail = sampleDetail()
        val initial = BookDetailUiState(
            isLoading = true,
            isRefreshing = true,
            detail = detail
        )

        val updated = initial.applyPersistedDetail(null)

        assertFalse(updated.isLoading)
        assertFalse(updated.isRefreshing)
        assertEquals(detail, updated.detail)
    }

    @Test
    fun resolveOfflineBookDetail_buildsFallbackFromCompletedDownload() {
        val detail = resolveOfflineBookDetail(
            download = DownloadItem(
                serverId = "server-1",
                libraryId = "library-1",
                bookId = "book-1",
                title = "Offline Sample",
                authorName = "Offline Author",
                coverUrl = "file:///cover.jpg",
                durationSeconds = 900.0,
                status = DownloadStatus.Completed,
                progressPercent = 100,
                localPath = "/tmp/book.mp3"
            ),
            currentDetail = null
        )

        assertEquals("Offline Sample", detail.book.title)
        assertEquals("Offline Author", detail.book.authorName)
        assertEquals("library-1", detail.book.libraryId)
        assertEquals(900.0, detail.book.durationSeconds)
        assertTrue(detail.chapters.isEmpty())
        assertTrue(detail.bookmarks.isEmpty())
    }

    private fun sampleDetail(): BookDetail {
        val book = BookSummary(
            id = "book-1",
            libraryId = "library-1",
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 600.0,
            coverUrl = null,
            progressPercent = 0.25,
            currentTimeSeconds = 150.0,
            isFinished = false
        )
        return BookDetail(
            book = book,
            description = "Description",
            publishedYear = "2025",
            sizeBytes = 1_024L,
            chapters = emptyList(),
            bookmarks = emptyList()
        )
    }
}
