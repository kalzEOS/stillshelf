package com.stillshelf.app.downloads.manager

import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DownloadPlaybackSourceTest {

    @Test
    fun resolvedTracks_migratesLegacySingleFileDownloads() {
        val item = DownloadItem(
            serverId = "server",
            libraryId = "library",
            bookId = "book",
            title = "Sample",
            authorName = "Author",
            coverUrl = null,
            durationSeconds = 120.0,
            status = DownloadStatus.Completed,
            progressPercent = 100,
            downloadId = 99L,
            localPath = "/tmp/book.mp3"
        )

        val tracks = item.resolvedTracks()

        assertEquals(1, tracks.size)
        assertEquals(99L, tracks.single().downloadId)
        assertEquals("/tmp/book.mp3", tracks.single().localPath)
    }

    @Test
    fun toLocalPlaybackSource_usesAllDownloadedTracksInOffsetOrder() {
        val item = DownloadItem(
            serverId = "server",
            libraryId = "library",
            bookId = "book",
            title = "Sample",
            authorName = "Author",
            coverUrl = null,
            durationSeconds = 180.0,
            status = DownloadStatus.Completed,
            progressPercent = 100,
            tracks = listOf(
                DownloadTrackItem(index = 1, startOffsetSeconds = 60.0, durationSeconds = 60.0, localPath = "file:///tmp/book-2.mp3"),
                DownloadTrackItem(index = 0, startOffsetSeconds = 0.0, durationSeconds = 60.0, localPath = "file:///tmp/book-1.mp3")
            )
        )

        val source = item.toLocalPlaybackSource(sampleBook())

        assertNotNull(source)
        assertEquals(2, source?.tracks?.size)
        assertEquals("file:///tmp/book-1.mp3", source?.streamUrl)
        assertEquals(0.0, source?.tracks?.get(0)?.startOffsetSeconds)
        assertEquals(60.0, source?.tracks?.get(1)?.startOffsetSeconds)
    }

    @Test
    fun toDownloadTrackRequests_fallsBackToPrimaryStreamWhenNoTrackMetadataExists() {
        val source = PlaybackSource(
            book = sampleBook(),
            streamUrl = "https://example.com/book.mp3",
            tracks = emptyList()
        )

        val requests = source.toDownloadTrackRequests()

        assertEquals(1, requests.size)
        assertEquals("https://example.com/book.mp3", requests.single().streamUrl)
        assertEquals(0.0, requests.single().startOffsetSeconds, 0.0)
    }

    @Test
    fun toDownloadTrackRequests_preservesMultiTrackOffsets() {
        val source = PlaybackSource(
            book = sampleBook(),
            streamUrl = "https://example.com/book-1.mp3",
            tracks = listOf(
                PlaybackTrack(startOffsetSeconds = 60.0, durationSeconds = 60.0, streamUrl = "https://example.com/book-2.mp3"),
                PlaybackTrack(startOffsetSeconds = 0.0, durationSeconds = 60.0, streamUrl = "https://example.com/book-1.mp3")
            )
        )

        val requests = source.toDownloadTrackRequests()

        assertEquals(2, requests.size)
        assertEquals("https://example.com/book-1.mp3", requests[0].streamUrl)
        assertEquals("https://example.com/book-2.mp3", requests[1].streamUrl)
        assertEquals(0, requests[0].index)
        assertEquals(1, requests[1].index)
    }

    private fun sampleBook(): BookSummary {
        return BookSummary(
            id = "book",
            libraryId = "library",
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 180.0,
            coverUrl = null
        )
    }
}
