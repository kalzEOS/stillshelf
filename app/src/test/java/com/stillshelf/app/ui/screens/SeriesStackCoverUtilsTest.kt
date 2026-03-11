package com.stillshelf.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesStackCoverUtilsTest {

    @Test
    fun resolveSeriesStackDisplayCovers_usesThreeDistinctCoversWhenAvailable() {
        val covers = resolveSeriesStackDisplayCovers(
            coverUrls = listOf("front", "left", "right", "front"),
            layerCount = 3
        )

        assertEquals(listOf("right", "left", "front"), covers)
    }

    @Test
    fun resolveSeriesStackDisplayCovers_avoidsRepeatingWhenTwoDistinctCoversExist() {
        val covers = resolveSeriesStackDisplayCovers(
            coverUrls = listOf("front", "left", "front"),
            layerCount = 3
        )

        assertEquals(listOf("left", "front"), covers)
    }

    @Test
    fun resolveSeriesStackDisplayCovers_repeatsOnlyWhenOneCoverExists() {
        val covers = resolveSeriesStackDisplayCovers(
            coverUrls = listOf("front", "front"),
            layerCount = 3
        )

        assertEquals(listOf("front", "front", "front"), covers)
    }
}
