package com.stillshelf.app.scrobbling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Optional, identity-verified names for a tapped artist. Never search/guess an artist by popularity. */
@Singleton
class ListeningArtistAliases @Inject constructor(private val http: ListeningHttp) {
    private data class Cached(val names: Set<String>, val expires: Long)
    private val lock = Mutex()
    private val records = linkedMapOf<String, Cached>()
    private var nextRequest = 0L

    suspend fun names(mbid: String, allowFetch: Boolean): Set<String> {
        if (!mbid.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) return emptySet()
        val id = mbid.lowercase(Locale.ROOT)
        return lock.withLock {
            val now = System.nanoTime()
            records[id]?.takeIf { it.expires > now }?.let { return@withLock it.names }
            if (!allowFetch) return@withLock emptySet()
            val names = try {
                withTimeoutOrNull(5_000) {
                    val wait = nextRequest - System.nanoTime()
                    if (wait > 0) delay((wait + 999_999) / 1_000_000)
                    nextRequest = System.nanoTime() + 1_100_000_000L
                    val json = http.json(Request.Builder().url("https://musicbrainz.org/ws/2/artist/$id?inc=aliases&fmt=json").build())
                    require(json.stringValue("id").equals(id, ignoreCase = true))
                    (listOf(json.stringValue("name")) + json.optJSONArray("aliases").objects().map { it.stringValue("name") })
                        .filter(String::isNotBlank).take(100).toSet()
                }.orEmpty()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
            records[id] = Cached(names, System.nanoTime() + if (names.isEmpty()) 60_000_000_000L else 86_400_000_000_000L)
            while (records.size > 100) records.remove(records.keys.first())
            names
        }
    }
}
