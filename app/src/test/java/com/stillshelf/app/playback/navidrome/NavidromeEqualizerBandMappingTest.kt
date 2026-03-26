package com.stillshelf.app.playback.navidrome

import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeEqualizerBandMappingTest {

    @Test
    fun mapsLowBandUsingMilliHertzUnits() {
        val desiredLevels = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

        assertEquals(
            1f,
            resolveNavidromeEqualizerBandLevelDb(
                effectCenterFrequencyMilliHz = 32_000,
                desiredLevels = desiredLevels
            ),
            0.001f
        )
    }

    @Test
    fun mapsMidBandUsingNearestConfiguredFrequency() {
        val desiredLevels = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

        assertEquals(
            6f,
            resolveNavidromeEqualizerBandLevelDb(
                effectCenterFrequencyMilliHz = 1_000_000,
                desiredLevels = desiredLevels
            ),
            0.001f
        )
    }

    @Test
    fun doesNotCollapseAllBandsIntoHighestBand() {
        val desiredLevels = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

        assertEquals(
            10f,
            resolveNavidromeEqualizerBandLevelDb(
                effectCenterFrequencyMilliHz = 16_000_000,
                desiredLevels = desiredLevels
            ),
            0.001f
        )
        assertEquals(
            2f,
            resolveNavidromeEqualizerBandLevelDb(
                effectCenterFrequencyMilliHz = 64_000,
                desiredLevels = desiredLevels
            ),
            0.001f
        )
    }
}
