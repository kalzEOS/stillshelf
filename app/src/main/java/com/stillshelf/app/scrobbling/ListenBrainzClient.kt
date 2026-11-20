package com.stillshelf.app.scrobbling

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ListenBrainzClient @Inject constructor(private val http: ListeningHttp) : ListeningProviderClient {
    private fun token(connection: ListeningConnection): String = connection.token.trim().also { value ->
        require(value.all { it.code in 0x21..0x7e }) { "The API token contains spaces or unsupported characters. Copy it again from the service settings." }
    }
    private suspend fun read(request: Request, service: String = "ListenBrainz"): JSONObject = withTimeoutOrNull(12_000) { http.json(request) }
        ?: throw ListeningConnectionException("$service did not respond in time. Try again later; your saved connection has not been removed.")
    internal fun base(connection: ListeningConnection): String = when (connection.provider) {
        ListeningProvider.KOITO, ListeningProvider.MALOJA -> connection.serverUrl.trimEnd('/') + "/apis/listenbrainz"
        else -> connection.serverUrl.ifBlank { "https://api.listenbrainz.org" }.trimEnd('/')
    }

    override suspend fun validate(connection: ListeningConnection): String {
        if (connection.token.isBlank() && connection.provider == ListeningProvider.LISTEN_BRAINZ && connection.username.isNotBlank()) {
            val userUrl = listeningUrl(base(connection), "1/user").newBuilder().addPathSegment(connection.username).addPathSegment("listen-count").build()
            read(Request.Builder().url(userUrl).build()).getJSONObject("payload").getLong("count")
            return connection.username
        }
        require(connection.token.isNotBlank()) { "Enter the service API token." }
        val json = read(Request.Builder().url(listeningUrl(base(connection), "1/validate-token"))
            .header("Authorization", "Token ${token(connection)}").build(), connection.provider.label)
        if (!json.optBoolean("valid")) throw ListeningApiException(401, false, "${connection.provider.label} rejected this API token. Check the token in your ${connection.provider.label} settings.")
        return json.stringValue("user_name").ifBlank { connection.username }
    }

    override suspend fun submit(connection: ListeningConnection, listen: TrackListen) {
        send(connection, listen, playingNow = false)
    }

    override suspend fun playingNow(connection: ListeningConnection, listen: TrackListen) {
        send(connection, listen, playingNow = true)
    }

    private suspend fun send(connection: ListeningConnection, listen: TrackListen, playingNow: Boolean) {
        require(connection.canSubmit) { "Enable direct scrobbling with valid service credentials first." }
        val metadata = JSONObject().put("artist_name", listen.artist).put("track_name", listen.title)
            .put("release_name", listen.album).put("additional_info", JSONObject()
                .put("duration_ms", listen.durationSeconds * 1000L).put("duration", listen.durationSeconds).put("submission_client", "StillShelf"))
        val item = JSONObject().put("track_metadata", metadata)
        if (!playingNow) item.put("listened_at", listen.startedAtSeconds)
        val json = JSONObject().put("listen_type", if (playingNow) "playing_now" else "single").put("payload", JSONArray().put(item))
        val response = http.json(Request.Builder().url(listeningUrl(base(connection), "1/submit-listens"))
            .header("Authorization", "Token ${token(connection)}")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build())
        if (response.has("error") || response.optString("status") != "ok") {
            throw ListeningApiException(400, false, "The listening service did not accept this listen.")
        }
    }

    override suspend fun statistics(connection: ListeningConnection): ListeningStatistics = coroutineScope {
        val username = connection.username.ifBlank { validate(connection) }
        require(username.isNotBlank()) { "Enter your ListenBrainz username." }
        // Encode the username as a path segment rather than accepting a URL path from user input.
        val user = okhttp3.HttpUrl.Builder().scheme("https").host("localhost").addPathSegment(username)
            .build().encodedPath.trim('/')
        suspend fun get(path: String, query: Map<String, String> = emptyMap()) = read(
            Request.Builder().url(listeningUrl(base(connection), "1/$path", query))
                .apply { if (connection.token.isNotBlank()) header("Authorization", "Token ${token(connection)}") }.build()
        )
        // Independent reads preserve valid metrics if another endpoint is unavailable. The HTTP
        // transport paces API request starts, while per-read timeouts avoid serial 35s stalls.
        val requests = listOf("user/$user/listen-count" to emptyMap(), "user/$user/listens" to mapOf("count" to "50")) +
            listOf("artists", "releases", "recordings").map { "stats/user/$user/$it" to mapOf("count" to "50", "range" to "all_time") }
        val countRead = async {
            try { Result.success(get(requests[0].first)) }
            catch (e: CancellationException) { throw e }
            catch (e: IOException) { Result.failure<JSONObject>(e) }
        }
        var historyLimit = 50
        val results = (listOf(countRead) + requests.drop(1).mapIndexed { index, (path, query) -> async {
            try {
                val knownCount = if (index == 0) countRead.await().getOrNull()?.optJSONObject("payload")?.longOrNull("count") else null
                // One bounded history response can supply an exact recorded-duration total for small accounts.
                // Large/unknown accounts keep the normal preview; never download unbounded histories.
                val fullHistory = index == 0 && connection.provider == ListeningProvider.LISTEN_BRAINZ && knownCount != null && knownCount < 1000
                val actualQuery = if (fullHistory) query + ("count" to "1000") else query
                if (index == 0) historyLimit = if (fullHistory) 1000 else 50
                Result.success(get(path, actualQuery))
            }
            catch (e: CancellationException) { throw e }
            catch (e: IOException) { Result.failure<JSONObject>(e) }
        } }).awaitAll()
        if (results.none { it.isSuccess }) throw requireNotNull(results.first().exceptionOrNull())
        fun payload(index: Int) = results[index].getOrNull()?.optJSONObject("payload")
        fun chart(index: Int, endpoint: String, title: String) = payload(index)?.optJSONArray(endpoint).objects().map {
            listenBrainzEntry(it, if (endpoint == "artists") "artist" else if (endpoint == "releases") "album" else "track", title)
        }
        val artists = chart(2, "artists", "artist_name")
        val albums = chart(3, "releases", "release_name")
        val tracks = chart(4, "recordings", "track_name")
        val history = payload(1)?.optJSONArray("listens").objects()
        val durationRows = history.map { recordedDuration(it.optJSONObject("track_metadata") ?: JSONObject()) }
        val fullHistory = payload(1)?.optJSONArray("listens") != null &&
            payload(0)?.longOrNull("count")?.let { it <= history.size && history.size < historyLimit } == true
        val duration = if (fullHistory && durationRows.all { it != null }) durationRows.sumOf { it!!.seconds } else null
        val missing = buildSet {
            if (payload(0)?.longOrNull("count") == null) add(ListeningMetric.COUNT)
            if (payload(1)?.optJSONArray("listens") == null) add(ListeningMetric.RECENT)
            listOf("artists", "releases", "recordings").forEachIndexed { index, field ->
                if (payload(index + 2)?.optJSONArray(field) == null) add(ListeningMetric.entries[index + 2])
            }
        }
        ListeningStatistics("ListenBrainz · All time", payload(0)?.longOrNull("count"), listeningSeconds = duration,
            topArtists = artists, topAlbums = albums, topTracks = tracks,
            recent = history.take(50).map {
                val metadata = it.optJSONObject("track_metadata") ?: JSONObject()
                listenBrainzEntry(metadata, "track", "track_name").copy(timestampSeconds = it.longOrNull("listened_at"))
            }, note = if (missing.isNotEmpty()) "Some ListenBrainz statistics could not be refreshed. Available and previously saved data is shown; try refreshing later." else null,
            moreCharts = ListeningChart.entries.filter { payload(it.ordinal + 2)?.optJSONArray(it.endpoint)?.length() == 50 }.toSet(),
            unavailableSections = missing,
            listeningTimeNote = if (duration == null) "A complete listening duration was not returned by ListenBrainz."
                else if (durationRows.any { it?.played == false }) "Based on recorded track lengths."
                else "Based on reported playback durations.")
    }

    override suspend fun chartPage(connection: ListeningConnection, chart: ListeningChart, offset: Int): ListeningChartPage {
        require(connection.username.isNotBlank()) { "Enter your ListenBrainz username." }
        val url = listeningUrl(base(connection), "1/stats/user").newBuilder().addPathSegment(connection.username)
            .addPathSegment(chart.endpoint).addQueryParameter("count", "50").addQueryParameter("offset", offset.toString())
            .addQueryParameter("range", "all_time").build()
        val request = Request.Builder().url(url).apply {
            if (connection.token.isNotBlank()) header("Authorization", "Token ${token(connection)}")
        }.build()
        val nodes = read(request).optJSONObject("payload")?.optJSONArray(chart.endpoint).objects()
        return ListeningChartPage(nodes.map { listenBrainzEntry(it, when(chart) {
            ListeningChart.ARTISTS -> "artist"; ListeningChart.ALBUMS -> "album"; ListeningChart.TRACKS -> "track"
        }, chart.titleField) }, nodes.size == 50)
    }
}
