package com.stillshelf.app.ui.navigation

import com.stillshelf.app.core.model.BackendProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootStartGraphResolutionTest {

    @Test
    fun routesPendingAudiobookshelfSelectionToMainOnColdStart() {
        val resolved = resolveDesiredRootStartGraph(
            selectedBackend = BackendProvider.AUDIOBOOKSHELF,
            hasNavidromeSession = false,
            hasActiveServer = true,
            hasActiveLibrary = false,
            hasPendingActiveLibrary = true
        )

        assertEquals(GraphRoute.MAIN, resolved)
    }

    @Test
    fun routesAudiobookshelfToAuthWithoutReadyOrPendingLibrary() {
        val resolved = resolveDesiredRootStartGraph(
            selectedBackend = BackendProvider.AUDIOBOOKSHELF,
            hasNavidromeSession = false,
            hasActiveServer = true,
            hasActiveLibrary = false,
            hasPendingActiveLibrary = false
        )

        assertEquals(GraphRoute.AUTH, resolved)
    }

    @Test
    fun keepsMainDuringPendingAudiobookshelfLibraryActivation() {
        val resolved = resolveDisplayedRootStartGraph(
            previousStartGraph = GraphRoute.MAIN,
            desiredStartGraph = GraphRoute.AUTH,
            selectedBackend = BackendProvider.AUDIOBOOKSHELF,
            hasActiveServer = true,
            hasPendingActiveLibrary = true
        )

        assertEquals(GraphRoute.MAIN, resolved)
    }

    @Test
    fun allowsAuthWhenNoPendingAudiobookshelfLibraryActivationExists() {
        val resolved = resolveDisplayedRootStartGraph(
            previousStartGraph = GraphRoute.MAIN,
            desiredStartGraph = GraphRoute.AUTH,
            selectedBackend = BackendProvider.AUDIOBOOKSHELF,
            hasActiveServer = true,
            hasPendingActiveLibrary = false
        )

        assertEquals(GraphRoute.AUTH, resolved)
    }

    @Test
    fun usesServersAsAuthEntryWhenAnyAudiobookshelfServerExists() {
        val resolved = resolveAuthStartDestination(hasAnyServer = true)

        assertEquals(AuthRoute.SERVERS, resolved)
    }

    @Test
    fun usesAddServerAsAuthEntryWhenNoAudiobookshelfServersExist() {
        val resolved = resolveAuthStartDestination(hasAnyServer = false)

        assertEquals(AuthRoute.ADD_SERVER, resolved)
    }

    @Test
    fun requiresExistingLibraryRowForResolvedActiveLibrarySelection() {
        assertFalse(
            hasResolvedActiveLibrarySelection(
                hasActiveServer = true,
                activeLibraryId = "library-1",
                availableLibraryIds = emptySet()
            )
        )
    }

    @Test
    fun acceptsActiveLibraryWhenMatchingLibraryRowExists() {
        assertTrue(
            hasResolvedActiveLibrarySelection(
                hasActiveServer = true,
                activeLibraryId = "library-1",
                availableLibraryIds = setOf("library-1", "library-2")
            )
        )
    }
}
