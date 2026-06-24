package com.stillshelf.app.ui.navigation

import android.net.Uri

object GraphRoute {
    const val BACKEND_SELECTOR = "backend_selector"
    const val AUTH = "auth_graph"
    const val MAIN = "main_graph"
    const val NAVIDROME_AUTH = "navidrome_auth"
    const val NAVIDROME = "navidrome_graph"
}

object AuthRoute {
    const val SERVERS = "auth/servers"
    const val ADD_SERVER = "auth/add_server"
    const val LIBRARY_PICKER = "auth/library_picker"
    const val SERVER_NAME_ARG = "serverName"
    const val BASE_URL_ARG = "baseUrl"

    const val LOGIN_PATTERN =
        "auth/login?serverName={$SERVER_NAME_ARG}&baseUrl={$BASE_URL_ARG}"

    fun loginRoute(serverName: String, baseUrl: String): String {
        return "auth/login?serverName=${Uri.encode(serverName)}&baseUrl=${Uri.encode(baseUrl)}"
    }
}

object MainRoute {
    const val SHELL = "main/shell"
    const val CUSTOMIZE = "main/customize"
    const val SETTINGS = "main/settings"
    const val ABOUT = "main/settings/about"
    const val ADVANCED = "main/settings/advanced"
    const val PODCAST_SETTINGS = "main/settings/podcasts"
    const val PODCAST_SHOW_ID_ARG = "podcastShowId"
    const val PODCAST_SHOW_PATTERN = "main/podcast/show/{$PODCAST_SHOW_ID_ARG}"

    fun podcastShow(showId: String): String = "main/podcast/show/${android.net.Uri.encode(showId)}"
    const val PLAYER = "main/player"
    const val PLAYER_BOOK_ID_ARG = "bookId"
    const val PLAYER_START_SECONDS_ARG = "startSeconds"
    const val PLAYER_PATTERN =
        "$PLAYER?$PLAYER_BOOK_ID_ARG={$PLAYER_BOOK_ID_ARG}&$PLAYER_START_SECONDS_ARG={$PLAYER_START_SECONDS_ARG}"
    const val SERVERS = "main/servers"
    const val LIBRARY_PICKER = "main/library_picker"

    fun player(bookId: String? = null, startSeconds: Double? = null): String {
        if (bookId.isNullOrBlank()) return PLAYER
        val encodedBookId = Uri.encode(bookId)
        val encodedStart = startSeconds?.let { Uri.encode(it.toString()) }
        return if (encodedStart.isNullOrBlank()) {
            "$PLAYER?$PLAYER_BOOK_ID_ARG=$encodedBookId"
        } else {
            "$PLAYER?$PLAYER_BOOK_ID_ARG=$encodedBookId&$PLAYER_START_SECONDS_ARG=$encodedStart"
        }
    }
}

object BrowseRoute {
    const val BOOKS = "main/browse/books"
    const val AUTHORS = "main/browse/authors"
    const val NARRATORS = "main/browse/narrators"
    const val SERIES = "main/browse/series"
    const val COLLECTIONS = "main/browse/collections"
    const val GENRES = "main/browse/genres"
    const val BOOKMARKS = "main/browse/bookmarks"
    const val PLAYLISTS = "main/browse/playlists"
    const val DOWNLOADED = "main/browse/downloaded"
    const val PODCASTS = "main/browse/podcasts"
}

object DetailRoute {
    const val BOOK_ID_ARG = "bookId"
    const val AUTHOR_NAME_ARG = "authorName"
    const val NARRATOR_NAME_ARG = "narratorName"
    const val SERIES_NAME_ARG = "seriesName"
    const val SERIES_ID_ARG = "seriesId"
    const val GENRE_NAME_ARG = "genreName"
    const val COLLECTION_ID_ARG = "collectionId"
    const val COLLECTION_NAME_ARG = "collectionName"
    const val PLAYLIST_ID_ARG = "playlistId"
    const val PLAYLIST_NAME_ARG = "playlistName"
    const val BOOK_PATTERN = "main/book/{$BOOK_ID_ARG}"
    const val AUTHOR_PATTERN = "main/author/{$AUTHOR_NAME_ARG}"
    const val NARRATOR_PATTERN = "main/narrator/{$NARRATOR_NAME_ARG}"
    const val SERIES_PATTERN = "main/series/{$SERIES_NAME_ARG}?$SERIES_ID_ARG={$SERIES_ID_ARG}"
    const val SUBSERIES_PATTERN = "main/subseries/{$SERIES_NAME_ARG}?$SERIES_ID_ARG={$SERIES_ID_ARG}"
    const val GENRE_PATTERN = "main/genre/{$GENRE_NAME_ARG}"
    const val COLLECTION_PATTERN =
        "main/collection/{$COLLECTION_ID_ARG}?$COLLECTION_NAME_ARG={$COLLECTION_NAME_ARG}"
    const val PLAYLIST_PATTERN =
        "main/playlist/{$PLAYLIST_ID_ARG}?$PLAYLIST_NAME_ARG={$PLAYLIST_NAME_ARG}"

