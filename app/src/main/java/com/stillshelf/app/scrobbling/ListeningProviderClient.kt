package com.stillshelf.app.scrobbling

import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface ListeningProviderClient {
    suspend fun validate(connection: ListeningConnection): String
    suspend fun submit(connection: ListeningConnection, listen: TrackListen)
    suspend fun playingNow(connection: ListeningConnection, listen: TrackListen) = Unit
    suspend fun statistics(connection: ListeningConnection): ListeningStatistics
    suspend fun chartPage(connection: ListeningConnection, chart: ListeningChart, offset: Int): ListeningChartPage? = null
}

internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key, -1).takeIf { it >= 0 } else null

internal fun JSONObject.stringValue(key: String): String = if (isNull(key)) "" else optString(key)

internal fun publicListeningUrl(base: String, path: String): String? = base.toHttpUrlOrNull()?.resolve(path)
    ?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()
