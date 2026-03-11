package com.stillshelf.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesBrowseNestingTest {

    @Test
    fun isNestedSeriesCandidate_returnsTrueWhenAllBooksExistInLargerSeries() {
        val parent = testCandidate(
            id = "parent",
            name = "Aesop's Fables",
            matchedBookIds = setOf("1", "2", "3")
        )
        val child = testCandidate(
            id = "child",
            name = "Sub-Series",
            matchedBookIds = setOf("3"),
            inferredParentKeys = setOf("parent")
        )

        assertTrue(isNestedSeriesCandidate(child, listOf(parent, child)))
    }

    @Test
    fun isNestedSeriesCandidate_returnsFalseForStandaloneSeries() {
        val parent = testCandidate(
            id = "parent",
            name = "Aesop's Fables",
            matchedBookIds = setOf("1", "2", "3")
        )
        val standalone = testCandidate(
            id = "standalone",
            name = "Moral Letters",
            matchedBookIds = setOf("9")
        )

        assertFalse(isNestedSeriesCandidate(standalone, listOf(parent, standalone)))
    }

    @Test
    fun isNestedSeriesCandidate_returnsFalseWithoutExplicitParentSignal() {
        val parent = testCandidate(
            id = "parent",
            name = "Collection A",
            matchedBookIds = setOf("1", "2")
        )
        val overlapping = testCandidate(
            id = "other",
            name = "Collection B",
            matchedBookIds = setOf("1")
        )

        assertFalse(isNestedSeriesCandidate(overlapping, listOf(parent, overlapping)))
    }

    private fun testCandidate(
        id: String,
        name: String,
        matchedBookIds: Set<String>,
        inferredParentKeys: Set<String> = emptySet()
    ) = SeriesBrowseCandidate(
        card = SeriesBrowseCard(
            id = id,
            name = name,
            subtitle = null,
            coverUrls = emptyList()
        ),
        matchedBookIds = matchedBookIds,
        inferredParentKeys = inferredParentKeys
    )
}
