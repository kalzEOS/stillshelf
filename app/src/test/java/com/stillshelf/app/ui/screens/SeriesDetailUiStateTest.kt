package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.SeriesDetailEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailUiStateTest {
    @Test
    fun applyPersistedEntries_ignoresInitialEmptyEmission() {
        val initial = SeriesDetailUiState(isLoading = true, hasLoadedOnce = false)

        val updated = initial.applyPersistedEntries(emptyList())

        assertEquals(initial, updated)
    }

    @Test
    fun applyPersistedEntries_clearsEntriesAfterLoadedEmptyEmission() {
        val initial = SeriesDetailUiState(
            isLoading = false,
            entries = listOf(sampleEntry("book-1")),
            hasLoadedOnce = true
        )

        val updated = initial.applyPersistedEntries(emptyList())

        assertTrue(updated.entries.isEmpty())
        assertFalse(updated.isLoading)
        assertTrue(updated.hasLoadedOnce)
    }

    @Test
    fun applyPersistedEntries_showsCachedContentImmediately() {
        val entries = listOf(sampleEntry("book-1"))

        val updated = SeriesDetailUiState(isLoading = true).applyPersistedEntries(entries)

        assertFalse(updated.isLoading)
        assertEquals(entries, updated.entries)
        assertTrue(updated.hasLoadedOnce)
    }

    @Test
    fun applyRefreshSuccess_replacesStaleEntriesWithEmptyResult() {
        val initial = SeriesDetailUiState(
            isRefreshing = true,
            entries = listOf(sampleEntry("book-1")),
            hasLoadedOnce = true
        )

        val updated = initial.applyRefreshSuccess(
            entries = emptyList(),
            hasCollapsibleSubseries = false
        )

        assertTrue(updated.entries.isEmpty())
        assertFalse(updated.isRefreshing)
        assertFalse(updated.isLoading)
        assertTrue(updated.hasLoadedOnce)
    }

    @Test
    fun beginRefresh_keepsVisibleContentDuringUserRefresh() {
        val initial = SeriesDetailUiState(entries = listOf(sampleEntry("book-1")), isLoading = false)

        val updated = initial.beginRefresh(
            hasLocalEntries = true,
            silent = false,
            isUserRefresh = true
        )

        assertFalse(updated.isLoading)
        assertTrue(updated.isRefreshing)
        assertEquals(initial.entries, updated.entries)
    }

    private fun sampleEntry(bookId: String): SeriesDetailEntry.BookItem {
        return SeriesDetailEntry.BookItem(
            book = BookSummary(
                id = bookId,
                libraryId = "library-1",
                title = "Sample",
                authorName = "Author",
                narratorName = null,
                durationSeconds = 60.0,
                coverUrl = null,
                seriesName = null,
                seriesNames = emptyList(),
                seriesIds = emptyList(),
                seriesSequence = null,
                genres = emptyList(),
                publishedYear = null,
                addedAtMs = null,
                progressPercent = null,
                currentTimeSeconds = null,
                isFinished = false
            )
        )
    }
}
