package com.stillshelf.app.absstats

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Keep the fields used by charts, not repeated chapter lists, descriptions or device/IP details. */
internal fun absStatisticsSession(row: JSONObject): JSONObject {
    val result = JSONObject()
    listOf("id", "libraryId", "libraryItemId", "episodeId", "mediaType", "displayTitle", "displayAuthor",
        "timeListening", "startedAt", "date").forEach { field ->
        row.opt(field)?.takeUnless { it == JSONObject.NULL }?.let { result.put(field, it) }
    }
    row.optJSONObject("mediaMetadata")?.let { metadata ->
        val saved = JSONObject()
        listOf("title", "authorName", "author").forEach { field -> metadata.optionalText(field)?.let { saved.put(field, it) } }
        listOf("authors", "genres", "narrators").forEach { field ->
            metadata.optJSONArray(field)?.let { array ->
                val names = (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.optionalText("name") ?: (array.opt(index) as? String)?.takeIf { it.isNotBlank() }
                }
                saved.put(field, JSONArray(names))
            }
        }
        result.put("mediaMetadata", saved)
    }
    return result
}

/** Derived only from a complete set of this account's native sessions; never from resume progress. */
internal fun absLibraryStatistics(sessions: JSONArray, libraryId: String?, year: Int): Pair<AbsListeningStatistics, AbsYearSummary> {
    val rows = (0 until sessions.length()).mapNotNull { sessions.optJSONObject(it) }
        .filter { (libraryId == null || it.optionalText("libraryId") == libraryId) && it.nonnegative("timeListening") != null }
    val days = mutableMapOf<LocalDate, Double>()
    val items = JSONObject()
    var total = 0.0
    var bookSeconds = 0.0
    var podcastSeconds = 0.0
    val authors = mutableMapOf<String, Double>()
    val genres = mutableMapOf<String, Double>()
    val narrators = mutableMapOf<String, Double>()
    fun labels(metadata: JSONObject?, field: String): List<String> = metadata?.optJSONArray(field)?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.optionalText("name")
            ?: (array.opt(index) as? String)?.takeIf { it.isNotBlank() } }.distinct()
    }.orEmpty()
    rows.forEach { row ->
        val seconds = row.nonnegative("timeListening")!!
        total += seconds
        val date = row.optionalText("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date != null) days[date] = (days[date] ?: 0.0) + seconds
        val itemId = row.optionalText("libraryItemId")
        if (itemId != null) {
            val item = items.optJSONObject(itemId) ?: JSONObject().put("mediaMetadata", row.optJSONObject("mediaMetadata")
                ?: JSONObject().put("title", row.optionalText("displayTitle")))
            item.put("timeListening", (item.nonnegative("timeListening") ?: 0.0) + seconds)
            items.put(itemId, item)
        }
        if (date?.year == year) {
            if (row.optionalText("mediaType") == "podcast" || row.optionalText("episodeId") != null) podcastSeconds += seconds
            else bookSeconds += seconds
            val metadata = row.optJSONObject("mediaMetadata")
            val names = labels(metadata, "authors").ifEmpty { listOfNotNull(metadata?.optionalText("authorName")
                ?: metadata?.optionalText("author") ?: row.optionalText("displayAuthor")) }
            names.forEach { authors[it] = (authors[it] ?: 0.0) + seconds }
            labels(metadata, "genres").forEach { genres[it] = (genres[it] ?: 0.0) + seconds }
            labels(metadata, "narrators").forEach { narrators[it] = (narrators[it] ?: 0.0) + seconds }
        }
    }
    val json = JSONObject().put("totalTime", total).put("items", items).put("days", JSONObject(days.mapKeys { it.key.toString() }))
        .put("recentSessions", JSONArray(rows.take(30)))
    // The server dates follow its timezone. Device-local 'today' is not substituted here.
    val stats = parseAbsStatistics(json)
    fun Map<String, Double>.ranked() = entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(10).map { it.key to it.value }
    val annual = AbsYearSummary(year, null, bookSeconds, podcastSeconds, authors.ranked(), genres.ranked(), narrators.ranked().firstOrNull())
    return stats to annual
}
