package com.stillshelf.app.scrobbling

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import io.mockk.*
import com.stillshelf.app.data.repo.NavidromeRepository
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Explicit opt-in only. Reads the local token in memory; never logs it or posts listens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListenBrainzAuthenticatedDiagnosticTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun productionInsightsViewModelCompletesCalendarPeriodsWithLiveResponses() = runBlocking {
        val path = System.getenv("STILLSHELF_LB_DIAGNOSTIC_TOKEN_FILE")
        assumeTrue("Requires the explicitly provided local diagnostic token", !path.isNullOrBlank())
        val token = File(path!!).readText().trim()
        require(token.isNotEmpty())
        val http = ListeningHttp()
        val username = ListenBrainzClient(http).validate(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = token, allowSubmissions = false))
        val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = username, token = token, allowSubmissions = false)
        val saved = MutableStateFlow(ListeningSettings(loaded = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL, connection = connection))
        val settings = mockk<ListeningSettingsStore> { every { state } returns saved; coEvery { load() } returns Unit }
        val repository = mockk<NavidromeRepository> { every { observeSession() } returns MutableStateFlow(null) }
        val cache = mockk<ListenBrainzInsightsCache>(relaxed = true) { coEvery { this@mockk.get(any()) } returns emptyMap() }
        Dispatchers.setMain(Dispatchers.Default.limitedParallelism(1))
        val owner = ViewModelStore()
        try {
            val vm = ListenBrainzInsightsViewModel(settings, repository, ListenBrainzInsightsClient(http), cache)
            owner.put("diagnostic", vm)
            withTimeout(90_000) {
                vm.state.first { it.enabled }
                for (period in listOf(InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH, InsightPeriod.THIS_YEAR)) {
                    vm.show(period)
                    val result = vm.state.first { it.period == period && it.results.isNotEmpty() && it.results.values.none { row -> row.loading } }
                    println("Live Insights VM: ${period.api}; completed=${result.results.size}; failed=${result.results.values.count { it.error }}; charts=${result.results.values.count { it.data?.meaningful() == true }}")
                    assertFalse("A completed period must not remain loading", result.results.values.any { it.loading })
                }
            }
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun additionalStatisticsParseActualProviderResponses() = runBlocking {
        val path = System.getenv("STILLSHELF_LB_DIAGNOSTIC_TOKEN_FILE")
        assumeTrue("Authenticated diagnostic needs an explicitly provided local token file", !path.isNullOrBlank())
        val token = File(path!!).readText().trim()
        require(token.isNotEmpty()) { "The local diagnostic token file is empty." }
        val http = ListeningHttp()
        withTimeout(90_000) {
            val username = ListenBrainzClient(http).validate(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = token, allowSubmissions = false))
            val connection = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = username, token = token, allowSubmissions = false)
            val client = ListenBrainzInsightsClient(http)
            for (kind in InsightKind.entries) {
                val data = client.fetch(connection, InsightPeriod.THIS_MONTH, kind)
                assertNotNull("The live account should expose ${kind.endpoint}", data)
                assertTrue("The live account should have positive ${kind.endpoint} records", data!!.rows.any { it.count > 0 })
                println("ListenBrainz insights read: ${kind.endpoint}; rows=${data.rows.size}; chartMeaningful=${data.meaningful()}")
            }
        }
    }
    @Test fun savedTokenAndStatisticsCanBeReadWithProductionClient() = runBlocking {
        val path = System.getenv("STILLSHELF_LB_DIAGNOSTIC_TOKEN_FILE")
        assumeTrue("Authenticated diagnostic needs an explicitly provided local token file", !path.isNullOrBlank())
        val token = File(path!!).readText().trim()
        require(token.isNotEmpty()) { "The local diagnostic token file is empty." }
        val client = ListenBrainzClient(ListeningHttp())
        withTimeout(35_000) {
            val username = client.validate(ListeningConnection(ListeningProvider.LISTEN_BRAINZ, token = token, allowSubmissions = false))
            assertTrue("Validated account must have a username", username.isNotBlank())
            val statistics = client.statistics(ListeningConnection(ListeningProvider.LISTEN_BRAINZ,
                username = username, token = token, allowSubmissions = false))
            assertNotNull("Listen count should be available", statistics.totalScrobbles)
            println("Authenticated ListenBrainz read succeeded; recentAvailable=${statistics.recent.isNotEmpty()}; tracks=${statistics.topTracks.size}; chartsAvailable=${statistics.topArtists.isNotEmpty()}; recordedDurationAvailable=${statistics.listeningSeconds != null}; partial=${statistics.note != null}")
        }
    }
}
