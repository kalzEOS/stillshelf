package com.stillshelf.app.absstats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal data class AbsListeningPatterns(val activeDays: Int, val longestStreak: Int,
    val busiestDay: DayOfWeek?, val averageActiveDaySeconds: Double?)

internal fun absListeningPatterns(days: Map<LocalDate, Double>): AbsListeningPatterns {
    val active = days.filterValues { it.isFinite() && it > 0 }
    var streak = 0
    var best = 0
    var previous: LocalDate? = null
    active.keys.sorted().forEach { date ->
        streak = if (previous?.plusDays(1) == date) streak + 1 else 1
        best = maxOf(best, streak)
        previous = date
    }
    return AbsListeningPatterns(active.size, best,
        active.entries.groupBy { it.key.dayOfWeek }.mapValues { (_, rows) -> rows.sumOf { it.value } }.maxByOrNull { it.value }?.key,
        active.values.takeIf { it.isNotEmpty() }?.average())
}

@Composable internal fun AbsListeningPatterns(days: Map<LocalDate, Double>) {
    val patterns = absListeningPatterns(days)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Listening Patterns", "Based on recorded server dates")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) {
                Metric("Longest streak", "${patterns.longestStreak} days")
            } }
            Card(Modifier.weight(1f)) { Column(Modifier.padding(14.dp)) {
                Metric("Most active day", patterns.busiestDay?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "Not available")
            } }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
            Metric("Average per active day", absListeningTime(patterns.averageActiveDaySeconds))
        } }
    }
}
