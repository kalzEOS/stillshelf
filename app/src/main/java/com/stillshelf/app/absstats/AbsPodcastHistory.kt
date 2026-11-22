package com.stillshelf.app.absstats

import org.json.JSONArray
import org.json.JSONObject

internal data class AbsPodcastGap(val showId: String, val episodeId: String, val libraryId: String?, val detectedAt: Long) {
    fun json() = JSONObject().put("show", showId).put("episode", episodeId)
        .put("library", libraryId ?: JSONObject.NULL).put("detectedAt", detectedAt)
}

internal fun podcastGaps(json: JSONObject?): List<AbsPodcastGap> = json?.optJSONArray("gaps")?.let { rows ->
    (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { row ->
        val show = row.optionalText("show") ?: return@let null
        val episode = row.optionalText("episode") ?: return@let null
        AbsPodcastGap(show, episode, row.optionalText("library"), row.nonnegative("detectedAt")?.toLong() ?: return@let null)
    } }
}.orEmpty()

/** Progress is evidence of past playback, never a substitute for measured listening time. */
internal fun missingPodcastHistory(progress: JSONArray, sessions: JSONArray, previous: List<AbsPodcastGap>, now: Long): List<AbsPodcastGap> {
    val recorded = (0 until sessions.length()).mapNotNull { sessions.optJSONObject(it) }
        .filter { (it.nonnegative("timeListening") ?: 0.0) > 0 }
    fun matches(row: JSONObject, show: String, episode: String) = row.optionalText("libraryItemId") == show && row.optionalText("episodeId") == episode
    // New playback does not repair a known older gap. An imported historical session can.
    val gaps = previous.filter { gap -> recorded.none { matches(it, gap.showId, gap.episodeId) &&
        (it.nonnegative("startedAt")?.toLong() ?: Long.MAX_VALUE) < gap.detectedAt } }.toMutableList()
    for (i in 0 until progress.length()) {
        val row = progress.optJSONObject(i) ?: continue
        val show = row.optionalText("libraryItemId") ?: continue
        val episode = row.optionalText("episodeId") ?: continue
        if ((row.nonnegative("currentTime") ?: 0.0) <= 0 && !row.optBoolean("isFinished")) continue
        if (recorded.none { matches(it, show, episode) } && gaps.none { it.showId == show && it.episodeId == episode })
            gaps += AbsPodcastGap(show, episode, null, now)
    }
    return gaps
}
