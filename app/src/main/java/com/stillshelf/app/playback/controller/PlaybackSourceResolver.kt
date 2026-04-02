package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl

internal data class AbsPlaybackSourceTarget(
    val playbackUrl: String,
    val headers: Map<String, String>
)

internal fun resolveAbsPlaybackSourceTarget(streamUrl: String): AbsPlaybackSourceTarget {
    val normalized = streamUrl.trim()
    val isLocalUri = normalized.startsWith("file://", ignoreCase = true) ||
        normalized.startsWith("content://", ignoreCase = true)
    if (isLocalUri) {
        return AbsPlaybackSourceTarget(
            playbackUrl = normalized,
            headers = emptyMap()
        )
    }
    val resolvedStream = splitAuthenticatedUrl(normalized)
    val headers = resolvedStream.authToken
        ?.takeIf { it.isNotBlank() }
        ?.let { token -> mapOf("Authorization" to authorizationHeaderValue(token)) }
        .orEmpty()
    return AbsPlaybackSourceTarget(
        playbackUrl = resolvedStream.cleanUrl,
        headers = headers
    )
}
