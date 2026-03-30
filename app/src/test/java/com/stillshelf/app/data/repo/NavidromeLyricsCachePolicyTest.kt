package com.stillshelf.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavidromeLyricsCachePolicyTest {

    @Test
    fun buildNavidromeLyricsCacheKeys_changesScopedKeyWhenSourceChanges_butFallbackStaysStable() {
        val prefix = "server-a|library-a|"
        val trackId = "track-1"

        val lrclibKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = "source-lrclib"
        )
        val netEaseKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = "source-netease"
        )

        assertFalse(lrclibKeys.sourceScopedKey == netEaseKeys.sourceScopedKey)
        assertEquals(lrclibKeys.fallbackKey, netEaseKeys.fallbackKey)
    }

    @Test
    fun buildNavidromeLyricsCacheKeys_removeSourceThenReAdd_preservesFallbackButChangesScopedKey() {
        val prefix = "server-a|library-a|"
        val trackId = "track-1"

        val noSourceKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = null
        )
        val reAddedSourceKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = "source-lrclib"
        )

        assertEquals(noSourceKeys.fallbackKey, reAddedSourceKeys.fallbackKey)
        assertFalse(noSourceKeys.sourceScopedKey == reAddedSourceKeys.sourceScopedKey)
    }

    @Test
    fun fallbackCacheKey_staysUsableAcrossSourceDeletionAndReplacement() {
        val prefix = "server-a|library-a|"
        val trackId = "track-1"

        val deletedSourceKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = "source-lrclib"
        )
        val replacementSourceKeys = buildNavidromeLyricsCacheKeys(
            cachePrefix = prefix,
            trackId = trackId,
            activeLyricsSourceId = "source-netease"
        )

        assertEquals(deletedSourceKeys.fallbackKey, replacementSourceKeys.fallbackKey)
    }

    @Test
    fun sourceScopedAndFallbackKeys_areDistinctForNormalSources() {
        val keys = buildNavidromeLyricsCacheKeys(
            cachePrefix = "server-a|library-a|",
            trackId = "track-1",
            activeLyricsSourceId = "source-lrclib"
        )

        assertNotEquals(keys.sourceScopedKey, keys.fallbackKey)
    }

    @Test
    fun buildNavidromeLyricsCachePrefixForTrack_targetsAllVariantsOfOneTrack() {
        val prefix = buildNavidromeLyricsCachePrefixForTrack(
            cachePrefix = "server-a|library-a|",
            trackId = "track-1"
        )

        assertEquals("server-a|library-a|lyrics:track-1:", prefix)
    }
}
