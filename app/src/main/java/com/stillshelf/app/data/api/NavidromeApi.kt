package com.stillshelf.app.data.api

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class NavidromeAuth(
    val serverId: String = "",
    val musicFolderId: String? = null,
    val baseUrl: String,
    val canonicalBaseUrl: String = baseUrl,
    val username: String,
    val encPassword: String
)

data class NavidromeMusicFolderDto(
    val id: String,
    val name: String
)

data class NavidromeScanStatusDto(
    val scanning: Boolean,
    val scannedCount: Int? = null,
    val folderCount: Int? = null,
    val lastScanLabel: String? = null
)

data class NavidromeArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?,
    val artistImageUrl: String?
)

data class NavidromeAlbumDto(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String?,
    val year: Int?,
    val songCount: Int,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val genre: String?
)

data class NavidromeTrackDto(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val suffix: String?,
    val contentType: String?,
    val bitRateKbps: Int?,
    val sizeBytes: Long? = null
)

data class NavidromeLyricsLineDto(
    val startMs: Int?,
    val value: String
)

data class NavidromeStructuredLyricsDto(
    val synced: Boolean,
    val offsetMs: Int?,
    val lines: List<NavidromeLyricsLineDto>
)

data class NavidromePlaylistDto(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val durationSeconds: Int? = null
)

data class NavidromePlaylistDetailDto(
    val playlist: NavidromePlaylistDto,
    val tracks: List<NavidromeTrackDto>
)

data class NavidromeRadioDto(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String?
)

data class NavidromeArtistDetailDto(
    val artist: NavidromeArtistDto,
    val albums: List<NavidromeAlbumDto>
)

data class NavidromeAlbumDetailDto(
    val album: NavidromeAlbumDto,
    val tracks: List<NavidromeTrackDto>
)

data class NavidromeSearchDto(
    val artists: List<NavidromeArtistDto>,
    val albums: List<NavidromeAlbumDto>,
    val tracks: List<NavidromeTrackDto>
)

internal fun decodeNavidromeResponseBody(
    bytes: ByteArray,
    declaredCharset: Charset?
): String {
    if (bytes.isEmpty()) return ""
    decodeStrictUtf8OrNull(bytes)?.let { return it }

    val candidates = buildList {
        declaredCharset?.let(::add)
        add(Charset.forName("windows-1252"))
        add(StandardCharsets.ISO_8859_1)
    }.distinct()

    return candidates
        .map { charset -> charset to String(bytes, charset) }
        .minByOrNull { (_, text) -> scoreDecodedNavidromeText(text) }
        ?.second
        .orEmpty()
}

internal fun scoreDecodedNavidromeText(text: String): Int {
    val replacementCount = text.count { it == '\uFFFD' }
    val controlCount = text.count { it.code in 0x80..0x9F }
    return (replacementCount * 10) + controlCount
}

private fun decodeStrictUtf8OrNull(bytes: ByteArray): String? {
    return runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}

