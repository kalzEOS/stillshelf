package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookSummary
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
