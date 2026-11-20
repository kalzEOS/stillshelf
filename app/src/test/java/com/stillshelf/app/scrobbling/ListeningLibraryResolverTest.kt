package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.*
import org.junit.Assert.*
import org.junit.Test
import io.mockk.*
import kotlinx.coroutines.test.runTest
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository

class ListeningLibraryResolverTest {
    @Test fun matchingArtistGetsLibraryDestinationAndArtworkWithoutChangingProviderCount() {
        val entry = ListeningEntry("  كاظم الساهر ", plays = 99, detailsUrl = "https://musicbrainz.org/artist/test")
        val library = NavidromeSearchResults(listOf(NavidromeArtist("artist", "كاظم الساهر", 3, "cover", "image")), emptyList(), emptyList())
        val resolved = matchListeningLibraryEntry(entry, "artist", library)
        assertEquals("artist", resolved.artistId)
        assertEquals("image", resolved.artworkUrl)
        assertEquals(99L, resolved.plays)
        assertEquals(entry.detailsUrl, resolved.detailsUrl)
    }

    @Test fun albumsRequireMatchingArtistAndAmbiguousTitlesDoNotOpenWrongItem() {
        val right = NavidromeAlbum("right", "Album", "Artist", null, null, 1, null, "cover", null)
        val wrong = right.copy(id = "wrong", artistName = "Other artist")
        val library = NavidromeSearchResults(emptyList(), listOf(wrong, right), emptyList())
        val entry = ListeningEntry("Album", "Artist", plays = 8)
        assertEquals("right", matchListeningLibraryEntry(entry, "album", library).albumId)
        val ambiguous = matchListeningLibraryEntry(entry, "album", library.copy(albums = listOf(right, right.copy(id = "duplicate"))))
        assertNull(ambiguous.albumId)
        assertEquals(2, ambiguous.albumChoices.size)
    }

    @Test fun tracksUseMatchingAlbumArtworkAndNeverInferCountsFromLibrary() {
        val track = NavidromeTrack("song", "Song", "Artist", "Album", "album", "artist", null, 180, "cover", "stream", null, null)
        val library = NavidromeSearchResults(emptyList(), emptyList(), listOf(track))
        val entry = ListeningEntry("Song", "Artist", plays = 5, albumName = "Album")
        val resolved = matchListeningLibraryEntry(entry, "track", library)
        assertEquals("album", resolved.albumId)
        assertEquals("cover", resolved.artworkUrl)
        assertEquals(5L, resolved.plays)
        assertNull(matchListeningLibraryEntry(entry.copy(subtitle = "Other"), "track", library).albumId)
    }

    @Test fun arabicVariantsAndCurlyApostrophesMatchWithoutGuessingOtherArtists() {
        val artist = NavidromeArtist("arabic", "إلـيسا", 1, null, null)
        val artists = NavidromeSearchResults(listOf(artist), emptyList(), emptyList())
        assertEquals("arabic", matchListeningLibraryEntry(ListeningEntry("\u200fاليسا"), "artist", artists).artistId)
        val track = NavidromeTrack("song", "It's Not Over", "Daughtry", "Daughtry", "album", null, null, 180, null, "", null, null)
        val songs = NavidromeSearchResults(emptyList(), emptyList(), listOf(track))
        assertEquals("album", matchListeningLibraryEntry(ListeningEntry("It’s Not Over", "Daughtry"), "track", songs).albumId)
        assertNull(matchListeningLibraryEntry(ListeningEntry("It’s Not Over", "Chris"), "track", songs).albumId)
        assertEquals(listeningLibraryName("كاظم الساهر"), listeningLibraryName("كَاظِم السَّاهِر"))
    }

