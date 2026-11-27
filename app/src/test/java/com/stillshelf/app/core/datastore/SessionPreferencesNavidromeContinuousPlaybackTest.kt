package com.stillshelf.app.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stillshelf.app.core.model.NavidromeContinuousPlaybackMode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SessionPreferencesNavidromeContinuousPlaybackTest {
    @Test
    fun defaultsOffAndRemembersSelectionWhenDisabledAndReopened() = runBlocking {
        val directory = createTempDirectory("continuous-playback").toFile()
        val file = File(directory, "session.preferences_pb")
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file }
            )
            val preferences = SessionPreferences(store)
            assertFalse(preferences.state.first().navidromeContinuousPlaybackEnabled)
            assertEquals(NavidromeContinuousPlaybackMode.SAME_ARTIST, preferences.state.first().navidromeContinuousPlaybackMode)
            preferences.setNavidromeContinuousPlaybackEnabled(true)
            preferences.setNavidromeContinuousPlaybackMode(NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS)
            assertTrue(withTimeout(5_000) {
                preferences.state.first {
                    it.navidromeContinuousPlaybackEnabled &&
                        it.navidromeContinuousPlaybackMode == NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS
                }.navidromeContinuousPlaybackEnabled
            })
            preferences.setNavidromeContinuousPlaybackEnabled(false)
            withTimeout(5_000) { preferences.state.first { !it.navidromeContinuousPlaybackEnabled } }
            firstJob.cancelAndJoin()

            val reopenedStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }
            )
            val reopened = SessionPreferences(reopenedStore)
            assertFalse(reopened.state.first().navidromeContinuousPlaybackEnabled)
            assertEquals(NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS, reopened.state.first().navidromeContinuousPlaybackMode)
            reopened.setNavidromeContinuousPlaybackEnabled(true)
            withTimeout(5_000) { reopened.state.first { it.navidromeContinuousPlaybackEnabled } }
            assertEquals(NavidromeContinuousPlaybackMode.SIMILAR_ARTISTS, reopened.state.first().navidromeContinuousPlaybackMode)
            reopenedStore.edit { it[stringPreferencesKey("navidrome_continuous_playback_mode")] = "unknown" }
            assertEquals(NavidromeContinuousPlaybackMode.SAME_ARTIST, withTimeout(5_000) {
                reopened.state.first { it.navidromeContinuousPlaybackMode == NavidromeContinuousPlaybackMode.SAME_ARTIST }
                    .navidromeContinuousPlaybackMode
            })
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
