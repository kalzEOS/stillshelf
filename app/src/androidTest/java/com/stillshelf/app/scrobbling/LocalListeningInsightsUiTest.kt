package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalListeningInsightsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun localInsightsReuseTheChartsAndExposePeriodAndTouchInteractions() {
        val daily = InsightDataset(InsightKind.DAILY, listOf(InsightRow("Monday", 8, index = 9)))
        val eras = InsightDataset(InsightKind.ERAS, listOf(InsightRow("2001", 4, index = 2001)))
        val results = mapOf(InsightKind.DAILY to InsightResult(daily), InsightKind.ERAS to InsightResult(eras),
            InsightKind.GENRES to InsightResult(InsightDataset(InsightKind.GENRES, listOf(InsightRow("Rock", 5)))))
        val vm = mockk<LocalListeningInsightsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(InsightsUiState(true, results = results))
            every { details } returns MutableStateFlow(LocalInsights(results, listOf(InsightRow("Artist", 8)), listOf(InsightRow("2026-11-20", 1))))
        }
        compose.setContent { MaterialTheme {
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                LocalListeningInsightsScreen(0, vm)
            }
        } }
        compose.onNodeWithText("Local listening insights").assertIsDisplayed()
        compose.onNodeWithText("Day and hour (${ZoneId.systemDefault().id})").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("insight-hour-1-9").performScrollTo().performClick()
        compose.onNodeWithTag("insight-hour-tooltip").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Music eras and release years").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("insight-bar-ERAS-0").performScrollTo().performClick()
        compose.onNodeWithText("All decades").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Artist discovery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("choice-Statistics period").performScrollTo().performClick()
        compose.onNodeWithTag("option-Last Month").performClick()
    }
}
