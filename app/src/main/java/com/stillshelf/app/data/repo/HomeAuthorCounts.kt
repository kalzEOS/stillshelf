package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.BookSummary

/** The complete active-library snapshot, never just the recent-items carousel. */
internal fun homeAuthorBookCounts(books: List<BookSummary>): Map<String, Int> = books
    .distinctBy { it.id }
    .flatMap { book ->
        book.authorName.split(Regex("\\s*(?:,|;|&|\\s+and\\s+)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    .groupingBy { it }
    .eachCount()
