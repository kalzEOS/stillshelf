package com.stillshelf.app.downloads.storage

import android.content.Context
import com.stillshelf.app.downloads.manager.DownloadItem
import com.stillshelf.app.downloads.manager.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext appContext: Context
) {
    companion object {
        private const val PREF_NAME = "stillshelf_downloads"
        private const val PREF_KEY_ITEMS = "items"
    }

    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadItems(): List<DownloadItem> {
        val raw = prefs.getString(PREF_KEY_ITEMS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.optJSONObject(index) ?: continue
                    val bookId = node.optString("bookId").trim()
                    if (bookId.isBlank()) continue
                    add(
                        DownloadItem(
                            serverId = node.optString("serverId"),
                            libraryId = node.optString("libraryId"),
                            bookId = bookId,
                            title = node.optString("title"),
                            authorName = node.optString("authorName"),
                            coverUrl = node.optString("coverUrl").ifBlank { null },
                            durationSeconds = node.optDouble("durationSeconds").takeIf { !it.isNaN() },
                            status = runCatching {
                                DownloadStatus.valueOf(node.optString("status"))
                            }.getOrDefault(DownloadStatus.Queued),
                            progressPercent = node.optInt("progressPercent", 0).coerceIn(0, 100),
                            downloadId = node.optLong("downloadId").takeIf { it > 0L },
                            localPath = node.optString("localPath").ifBlank { null },
                            errorMessage = node.optString("errorMessage").ifBlank { null },
                            updatedAtMs = node.optLong("updatedAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun persistItems(items: List<DownloadItem>) {
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("serverId", item.serverId)
                        .put("libraryId", item.libraryId)
                        .put("bookId", item.bookId)
                        .put("title", item.title)
                        .put("authorName", item.authorName)
                        .put("coverUrl", item.coverUrl)
                        .put("durationSeconds", item.durationSeconds)
                        .put("status", item.status.name)
                        .put("progressPercent", item.progressPercent)
                        .put("downloadId", item.downloadId)
                        .put("localPath", item.localPath)
                        .put("errorMessage", item.errorMessage)
                        .put("updatedAtMs", item.updatedAtMs)
                )
            }
        }.toString()
        prefs.edit().putString(PREF_KEY_ITEMS, payload).apply()
    }
}
