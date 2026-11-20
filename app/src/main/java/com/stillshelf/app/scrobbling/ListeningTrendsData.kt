package com.stillshelf.app.scrobbling

import java.time.*
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import okhttp3.Request
import org.json.JSONObject

enum class TrendRange(val label: String) {
    WEEK("Last 7 Days"), MONTH("Last 30 Days"), QUARTER("Last 3 Months"), YEAR("Last 12 Months"), ALL("All Time");
    fun start(today: LocalDate): LocalDate? = when(this) {
        WEEK -> today.minusDays(6); MONTH -> today.minusDays(29); QUARTER -> today.minusMonths(3).plusDays(1)
        YEAR -> today.minusYears(1).plusDays(1); ALL -> null
    }
}
data class TrendPoint(val date: LocalDate, val count: Long, val seconds: Long? = null, val end: LocalDate = date)
internal fun trendDurationUnit(points: List<TrendPoint>): Pair<Double, String> = when {
    (points.maxOfOrNull { it.seconds ?: 0 } ?: 0) >= 3600 -> 3600.0 to "h"
    (points.maxOfOrNull { it.seconds ?: 0 } ?: 0) >= 60 -> 60.0 to "min"
    else -> 1.0 to "s"
}
data class ListeningTrends(val points: List<TrendPoint>, val daily: Boolean, val complete: Boolean,
    val note: String, val streak: Int? = null, val hour: Int? = null, val heatmap: List<TrendPoint> = emptyList(), val durationNote: String? = null)

internal fun trendBuckets(points: List<TrendPoint>, range: TrendRange): List<TrendPoint> =
    points.groupBy { when(range) { TrendRange.QUARTER -> it.date.minusDays((it.date.dayOfWeek.value - 1).toLong())
        TrendRange.YEAR, TrendRange.ALL -> it.date.withDayOfMonth(1); else -> it.date } }.toSortedMap().map { (date, rows) ->
        TrendPoint(rows.minOf { it.date }, rows.sumOf { it.count }, if (rows.all { it.seconds != null }) rows.sumOf { it.seconds!! } else null, rows.maxOf { it.end })
    }

class ListeningTrendsClient @Inject constructor(private val http: ListeningHttp) {
    suspend fun fetch(connection: ListeningConnection?, range: TrendRange, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): ListeningTrends {
        if (connection == null) return ListeningTrends(emptyList(), false, false, "This integration does not expose complete timestamped history for Trends. Choose a supported external statistics source in Settings.")
        suspend fun get(path: String, query: Map<String, String>) = http.json(Request.Builder().url(listeningUrl(
            if (connection.provider == ListeningProvider.LISTEN_BRAINZ) connection.serverUrl.ifBlank { "https://api.listenbrainz.org" } else connection.serverUrl, path, query))
            .apply { if (connection.token.isNotBlank()) header("Authorization", "Token ${connection.token.trim()}") }.build())
        return when(connection.provider) {
            ListeningProvider.KOITO -> {
                val start = range.start(today) ?: OffsetDateTime.parse(get("apis/web/v1/first-activity", emptyMap()).getString("time")).toLocalDate()
                val fetchStart = minOf(start, today.minusYears(1).plusDays(1))
                val days = ChronoUnit.DAYS.between(fetchStart, today).coerceAtLeast(0) + 1
                require(days <= 36600) { "This history range is too large to display safely." }
                val json = get("apis/web/v1/listen-activity", mapOf("step" to "day", "range" to (days + 1).toString()))
                val rows = json.getJSONArray("activity").objects().map {
                    TrendPoint(OffsetDateTime.parse(it.getString("start_time")).withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(), it.getLong("listens"))
                }.filter { !it.date.isBefore(fetchStart) && !it.date.isAfter(today) }
                // The API supplies zero buckets. Missing buckets remain unknown, never invented zeros.
                val selected = rows.filter { !it.date.isBefore(start) }.sortedBy { it.date }
                val complete = selected.map { it.date }.distinct().size.toLong() == ChronoUnit.DAYS.between(start, today) + 1
                ListeningTrends(selected, true, complete, if (!complete) "Some days are unavailable." else "",
                    json.longOrNull("streak")?.toInt(), heatmap = rows.sortedBy { it.date })
            }
            ListeningProvider.LISTEN_BRAINZ -> {
                require(connection.username.isNotBlank()) { "The saved ListenBrainz connection needs a username." }
                val user = okhttp3.HttpUrl.Builder().scheme("https").host("localhost").addPathSegment(connection.username).build().encodedPath.trim('/')
                val zone = ZoneId.systemDefault()
                val start = range.start(today)
                var before = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
                val records = mutableListOf<Pair<Long, Long?>>()
                var trackLengths = false
                var complete = false
                var crossedBoundary = false
                for (page in 0 until 4) {
                    val query = mutableMapOf("count" to "1000")
                    if (page > 0) query["max_ts"] = before.toString()
                    val rows = get("1/user/$user/listens", query).getJSONObject("payload").getJSONArray("listens").objects()
                    records += rows.map {
                        val duration = recordedDuration(it.optJSONObject("track_metadata") ?: JSONObject())
                        if (duration?.played == false) trackLengths = true
                        it.getLong("listened_at") to duration?.seconds
                    }
                    if (rows.size < 1000 || (start != null && rows.minOf { it.getLong("listened_at") } < start.atStartOfDay(zone).toEpochSecond())) {
                        complete = !crossedBoundary; break
                    }
                    val next = rows.minOf { it.getLong("listened_at") }
                    if (next >= before) break
                    before = next
                    crossedBoundary = true
                }
                recordTrends(records, range, today, complete).copy(durationNote =
                    if (trackLengths) "Based on recorded track lengths" else "Reported playback duration")

            }
            ListeningProvider.MALOJA -> {
                val json = get("apis/mlj_1/scrobbles", mapOf("perpage" to "200"))
                require(json.optString("status") !in setOf("error", "failure")) { "Maloja history unavailable" }
                val rows = json.getJSONArray("list").objects()
                val total = get("apis/mlj_1/numscrobbles", emptyMap()).getLong("amount")
                recordTrends(rows.map { it.getLong("time") to it.longOrNull("duration") }, range, today, total <= rows.size)
            }
            ListeningProvider.LAST_FM -> {
                require(connection.apiKey.isNotBlank() && connection.username.isNotBlank()) { "The saved Last.fm connection needs an API key and username." }
                val query = mutableMapOf("method" to "user.getRecentTracks", "user" to connection.username,
                    "api_key" to connection.apiKey, "format" to "json", "limit" to "200",
                    "to" to today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toString())
                range.start(today)?.let { query["from"] = it.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toString() }
                val json = http.json(Request.Builder().url(listeningUrl("https://ws.audioscrobbler.com", "2.0", query).newBuilder().addPathSegment("").build()).build())
                require(!json.has("error")) { "Last.fm history unavailable" }
                val recent = json.getJSONObject("recenttracks")
                val rows = recent.getJSONArray("track").objects().mapNotNull { it.optJSONObject("date")?.longOrNull("uts")?.let { time -> time to null } }
                recordTrends(rows, range, today, (recent.optJSONObject("@attr")?.optLong("total", Long.MAX_VALUE) ?: Long.MAX_VALUE) <= rows.size)
            }
        }
    }
}

