package com.stillshelf.app.data.repo

import com.stillshelf.app.core.util.AppResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryRoutingRetryTest {

    @Test
    fun retryResolvedServerRefresh_retriesUntilSuccess() = runTest {
        var attempts = 0

        val result = retryResolvedServerRefresh(
            maxAttempts = 3,
            retryDelayMs = 1_000L
        ) {
            attempts += 1
            if (attempts < 3) {
                AppResult.Error("temporary")
            } else {
                AppResult.Success(Unit)
            }
        }

        assertTrue(result is AppResult.Success)
        assertEquals(3, attempts)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun retryResolvedServerRefresh_returnsLastErrorAfterMaxAttempts() = runTest {
        var attempts = 0

        val result = retryResolvedServerRefresh(
            maxAttempts = 3,
            retryDelayMs = 500L
        ) {
            attempts += 1
            AppResult.Error("still failing")
        }

        assertTrue(result is AppResult.Error)
        result as AppResult.Error
        assertEquals("still failing", result.message)
        assertEquals(3, attempts)
        assertEquals(1_000L, currentTime)
    }
}
