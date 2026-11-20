package com.stillshelf.app.scrobbling

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun InsightBars(rows: List<InsightRow>, tag: String, unit: String = "listens", onSelect: (InsightRow) -> Unit = {}) {
    var selected by remember(rows) { mutableStateOf<InsightRow?>(null) }
    val maximum = rows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: return
    Column(Modifier.fillMaxWidth().testTag("insight-bars-$tag"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        selected?.let { InsightTooltip("${it.label}${if (it.detail.isBlank()) "" else " · ${it.detail}"}\n${it.count} $unit", "insight-tooltip-$tag") }
        rows.forEachIndexed { index, row ->
            Column(Modifier.fillMaxWidth().testTag("insight-bar-$tag-$index").clickable { selected = row; onSelect(row) }.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%,d".format(row.count), style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((row.count.toFloat() / maximum).coerceIn(0f, 1f)).height(10.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .65f), RoundedCornerShape(3.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall)
            Text("$maximum $unit", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun InsightTooltip(text: String, tag: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
        Text(text, Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

@Composable
internal fun InsightHourGrid(data: InsightDataset) {
    var selected by remember(data) { mutableStateOf<InsightRow?>(null) }
    val rows = data.rows.associateBy { it.label to it.index }
    val maximum = data.rows.maxOf { it.count }.coerceAtLeast(1)
    Text("Day and hour (UTC)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    selected?.let { InsightTooltip("${it.label} · %02d:00 UTC\n${it.count} listens".format(it.index), "insight-hour-tooltip") }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(40.dp)) {
            Spacer(Modifier.height(24.dp))
            DayOfWeek.entries.forEach { day -> Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
            } }
        }
        Column(Modifier.horizontalScroll(rememberScrollState()).testTag("insight-hour-scroll")) {
            Row { repeat(24) { hour -> Box(Modifier.width(24.dp).height(24.dp), contentAlignment = Alignment.Center) {
                if (hour % 3 == 0) Text(hour.toString(), style = MaterialTheme.typography.labelSmall)
            } } }
            DayOfWeek.entries.forEach { day ->
                val name = day.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercase)
                Row {
                    repeat(24) { hour ->
                        val row = rows[name to hour]
                        val tint = if (row == null || row.count == 0L) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary.copy(alpha = .2f + .8f * row.count.toFloat() / maximum)
                        Box(Modifier.size(24.dp).padding(1.dp).background(tint, RoundedCornerShape(3.dp))
                            .testTag("insight-hour-${day.value}-$hour")
                            .semantics { contentDescription = "$name $hour:00 UTC: ${row?.count?.toString() ?: "unavailable"} listens" }
                            .clickable(enabled = row != null) { selected = row })
                    }
                }
            }
        }
    }
}
