package com.stillshelf.app.scrobbling

import java.time.DayOfWeek
import java.time.Month
import java.time.LocalDate
import java.util.Locale
import org.json.JSONObject

/** Normalize bounded provider aggregates. This never derives durations or first-ever discoveries. */
internal fun parseInsight(kind: InsightKind, json: JSONObject, period: InsightPeriod): InsightDataset {
    val payload = json.optJSONObject("payload") ?: json
    require(!payload.has("range") || payload.getString("range") == period.api) { "The provider returned a different statistics period." }
    val rows = when (kind) {
        InsightKind.DAILY -> {
            val days = payload.optJSONObject(kind.field) ?: throw IllegalArgumentException("Missing daily activity")
            DayOfWeek.entries.flatMap { day ->
                val name = day.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
                days.optJSONArray(name).objects().mapNotNull { item ->
                    val count = item.longOrNull("listen_count") ?: return@mapNotNull null
                    val hour = item.longOrNull("hour")?.takeIf { it in 0..23 }?.toInt() ?: return@mapNotNull null
                    InsightRow(name, count, index = hour)
                }.distinctBy { it.index }
            }
        }
        InsightKind.ARTIST_ALBUMS -> requiredRows(payload, kind).sortedByDescending { it.longOrNull("listen_count") ?: 0 }.take(12).flatMap { artist ->
            val name = artist.stringValue("artist_name").ifBlank { artist.stringValue("name") }
            val identity = artist.stringValue("artist_mbid").ifBlank { name }
            if (name.isBlank()) emptyList() else artist.optJSONArray("albums").objects()
                .mapNotNull { item -> positiveRow(item, "name")?.copy(detail = name, identity = identity) }
                .sortedByDescending { it.count }.take(15)
        }
        InsightKind.EVOLUTION -> {
            val all = requiredRows(payload, kind).mapNotNull { item ->
                val count = item.longOrNull("listen_count")?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = item.stringValue("artist_name").ifBlank { item.stringValue("name") }
                val unit = item.stringValue("time_unit")
                val order = evolutionOrder(period, unit) ?: return@mapNotNull null
                if (name.isBlank()) null else InsightRow(name, count, bucket = unit, index = order,
                    identity = item.stringValue("artist_mbid").ifBlank { name })
            }
            val top = all.groupBy { it.identity }.entries.sortedByDescending { it.value.sumOf(InsightRow::count) }.take(10).map { it.key }.toSet()
            all.filter { it.identity in top }.groupBy { it.identity to it.index }.map { (_, items) -> items.first().copy(count = items.sumOf(InsightRow::count)) }
        }
        InsightKind.GENRES -> {
            // The deployed API uses payload.genre_activity; the documented legacy example uses result.
            val source = payload.optJSONArray(kind.field) ?: json.optJSONArray("result") ?: throw IllegalArgumentException("Missing genre activity")
            source.objects().mapNotNull { positiveRow(it, "genre") }.groupBy { it.label }
                .map { (name, values) -> InsightRow(name, values.sumOf(InsightRow::count)) }.sortedByDescending { it.count }.take(15)
        }
        InsightKind.ERAS -> requiredRows(payload, kind).mapNotNull { item ->
            val year = item.longOrNull("year")?.takeIf { it in 1000..LocalDate.now().year.toLong() + 1 }?.toInt() ?: return@mapNotNull null
            positiveRow(item, "year")?.copy(label = year.toString(), index = year)
        }.groupBy { it.index }.map { (_, values) -> values.first().copy(count = values.sumOf(InsightRow::count)) }.sortedBy { it.index }
        InsightKind.ORIGINS -> requiredRows(payload, kind).mapNotNull { item ->
            val count = item.longOrNull("artist_count")?.takeIf { it > 0 } ?: return@mapNotNull null
            item.stringValue("country").takeIf { it.matches(Regex("[A-Za-z]{2,3}")) }?.let { InsightRow(it.uppercase(Locale.ROOT), count) }
        }.sortedByDescending { it.count }.take(15)
        InsightKind.ALBUMS, InsightKind.TRACKS -> requiredRows(payload, kind).mapNotNull { item ->
            positiveRow(item, if (kind == InsightKind.ALBUMS) "release_name" else "track_name")?.copy(detail = item.stringValue("artist_name"))
        }.sortedByDescending { it.count }.take(10)
    }
    return InsightDataset(kind, rows, payload.longOrNull("from_ts"), payload.longOrNull("to_ts"), payload.longOrNull("last_updated"))
}

private fun positiveRow(json: JSONObject, field: String): InsightRow? {
    val name = json.stringValue(field)
    val count = json.longOrNull("listen_count")?.takeIf { it > 0 } ?: return null
    return if (name.isBlank()) null else InsightRow(name, count)
}

private fun requiredRows(json: JSONObject, kind: InsightKind) = requireNotNull(json.optJSONArray(kind.field)) { "Missing ${kind.field}" }.objects()

internal fun evolutionOrder(period: InsightPeriod, unit: String): Int? = when (period) {
    InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> DayOfWeek.entries.firstOrNull { it.name.equals(unit, true) }?.value
    InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH -> unit.toIntOrNull()?.takeIf { it in 1..31 }
    InsightPeriod.THIS_YEAR, InsightPeriod.LAST_YEAR -> Month.entries.firstOrNull { it.name.equals(unit, true) }?.value
    InsightPeriod.ALL -> unit.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year + 1 }
}

internal fun InsightDataset.meaningful() = rows.any { it.count > 0 } &&
    (kind != InsightKind.EVOLUTION || rows.mapNotNull { it.index }.distinct().size >= 2)
