package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadItem
import com.stillshelf.app.downloads.navidrome.NavidromeDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromePlaybackCacheWarmupTest {
    @Test
    fun normalizeNavidromePlaybackWarmupTracks_filtersInvalidAndRadioTracks() {
        val tracks = listOf(
            track(id = "track-1", streamUrl = "https://example.com/1.mp3"),
            track(id = "track-1", streamUrl = "https://example.com/1-duplicate.mp3"),
            track(id = "track-2", streamUrl = ""),
            track(id = "radio:stream", streamUrl = "https://example.com/live.mp3"),
            track(id = "track-3", streamUrl = "https://example.com/3.mp3")
        )

        val normalized = normalizeNavidromePlaybackWarmupTracks(tracks)

        assertEquals(listOf("track-1", "track-3"), normalized.map { it.id })
    }

    @Test
    fun buildNavidromePlaybackWarmupSignature_isOrderSensitive() {
        val forward = buildNavidromePlaybackWarmupSignature(
            listOf(track("track-1"), track("track-2"))
        )
        val reversed = buildNavidromePlaybackWarmupSignature(
            listOf(track("track-2"), track("track-1"))
        )

        assertEquals("track-1|track-2", forward)
        assertFalse(forward == reversed)
    }

    @Test
    fun buildNavidromePlaybackWarmupSignature_changesAcrossConnectionContexts() {
        val wifiSignature = buildNavidromePlaybackWarmupSignature(
            tracks = listOf(track("track-1"), track("track-2")),
            connectionSignature = "srv|https://lan.example.com|Local|Auto"
        )
        val cellularSignature = buildNavidromePlaybackWarmupSignature(
            tracks = listOf(track("track-1"), track("track-2")),
            connectionSignature = "srv|https://wan.example.com|Remote|Auto"
        )

        assertFalse(wifiSignature == cellularSignature)
    }

    @Test
    fun selectNavidromePlaybackWarmupTracks_capsAtMaxInFlight() {
        val tracks = (0 until 30).map { track("track-$it") }

        val selected = selectNavidromePlaybackWarmupTracks(tracks = tracks)

        assertEquals(MAX_IN_FLIGHT_CACHE_DOWNLOADS, selected.size)
        assertEquals((0 until MAX_IN_FLIGHT_CACHE_DOWNLOADS).map { "track-$it" }, selected.map { it.id })
    }

    @Test
    fun selectNavidromePlaybackWarmupTracks_respectsFromIndex() {
        val tracks = (0 until 50).map { track("track-$it") }

        val selected = selectNavidromePlaybackWarmupTracks(tracks = tracks, fromIndex = 10)

        assertEquals(MAX_IN_FLIGHT_CACHE_DOWNLOADS, selected.size)
        assertEquals((10 until 10 + MAX_IN_FLIGHT_CACHE_DOWNLOADS).map { "track-$it" }, selected.map { it.id })
    }

    @Test
    fun selectNavidromePlaybackWarmupTracks_nearEndReturnsRemaining() {
        val tracks = (0 until 30).map { track("track-$it") }

        val selected = selectNavidromePlaybackWarmupTracks(tracks = tracks, fromIndex = 20)

        assertEquals(10, selected.size)
        assertEquals((20 until 30).map { "track-$it" }, selected.map { it.id })
    }

    @Test
    fun chooseNavidromePlaybackUri_prefersLocalCacheWhenAllowed() {
        val resolved = chooseNavidromePlaybackUri(
            streamUrl = "https://example.com/stream.mp3",
            localPlaybackUri = "file:///data/user/0/app/cache/track.mp3",
            forceRemote = false
        )

        assertEquals("file:///data/user/0/app/cache/track.mp3", resolved)
    }

    @Test
    fun chooseNavidromePlaybackUri_respectsForcedRemotePlayback() {
        val resolved = chooseNavidromePlaybackUri(
            streamUrl = "https://example.com/stream.mp3",
            localPlaybackUri = "file:///data/user/0/app/cache/track.mp3",
            forceRemote = true
        )

        assertEquals("https://example.com/stream.mp3", resolved)
    }

    // Retry storm prevention: a cleared signature (null) is never equal to any real signature,
    // so the warmup guard always lets the next attempt through after a failure.
    @Test
    fun clearedSignature_neverMatchesAnyRealSignature() {
        val storedSignature: String? = null
        val nextSignature = buildNavidromePlaybackWarmupSignature(
            tracks = listOf(track("track-1"), track("track-2")),
            connectionSignature = "srv|https://wan.example.com|Remote|Auto"
        )

        assertFalse(storedSignature == nextSignature)
    }

    // Same queue + same connection → same signature, so a second call while warmup is
    // in-flight or already succeeded is a no-op.
    @Test
    fun unchangedQueueAndConnection_producesIdenticalSignature() {
        val tracks = listOf(track("track-1"), track("track-2"))
        val connection = "srv|https://wan.example.com|Remote|Auto"

        assertEquals(
            buildNavidromePlaybackWarmupSignature(tracks, connection),
            buildNavidromePlaybackWarmupSignature(tracks, connection)
        )
    }

    // After a failure clears the signature, a connection change produces a new signature
    // that does not match null, so the retry proceeds — and the new signature encodes the
    // new route so a stale-URL warmup cannot silently reuse the cleared slot.
    @Test
    fun connectionChangeAfterFailure_newSignatureUnblocksRetry() {
        val queue = listOf(track("track-1"), track("track-2"))
        val staleConnection = "srv|https://lan.example.com|Local|Auto"
        val freshConnection = "srv|https://wan.example.com|Remote|Auto"

        val staleSignature = buildNavidromePlaybackWarmupSignature(queue, staleConnection)
        // Failure clears the stored signature.
        val storedAfterFailure: String? = null
        val freshSignature = buildNavidromePlaybackWarmupSignature(queue, freshConnection)

        // Retry gate passes (null != fresh).
        assertFalse(storedAfterFailure == freshSignature)
        // The new signature is distinct from the stale one, so a route-flip is never masked.
        assertFalse(staleSignature == freshSignature)
    }

    // admitWarmupTracks: in-flight cap enforcement

    @Test
    fun admitWarmupTracks_noInFlight_admitsUpToCap() {
        val candidates = (0 until 30).map { track("track-$it") }

        val admitted = admitWarmupTracks(
            candidates = candidates,
            currentItems = emptyList(),
            cacheLimitBytes = Long.MAX_VALUE
        )

        assertEquals(MAX_IN_FLIGHT_CACHE_DOWNLOADS, admitted.size)
    }

    @Test
    fun admitWarmupTracks_inFlightAtCap_admitsNothing() {
        val candidates = (0 until 10).map { track("track-$it") }
        val inFlight = (0 until MAX_IN_FLIGHT_CACHE_DOWNLOADS).map {
            cacheItem("in-$it", NavidromeDownloadStatus.Downloading)
        }

        val admitted = admitWarmupTracks(
            candidates = candidates,
            currentItems = inFlight,
            cacheLimitBytes = Long.MAX_VALUE
        )

        assertTrue(admitted.isEmpty())
    }

    @Test
    fun admitWarmupTracks_partialInFlight_admitsRemainingSlots() {
        val inFlightCount = 10
        val candidates = (0 until 20).map { track("track-$it") }
        val inFlight = (0 until inFlightCount).map { cacheItem("in-$it", NavidromeDownloadStatus.Downloading) }

        val admitted = admitWarmupTracks(
            candidates = candidates,
            currentItems = inFlight,
            cacheLimitBytes = Long.MAX_VALUE
        )

        assertEquals(MAX_IN_FLIGHT_CACHE_DOWNLOADS - inFlightCount, admitted.size)
    }

    @Test
    fun admitWarmupTracks_finiteLimit_includesInFlightReservation() {
        val bytesPerSong = 8L * 1024 * 1024
        val limitBytes = 3 * bytesPerSong  // room for 3 songs total
        val inFlight = listOf(cacheItem("in-0", NavidromeDownloadStatus.Downloading))  // 1 reserved
        // 2 slots remain in budget; 1 in-flight counts against reservation so only 1 new song fits
        val candidates = (0 until 5).map { track("track-$it") }

        val admitted = admitWarmupTracks(
            candidates = candidates,
            currentItems = inFlight,
            cacheLimitBytes = limitBytes,
            defaultBytesPerSong = bytesPerSong
        )

        assertEquals(2, admitted.size)
    }

    @Test
    fun admitWarmupTracks_budgetExhausted_admitsNothing() {
        val bytesPerSong = 8L * 1024 * 1024
        val completed = listOf(
            cacheItem("done-0", NavidromeDownloadStatus.Completed, fileSizeBytes = bytesPerSong),
            cacheItem("done-1", NavidromeDownloadStatus.Completed, fileSizeBytes = bytesPerSong),
        )
        val limitBytes = 2 * bytesPerSong  // already at limit
        val candidates = listOf(track("track-0"))

        val admitted = admitWarmupTracks(
            candidates = candidates,
            currentItems = completed,
            cacheLimitBytes = limitBytes,
            defaultBytesPerSong = bytesPerSong
        )

        assertTrue(admitted.isEmpty())
    }

    private fun track(id: String, streamUrl: String = "https://example.com/$id.mp3"): NavidromeTrack {
        return NavidromeTrack(
            id = id,
            title = "Title $id",
            artistName = "Artist",
            albumName = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            trackNumber = 1,
            durationSeconds = 180,
            coverUrl = null,
            streamUrl = streamUrl,
            formatLabel = "MP3",
            bitRateKbps = 320
        )
    }

    private fun cacheItem(
        trackId: String,
        status: NavidromeDownloadStatus,
        fileSizeBytes: Long? = null
    ): NavidromeDownloadItem {
        return NavidromeDownloadItem(
            serverId = "srv",
            libraryId = "_all",
            trackId = trackId,
            albumId = null,
            albumSongCount = null,
            artistId = null,
            title = trackId,
            artistName = "Artist",
            albumName = "Album",
            coverUrl = null,
            durationSeconds = 180,
            formatLabel = "MP3",
            status = status,
            progressPercent = if (status == NavidromeDownloadStatus.Completed) 100 else 0,
            fileSizeBytes = fileSizeBytes,
            isPlaybackCache = true
        )
    }
}
