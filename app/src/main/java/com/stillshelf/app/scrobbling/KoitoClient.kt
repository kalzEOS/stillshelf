package com.stillshelf.app.scrobbling

import javax.inject.Inject
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class KoitoClient @Inject constructor(
    private val http: ListeningHttp,
    private val submissions: ListenBrainzClient
) : ListeningProviderClient {
    override suspend fun validate(connection: ListeningConnection): String {
        if (connection.token.isNotBlank()) return submissions.validate(connection)
        http.json(Request.Builder().url(listeningUrl(connection.serverUrl, "apis/web/v1/summary",
            mapOf("period" to "all_time"))).build()).getLong("plays")
        return connection.username
    }
    override suspend fun submit(connection: ListeningConnection, listen: TrackListen) = submissions.submit(connection, listen)
    override suspend fun playingNow(connection: ListeningConnection, listen: TrackListen) = submissions.playingNow(connection, listen)

    override suspend fun statistics(connection: ListeningConnection): ListeningStatistics {
        suspend fun get(path: String) = http.json(Request.Builder()
            .url(listeningUrl(connection.serverUrl, "apis/web/v1/$path", mapOf("period" to "all_time", "limit" to "5")))
            .apply { if (connection.token.isNotBlank()) header("Authorization", "Token ${connection.token}") }.build())
        val summary = get("summary")
        val recent = get("listens")
        fun artists(item: JSONObject) = item.optJSONArray("artists").objects().joinToString(", ") { it.stringValue("name") }
        fun image(item: JSONObject): String? {
            val images = item.optJSONObject("image") ?: return null
            val raw = listOf("medium", "small", "large").firstNotNullOfOrNull { size -> images.stringValue(size).takeIf(String::isNotBlank) } ?: return null
            return publicListeningUrl(connection.serverUrl, raw)
        }
        fun chart(key: String, title: String) = summary.optJSONArray(key).objects().mapNotNull {
            val item = it.optJSONObject("item") ?: return@mapNotNull null
            val kind = when (key) { "top_artists" -> "artist"; "top_albums" -> "album"; else -> "track" }
            ListeningEntry(item.stringValue(title), artists(item), item.longOrNull("listen_count"), artworkUrl = image(item),
                detailsUrl = item.longOrNull("id")?.let { id -> listeningUrl(connection.serverUrl, "$kind/$id").toString() })
        }
        return ListeningStatistics("Koito · All time", summary.longOrNull("plays"),
            summary.longOrNull("minutes_listened")?.times(60),
            chart("top_artists", "name"), chart("top_albums", "title"), chart("top_tracks", "title"),
            recent.optJSONArray("items").objects().mapNotNull {
                val track = it.optJSONObject("track") ?: return@mapNotNull null
                val timestamp = runCatching {
                    val value = it.stringValue("time").replace(Regex("\\.\\d+(?=Z|[+-]\\d{2}:\\d{2}$)"), "")
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }.parse(value)?.time?.div(1000)
                }.getOrNull()
                ListeningEntry(track.stringValue("title"), artists(track), timestampSeconds = timestamp, artworkUrl = image(track),
                    detailsUrl = track.longOrNull("id")?.let { id -> listeningUrl(connection.serverUrl, "track/$id").toString() })
            })
    }
}
