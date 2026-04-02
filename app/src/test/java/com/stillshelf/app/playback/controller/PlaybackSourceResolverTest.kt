package com.stillshelf.app.playback.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceResolverTest {

    @Test
    fun resolveAbsPlaybackSourceTarget_keepsLocalFileUrisUntouched() {
        val target = resolveAbsPlaybackSourceTarget("file:///storage/emulated/0/book/chapter1.mp3")

        assertEquals("file:///storage/emulated/0/book/chapter1.mp3", target.playbackUrl)
        assertTrue(target.headers.isEmpty())
    }

    @Test
    fun resolveAbsPlaybackSourceTarget_stripsEmbeddedTokenIntoAuthorizationHeader() {
        val streamUrl = "https://example.com/audio/track.mp3?token=secret-token"

        val target = resolveAbsPlaybackSourceTarget(streamUrl)

        assertEquals("https://example.com/audio/track.mp3", target.playbackUrl)
        assertEquals("Bearer secret-token", target.headers["Authorization"])
    }

    @Test
    fun resolveAbsPlaybackSourceTarget_preservesBearerPrefix() {
        val streamUrl = "https://example.com/audio/track.mp3?token=Bearer%20already-prefixed"

        val target = resolveAbsPlaybackSourceTarget(streamUrl)

        assertEquals("Bearer already-prefixed", target.headers["Authorization"])
    }
}
