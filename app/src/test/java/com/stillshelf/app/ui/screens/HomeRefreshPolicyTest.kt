package com.stillshelf.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshPolicyTest {

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresForegroundTransition() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = true,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = "library-1"
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresVisibleHomeScreen() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = false,
                activeLibraryId = "library-1"
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_requiresActiveLibrary() {
        assertFalse(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = null
            )
        )
    }

    @Test
    fun shouldRefreshHomeOnAppForeground_refreshesWhenReturningToVisibleHome() {
        assertTrue(
            shouldRefreshHomeOnAppForeground(
                wasInForeground = false,
                isInForeground = true,
                homeScreenVisible = true,
                activeLibraryId = "library-1"
            )
        )
    }
}
