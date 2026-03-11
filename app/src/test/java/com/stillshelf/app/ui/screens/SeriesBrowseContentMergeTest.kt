package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.SeriesDetailEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBrowseContentMergeTest {

    @Test
    fun mergeSeriesBrowseBooks_mergesSeriesContentBooksAndIgnoresSubseriesEntries() {
        val initial = listOf(
            testBook(
                id = "book-1",
                coverUrl = null,
                seriesIds = listOf("series-1")
            )
        )

        val merged = mergeSeriesBrowseBooks(
            initialMatchedBooks = initial,
            seriesEntries = listOf(
                SeriesDetailEntry.SubseriesItem(
                    id = "child-series",
                    name = "Child Series",
                    bookCount = 1,
                    coverUrl = "child-cover"
                ),
                SeriesDetailEntry.BookItem(
                    testBook(
                        id = "book-1",
                        coverUrl = "resolved-cover",
                        seriesName = "Series Name",
                        seriesIds = listOf("series-1"),
                        progressPercent = 0.5
                    )
                ),
                SeriesDetailEntry.BookItem(
                    testBook(
                        id = "book-2",
                        coverUrl = "cover-2",
                        seriesName = "Series Name",
                        seriesIds = listOf("series-1")
                    )
                )
            ),
            expectedCount = null
        )

        assertEquals(listOf("book-1", "book-2"), merged.map { it.id })
        assertEquals("resolved-cover", merged.first().coverUrl)
        assertEquals("Series Name", merged.first().seriesName)
        assertEquals(0.5, merged.first().progressPercent ?: 0.0, 0.0)
    }

    @Test
    fun mergeSeriesBrowseBooks_stopsWhenExpectedCountIsReached() {
        val merged = mergeSeriesBrowseBooks(
            initialMatchedBooks = emptyList(),
            seriesEntries = listOf(
                SeriesDetailEntry.BookItem(testBook(id = "book-1")),
                SeriesDetailEntry.BookItem(testBook(id = "book-2")),
                SeriesDetailEntry.BookItem(testBook(id = "book-3"))
            ),
            expectedCount = 2
        )

        assertEquals(listOf("book-1", "book-2"), merged.map { it.id })
        assertTrue(merged.none { it.id == "book-3" })
    }

    private fun testBook(
        id: String,
        coverUrl: String? = "cover",
        seriesName: String? = null,
        seriesIds: List<String> = emptyList(),
        progressPercent: Double? = null
    ) = BookSummary(
        id = id,
        libraryId = "library",
        title = "Title $id",
        authorName = "Author",
        narratorName = null,
        durationSeconds = null,
        coverUrl = coverUrl,
        seriesName = seriesName,
        seriesNames = listOfNotNull(seriesName),
        seriesIds = seriesIds,
        seriesSequence = null,
        genres = emptyList(),
        publishedYear = null,
        addedAtMs = null,
        progressPercent = progressPercent,
        currentTimeSeconds = null,
        isFinished = false
    )
}
