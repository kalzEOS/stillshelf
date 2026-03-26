package com.stillshelf.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeEqualizerProfileTest {

    @Test
    fun normalizedBandLevels_clampsAndRoundsValues() {
        val profile = NavidromeEqualizerProfile(
            id = "eq-1",
            name = "Test",
            bandLevelsDb = listOf(6.7f, 1.6f, Float.NaN, -8.2f)
        )

        assertEquals(
            listOf(6f, 2f, 0f, -6f, 0f, 0f, 0f, 0f, 0f, 0f),
            profile.normalizedBandLevelsDb()
        )
    }

    @Test
    fun effectiveBandLevels_removeSharedOffsetAcrossBands() {
        val profile = NavidromeEqualizerProfile(
            id = "eq-2",
            name = "Offset",
            bandLevelsDb = listOf(6f, 4f, 2f, 0f, -2f)
        )

        assertEquals(
            listOf(0f, -2f, -4f, -6f, -8f, -6f, -6f, -6f, -6f, -6f),
            profile.effectiveBandLevelsDb()
        )
    }

    @Test
    fun flatProfile_detectsUniformBoostAsFlatCurve() {
        assertTrue(
            NavidromeEqualizerProfile(
                id = "eq-3",
                name = "Uniform boost",
                bandLevelsDb = List(10) { 6f }
            ).isFlat()
        )
    }

    @Test
    fun flatProfile_detectsZeroedBands() {
        assertTrue(
            NavidromeEqualizerProfile(
                id = "eq-4",
                name = "Flat"
            ).isFlat()
        )
    }
}
