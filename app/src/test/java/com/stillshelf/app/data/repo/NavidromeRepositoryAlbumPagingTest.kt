package com.stillshelf.app.data.repo

import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.ActiveServerConnectionStatus
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeServer
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerConnectionRoute
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.data.api.NavidromeAlbumDto
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.network.NetworkConnectionState
import com.stillshelf.app.core.network.NetworkConnectionType
import com.stillshelf.app.core.network.NetworkMonitor
import com.stillshelf.app.core.util.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Field
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NavidromeRepositoryAlbumPagingTest {
    @Test
    fun fetchAlbums_pagesThroughAllAlbumBatches() = runTest {
        val api = mockk<NavidromeApi>(relaxed = true)
        val sessionPreferences = mockk<SessionPreferences>(relaxed = true) {
            every { state } returns flowOf(
                SessionPreferenceState(
                    activeServerId = "server-1",
                    activeLibraryId = "library-1",
                    selectedBackend = null,
                    navidromeServers = listOf(
                        NavidromeServer(
                            id = "server-1",
                            name = "Navidrome",
                            baseUrl = "https://navidrome.example",
                            username = "user",
                            createdAt = 1L
                        )
                    ),
                    activeNavidromeServerId = "server-1",
                    navidromeActiveLibraryIds = mapOf("server-1" to "library-1")
                )
            )
            coEvery { isNavidromeLegacySessionMigrated() } returns true
        }
        val secureTokenStorage = mockk<SecureTokenStorage>(relaxed = true) {
            coEvery { getNamedSecret(any()) } returns "secret"
        }
        val networkMonitor = mockk<NetworkMonitor>(relaxed = true) {
            every { observeConnectionState() } returns flowOf(
                NetworkConnectionState(
                    type = NetworkConnectionType.Offline,
                    identity = "offline"
                )
            )
        }
        coEvery { api.measurePing(any()) } returns Result.success(12L)

        val repository = NavidromeRepository(
            navidromeApi = api,
            sessionPreferences = sessionPreferences,
            secureTokenStorage = secureTokenStorage,
            networkMonitor = networkMonitor,
            okHttpClient = OkHttpClient()
        )
        setActiveConnectionStatus(
            repository,
            ActiveServerConnectionStatus(
                serverId = "server-1",
                effectiveBaseUrl = "https://navidrome.example",
                route = ServerConnectionRoute.Default,
                connectionMode = ServerConnectionMode.Auto,
                switchingEnabled = false
            )
        )
        every { api.encodePassword("secret") } returns "encoded-secret"

        val firstPage = (1..100).map { index ->
            albumDto(id = "album-$index", name = "Album $index")
        }
        val secondPage = (101..150).map { index ->
            albumDto(id = "album-$index", name = "Album $index")
        }
        coEvery {
            api.getAlbumList(any(), type = "newest", size = 100, offset = 0)
        } returns Result.success(firstPage)
        coEvery {
            api.getAlbumList(any(), type = "newest", size = 100, offset = 100)
        } returns Result.success(secondPage)

        val result = repository.fetchAlbums(sort = NavidromeAlbumSortOption.RECENT, forceRefresh = true)

        val albums = (result as AppResult.Success<List<NavidromeAlbum>>).value
        assertEquals(150, albums.size)
        assertEquals("album-1", albums.first().id)
        assertEquals("album-150", albums.last().id)
        coVerify(exactly = 1) { api.getAlbumList(any(), type = "newest", size = 100, offset = 0) }
        coVerify(exactly = 1) { api.getAlbumList(any(), type = "newest", size = 100, offset = 100) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setActiveConnectionStatus(
        repository: NavidromeRepository,
        status: ActiveServerConnectionStatus
    ) {
        val field: Field = NavidromeRepository::class.java.getDeclaredField("mutableActiveConnectionStatus")
        field.isAccessible = true
        val flow = field.get(repository) as MutableStateFlow<ActiveServerConnectionStatus?>
        flow.value = status
    }

    private fun albumDto(id: String, name: String): NavidromeAlbumDto {
        return NavidromeAlbumDto(
            id = id,
            name = name,
            artistName = "Artist",
            artistId = "artist-1",
            year = null,
            songCount = 10,
            durationSeconds = null,
            coverArtId = null,
            genre = null
        )
    }
}
