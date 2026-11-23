package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAuthorCountsTest {
    @Test fun countsAllBooksRatherThanOnlyTheNewestBook() {
        val books = listOf(book("new", "Writer"), book("old", "Writer"), book("other", "Other"))
        assertEquals(mapOf("writer" to 2, "other" to 1), homeAuthorBookCounts(books))
    }

    @Test fun coauthorsAndDuplicateRowsCountEachBookOncePerAuthor() {
        val first = book("one", " Writer; OTHER and writer ")
        assertEquals(mapOf("writer" to 2, "other" to 1),
            homeAuthorBookCounts(listOf(first, first, book("two", "WRITER"), book("none", " "))))
    }

    @Test fun emptyLibraryDoesNotInventCounts() {
        assertEquals(emptyMap<String, Int>(), homeAuthorBookCounts(emptyList()))
    }

    private fun book(id: String, authors: String) = BookSummary(id = id, libraryId = "books",
        title = id, authorName = authors, narratorName = null, durationSeconds = null, coverUrl = null)
}
