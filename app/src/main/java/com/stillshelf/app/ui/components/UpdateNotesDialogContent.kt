package com.stillshelf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun UpdateNotesDialogContent(
    versionName: String,
    notes: String?,
    maxHeight: Dp = 240.dp
) {
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp)
                .onSizeChanged { viewportHeightPx = it.height }
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("StillShelf $versionName is available.")
            notes
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { releaseNotes ->
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }

        val maxScrollPx = scrollState.maxValue.toFloat()
        if (maxScrollPx > 0f && viewportHeightPx > 0) {
            val viewportPx = viewportHeightPx.toFloat()
            val contentPx = viewportPx + maxScrollPx
            val thumbHeightPx = max(
                minThumbHeightPx,
                (viewportPx * viewportPx) / contentPx
            ).coerceAtMost(viewportPx)
            val thumbTravelPx = (viewportPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetPx = (scrollState.value.toFloat() / maxScrollPx) * thumbTravelPx
            val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffsetDp)
                    .padding(top = 2.dp, end = 1.dp)
                    .width(3.dp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}
