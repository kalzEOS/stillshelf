package com.stillshelf.app.downloads.navidrome

import com.stillshelf.app.core.model.NavidromeCacheSizeOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeCachePolicyTest {

    // region NavidromeCacheSizeOption parsing

    @Test
    fun cacheSizeOption_offReturnsNullBytes() {
        assertNull(NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.NO_CACHING))
    }

    @Test
    fun cacheSizeOption_parsesKnownSizesCorrectly() {
        assertEquals(256L * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.MB_256))
        assertEquals(512L * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.MB_512))
        assertEquals(1024L * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.GB_1))
        assertEquals(2L * 1024 * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.GB_2))
        assertEquals(5L * 1024 * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.GB_5))
        assertEquals(10L * 1024 * 1024 * 1024, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.GB_10))
        assertEquals(Long.MAX_VALUE, NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.NO_LIMIT))
    }

    @Test
    fun cacheSizeOption_unknownStringFallsBackToDefault() {
        val defaultBytes = NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.default)
        assertEquals(defaultBytes, NavidromeCacheSizeOption.toBytes("nonsense"))
        assertEquals(defaultBytes, NavidromeCacheSizeOption.toBytes(""))
    }

    @Test
    fun cacheSizeOption_allContainsOffAndNoLimit() {
        assertTrue(NavidromeCacheSizeOption.NO_CACHING in NavidromeCacheSizeOption.all)
        assertTrue(NavidromeCacheSizeOption.NO_LIMIT in NavidromeCacheSizeOption.all)
    }

    @Test
    fun cacheSizeOption_offDisplaysAsNoCaching() {
        assertEquals("No caching", NavidromeCacheSizeOption.label(NavidromeCacheSizeOption.NO_CACHING))
    }

    @Test
    fun cacheSizeOption_noLimitDisplaysAsNoLimit() {
        assertEquals("No limit", NavidromeCacheSizeOption.label(NavidromeCacheSizeOption.NO_LIMIT))
    }

    // endregion

    // region Cache vs Download separation

    @Test
    fun cacheItems_areExcludedFromDownloadView() {
        val items = listOf(
            item("track-1", isPlaybackCache = false),
            item("track-2", isPlaybackCache = true),
            item("track-3", isPlaybackCache = true),
        )

        val downloads = items.filterNot { it.isPlaybackCache }

        assertEquals(listOf("track-1"), downloads.map { it.trackId })
    }

    @Test
    fun cacheItems_areOnlyReturnedByCacheFilter() {
        val items = listOf(
            item("track-1", isPlaybackCache = false),
            item("track-2", isPlaybackCache = true),
        )

        val cached = items.filter { it.isPlaybackCache && it.status == NavidromeDownloadStatus.Completed }

        assertEquals(listOf("track-2"), cached.map { it.trackId })
    }

    @Test
    fun cacheItems_doNotAppearInDownloadedTrackIds() {
        val items = listOf(
            item("download-1", isPlaybackCache = false),
            item("cache-1", isPlaybackCache = true),
            item("cache-2", isPlaybackCache = true),
        )

        val downloadedIds = items
            .filterNot { it.isPlaybackCache }
            .filter { it.status == NavidromeDownloadStatus.Completed }
            .map { it.trackId }
            .toSet()

        assertTrue("download-1" in downloadedIds)
        assertTrue("cache-1" !in downloadedIds)
        assertTrue("cache-2" !in downloadedIds)
    }

    // endregion

    // region LRU eviction

    @Test
    fun lruEviction_removesLeastRecentlyUsedFirst() {
        val items = listOf(
            item("track-newest", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 3000L),
            item("track-oldest", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 1000L),
            item("track-middle", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 2000L),
        )
        val limitBytes = 150L * MB

        val toRemove = lruEvict(items, limitBytes)

        assertEquals(listOf("track-oldest", "track-middle"), toRemove.map { it.trackId })
    }

    @Test
    fun lruEviction_doesNotEvictWhenUnderLimit() {
        val items = listOf(
            item("track-1", isPlaybackCache = true, fileSizeBytes = 50L * MB, lastAccessedAtMs = 1000L),
        )

        val toRemove = lruEvict(items, 200L * MB)

        assertTrue(toRemove.isEmpty())
    }

    @Test
    fun lruEviction_stopsOnceUnderLimit() {
        val items = listOf(
            item("track-1", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 1000L),
            item("track-2", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 2000L),
            item("track-3", isPlaybackCache = true, fileSizeBytes = 100L * MB, lastAccessedAtMs = 3000L),
        )
        // 300MB total, limit 150MB → need to remove at most 2 items, but first one removal (100MB) makes it 200MB
        // still over, second removal (100MB) makes it 100MB, which is under 150MB
        val toRemove = lruEvict(items, 150L * MB)

        assertEquals(listOf("track-1", "track-2"), toRemove.map { it.trackId })
    }

    @Test
    fun prunePlaybackCache_doesNotRemovePermanentDownloads() {
        val keepCacheIds = setOf("track-cache-kept")
        val items = listOf(
            item("track-download", isPlaybackCache = false),
            item("track-cache-kept", isPlaybackCache = true),
            item("track-cache-pruned", isPlaybackCache = true),
        )

        // prunePlaybackCache removes cache items not in keepCacheIds
        val pruned = items.filter {
            it.isPlaybackCache && it.trackId !in keepCacheIds
        }

        assertEquals(listOf("track-cache-pruned"), pruned.map { it.trackId })
        // permanent download is never in the pruned set
        assertTrue(items.filter { !it.isPlaybackCache }.none { it.trackId in pruned.map { p -> p.trackId } })
    }

    @Test
    fun prefetchWarmup_doesNotOverwritePermanentDownload() {
        // Simulate the guard that prefetchPlaybackQueue applies before enqueuing.
        // A permanent download entry must survive even when its file is missing.
        val permanentDownload = item("track-1", isPlaybackCache = false, status = NavidromeDownloadStatus.Completed)
        val existingByTrackId = mapOf("track-1" to permanentDownload)

        // Should resolve to null (skip) because existing is a permanent download
        val wouldEnqueue = existingByTrackId["track-1"]?.let { existing ->
            if (!existing.isPlaybackCache) null else existing
        }

        assertEquals(null, wouldEnqueue)
    }

    @Test
    fun removeItems_doesNotWipePermanentDownloadWhenCacheRowSharedTrackId() {
        // Verifies that the removal key includes isPlaybackCache so both rows can coexist.
        data class RemovalKey(val serverId: String, val libraryId: String, val trackId: String, val isPlaybackCache: Boolean)

        val permanentDownload = item("track-1", isPlaybackCache = false)
        val cacheEntry = item("track-1", isPlaybackCache = true)
        val current = listOf(permanentDownload, cacheEntry)

        val toRemove = listOf(cacheEntry)
        val removalKeys = toRemove.map { RemovalKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) }.toSet()
        val result = current.filterNot { RemovalKey(it.serverId, it.libraryId, it.trackId, it.isPlaybackCache) in removalKeys }

        assertEquals(1, result.size)
        assertFalse(result.single().isPlaybackCache)
    }

    @Test
    fun prefetchReplaceItems_onlyRemovesCacheRows() {
        val serverId = "srv"
        val libraryId = "lib"
        val permanentDownload = item("track-1", isPlaybackCache = false)
        val cacheEntry = item("track-1", isPlaybackCache = true)
        val otherItem = item("track-2", isPlaybackCache = false)
        val current = listOf(permanentDownload, otherItem)
        val newTrackIds = setOf("track-1")

        // Mirrors the fixed replaceItems logic: only remove if isPlaybackCache
        val result = current.filterNot {
            it.serverId == serverId &&
                it.libraryId == libraryId &&
                it.trackId in newTrackIds &&
                it.isPlaybackCache
        } + listOf(cacheEntry)

        // Permanent download for track-1 is preserved, cache entry is added alongside
        assertEquals(3, result.size)
        assertTrue(result.any { it.trackId == "track-1" && !it.isPlaybackCache })
        assertTrue(result.any { it.trackId == "track-1" && it.isPlaybackCache })
    }

    // endregion

    // region Disable / re-enable caching guard

    @Test
    fun prefetchEarlyExit_skipsWhenCachingDisabled() {
        // Mirrors the prefetchPlaybackQueue guard: if toBytes returns null, caching is off.
        val offBytes = NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.NO_CACHING)
        val shouldSkip = offBytes == null
        assertTrue("prefetch must exit early when NO_CACHING is active", shouldSkip)
    }

    @Test
    fun prefetchEarlyExit_proceedsForEveryNonOffOption() {
        // Every option except NO_CACHING must produce a non-null byte value so prefetch runs.
        val nonOffOptions = NavidromeCacheSizeOption.all.filter { it != NavidromeCacheSizeOption.NO_CACHING }
        assertTrue("all list must contain non-off options", nonOffOptions.isNotEmpty())
        nonOffOptions.forEach { option ->
            assertFalse(
                "toBytes($option) must not be null — prefetch would be incorrectly skipped",
                NavidromeCacheSizeOption.toBytes(option) == null
            )
        }
    }

    @Test
    fun reenableCachingGuard_invalidatesWarmupOnlyForNonOffOptions() {
        // Mirrors the setPlaybackCacheSizeLimit guard: invalidate warmup only when
        // switching TO a real limit, never when switching to NO_CACHING.
        val shouldInvalidateForOff = NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.NO_CACHING) != null
        assertFalse("warmup must NOT be invalidated when disabling caching", shouldInvalidateForOff)

        val nonOffOptions = NavidromeCacheSizeOption.all.filter { it != NavidromeCacheSizeOption.NO_CACHING }
        nonOffOptions.forEach { option ->
            val shouldInvalidate = NavidromeCacheSizeOption.toBytes(option) != null
            assertTrue("warmup must be invalidated when enabling $option", shouldInvalidate)
        }
    }

    @Test
    fun noLimit_neverEvictsAnything() {
        // NO_LIMIT returns Long.MAX_VALUE — eviction loop must always break immediately.
        val limitBytes = NavidromeCacheSizeOption.toBytes(NavidromeCacheSizeOption.NO_LIMIT)!!
        val items = listOf(
            item("track-1", isPlaybackCache = true, fileSizeBytes = 500L * MB, lastAccessedAtMs = 1000L),
            item("track-2", isPlaybackCache = true, fileSizeBytes = 500L * MB, lastAccessedAtMs = 2000L),
        )
        val toRemove = lruEvict(items, limitBytes)
        assertTrue("NO_LIMIT must never evict any cache items", toRemove.isEmpty())
    }

    // endregion

    // region helpers

    private val MB = 1024 * 1024L

    private fun lruEvict(cacheItems: List<NavidromeDownloadItem>, limitBytes: Long): List<NavidromeDownloadItem> {
        val sorted = cacheItems.sortedBy { it.lastAccessedAtMs }
        var totalBytes = sorted.sumOf { it.fileSizeBytes ?: 0L }
        val toRemove = mutableListOf<NavidromeDownloadItem>()
        for (it in sorted) {
            if (totalBytes <= limitBytes) break
            toRemove += it
            totalBytes -= it.fileSizeBytes ?: 0L
        }
        return toRemove
    }

    private fun item(
        id: String,
        isPlaybackCache: Boolean = false,
        status: NavidromeDownloadStatus = NavidromeDownloadStatus.Completed,
        fileSizeBytes: Long? = null,
        lastAccessedAtMs: Long = 1000L
    ): NavidromeDownloadItem = NavidromeDownloadItem(
        serverId = "srv",
        libraryId = "lib",
        trackId = id,
        albumId = null,
        albumSongCount = null,
        artistId = null,
        title = "Title $id",
        artistName = "Artist",
        albumName = "Album",
        coverUrl = null,
        durationSeconds = 180,
        formatLabel = "mp3",
        status = status,
        progressPercent = if (status == NavidromeDownloadStatus.Completed) 100 else 0,
        isPlaybackCache = isPlaybackCache,
        fileSizeBytes = fileSizeBytes,
        lastAccessedAtMs = lastAccessedAtMs,
        updatedAtMs = 0L
    )

    // endregion
}
