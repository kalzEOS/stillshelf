package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ListenBrainzInsightsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 11, 19)
    private val original = ListeningTrends((0L..6L).map { TrendPoint(date.minusDays(6 - it), it + 1) }, true, true, "", hour = 8)
    private val datasets = listOf(
        InsightDataset(InsightKind.DAILY, listOf(InsightRow("Monday", 4, index = 8), InsightRow("Monday", 0, index = 9))),
        InsightDataset(InsightKind.EVOLUTION, listOf(InsightRow("فنان", 3, bucket = "Monday", index = 1, identity = "a"),
            InsightRow("فنان", 5, bucket = "Tuesday", index = 2, identity = "a"))),
        InsightDataset(InsightKind.ARTIST_ALBUMS, listOf(InsightRow("Album", 3, "Artist", identity = "a"))),
        InsightDataset(InsightKind.ALBUMS, listOf(InsightRow("A period album", 7, "Artist"))),
        InsightDataset(InsightKind.TRACKS, listOf(InsightRow("A period track", 6, "Artist"))),
        InsightDataset(InsightKind.GENRES, listOf(InsightRow("Rock", 8), InsightRow("Folk", 2))),
        InsightDataset(InsightKind.ERAS, listOf(InsightRow("1983", 3, index = 1983), InsightRow("1989", 2, index = 1989), InsightRow("2001", 1, index = 2001))),
        InsightDataset(InsightKind.ORIGINS, listOf(InsightRow("USA", 3)))
    )
    private fun state() = InsightsUiState(true, InsightPeriod.LAST_WEEK, datasets.associate { it.kind to InsightResult(it) })

    private fun content(state: InsightsUiState, dark: Boolean = false, onPeriod: (InsightPeriod) -> Unit = {}) {
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface { Column(Modifier.width(320.dp).fillMaxHeight().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                ListeningTrendsDashboard(TrendsUiState(original), TrendRange.MONTH) {}
                ListenBrainzInsightsDashboard(state, onPeriod)
            } }
        } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (root?.findAccessibilityNodeInfosByText("Android App Compatibility")?.isNotEmpty() == true)
            root.findAccessibilityNodeInfosByText("OK").firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        android.os.SystemClock.sleep(250)
        java.io.File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun originalSectionsStayFirstAndNewChartsUseExactInteractiveValues() {
        content(state())
        val order = listOf("Listening Activity", "Listening Heatmap", "Listening Patterns", "ListenBrainz insights").map {
            compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
        assertTrue(order.zipWithNext().all { (a, b) -> a < b })
        // Scroll the vertical page before interacting with the grid's nested horizontal scroll.
        compose.onNodeWithTag("insight-DAILY").performScrollTo()
        compose.onNodeWithTag("insight-hour-1-8").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("insight-hour-tooltip").assertTextContains("4 listens", substring = true)
        screenshot("listenbrainz-additional-hour-grid")
        compose.onNodeWithTag("insight-evolution-chart").performScrollTo().performTouchInput { click(Offset(37.dp.toPx(), centerY)) }
        compose.onNodeWithTag("insight-evolution-tooltip").assertTextContains("3 listens", substring = true)
        screenshot("listenbrainz-additional-evolution")
        compose.onNodeWithTag("insight-bar-GENRES-0").performScrollTo().performClick()
        compose.onNodeWithTag("insight-tooltip-GENRES").assertTextContains("8 listens", substring = true)
        screenshot("listenbrainz-additional-genres")
    }

    @Test fun releaseDecadesDrillIntoRealYearsAndPeriodMenuKeepsOldRanges() {
        var requested: InsightPeriod? = null
        content(state(), onPeriod = { requested = it })
        compose.onNodeWithTag("insight-bar-ERAS-0").performScrollTo().performClick()
        compose.onNodeWithText("1983").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1989").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("All decades").performScrollTo().performClick()
        compose.onNodeWithText("1980s").assertIsDisplayed()
        screenshot("listenbrainz-additional-eras")
        compose.onNodeWithTag("choice-Statistics period").performScrollTo().performClick()
        compose.onNodeWithTag("option-This Month").performClick()
        assertEquals(InsightPeriod.THIS_MONTH, requested)
        compose.onAllNodesWithText("Last 30 Days ▾").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun missingOrUnsupportedStatisticsProduceNoEmptyChartsAndDoNotRemoveOriginals() {
        val state = mutableStateOf(InsightsUiState(true, results = InsightKind.entries.associateWith {
            InsightResult(InsightDataset(it, emptyList())) }))
        compose.setContent { MaterialTheme { Surface {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                ListeningTrendsDashboard(TrendsUiState(original), TrendRange.MONTH) {}
                ListenBrainzInsightsDashboard(state.value, {})
            }
        } } }
        compose.onNodeWithText("No additional statistics are available for this period.").performScrollTo().assertIsDisplayed()
        InsightKind.entries.forEach { compose.onNodeWithTag("insight-${it.name}").assertDoesNotExist() }
        compose.runOnIdle { state.value = InsightsUiState(enabled = false) }
        compose.onNodeWithTag("listenbrainz-insights").assertDoesNotExist()
        compose.onNodeWithText("Listening Activity").performScrollTo().assertIsDisplayed()
    }

    @Test fun darkThemeShowsOnlyMeaningfulChartsAndHidesDuplicateAllTimeRankings() {
        content(state().copy(period = InsightPeriod.ALL), dark = true)
        compose.onNodeWithTag("insight-ALBUMS").assertDoesNotExist()
        compose.onNodeWithTag("insight-TRACKS").assertDoesNotExist()
        compose.onNodeWithTag("insight-GENRES").performScrollTo().assertIsDisplayed()
        screenshot("listenbrainz-additional-dark-genres")
        compose.onNodeWithTag("insight-ORIGINS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 artists").assertIsDisplayed()
        screenshot("listenbrainz-additional-origins")
    }

    @Test fun partialRefreshStopsTheProgressBarAndMakesRetryUsable() {
        val state = mutableStateOf(InsightsUiState(true, results = mapOf(
            InsightKind.GENRES to InsightResult(error = true), InsightKind.ERAS to InsightResult(loading = true))))
        var retries = 0
        compose.setContent { MaterialTheme { Surface {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                ListenBrainzInsightsDashboard(state.value, {}, { retries++ })
            }
        } } }
        compose.onNodeWithTag("insights-loading").assertIsDisplayed()
        compose.onNodeWithText("Retry additional statistics").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(results = state.value.results +
            (InsightKind.ERAS to InsightResult(error = true))) }
        compose.onNodeWithTag("insights-loading").assertDoesNotExist()
        compose.onNodeWithText("Retry additional statistics").assertIsEnabled().performClick()
        assertEquals(1, retries)
    }
}
