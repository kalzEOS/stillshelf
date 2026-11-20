package com.stillshelf.app.scrobbling

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.data.api.*
import com.stillshelf.app.downloads.navidrome.*
import com.stillshelf.app.playback.navidrome.parseNavidromeTrackSnapshot
import com.stillshelf.app.playback.navidrome.serializeNavidromeTrackSnapshot
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalListeningMetadataTest {
    @Test fun libraryResponseRetainsMultipleGenreTagsAndSuppliedOriginalReleaseYear() = runTest {
        suspend fun parse(fields: String): NavidromeTrackDto {
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(
                    """{"subsonic-response":{"status":"ok","searchResult3":{"song":[{"id":"track","title":"Song","artist":"Artist",$fields}]}}}"""
                        .toResponseBody("application/json".toMediaType())).build() }.build()
            return NavidromeApi(client).search(NavidromeAuth(baseUrl = "https://library.invalid", username = "test", encPassword = "test"), "Song")
                .getOrThrow().tracks.single()
        }
        val tagged = parse(""""genres":[{"name":"Pop"},{"name":"Rock"},{"name":"pop"}],"year":2020,"originalReleaseDate":{"year":1999}""")
        assertEquals(listOf("Pop", "Rock"), tagged.genres)
        assertEquals(1999, tagged.releaseYear)
        val legacy = parse(""""genre":"Classical","year":1980""")
        assertEquals(listOf("Classical"), legacy.genres)
        assertEquals(1980, legacy.releaseYear)
        val missing = parse(""""year":0""")
        assertTrue(missing.genres.isEmpty())
        assertNull(missing.releaseYear)
    }

    @Test fun playbackAndDownloadSnapshotsPreserveLocalStatisticsMetadata() {
        val track = NavidromeTrack("track", "Song", "Artist", "Album", "album", "artist", null, 60,
            null, "https://library.invalid/stream", "FLAC", 900, musicBrainzId = "00000000-0000-0000-0000-000000000001",
            genres = listOf("Pop", "Rock"), releaseYear = 2001)
        assertEquals(track, parseNavidromeTrackSnapshot(serializeNavidromeTrackSnapshot(track)))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = NavidromeDownloadStorage(context)
        val previous = storage.loadItems()
        try {
            val item = NavidromeDownloadItem("server", "library", track.id, track.albumId, null, track.artistId, track.title,
                track.artistName, track.albumName, track.coverUrl, track.durationSeconds, track.formatLabel,
                NavidromeDownloadStatus.Completed, 100, musicBrainzId = track.musicBrainzId, genres = track.genres, releaseYear = track.releaseYear)
            storage.persistItems(listOf(item))
            assertEquals(item, storage.loadItems().single())
        } finally { storage.persistItems(previous) }
    }
}
