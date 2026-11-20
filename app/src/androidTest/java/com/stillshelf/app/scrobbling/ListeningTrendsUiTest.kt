package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ListeningTrendsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun barsShowExactCountsAndRangeChoicesAreAccessible() {
        val date = LocalDate.of(2026, 11, 19)
        // Anonymized daily distribution from a successful live ListenBrainz response.
        val counts = mapOf(9 to 9L, 10 to 22L, 11 to 6L, 12 to 13L, 13 to 1L, 14 to 6L, 15 to 11L)
        val live = org.json.JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("listenbrainz-trends-daily.json").bufferedReader().use { it.readText() })
        val heatmap = (0L..364L).map { offset -> val day = date.minusDays(364 - offset); TrendPoint(day, live.getJSONObject("days").optLong(day.toString(), 0)) }
        val data = ListeningTrends((0L..29L).map { offset -> val day = date.minusDays(29 - offset)
            TrendPoint(day, if (day.monthValue == 11) counts[day.dayOfMonth] ?: 0 else 0) }, true, true, "", hour = live.getInt("hour"), heatmap = heatmap)
        compose.setContent { MaterialTheme { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            ListeningTrendsDashboard(TrendsUiState(data), TrendRange.MONTH) {}
        } } }
        compose.onNodeWithTag("trend-chart").performScrollTo().performTouchInput {
            click(androidx.compose.ui.geometry.Offset(36.dp.toPx() + (width - 44.dp.toPx()) * 25.5f / 30, centerY)) }
        compose.onNodeWithTag("trend-tooltip").assertTextContains("11 scrobbles", substring = true)
        compose.onNodeWithText("Listening Heatmap").performScrollTo()
        compose.onNodeWithTag("trend-heatmap").performTouchInput { click(androidx.compose.ui.geometry.Offset(4.dp.toPx(), 51.dp.toPx())) }
        compose.onNodeWithTag("heatmap-tooltip").assertTextContains("16 scrobbles", substring = true)
        compose.onAllNodesWithText("Last 30 Days ▾").onFirst().performScrollTo()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), "trends-dashboard.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithText("Last 30 Days ▾").onFirst().performClick()
        compose.onNodeWithText("Last 12 Months").assertIsDisplayed()
    }
}