@Singleton
class NavidromeApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun testServerConnection(baseUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = normalizeBaseUrl(baseUrl).toHttpUrlOrNull()
                ?: throw IOException("Invalid Navidrome URL.")
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code >= 500) {
                    throw IOException("Navidrome server test failed with HTTP ${response.code}.")
                }
            }
            Unit
        }
    }

    suspend fun ping(
        baseUrl: String,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = NavidromeAuth(
                baseUrl = normalizeBaseUrl(baseUrl),
                username = username.trim(),
                encPassword = encodePassword(password)
            )
            execute(buildRequest(auth, "rest/ping.view"))
            Unit
        }
    }

    suspend fun ping(auth: NavidromeAuth): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            execute(buildRequest(auth, "rest/ping.view"))
            Unit
        }
    }

    suspend fun measurePing(auth: NavidromeAuth): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val startedAtMs = System.currentTimeMillis()
            execute(buildRequest(auth, "rest/ping.view"))
            (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        }
    }

    suspend fun getArtists(auth: NavidromeAuth): Result<List<NavidromeArtistDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = execute(
                    buildRequest(
                        auth,
                        "rest/getArtists.view",
                        queryWithMusicFolder(auth)
                    )
                )
                parseArtists(root)
            }
        }

    suspend fun getMusicFolders(auth: NavidromeAuth): Result<List<NavidromeMusicFolderDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = execute(buildRequest(auth, "rest/getMusicFolders.view"))
                parseMusicFolders(root.optJSONObject("musicFolders")?.optJSONArray("musicFolder"))
            }
        }

    suspend fun getScanStatus(auth: NavidromeAuth): Result<NavidromeScanStatusDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = execute(buildRequest(auth, "rest/getScanStatus.view"))
                parseScanStatus(root.optJSONObject("scanStatus"))
            }
        }

    suspend fun startScan(
        auth: NavidromeAuth,
        fullScan: Boolean = false
    ): Result<NavidromeScanStatusDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/startScan.view",
                    query = if (fullScan) {
                        listOf("fullScan" to "true")
                    } else {
                        emptyList()
                    }
                )
            )
            parseScanStatus(root.optJSONObject("scanStatus"))
        }
    }

    suspend fun getAlbumList(
        auth: NavidromeAuth,
        type: String,
        size: Int = 24,
        offset: Int = 0
    ): Result<List<NavidromeAlbumDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getAlbumList2.view",
                    query = queryWithMusicFolder(
                        auth,
                        "type" to type,
                        "size" to size.coerceIn(1, 100).toString(),
                        "offset" to offset.coerceAtLeast(0).toString()
                    )
                )
            )
            parseAlbumArray(
                root.optJSONObject("albumList2")?.optJSONArray("album")
            )
        }
    }

    suspend fun getPlaylists(auth: NavidromeAuth): Result<List<NavidromePlaylistDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = execute(buildRequest(auth, "rest/getPlaylists.view"))
                parsePlaylists(root.optJSONObject("playlists")?.optJSONArray("playlist"))
            }
        }

    suspend fun getPlaylist(
        auth: NavidromeAuth,
        playlistId: String
    ): Result<NavidromePlaylistDetailDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getPlaylist.view",
                    query = listOf("id" to playlistId)
                )
            )
            val playlistNode = root.optJSONObject("playlist")
                ?: throw IOException("Playlist response was empty.")
            NavidromePlaylistDetailDto(
                playlist = parsePlaylist(playlistNode),
                tracks = parseTrackArray(
                    playlistNode.optJSONArray("entry")
                        ?: playlistNode.optJSONArray("song")
                )
            )
        }
    }

    suspend fun createPlaylist(
        auth: NavidromeAuth,
        name: String
    ): Result<NavidromePlaylistDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/createPlaylist.view",
                    query = listOf("name" to name.trim())
                )
            )
            root.optJSONObject("playlist")
                ?.let(::parsePlaylist)
                ?: NavidromePlaylistDto(
                    id = "",
                    name = name.trim().ifBlank { "Playlist" }
                )
        }
    }

    suspend fun updatePlaylist(
        auth: NavidromeAuth,
        playlistId: String,
        name: String,
        songIndicesToRemove: List<Int> = emptyList(),
        songIdsToAdd: List<String> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildList {
                add("playlistId" to playlistId)
                add("name" to name.trim())
                songIndicesToRemove.forEach { index ->
                    add("songIndexToRemove" to index.toString())
                }
                songIdsToAdd.forEach { trackId ->
                    add("songIdToAdd" to trackId)
                }
            }
            execute(
                buildRequest(
                    auth = auth,
                    path = "rest/updatePlaylist.view",
                    query = query
                )
            )
            Unit
        }
    }

    suspend fun deletePlaylist(
        auth: NavidromeAuth,
        playlistId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            execute(
                buildRequest(
                    auth = auth,
                    path = "rest/deletePlaylist.view",
                    query = listOf("id" to playlistId)
                )
            )
            Unit
        }
    }

    suspend fun getRadios(auth: NavidromeAuth): Result<List<NavidromeRadioDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = execute(buildRequest(auth, "rest/getInternetRadioStations.view"))
                parseRadios(
                    root.optJSONObject("internetRadioStations")
                        ?.optJSONArray("internetRadioStation")
                )
            }
        }

    suspend fun getArtist(
        auth: NavidromeAuth,
        artistId: String
    ): Result<NavidromeArtistDetailDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getArtist.view",
                    query = mapOf("id" to artistId)
                )
            )
            val artistNode = root.optJSONObject("artist")
                ?: throw IOException("Artist response was empty.")
            NavidromeArtistDetailDto(
                artist = parseArtist(artistNode),
                albums = parseAlbumArray(artistNode.optJSONArray("album"))
            )
        }
    }

    suspend fun getAlbum(
        auth: NavidromeAuth,
        albumId: String
    ): Result<NavidromeAlbumDetailDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getAlbum.view",
                    query = mapOf("id" to albumId)
                )
            )
            val albumNode = root.optJSONObject("album")
                ?: throw IOException("Album response was empty.")
            NavidromeAlbumDetailDto(
                album = parseAlbum(albumNode),
                tracks = parseTrackArray(albumNode.optJSONArray("song"))
            )
        }
    }

    suspend fun search(
        auth: NavidromeAuth,
        query: String,
        artistCount: Int = 8,
        albumCount: Int = 12,
        songCount: Int = 12
    ): Result<NavidromeSearchDto> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/search3.view",
                    query = queryWithMusicFolder(
                        auth,
                        "query" to query,
                        "artistCount" to artistCount.coerceIn(1, 25).toString(),
                        "albumCount" to albumCount.coerceIn(1, 50).toString(),
                        "songCount" to songCount.coerceIn(1, 50).toString()
                    )
                )
            )
            val searchNode = root.optJSONObject("searchResult3")
            NavidromeSearchDto(
                artists = parseArtistItems(searchNode?.optJSONArray("artist")),
                albums = parseAlbumArray(searchNode?.optJSONArray("album")),
                tracks = parseTrackArray(searchNode?.optJSONArray("song"))
            )
        }
    }

    suspend fun getSongs(
        auth: NavidromeAuth,
        query: String = "",
        songCount: Int = 200,
        songOffset: Int = 0
    ): Result<List<NavidromeTrackDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/search3.view",
                    query = queryWithMusicFolder(
                        auth,
                        "query" to query,
                        "songCount" to songCount.coerceIn(1, 500).toString(),
                        "songOffset" to songOffset.coerceAtLeast(0).toString(),
                        "artistCount" to "0",
                        "albumCount" to "0"
                    )
                )
            )
            parseTrackArray(root.optJSONObject("searchResult3")?.optJSONArray("song"))
        }
    }

    suspend fun getLyricsBySongId(
        auth: NavidromeAuth,
        songId: String
    ): Result<List<NavidromeStructuredLyricsDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getLyricsBySongId.view",
                    query = mapOf("id" to songId)
                )
            )
            parseStructuredLyrics(root.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics"))
        }
    }

    suspend fun getLyrics(
        auth: NavidromeAuth,
        artistName: String,
        trackTitle: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getLyrics.view",
                    query = mapOf(
                        "artist" to artistName,
                        "title" to trackTitle
                    )
                )
            )
            parsePlainLyrics(root)
        }
    }

    fun coverArtUrl(auth: NavidromeAuth, coverArtId: String?, size: Int = 600): String? {
        val id = coverArtId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return buildUrl(
            auth = auth,
            path = "rest/getCoverArt.view",
            query = mapOf(
                "id" to id,
                "size" to size.coerceIn(64, 1200).toString()
            )
        ).toString()
    }

    fun streamUrl(auth: NavidromeAuth, trackId: String): String {
        return buildUrl(
            auth = auth,
            path = "rest/stream.view",
            query = mapOf("id" to trackId)
        ).toString()
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    fun encodePassword(password: String): String {
        return password.encodeToByteArray().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private fun execute(request: Request): JSONObject {
        okHttpClient.newCall(request).execute().use { response ->
            val body = decodeNavidromeResponseBody(
                bytes = response.body?.bytes() ?: ByteArray(0),
                declaredCharset = response.body?.contentType()?.charset()
            )
            if (!response.isSuccessful) {
                throw IOException("Navidrome request failed with HTTP ${response.code}.")
            }
            val root = JSONObject(body).optJSONObject("subsonic-response")
                ?: throw IOException("Unexpected Navidrome response.")
            if (!root.optString("status").equals("ok", ignoreCase = true)) {
                val error = root.optJSONObject("error")
                val message = error?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Navidrome request failed."
                throw IOException(message)
            }
            return root
        }
    }

    private fun buildRequest(
        auth: NavidromeAuth,
        path: String,
        query: Map<String, String> = emptyMap()
    ): Request {
        return buildRequest(auth, path, query.entries.map { it.toPair() })
    }

    private fun buildRequest(
        auth: NavidromeAuth,
        path: String,
        query: List<Pair<String, String>>
    ): Request {
        return Request.Builder()
            .url(buildUrl(auth, path, query))
            .get()
            .build()
    }

    private fun buildUrl(
        auth: NavidromeAuth,
        path: String,
        query: Map<String, String> = emptyMap()
    ): HttpUrl {
        return buildUrl(auth, path, query.entries.map { it.toPair() })
    }

    private fun buildUrl(
        auth: NavidromeAuth,
        path: String,
        query: List<Pair<String, String>>
    ): HttpUrl {
        val base = auth.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid Navidrome URL.")
        val builder = base.newBuilder()
        val cleanSegments = path.trim('/').split('/').filter { it.isNotBlank() }
        cleanSegments.forEach(builder::addPathSegment)
        builder.addQueryParameter("u", auth.username)
        builder.addQueryParameter("p", "enc:${auth.encPassword}")
        builder.addQueryParameter("v", API_VERSION)
        builder.addQueryParameter("c", CLIENT_NAME)
        builder.addQueryParameter("f", "json")
        query.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private fun parseArtists(root: JSONObject): List<NavidromeArtistDto> {
        return parseArtistIndices(root.optJSONObject("artists")?.optJSONArray("index"))
    }

    private fun parseArtistIndices(indices: JSONArray?): List<NavidromeArtistDto> {
        if (indices == null) return emptyList()
        val results = mutableListOf<NavidromeArtistDto>()
        repeat(indices.length()) { index ->
            val indexNode = indices.optJSONObject(index) ?: return@repeat
            results += parseArtistItems(indexNode.optJSONArray("artist"))
        }
        return results
    }

    private fun parseArtistItems(items: JSONArray?): List<NavidromeArtistDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeArtistDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            results += parseArtist(item)
        }
        return results
    }

    private fun parseArtist(item: JSONObject): NavidromeArtistDto {
        return NavidromeArtistDto(
            id = item.optString("id"),
            name = item.optString("name").normalizeNavidromeText().ifBlank { "Unknown artist" },
            albumCount = item.optInt("albumCount", 0),
            coverArtId = item.optString("coverArt").ifBlank { null },
            artistImageUrl = item.optString("artistImageUrl").ifBlank { null }
        )
    }

    private fun parseMusicFolders(items: JSONArray?): List<NavidromeMusicFolderDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeMusicFolderDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val id = item.optString("id").trim()
            val name = item.optString("name").normalizeNavidromeText().trim()
            if (id.isNotBlank()) {
                results += NavidromeMusicFolderDto(
                    id = id,
                    name = name.ifBlank { "Library" }
                )
            }
        }
        return results
    }

    private fun parseAlbumArray(items: JSONArray?): List<NavidromeAlbumDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeAlbumDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            results += parseAlbum(item)
        }
        return results
    }

    private fun parseAlbum(item: JSONObject): NavidromeAlbumDto {
        val year = item.takeIf { it.has("year") }?.optInt("year")?.takeIf { it > 0 }
        val duration = item.takeIf { it.has("duration") }?.optInt("duration")?.takeIf { it > 0 }
        val primaryArtistId = item.optJSONArray("artists")
            ?.optJSONObject(0)
            ?.optString("id")
            ?.ifBlank { null }
            ?: item.optString("artistId").ifBlank { null }

        return NavidromeAlbumDto(
            id = item.optString("id"),
            name = item.optString("name").normalizeNavidromeText().ifBlank { "Unknown album" },
            artistName = item.optString("displayArtist").normalizeNavidromeText().ifBlank {
                item.optString("artist").normalizeNavidromeText().ifBlank { "Unknown artist" }
            },
            artistId = primaryArtistId,
            year = year,
            songCount = item.optInt("songCount", 0),
            durationSeconds = duration,
            coverArtId = item.optString("coverArt").ifBlank { null },
            genre = item.optString("genre").normalizeNavidromeText().ifBlank {
                item.optJSONArray("genres")
                    ?.optJSONObject(0)
                    ?.optString("name")
                    ?.normalizeNavidromeText()
                    ?.ifBlank { null }
            }
        )
    }

    private fun parseTrackArray(items: JSONArray?): List<NavidromeTrackDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeTrackDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            results += parseTrack(item)
        }
        return results
    }

    private fun parseTrack(item: JSONObject): NavidromeTrackDto {
        val duration = item.takeIf { it.has("duration") }?.optInt("duration")?.takeIf { it > 0 }
        val trackNumber = item.takeIf { it.has("track") }?.optInt("track")?.takeIf { it > 0 }
        val bitRateKbps = item.takeIf { it.has("bitRate") }?.optInt("bitRate")?.takeIf { it > 0 }
        val sizeBytes = item.takeIf { it.has("size") }?.optLong("size")?.takeIf { it > 0L }
        return NavidromeTrackDto(
            id = item.optString("id"),
            title = item.optString("title").normalizeNavidromeText().ifBlank { "Unknown track" },
            artistName = item.optString("displayArtist").normalizeNavidromeText().ifBlank {
                item.optString("artist").normalizeNavidromeText().ifBlank { "Unknown artist" }
            },
            albumName = item.optString("album").normalizeNavidromeText().ifBlank { "Unknown album" },
            albumId = item.optString("albumId").ifBlank { null },
            artistId = item.optString("artistId").ifBlank { null },
            trackNumber = trackNumber,
            durationSeconds = duration,
            coverArtId = item.optString("coverArt").ifBlank { null },
            suffix = item.optString("suffix").ifBlank { null },
            contentType = item.optString("contentType").ifBlank { null },
            bitRateKbps = bitRateKbps,
            sizeBytes = sizeBytes
        )
    }

    private fun parseStructuredLyrics(items: JSONArray?): List<NavidromeStructuredLyricsDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeStructuredLyricsDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val lines = buildList {
                val sourceLines = item.optJSONArray("line") ?: JSONArray()
                repeat(sourceLines.length()) { lineIndex ->
                    val line = sourceLines.optJSONObject(lineIndex) ?: return@repeat
                    val value = line.optString("value").normalizeNavidromeText().trim()
                    if (value.isBlank()) return@repeat
                    add(
                        NavidromeLyricsLineDto(
                            startMs = line.takeIf { it.has("start") }
                                ?.optInt("start")
                                ?.takeIf { it >= 0 },
                            value = value
                        )
                    )
                }
            }
            if (lines.isEmpty()) return@repeat
            results += NavidromeStructuredLyricsDto(
                synced = item.optBoolean("synced", false),
                offsetMs = item.takeIf { it.has("offset") }?.optInt("offset"),
                lines = lines
            )
        }
        return results
    }

    private fun parsePlainLyrics(root: JSONObject): String? {
        val directNode = root.optJSONObject("lyrics")
        val arrayNode = root.optJSONArray("lyrics")?.optJSONObject(0)
        val value = sequenceOf(directNode, arrayNode)
            .mapNotNull { node ->
                node?.optString("value")
                    ?.normalizeNavidromeText()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: node?.optString("lyrics")
                        ?.normalizeNavidromeText()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
        return value
    }

    private fun parsePlaylist(item: JSONObject): NavidromePlaylistDto {
        return NavidromePlaylistDto(
            id = item.optString("id"),
            name = item.optString("name").normalizeNavidromeText().ifBlank { "Playlist" },
            songCount = item.takeIf { it.has("songCount") }?.optInt("songCount")?.takeIf { it >= 0 },
            durationSeconds = item.takeIf { it.has("duration") }?.optInt("duration")?.takeIf { it >= 0 }
        )
    }

    private fun parseScanStatus(item: JSONObject?): NavidromeScanStatusDto {
        if (item == null) {
            return NavidromeScanStatusDto(scanning = false)
        }
        return NavidromeScanStatusDto(
            scanning = item.optString("scanning").toBooleanStrictOrNull()
                ?: item.optBoolean("scanning", false),
            scannedCount = item.optString("count")
                .toIntOrNull()
                ?: item.takeIf { it.has("count") }?.optInt("count")?.takeIf { it >= 0 },
            folderCount = item.optString("folderCount")
                .toIntOrNull()
                ?: item.takeIf { it.has("folderCount") }?.optInt("folderCount")?.takeIf { it >= 0 },
            lastScanLabel = item.optString("lastScan").trim().ifBlank { null }
        )
    }

    private fun parsePlaylists(items: JSONArray?): List<NavidromePlaylistDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromePlaylistDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            results += parsePlaylist(item)
        }
        return results
    }

    private fun parseRadios(items: JSONArray?): List<NavidromeRadioDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromeRadioDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            val streamUrl = item.optString("streamUrl")
                .ifBlank { item.optString("url") }
                .ifBlank { null }
                ?: return@repeat
            results += NavidromeRadioDto(
                id = item.optString("id"),
                name = item.optString("name").normalizeNavidromeText().ifBlank { "Radio" },
                streamUrl = streamUrl,
                homePageUrl = item.optString("homePageUrl")
                    .ifBlank { item.optString("homepageUrl") }
                    .ifBlank { null }
            )
        }
        return results
    }

    private fun String.normalizeNavidromeText(): String {
        return trim()
            .replace("Â’", "'")
            .replace("Â'", "'")
            .replace("â€™", "'")
            .replace("â€˜", "'")
            .replace("â€œ", "\"")
            .replace("â€�", "\"")
            .replace("Â\"", "\"")
            .replace('\u0091', '\'')
            .replace('\u0092', '\'')
            .replace('\u0093', '"')
            .replace('\u0094', '"')
            .replace(Regex("(?<=[\\p{L}\\p{N}])\uFFFD(?=[\\p{L}\\p{N}])"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "stillshelf"
    }

    private fun queryWithMusicFolder(
        auth: NavidromeAuth,
        vararg pairs: Pair<String, String>
    ): Map<String, String> {
        val query = linkedMapOf<String, String>()
        pairs.forEach { (key, value) ->
            query[key] = value
        }
        auth.musicFolderId?.trim()?.takeIf { it.isNotBlank() }?.let { query["musicFolderId"] = it }
        return query
    }
}
