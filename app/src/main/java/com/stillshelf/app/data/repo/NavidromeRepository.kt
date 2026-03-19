package com.stillshelf.app.data.repo

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.CachedNavidromeHomePayload
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeHome
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeSession
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.api.NavidromeAlbumDetailDto
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.data.api.NavidromeArtistDetailDto
import com.stillshelf.app.data.api.NavidromeAuth
import com.stillshelf.app.data.api.NavidromeRadioDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

enum class NavidromeAlbumSortOption(
    val key: String,
    val label: String
) {
    RECENT("recent", "Recent"),
    ALBUM_TITLE("album_title", "Album title"),
    ALBUM_ARTIST("album_artist", "Album artist"),
    RELEASE_YEAR("release_year", "Release year");

    companion object {
        fun fromKey(value: String?): NavidromeAlbumSortOption {
            return entries.firstOrNull { it.key == value } ?: RECENT
        }
    }
}

@Singleton
class NavidromeRepository @Inject constructor(
    private val navidromeApi: NavidromeApi,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage
) {
    private companion object {
        const val NAVIDROME_PASSWORD_KEY = "navidrome_password"
        private const val HOME_CACHE_MAX_AGE_MS: Long = 10 * 60 * 1000L
        private const val PERSISTED_HOME_CACHE_MAX_AGE_MS: Long = 15 * 60 * 1000L
        private const val CONTENT_CACHE_MAX_AGE_MS: Long = 20 * 60 * 1000L
        private const val DETAIL_CACHE_MAX_AGE_MS: Long = 30 * 60 * 1000L
        private const val SEARCH_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
    }

    private data class TimedCacheEntry<T>(
        val value: T,
        val savedAtMs: Long
    )

    private val cacheMutex = Mutex()
    private val homeCache = mutableMapOf<String, TimedCacheEntry<NavidromeHome>>()
    private val albumsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeAlbum>>>()
    private val artistsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeArtist>>>()
    private val playlistsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromePlaylist>>>()
    private val radiosCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeRadio>>>()
    private val songsCache = mutableMapOf<String, TimedCacheEntry<List<NavidromeTrack>>>()
    private val artistDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeArtistDetail>>()
    private val albumDetailCache = mutableMapOf<String, TimedCacheEntry<NavidromeAlbumDetail>>()
    private val searchCache = mutableMapOf<String, TimedCacheEntry<NavidromeSearchResults>>()

    fun observeSession(): Flow<NavidromeSession?> = sessionPreferences.state.map { state ->
        val baseUrl = state.navidromeBaseUrl?.trim().orEmpty()
        val username = state.navidromeUsername?.trim().orEmpty()
        if (baseUrl.isBlank() || username.isBlank()) {
            null
        } else {
            NavidromeSession(
                baseUrl = baseUrl,
                username = username
            )
        }
    }

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String
    ): AppResult<Unit> {
        val normalizedBaseUrl = navidromeApi.normalizeBaseUrl(baseUrl)
        val normalizedUsername = username.trim()
        if (normalizedBaseUrl.isBlank()) return AppResult.Error("Navidrome URL is required.")
        if (normalizedUsername.isBlank()) return AppResult.Error("Username is required.")
        if (password.isBlank()) return AppResult.Error("Password is required.")

        val ping = navidromeApi.ping(normalizedBaseUrl, normalizedUsername, password)
        if (ping.isSuccess) {
            return try {
                clearCaches()
                sessionPreferences.clearCachedNavidromeHome()
                sessionPreferences.setNavidromeSession(
                    baseUrl = normalizedBaseUrl,
                    username = normalizedUsername
                )
                secureTokenStorage.saveNamedSecret(
                    key = NAVIDROME_PASSWORD_KEY,
                    value = password
                )
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Error("Unable to save Navidrome session.", t)
            }
        }
        return AppResult.Error(
            ping.exceptionOrNull()?.message ?: "Unable to sign in to Navidrome.",
            ping.exceptionOrNull()
        )
    }

    suspend fun signOut() {
        clearCaches()
        sessionPreferences.clearCachedNavidromeHome()
        sessionPreferences.clearNavidromeSession()
        secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
    }

    suspend fun fetchHome(forceRefresh: Boolean = false): AppResult<NavidromeHome> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "home")
        val sessionKey = cacheKey(auth, "persisted-home")
        if (!forceRefresh) {
            getFreshCache(homeCache, cacheKey, HOME_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
            getFreshPersistedHomeSnapshot(sessionKey)?.let { cached ->
                putCache(homeCache, cacheKey, cached)
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) {
            null
        } else {
            getAnyCache(homeCache, cacheKey) ?: getAnyPersistedHomeSnapshot(sessionKey)
        }
        coroutineScope {
            val recentAlbums = async { navidromeApi.getAlbumList(auth, type = "newest", size = 18) }
            val artists = async { navidromeApi.getArtists(auth) }
            val playlists = async { navidromeApi.getPlaylists(auth) }

            val albumResult = recentAlbums.await()
            val artistResult = artists.await()
            val playlistResult = playlists.await()

            if (albumResult.isFailure || artistResult.isFailure || playlistResult.isFailure) {
                staleCache?.let { return@coroutineScope AppResult.Success(it) }
                throw (
                    albumResult.exceptionOrNull()
                        ?: artistResult.exceptionOrNull()
                        ?: playlistResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to load Navidrome home.")
                    )
            }

            val albums = albumResult.getOrThrow()
            val artistItems = artistResult.getOrThrow()
            val playlistItems = playlistResult.getOrThrow()

            val home = NavidromeHome(
                recentAlbums = albums.map { it.toModel(auth) },
                artists = artistItems.map { it.toModel(auth) },
                playlists = playlistItems.map { it.toModel() },
                radios = emptyList()
            )
            putCache(homeCache, cacheKey, home)
            putCache(playlistsCache, cacheKey(auth, "playlists"), home.playlists)
            sessionPreferences.setCachedNavidromeHome(
                sessionKey = sessionKey,
                payload = serializeHome(home),
                savedAtMs = System.currentTimeMillis()
            )

            AppResult.Success(
                home
            )
        }
    }

    suspend fun fetchAlbums(
        sort: NavidromeAlbumSortOption = NavidromeAlbumSortOption.RECENT,
        forceRefresh: Boolean = false
    ): AppResult<List<NavidromeAlbum>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "albums:${sort.key}")
        if (!forceRefresh) {
            getFreshCache(albumsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(albumsCache, cacheKey)
        val apiType = when (sort) {
            NavidromeAlbumSortOption.RECENT -> "newest"
            NavidromeAlbumSortOption.ALBUM_TITLE,
            NavidromeAlbumSortOption.ALBUM_ARTIST,
            NavidromeAlbumSortOption.RELEASE_YEAR -> "alphabeticalByName"
        }
        val result = navidromeApi.getAlbumList(auth, type = apiType, size = 200)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load albums.")
        }
        val albums = result.getOrThrow()
            .map { it.toModel(auth) }

        val sorted = when (sort) {
            NavidromeAlbumSortOption.RECENT -> albums
            NavidromeAlbumSortOption.ALBUM_TITLE -> albums.sortedBy { it.name.lowercase() }
            NavidromeAlbumSortOption.ALBUM_ARTIST -> albums.sortedWith(
                compareBy<NavidromeAlbum> { it.artistName.lowercase() }
                    .thenBy { it.name.lowercase() }
            )

            NavidromeAlbumSortOption.RELEASE_YEAR -> albums.sortedWith(
                compareByDescending<NavidromeAlbum> { it.year ?: Int.MIN_VALUE }
                    .thenBy { it.artistName.lowercase() }
                    .thenBy { it.name.lowercase() }
            )
        }
        putCache(albumsCache, cacheKey, sorted)
        AppResult.Success(sorted)
    }

    suspend fun fetchArtists(forceRefresh: Boolean = false): AppResult<List<NavidromeArtist>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "artists")
        if (!forceRefresh) {
            getFreshCache(artistsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(artistsCache, cacheKey)
        val result = navidromeApi.getArtists(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load artists.")
        }
        val artists = result.getOrThrow()
            .map { it.toModel(auth) }
            .sortedBy { it.name.lowercase() }
        putCache(artistsCache, cacheKey, artists)
        AppResult.Success(artists)
    }

    suspend fun fetchPlaylists(forceRefresh: Boolean = false): AppResult<List<NavidromePlaylist>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "playlists")
        if (!forceRefresh) {
            getFreshCache(playlistsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(playlistsCache, cacheKey)
        val result = navidromeApi.getPlaylists(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load playlists.")
        }
        val playlists = result.getOrThrow().map { it.toModel() }
        putCache(playlistsCache, cacheKey, playlists)
        AppResult.Success(playlists)
    }

    suspend fun fetchRadios(forceRefresh: Boolean = false): AppResult<List<NavidromeRadio>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "radios")
        if (!forceRefresh) {
            getFreshCache(radiosCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(radiosCache, cacheKey)
        val result = navidromeApi.getRadios(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load radios.")
        }
        val radios = result.getOrThrow()
            .map { it.toModel() }
            .sortedBy { it.name.lowercase() }
        putCache(radiosCache, cacheKey, radios)
        AppResult.Success(radios)
    }

    suspend fun fetchSongs(forceRefresh: Boolean = false): AppResult<List<NavidromeTrack>> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "songs")
        if (!forceRefresh) {
            getFreshCache(songsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(songsCache, cacheKey)
        val result = navidromeApi.getSongs(auth)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load songs.")
        }
        val songs = result.getOrThrow()
            .map { it.toModel(auth) }
            .sortedWith(
                compareBy<NavidromeTrack> { it.artistName.lowercase() }
                    .thenBy { it.albumName.lowercase() }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        putCache(songsCache, cacheKey, songs)
        AppResult.Success(songs)
    }

    suspend fun fetchArtistDetail(
        artistId: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeArtistDetail> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "artist:$artistId")
        if (!forceRefresh) {
            getFreshCache(artistDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(artistDetailCache, cacheKey)
        val result = navidromeApi.getArtist(auth, artistId)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load artist.")
        }
        val detail = result.getOrThrow().toModel(auth)
        putCache(artistDetailCache, cacheKey, detail)
        AppResult.Success(detail)
    }

    suspend fun fetchAlbumDetail(
        albumId: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeAlbumDetail> = withAuth { auth ->
        val cacheKey = cacheKey(auth, "album:$albumId")
        if (!forceRefresh) {
            getFreshCache(albumDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(albumDetailCache, cacheKey)
        val result = navidromeApi.getAlbum(auth, albumId)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to load album.")
        }
        val detail = result.getOrThrow().toModel(auth)
        putCache(albumDetailCache, cacheKey, detail)
        AppResult.Success(detail)
    }

    suspend fun search(
        query: String,
        forceRefresh: Boolean = false
    ): AppResult<NavidromeSearchResults> = withAuth { auth ->
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return@withAuth AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
        }
        val cacheKey = cacheKey(auth, "search:${normalizedQuery.lowercase()}")
        if (!forceRefresh) {
            getFreshCache(searchCache, cacheKey, SEARCH_CACHE_MAX_AGE_MS)?.let { cached ->
                return@withAuth AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(searchCache, cacheKey)
        val result = navidromeApi.search(auth, normalizedQuery)
        if (result.isFailure) {
            staleCache?.let { return@withAuth AppResult.Success(it) }
            throw result.exceptionOrNull() ?: IllegalStateException("Unable to search Navidrome.")
        }
        val apiResults = result.getOrThrow()
        val results = NavidromeSearchResults(
            artists = apiResults.artists.map { it.toModel(auth) },
            albums = apiResults.albums.map { it.toModel(auth) },
            tracks = apiResults.tracks.map { it.toModel(auth) }
        )
        putCache(searchCache, cacheKey, results)
        AppResult.Success(results)
    }

    private suspend fun currentAuth(): NavidromeAuth? {
        val state = sessionPreferences.state.first()
        val baseUrl = state.navidromeBaseUrl?.trim().orEmpty()
        val username = state.navidromeUsername?.trim().orEmpty()
        if (baseUrl.isBlank() || username.isBlank()) return null
        val password = secureTokenStorage.getNamedSecret(NAVIDROME_PASSWORD_KEY)
            ?.trim()
            .orEmpty()
        if (password.isBlank()) return null
        return NavidromeAuth(
            baseUrl = baseUrl,
            username = username,
            encPassword = navidromeApi.encodePassword(password)
        )
    }

    private suspend fun <T> withAuth(
        block: suspend (NavidromeAuth) -> AppResult<T>
    ): AppResult<T> {
        val auth = currentAuth() ?: return AppResult.Error("Navidrome session expired. Please sign in again.")
        return try {
            block(auth)
        } catch (t: Throwable) {
            AppResult.Error(t.message ?: "Navidrome request failed.", t)
        }
    }

    private fun cacheKey(auth: NavidromeAuth, suffix: String): String {
        return "${auth.baseUrl.lowercase()}|${auth.username.lowercase()}|$suffix"
    }

    private suspend fun <T> getFreshCache(
        source: Map<String, TimedCacheEntry<T>>,
        key: String,
        maxAgeMs: Long
    ): T? = cacheMutex.withLock {
        val entry = source[key] ?: return@withLock null
        if ((System.currentTimeMillis() - entry.savedAtMs) <= maxAgeMs) {
            entry.value
        } else {
            null
        }
    }

    private suspend fun <T> getAnyCache(
        source: Map<String, TimedCacheEntry<T>>,
        key: String
    ): T? = cacheMutex.withLock {
        source[key]?.value
    }

    private suspend fun <T> putCache(
        destination: MutableMap<String, TimedCacheEntry<T>>,
        key: String,
        value: T
    ) {
        cacheMutex.withLock {
            destination[key] = TimedCacheEntry(
                value = value,
                savedAtMs = System.currentTimeMillis()
            )
        }
    }

    private suspend fun clearCaches() {
        cacheMutex.withLock {
            homeCache.clear()
            albumsCache.clear()
            artistsCache.clear()
            playlistsCache.clear()
            radiosCache.clear()
            songsCache.clear()
            artistDetailCache.clear()
            albumDetailCache.clear()
            searchCache.clear()
        }
    }

    private suspend fun getFreshPersistedHomeSnapshot(sessionKey: String): NavidromeHome? {
        val cached = sessionPreferences.getCachedNavidromeHome() ?: return null
        if (cached.sessionKey != sessionKey) return null
        val ageMs = (System.currentTimeMillis() - cached.savedAtMs).coerceAtLeast(0L)
        if (ageMs > PERSISTED_HOME_CACHE_MAX_AGE_MS) return null
        return parseHome(cached)
    }

    private suspend fun getAnyPersistedHomeSnapshot(sessionKey: String): NavidromeHome? {
        val cached = sessionPreferences.getCachedNavidromeHome() ?: return null
        if (cached.sessionKey != sessionKey) return null
        return parseHome(cached)
    }

    private fun serializeHome(home: NavidromeHome): String {
        return JSONObject()
            .put("recentAlbums", JSONArray().apply {
                home.recentAlbums.forEach { album ->
                    put(
                        JSONObject()
                            .put("id", album.id)
                            .put("name", album.name)
                            .put("artistName", album.artistName)
                            .put("artistId", album.artistId)
                            .put("year", album.year)
                            .put("songCount", album.songCount)
                            .put("durationSeconds", album.durationSeconds)
                            .put("coverUrl", album.coverUrl)
                            .put("genre", album.genre)
                    )
                }
            })
            .put("artists", JSONArray().apply {
                home.artists.forEach { artist ->
                    put(
                        JSONObject()
                            .put("id", artist.id)
                            .put("name", artist.name)
                            .put("albumCount", artist.albumCount)
                            .put("coverUrl", artist.coverUrl)
                            .put("imageUrl", artist.imageUrl)
                    )
                }
            })
            .put("playlists", JSONArray().apply {
                home.playlists.forEach { playlist ->
                    put(
                        JSONObject()
                            .put("id", playlist.id)
                            .put("name", playlist.name)
                            .put("songCount", playlist.songCount)
                            .put("durationSeconds", playlist.durationSeconds)
                    )
                }
            })
            .put("radios", JSONArray().apply {
                home.radios.forEach { radio ->
                    put(
                        JSONObject()
                            .put("id", radio.id)
                            .put("name", radio.name)
                            .put("streamUrl", radio.streamUrl)
                            .put("homePageUrl", radio.homePageUrl)
                    )
                }
            })
            .toString()
    }

    private fun parseHome(cached: CachedNavidromeHomePayload): NavidromeHome? {
        val root = runCatching { org.json.JSONObject(cached.payload) }.getOrNull() ?: return null
        return NavidromeHome(
            recentAlbums = buildList {
                val source = root.optJSONArray("recentAlbums") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromeAlbum(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            artistName = item.optString("artistName"),
                            artistId = item.optString("artistId").ifBlank { null },
                            year = item.optInt("year").takeIf { item.has("year") && !item.isNull("year") },
                            songCount = item.optInt("songCount"),
                            durationSeconds = item.optInt("durationSeconds").takeIf {
                                item.has("durationSeconds") && !item.isNull("durationSeconds")
                            },
                            coverUrl = item.optString("coverUrl").ifBlank { null },
                            genre = item.optString("genre").ifBlank { null }
                        )
                    )
                }
            },
            artists = buildList {
                val source = root.optJSONArray("artists") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromeArtist(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            albumCount = item.optInt("albumCount"),
                            coverUrl = item.optString("coverUrl").ifBlank { null },
                            imageUrl = item.optString("imageUrl").ifBlank { null }
                        )
                    )
                }
            },
            playlists = buildList {
                val source = root.optJSONArray("playlists") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    add(
                        NavidromePlaylist(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            songCount = item.optInt("songCount").takeIf {
                                item.has("songCount") && !item.isNull("songCount")
                            },
                            durationSeconds = item.optInt("durationSeconds").takeIf {
                                item.has("durationSeconds") && !item.isNull("durationSeconds")
                            }
                        )
                    )
                }
            },
            radios = buildList {
                val source = root.optJSONArray("radios") ?: JSONArray()
                for (index in 0 until source.length()) {
                    val item = source.optJSONObject(index) ?: continue
                    val streamUrl = item.optString("streamUrl")
                        .ifBlank { item.optString("url") }
                        .ifBlank { null }
                        ?: continue
                    add(
                        NavidromeRadio(
                            id = item.optString("id"),
                            name = item.optString("name").ifBlank { "Radio" },
                            streamUrl = streamUrl,
                            homePageUrl = item.optString("homePageUrl")
                                .ifBlank { item.optString("homepageUrl") }
                                .ifBlank { null }
                        )
                    )
                }
            }
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeArtistDto.toModel(
        auth: NavidromeAuth
    ): NavidromeArtist {
        return NavidromeArtist(
            id = id,
            name = name,
            albumCount = albumCount,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 400),
            imageUrl = artistImageUrl
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeAlbumDto.toModel(
        auth: NavidromeAuth
    ): NavidromeAlbum {
        return NavidromeAlbum(
            id = id,
            name = name,
            artistName = artistName,
            artistId = artistId,
            year = year,
            songCount = songCount,
            durationSeconds = durationSeconds,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 600),
            genre = genre
        )
    }

    private fun com.stillshelf.app.data.api.NavidromeTrackDto.toModel(
        auth: NavidromeAuth
    ): NavidromeTrack {
        return NavidromeTrack(
            id = id,
            title = title,
            artistName = artistName,
            albumName = albumName,
            albumId = albumId,
            artistId = artistId,
            trackNumber = trackNumber,
            durationSeconds = durationSeconds,
            coverUrl = navidromeApi.coverArtUrl(auth, coverArtId, size = 400),
            streamUrl = navidromeApi.streamUrl(auth, id),
            formatLabel = suffix?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
                ?: contentType
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.uppercase(),
            bitRateKbps = bitRateKbps
        )
    }

    private fun com.stillshelf.app.data.api.NavidromePlaylistDto.toModel(): NavidromePlaylist {
        return NavidromePlaylist(
            id = id,
            name = name,
            songCount = songCount,
            durationSeconds = durationSeconds
        )
    }

    private fun NavidromeRadioDto.toModel(): NavidromeRadio {
        return NavidromeRadio(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl
        )
    }

    private fun NavidromeArtistDetailDto.toModel(auth: NavidromeAuth): NavidromeArtistDetail {
        return NavidromeArtistDetail(
            artist = artist.toModel(auth),
            albums = albums.map { it.toModel(auth) }
        )
    }

    private fun NavidromeAlbumDetailDto.toModel(auth: NavidromeAuth): NavidromeAlbumDetail {
        return NavidromeAlbumDetail(
            album = album.toModel(auth),
            tracks = tracks.map { it.toModel(auth) }
        )
    }
}
