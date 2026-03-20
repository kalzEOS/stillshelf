package com.stillshelf.app.data.api

import java.io.IOException
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
    val bitRateKbps: Int?
)

data class NavidromePlaylistDto(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val durationSeconds: Int? = null
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

    suspend fun getAlbumList(
        auth: NavidromeAuth,
        type: String,
        size: Int = 24
    ): Result<List<NavidromeAlbumDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = execute(
                buildRequest(
                    auth = auth,
                    path = "rest/getAlbumList2.view",
                    query = queryWithMusicFolder(
                        auth,
                        "type" to type,
                        "size" to size.coerceIn(1, 100).toString()
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
            val body = response.body?.string().orEmpty()
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
            name = item.optString("name").ifBlank { "Unknown artist" },
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
            val name = item.optString("name").trim()
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
            name = item.optString("name").ifBlank { "Unknown album" },
            artistName = item.optString("displayArtist").ifBlank {
                item.optString("artist").ifBlank { "Unknown artist" }
            },
            artistId = primaryArtistId,
            year = year,
            songCount = item.optInt("songCount", 0),
            durationSeconds = duration,
            coverArtId = item.optString("coverArt").ifBlank { null },
            genre = item.optString("genre").ifBlank {
                item.optJSONArray("genres")
                    ?.optJSONObject(0)
                    ?.optString("name")
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
        return NavidromeTrackDto(
            id = item.optString("id"),
            title = item.optString("title").ifBlank { "Unknown track" },
            artistName = item.optString("displayArtist").ifBlank {
                item.optString("artist").ifBlank { "Unknown artist" }
            },
            albumName = item.optString("album").ifBlank { "Unknown album" },
            albumId = item.optString("albumId").ifBlank { null },
            artistId = item.optString("artistId").ifBlank { null },
            trackNumber = trackNumber,
            durationSeconds = duration,
            coverArtId = item.optString("coverArt").ifBlank { null },
            suffix = item.optString("suffix").ifBlank { null },
            contentType = item.optString("contentType").ifBlank { null },
            bitRateKbps = bitRateKbps
        )
    }

    private fun parsePlaylists(items: JSONArray?): List<NavidromePlaylistDto> {
        if (items == null) return emptyList()
        val results = mutableListOf<NavidromePlaylistDto>()
        repeat(items.length()) { index ->
            val item = items.optJSONObject(index) ?: return@repeat
            results += NavidromePlaylistDto(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { "Playlist" },
                songCount = item.takeIf { it.has("songCount") }?.optInt("songCount")?.takeIf { it >= 0 },
                durationSeconds = item.takeIf { it.has("duration") }?.optInt("duration")?.takeIf { it >= 0 }
            )
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
                name = item.optString("name").ifBlank { "Radio" },
                streamUrl = streamUrl,
                homePageUrl = item.optString("homePageUrl")
                    .ifBlank { item.optString("homepageUrl") }
                    .ifBlank { null }
            )
        }
        return results
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
