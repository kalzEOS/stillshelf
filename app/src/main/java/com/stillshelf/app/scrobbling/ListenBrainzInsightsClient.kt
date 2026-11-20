package com.stillshelf.app.scrobbling

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONObject

class ListenBrainzInsightsClient @Inject constructor(private val http: ListeningHttp) {
    private val requests = Semaphore(2)

    suspend fun fetch(connection: ListeningConnection, period: InsightPeriod, kind: InsightKind): InsightDataset? = requests.withPermit {
        require(connection.provider == ListeningProvider.LISTEN_BRAINZ && connection.username.isNotBlank()) { "Select a saved ListenBrainz statistics connection." }
        val url = listeningUrl(connection.serverUrl.ifBlank { "https://api.listenbrainz.org" }, "1/stats/user").newBuilder()
            .addPathSegment(connection.username).addPathSegment(kind.endpoint).addQueryParameter("range", period.api)
            .apply { if (kind in setOf(InsightKind.ALBUMS, InsightKind.TRACKS)) addQueryParameter("count", "10") }.build()
        val request = Request.Builder().url(url).apply {
            if (connection.token.isNotBlank()) header("Authorization", "Token ${connection.token.trim()}")
        }.build()
        val body = try { withTimeoutOrNull(15_000) { http.text(request).first }
            ?: throw ListeningConnectionException("ListenBrainz did not respond in time.") }
        catch (e: ListeningApiException) { if (e.status == 404) return@withPermit null else throw e }
        if (body.isBlank()) return@withPermit null // HTTP 204: not calculated, not a transport failure.
        withContext(Dispatchers.IO) { parseInsight(kind, JSONObject(body), period) }
    }
}
