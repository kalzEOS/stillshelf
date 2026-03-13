package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.PlaybackTrack
import com.stillshelf.app.data.api.AudiobookshelfBookDetailDto

internal fun buildPlaybackTracks(
    detail: AudiobookshelfBookDetailDto,
    buildPlaybackUrl: (String) -> String
): List<PlaybackTrack> {
    val resolvedTracks = detail.audioTracks
        .mapNotNull { track ->
            val contentUrl = track.contentUrl.trim()
            if (contentUrl.isBlank()) {
                null
            } else {
                PlaybackTrack(
                    startOffsetSeconds = track.startOffsetSeconds.coerceAtLeast(0.0),
                    durationSeconds = track.durationSeconds?.takeIf { it >= 0.0 },
                    streamUrl = buildPlaybackUrl(contentUrl)
                )
            }
        }
        .sortedBy { it.startOffsetSeconds }
    if (resolvedTracks.isNotEmpty()) return resolvedTracks

    val fallbackPath = detail.streamPath?.trim().orEmpty()
    if (fallbackPath.isBlank()) return emptyList()
    return listOf(
        PlaybackTrack(
            startOffsetSeconds = 0.0,
            durationSeconds = detail.durationSeconds?.takeIf { it >= 0.0 },
            streamUrl = buildPlaybackUrl(fallbackPath)
        )
    )
}
