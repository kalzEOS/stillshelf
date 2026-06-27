package com.stillshelf.app.ui.common

import android.net.Uri
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack
import java.io.File

internal fun PlaybackSource.asSingleTrackDownloadSource(): PlaybackSource {
    return copy(
        tracks = listOf(
            PlaybackTrack(
                startOffsetSeconds = 0.0,
                durationSeconds = book.durationSeconds,
                streamUrl = streamUrl
            )
        )
    )
}

internal fun PlaybackSource.asLocalPlaybackSource(localPath: String?): PlaybackSource? {
    val localUri = localPath.toPlayableLocalUri() ?: return null
    return copy(
        streamUrl = localUri,
        tracks = tracks.map { track ->
            track.copy(streamUrl = localUri)
        }
    )
}

private fun String?.toPlayableLocalUri(): String? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return when {
        normalized.startsWith("file://", ignoreCase = true) -> normalized
        normalized.startsWith("content://", ignoreCase = true) -> normalized
        else -> Uri.fromFile(File(normalized)).toString()
    }
}
