package com.stillshelf.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesNameMatchingTest {

    @Test
    fun matchesSeriesName_acceptsExactNormalizedMatch() {
        assertTrue(
            matchesSeriesName(
                normalizedSeries = "van dusen",
                candidateSeriesNames = listOf("Van   Dusen #1")
            )
        )
    }

    @Test
    fun matchesSeriesName_rejectsSubstringOverlap() {
        assertFalse(
            matchesSeriesName(
                normalizedSeries = "van dusen",
                candidateSeriesNames = listOf("Professor van Dusen")
            )
        )
    }
}
