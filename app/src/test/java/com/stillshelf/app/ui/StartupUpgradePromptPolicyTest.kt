package com.stillshelf.app.ui

import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.model.BackendProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupUpgradePromptPolicyTest {

    @Test
    fun showsForUpdatedAbsUserWithoutNavidromeSetup() {
        val preferences = SessionPreferenceState(
            activeServerId = "abs-server",
            activeLibraryId = "library-a",
            selectedBackend = BackendProvider.AUDIOBOOKSHELF
        )

        val shouldShow = shouldShowUpgradeMessagePrompt(
            currentVersionName = "0.2.6-beta.21",
            currentVersionCode = 59,
            firstInstallTimeMs = 100L,
            lastUpdateTimeMs = 200L,
            acknowledgedVersion = null,
            preferences = preferences
        )

        assertTrue(shouldShow)
    }

    @Test
    fun doesNotShowForFreshInstall() {
        val preferences = SessionPreferenceState(
            activeServerId = "abs-server",
            activeLibraryId = "library-a",
            selectedBackend = BackendProvider.AUDIOBOOKSHELF
        )

        val shouldShow = shouldShowUpgradeMessagePrompt(
            currentVersionName = "0.2.6-beta.21",
            currentVersionCode = 59,
            firstInstallTimeMs = 100L,
            lastUpdateTimeMs = 100L,
            acknowledgedVersion = null,
            preferences = preferences
        )

        assertFalse(shouldShow)
    }

    @Test
    fun doesNotShowOnceAcknowledged() {
        val preferences = SessionPreferenceState(
            activeServerId = "abs-server",
            activeLibraryId = "library-a",
            selectedBackend = BackendProvider.AUDIOBOOKSHELF
        )

        val shouldShow = shouldShowUpgradeMessagePrompt(
            currentVersionName = "0.2.6-beta.21",
            currentVersionCode = 59,
            firstInstallTimeMs = 100L,
            lastUpdateTimeMs = 200L,
            acknowledgedVersion = "0.2.6-beta.21",
            preferences = preferences
        )

        assertFalse(shouldShow)
    }

    @Test
    fun doesNotShowWhenNavidromeAlreadyConfigured() {
        val preferences = SessionPreferenceState(
            activeServerId = "abs-server",
            activeLibraryId = "library-a",
            selectedBackend = BackendProvider.AUDIOBOOKSHELF,
            navidromeServers = listOf(
                com.stillshelf.app.core.model.NavidromeServer(
                    id = "nav-server",
                    name = "Remote",
                    baseUrl = "https://music.example.com",
                    username = "user",
                    createdAt = 1L
                )
            )
        )

        val shouldShow = shouldShowUpgradeMessagePrompt(
            currentVersionName = "0.2.6-beta.21",
            currentVersionCode = 59,
            firstInstallTimeMs = 100L,
            lastUpdateTimeMs = 200L,
            acknowledgedVersion = null,
            preferences = preferences
        )

        assertFalse(shouldShow)
    }
}
