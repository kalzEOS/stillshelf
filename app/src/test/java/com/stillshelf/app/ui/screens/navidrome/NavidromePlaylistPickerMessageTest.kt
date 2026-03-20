package com.stillshelf.app.ui.screens.navidrome

import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromePlaylistPickerMessageTest {

    @Test
    fun buildNavidromePlaylistAddMessage_returnsDuplicateSingleMessage() {
        val message = buildNavidromePlaylistAddMessage(
            playlistName = "Favorites",
            addedCount = 0,
            duplicateCount = 1
        )

        assertEquals("This song is already in \"Favorites\"", message)
    }

    @Test
    fun buildNavidromePlaylistAddMessage_returnsDuplicateBulkMessage() {
        val message = buildNavidromePlaylistAddMessage(
            playlistName = "Favorites",
            addedCount = 0,
            duplicateCount = 3
        )

        assertEquals("All selected songs are already in \"Favorites\"", message)
    }

    @Test
    fun buildNavidromePlaylistAddMessage_returnsMixedOutcomeMessage() {
        val message = buildNavidromePlaylistAddMessage(
            playlistName = "Favorites",
            addedCount = 2,
            duplicateCount = 1
        )

        assertEquals("Added 2 songs to \"Favorites\". 1 already there", message)
    }

    @Test
    fun buildNavidromePlaylistAddMessage_returnsSingleAddMessage() {
        val message = buildNavidromePlaylistAddMessage(
            playlistName = "Favorites",
            addedCount = 1,
            duplicateCount = 0
        )

        assertEquals("Added to \"Favorites\"", message)
    }
}
