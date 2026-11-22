package com.stillshelf.app.absstats

import org.json.JSONArray
import org.json.JSONObject

internal class AbsPodcastEpisodeUnavailable : IllegalStateException("This episode is not yet stored in the Audiobookshelf server library")

/** RSS identifiers are not ABS episode IDs. Only an exact GUID/URL match may bridge them. */
internal fun nativePodcastEpisodeId(episodes: JSONArray, originalId: String): String? {
    val rows = (0 until episodes.length()).mapNotNull { episodes.optJSONObject(it) }
    rows.firstOrNull { it.optionalText("id") == originalId }?.let { return originalId }
    return rows.filter { it.optionalText("guid") == originalId || it.optionalText("enclosureURL") == originalId ||
        it.optJSONObject("enclosure")?.optionalText("url") == originalId }
        .mapNotNull { it.optionalText("id") }.distinct().singleOrNull()
}

internal fun nativePodcastSession(session: JSONObject, episodeId: String) = JSONObject(session.toString()).put("episodeId", episodeId)
