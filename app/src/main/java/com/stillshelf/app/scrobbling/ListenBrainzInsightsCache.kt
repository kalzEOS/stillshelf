package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.datastore.SecureTokenStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class InsightCacheEntry(val checked: Long, val data: InsightDataset?, val failed: Boolean = false, val stale: Boolean = false)

/** Bounded encrypted snapshots. Keys contain account identity hashes, never authentication tokens. */
@Singleton
class ListenBrainzInsightsCache @Inject constructor(private val storage: SecureTokenStorage) {
    private val lock = Mutex()
    private var loaded = false
    private val records = linkedMapOf<String, JSONObject>()
    private suspend fun load() {
        if (loaded) return
        val json = storage.getNamedSecret(KEY)?.let { runCatching { JSONObject(it) }.getOrNull() }
        json?.keys()?.forEach { key -> json.optJSONObject(key)?.let { records[key] = it } }
        loaded = true
    }
    suspend fun get(key: String): Map<InsightKind, InsightCacheEntry> = withContext(Dispatchers.IO) { lock.withLock {
        load()
        val json = records[key] ?: return@withLock emptyMap()
        InsightKind.entries.mapNotNull { kind -> json.optJSONObject(kind.name)?.let { row ->
            runCatching { kind to InsightCacheEntry(row.getLong("checked"), row.optJSONObject("data")?.let(::insightFromJson),
                row.optBoolean("failed"), row.optBoolean("stale")) }.getOrNull()
        } }.toMap()
    } }
    suspend fun put(key: String, entries: Map<InsightKind, InsightCacheEntry>) = withContext(Dispatchers.IO) { lock.withLock {
        load()
        records.remove(key)
        records[key] = JSONObject().apply { entries.forEach { (kind, entry) -> put(kind.name, JSONObject()
            .put("checked", entry.checked).put("data", entry.data?.let(::insightJson)).put("failed", entry.failed).put("stale", entry.stale)) } }
        while (records.size > 12) records.remove(records.keys.first())
        storage.saveNamedSecret(KEY, JSONObject().apply { records.forEach { (key, value) -> put(key, value) } }.toString(), true, false)
    } }
    private companion object { const val KEY = "listening_insights_cache_v1" }
}
