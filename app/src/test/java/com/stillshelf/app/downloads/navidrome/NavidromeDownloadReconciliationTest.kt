package com.stillshelf.app.downloads.navidrome

import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeDownloadReconciliationTest {

    @Test
    fun reconcileNavidromeDownloadItems_marksInterruptedActiveDownloadAsFailed() {
        val item = sampleItem(
            status = NavidromeDownloadStatus.Downloading,
            downloadId = 41L
        )

        val reconciled = reconcileNavidromeDownloadItems(
            items = listOf(item),
            snapshotsByDownloadId = emptyMap(),
            localFileExists = { true }
        )

        assertEquals(NavidromeDownloadStatus.Failed, reconciled.single().status)
        assertEquals("Download was interrupted.", reconciled.single().errorMessage)
    }

    @Test
    fun reconcileNavidromeDownloadItems_marksCompletedDownloadFailedWhenFileIsMissing() {
        val item = sampleItem(
            status = NavidromeDownloadStatus.Completed,
            downloadId = 52L,
            localPath = "/tmp/missing.mp3"
        )

        val reconciled = reconcileNavidromeDownloadItems(
            items = listOf(item),
            snapshotsByDownloadId = emptyMap(),
            localFileExists = { false }
        )

        assertEquals(NavidromeDownloadStatus.Failed, reconciled.single().status)
        assertEquals("Downloaded file is missing.", reconciled.single().errorMessage)
    }

    @Test
    fun reconcileNavidromeDownloadItems_keepsCompletedDownloadWhenFileStillExists() {
        val item = sampleItem(
            status = NavidromeDownloadStatus.Completed,
            downloadId = 63L,
            localPath = "/tmp/present.mp3"
        )

        val reconciled = reconcileNavidromeDownloadItems(
            items = listOf(item),
            snapshotsByDownloadId = emptyMap(),
            localFileExists = { true }
        )

        assertEquals(NavidromeDownloadStatus.Completed, reconciled.single().status)
        assertEquals(null, reconciled.single().errorMessage)
    }

    private fun sampleItem(
        status: NavidromeDownloadStatus,
        downloadId: Long?,
        localPath: String? = "/tmp/sample.mp3"
    ): NavidromeDownloadItem {
        return NavidromeDownloadItem(
            serverId = "server-1",
            libraryId = "library-1",
            trackId = "track-1",
            albumId = "album-1",
            albumSongCount = 2,
            artistId = "artist-1",
            title = "Song",
            artistName = "Artist",
            albumName = "Album",
            coverUrl = null,
            durationSeconds = 180,
            formatLabel = "mp3",
            status = status,
            progressPercent = 50,
            downloadId = downloadId,
            localPath = localPath,
            errorMessage = null,
            updatedAtMs = 0L
        )
    }
}