    @Test fun stableArtistIdentityMatchesDifferentScriptAndRejectsAConflictingIdentity() {
        val mbid = "0b91b4e7-5c97-4446-8665-27fe6026cd2f"
        val entry = ListeningEntry("كاظم الساهر", musicBrainzArtistId = mbid)
        val right = NavidromeArtist("right", "Kadim Al Sahir", 1, null, null, mbid)
        val wrong = right.copy(id = "wrong", name = entry.title, musicBrainzId = "b3f5bc93-c962-458f-961e-d861e6e0c828")
        assertEquals("right", matchListeningLibraryEntry(entry, "artist", NavidromeSearchResults(listOf(wrong, right), emptyList(), emptyList())).artistId)
        assertNull(matchListeningLibraryEntry(entry, "artist", NavidromeSearchResults(listOf(wrong), emptyList(), emptyList())).artistId)
    }

    @Test fun artistIndexFindsAnExistingArtistOutsideTheSmallSearchPage() = runTest {
        val entry = ListeningEntry("كاظم الساهر", musicBrainzArtistId = "0b91b4e7-5c97-4446-8665-27fe6026cd2f")
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
            coEvery { fetchArtists(any()) } returns AppResult.Success(listOf(NavidromeArtist("local", "Kadim Al Sahir", 1, "cover", null, entry.musicBrainzArtistId)))
        }
        val resolved = ListeningLibraryResolver(repo).resolve(entry, "artist")
        assertEquals("local", resolved.artistId)
        assertEquals("cover", resolved.artworkUrl)
        assertEquals(entry.title, resolved.title)
    }

    @Test fun tappedArtistCanUseVerifiedAliasesWhenLocalFilesHaveNoMbid() = runTest {
        val mbid = "b3f5bc93-c962-458f-961e-d861e6e0c828"
        val entry = ListeningEntry("فضل شاكر", musicBrainzArtistId = mbid)
        val aliases = mockk<ListeningArtistAliases> { coEvery { names(mbid, true) } returns setOf("Fadel Shaker", "فضل شاكر") }
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
            coEvery { fetchArtists(any()) } returns AppResult.Success(listOf(NavidromeArtist("local", "Fadel Shaker", 1, null, "image")))
        }
        assertEquals("local", ListeningLibraryResolver(repo, aliases).resolve(entry, "artist", true).artistId)
        coVerify(exactly = 1) { aliases.names(mbid, true) }
    }

    @Test fun daughtryAlbumIdentitySelectsTheCorrectEditionOrOffersActualLibraryChoices() {
        val release = "59f3c45f-0cfc-4b56-a04c-9479970992c6"
        val album = NavidromeAlbum("original", "Daughtry", "Daughtry", "artist", 2006, 12, null, null, null, release)
        val deluxe = album.copy(id = "deluxe", musicBrainzId = "a7d59a07-0b66-4b4c-adeb-57dbc879da3a", songCount = 15)
        val results = NavidromeSearchResults(emptyList(), listOf(deluxe, album), emptyList())
        val entry = ListeningEntry("Daughtry", "Daughtry", musicBrainzReleaseId = release)
        assertEquals("original", matchListeningLibraryEntry(entry, "album", results).albumId)
        val choice = matchListeningLibraryEntry(entry.copy(musicBrainzReleaseId = null), "album", results)
        assertNull(choice.albumId)
        assertEquals(setOf("original", "deluxe"), choice.albumChoices.map { it.id }.toSet())
    }

    @Test fun albumLookupUsesArtistDiscographyBeyondSearchPageAndOffersAnEditionWithoutGuessing() = runTest {
        val artist = NavidromeArtist("artist", "Daughtry", 10, "cover", "image")
        val album = NavidromeAlbum("edition", "Daughtry (Deluxe Edition)", "Daughtry", "artist", 2006, 15, null, "cover", null)
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(listOf(artist), emptyList(), emptyList()))
            coEvery { fetchArtistDetail("artist", any()) } returns AppResult.Success(NavidromeArtistDetail(artist, listOf(album)))
        }
        val result = ListeningLibraryResolver(repo).resolve(ListeningEntry("Daughtry", "Daughtry"), "album", true)
        assertNull(result.albumId)
        assertEquals("edition", result.albumChoices.single().id)
    }
}
