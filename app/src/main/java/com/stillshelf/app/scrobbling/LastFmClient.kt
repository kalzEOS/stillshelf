package com.stillshelf.app.scrobbling

import java.security.MessageDigest
import javax.inject.Inject
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

internal fun lastFmSignature(parameters: Map<String, String>, secret: String): String {
    val value = parameters.filterKeys { it != "format" && it != "callback" && it != "api_sig" }
        .toSortedMap().entries.joinToString("") { it.key + it.value } + secret
    return MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

class LastFmClient @Inject constructor(private val http: ListeningHttp) : ListeningProviderClient {
    private suspend fun request(connection: ListeningConnection, method: String,
        parameters: Map<String, String> = emptyMap(), signed: Boolean = false): JSONObject {
        val params = mutableMapOf("method" to method, "api_key" to connection.apiKey).apply { putAll(parameters) }
        if (signed) params["api_sig"] = lastFmSignature(params, connection.apiSecret)
        params["format"] = "json"
        val form = FormBody.Builder().apply { params.forEach { (key, value) -> add(key, value) } }.build()
        val result = http.json(Request.Builder().url("https://ws.audioscrobbler.com/2.0/").post(form).build())
        if (result.has("error")) {
            val code = result.optInt("error")
            throw ListeningApiException(code, code in setOf(11, 16, 29), when (code) {
                14 -> "Approve StillShelf in your browser, then finish authorization."
                9, 4, 10, 13, 15, 26 -> "Last.fm authorization failed. Check your API credentials and authorize again."
                else -> "Last.fm could not complete this request (error $code)."
            })
        }
        return result
    }

    suspend fun authorizationToken(connection: ListeningConnection): String {
        require(connection.apiKey.isNotBlank() && connection.apiSecret.isNotBlank()) { "Enter your Last.fm API key and shared secret." }
        return request(connection, "auth.getToken", signed = true).getString("token")
    }

    suspend fun finishAuthorization(connection: ListeningConnection, requestToken: String): ListeningConnection {
        val session = request(connection, "auth.getSession", mapOf("token" to requestToken), signed = true).getJSONObject("session")
        return ListeningConnection(ListeningProvider.LAST_FM, username = session.getString("name"),
            token = session.getString("key"), apiKey = connection.apiKey, apiSecret = connection.apiSecret)
    }

    override suspend fun validate(connection: ListeningConnection): String {
        require(connection.apiKey.isNotBlank()) { "Enter your Last.fm API key." }
        if (connection.token.isNotBlank()) {
            require(connection.apiSecret.isNotBlank()) { "Enter your Last.fm API shared secret to verify this session." }
            return request(connection, "user.getInfo", mapOf("sk" to connection.token), signed = true)
                .getJSONObject("user").getString("name")
        }
        require(connection.username.isNotBlank()) { "Enter your Last.fm username for statistics." }
        return request(connection, "user.getInfo", mapOf("user" to connection.username))
            .getJSONObject("user").getString("name")
    }

    override suspend fun submit(connection: ListeningConnection, listen: TrackListen) {
        require(connection.canSubmit) { "Authorize Last.fm before enabling scrobbling." }
        val json = request(connection, "track.scrobble", mapOf("sk" to connection.token,
            "artist" to listen.artist, "track" to listen.title, "album" to listen.album,
            "duration" to listen.durationSeconds.toString(), "timestamp" to listen.startedAtSeconds.toString()), signed = true)
        val scrobbles = json.optJSONObject("scrobbles")
        if (scrobbles?.optJSONObject("@attr")?.optInt("accepted", 0) != 1) {
            throw ListeningApiException(400, false, "Last.fm did not accept this listen.")
        }
    }

    override suspend fun playingNow(connection: ListeningConnection, listen: TrackListen) {
        require(connection.canSubmit) { "Authorize Last.fm before enabling scrobbling." }
        request(connection, "track.updateNowPlaying", mapOf("sk" to connection.token, "artist" to listen.artist,
            "track" to listen.title, "album" to listen.album, "duration" to listen.durationSeconds.toString()), signed = true)
    }

    override suspend fun statistics(connection: ListeningConnection): ListeningStatistics {
        suspend fun get(method: String) = request(connection, method, mapOf("user" to connection.username, "limit" to "5", "period" to "overall"))
        val info = get("user.getInfo").getJSONObject("user")
        suspend fun chart(kind: String, item: String): List<ListeningEntry> = get("user.getTop$kind")
            .optJSONObject("top${kind.lowercase()}")?.optJSONArray(item).objects().map {
                ListeningEntry(it.stringValue("name"), it.optJSONObject("artist")?.stringValue("name").orEmpty(), it.longOrNull("playcount"),
                    artworkUrl = it.optJSONArray("image").objects().lastOrNull { image -> image.stringValue("#text").isNotBlank() }?.stringValue("#text"),
                    detailsUrl = it.stringValue("url").takeIf(String::isNotBlank))
            }
        val recent = get("user.getRecentTracks").optJSONObject("recenttracks")?.optJSONArray("track").objects()
            .filter { it.optJSONObject("@attr")?.optString("nowplaying") != "true" }.map {
                ListeningEntry(it.stringValue("name"), it.optJSONObject("artist")?.stringValue("#text").orEmpty(),
                    timestampSeconds = it.optJSONObject("date")?.longOrNull("uts"),
                    artworkUrl = it.optJSONArray("image").objects().lastOrNull { image -> image.stringValue("#text").isNotBlank() }?.stringValue("#text"),
                    detailsUrl = it.stringValue("url").takeIf(String::isNotBlank))
            }
        return ListeningStatistics("Last.fm · All time", info.longOrNull("playcount"),
            topArtists = chart("Artists", "artist"), topAlbums = chart("Albums", "album"),
            topTracks = chart("Tracks", "track"), recent = recent)
    }
}
