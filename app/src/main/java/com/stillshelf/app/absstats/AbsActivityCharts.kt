package com.stillshelf.app.absstats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable internal fun AbsDailyActivity(days: Map<LocalDate, Double>) {
    var range by remember { mutableStateOf(30) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    val first = maxOf(today.minusDays(range - 1L), days.keys.minOrNull() ?: today)
    val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= today }.toList()
    val peak = dates.maxOfOrNull { days[it] ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Listening Activity", "Time recorded each day · server dates")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(30, 90, 365).forEach { size ->
            FilterChip(selected = range == size, onClick = { range = size; selected = null }, label = { Text("$size days") })
        } }
        if (days.isEmpty()) Text("No daily listening records yet.")
        else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val chartWidth = maxOf(maxWidth, (dates.size * 12).dp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(chartWidth).height(160.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                dates.forEach { date ->
                    val value = days[date] ?: 0.0
                    Box(Modifier.weight(1f).height((if (value > 0) (value / peak * 150).coerceAtLeast(4.0) else 2.0).dp)
                        .background(if (selected == date) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0) 0.8f else 0.15f), RoundedCornerShape(3.dp))
                        .semantics { contentDescription = "$date: ${absListeningTime(value)}" }
                        .clickable { selected = date })
                }
            }
            }
            }
            if (dates.size > 30) Text("Scroll the chart to explore each day.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(first.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.bodySmall)
                Text(today.format(DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.bodySmall)
            }
            selected?.let { Text("${it.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · ${absListeningTime(days[it] ?: 0.0)}") }
        }
    }
}

@Composable internal fun AbsListeningCalendar(days: Map<LocalDate, Double>) {
    val today = LocalDate.now()
    val start = today.minusWeeks(51).with(DayOfWeek.MONDAY)
    val firstRecord = days.keys.minOrNull()
    val peak = days.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Listening Calendar", "Last 12 months · scroll to explore")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(52) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { day ->
                        val date = start.plusDays(week * 7L + day)
                        val known = firstRecord != null && date >= firstRecord && date <= today
                        val value = days[date] ?: 0.0
                        val alpha = if (!known) 0.04f else if (value == 0.0) 0.12f else (0.3 + 0.7 * value / peak).toFloat()
                        Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(3.dp))
                            .semantics { contentDescription = "$date: ${if (known) absListeningTime(value) else "Outside recorded date range"}" }
                            .clickable(enabled = known) { selected = date })
                    }
                }
            }
        }
        selected?.let { Text("$it · ${absListeningTime(days[it] ?: 0.0)}") }
        Text("Darker squares mean more recorded listening time.", style = MaterialTheme.typography.bodySmall)
    }
}
