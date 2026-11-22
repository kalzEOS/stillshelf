package com.stillshelf.app.absstats

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.ui.screens.CustomizeUiState
import com.stillshelf.app.ui.screens.CustomizeViewModel
import com.stillshelf.app.ui.screens.HomeSectionIds
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AbsStatisticsScreenTest {
    @Test fun podcastLibraryShowsTheActualAccountNameAndLibraryType() {
        selection.value = AbsStatisticsScope("podcasts", listOf(Library("podcasts", "server", "Family shows", "podcast")), "Reader")
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("Audiobookshelf · Account: Reader").assertExists()
        compose.onNodeWithText("Family shows ▾").assertExists()
        compose.onNodeWithText("Podcast library · account: Reader").assertExists()
    }
    @Test fun pendingMergeKeepsServerTotalAndExplainsTheSelectedAccountAndLibrary() {
        state.value = state.value.copy(snapshot = state.value.snapshot.copy(localMergePending = true, partial = true, cached = true))
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("Audiobook library · this account only").assertExists()
        compose.onNodeWithText("Showing saved server statistics.", substring = true).assertExists()
        compose.onNodeWithText("1 h 0 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("My books ▾").performScrollTo().performClick()
        compose.onNodeWithText("All libraries").performClick()
        compose.onNodeWithText("Books and podcasts · this account only").assertExists()
    }
    @Test fun combinedHistoryExplainsItsLocalContribution() {
        state.value = state.value.copy(snapshot = state.value.snapshot.copy(localAdditionalMs = 30_000))
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("Includes listening recorded on this device.", substring = true).assertExists()
        compose.onNodeWithText("Server history and listening recorded on this device").performScrollTo().assertIsDisplayed()
    }
    @Test fun clearingLocalHistoryRequiresConfirmation() {
        val controls = MutableStateFlow(AbsLocalControls(key = "a".repeat(64), account = "Test account", count = 2))
        val local = mockk<AbsLocalHistoryViewModel>(relaxed = true) { every { state } returns controls }
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { AbsLocalHistoryControls(local) } } }
        compose.onNodeWithText("Clear Local History").performClick()
        compose.onNodeWithText("Clear local history?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        verify(exactly = 0) { local.clear() }
        compose.onNodeWithText("Clear Local History").performClick()
        compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
        verify(exactly = 1) { local.clear() }
    }
    @Test fun quickCacheRefreshDoesNotFlashIndicatorOrChangeUpdatedLabel() {
        state.value = state.value.copy(snapshot = state.value.snapshot.copy(updatedAt = 1_800_000_000_000L, cached = true))
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("Updated ", substring = true).assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.mainClock.advanceTimeBy(100)
        compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(loading = false, snapshot = state.value.snapshot.copy(cached = false)) }
        compose.mainClock.advanceTimeBy(300)
        compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.onNodeWithText("Saved statistics", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Updated ", substring = true).assertExists()
        compose.mainClock.autoAdvance = true
    }
    @Test fun missingPodcastHistoryShowsUnavailableThenKeepsNewRecordedTimeAndWarning() {
        selection.value = AbsStatisticsScope("podcasts", listOf(Library("podcasts", "server", "Podcasts", "podcast")))
        state.value = AbsStatisticsState(AbsStatisticsSnapshot(statistics = stats.copy(seconds = 0.0), podcastHistoryIncomplete = true))
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("Listening time unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0 min").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(snapshot = state.value.snapshot.copy(statistics = stats.copy(seconds = 30.0))) }
        compose.onNodeWithText("30 sec").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Earlier podcast listening time is unavailable:", substring = true).assertExists()
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun fullYearChartKeepsDailyBarsVisibleAndScrollable() {
        val today = java.time.LocalDate.now()
        val first = today.minusDays(364)
        val days = (0L..364L).associate { first.plusDays(it) to 60.0 }
        compose.setContent { MaterialTheme { AbsDailyActivity(days) } }
        compose.onNodeWithText("365 days").performClick()
        compose.onNodeWithContentDescription("$first: 1 min").assertIsDisplayed()
        assertTrue(compose.onNodeWithContentDescription("$first: 1 min").fetchSemanticsNode().size.width > 0)
        compose.onNodeWithContentDescription("$today: 1 min").performScrollTo().performClick()
        compose.onNodeWithText("${today.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))} · 1 min").assertIsDisplayed()
    }
    private val stats = AbsListeningStatistics(3600.0, null, emptyMap(), emptyList(), emptyList())
    private val state = MutableStateFlow(AbsStatisticsState(AbsStatisticsSnapshot(statistics = stats)))
    private val libraries = listOf(Library("mine", "server", "My books", "book"), Library("other", "server", "Other books", "book"))
    private val selection = MutableStateFlow(AbsStatisticsScope("mine", libraries))
    private val history = MutableStateFlow(AbsHistoryState(hasMore = false))
    private val vm = mockk<AbsStatisticsViewModel>(relaxed = true) {
        every { this@mockk.state } returns this@AbsStatisticsScreenTest.state
        every { this@mockk.selection } returns this@AbsStatisticsScreenTest.selection
        every { this@mockk.history } returns this@AbsStatisticsScreenTest.history
        every { library(any()) } answers {
            this@AbsStatisticsScreenTest.selection.value = selection.value.copy(libraryId = firstArg())
            this@AbsStatisticsScreenTest.state.value = state.value.copy(snapshot = AbsStatisticsSnapshot(statistics = stats.copy(seconds = 7200.0)))
        }
    }
    @Test fun librarySelectorShowsIndependentFilteredStatistics() {
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { _, _ -> }, vm) } }
        compose.onNodeWithText("My books ▾").performClick()
        compose.onNodeWithText("Other books").performClick()
        compose.onNodeWithText("Other books ▾").assertIsDisplayed()
        compose.onNodeWithText("2 h 0 min").assertIsDisplayed()
        verify { vm.library("other") }
        compose.onNodeWithText("Scrobbling & Statistics").assertDoesNotExist()
        compose.onNodeWithText("Send scrobbles from StillShelf").assertDoesNotExist()
    }
    @Test fun historyOpensTheCorrectBookOrPodcastInsideTheApp() {
        history.value = AbsHistoryState(listOf(
            AbsListeningSession("session", "show", "episode", "Example episode", "Host", 120.0, null)), total = 1, hasMore = false)
        var opened: Pair<String, String?>? = null
        compose.setContent { MaterialTheme { AbsStatisticsScreen({}, { item, episode -> opened = item to episode }, vm) } }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("Example episode").performScrollTo().performClick()
        assertEquals("show" to "episode", opened)
    }
    @Test fun absVisibilitySwitchPreservesOtherHomePreferences() {
        val settings = MutableStateFlow(CustomizeUiState(hiddenPersonalizedSectionIds = setOf("other-section")))
        val customization = mockk<CustomizeViewModel>(relaxed = true) {
            every { uiState } returns settings
            every { setHiddenPersonalizedSectionIds(any()) } answers {
                settings.value = settings.value.copy(hiddenPersonalizedSectionIds = firstArg())
            }
        }
        compose.setContent { MaterialTheme { AbsStatisticsSettings({}, {}, {}, customization = customization, localHistory = {}) } }
        compose.onNodeWithContentDescription("Show Audiobookshelf Listening Statistics").performScrollTo().performClick()
        assertEquals(setOf("other-section", HomeSectionIds.ABS_STATISTICS), settings.value.hiddenPersonalizedSectionIds)
        compose.onNodeWithContentDescription("Show Audiobookshelf Listening Statistics").performClick()
        assertEquals(setOf("other-section"), settings.value.hiddenPersonalizedSectionIds)
    }
}
