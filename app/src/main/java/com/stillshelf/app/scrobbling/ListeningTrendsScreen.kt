package com.stillshelf.app.scrobbling

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
internal fun ListeningTrendsScreen(refresh: Int, viewModel: ListeningTrendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TrendRange.MONTH) }
    var handledRefresh by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(range, refresh) { val force = handledRefresh != refresh; handledRefresh = refresh; viewModel.load(range, force) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ListeningTrendsDashboard(state, range) { range = it }
        ListenBrainzInsightsScreen(refresh)
    }
}

@Composable
internal fun ListeningTrendsDashboard(state: TrendsUiState, range: TrendRange, onRange: (TrendRange) -> Unit) {
    var heatmapRange by rememberSaveable { mutableStateOf(TrendRange.YEAR) }
    Column(Modifier.fillMaxWidth().testTag("trends-dashboard"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.data?.let { data ->
            if (!data.complete && data.daily) Text("Some listening history is unavailable.", style = MaterialTheme.typography.bodySmall)
            if (data.points.isEmpty()) Text("No supported historical data for this range.") else {
                TrendCard("Listening Activity", "Scrobbles over time", range, onRange) { InteractiveTrendChart(trendBuckets(data.points, range), false) }
                if (data.complete && data.points.all { it.seconds != null }) TrendCard("Listening Time", data.durationNote ?: "Hours listened over time", range, onRange) {
                    InteractiveTrendChart(trendBuckets(data.points, range), true)
                }
                if (data.daily) TrendCard("Listening Heatmap", "Listening activity by day", heatmapRange, { heatmapRange = it }) {
                    val available = data.heatmap.ifEmpty { data.points }
                    val start = heatmapRange.start(available.maxOf { it.date })
                    TrendHeatmap(available.filter { start == null || !it.date.isBefore(start) })
                }
                if (data.daily && data.complete && data.points.isNotEmpty()) TrendCard("Listening Patterns", "Your listening habits") {
                    val active = data.points.filter { it.count > 0 }
                    val best = active.groupBy { it.date.dayOfWeek }.maxByOrNull { it.value.sumOf { p -> p.count } }?.key
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (best != null) PatternTile("Most Active Day", best.name.lowercase().replaceFirstChar(Char::uppercase), Modifier.weight(1f))
                        data.hour?.let { PatternTile("Most Active Time", activeHourLabel(it), Modifier.weight(1f)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PatternTile("Daily Average", "%.1f scrobbles".format(data.points.sumOf { it.count }.toDouble() / data.points.size), Modifier.weight(1f))
                        data.streak?.let { PatternTile("Listening Streak", "$it days", Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun TrendCard(title: String, subtitle: String, range: TrendRange? = null, onRange: (TrendRange) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (range != null) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(range.label + " ▾", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            TrendRange.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label) },
                                onClick = { expanded = false; onRange(value) }) }
                        }
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun InteractiveTrendChart(points: List<TrendPoint>, duration: Boolean) {
    var selected by remember(points) { mutableStateOf<TrendPoint?>(null) }
    val color = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val density = androidx.compose.ui.platform.LocalDensity.current
    val durationUnit = trendDurationUnit(points)
    val values = points.map { if (duration) it.seconds!!.toDouble() / durationUnit.first else it.count.toDouble() }
    val rawMax = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val scale = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawMax)))
    val maximum = if (duration) kotlin.math.ceil(rawMax / scale) * scale else kotlin.math.ceil(rawMax / 4).coerceAtLeast(1.0) * 4
    Column {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(190.dp).testTag("trend-chart")
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val left = with(density) { 36.dp.toPx() }
                        val width = size.width - left - with(density) { 8.dp.toPx() }
                        val index = ((tap.x - left) / width * points.size).toInt().coerceIn(points.indices)
                        selected = points[index]
                    }
                }) {
                val left = 36.dp.toPx(); val right = size.width - 8.dp.toPx()
                val top = 30.dp.toPx(); val bottom = size.height - 26.dp.toPx()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = with(density) { 10.dp.toPx() }; this.color = ink.toArgb()
                }
                for (tick in 0..4) {
                    val y = bottom - (bottom - top) * tick / 4
                    drawLine(grid.copy(alpha = .55f), Offset(left, y), Offset(right, y), 1.dp.toPx())
                    paint.textAlign = android.graphics.Paint.Align.RIGHT
                    drawContext.canvas.nativeCanvas.drawText(if (duration) "%.1f%s".format(maximum * tick / 4, durationUnit.second) else "%.0f".format(maximum * tick / 4), left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
                }
                val step = (right - left) / points.size
                fun xy(index: Int) = Offset(left + step * (index + .5f), bottom - ((bottom - top) * values[index] / maximum).toFloat())
                if (duration) {
                    val line = androidx.compose.ui.graphics.Path()
                    line.moveTo(xy(0).x, xy(0).y)
                    for (i in 1 until points.size) {
                        val a = xy(i - 1); val b = xy(i); val mid = (a.x + b.x) / 2
                        line.cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                    val area = androidx.compose.ui.graphics.Path().apply { addPath(line); lineTo(xy(points.lastIndex).x, bottom); lineTo(xy(0).x, bottom); close() }
                    drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = .35f), color.copy(alpha = .03f))))
                    drawPath(line, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    points.indices.forEach { drawCircle(color, 3.dp.toPx(), xy(it)) }
                } else points.indices.forEach { i ->
                    val p = xy(i)
                    val height = if (values[i] > 0) (bottom - p.y).coerceAtLeast(2.dp.toPx()) else 0f
                    drawRoundRect(color.copy(alpha = if (selected == points[i]) 1f else .6f),
                        Offset(left + step * i + step * .12f, bottom - height),
                        androidx.compose.ui.geometry.Size(step * .76f, height),
                        androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
                paint.textAlign = android.graphics.Paint.Align.CENTER
                val ticks = (0..4).map { it * points.lastIndex / 4 }.distinct()
                ticks.forEach { i ->
                    val text = points[i].date.format(DateTimeFormatter.ofPattern(if (points.first().date.year == points.last().date.year) "MMM d" else "MMM yy"))
                    val x = xy(i).x.coerceIn(left + 16.dp.toPx(), right - 16.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(text, x, size.height - 5.dp.toPx(), paint)
                }
            }
            selected?.let {
                Surface(Modifier.align(Alignment.TopCenter).testTag("trend-tooltip").semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface) {
                    Text("${it.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}${if (it.end != it.date) " – ${it.end}" else ""}\n${if (duration) "%.2f %s".format(it.seconds!! / durationUnit.first, durationUnit.second) else "${it.count} scrobbles"}",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

@Composable
private fun TrendHeatmap(points: List<TrendPoint>) {
    var selected by remember(points) { mutableStateOf<TrendPoint?>(null) }
    val visible = points.filter { !it.date.isBefore(points.maxOf { p -> p.date }.minusDays(365)) }
    val byDate = visible.associateBy { it.date }
    val max = points.maxOf { it.count }.coerceAtLeast(1)
    selected?.let { Text("${it.date}: ${it.count} scrobbles", Modifier.testTag("heatmap-tooltip")) }
    val first = visible.minOf { it.date }
    val end = points.maxOf { it.date }
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val weeks = (java.time.temporal.ChronoUnit.DAYS.between(start, end) / 7 + 1).toInt()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pitchDp = ((maxWidth - 32.dp) / weeks).coerceIn(8.dp, 17.dp)
        val width = maxOf(maxWidth, 32.dp + pitchDp * weeks)
        val primary = MaterialTheme.colorScheme.primary
        val faint = MaterialTheme.colorScheme.surfaceVariant
        val ink = MaterialTheme.colorScheme.onSurfaceVariant
        val density = androidx.compose.ui.platform.LocalDensity.current
        Row {
            Column(Modifier.width(32.dp)) {
                Spacer(Modifier.height(23.dp))
                Box(Modifier.height(pitchDp * 7)) {
                    listOf(0 to "Mon", 2 to "Wed", 4 to "Fri", 6 to "Sun").forEach { (row, label) ->
                        Text(label, Modifier.offset(y = pitchDp * row - 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(width - 32.dp).height(31.dp + pitchDp * 7).testTag("trend-heatmap").pointerInput(points, pitchDp) {
                detectTapGestures { tap ->
                    val x = 0f
                    val pitch = with(density) { pitchDp.toPx() }
                    val col = ((tap.x - x) / pitch).toInt()
                    val row = ((tap.y - with(density) { 23.dp.toPx() }) / pitch).toInt()
                    if (col in 0 until weeks && row in 0..6) selected = byDate[start.plusDays(col * 7L + row)]
                }
            }) {
                val pitch = pitchDp.toPx(); val left = 0f; val top = 23.dp.toPx()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textSize = 10.dp.toPx() }
                repeat(weeks) { week ->
                    val date = start.plusDays(week * 7L)
                    if ((week == 0 && date.plusDays(14).month == date.month) || date.dayOfMonth <= 7) drawContext.canvas.nativeCanvas.drawText(date.format(DateTimeFormatter.ofPattern("MMM")), left + pitch * week, 13.dp.toPx(), paint)
                    repeat(7) { day ->
                        val point = byDate[date.plusDays(day.toLong())]
                        if (point != null) drawRoundRect(primary.copy(alpha = if (point.count == 0L) .08f else .25f + .75f * point.count.toFloat() / max),
                            Offset(left + pitch * week, top + pitch * day), androidx.compose.ui.geometry.Size(pitch - 2.dp.toPx(), pitch - 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        else drawRoundRect(faint.copy(alpha = .35f), Offset(left + pitch * week, top + pitch * day), androidx.compose.ui.geometry.Size(pitch - 2.dp.toPx(), pitch - 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                    }
                }
            }
        }
        }
    }
}
