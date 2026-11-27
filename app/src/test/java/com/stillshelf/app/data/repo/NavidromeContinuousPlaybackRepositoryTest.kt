package com.stillshelf.app.data.repo

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.core.model.NavidromeContinuousPlaybackMode
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.network.NetworkConnectionState
import com.stillshelf.app.core.network.NetworkConnectionType
import com.stillshelf.app.core.network.NetworkMonitor
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.api.NavidromeApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NavidromeContinuousPlaybackRepositoryTest {
    @Test
    fun genreUsesLastTracksMetadataAndSelectedLibrary() = runBlocking {
        val fixture = fixture { "\"randomSongs\":{\"song\":[$song]}" }
        val result = fixture.repository.fetchContinuousPlaybackTracks(
            reference.copy(genres = listOf("", "Jazz")), NavidromeContinuousPlaybackMode.SAME_GENRE
        )
        assertEquals(listOf("next"), songs(result).map { it.id })
        val request = fixture.requests.first { it.encodedPath.endsWith("getRandomSongs.view") }
        assertEquals("Jazz", request.queryParameter("genre"))
        assertEquals("library", request.queryParameter("musicFolderId"))
        assertTrue(songs(result).single().streamUrl.contains("/rest/stream.view"))
    }

    @Test
    fun missingGenreOrArtistDoesNotAccidentallyRequestRandomLibraryMusic() = runBlocking {
        val fixture = fixture { error("No music request expected") }
        assertTrue(songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.SAME_GENRE
        )).isEmpty())
        assertTrue(songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference.copy(artistId = null), NavidromeContinuousPlaybackMode.SAME_ARTIST
        )).isEmpty())
        assertTrue(fixture.requests.none { it.encodedPath.endsWith("getRandomSongs.view") })
    }

    @Test
    fun similarArtistsUsesId3EndpointAndLastTracksArtist() = runBlocking {
        val fixture = fixture { "\"similarSongs2\":{\"song\":[$song]}" }
        val result = fixture.repository.fetchContinuousPlaybackTracks(reference, NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS)
        assertEquals("next", songs(result).single().id)
        val request = fixture.requests.first { it.encodedPath.endsWith("getSimilarSongs2.view") }
        assertEquals(reference.artistId, request.queryParameter("id"))
    }

    @Test
    fun sameArtistLoadsDiscographyAndExcludesOtherPerformers() = runBlocking {
        val fixture = fixture { path ->
            when {
                path.endsWith("getArtist.view") -> "\"artist\":{\"id\":\"artist\",\"name\":\"Artist\",\"album\":[{\"id\":\"album\",\"name\":\"Album\"}]}"
                else -> "\"album\":{\"id\":\"album\",\"name\":\"Album\",\"song\":[$song,${song.replace("next", "other").replace("Artist", "Other").replace("\"artist\"", "\"other-artist\"")}]}"
            }
        }
        assertEquals(listOf("next"), songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.SAME_ARTIST
        )).map { it.id })
    }

    @Test
    fun sameArtistFindsCompilationTracksWhenArtistHasNoAlbums() = runBlocking {
        val fixture = fixture { path ->
            when {
                path.endsWith("getArtist.view") -> "\"artist\":{\"id\":\"artist\",\"name\":\"Artist\",\"album\":[]}"
                path.endsWith("search3.view") -> "\"searchResult3\":{\"song\":[$song,${song.replace("next", "unrelated").replace("Artist", "Other").replace("\"artist\"", "\"other-artist\"")}] }"
                else -> error("Unexpected music request: $path")
            }
        }
        assertEquals(listOf("next"), songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.SAME_ARTIST
        )).map { it.id })
        val search = fixture.requests.first { it.encodedPath.endsWith("search3.view") }
        assertEquals(reference.artistName, search.queryParameter("query"))
        assertEquals("library", search.queryParameter("musicFolderId"))
        assertEquals("500", search.queryParameter("songCount"))
    }

    @Test
    fun sameArtistSearchesCompilationsWhenOwnAlbumContainsOnlyTheReference() = runBlocking {
        val fixture = fixture { path ->
            when {
                path.endsWith("getArtist.view") -> "\"artist\":{\"id\":\"artist\",\"name\":\"Artist\",\"album\":[{\"id\":\"album\",\"name\":\"Album\"}]}"
                path.endsWith("getAlbum.view") -> "\"album\":{\"id\":\"album\",\"name\":\"Album\",\"song\":[${song.replace("next", "last")}]}"
                path.endsWith("search3.view") -> "\"searchResult3\":{\"song\":[${song.replace("next", "last")},$song]}"
                else -> error("Unexpected music request: $path")
            }
        }
        assertEquals(listOf("last", "next"), songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.SAME_ARTIST
        )).map { it.id })
    }

    @Test
    fun sameArtistAlbumMatchingUsesIdsBeforeNames() = runBlocking {
        assertArtistIdentityFiltering(searchFallback = false)
    }

    @Test
    fun sameArtistSearchMatchingUsesIdsBeforeNames() = runBlocking {
        assertArtistIdentityFiltering(searchFallback = true)
    }

    private suspend fun assertArtistIdentityFiltering(searchFallback: Boolean) {
        val candidates = listOf(
            JSONObject(song).put("id", "same-id").put("artist", "Different display name"),
            JSONObject(song).put("id", "different-id").put("artistId", "another-artist"),
            JSONObject(song).put("id", "missing-id").put("artist", "aRtIsT").apply { remove("artistId") },
            JSONObject(song).put("id", "blank-id").put("artistId", ""),
            JSONObject(song).put("id", "unrelated-name").put("artist", "Other").apply { remove("artistId") }
        ).joinToString(",")
        val fixture = fixture { path ->
            when {
                path.endsWith("getArtist.view") -> {
                    val albums = if (searchFallback) "[]" else "[{\"id\":\"album\",\"name\":\"Album\"}]"
                    "\"artist\":{\"id\":\"artist\",\"name\":\"Artist\",\"album\":$albums}"
                }
                path.endsWith("getAlbum.view") -> "\"album\":{\"id\":\"album\",\"name\":\"Album\",\"song\":[$candidates]}"
                path.endsWith("search3.view") -> "\"searchResult3\":{\"song\":[$candidates]}"
                else -> error("Unexpected music request: $path")
            }
        }
        val result = fixture.repository.fetchContinuousPlaybackTracks(reference, NavidromeContinuousPlaybackMode.SAME_ARTIST)
        assertEquals(listOf("same-id", "missing-id", "blank-id"), songs(result).map { it.id })
        assertEquals(searchFallback, fixture.requests.any { it.encodedPath.endsWith("search3.view") })
    }

    @Test
    fun randomModeHasNoGenreFilterAndEmptyResultsAreAllowed() = runBlocking {
        val fixture = fixture { "\"randomSongs\":{}" }
        assertTrue(songs(fixture.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.RANDOM
        )).isEmpty())
        assertNull(fixture.requests.first { it.encodedPath.endsWith("getRandomSongs.view") }.queryParameter("genre"))
    }

    @Test
    fun recommendationsUseCurrentStreamRouteWhenDiscoveryFinishesAfterEndpointSwitch() = runBlocking {
        lateinit var running: Fixture
        running = fixture { path ->
            check(path.endsWith("getRandomSongs.view"))
            @Suppress("UNCHECKED_CAST")
            val status = NavidromeRepository::class.java.getDeclaredField("mutableActiveConnectionStatus")
                .apply { isAccessible = true }.get(running.repository) as MutableStateFlow<ActiveServerConnectionStatus?>
            status.value = ActiveServerConnectionStatus(
                serverId = "server", effectiveBaseUrl = "https://remote.example.com",
                route = ServerConnectionRoute.Default, connectionMode = ServerConnectionMode.Auto,
                switchingEnabled = false
            )
            "\"randomSongs\":{\"song\":[$song]}"
        }
        val result = songs(running.repository.fetchContinuousPlaybackTracks(reference, NavidromeContinuousPlaybackMode.RANDOM))
        assertTrue(result.single().streamUrl.startsWith("https://remote.example.com/"))
    }

    @Test
    fun unsupportedRecommendationsAndNetworkFailuresReturnErrors() = runBlocking {
        val unsupported = fixture { "\"error\":{\"code\":70,\"message\":\"Not supported\"}" }
        assertTrue(unsupported.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS
        ) is AppResult.Error)
        val offline = fixture { throw IOException("Offline") }
        assertTrue(offline.repository.fetchContinuousPlaybackTracks(
            reference, NavidromeContinuousPlaybackMode.RANDOM
        ) is AppResult.Error)
    }

    private data class Fixture(val repository: NavidromeRepository, val requests: List<HttpUrl>)

    private fun fixture(content: (String) -> String): Fixture {
        val requests = CopyOnWriteArrayList<HttpUrl>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url)
            val path = request.url.encodedPath
            val payload = if (path.endsWith("ping.view")) "" else content(path)
            val status = if (payload.startsWith("\"error\"")) "failed" else "ok"
            val body = "{\"subsonic-response\":{\"status\":\"$status\"${if (payload.isBlank()) "" else ",$payload"}}}"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val preferences = mockk<SessionPreferences>(relaxed = true) {
            every { state } returns flowOf(SessionPreferenceState(
                activeServerId = null, activeLibraryId = null,
                selectedBackend = BackendProvider.NAVIDROME,
                activeNavidromeServerId = "server", navidromeActiveLibraryIds = mapOf("server" to "library"),
                navidromeServers = listOf(NavidromeServer("server", "Server", "https://example.com", "user", 0))
            ))
            coEvery { isNavidromeLegacySessionMigrated() } returns true
        }
        val secrets = mockk<SecureTokenStorage>(relaxed = true) {
            coEvery { getNamedSecret(any()) } returns "test-secret"
        }
        val network = mockk<NetworkMonitor> {
            every { observeConnectionState() } returns flowOf(NetworkConnectionState(NetworkConnectionType.Offline, "offline"))
        }
        return Fixture(NavidromeRepository(NavidromeApi(client), preferences, secrets, network, client), requests)
    }

    private fun songs(result: AppResult<List<NavidromeTrack>>) = (result as AppResult.Success).value

    private val reference = NavidromeTrack(
        "last", "Last", "Artist", "Album", "album", "artist", null, 180, null,
        "https://example.com/last", "MP3", 320
    )
    private val song = """{"id":"next","title":"Next","artist":"Artist","artistId":"artist","album":"Album","albumId":"album","duration":180}"""
}
