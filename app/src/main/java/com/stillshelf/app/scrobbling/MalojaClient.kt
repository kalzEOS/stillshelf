package com.stillshelf.app.scrobbling

import javax.inject.Inject
import okhttp3.Request
import org.json.JSONObject

class MalojaClient @Inject constructor(
    private val http: ListeningHttp,
    private val submissions: ListenBrainzClient
) : ListeningProviderClient {
    override suspend fun validate(connection: ListeningConnection): String {
        if (connection.token.isNotBlank()) return submissions.validate(connection)
        http.json(Request.Builder().url(listeningUrl(connection.serverUrl, "apis/mlj_1/numscrobbles")).build()).getLong("amount")
        return connection.username
    }
    override suspend fun submit(connection: ListeningConnection, listen: TrackListen) = submissions.submit(connection, listen)
    // Maloja accepts playing_now but deliberately does not persist it as a listen.
    override suspend fun playingNow(connection: ListeningConnection, listen: TrackListen) = submissions.playingNow(connection, listen)

    override suspend fun statistics(connection: ListeningConnection): ListeningStatistics {
        // Maloja's native statistics are read-only. Its submission token is not a statistics proxy.
        suspend fun get(path: String): JSONObject {
            val result = http.json(Request.Builder()
                .url(listeningUrl(connection.serverUrl, "apis/mlj_1/$path", mapOf("perpage" to "5"))).build())
            if (result.optString("status") in setOf("error", "failure")) {
                throw ListeningApiException(400, false, "Maloja could not retrieve these statistics.")
            }
            return result
        }
        val count = get("numscrobbles").longOrNull("amount")
        fun artists(item: JSONObject): String = item.optJSONArray("artists")?.let { values ->
            (0 until values.length()).joinToString(", ") { values.optString(it) }
        }.orEmpty()
        suspend fun chart(kind: String): List<ListeningEntry> = get("charts/$kind").optJSONArray("list").objects().take(3).map {
            val entity = it.optJSONObject(if (kind == "tracks") "track" else "album")
            ListeningEntry(if (kind == "artists") it.stringValue("artist") else entity?.stringValue("title").orEmpty(),
                entity?.let(::artists).orEmpty(), it.longOrNull("scrobbles"))
        }
        val recent = get("scrobbles").optJSONArray("list").objects().mapNotNull {
            val track = it.optJSONObject("track") ?: return@mapNotNull null
            ListeningEntry(track.stringValue("title"), artists(track), timestampSeconds = it.longOrNull("time"))
        }
        return ListeningStatistics("Maloja · All time", count, topArtists = chart("artists"),
            topAlbums = chart("albums"), topTracks = chart("tracks"), recent = recent)
    }
}
