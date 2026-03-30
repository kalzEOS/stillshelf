package com.stillshelf.app.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPreferencesNavidromeLyricsCacheTest {

    @Test
    fun removeCachedNavidromeLyricsEntriesByPrefix_removesOnlyMatchingTrackEntries() {
        val preferences = SessionPreferences(dataStore = unusedDataStore())
        val trackOnePrefix = "server-a|library-a|lyrics:track-1:"
        val trackOneScoped = "${trackOnePrefix}source:source-a"
        val trackOneFallback = "${trackOnePrefix}fallback"
        val trackTwoScoped = "server-a|library-a|lyrics:track-2:source:source-a"
        val entries = linkedMapOf(
            trackOneScoped to CachedNavidromeLyricsPayload(
                cacheKey = trackOneScoped,
                payload = """{"lines":[{"text":"one"}],"isSynced":false}""",
                savedAtMs = 1L
            ),
            trackOneFallback to CachedNavidromeLyricsPayload(
                cacheKey = trackOneFallback,
                payload = """{"lines":[{"text":"fallback"}],"isSynced":false}""",
                savedAtMs = 1L
            ),
            trackTwoScoped to CachedNavidromeLyricsPayload(
                cacheKey = trackTwoScoped,
                payload = """{"lines":[{"text":"two"}],"isSynced":false}""",
                savedAtMs = 1L
            )
        )

        val remaining = preferences.removeCachedNavidromeLyricsEntriesByPrefix(
            values = entries,
            cacheKeyPrefix = trackOnePrefix
        )

        assertEquals(setOf(trackTwoScoped), remaining.keys)
    }

    private fun unusedDataStore() = PreferenceDataStoreFactory.create(
        produceFile = {
            File(createTempDirectory(prefix = "session-prefs-unused").toFile(), "session.preferences_pb")
        }
    )
}
