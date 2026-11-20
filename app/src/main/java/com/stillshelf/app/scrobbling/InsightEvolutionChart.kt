package com.stillshelf.app.scrobbling

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun InsightEvolution(data: InsightDataset, period: InsightPeriod) {
    var limit by rememberSaveable { mutableStateOf(5) }
    var selected by remember(data, limit) { mutableStateOf<Int?>(null) }
    val artists = data.rows.groupBy { it.identity }.entries.sortedByDescending { it.value.sumOf(InsightRow::count) }.take(limit)
    val minimum = data.rows.minOf { it.index!! }
    val maximum = data.rows.maxOf { it.index!! }
    val buckets = when (period) {
        InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> 1..7
        InsightPeriod.THIS_MONTH, InsightPeriod.LAST_MONTH -> 1..maximum
        InsightPeriod.THIS_YEAR, InsightPeriod.LAST_YEAR -> 1..maximum
        InsightPeriod.ALL -> minimum..maximum
    }.toList()
    fun label(bucket: Int, short: Boolean = false): String = when (period) {
        InsightPeriod.THIS_WEEK, InsightPeriod.LAST_WEEK -> DayOfWeek.of(bucket).getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())
        InsightPeriod.THIS_YEAR, InsightPeriod.LAST_YEAR -> Month.of(bucket).getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())
        else -> bucket.toString()
    }
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    val colors = remember(primary, background) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
        // Separate the first five hues widely, then fill the gaps for Top 10. Keep the
        // theme's primary hue as the anchor instead of blending similar theme accents.
        val offsets = listOf(0f, 144f, 288f, 72f, 216f, 36f, 180f, 324f, 108f, 252f)
        offsets.map { offset ->
            val hue = (hsv[0] + offset) % 360f
            val dark = background.luminance() < .5f
            var lightness = if (dark) .72f else .38f
            var color = Color.hsl(hue, .78f, lightness)
            // Legend labels and small chart dots both need readable contrast.
            repeat(12) {
                if (androidx.core.graphics.ColorUtils.calculateContrast(color.toArgb(), background.toArgb()) < 4.5) {
                    lightness = (lightness + if (dark) .02f else -.02f).coerceIn(.1f, .94f)
                    color = Color.hsl(hue, .78f, lightness)
                }
            }
            color
        }
    }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    // Whole-listen steps keep small datasets readable (no repeated rounded tick labels).
    val tickStep = kotlin.math.ceil(artists.flatMap { it.value }.maxOf { it.count } / 4.0).coerceAtLeast(1.0)
    val peak = (tickStep * 4).toFloat()
    val valueByArtist = artists.map { it.value.associateBy { row -> row.index } }
    ListeningChoice("Artists shown", limit, mapOf(3 to "Top 3", 5 to "Top 5", 10 to "Top 10"), true) { limit = it }
    artists.forEachIndexed { index, artist -> Text("● ${artist.value.first().label}", color = colors[index % colors.size], style = MaterialTheme.typography.bodySmall) }
    selected?.let { bucket ->
        InsightTooltip(listOf(label(bucket)).plus(artists.mapIndexed { i, artist ->
            "${artist.value.first().label}: ${valueByArtist[i][bucket]?.count?.let { "$it listens" } ?: "not returned"}"
        }).joinToString("\n"), "insight-evolution-tooltip")
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp).testTag("insight-evolution-chart").pointerInput(data, limit, period) {
        detectTapGestures { tap ->
            val left = with(density) { 36.dp.toPx() }
            val width = size.width - left - with(density) { 8.dp.toPx() }
            selected = buckets[((tap.x - left) / width * buckets.lastIndex).toInt().coerceIn(buckets.indices)]
        }
    }) {
        val left = 36.dp.toPx(); val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx(); val bottom = size.height - 30.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textSize = 10.dp.toPx() }
        repeat(5) { tick ->
            val y = bottom - (bottom - top) * tick / 4
            drawLine(grid.copy(alpha = .55f), Offset(left, y), Offset(right, y))
            paint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(peak * tick / 4.0), left - 5.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        artists.forEachIndexed { index, _ ->
            var previous: Offset? = null
            buckets.forEachIndexed { bucketIndex, bucket ->
                val row = valueByArtist[index][bucket]
                if (row == null) previous = null else {
                    val point = Offset(left + (right - left) * bucketIndex / buckets.lastIndex.coerceAtLeast(1),
                        bottom - (bottom - top) * row.count.toFloat() / peak)
                    previous?.let { drawLine(colors[index % colors.size], it, point, 2.dp.toPx()) }
                    drawCircle(colors[index % colors.size], 3.dp.toPx(), point)
                    previous = point
                }
            }
        }
        paint.textAlign = Paint.Align.CENTER
        (0..4).map { it * buckets.lastIndex / 4 }.distinct().forEach { i ->
            val text = label(buckets[i], short = true)
            val x = (left + (right - left) * i / buckets.lastIndex.coerceAtLeast(1)).coerceIn(left + 12.dp.toPx(), right - 12.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(text, x, size.height - 8.dp.toPx(), paint)
        }
    }
}