    fun book(bookId: String): String = "main/book/${Uri.encode(bookId)}"
    fun author(name: String): String = "main/author/${Uri.encode(name)}"
    fun narrator(name: String): String = "main/narrator/${Uri.encode(name)}"
    fun series(name: String, id: String? = null): String {
        val encodedName = Uri.encode(name)
        val encodedId = id?.trim()?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encodedId == null) {
            "main/series/$encodedName"
        } else {
            "main/series/$encodedName?$SERIES_ID_ARG=$encodedId"
        }
    }

    fun subseries(name: String, id: String? = null): String {
        val encodedName = Uri.encode(name)
        val encodedId = id?.trim()?.takeIf { it.isNotBlank() }?.let(Uri::encode)
        return if (encodedId == null) {
            "main/subseries/$encodedName"
        } else {
            "main/subseries/$encodedName?$SERIES_ID_ARG=$encodedId"
        }
    }
    fun genre(name: String): String = "main/genre/${Uri.encode(name)}"
    fun collection(id: String, name: String): String {
        return "main/collection/${Uri.encode(id)}?$COLLECTION_NAME_ARG=${Uri.encode(name)}"
    }

    fun playlist(id: String, name: String): String {
        return "main/playlist/${Uri.encode(id)}?$PLAYLIST_NAME_ARG=${Uri.encode(name)}"
    }
}

enum class MainTab(val route: String, val label: String) {
    Home("main/tab/home", "Home"),
    Browse("main/tab/browse", "Browse"),
    Search("main/tab/search", "Search"),
    Downloads("main/tab/downloads", "Downloads"),
    Settings("main/tab/settings", "Settings");

    companion object {
        fun fromRoute(route: String?): MainTab {
            return entries.firstOrNull { it.route == route } ?: Home
        }
    }
}

