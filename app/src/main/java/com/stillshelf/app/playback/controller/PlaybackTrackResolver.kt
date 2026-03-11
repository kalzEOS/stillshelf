package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack

internal data class PlaybackTrackSelection(
    val streamUrl: String,
    val trackStartOffsetMs: Long,
    val localSeekMs: Long,
    val bookDurationMs: Long
)

private data class NormalizedPlaybackTrack(
    val streamUrl: String,
    val startOffsetMs: Long,
    val endOffsetMs: Long?
)

internal fun resolvePlaybackTrackSelection(
    source: PlaybackSource,
    positionMs: Long
): PlaybackTrackSelection {
    val safePositionMs = positionMs.coerceAtLeast(0L)
    val tracks = normalizePlaybackTracks(source.tracks)
    val bookDurationMs = resolvePlaybackBookDurationMs(source, tracks)
    val selectedTrack = tracks.firstOrNull { track ->
        safePositionMs >= track.startOffsetMs &&
            (track.endOffsetMs == null || safePositionMs < track.endOffsetMs)
    } ?: tracks.lastOrNull()

    if (selectedTrack == null) {
        return PlaybackTrackSelection(
            streamUrl = source.streamUrl,
            trackStartOffsetMs = 0L,
            localSeekMs = safePositionMs,
            bookDurationMs = bookDurationMs
        )
    }

    return PlaybackTrackSelection(
        streamUrl = selectedTrack.streamUrl,
        trackStartOffsetMs = selectedTrack.startOffsetMs,
        localSeekMs = (safePositionMs - selectedTrack.startOffsetMs).coerceAtLeast(0L),
        bookDurationMs = bookDurationMs
    )
}

internal fun resolveNextTrackStartMs(
    tracks: List<PlaybackTrack>,
    currentTrackStartOffsetMs: Long
): Long? {
    return normalizePlaybackTracks(tracks)
        .firstOrNull { track -> track.startOffsetMs > currentTrackStartOffsetMs }
        ?.startOffsetMs
}

internal fun resolveTrackStartOffsetForPosition(
    tracks: List<PlaybackTrack>,
    positionMs: Long
): Long? {
    return normalizePlaybackTracks(tracks)
        .firstOrNull { track ->
            positionMs >= track.startOffsetMs &&
                (track.endOffsetMs == null || positionMs < track.endOffsetMs)
        }
        ?.startOffsetMs
        ?: normalizePlaybackTracks(tracks).lastOrNull()?.takeIf { positionMs >= it.startOffsetMs }?.startOffsetMs
}

private fun normalizePlaybackTracks(tracks: List<PlaybackTrack>): List<NormalizedPlaybackTrack> {
    val normalized = tracks
        .asSequence()
        .mapNotNull { track ->
            val streamUrl = track.streamUrl.trim()
            if (streamUrl.isBlank()) {
                null
            } else {
                NormalizedPlaybackTrack(
                    streamUrl = streamUrl,
                    startOffsetMs = (track.startOffsetSeconds * 1000.0).toLong().coerceAtLeast(0L),
                    endOffsetMs = track.durationSeconds
                        ?.takeIf { it >= 0.0 }
                        ?.let { durationSeconds ->
                            ((track.startOffsetSeconds + durationSeconds) * 1000.0).toLong().coerceAtLeast(0L)
                        }
                )
            }
        }
        .sortedBy { it.startOffsetMs }
        .toList()
    if (normalized.isEmpty()) return emptyList()
    return normalized.mapIndexed { index, track ->
        if (track.endOffsetMs != null) {
            track
        } else {
            track.copy(endOffsetMs = normalized.getOrNull(index + 1)?.startOffsetMs)
        }
    }
}

private fun resolvePlaybackBookDurationMs(
    source: PlaybackSource,
    tracks: List<NormalizedPlaybackTrack>
): Long {
    val bookDurationMs = source.book.durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { durationSeconds -> (durationSeconds * 1000.0).toLong() }
        ?: 0L
    val trackDurationMs = tracks.lastOrNull()?.endOffsetMs ?: 0L
    return maxOf(bookDurationMs, trackDurationMs)
}
