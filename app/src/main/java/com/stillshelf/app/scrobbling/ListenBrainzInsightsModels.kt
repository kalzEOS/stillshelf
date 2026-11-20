package com.stillshelf.app.scrobbling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONObject

enum class InsightPeriod(val api: String, val label: String) {
    THIS_WEEK("this_week", "This Week"), THIS_MONTH("this_month", "This Month"), THIS_YEAR("this_year", "This Year"),
    LAST_WEEK("week", "Last Week"), LAST_MONTH("month", "Last Month"), LAST_YEAR("year", "Last Year"), ALL("all_time", "All Time");

    fun anchor(today: LocalDate): LocalDate? = when (this) {
        THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        LAST_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        THIS_MONTH -> today.withDayOfMonth(1)
        LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1)
        THIS_YEAR -> today.withDayOfYear(1)
        LAST_YEAR -> today.withDayOfYear(1).minusYears(1)
        ALL -> null
    }
    val reuseMillis: Long get() = if (this in setOf(LAST_WEEK, LAST_MONTH, LAST_YEAR)) 6 * 60 * 60_000L else 60 * 60_000L
}

enum class InsightKind(val endpoint: String, val field: String, val title: String) {
    DAILY("daily-activity", "daily_activity", "Listening by day and hour"),
    EVOLUTION("artist-evolution-activity", "artist_evolution_activity", "Artist listening evolution"),
    ARTIST_ALBUMS("artist-activity", "artist_activity", "Albums by artist"),
    ALBUMS("releases", "releases", "Albums by period"), TRACKS("recordings", "recordings", "Tracks by period"),
    GENRES("genre-activity", "genre_activity", "Genre distribution"),
    ERAS("era-activity", "era_activity", "Music eras and release years"),
    ORIGINS("artist-map", "artist_map", "Artist origins")
}

/** A null/missing bucket stays unknown; only the provider's explicit zeros become zero cells. */
data class InsightRow(val label: String, val count: Long, val detail: String = "", val bucket: String = "", val index: Int? = null, val identity: String = "")
data class InsightDataset(val kind: InsightKind, val rows: List<InsightRow>, val from: Long? = null, val to: Long? = null, val updated: Long? = null)
data class InsightResult(val data: InsightDataset? = null, val loading: Boolean = false, val error: Boolean = false, val cached: Boolean = false)
data class InsightsUiState(val enabled: Boolean = false, val period: InsightPeriod = InsightPeriod.THIS_MONTH,
    val results: Map<InsightKind, InsightResult> = emptyMap(), val loading: Boolean = false)

internal fun insightJson(data: InsightDataset) = JSONObject().put("kind", data.kind.name)
    .put("from", data.from).put("to", data.to).put("updated", data.updated)
    .put("rows", JSONArray(data.rows.map { row -> JSONObject().put("label", row.label).put("count", row.count)
        .put("detail", row.detail).put("bucket", row.bucket).put("index", row.index).put("identity", row.identity) }))

internal fun insightFromJson(json: JSONObject): InsightDataset = InsightDataset(InsightKind.valueOf(json.getString("kind")),
    json.getJSONArray("rows").objects().map { InsightRow(it.getString("label"), it.getLong("count"),
        it.stringValue("detail"), it.stringValue("bucket"), it.longOrNull("index")?.toInt(), it.stringValue("identity")) },
    json.longOrNull("from"), json.longOrNull("to"), json.longOrNull("updated"))
