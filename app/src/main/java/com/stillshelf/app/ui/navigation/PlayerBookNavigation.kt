package com.stillshelf.app.ui.navigation

import androidx.navigation.NavHostController

internal fun NavHostController.navigateToPlayingBook(bookId: String) {
    if (bookId.isBlank()) return
    val entry = currentBackStackEntry
    if (entry?.destination?.route == DetailRoute.BOOK_PATTERN &&
        entry.arguments?.getString(DetailRoute.BOOK_ID_ARG) == bookId) return

    // Single-top reuses the detail ViewModel's original SavedStateHandle.
    // A different book must get a new entry, even when a book is already open.
    navigate(DetailRoute.book(bookId))
}
