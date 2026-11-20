package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray

/** Configuration is one encrypted record, so a failed save cannot publish half a connection. */
@Singleton
class ListeningSettingsStore @Inject constructor(private val secrets: SecureTokenStorage) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ListeningSettings())
    val state = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock { loadLocked() }

    private suspend fun loadLocked() {
        if (mutableState.value.loaded) return
        val json = secrets.getNamedSecret(KEY)?.let(::JSONObject)
        val legacy = json?.optJSONObject("connection")?.let(ListeningConnection::fromJson)
        val modern = json?.optInt("schema", 1) == 2
        val connections = if (modern) json?.optJSONArray("connections").objects().map(ListeningConnection::fromJson) else listOfNotNull(legacy)
        val direct = json?.optBoolean("enabled") == true && json.optString("method") == ScrobblingMethod.STILL_SHELF.name
        val senderId = if (modern) json?.optString("sendingConnectionId")?.ifBlank { null } else legacy?.takeIf { it.canSubmit }?.revision
        val sender = connections.firstOrNull { it.revision == senderId }
        mutableState.value = if (json == null) ListeningSettings(loaded = true, multipleConnections = true) else ListeningSettings(
            loaded = true,
            method = ScrobblingMethod.valueOf(json.getString("method")),
            scrobblingEnabled = direct && sender?.canSubmit == true,
            showStatistics = json.optBoolean("showStatistics"),
            statisticsSource = StatisticsSource.valueOf(json.getString("statisticsSource")),
            connection = if (modern) connections.firstOrNull { it.revision == json.optString("statisticsConnectionId") } else legacy,
            connections = connections, sendingConnectionId = senderId, multipleConnections = true,
            localRecordingEnabled = json.optBoolean("localRecordingEnabled", false),
            previousStatisticsSource = runCatching { StatisticsSource.valueOf(json.optString("previousStatisticsSource")) }.getOrDefault(StatisticsSource.NAVIDROME)
        )
    }

    suspend fun update(transform: (ListeningSettings) -> ListeningSettings) = mutex.withLock {
        loadLocked()
        val previous = mutableState.value
        var updated = transform(previous).copy(loaded = true)
        if (updated.statisticsSource == StatisticsSource.LOCAL && previous.statisticsSource != StatisticsSource.LOCAL)
            updated = updated.copy(previousStatisticsSource = previous.statisticsSource)
        if (!updated.localRecordingEnabled && updated.statisticsSource == StatisticsSource.LOCAL)
            updated = updated.copy(statisticsSource = updated.previousStatisticsSource.takeIf {
                it != StatisticsSource.LOCAL && (it != StatisticsSource.EXTERNAL || updated.connection != null)
            } ?: StatisticsSource.NAVIDROME)
        val json = JSONObject().put("schema", 2).put("method", updated.method.name)
            .put("enabled", updated.scrobblingEnabled).put("showStatistics", updated.showStatistics)
            .put("statisticsSource", updated.statisticsSource.name)
            .put("localRecordingEnabled", updated.localRecordingEnabled)
            .put("previousStatisticsSource", updated.previousStatisticsSource.name)
            .put("connections", JSONArray(updated.savedConnections.map { it.toJson() }))
            .put("statisticsConnectionId", updated.connection?.revision.orEmpty())
            .put("sendingConnectionId", updated.sendingConnection?.revision.orEmpty())
        secrets.saveNamedSecret(KEY, json.toString(), persistAcrossRestarts = true, allowInsecureStorage = false)
        mutableState.value = updated
    }

    suspend fun removeConnection(id: String? = state.value.connection?.revision) = update { settings ->
        if (id == null) settings else settings.withoutConnection(id)
    }

    private companion object { const val KEY = "listening_configuration_v1" }
}
