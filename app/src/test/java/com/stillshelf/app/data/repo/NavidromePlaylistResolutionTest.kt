package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.NavidromePlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavidromePlaylistResolutionTest {

    @Test
    fun resolveCreatedPlaylistCandidate_prefersNewPlaylistWithMatchingName() {
        val existing = NavidromePlaylist(id = "old-1", name = "Favorites")
        val created = NavidromePlaylist(id = "new-1", name = "Favorites")

        val resolved = resolveCreatedPlaylistCandidate(
            playlists = listOf(existing, created),
            playlistName = "Favorites",
            previousPlaylistIds = setOf(existing.id)
        )

        assertEquals(created, resolved)
    }

    @Test
    fun resolveCreatedPlaylistCandidate_fallsBackToSingleNewPlaylistWhenNameDiffers() {
        val existing = NavidromePlaylist(id = "old-1", name = "Favorites")
        val created = NavidromePlaylist(id = "new-1", name = "Favorites (1)")

        val resolved = resolveCreatedPlaylistCandidate(
            playlists = listOf(existing, created),
            playlistName = "Favorites",
            previousPlaylistIds = setOf(existing.id)
        )

        assertEquals(created, resolved)
    }

    @Test
    fun resolveCreatedPlaylistCandidate_returnsLastMatchingPlaylistWithoutPreviousIds() {
        val older = NavidromePlaylist(id = "old-1", name = "Favorites")
        val newer = NavidromePlaylist(id = "new-1", name = "Favorites")

        val resolved = resolveCreatedPlaylistCandidate(
            playlists = listOf(older, newer),
            playlistName = "Favorites",
            previousPlaylistIds = null
        )

        assertEquals(newer, resolved)
    }

    @Test
    fun resolveCreatedPlaylistCandidate_returnsNullWhenNothingMatches() {
        val resolved = resolveCreatedPlaylistCandidate(
            playlists = listOf(NavidromePlaylist(id = "one", name = "Road Trip")),
            playlistName = "Favorites",
            previousPlaylistIds = emptySet()
        )

        assertNull(resolved)
    }
}
