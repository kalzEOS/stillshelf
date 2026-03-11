package com.stillshelf.app.ui.screens

import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBrowseMatchCompletenessTest {

    @Test
    fun isSeriesBrowseMatchComplete_returnsFalseWhenNoBooksMatched() {
        assertFalse(
            isSeriesBrowseMatchComplete(
                matchedBooks = emptyList(),
                expectedCount = null
            )
        )
    }

    @Test
    fun isSeriesBrowseMatchComplete_returnsFalseWhenMatchedBooksAreUnderExpectedCount() {
        assertFalse(
            isSeriesBrowseMatchComplete(
                matchedBooks = listOf(testBook("1")),
                expectedCount = 2
            )
        )
    }

    @Test
    fun isSeriesBrowseMatchComplete_returnsTrueWhenExpectedCountIsSatisfied() {
        assertTrue(
            isSeriesBrowseMatchComplete(
                matchedBooks = listOf(testBook("1"), testBook("2")),
                expectedCount = 2
            )
        )
    }

    @Test
    fun isSeriesBrowseMatchComplete_returnsTrueForAnyNonEmptyMatchWithoutExpectedCount() {
        assertTrue(
            isSeriesBrowseMatchComplete(
                matchedBooks = listOf(testBook("1")),
                expectedCount = null
            )
        )
    }

    private fun testBook(id: String) = BookSummary(
        id = id,
        libraryId = "library",
        title = "Title $id",
        authorName = "Author",
        narratorName = null,
        durationSeconds = null,
        coverUrl = null
    )
}
