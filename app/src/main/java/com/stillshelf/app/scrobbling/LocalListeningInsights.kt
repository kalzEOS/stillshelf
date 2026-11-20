package com.stillshelf.app.scrobbling

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalInsights(val results: Map<InsightKind, InsightResult> = emptyMap(),
    val artists: List<InsightRow> = emptyList(), val discovery: List<InsightRow> = emptyList(), val coverage: String = "")

/** All queries aggregate on-device snapshots. No provider, enrichment or network client is involved. */
class LocalListeningInsights @Inject constructor(private val database: LocalListeningDatabase, private val dao: LocalListeningDao) {
    suspend fun load(period: InsightPeriod, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LocalInsights =
        withContext(Dispatchers.IO) { database.withTransaction {
            val first = dao.summary().first ?: return@withTransaction LocalInsights()
            val firstDate = Instant.ofEpochSecond(first).atZone(zone).toLocalDate()
            require(java.time.temporal.ChronoUnit.DAYS.between(firstDate, today) <= 36600) { "History range is too large to display." }
            val start = period.anchor(today) ?: firstDate
            val end = when (period) {
                InsightPeriod.LAST_WEEK -> start.plusWeeks(1)
                InsightPeriod.LAST_MONTH -> start.plusMonths(1)
                InsightPeriod.LAST_YEAR -> start.plusYears(1)
                else -> today.plusDays(1)
            }
            val from = start.atStartOfDay(zone).toEpochSecond()
            val until = end.atStartOfDay(zone).toEpochSecond()
            val where = "l.startedAtSeconds >= ? AND l.startedAtSeconds < ?"
            suspend fun query(sql: String, vararg args: Any) = dao.insights(SimpleSQLiteQuery(sql, args))
            suspend fun grouped(label: String, detail: String = "''", bucket: String = "0", identity: String = "''",
                group: String, extra: String = "", join: String = "", limit: Int = 180) = query(
                "SELECT $label AS label, $detail AS detail, $bucket AS bucket, COUNT(*) AS count, $identity AS identity " +
                    "FROM local_listens l $join WHERE $where $extra GROUP BY $group ORDER BY count DESC, label LIMIT $limit", from, until)
            fun LocalInsightAggregate.row() = InsightRow(label, count, detail, index = bucket, identity = identity)
            fun dataset(kind: InsightKind, rows: List<InsightRow>) = InsightDataset(kind, rows, from, until)
            val results = linkedMapOf<InsightKind, InsightResult>()
            fun add(kind: InsightKind, rows: List<InsightRow>) { if (rows.any { it.count > 0 }) results[kind] = InsightResult(dataset(kind, rows)) }

            val hour = "CAST(strftime('%H', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER)"
            val weekday = "((CAST(strftime('%w', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER) + 6) % 7 + 1)"
            val hours = grouped(weekday, bucket = hour, group = "$weekday, $hour").associateBy { it.label.toInt() to it.bucket }
            if (hours.isNotEmpty()) add(InsightKind.DAILY, DayOfWeek.entries.flatMap { day -> (0..23).map { h ->
                InsightRow(day.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase), hours[day.value to h]?.count ?: 0, index = h)
            } })

            val artists = grouped("l.artist", identity = "l.artistKey", group = "l.artistKey", limit = 10).map { it.row() }
            val artistKeys = artists.map { it.identity }.toSet()
            val bucket = when (period) {
                InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> weekday
                InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH -> "CAST(strftime('%d', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER)"
                InsightPeriod.THIS_YEAR, InsightPeriod.LAST_YEAR -> "CAST(strftime('%m', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER)"
                InsightPeriod.ALL -> "(CAST(strftime('%Y', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER) * 12 + CAST(strftime('%m', l.startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER))"
            }
            val evolution = query("SELECT l.artist AS label, '' AS detail, $bucket AS bucket, COUNT(*) AS count, l.artistKey AS identity " +
                "FROM local_listens l WHERE $where AND l.artistKey IN (SELECT artistKey FROM local_listens " +
                "WHERE startedAtSeconds >= ? AND startedAtSeconds < ? GROUP BY artistKey ORDER BY COUNT(*) DESC LIMIT 10) " +
                "GROUP BY l.artistKey, $bucket", from, until, from, until).filter { it.identity in artistKeys }
            val monthFormat = DateTimeFormatter.ofPattern("MMM yy")
            fun order(date: LocalDate) = when (period) {
                InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> date.dayOfWeek.value
                InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH -> date.dayOfMonth
                InsightPeriod.THIS_YEAR, InsightPeriod.LAST_YEAR -> date.monthValue
                InsightPeriod.ALL -> date.year * 12 + date.monthValue
            }
            val knownStart = maxOf(firstDate, start)
            val knownEnd = minOf(end, today.plusDays(1))
            val bins = generateSequence(knownStart) { it.plusDays(1) }.takeWhile { it < knownEnd }.map(::order).distinct().toList()
            val indexed = evolution.associateBy { it.identity to it.bucket }
            add(InsightKind.EVOLUTION, artists.flatMap { artist -> bins.map { index ->
                InsightRow(artist.label, indexed[artist.identity to index]?.count ?: 0, index = index,
                    identity = artist.identity, bucket = if (period == InsightPeriod.ALL)
                        YearMonth.of((index - 1) / 12, (index - 1) % 12 + 1).format(monthFormat) else index.toString())
            } })
            add(InsightKind.ALBUMS, grouped("l.album", "l.artist", identity = "l.albumKey", group = "l.albumKey", extra = "AND l.album != ''", limit = 10).map { it.row() })
            add(InsightKind.TRACKS, grouped("l.title", "l.artist", identity = "l.trackKey", group = "l.trackKey", limit = 10).map { it.row() })
            add(InsightKind.ARTIST_ALBUMS, grouped("l.album", "l.artist", identity = "l.artistKey", group = "l.albumKey", extra = "AND l.album != ''")
                .groupBy { it.identity }.entries.sortedByDescending { it.value.sumOf(LocalInsightAggregate::count) }.take(12)
                .flatMap { it.value.take(15).map { row -> row.row() } })
            add(InsightKind.GENRES, grouped("g.genre", group = "g.genre COLLATE NOCASE",
                join = "JOIN local_listen_genres g ON g.listenId = l.id", limit = 15).map { it.row() })
            add(InsightKind.ERAS, grouped("CAST(l.releaseYear AS TEXT)", bucket = "l.releaseYear", group = "l.releaseYear",
                extra = "AND l.releaseYear IS NOT NULL", limit = 1000).map { it.row() }.sortedBy { it.index })

            val discoveries = query("SELECT strftime('%Y-%m-%d', firstSeen, 'unixepoch', 'localtime') AS label, '' AS detail, " +
                "0 AS bucket, COUNT(*) AS count, '' AS identity FROM " +
                "(SELECT MIN(startedAtSeconds) AS firstSeen FROM local_listens GROUP BY artistKey) " +
                "WHERE firstSeen >= ? AND firstSeen < ? GROUP BY label ORDER BY label DESC LIMIT 30", from, until).map { it.row() }
            val coverage = query("SELECT '' AS label, '' AS detail, COUNT(*) AS bucket, " +
                "COALESCE(SUM(CASE WHEN l.releaseYear IS NOT NULL THEN 1 ELSE 0 END), 0) AS count, " +
                "CAST(COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM local_listen_genres g WHERE g.listenId = l.id) THEN 1 ELSE 0 END), 0) AS TEXT) AS identity " +
                "FROM local_listens l WHERE $where", from, until).firstOrNull()?.takeIf { it.bucket > 0 }
            LocalInsights(results, artists, discoveries, coverage?.let {
                "Library metadata coverage: genre tags for ${it.identity} of ${it.bucket} listens; release years for ${it.count} of ${it.bucket}."
            }.orEmpty())
        } }
}
