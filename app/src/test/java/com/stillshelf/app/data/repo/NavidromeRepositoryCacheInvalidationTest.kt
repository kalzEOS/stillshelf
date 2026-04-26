package com.stillshelf.app.data.repo

import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.network.NetworkConnectionState
import com.stillshelf.app.core.network.NetworkConnectionType
import com.stillshelf.app.core.network.NetworkMonitor
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.core.datastore.SecureTokenStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Field
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NavidromeRepositoryCacheInvalidationTest {
    @Test
    fun invalidateArtistAndAlbumDetailCaches_clearsBothDetailCaches() = runTest {
        val repository = buildRepository()

        cacheMap(repository, "artistDetailCache")["artist-1"] = Any()
        cacheMap(repository, "albumDetailCache")["album-1"] = Any()

        repository.invalidateArtistAndAlbumDetailCaches()

        assertTrue(cacheMap(repository, "artistDetailCache").isEmpty())
        assertTrue(cacheMap(repository, "albumDetailCache").isEmpty())
    }

    private fun buildRepository(): NavidromeRepository {
        val api = mockk<NavidromeApi>(relaxed = true)
        val sessionPreferences = mockk<SessionPreferences>(relaxed = true) {
            every { state } returns flowOf(
                SessionPreferenceState(
                    activeServerId = null,
                    activeLibraryId = null
                )
            )
            coEvery { isNavidromeLegacySessionMigrated() } returns true
        }
        val secureTokenStorage = mockk<SecureTokenStorage>(relaxed = true)
        val networkMonitor = mockk<NetworkMonitor>(relaxed = true) {
            every { observeConnectionState() } returns flowOf(
                NetworkConnectionState(
                    type = NetworkConnectionType.Offline,
                    identity = "offline"
                )
            )
        }

        return NavidromeRepository(
            navidromeApi = api,
            sessionPreferences = sessionPreferences,
            secureTokenStorage = secureTokenStorage,
            networkMonitor = networkMonitor,
            okHttpClient = OkHttpClient()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun cacheMap(repository: NavidromeRepository, fieldName: String): MutableMap<String, Any?> {
        val field: Field = NavidromeRepository::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(repository) as MutableMap<String, Any?>
    }
}
