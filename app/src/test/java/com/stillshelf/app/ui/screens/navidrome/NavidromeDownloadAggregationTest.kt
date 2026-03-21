package com.stillshelf.app.ui.screens.navidrome

import com.stillshelf.app.downloads.navidrome.NavidromeDownloadItem
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeDownloadAggregationTest {

    @Test
    fun computeFullyDownloadedAlbumCountByArtistId_countsOnlyCompleteAlbums() {
        val completedItems = listOf(
            completedTrack(trackId = "a1-t1", albumId = "album-1", artistId = "artist-1", albumSongCount = 2),
            completedTrack(trackId = "a1-t2", albumId = "album-1", artistId = "artist-1", albumSongCount = 2),
            completedTrack(trackId = "a2-t1", albumId = "album-2", artistId = "artist-1", albumSongCount = 2),
            completedTrack(trackId = "b1-t1", albumId = "album-3", artistId = "artist-2", albumSongCount = 1)
        )

        val counts = computeFullyDownloadedAlbumCountByArtistId(completedItems)

        assertEquals(mapOf("artist-1" to 1, "artist-2" to 1), counts)
    }

    @Test
    fun computeFullyDownloadedAlbumCountByArtistId_ignoresAlbumsWithoutKnownSongCounts() {
        val completedItems = listOf(
            completedTrack(trackId = "a1-t1", albumId = "album-1", artistId = "artist-1", albumSongCount = null),
            completedTrack(trackId = "a1-t2", albumId = "album-1", artistId = "artist-1", albumSongCount = null)
        )

        val counts = computeFullyDownloadedAlbumCountByArtistId(completedItems)

        assertEquals(emptyMap<String, Int>(), counts)
    }

    private fun completedTrack(
        trackId: String,
        albumId: String,
        artistId: String,
        albumSongCount: Int?
    ): NavidromeDownloadItem {
        return NavidromeDownloadItem(
            serverId = "server-1",
            libraryId = "library-1",
            trackId = trackId,
            albumId = albumId,
            albumSongCount = albumSongCount,
            artistId = artistId,
            title = trackId,
            artistName = artistId,
            albumName = albumId,
            coverUrl = null,
            durationSeconds = null,
            formatLabel = "mp3",
            status = NavidromeDownloadStatus.Completed,
            progressPercent = 100,
            downloadId = null,
            localPath = "/tmp/$trackId.mp3",
            errorMessage = null,
            updatedAtMs = 0L
        )
    }
}
