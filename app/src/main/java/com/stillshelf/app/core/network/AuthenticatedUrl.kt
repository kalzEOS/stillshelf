package com.stillshelf.app.core.network

import android.util.Base64
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val LEGACY_TOKEN_QUERY_KEY = "token"
private const val AUTH_TOKEN_FRAGMENT_KEY = "stillshelf_token"
data class AuthenticatedUrl(
    val cleanUrl: String,
    val authToken: String?
)

fun addAuthTokenFragment(url: String, authToken: String): String {
    if (authToken.isBlank()) return url
    val httpUrl = url.toHttpUrlOrNull() ?: return url
    val encodedToken = Base64.encodeToString(
        authToken.toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    return httpUrl.newBuilder()
        .fragment("$AUTH_TOKEN_FRAGMENT_KEY=$encodedToken")
        .build()
        .toString()
}

fun splitAuthenticatedUrl(url: String): AuthenticatedUrl {
    val httpUrl = url.toHttpUrlOrNull() ?: return AuthenticatedUrl(url, null)
    val queryToken = httpUrl.queryParameter(LEGACY_TOKEN_QUERY_KEY)
    val fragmentToken = parseTokenFromFragment(httpUrl.fragment)
    val cleaned = httpUrl.newBuilder()
        .removeAllQueryParameters(LEGACY_TOKEN_QUERY_KEY)
        .fragment(null)
        .build()
        .toString()
    return AuthenticatedUrl(
        cleanUrl = cleaned,
        authToken = fragmentToken ?: queryToken
    )
}

fun authorizationHeaderValue(token: String): String {
    return if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
}

private fun parseTokenFromFragment(fragment: String?): String? {
    if (fragment.isNullOrBlank()) return null
    val parts = fragment.split("&")
    val rawValue = parts.asSequence()
        .mapNotNull { piece ->
            val split = piece.split("=", limit = 2)
            val key = split.firstOrNull().orEmpty()
            if (key != AUTH_TOKEN_FRAGMENT_KEY) return@mapNotNull null
            split.getOrElse(1) { "" }
        }
        .firstOrNull()
        ?.trim()
        ?: return null
    if (rawValue.isBlank()) return null
    return runCatching {
        val decoded = Base64.decode(rawValue, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        String(decoded, StandardCharsets.UTF_8)
    }.getOrNull()
}
