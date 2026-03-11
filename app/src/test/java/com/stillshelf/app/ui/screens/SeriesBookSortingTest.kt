package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesBookSortingTest {

    @Test
    fun sortSeriesBooksForDisplay_keepsPrimarySeriesBooksAheadOfSubseries() {
        val sorted = sortSeriesBooksForDisplay(
            books = listOf(
                testBook(title = "Side Story 1", seriesName = "Main Saga: Side Stories", seriesSequence = 1.0),
                testBook(title = "Book 1", seriesName = "Main Saga", seriesSequence = 1.0),
                testBook(title = "Book 2", seriesName = "Main Saga", seriesSequence = 2.0)
            ),
            targetSeriesName = "Main Saga"
        )

        assertEquals(listOf("Book 1", "Book 2", "Side Story 1"), sorted.map { it.title })
    }

    @Test
    fun sortSeriesBooksForDisplay_groupsSubseriesDeterministically() {
        val sorted = sortSeriesBooksForDisplay(
            books = listOf(
                testBook(title = "B", seriesName = "Main Saga: Beta", seriesSequence = 2.0),
                testBook(title = "A", seriesName = "Main Saga: Alpha", seriesSequence = 2.0),
                testBook(title = "A2", seriesName = "Main Saga: Alpha", seriesSequence = 3.0)
            ),
            targetSeriesName = "Main Saga"
        )

        assertEquals(listOf("A", "A2", "B"), sorted.map { it.title })
    }

    private fun testBook(
        title: String,
        seriesName: String,
        seriesSequence: Double?
    ) = BookSummary(
        id = title,
        libraryId = "library",
        title = title,
        authorName = "Author",
        narratorName = null,
        durationSeconds = null,
        coverUrl = null,
        seriesName = seriesName,
        seriesSequence = seriesSequence
    )
}
