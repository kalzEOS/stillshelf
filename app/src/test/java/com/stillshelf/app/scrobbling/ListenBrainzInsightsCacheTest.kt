package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ListenBrainzInsightsCacheTest {
    @Test fun encryptedCacheReopensPartialResultsAndSeparatesAccountsAndPeriods() = runTest {
        var persisted: String? = null
        val storage = mockk<SecureTokenStorage> {
            coEvery { getNamedSecret(any()) } answers { persisted }
            coEvery { saveNamedSecret(any(), any(), true, false) } answers { persisted = secondArg() }
        }
        val values = mapOf(InsightKind.ERAS to InsightCacheEntry(5, InsightDataset(InsightKind.ERAS,
            listOf(InsightRow("1990", 7, index = 1990))), failed = true, stale = true))
        ListenBrainzInsightsCache(storage).put("one-account-month", values)
        val reopened = ListenBrainzInsightsCache(storage)
        assertEquals(values, reopened.get("one-account-month"))
        assertTrue(reopened.get("other-account-month").isEmpty())
        assertTrue(reopened.get("one-account-year").isEmpty())
        coVerify { storage.saveNamedSecret("listening_insights_cache_v1", any(), true, false) }
        coVerify(exactly = 0) { storage.clearNamedSecret(any()) }
    }
}
