package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import androidx.test.platform.app.InstrumentationRegistry

class ListeningFiveIssuesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun daughtryTrackWithTwoFormatsOpensCurrentLibraryAlbumThroughTheActualViewModel() {
        val id = "f9462766-8938-4bb8-8d4a-1d4f5d4b0144"
        val entry = ListeningEntry("It’s Not Over", "Daughtry", 4, albumName = "Daughtry", musicBrainzRecordingId = id)
        val track = NavidromeTrack("new-flac", "It's Not Over", "Daughtry", "Daughtry", "current-album", "artist", 1,
            215, null, "", "FLAC", 900, musicBrainzId = id)
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "test")
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL, connection = connection))
        val settings = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val client = mockk<ListeningProviderClient> { coEvery { statistics(any()) } returns ListeningStatistics("ListenBrainz", topTracks = listOf(entry)) }
        val clients = mockk<ListeningClients> { every { forProvider(any()) } returns client }
        val repository = mockk<NavidromeRepository> {
            every { observeSession() } returns MutableStateFlow(null)
            coEvery { currentAuth(any()) } returns mockk(relaxed = true)
            coEvery { search(any(), any()) } returns AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(),
                listOf(track.copy(id = "mp3-copy", formatLabel = "MP3"), track)))
        }
        val cache = mockk<ListeningStatisticsCache>(relaxed = true)
        coEvery { cache.get(any()) } returns null
        lateinit var vm: ListeningStatisticsViewModel
        compose.runOnUiThread {
            vm = ListeningStatisticsViewModel(settings, clients, mockk(relaxed = true), repository, cache)
            compose.activity.viewModelStore.put("track-replacement", vm)
        }
        var opened: String? = null
        var browser: String? = null
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) { browser = uri }
            }) { ListeningStatisticsScreen({}, onOpenAlbum = { opened = it }, viewModel = vm) }
        } }
        compose.onNodeWithTag("statistics-tab-Tracks").performClick()
        compose.onNodeWithText("It’s Not Over").performClick()
        compose.waitForIdle()
        assertEquals("current-album", opened)
        assertNull(browser)
        compose.onNodeWithText("Could not match this entry").assertDoesNotExist()
        assertEquals(4L, vm.state.value.statistics!!.topTracks.single().plays)
    }

    @Test fun duplicateAlbumEditionsOpenAnInAppChoiceInsteadOfTheBrowser() {
        val album = NavidromeAlbum("original", "Daughtry", "Daughtry", "artist", 2006, 12, null, null, null)
        val entry = ListeningEntry("Daughtry", "Daughtry", 15)
        val vm = mockk<ListeningStatisticsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(ListeningStatisticsUiState(enabled = true,
                statistics = ListeningStatistics("ListenBrainz", topAlbums = listOf(entry))))
            coEvery { resolveForNavigation(any(), "album") } returns entry.copy(albumChoices = listOf(album, album.copy(id = "deluxe", songCount = 15)),
                albumChoiceFormats = mapOf("original" to "MP3", "deluxe" to "FLAC"))
        }
        var browser: String? = null
        var opened: String? = null
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides object : androidx.compose.ui.platform.UriHandler {
                override fun openUri(uri: String) { browser = uri }
            }) { ListeningStatisticsScreen({}, onOpenAlbum = { opened = it }, viewModel = vm) }
        } }
        compose.onNodeWithTag("statistics-tab-Albums").performClick()
        compose.onAllNodesWithText("Daughtry").onFirst().performClick()
        compose.onNodeWithText("Choose a library album").assertIsDisplayed()
        compose.onNodeWithText("FLAC", substring = true).assertIsDisplayed()
        compose.onNodeWithText("MP3", substring = true).assertIsDisplayed()
        screenshot("album-edition-choice")
        compose.onNodeWithTag("library-album-choice-deluxe").performClick()
        assertEquals("deluxe", opened)
        assertNull(browser)
    }

    @Test fun actualInsightsScreenSurvivesCancellationReentryAndCalendarChanges() {
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "test")
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true,
            statisticsSource = StatisticsSource.EXTERNAL, connection = connection))
        val settings = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }
        val cache = mockk<ListenBrainzInsightsCache>(relaxed = true) { coEvery { this@mockk.get(any()) } returns emptyMap() }
        val gate = CompletableDeferred<Unit>()
        val client = mockk<ListenBrainzInsightsClient> {
            coEvery { fetch(any(), any(), any()) } coAnswers {
                if (secondArg<InsightPeriod>() == InsightPeriod.THIS_MONTH && thirdArg<InsightKind>() == InsightKind.ERAS) gate.await()
                val kind = thirdArg<InsightKind>()
                InsightDataset(kind, if (kind == InsightKind.GENRES) listOf(InsightRow("Rock", 7)) else emptyList())
            }
        }
        lateinit var vm: ListenBrainzInsightsViewModel
        compose.runOnUiThread {
            vm = ListenBrainzInsightsViewModel(settings, repository, client, cache)
            compose.activity.viewModelStore.put("insights-lifecycle", vm)
        }
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { Surface {
            Column(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                if (visible.value) ListenBrainzInsightsScreen(0, vm)
            }
        } } }
        compose.onNodeWithTag("insights-loading").assertIsDisplayed()
        compose.onNodeWithText("Genre distribution").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertFalse(vm.state.value.loading)
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("choice-Statistics period").performClick()
        compose.onNodeWithTag("option-This Year").performClick()
        compose.waitForIdle()
        assertEquals(InsightPeriod.THIS_YEAR, vm.state.value.period)
        compose.onNodeWithTag("insights-loading").assertDoesNotExist()
        compose.onNodeWithText("Genre distribution").assertIsDisplayed()
        compose.onNodeWithTag("choice-Statistics period").performClick()
        compose.onNodeWithTag("option-This Month").performClick()
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithTag("insights-loading").assertDoesNotExist()
        assertFalse(vm.state.value.loading)
        screenshot("insights-period-completed")
    }

    @Test fun suppliedListeningDurationsAndUnambiguousClockLabelRenderOnMobile() {
        val day = LocalDate.of(2026, 11, 19)
        val trends = ListeningTrends(listOf(TrendPoint(day, 2, 300)), true, true, "", hour = 9,
            durationNote = "Based on recorded track lengths")
        compose.setContent { MaterialTheme { Surface {
            Column(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                ListeningTrendsDashboard(TrendsUiState(trends), TrendRange.MONTH) {}
            }
        } } }
        compose.onNodeWithText("Listening Time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Based on recorded track lengths").assertIsDisplayed()
        compose.onNodeWithText("9–10 AM").performScrollTo().assertIsDisplayed()
        screenshot("recorded-time-and-am-pm")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Android 17 can overlay a compatibility notice for MockK's test-only native agent.
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            android.os.SystemClock.sleep(500)
            val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
            roots.flatMap { it.findAccessibilityNodeInfosByText("Don't Show Again") }.firstOrNull()?.let { button ->
                var target: android.view.accessibility.AccessibilityNodeInfo? = button
                while (target != null && !target.isClickable) target = target.parent
                target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                android.os.SystemClock.sleep(400)
            }
        } finally { info.flags = originalFlags; automation.serviceInfo = info }
        java.io.File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
