package com.stillshelf.app.playback.navidrome

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeOutputRoutingPolicyTest {

    @Test
    fun isAutoPreferredWiredRoute_returnsTrueForCarFriendlyWiredRoutes() {
        assertTrue(isAutoPreferredWiredRoute("wired"))
        assertTrue(isAutoPreferredWiredRoute("usb"))
        assertTrue(isAutoPreferredWiredRoute("hdmi"))
        assertTrue(isAutoPreferredWiredRoute("dock"))
        assertTrue(isAutoPreferredWiredRoute("line"))
    }

    @Test
    fun isAutoPreferredWiredRoute_returnsFalseForBluetoothAndSpeakerRoutes() {
        assertFalse(isAutoPreferredWiredRoute(null))
        assertFalse(isAutoPreferredWiredRoute("speaker"))
        assertFalse(isAutoPreferredWiredRoute("bt:car"))
    }
}
