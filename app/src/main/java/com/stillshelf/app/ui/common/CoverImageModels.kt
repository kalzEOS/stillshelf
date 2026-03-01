package com.stillshelf.app.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val StandardGridCoverWidth: Dp = 150.dp
val StandardGridCoverHeight: Dp = 150.dp
val WideCoverBackgroundBlur: Dp = 36.dp
private const val TypicalCoverAspectRatio = 0.66f

@Composable
fun rememberCoverImageModel(coverUrl: String?): Any? {
    val context = LocalContext.current
    val normalizedCacheKey = remember(coverUrl) { coverUrl?.let(::normalizeCoverCacheKey) }
    val resolvedUrl = remember(coverUrl) { coverUrl?.let(::splitAuthenticatedUrl) }
    return remember(coverUrl, normalizedCacheKey, resolvedUrl, context) {
        if (coverUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context).apply {
                data(resolvedUrl?.cleanUrl ?: coverUrl)
                memoryCacheKey(normalizedCacheKey)
                diskCacheKey(normalizedCacheKey)
                crossfade(false)
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
    backgroundBlur: Dp = WideCoverBackgroundBlur
) {
    val model = rememberCoverImageModel(coverUrl)
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        val preferredInset = (maxWidth * 0.20f).coerceIn(2.dp, 34.dp)
        val maxInsetWithoutVerticalLetterbox =
            ((maxWidth - (maxHeight * TypicalCoverAspectRatio)) / 2f).coerceAtLeast(0.dp)
        val sideInset = minOf(preferredInset, maxInsetWithoutVerticalLetterbox)
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlur),
            contentScale = ContentScale.Crop
        )
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sideInset),
            contentScale = contentScale
        )
    }
}
