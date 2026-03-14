package com.stillshelf.app.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRefreshPolicyTest {
    @Test
    fun ifMissing_refreshesWhenLocalRecordIsMissing() {
        assertTrue(
            shouldRefreshDetail(
                policy = DetailRefreshPolicy.IfMissing,
                localExists = false,
                lastSuccessfulSyncAtMs = null,
                maxAgeMs = 1_000L,
                nowMs = 5_000L
            )
        )
    }

    @Test
    fun ifMissing_skipsRefreshWhenLocalRecordExists() {
        assertFalse(
            shouldRefreshDetail(
                policy = DetailRefreshPolicy.IfMissing,
                localExists = true,
                lastSuccessfulSyncAtMs = 4_000L,
                maxAgeMs = 1_000L,
                nowMs = 5_000L
            )
        )
    }

    @Test
    fun ifStale_refreshesWhenRecordIsOlderThanBudget() {
        assertTrue(
            shouldRefreshDetail(
                policy = DetailRefreshPolicy.IfStale,
                localExists = true,
                lastSuccessfulSyncAtMs = 1_000L,
                maxAgeMs = 1_000L,
                nowMs = 3_000L
            )
        )
    }

    @Test
    fun ifStale_skipsRefreshWhenRecordIsFresh() {
        assertFalse(
            shouldRefreshDetail(
                policy = DetailRefreshPolicy.IfStale,
                localExists = true,
                lastSuccessfulSyncAtMs = 4_500L,
                maxAgeMs = 1_000L,
                nowMs = 5_000L
            )
        )
    }

    @Test
    fun force_alwaysRefreshes() {
        assertTrue(
            shouldRefreshDetail(
                policy = DetailRefreshPolicy.Force,
                localExists = true,
                lastSuccessfulSyncAtMs = 5_000L,
                maxAgeMs = 60_000L,
                nowMs = 5_001L
            )
        )
    }
}
