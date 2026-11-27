package com.stillshelf.app.playback.navidrome

import com.stillshelf.app.core.model.NavidromeTrack

/** Prefer unheard candidates, relaxing history only when the library offers no alternatives. */
internal fun selectNavidromeContinuationTracks(
    candidates: List<NavidromeTrack>,
    referenceId: String,
    recentIds: Set<String>,
    limit: Int = 20
): List<NavidromeTrack> {
    val eligible = candidates.distinctBy { it.id }.filter {
        it.id.isNotBlank() && it.id != referenceId && !it.id.startsWith("radio:") && it.streamUrl.isNotBlank()
    }
    val fresh = eligible.filterNot { it.id in recentIds }
    return fresh.ifEmpty { eligible }.shuffled().take(limit)
}

/** Invalidates in-flight recommendations whenever the user changes playback intent. */
internal class NavidromeContinuationRequest {
    private var generation = 0L
    private var pending: Long? = null

    fun begin(): Long? {
        if (pending != null) return null
        return generation.also { pending = it }
    }

    fun isCurrent(token: Long): Boolean = pending == token && generation == token

    fun finish(token: Long) {
        if (isCurrent(token)) {
            pending = null
            generation++
        }
    }

    fun invalidate() {
        generation++
        pending = null
    }
}
