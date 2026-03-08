package com.stillshelf.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val StandardGridCoverWidth: Dp = 150.dp
val StandardGridCoverHeight: Dp = 150.dp
val WideCoverBackgroundBlur: Dp = 30.dp
private const val TypicalCoverAspectRatio = 0.66f
private const val CoverBlurZoomScale = 1.20f
private const val SquareCoverMinAspectRatio = 0.90f
private const val SquareCoverMaxAspectRatio = 1.10f

@Composable
fun rememberCoverImageModel(
    coverUrl: String?,
    preferOriginalSize: Boolean = false
): Any? {
    val context = LocalContext.current
    val normalizedCacheKey = remember(coverUrl) { coverUrl?.let(::normalizeCoverCacheKey) }
    val resolvedUrl = remember(coverUrl) { coverUrl?.let(::splitAuthenticatedUrl) }
    return remember(coverUrl, normalizedCacheKey, resolvedUrl, context, preferOriginalSize) {
        if (coverUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context).apply {
                data(resolvedUrl?.cleanUrl ?: coverUrl)
                memoryCacheKey(normalizedCacheKey)
                diskCacheKey(normalizedCacheKey)
                crossfade(false)
                if (preferOriginalSize) {
                    size(Size.ORIGINAL)
                }
                resolvedUrl?.authToken?.takeIf { it.isNotBlank() }?.let { token ->
                    addHeader("Authorization", authorizationHeaderValue(token))
                }
            }.build()
        }
    }
}

private fun normalizeCoverCacheKey(url: String): String {
    val cleanUrl = splitAuthenticatedUrl(url).cleanUrl
    val httpUrl = cleanUrl.toHttpUrlOrNull() ?: return cleanUrl
    val builder = httpUrl.newBuilder().query(null)
    val queryNames = httpUrl.queryParameterNames.sorted()
    queryNames.forEach { key ->
        if (key.equals("token", ignoreCase = true)) return@forEach
        httpUrl.queryParameterValues(key).forEach { value ->
            builder.addQueryParameter(key, value)
        }
    }
    return builder.build().toString()
}

@Composable
fun FramedCoverImage(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Fit,
    backgroundBlur: Dp = WideCoverBackgroundBlur,
    frameOverlayAlphaMultiplier: Float = 1f,
    disableBlurredFrame: Boolean = false,
    limitInsetToAvoidVerticalLetterbox: Boolean = true,
    forcedSideInset: Dp? = null
) {
    val model = rememberCoverImageModel(coverUrl)
    val frameBackgroundAlpha = if (disableBlurredFrame) 0f else 0.26f
    if (model == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = frameBackgroundAlpha))
    ) {
        val blurLightenOverlayAlphaBase = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
            0.25f
        } else {
            0.19f
        }
        val blurLightenOverlayAlpha = (blurLightenOverlayAlphaBase * frameOverlayAlphaMultiplier)
            .coerceIn(0f, 1f)
        val painter = rememberAsyncImagePainter(model = model)
        val isSuccessState = painter.state is AsyncImagePainter.State.Success
        val successState = painter.state as? AsyncImagePainter.State.Success
        val intrinsicWidth = successState?.result?.drawable?.intrinsicWidth?.takeIf { it > 0 }
        val intrinsicHeight = successState?.result?.drawable?.intrinsicHeight?.takeIf { it > 0 }
        val resolvedAspectRatio = if (intrinsicWidth != null && intrinsicHeight != null) {
            intrinsicWidth.toFloat() / intrinsicHeight.toFloat()
        } else {
            TypicalCoverAspectRatio
        }
        val isSquareLikeCover = resolvedAspectRatio in SquareCoverMinAspectRatio..SquareCoverMaxAspectRatio
        val shouldUseBlurredFrame = !isSquareLikeCover && !disableBlurredFrame
        val preferredInset = forcedSideInset?.coerceIn(0.dp, maxWidth / 2f)
            ?: (maxWidth * 0.20f).coerceIn(2.dp, 34.dp)
        val maxInsetWithoutVerticalLetterbox =
            ((maxWidth - (maxHeight * resolvedAspectRatio)) / 2f).coerceAtLeast(0.dp)
        val sideInset = if (shouldUseBlurredFrame) {
            if (limitInsetToAvoidVerticalLetterbox) {
                minOf(preferredInset, maxInsetWithoutVerticalLetterbox)
            } else {
                preferredInset
            }
        } else {
            0.dp
        }
        if (isSuccessState && shouldUseBlurredFrame) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(backgroundBlur)
                    .graphicsLayer(
                        alpha = 0.84f,
                        scaleX = CoverBlurZoomScale,
                        scaleY = CoverBlurZoomScale
                    ),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = blurLightenOverlayAlpha))
            )
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sideInset),
            contentScale = contentScale
        )
    }
}
