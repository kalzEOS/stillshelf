package com.stillshelf.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshPolicyTest {

    @Test
    fun resolveReadyHomeLibraryId_hidesPendingLibrarySelection() {
        assertNull(
            resolveReadyHomeLibraryId(
                activeLibraryId = "library-1",
                requiresLibrarySelection = true
            )
        )
    }

    @Test
    fun resolveReadyHomeLibraryId_returnsLibraryWhenSelectionIsReady() {
        assertEquals(
            "library-1",
            resolveReadyHomeLibraryId(
                activeLibraryId = "library-1",
                requiresLibrarySelection = false
            )
        )
    }

    @Test
    fun shouldPreserveHomeContentDuringPendingSelection_requiresPendingLibrary() {
        assertTrue(
            shouldPreserveHomeContentDuringPendingSelection(
                activeLibraryId = "library-1",
                requiresLibrarySelection = true
            )
        )
    }

    @Test
    fun shouldPreserveHomeContentDuringPendingSelection_returnsFalseWithoutPendingLibrary() {
        assertFalse(
            shouldPreserveHomeContentDuringPendingSelection(
                activeLibraryId = null,
                requiresLibrarySelection = true
            )
        )
    }

    @Test
    fun shouldPreserveHomeContentDuringPendingSelection_returnsFalseWhenSelectionIsReady() {
        assertFalse(
            shouldPreserveHomeContentDuringPendingSelection(
                activeLibraryId = "library-1",
                requiresLibrarySelection = false
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresForegroundTransition() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = true,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = "library-1"
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresVisibleHomeScreen() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = false,
                activeLibraryId = "library-1"
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresActiveLibrary() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = null
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_refreshesWhenReturningToVisibleHome() {
        assertTrue(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = "library-1"
            )
        )
    }

    @Test
    fun shouldShowLoadingForVisibleHomeRefresh_returnsTrueWhenHomeIsEmpty() {
        assertTrue(shouldShowLoadingForVisibleHomeRefresh(HomeUiState()))
    }

    @Test
    fun shouldShowLoadingForVisibleHomeRefresh_returnsFalseWhenAnyHomeContentExists() {
        assertFalse(
            shouldShowLoadingForVisibleHomeRefresh(
                HomeUiState(
                    recentlyAdded = listOf(
                        bookSummary("book-1")
                    )
                )
            )
        )
    }

    private fun bookSummary(id: String) = com.stillshelf.app.core.model.BookSummary(
        id = id,
        libraryId = "library-1",
        title = "Book",
        authorName = "Author",
        narratorName = null,
        narratorNames = emptyList(),
        durationSeconds = 60.0,
        coverUrl = null,
        seriesName = null,
        seriesNames = emptyList(),
        seriesSequence = null,
        seriesIds = emptyList(),
        genres = emptyList(),
        publishedYear = null,
        addedAtMs = null,
        authorIds = emptyList(),
        progressPercent = null,
        currentTimeSeconds = null,
        isFinished = false
    )
}
