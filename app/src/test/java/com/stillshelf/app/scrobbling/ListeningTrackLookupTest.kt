package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.*
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListeningTrackLookupTest {
    private val recording = "f9462766-8938-4bb8-8d4a-1d4f5d4b0144"
    private val entry = ListeningEntry("It’s Not Over", "Daughtry", 4, albumName = "Daughtry", musicBrainzRecordingId = recording)
    private val track = NavidromeTrack("new-flac-id", "It's Not Over", "Daughtry", "Daughtry", "current-album", "artist", 1,
        215, "cover", "stream", "FLAC", 900, musicBrainzId = recording)
    private fun results(vararg tracks: NavidromeTrack) = NavidromeSearchResults(emptyList(), emptyList(), tracks.toList())

    @Test fun recordingIdentityFollowsCurrentFlacDespiteChangedFileIdAndEditionTags() {
        val current = track.copy(albumName = "Daughtry (Deluxe Edition)")
        val resolved = matchListeningTrackEntry(entry, results(current))
        assertEquals("current-album", resolved.albumId)
        assertEquals(4L, resolved.plays)
        assertEquals(entry.title, resolved.title)
    }
    @Test fun mp3AndFlacCopiesInOneAlbumAreOneNavigationDestination() {
        val resolved = matchListeningTrackEntry(entry, results(track.copy(id = "old-mp3-id", formatLabel = "MP3"), track))
        assertEquals("current-album", resolved.albumId)
        assertTrue(resolved.albumChoices.isEmpty())
        assertEquals(4L, resolved.plays)
    }
    @Test fun differentAlbumsOrRecordingVersionsRequireExplicitInAppChoice() {
        val multiple = matchListeningTrackEntry(entry.copy(albumName = ""), results(track, track.copy(id = "another", albumId = "other-album", formatLabel = "MP3")))
        assertNull(multiple.albumId)
        assertEquals(setOf("current-album", "other-album"), multiple.albumChoices.map { it.id }.toSet())
        assertEquals(mapOf("current-album" to "FLAC", "other-album" to "MP3"), multiple.albumChoiceFormats)
        val version = matchListeningTrackEntry(entry, results(track.copy(musicBrainzId = "475e4eae-0510-4646-892c-e7788bb624bb")))
        assertNull(version.albumId)
        assertEquals("current-album", version.albumChoices.single().id)
        assertNull(matchListeningTrackEntry(entry, results(track.copy(artistName = "Other artist", musicBrainzId = null))).albumId)
    }
    @Test fun missingSongSearchFindsCurrentTrackInsideTheArtistsAlbum() = runTest {
        val artist = NavidromeArtist("artist", "Daughtry", 1, "cover", "image")
        val album = NavidromeAlbum("current-album", "Daughtry (Deluxe Edition)", "Daughtry", "artist", 2006, 15, null, "cover", null)
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(listOf(artist), emptyList(), emptyList()))
            coEvery { fetchArtistDetail("artist", true) } returns AppResult.Success(NavidromeArtistDetail(artist, listOf(album)))
            coEvery { fetchAlbumDetail("current-album", true) } returns AppResult.Success(NavidromeAlbumDetail(album, listOf(track.copy(albumName = album.name))))
        }
        val resolver = ListeningLibraryResolver(repo)
        assertNull(resolver.resolve(entry, "track").albumId)
        coVerify(exactly = 0) { repo.fetchAlbumDetail(any(), any()) }
        assertEquals("current-album", resolver.resolve(entry, "track", true).albumId)
        coVerify(exactly = 1) { repo.fetchAlbumDetail("current-album", true) }
    }
    @Test fun tapDoesNotWaitBehindThreeBlockedBackgroundSearches() = runTest {
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), false) } coAnswers { awaitCancellation() }
            coEvery { search(any(), true) } returns AppResult.Success(results(track))
        }
        val resolver = ListeningLibraryResolver(repo)
        repeat(3) { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { resolver.resolve(entry, "track") } }
        assertEquals("current-album", withTimeout(1000) { resolver.resolve(entry, "track", true) }.albumId)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test fun slowAlbumCannotDiscardTheAlreadyReturnedCurrentFlacCandidate() = runTest {
        val artist = NavidromeArtist("artist", "Daughtry", 2, "cover", "image")
        val album = NavidromeAlbum("current-album", "Daughtry", "Daughtry", "artist", 2006, 12, null, "cover", null)
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(listOf(artist), emptyList(), emptyList()))
            coEvery { fetchArtistDetail("artist", true) } returns AppResult.Success(NavidromeArtistDetail(artist, listOf(album, album.copy(id = "slow"))))
            coEvery { fetchAlbumDetail("current-album", true) } returns AppResult.Success(NavidromeAlbumDetail(album, listOf(track)))
            coEvery { fetchAlbumDetail("slow", true) } coAnswers { awaitCancellation() }
        }
        val found = ListeningLibraryResolver(repo).resolve(entry, "track", true)
        assertEquals(8_000, testScheduler.currentTime)
        assertNull(found.albumId) // Another unavailable album could contain a second edition.
        assertEquals("current-album", found.albumChoices.single().id)
        assertEquals("FLAC", found.albumChoiceFormats["current-album"])
        coVerify(exactly = 1) { repo.fetchAlbumDetail("slow", true) }
    }

    @Test fun partialLookupPreservesCallerCancellationInsteadOfOpeningChoicesAfterLeaving() = runTest {
        val artist = NavidromeArtist("artist", "Daughtry", 1, "cover", "image")
        val album = NavidromeAlbum("current-album", "Daughtry", "Daughtry", "artist", null, 1, null, null, null)
        val repo = mockk<NavidromeRepository> {
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(listOf(artist), emptyList(), emptyList()))
            coEvery { fetchArtistDetail("artist", true) } returns AppResult.Success(NavidromeArtistDetail(artist, listOf(album)))
            coEvery { fetchAlbumDetail(any(), true) } coAnswers { awaitCancellation() }
        }
        val job = async { ListeningLibraryResolver(repo).resolve(entry, "track", true) }
        runCurrent(); job.cancel()
        try { job.await(); fail("Navigation cancellation must propagate") } catch (_: CancellationException) { }
    }
}
