package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackAudioEffectsResolverTest {

    @Test
    fun resolveBoostGainMb_clampsIntoSupportedRange() {
        assertEquals(0, resolveBoostGainMb(-1f))
        assertEquals(900, resolveBoostGainMb(0.5f))
        assertEquals(1800, resolveBoostGainMb(1f))
    }

    @Test
    fun resolveSoftToneBandLevel_leavesLowBandsFlat() {
        assertEquals(
            0.toShort(),
            resolveSoftToneBandLevel(
                softToneLevel = 1f,
                bandIndex = 0,
                bandCount = 5,
                minLevelMb = -1500,
                maxLevelMb = 1500
            )
        )
    }

    @Test
    fun resolveSoftToneBandLevel_attenuatesHigherBands() {
        assertEquals(
            (-900).toShort(),
            resolveSoftToneBandLevel(
                softToneLevel = 1f,
                bandIndex = 4,
                bandCount = 5,
                minLevelMb = -1500,
                maxLevelMb = 1500
            )
        )
    }

    @Test
    fun resolveSoftToneBandLevel_respectsEffectRange() {
        assertEquals(
            (-300).toShort(),
            resolveSoftToneBandLevel(
                softToneLevel = 1f,
                bandIndex = 4,
                bandCount = 5,
                minLevelMb = -300,
                maxLevelMb = 300
            )
        )
    }
}
