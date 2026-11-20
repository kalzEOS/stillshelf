package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.api.NavidromeAuth
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.data.repo.NavidromeRepository
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class NavidromeListeningClient @Inject constructor(
    private val http: ListeningHttp,
    private val repository: NavidromeRepository,
    private val api: NavidromeApi
) {
    suspend fun statistics(): ListeningStatistics {
        val auth = repository.currentAuth() ?: throw IOException("Connect to Navidrome to view its listening history.")
        val password = auth.encPassword.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
        val login = http.json(Request.Builder().url(listeningUrl(auth.baseUrl, "auth/login"))
            .post(JSONObject().put("username", auth.username).put("password", password).toString()
                .toRequestBody("application/json".toMediaType())).build())
        val token = login.getString("token")
        fun request(path: String, query: Map<String, String> = emptyMap()) = Request.Builder()
            .url(listeningUrl(auth.baseUrl, path, query)).header("X-ND-Authorization", "Bearer $token").build()
        fun artwork(node: JSONObject, kind: String): String? {
            if (node.optBoolean("imageAbsent")) return null
            if (kind == "artist") node.stringValue("largeImageUrl").takeIf { it.isNotBlank() }?.let { return it }
            val prefix = when (kind) { "artist" -> "ar"; "album" -> "al"; else -> "mf" }
            return node.stringValue("id").takeIf { it.isNotBlank() }?.let { api.coverArtUrl(auth, "$prefix-$it", 200) }
        }
        val (body, total) = http.text(request("api/scrobble", mapOf("_start" to "0", "_end" to "5", "_sort" to "submission_time", "_order" to "DESC")))
        val records = try { JSONArray(body).objects() } catch (_: Exception) {
            throw IOException("This Navidrome version does not expose native listening history. Try an external statistics source.")
        }
        val recent = records.map { record ->
            val id = record.getString("mediaFileId")
            val pathId = okhttp3.HttpUrl.Builder().scheme("https").host("localhost").addPathSegment(id).build().encodedPath.trim('/')
            val song = try { http.json(request("api/song/$pathId")) } catch (e: ListeningApiException) {
                if (e.status != 404) throw e
                return@map ListeningEntry("Track no longer available", timestampSeconds = record.longOrNull("submissionTime"))
            }
            ListeningEntry(song.stringValue("title"), song.stringValue("artist"), timestampSeconds = record.longOrNull("submissionTime"),
                artworkUrl = artwork(song, "song"), albumId = song.stringValue("albumId").takeIf { it.isNotBlank() })
        }
        suspend fun chart(path: String, title: String) = JSONArray(http.text(request("api/$path", mapOf(
            "_start" to "0", "_end" to "20", "_sort" to "play_count", "_order" to "DESC"
        ))).first).objects().filter { (it.longOrNull("playCount") ?: 0) > 0 }.map {
            ListeningEntry(it.stringValue(title), it.stringValue("artist"), it.longOrNull("playCount"), artworkUrl = artwork(it, path),
                artistId = if (path == "artist") it.stringValue("id").takeIf(String::isNotBlank) else null,
                albumId = (if (path == "album") it.stringValue("id") else it.stringValue("albumId")).takeIf(String::isNotBlank))
        }
        val artists = try { chart("artist", "name") } catch (e: ListeningApiException) {
            if (e.status !in setOf(400, 404)) throw e
            emptyList()
        }
        return ListeningStatistics("Navidrome · Recorded history", total?.toLongOrNull(),
            topArtists = artists, topAlbums = chart("album", "name"), topTracks = chart("song", "title"), recent = recent,
            note = "Scrobbles count the server's recorded history. Charts use server library play counts, which may include earlier plays. Total listening duration is not provided.",
            artistsUnavailableReason = if (artists.isEmpty()) "This server has not provided artist play counts. An external statistics service may provide richer artist charts." else null)
    }

    /** Standard playback reporting is sent only to Navidrome, never to external credentials. */
    suspend fun report(auth: NavidromeAuth, listen: TrackListen, submission: Boolean) {
        val json = http.json(Request.Builder().url(listeningUrl(auth.baseUrl, "rest/scrobble.view", mapOf(
            "u" to auth.username, "p" to "enc:${auth.encPassword}", "v" to "1.16.1", "c" to "StillShelf",
            "f" to "json", "id" to listen.id, "time" to (listen.startedAtSeconds * 1000).toString(),
            "submission" to submission.toString()
        ))).build()).optJSONObject("subsonic-response")
        if (json?.optString("status") != "ok") throw IOException("Navidrome could not record this listen.")
    }
}
