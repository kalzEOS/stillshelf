package com.stillshelf.app.data.api

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavidromeArtistIdentityTest {
    @Test fun searchAndCurrentAlbumRetainTheRecordingIdOfAReplacementFlac() = runTest {
        val id = "f9462766-8938-4bb8-8d4a-1d4f5d4b0144"
        val song = """{"id":"new-flac","title":"It's Not Over","artist":"Daughtry","album":"Daughtry", "albumId":"current-album","suffix":"flac","musicBrainzId":"$id"}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val payload = if (chain.request().url.encodedPath.contains("getAlbum"))
                """"album":{"id":"current-album","name":"Daughtry","artist":"Daughtry","song":[$song]}""" else """"searchResult3":{"song":[$song]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"subsonic-response":{"status":"ok",$payload}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = NavidromeApi(http)
        val auth = NavidromeAuth(baseUrl = "https://library.invalid", username = "test", encPassword = "test")
        assertEquals(id, api.search(auth, "It's Not Over").getOrThrow().tracks.single().musicBrainzId)
        val current = api.getAlbum(auth, "current-album").getOrThrow().tracks.single()
        assertEquals("new-flac", current.id)
        assertEquals("flac", current.suffix)
        assertEquals(id, current.musicBrainzId)
    }
    @Test fun artistIndexAndSearchRetainOptionalMusicBrainzIdentity() = runTest {
        val id = "0b91b4e7-5c97-4446-8665-27fe6026cd2f"
        val artist = """{"id":"local","name":"Kadim Al Sahir","musicBrainzId":"$id"}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val payload = if (chain.request().url.encodedPath.contains("getArtists"))
                """"artists":{"index":[{"artist":[$artist]}]}""" else """"searchResult3":{"artist":[$artist]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"subsonic-response":{"status":"ok",$payload}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = NavidromeApi(http)
        val auth = NavidromeAuth(baseUrl = "https://library.invalid", username = "test", encPassword = "test")
        assertEquals(id, api.getArtists(auth).getOrThrow().single().musicBrainzId)
        assertEquals(id, api.search(auth, "Kadim").getOrThrow().artists.single().musicBrainzId)
    }
}
