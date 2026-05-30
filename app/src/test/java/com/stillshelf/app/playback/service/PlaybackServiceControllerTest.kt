package com.stillshelf.app.playback.service

import com.stillshelf.app.core.model.BackendProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServiceControllerTest {

    @Test
    fun startCanClaimOwnershipWhenServiceIsInactive() {
        assertTrue(
            shouldClaimPlaybackServiceOwnership(
                activeOwner = BackendProvider.AUDIOBOOKSHELF,
                serviceIsActive = false,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }

    @Test
    fun startCanRefreshCurrentOwnersNotification() {
        assertTrue(
            shouldClaimPlaybackServiceOwnership(
                activeOwner = BackendProvider.NAVIDROME,
                serviceIsActive = true,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }

    @Test
    fun startCannotStealActiveOwnershipFromAnotherBackend() {
        assertFalse(
            shouldClaimPlaybackServiceOwnership(
                activeOwner = BackendProvider.AUDIOBOOKSHELF,
                serviceIsActive = true,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }

    @Test
    fun stopIsAllowedForMatchingOwner() {
        assertTrue(
            shouldStopPlaybackServiceForOwner(
                activeOwner = BackendProvider.NAVIDROME,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }

    @Test
    fun stopIsIgnoredForDifferentOwner() {
        assertFalse(
            shouldStopPlaybackServiceForOwner(
                activeOwner = BackendProvider.AUDIOBOOKSHELF,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }

    @Test
    fun stopIsIgnoredWhenNothingOwnsTheService() {
        assertFalse(
            shouldStopPlaybackServiceForOwner(
                activeOwner = null,
                requestedOwner = BackendProvider.NAVIDROME
            )
        )
    }
}
