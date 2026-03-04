package com.stillshelf.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizeDraftUtilsTest {

    @Test
    fun ensureListenAgainSection_appendsWhenMissing() {
        val rows = listOf(
            ToggleSectionItem(HomeSectionIds.CONTINUE, "Continue Listening"),
            ToggleSectionItem(HomeSectionIds.RECENTLY_ADDED, "Recently Added")
        )

        val updated = ensureListenAgainSection(rows)

        assertEquals(3, updated.size)
        assertEquals(HomeSectionIds.LISTEN_AGAIN, updated.last().id)
    }

    @Test
    fun moveSectionRow_movesItemToTargetIndex() {
        val rows = listOf(
            ToggleSectionItem("a", "A"),
            ToggleSectionItem("b", "B"),
            ToggleSectionItem("c", "C")
        )

        val moved = moveSectionRow(rows, from = 0, to = 2)

        assertEquals(listOf("b", "c", "a"), moved.map { it.id })
    }

    @Test
    fun toggleHiddenSection_addsAndRemovesSameId() {
        val added = toggleHiddenSection(hidden = emptySet(), id = "series")
        assertTrue("series" in added)

        val removed = toggleHiddenSection(hidden = added, id = "series")
        assertTrue("series" !in removed)
    }
}
