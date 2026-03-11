package com.stillshelf.app.data.api

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudiobookshelfApiTest {

    private val api = AudiobookshelfApi(OkHttpClient())

    @Test
    fun parseLibraryItem_parsesCollapsedSeriesWithoutMedia() {
        val parsed = api.buildLibraryItemDto(
            id = "parent-item",
            libraryId = "library-1",
            title = null,
            relPath = null,
            authorName = null,
            narratorName = null,
            durationSeconds = null,
            seriesNames = emptyList(),
            seriesIds = emptyList(),
            seriesSequence = null,
            genres = emptyList(),
            publishedYear = null,
            addedAtMs = null,
            progressPercent = null,
            currentTimeSeconds = null,
            isFinished = false,
            collapsedSeries = AudiobookshelfCollapsedSeriesDto(
                id = "series-child",
                name = "Sub-Series",
                bookCount = 1,
                libraryItemIds = listOf("book-1")
            )
        )

        assertNotNull(parsed)
        assertEquals("Sub-Series", parsed?.title)
        assertEquals("Unknown Author", parsed?.authorName)
        assertEquals("series-child", parsed?.collapsedSeries?.id)
        assertEquals(listOf("book-1"), parsed?.collapsedSeries?.libraryItemIds)
    }

    @Test
    fun parseLibraryItem_preservesCollapsedSeriesAlongsideMediaMetadata() {
        val parsed = api.buildLibraryItemDto(
            id = "parent-item",
            libraryId = "library-1",
            title = "Parent",
            relPath = null,
            authorName = "Author",
            narratorName = null,
            durationSeconds = 123.0,
            seriesNames = emptyList(),
            seriesIds = emptyList(),
            seriesSequence = null,
            genres = emptyList(),
            publishedYear = null,
            addedAtMs = null,
            progressPercent = null,
            currentTimeSeconds = null,
            isFinished = false,
            collapsedSeries = AudiobookshelfCollapsedSeriesDto(
                id = "series-child",
                name = "Sub-Series",
                bookCount = 2,
                sequenceLabel = "6",
                libraryItemIds = listOf("book-6")
            )
        )

        assertNotNull(parsed)
        assertEquals("Parent", parsed?.title)
        assertEquals("Sub-Series", parsed?.collapsedSeries?.name)
        assertEquals("6", parsed?.collapsedSeries?.sequenceLabel)
    }
}
