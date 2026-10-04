package com.stillshelf.app.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPreferencesNavidromeKeepScreenOnTest {

    @Test
    fun keepScreenOnForLyrics_defaultsOff_andPersistsAcrossReopening() = runBlocking {
        val directory = createTempDirectory(prefix = "lyrics-screen-on").toFile()
        val file = File(directory, "session.preferences_pb")
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val preferences = SessionPreferences(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO + firstJob),
                    produceFile = { file }
                )
            )
            assertFalse(preferences.state.first().navidromeKeepScreenOnForLyrics)
            preferences.setNavidromeKeepScreenOnForLyrics(true)
            assertTrue(withTimeout(5_000) {
                preferences.state.first { it.navidromeKeepScreenOnForLyrics }
                    .navidromeKeepScreenOnForLyrics
            })
            firstJob.cancelAndJoin()

            val reopenedPreferences = SessionPreferences(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.IO + secondJob),
                    produceFile = { file }
                )
            )
            assertTrue(reopenedPreferences.state.first().navidromeKeepScreenOnForLyrics)
            reopenedPreferences.setNavidromeKeepScreenOnForLyrics(false)
            assertFalse(withTimeout(5_000) {
                reopenedPreferences.state.first { !it.navidromeKeepScreenOnForLyrics }
                    .navidromeKeepScreenOnForLyrics
            })
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
