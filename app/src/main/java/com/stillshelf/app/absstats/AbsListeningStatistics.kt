package com.stillshelf.app.absstats

import java.time.LocalDate
import org.json.JSONObject
import org.json.JSONArray

data class AbsListeningItem(val id: String, val title: String, val author: String, val seconds: Double)
data class AbsListeningSession(val id: String, val itemId: String, val episodeId: String?, val title: String,
    val author: String, val seconds: Double, val startedAt: Long?)
data class AbsYearSummary(val year: Int, val booksFinished: Long?, val bookSeconds: Double?,
    val podcastSeconds: Double?, val authors: List<Pair<String, Double>>, val genres: List<Pair<String, Double>>,
    val narrator: Pair<String, Double>?)
data class AbsListeningStatistics(val seconds: Double?, val todaySeconds: Double?, val days: Map<LocalDate, Double>,
    val items: List<AbsListeningItem>, val recent: List<AbsListeningSession>, val daysAvailable: Boolean = true)
data class AbsHistoryPage(val sessions: List<AbsListeningSession>, val total: Int, val hasMore: Boolean, val cached: Boolean = false)

internal fun JSONObject.nonnegative(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0 }
internal fun JSONObject.optionalText(name: String): String? = (opt(name) as? String)?.takeIf { it.isNotBlank() }

internal fun parseAbsStatistics(json: JSONObject): AbsListeningStatistics {
    require(json.has("totalTime") || json.has("days") || json.has("items")) { "Unsupported statistics response" }
    val days = json.optJSONObject("days")?.let { dates -> dates.keys().asSequence().mapNotNull { key ->
        val date = runCatching { LocalDate.parse(key) }.getOrNull()
        val seconds = dates.nonnegative(key)
        if (date != null && seconds != null) date to seconds else null
    }.toMap() }.orEmpty()
    val items = json.optJSONObject("items")?.let { rows -> rows.keys().asSequence().mapNotNull { id ->
        val row = rows.optJSONObject(id) ?: return@mapNotNull null
        val metadata = row.optJSONObject("mediaMetadata") ?: JSONObject()
        val time = row.nonnegative("timeListening") ?: return@mapNotNull null
        val authors = metadata.optJSONArray("authors")?.let { names -> (0 until names.length()).mapNotNull { index ->
            names.optJSONObject(index)?.optionalText("name") ?: (names.opt(index) as? String)?.takeIf { it.isNotBlank() }
        }.joinToString(", ").takeIf { it.isNotBlank() } }
        AbsListeningItem(id, metadata.optionalText("title") ?: "Unavailable title",
            metadata.optionalText("authorName") ?: metadata.optionalText("author") ?: authors ?: "", time)
    }.sortedByDescending { it.seconds }.toList() }.orEmpty()
    return AbsListeningStatistics(json.nonnegative("totalTime"), json.nonnegative("today"), days, items.filter { it.seconds > 0 },
        parseAbsSessions(json.optJSONArray("recentSessions")), json.optJSONObject("days") != null)
}

internal fun parseAbsSessions(rows: JSONArray?): List<AbsListeningSession> = rows?.let { (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optionalText("id") ?: return@mapNotNull null
        AbsListeningSession(id, row.optionalText("libraryItemId").orEmpty(), row.optionalText("episodeId"),
            row.optionalText("displayTitle") ?: "Unavailable title", row.optionalText("displayAuthor").orEmpty(),
            row.nonnegative("timeListening") ?: return@mapNotNull null, row.nonnegative("startedAt")?.toLong())
    } }.orEmpty()

internal fun parseAbsHistory(json: JSONObject): AbsHistoryPage {
    require(json.optJSONArray("sessions") != null && json.nonnegative("total") != null)
    val page = json.nonnegative("page")?.toInt() ?: error("Missing page")
    val size = json.nonnegative("itemsPerPage")?.toInt()?.takeIf { it > 0 } ?: error("Missing page size")
    val total = json.nonnegative("total")!!.toInt()
    return AbsHistoryPage(parseAbsSessions(json.optJSONArray("sessions")), total, (page.toLong() + 1) * size < total)
}

internal fun parseAbsYear(json: JSONObject, year: Int): AbsYearSummary {
    require(json.has("numBooksFinished") || json.has("totalListeningTime"))
    fun ranks(name: String, label: String) = json.optJSONArray(name)?.let { rows ->
        (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { row ->
            val text = row.optionalText(label); val seconds = row.nonnegative("time")
            if (text != null && seconds != null && seconds > 0) text to seconds else null
        } }
    }.orEmpty()
    val narrator = json.optJSONObject("mostListenedNarrator")?.let { row ->
        val text = row.optionalText("name"); val seconds = row.nonnegative("time")
        if (text != null && seconds != null && seconds > 0) text to seconds else null
    }
    return AbsYearSummary(year, json.nonnegative("numBooksFinished")?.toLong(), json.nonnegative("totalBookListeningTime"),
        json.nonnegative("totalPodcastListeningTime"), ranks("topAuthors", "name"), ranks("topGenres", "genre"), narrator)
}

internal fun absListeningTime(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds < 0) return "Not available"
    val minutes = (seconds / 60).toLong()
    return when { minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"; minutes > 0 -> "$minutes min"; seconds > 0 -> "${seconds.toLong()} sec"; else -> "0 min" }
}