internal fun resolveSafeScreenArea(route: String?): String {
    val rawRoute = route?.trim().orEmpty()
    if (rawRoute.isBlank()) return "unknown"

    return when {
        rawRoute == GraphRoute.BACKEND_SELECTOR -> "backend_selector"
        rawRoute == GraphRoute.AUTH -> "auth_graph"
        rawRoute == GraphRoute.MAIN -> "main_graph"
        rawRoute == GraphRoute.NAVIDROME_AUTH -> "navidrome_auth"
        rawRoute == GraphRoute.NAVIDROME -> "navidrome_graph"

        rawRoute == MainRoute.SHELL -> "main_shell"
        rawRoute == MainRoute.CUSTOMIZE -> "customize"
        rawRoute == MainRoute.SETTINGS -> "settings"
        rawRoute == MainRoute.ABOUT -> "about"
        rawRoute == MainRoute.ADVANCED -> "advanced"
        rawRoute == MainRoute.PODCAST_SETTINGS -> "podcast_settings"
        rawRoute.startsWith("main/podcast/show/") -> "podcast_show_detail"
        rawRoute == MainRoute.PLAYER || rawRoute.startsWith("main/player") -> "player"
        rawRoute == MainRoute.SERVERS -> "servers"
        rawRoute == MainRoute.LIBRARY_PICKER -> "library_picker"

        rawRoute == MainTab.Home.route -> "home"
        rawRoute == MainTab.Browse.route -> "browse"
        rawRoute == MainTab.Search.route -> "search"
        rawRoute == MainTab.Downloads.route -> "downloads"
        rawRoute == MainTab.Settings.route -> "settings_tab"

        rawRoute == BrowseRoute.BOOKS -> "browse_books"
        rawRoute == BrowseRoute.AUTHORS -> "browse_authors"
        rawRoute == BrowseRoute.NARRATORS -> "browse_narrators"
        rawRoute == BrowseRoute.SERIES -> "browse_series"
        rawRoute == BrowseRoute.COLLECTIONS -> "browse_collections"
        rawRoute == BrowseRoute.GENRES -> "browse_genres"
        rawRoute == BrowseRoute.BOOKMARKS -> "browse_bookmarks"
        rawRoute == BrowseRoute.PLAYLISTS -> "browse_playlists"
        rawRoute == BrowseRoute.DOWNLOADED -> "browse_downloaded"
        rawRoute == BrowseRoute.PODCASTS -> "browse_podcasts"

        rawRoute == AuthRoute.SERVERS -> "auth_servers"
        rawRoute == AuthRoute.ADD_SERVER -> "auth_add_server"
        rawRoute == AuthRoute.LIBRARY_PICKER -> "auth_library_picker"
        rawRoute.startsWith("auth/login") -> "auth_login"

        rawRoute == NavidromeRoute.LOGIN -> "navidrome_login"
        rawRoute == NavidromeRoute.HOME -> "navidrome_home"
        rawRoute == NavidromeRoute.LIBRARY -> "navidrome_library"
        rawRoute == NavidromeRoute.ARTISTS -> "navidrome_artists"
        rawRoute == NavidromeRoute.ALBUMS -> "navidrome_albums"
        rawRoute == NavidromeRoute.NEWEST_ALBUMS -> "navidrome_newest_albums"
        rawRoute == NavidromeRoute.RADIOS -> "navidrome_radios"
        rawRoute == NavidromeRoute.MUSIC_RADIO -> "navidrome_music_radio"
        rawRoute == NavidromeRoute.SONGS -> "navidrome_songs"
        rawRoute == NavidromeRoute.DOWNLOADED -> "navidrome_downloaded"
        rawRoute == NavidromeRoute.FAVORITES -> "navidrome_favorites"
        rawRoute == NavidromeRoute.PLAYLISTS -> "navidrome_playlists"
        rawRoute == NavidromeRoute.SEARCH -> "navidrome_search"
        rawRoute == NavidromeRoute.SETTINGS -> "navidrome_settings"
        rawRoute == NavidromeRoute.EQUALIZER -> "navidrome_equalizer"
        rawRoute == NavidromeRoute.LYRICS_SOURCES -> "navidrome_lyrics_sources"
        rawRoute == NavidromeRoute.SERVERS -> "navidrome_servers"
        rawRoute == NavidromeRoute.ADVANCED -> "navidrome_advanced"
        rawRoute == NavidromeRoute.ABOUT -> "navidrome_about"
        rawRoute == NavidromeRoute.CUSTOMIZE -> "navidrome_customize"
        rawRoute.startsWith("navidrome/playlist/") -> "navidrome_playlist_detail"
        rawRoute.startsWith("navidrome/artist/") -> "navidrome_artist_detail"
        rawRoute.startsWith("navidrome/album/") -> "navidrome_album_detail"

        rawRoute.startsWith("main/browse/") -> "browse_area"
        rawRoute.startsWith("main/book/") -> "book_detail"
        rawRoute.startsWith("main/author/") -> "author_detail"
        rawRoute.startsWith("main/narrator/") -> "narrator_detail"
        rawRoute.startsWith("main/series/") || rawRoute.startsWith("main/subseries/") -> "series_detail"
        rawRoute.startsWith("main/genre/") -> "genre_detail"
        rawRoute.startsWith("main/collection/") -> "collection_detail"
        rawRoute.startsWith("main/playlist/") -> "playlist_detail"
        rawRoute.startsWith("main/") -> "main_area"

        else -> "unknown"
    }
}

object NavidromeRoute {
    const val LOGIN = "navidrome/login"
    const val HOME = "navidrome/home"
    const val LIBRARY = "navidrome/library"
    const val ARTISTS = "navidrome/library/artists"
    const val ALBUMS = "navidrome/library/albums"
    const val NEWEST_ALBUMS = "navidrome/library/albums/newest"
    const val RADIOS = "navidrome/library/radios"
    const val MUSIC_RADIO = "navidrome/library/music-radio"
    const val SONGS = "navidrome/library/songs"
    const val DOWNLOADED = "navidrome/library/downloaded"
    const val FAVORITES = "navidrome/library/favorites"
    const val PLAYLISTS = "navidrome/library/playlists"
    const val PLAYLIST_ID_ARG = "playlistId"
    const val SEARCH = "navidrome/search"
    const val SETTINGS = "navidrome/settings"
    const val EQUALIZER = "navidrome/settings/equalizer"
    const val LYRICS_SOURCES = "navidrome/settings/lyrics-sources"
    const val SERVERS = "navidrome/settings/servers"
    const val ADVANCED = "navidrome/settings/advanced"
    const val ABOUT = "navidrome/settings/about"
    const val CUSTOMIZE = "navidrome/customize"
    const val ARTIST_ID_ARG = "artistId"
    const val ALBUM_ID_ARG = "albumId"
    const val PLAYLIST_PATTERN = "navidrome/playlist/{$PLAYLIST_ID_ARG}"
    const val ARTIST_PATTERN = "navidrome/artist/{$ARTIST_ID_ARG}"
    const val ALBUM_PATTERN = "navidrome/album/{$ALBUM_ID_ARG}"

    fun playlist(playlistId: String): String = "navidrome/playlist/${Uri.encode(playlistId)}"

    fun artist(artistId: String): String = "navidrome/artist/${Uri.encode(artistId)}"

    fun album(albumId: String): String = "navidrome/album/${Uri.encode(albumId)}"
}
