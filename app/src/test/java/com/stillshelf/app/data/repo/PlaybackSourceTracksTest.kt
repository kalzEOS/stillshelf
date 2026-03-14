package com.stillshelf.app.data.repo

import com.stillshelf.app.data.api.AudiobookshelfAudioTrackDto
import com.stillshelf.app.data.api.AudiobookshelfBookDetailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackSourceTracksTest {

    @Test
    fun buildPlaybackTracks_usesExpandedAudioTracksWhenAvailable() {
        val detail = sampleDetail(
            streamPath = "audio/part-1.mp3",
            audioTracks = listOf(
                AudiobookshelfAudioTrackDto(
                    startOffsetSeconds = 60.0,
                    durationSeconds = 60.0,
                    contentUrl = "audio/part-2.mp3"
                ),
                AudiobookshelfAudioTrackDto(
                    startOffsetSeconds = 0.0,
                    durationSeconds = 60.0,
                    contentUrl = "audio/part-1.mp3"
                )
            )
        )

        val tracks = buildPlaybackTracks(detail) { path -> "https://example.com/$path" }

        assertEquals(2, tracks.size)
        assertEquals("https://example.com/audio/part-1.mp3", tracks[0].streamUrl)
        assertEquals(0.0, tracks[0].startOffsetSeconds, 0.0)
        assertEquals("https://example.com/audio/part-2.mp3", tracks[1].streamUrl)
        assertEquals(60.0, tracks[1].startOffsetSeconds, 0.0)
    }

    @Test
    fun buildPlaybackTracks_fallsBackToSingleStreamPath() {
        val detail = sampleDetail(
            streamPath = "audio/book.mp3",
            audioTracks = emptyList()
        )

        val tracks = buildPlaybackTracks(detail) { path -> "https://example.com/$path" }

        assertEquals(1, tracks.size)
        assertEquals("https://example.com/audio/book.mp3", tracks.single().streamUrl)
        assertEquals(0.0, tracks.single().startOffsetSeconds, 0.0)
        assertNotNull(tracks.single().durationSeconds)
        assertEquals(180.0, tracks.single().durationSeconds!!, 0.0)
    }

    private fun sampleDetail(
        streamPath: String?,
        audioTracks: List<AudiobookshelfAudioTrackDto>
    ): AudiobookshelfBookDetailDto {
        return AudiobookshelfBookDetailDto(
            id = "book",
            libraryId = "library",
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            narratorNames = emptyList(),
            durationSeconds = 180.0,
            description = null,
            publishedYear = null,
            seriesName = null,
            seriesNames = emptyList(),
            seriesIds = emptyList(),
            seriesSequence = null,
            genres = emptyList(),
            sizeBytes = null,
            chapters = emptyList(),
            streamPath = streamPath,
            audioTracks = audioTracks
        )
    }
}
