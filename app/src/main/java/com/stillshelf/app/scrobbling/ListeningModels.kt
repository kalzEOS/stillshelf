package com.stillshelf.app.scrobbling

import org.json.JSONObject
import java.util.UUID

enum class ScrobblingMethod { STILL_SHELF, NAVIDROME }
enum class ScrobblingSelection { OFF, NAVIDROME, DIRECT }
enum class StatisticsSource { EXTERNAL, NAVIDROME }
enum class ListeningProvider(val label: String) {
    KOITO("Koito"), MALOJA("Maloja"), LAST_FM("Last.fm"), LISTEN_BRAINZ("ListenBrainz")
}

/** Secrets deliberately have no generated toString(). One connection can serve both operations. */
class ListeningConnection(
    val provider: ListeningProvider,
    val serverUrl: String = "",
    val username: String = "",
    val token: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val revision: String = UUID.randomUUID().toString(),
    val allowSubmissions: Boolean = true
) {
    val hasSubmissionCredentials: Boolean get() = when (provider) {
        ListeningProvider.LAST_FM -> token.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()
        else -> token.isNotBlank()
    }
    val canSubmit: Boolean get() = allowSubmissions && hasSubmissionCredentials
    override fun toString() = "ListeningConnection(${provider.name}, credentials=REDACTED)"
    fun withSubmissionPermission(allowed: Boolean) = if (allowed == allowSubmissions) this else
        ListeningConnection(provider, serverUrl, username, token, apiKey, apiSecret, revision, allowed)

    internal fun toJson() = JSONObject().put("provider", provider.name)
        .put("serverUrl", serverUrl).put("username", username).put("token", token)
        .put("apiKey", apiKey).put("apiSecret", apiSecret).put("revision", revision).put("allowSubmissions", allowSubmissions)

    companion object {
        internal fun fromJson(json: JSONObject) = ListeningConnection(
            ListeningProvider.valueOf(json.getString("provider")), json.optString("serverUrl"),
            json.optString("username"), json.optString("token"), json.optString("apiKey"),
            json.optString("apiSecret"), json.getString("revision"), json.optBoolean("allowSubmissions", true)
        )
    }
}

data class ListeningSettings(
    val loaded: Boolean = false,
    val method: ScrobblingMethod = ScrobblingMethod.STILL_SHELF,
    val scrobblingEnabled: Boolean = false,
    val showStatistics: Boolean = false,
    val statisticsSource: StatisticsSource = StatisticsSource.NAVIDROME,
    val connection: ListeningConnection? = null,
    val connections: List<ListeningConnection> = emptyList(),
    val sendingConnectionId: String? = null,
    val multipleConnections: Boolean = false
) {
    // connection remains the selected statistics connection for existing statistics/cache consumers.
    val savedConnections: List<ListeningConnection> get() = (connections + listOfNotNull(connection)).distinctBy { it.revision }
    val sendingConnection: ListeningConnection? get() = if (multipleConnections)
        savedConnections.firstOrNull { it.revision == sendingConnectionId } else connection
    val selection: ScrobblingSelection get() = when {
        method == ScrobblingMethod.NAVIDROME -> ScrobblingSelection.NAVIDROME
        scrobblingEnabled && sendingConnection?.allowSubmissions != false -> ScrobblingSelection.DIRECT
        else -> ScrobblingSelection.OFF
    }
    fun withSelection(selection: ScrobblingSelection) = copy(
        method = if (selection == ScrobblingSelection.NAVIDROME) ScrobblingMethod.NAVIDROME else ScrobblingMethod.STILL_SHELF,
        scrobblingEnabled = selection == ScrobblingSelection.DIRECT,
        connection = if (selection == ScrobblingSelection.DIRECT) connection?.withSubmissionPermission(true) else connection
    )
    val canSubmit: Boolean get() = loaded && method == ScrobblingMethod.STILL_SHELF &&
        scrobblingEnabled && sendingConnection?.canSubmit == true
}

data class ListeningEntry(
    val title: String,
    val subtitle: String = "",
    val plays: Long? = null,
    val timestampSeconds: Long? = null,
    val artworkUrl: String? = null,
    val detailsUrl: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String = "",
    val musicBrainzArtistId: String? = null,
    val musicBrainzReleaseId: String? = null,
    val albumChoices: List<com.stillshelf.app.core.model.NavidromeAlbum> = emptyList(),
    val musicBrainzRecordingId: String? = null,
    val albumChoiceFormats: Map<String, String> = emptyMap()
) {
    override fun toString() = "ListeningEntry(title=$title, plays=$plays)"
}

data class ListeningStatistics(
    val sourceLabel: String,
    val totalScrobbles: Long? = null,
    val listeningSeconds: Long? = null,
    val topArtists: List<ListeningEntry> = emptyList(),
    val topAlbums: List<ListeningEntry> = emptyList(),
    val topTracks: List<ListeningEntry> = emptyList(),
    val recent: List<ListeningEntry> = emptyList(),
    val note: String? = null,
    val artistsUnavailableReason: String? = null,
    val moreCharts: Set<ListeningChart> = emptySet(),
    val unavailableSections: Set<ListeningMetric> = emptySet(),
    val listeningTimeNote: String? = null
)

enum class ListeningMetric { COUNT, RECENT, ARTISTS, ALBUMS, TRACKS }

enum class ListeningChart(val endpoint: String, val titleField: String) {
    ARTISTS("artists", "artist_name"), ALBUMS("releases", "release_name"), TRACKS("recordings", "track_name")
}
data class ListeningChartPage(val entries: List<ListeningEntry>, val hasMore: Boolean)

data class TrackListen(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val startedAtSeconds: Long
)

internal fun scrobbleThresholdMs(durationSeconds: Int, provider: ListeningProvider): Long? {
    if (durationSeconds <= 0 || (provider == ListeningProvider.LAST_FM && durationSeconds <= 30)) return null
    return minOf(durationSeconds * 500L, 240_000L)
}
