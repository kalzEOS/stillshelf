package com.stillshelf.app.downloads.navidrome

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

enum class NavidromeDownloadStatus {
    Queued,
    Downloading,
    Completed,
    Failed
}

data class NavidromeDownloadItem(
    val serverId: String,
    val libraryId: String,
    val trackId: String,
    val albumId: String?,
    val albumSongCount: Int?,
    val artistId: String?,
    val title: String,
    val artistName: String,
    val albumName: String,
    val coverUrl: String?,
    val durationSeconds: Int?,
    val formatLabel: String?,
    val status: NavidromeDownloadStatus,
    val progressPercent: Int,
    val downloadId: Long? = null,
    val localPath: String? = null,
    val fileSizeBytes: Long? = null,
    val errorMessage: String? = null,
    val isPlaybackCache: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedAtMs: Long = updatedAtMs
)

@Singleton
class NavidromeDownloadStorage @Inject constructor(
    @ApplicationContext appContext: Context
) {
    private companion object {
        const val PREF_NAME = "stillshelf_navidrome_downloads"
        const val PREF_KEY_ITEMS = "items"
    }

    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadItems(): List<NavidromeDownloadItem> {
        val raw = prefs.getString(PREF_KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.optJSONObject(index) ?: continue
                    val trackId = node.optString("trackId").trim()
                    if (trackId.isBlank()) continue
                    add(
                        NavidromeDownloadItem(
                            serverId = node.optString("serverId").trim(),
                            libraryId = node.optString("libraryId").trim(),
                            trackId = trackId,
                            albumId = node.optString("albumId").ifBlank { null },
                            albumSongCount = node.takeIf { it.has("albumSongCount") }?.optInt("albumSongCount")
                                ?.takeIf { it > 0 },
                            artistId = node.optString("artistId").ifBlank { null },
                            title = node.optString("title").normalizeNavidromeText(),
                            artistName = node.optString("artistName").normalizeNavidromeText(),
                            albumName = node.optString("albumName").normalizeNavidromeText(),
                            coverUrl = node.optString("coverUrl").ifBlank { null },
                            durationSeconds = node.takeIf { it.has("durationSeconds") }?.optInt("durationSeconds")
                                ?.takeIf { it >= 0 },
                            formatLabel = node.optString("formatLabel").ifBlank { null },
                            status = runCatching {
                                NavidromeDownloadStatus.valueOf(node.optString("status"))
                            }.getOrDefault(NavidromeDownloadStatus.Queued),
                            progressPercent = node.optInt("progressPercent", 0).coerceIn(0, 100),
                            downloadId = node.optLong("downloadId").takeIf { it > 0L },
                            localPath = node.optString("localPath").ifBlank { null },
                            fileSizeBytes = node.optLong("fileSizeBytes").takeIf { it > 0L },
                            errorMessage = node.optString("errorMessage").ifBlank { null },
                            isPlaybackCache = node.optBoolean("isPlaybackCache", false),
                            updatedAtMs = node.optLong("updatedAtMs", System.currentTimeMillis()),
                            lastAccessedAtMs = node.optLong("lastAccessedAtMs", node.optLong("updatedAtMs", System.currentTimeMillis()))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun persistItems(items: List<NavidromeDownloadItem>) {
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("serverId", item.serverId)
                        .put("libraryId", item.libraryId)
                        .put("trackId", item.trackId)
                        .put("albumId", item.albumId)
                        .put("albumSongCount", item.albumSongCount)
                        .put("artistId", item.artistId)
                        .put("title", item.title)
                        .put("artistName", item.artistName)
                        .put("albumName", item.albumName)
                        .put("coverUrl", item.coverUrl)
                        .put("durationSeconds", item.durationSeconds)
                        .put("formatLabel", item.formatLabel)
                        .put("status", item.status.name)
                        .put("progressPercent", item.progressPercent)
                        .put("downloadId", item.downloadId)
                        .put("localPath", item.localPath)
                        .put("fileSizeBytes", item.fileSizeBytes)
                        .put("errorMessage", item.errorMessage)
                        .put("isPlaybackCache", item.isPlaybackCache)
                        .put("updatedAtMs", item.updatedAtMs)
                        .put("lastAccessedAtMs", item.lastAccessedAtMs)
                )
            }
        }.toString()
        prefs.edit().putString(PREF_KEY_ITEMS, payload).apply()
    }

    private fun String.normalizeNavidromeText(): String {
        return trim()
            .replace("Â’", "'")
            .replace("Â'", "'")
            .replace("â€™", "'")
            .replace("â€˜", "'")
            .replace("â€œ", "\"")
            .replace("â€�", "\"")
            .replace("Â\"", "\"")
            .replace('\u0091', '\'')
            .replace('\u0092', '\'')
            .replace('\u0093', '"')
            .replace('\u0094', '"')
            .replace(Regex("(?<=[\\p{L}\\p{N}])\uFFFD(?=[\\p{L}\\p{N}])"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
