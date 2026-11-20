package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.model.NavidromeSession
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted, bounded snapshots; no provider credentials are used in cache identities. */
@Singleton
class ListeningStatisticsCache @Inject constructor(private val storage: SecureTokenStorage) {
    private val lock = Mutex()
    private var loaded = false
    private val records = linkedMapOf<String, JSONObject>()
    private suspend fun load() {
        if (loaded) return
        val saved = storage.getNamedSecret("listening_statistics_cache_v1")
        val json = try { saved?.let(::JSONObject) } catch (_: org.json.JSONException) { null }
        json?.keys()?.forEach { key -> json.optJSONObject(key)?.let { records[key] = it } }
        loaded = true
    }
    suspend fun get(key: String): ListeningStatistics? = withContext(Dispatchers.IO) { lock.withLock {
        load()
        records[key]?.optJSONObject("statistics")?.let(::decodeListeningStatistics)
    } }
    suspend fun put(key: String, statistics: ListeningStatistics) = withContext(Dispatchers.IO) { lock.withLock {
        load()
        val previous = records[key]?.optJSONObject("statistics")?.let(::decodeListeningStatistics)
        val merged = statistics.retainingUnavailable(previous)
        records.remove(key)
        records[key] = JSONObject().put("statistics", encodeListeningStatistics(merged))
        while (records.size > 6) records.remove(records.keys.first())
        val json = JSONObject().apply { records.forEach { (key, value) -> put(key, value) } }
        storage.saveNamedSecret("listening_statistics_cache_v1", json.toString(), true, false)
    } }
}

internal fun listeningStatisticsCacheKey(settings: ListeningSettings, session: NavidromeSession?): String? {
    val fields = mutableListOf(settings.statisticsSource.name)
    if (settings.statisticsSource == StatisticsSource.EXTERNAL) {
        val connection = settings.connection ?: return null
        fields += listOf(connection.provider.name, connection.revision, connection.username, connection.serverUrl.trimEnd('/'))
    } else if (settings.statisticsSource == StatisticsSource.NAVIDROME && session == null) return null
    // Local IDs and artwork are scoped to the active library server, even for external counts.
    fields += listOf(session?.serverId.orEmpty(), session?.username.orEmpty(), session?.baseUrl.orEmpty())
    val identity = fields.joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun encodeListeningStatistics(stats: ListeningStatistics): JSONObject {
    fun entries(rows: List<ListeningEntry>) = JSONArray(rows.map { row -> JSONObject()
        .put("title", row.title).put("subtitle", row.subtitle).put("plays", row.plays).put("time", row.timestampSeconds)
        .put("artwork", row.artworkUrl).put("url", row.detailsUrl).put("artistId", row.artistId).put("albumId", row.albumId).put("albumName", row.albumName)
        .put("artistMbid", row.musicBrainzArtistId).put("releaseMbid", row.musicBrainzReleaseId).put("recordingMbid", row.musicBrainzRecordingId) })
    return JSONObject().put("source", stats.sourceLabel).put("count", stats.totalScrobbles).put("seconds", stats.listeningSeconds)
        .put("artists", entries(stats.topArtists)).put("albums", entries(stats.topAlbums)).put("tracks", entries(stats.topTracks))
        .put("recent", entries(stats.recent)).put("note", stats.note).put("artistReason", stats.artistsUnavailableReason)
        .put("more", JSONArray(stats.moreCharts.map { it.name }))
        .put("unavailable", JSONArray(stats.unavailableSections.map { it.name })).put("timeNote", stats.listeningTimeNote)
}

private fun decodeListeningStatistics(json: JSONObject): ListeningStatistics {
    fun entries(key: String) = json.optJSONArray(key).objects().map { row -> ListeningEntry(
        row.stringValue("title"), row.stringValue("subtitle"), row.longOrNull("plays"), row.longOrNull("time"),
        row.stringValue("artwork").ifBlank { null }, row.stringValue("url").ifBlank { null }, row.stringValue("artistId").ifBlank { null },
        row.stringValue("albumId").ifBlank { null }, row.stringValue("albumName"), row.stringValue("artistMbid").ifBlank { null },
        row.stringValue("releaseMbid").ifBlank { null }, musicBrainzRecordingId = row.stringValue("recordingMbid").ifBlank { null }) }
    val more = json.optJSONArray("more") ?: JSONArray()
    return ListeningStatistics(json.stringValue("source"), json.longOrNull("count"), json.longOrNull("seconds"),
        entries("artists"), entries("albums"), entries("tracks"), entries("recent"), json.stringValue("note").ifBlank { null },
        json.stringValue("artistReason").ifBlank { null },
        (0 until more.length()).mapNotNull { runCatching { ListeningChart.valueOf(more.getString(it)) }.getOrNull() }.toSet(),
        json.optJSONArray("unavailable").let { missing -> (0 until (missing?.length() ?: 0)).mapNotNull {
            runCatching { ListeningMetric.valueOf(missing!!.getString(it)) }.getOrNull()
        }.toSet() }, json.stringValue("timeNote").ifBlank { null })
}
