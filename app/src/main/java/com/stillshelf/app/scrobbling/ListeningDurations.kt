package com.stillshelf.app.scrobbling

import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class RecordedDuration(val seconds: Long, val played: Boolean)

/** Provider-supplied duration only. Counts, library lengths and guessed averages are never used. */
internal fun recordedDuration(metadata: JSONObject): RecordedDuration? {
    val info = metadata.optJSONObject("additional_info") ?: metadata
    info.longOrNull("duration_played")?.takeIf { it in 1..2_073_600 }?.let { return RecordedDuration(it, true) }
    info.longOrNull("duration_ms")?.takeIf { it in 1000..2_073_600_000 }?.let { return RecordedDuration(it / 1000, false) }
    return info.longOrNull("duration")?.takeIf { it in 1..2_073_600 }?.let { RecordedDuration(it, false) }
}

internal fun activeHourLabel(hour: Int, locale: Locale = Locale.getDefault()): String {
    val start = LocalTime.of(hour, 0)
    val end = LocalTime.of((hour + 1) % 24, 0)
    val number = DateTimeFormatter.ofPattern("h", locale)
    val marker = DateTimeFormatter.ofPattern("a", locale)
    return if (start.format(marker) == end.format(marker))
        "${start.format(number)}–${end.format(number)} ${end.format(marker)}"
    else "${start.format(number)} ${start.format(marker)}–${end.format(number)} ${end.format(marker)}"
}
