package com.stillshelf.app.data.repo

import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeHome
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeSession
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.api.NavidromeAlbumDetailDto
import com.stillshelf.app.data.api.NavidromeApi
import com.stillshelf.app.data.api.NavidromeArtistDetailDto
import com.stillshelf.app.data.api.NavidromeAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
        sessionPreferences.clearNavidromeSession()
        secureTokenStorage.clearNamedSecret(NAVIDROME_PASSWORD_KEY)
    }

    suspend fun fetchHome(): AppResult<NavidromeHome> = withAuth { auth ->
        coroutineScope {
            val recentAlbums = async { navidromeApi.getAlbumList(auth, type = "newest", size = 18) }
            val artists = async { navidromeApi.getArtists(auth) }
            val playlists = async { navidromeApi.getPlaylists(auth) }

            val albumResult = recentAlbums.await()
            val artistResult = artists.await()
            val playlistResult = playlists.await()

            val albums = albumResult.getOrElse { throw it }
            val artistItems = artistResult.getOrElse { throw it }
            val playlistItems = playlistResult.getOrElse { throw it }

            AppResult.Success(
                NavidromeHome(
                    recentAlbums = albums.map { it.toModel(auth) },
                    artists = artistItems.map { it.toModel(auth) },
                    playlists = playlistItems.map { it.toModel() }
                )
            )
        }
    }

    suspend fun fetchAlbums(
        sort: NavidromeAlbumSortOption = NavidromeAlbumSortOption.RECENT
    ): AppResult<List<NavidromeAlbum>> = withAuth { auth ->
        val apiType = when (sort) {
            NavidromeAlbumSortOption.RECENT -> "newest"
            NavidromeAlbumSortOption.ALBUM_TITLE,
            NavidromeAlbumSortOption.ALBUM_ARTIST,
            NavidromeAlbumSortOption.RELEASE_YEAR -> "alphabeticalByName"
        }
        val albums = navidromeApi.getAlbumList(auth, type = apiType, size = 200)
            .getOrElse { throw it }
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
        AppResult.Success(sorted)
    }

    suspend fun fetchArtists(): AppResult<List<NavidromeArtist>> = withAuth { auth ->
        val artists = navidromeApi.getArtists(auth)
            .getOrElse { throw it }
            .map { it.toModel(auth) }
            .sortedBy { it.name.lowercase() }
        AppResult.Success(artists)
    }

    suspend fun fetchArtistDetail(artistId: String): AppResult<NavidromeArtistDetail> = withAuth { auth ->
        val detail = navidromeApi.getArtist(auth, artistId)
            .getOrElse { throw it }
        AppResult.Success(detail.toModel(auth))
    }

    suspend fun fetchAlbumDetail(albumId: String): AppResult<NavidromeAlbumDetail> = withAuth { auth ->
        val detail = navidromeApi.getAlbum(auth, albumId)
            .getOrElse { throw it }
        AppResult.Success(detail.toModel(auth))
    }

    suspend fun search(query: String): AppResult<NavidromeSearchResults> = withAuth { auth ->
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return@withAuth AppResult.Success(NavidromeSearchResults(emptyList(), emptyList(), emptyList()))
        }
        val results = navidromeApi.search(auth, normalizedQuery)
            .getOrElse { throw it }
        AppResult.Success(
            NavidromeSearchResults(
                artists = results.artists.map { it.toModel(auth) },
                albums = results.albums.map { it.toModel(auth) },
                tracks = results.tracks.map { it.toModel(auth) }
            )
        )
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
            streamUrl = navidromeApi.streamUrl(auth, id)
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

    private companion object {
        const val NAVIDROME_PASSWORD_KEY = "navidrome_password"
    }
}
