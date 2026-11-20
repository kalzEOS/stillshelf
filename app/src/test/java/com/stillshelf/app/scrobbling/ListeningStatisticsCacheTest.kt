package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListeningStatisticsCacheTest {
    @Test fun concurrentScreenPartialSnapshotCannotEraseLastGoodTrackChart() = runTest {
        val storage = mockk<SecureTokenStorage> {
            coEvery { getNamedSecret(any()) } returns null
            coEvery { saveNamedSecret(any(), any(), true, false) } returns Unit
        }
        val cache = ListeningStatisticsCache(storage)
        val tracks = listOf(ListeningEntry("Saved song", "Artist", 4))
        cache.put("same-account", ListeningStatistics("ListenBrainz", topTracks = tracks))
        cache.put("same-account", ListeningStatistics("ListenBrainz", unavailableSections = setOf(ListeningMetric.TRACKS)))
        assertEquals(tracks, cache.get("same-account")!!.topTracks)
        assertNull(cache.get("other-account"))
        cache.put("same-account", ListeningStatistics("ListenBrainz"))
        assertTrue(cache.get("same-account")!!.topTracks.isEmpty())
    }
    @Test fun malformedCacheCanBeReplacedWithoutTouchingSavedConnection() = runTest {
        val storage = mockk<SecureTokenStorage> {
            coEvery { getNamedSecret(any()) } returns "not JSON"
            coEvery { saveNamedSecret(any(), any(), true, false) } returns Unit
        }
        val cache = ListeningStatisticsCache(storage)
        assertNull(cache.get("source"))
        cache.put("source", ListeningStatistics("Koito", 42))
        assertEquals(42L, cache.get("source")?.totalScrobbles)
        coVerify(exactly = 0) { storage.clearNamedSecret(any()) }
    }
    @Test fun lastStatisticsSurviveReopeningAndPreserveUnavailableMetrics() = runTest {
        var saved: String? = null
        val storage = mockk<SecureTokenStorage> {
            coEvery { getNamedSecret(any()) } answers { saved }
            coEvery { saveNamedSecret(any(), any(), true, false) } answers { saved = secondArg() }
        }
        val stats = ListeningStatistics("Koito", 42, topArtists = listOf(ListeningEntry("فنان", plays = 4, artworkUrl = "https://example.invalid/image", artistId = "artist",
            musicBrainzArtistId = "0b91b4e7-5c97-4446-8665-27fe6026cd2f", musicBrainzRecordingId = "f9462766-8938-4bb8-8d4a-1d4f5d4b0144")), unavailableSections = setOf(ListeningMetric.ALBUMS))
        ListeningStatisticsCache(storage).put("account", stats)
        val reopened = ListeningStatisticsCache(storage)
        assertEquals(stats, reopened.get("account"))
        assertNull(reopened.get("account")!!.listeningSeconds)
        assertNull(reopened.get("other-account"))
        coVerify { storage.saveNamedSecret("listening_statistics_cache_v1", any(), true, false) }
    }

    @Test fun cacheIdentityIgnoresScrobblingButSeparatesAccountsProvidersAndRemovedConnections() {
        val connection = ListeningConnection(ListeningProvider.KOITO, "https://example.invalid", "reader", "secret", revision = "one")
        val settings = ListeningSettings(loaded = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL, connection = connection)
        val key = listeningStatisticsCacheKey(settings, null)
        assertEquals(key, listeningStatisticsCacheKey(settings.withSelection(ScrobblingSelection.NAVIDROME), null))
        assertEquals(key, listeningStatisticsCacheKey(settings.withSelection(ScrobblingSelection.OFF), null))
        assertNotEquals(key, listeningStatisticsCacheKey(settings.copy(connection = ListeningConnection(ListeningProvider.MALOJA, revision = "one")), null))
        assertNotEquals(key, listeningStatisticsCacheKey(settings.copy(connection = ListeningConnection(ListeningProvider.KOITO, revision = "new")), null))
        assertNull(listeningStatisticsCacheKey(settings.copy(connection = null), null))
        assertFalse(key!!.contains("secret"))
    }
}
