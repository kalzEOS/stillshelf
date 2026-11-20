package com.stillshelf.app.scrobbling

import org.json.JSONObject
import okhttp3.HttpUrl

/** Keep provider counts while retaining its MusicBrainz identity and returned cover mapping. */
internal fun listenBrainzEntry(node: JSONObject, kind: String, titleField: String): ListeningEntry {
    val mapping = node.optJSONObject("mbid_mapping") ?: node.optJSONObject("additional_info") ?: node
    fun id(key: String) = mapping.stringValue(key).ifBlank { node.stringValue(key) }
        .takeIf { it.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) }
    val artistId = id("artist_mbid") ?: (mapping.optJSONArray("artist_mbids") ?: node.optJSONArray("artist_mbids"))?.optString(0)
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) }
    val entity = when(kind) { "artist" -> "artist"; "album" -> "release"; else -> "recording" }
    val mbid = when(kind) { "artist" -> artistId; "album" -> id("release_mbid"); else -> id("recording_mbid") }
    val title = node.stringValue(titleField)
    val artist = node.stringValue("artist_name")
    val details = if (mbid != null) "https://musicbrainz.org/$entity/$mbid" else
        HttpUrl.Builder().scheme("https").host("musicbrainz.org").addPathSegment("search")
            .addQueryParameter("query", if (kind == "artist") title else "$title $artist")
            .addQueryParameter("type", entity).addQueryParameter("method", "indexed").build().toString()
    val coverRelease = id("caa_release_mbid") ?: id("release_mbid")
    val caaId = mapping.longOrNull("caa_id")
    val cover = if (kind == "artist" || coverRelease == null) null else
        "https://coverartarchive.org/release/$coverRelease/${caaId?.let { "$it-250.jpg" } ?: "front-250"}"
    return ListeningEntry(title, if (kind == "artist") "" else artist, node.longOrNull("listen_count"),
        artworkUrl = cover, detailsUrl = details, albumName = node.stringValue("release_name"), musicBrainzArtistId = artistId,
        musicBrainzReleaseId = id("release_mbid"), musicBrainzRecordingId = id("recording_mbid"))
}
