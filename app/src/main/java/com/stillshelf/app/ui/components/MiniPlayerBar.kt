package com.stillshelf.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.stillshelf.app.core.util.formatHoursMinutesPrecise
import com.stillshelf.app.core.util.progressPresentation
import kotlin.math.cos
import kotlin.math.sin
import com.stillshelf.app.ui.common.rememberCoverImageModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onRewind15: () -> Unit,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.item
    val title = state.displayTitle.ifBlank { item?.book?.title ?: "Nothing playing" }
    val subtitle = when {
        state.isLoading -> "Loading playback..."
        item != null -> formatMiniPlayerSubtitle(item)
        !state.errorMessage.isNullOrBlank() -> state.errorMessage
        else -> "Choose a book to start."
    }
    val shape = RoundedCornerShape(24.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    val frostedFill = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(frostedFill)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item?.book?.coverUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                AsyncImage(
                    model = rememberCoverImageModel(item?.book?.coverUrl),
                    contentDescription = item?.book?.title,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                        repeatDelayMillis = 2000,
                        initialDelayMillis = 1200,
                        spacing = MarqueeSpacing(36.dp)
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRewind15) {
                MiniSeek15Glyph(
                    forward = false,
                    seconds = state.rewindSeconds,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onPlayPause) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniSeek15Glyph(
    forward: Boolean,
    seconds: Int,
    tint: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { scaleX = if (forward) 1f else -1f }
        ) {
            val strokeWidth = 2.2.dp.toPx()
            val inset = 2.dp.toPx()
            val arcSize = size.minDimension - inset * 2
            drawArc(
                color = tint,
                startAngle = 20f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val radius = arcSize / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val angle = Math.toRadians(270.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            val head = 3.8.dp.toPx()
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(x - head, y - head * 0.55f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(x - head, y + head * 0.55f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        Text(
            text = seconds.coerceIn(10, 60).toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tint
        )
    }
}

private fun formatMiniPlayerSubtitle(item: com.stillshelf.app.core.model.ContinueListeningItem): String {
    val presentation = item.progressPresentation()
    val percentLabel = presentation.progressPercentLabel()
    return if (presentation.durationSeconds == null) {
        if ((presentation.normalizedProgressPercent ?: 0.0) > 0.0) {
            "In progress • $percentLabel complete"
        } else {
            "0h 0m left • 0% complete"
        }
    } else if (!presentation.hasStarted && !presentation.isFinished) {
        "${formatHoursMinutesPrecise(presentation.durationSeconds)} left • $percentLabel complete"
    } else {
        "${presentation.remainingTimeLabel(precise = true, emptyFallback = "0h 0m left")} • $percentLabel complete"
    }
}
