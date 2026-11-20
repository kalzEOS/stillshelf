package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningStatisticsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedAlbumRequestIsNotPresentedAsAnEmptyLibraryOrUnsupportedProvider() {
        val stats = ListeningStatistics("ListenBrainz · All time", unavailableSections = setOf(ListeningMetric.ALBUMS))
        compose.setContent { MaterialTheme {
            ListeningStatisticsPage(ListeningStatisticsUiState(enabled = true, statistics = stats), {}, {}, { true }, {})
        } }
        compose.onNodeWithTag("statistics-tab-Albums").performClick()
        compose.onNodeWithText("Albums temporarily unavailable").assertIsDisplayed()
        compose.onNodeWithText("No Albums statistics available").assertDoesNotExist()
        compose.onNodeWithText("Albums · Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry Albums").assertIsEnabled()
    }

    @Test fun unmatchedChartsNeverOpenBrowserUnlessExplicitlyRequested() {
        var opened: String? = null
        val entries = (1..50).map { ListeningEntry("Artist $it", plays = it.toLong(),
            artworkUrl = if (it % 2 == 0) "https://provider.invalid/image" else null,
            detailsUrl = "https://musicbrainz.org/search?query=Artist$it&type=artist") }
        val stats = ListeningStatistics("ListenBrainz · All time", topArtists = entries,
            topTracks = listOf(ListeningEntry("Song", "Artist", detailsUrl = "https://musicbrainz.org/recording/known")),
            moreCharts = setOf(ListeningChart.ARTISTS))
        val vm = mockk<ListeningStatisticsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(ListeningStatisticsUiState(enabled = true, statistics = stats))
            coEvery { resolveForNavigation(any(), any()) } coAnswers { firstArg() }
        }
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) { ListeningStatisticsScreen(onBack = {}, viewModel = vm) }
        } }
        compose.onNodeWithTag("statistics-tab-Artists").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Artist 50"))
        compose.onNodeWithText("Artist 50").performClick()
        compose.onNodeWithText("Could not match this entry").assertIsDisplayed()
        assertNull(opened)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Load more Artists"))
        compose.onNodeWithText("Load more Artists").performClick()
        io.mockk.verify { vm.loadMore(ListeningChart.ARTISTS) }
        compose.onNodeWithTag("statistics-tab-Tracks").performClick()
        compose.onNodeWithText("Song").performClick()
        assertNull(opened)
        compose.onNodeWithText("Open provider website").performClick()
        assertEquals("https://musicbrainz.org/recording/known", opened)
    }

    @Test fun cachedRowWithProviderArtworkRetriesLibraryNavigationWithoutOpeningBrowser() {
        val entry = ListeningEntry("Cached Song", "Artist", artworkUrl = "https://provider.invalid/image",
            detailsUrl = "https://listenbrainz.org/user/reader")
        val state = MutableStateFlow(ListeningStatisticsUiState(enabled = true, isCached = true,
            error = "Service unavailable", statistics = ListeningStatistics("ListenBrainz", recent = listOf(entry))))
        val vm = mockk<ListeningStatisticsViewModel>(relaxed = true) {
            every { this@mockk.state } returns state
            coEvery { resolveForNavigation(entry, "track") } returns entry.copy(albumId = "local-album")
        }
        var openedAlbum: String? = null
        var browser: String? = null
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) { browser = uri }
            }) { ListeningStatisticsSection(vm, onOpenAlbum = { openedAlbum = it }) }
        } }
        compose.onNodeWithText("Cached Song").performClick()
        compose.waitForIdle()
        assertEquals("local-album", openedAlbum)
        assertNull(browser)
    }

    @Test fun chartNavigationBackRestoresTabAndScrollForArtistsAlbumsAndTracks() {
        val artists = (1..30).map { ListeningEntry("Entry $it", "Artist", plays = it.toLong(), artistId = "artist-$it") }
        val albums = (1..30).map { ListeningEntry("Entry $it", "Artist", plays = it.toLong(), albumId = "album-$it") }
        val stats = ListeningStatistics("Navidrome history", topArtists = artists, topAlbums = albums, topTracks = albums)
        val vm = mockk<ListeningStatisticsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(ListeningStatisticsUiState(enabled = true, statistics = stats))
            coEvery { resolveForNavigation(any(), any()) } coAnswers { firstArg() }
        }
        compose.setContent { MaterialTheme {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "home") {
                composable("home") { Button(onClick = { nav.navigate("statistics") }) { Text("See all") } }
                composable("statistics") {
                    ListeningStatisticsScreen(onBack = { nav.popBackStack() }, viewModel = vm,
                        onOpenArtist = { nav.navigate("entry") }, onOpenAlbum = { nav.navigate("entry") })
                }
                composable("entry") { Button(onClick = { nav.popBackStack() }) { Text("Back to Statistics") } }
            }
        } }
        compose.onNodeWithText("See all").performClick()
        listOf("Artists", "Albums", "Tracks").forEach { tab ->
            compose.onNodeWithTag("statistics-tab-$tab").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Entry 12"))
            val top = compose.onNodeWithText("Entry 12").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithText("Entry 12").performClick()
            compose.onNodeWithText("Back to Statistics").performClick()
            compose.onNodeWithTag("statistics-tab-$tab").assertIsSelected()
            compose.onNodeWithText("Entry 12").assertIsDisplayed()
            assertEquals(top, compose.onNodeWithText("Entry 12").fetchSemanticsNode().boundsInRoot.top, 2f)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("See all").assertIsDisplayed()
    }

    @Test fun nativeMissingMetricsStillRenderThreeEqualCardsAndSeeAllOpensStyledCharts() {
        val stats = ListeningStatistics("Navidrome · Recorded history", totalScrobbles = 606,
            topArtists = listOf(ListeningEntry("كاظم الساهر", plays = 42)),
            topAlbums = listOf(ListeningEntry("Album", "Artist", 30)),
            topTracks = listOf(ListeningEntry("Song", "Artist", 10)))
        val vm = mockk<ListeningStatisticsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(ListeningStatisticsUiState(enabled = true, statistics = stats, sourceLabel = stats.sourceLabel))
        }
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                Box(Modifier.width(320.dp).fillMaxHeight()) { ListeningStatisticsSection(vm) }
            }
        } } }
        val cards = listOf("Scrobbles", "Listening Time", "Top Artist").map {
            compose.onNodeWithTag("metric-$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(kotlin.math.abs(cards[0].width - cards[1].width) < 2f)
        assertTrue(kotlin.math.abs(cards[1].width - cards[2].width) < 2f)
        assertTrue(kotlin.math.abs(cards[0].height - cards[1].height) < 2f)
        assertTrue(kotlin.math.abs(cards[1].height - cards[2].height) < 2f)
        assertTrue(cards[0].right <= cards[1].left && cards[1].right <= cards[2].left)
        compose.onNodeWithText("Not provided").assertIsDisplayed()
        screenshot("home-three-cards-320dp")
        compose.onNodeWithText("See all").performClick()
        compose.onNodeWithText("Artists").performClick()
        compose.onAllNodesWithText("كاظم الساهر").onLast().assertIsDisplayed()
        compose.onNodeWithText("42").assertIsDisplayed()
        screenshot("artists-arabic-320dp")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("See all").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
