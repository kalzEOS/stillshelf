package com.stillshelf.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Composable
fun PlaybackLoadingIndicator(
    modifier: Modifier = Modifier,
    baseTint: Color,
    sweepTint: Color
) {
    val transition = rememberInfiniteTransition(label = "playback-loading")
    val sweepProgress by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playback-loading-sweep"
    )
    val barHeights = remember { listOf(0.48f, 0.74f, 1f, 0.74f, 0.48f) }

    Canvas(modifier = modifier) {
        val barCount = barHeights.size
        if (barCount == 0) return@Canvas
        val clusterWidth = size.width * 0.68f
        val spacingRatio = 0.52f
        val barWidth = (
            clusterWidth / (barCount + (barCount - 1) * spacingRatio)
            ).coerceAtLeast(1f)
        val spacing = barWidth * spacingRatio
        val clusterStartX = (size.width - clusterWidth) / 2f
        val cornerRadius = barWidth / 2f
        val sweepCenterX = size.width * sweepProgress
        val sweepRadius = barWidth * 1.8f
        val maxBarHeight = size.height * 0.82f

        barHeights.forEachIndexed { index, heightFraction ->
            val left = clusterStartX + index * (barWidth + spacing)
            val barHeight = (maxBarHeight * heightFraction).coerceAtLeast(size.height * 0.3f)
            val top = (size.height - barHeight) / 2f
            val barCenterX = left + (barWidth / 2f)
            val distanceFraction = ((barCenterX - sweepCenterX) / sweepRadius).let { kotlin.math.abs(it) }
            val sweepStrength = (1f - distanceFraction.coerceIn(0f, 1f)).let { it * it }
            val tint = lerp(baseTint, sweepTint, sweepStrength)

            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        }
    }
}