internal fun recordTrends(records: List<Pair<Long, Long?>>, range: TrendRange, today: LocalDate, complete: Boolean, zone: ZoneId = ZoneId.systemDefault(), includeHeatmap: Boolean = true): ListeningTrends {
    val start = range.start(today) ?: records.minOfOrNull { Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate() } ?: today
    val grouped = records.filter { val date = Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate(); !date.isBefore(start) && !date.isAfter(today) }
        .groupBy { Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate() }
    val durations = grouped.isNotEmpty() && grouped.values.flatten().all { it.second != null }
    val dates = if (complete) generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList() else grouped.keys.sorted()
    val hour = if (complete) grouped.values.flatten().groupingBy { Instant.ofEpochSecond(it.first).atZone(zone).hour }.eachCount().maxByOrNull { it.value }?.key else null
    return ListeningTrends(dates.map { date -> val rows = grouped[date].orEmpty(); TrendPoint(date, rows.size.toLong(), if (durations) rows.sumOf { it.second!! } else null) },
        true, complete, if (complete) "" else "Some listening history is unavailable.", hour = hour,
        heatmap = if (includeHeatmap && range != TrendRange.YEAR) recordTrends(records, TrendRange.YEAR, today, complete && records.size < 1000, zone, false).points else emptyList())
}

internal fun trendsJson(data: ListeningTrends): JSONObject = JSONObject().put("daily", data.daily).put("complete", data.complete).put("note", data.note).put("streak", data.streak).put("hour", data.hour).put("durationNote", data.durationNote)
    .put("heatmap", if (data.heatmap.isEmpty()) null else trendsJson(data.copy(points = data.heatmap, heatmap = emptyList())))
    .put("points", org.json.JSONArray(data.points.map { JSONObject().put("date", it.date.toString()).put("end", it.end.toString()).put("count", it.count).put("seconds", it.seconds) }))
internal fun parseTrends(json: JSONObject): ListeningTrends = ListeningTrends(json.getJSONArray("points").objects().map {
    TrendPoint(LocalDate.parse(it.getString("date")), it.getLong("count"), it.longOrNull("seconds"), LocalDate.parse(it.optString("end", it.getString("date"))))
}, json.getBoolean("daily"), json.getBoolean("complete"), json.getString("note"), json.longOrNull("streak")?.toInt(), json.longOrNull("hour")?.toInt(), json.optJSONObject("heatmap")?.let { parseTrends(it).points }.orEmpty(),
    json.stringValue("durationNote").ifBlank { null })
