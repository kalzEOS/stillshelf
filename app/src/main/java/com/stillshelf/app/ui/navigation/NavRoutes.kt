package com.stillshelf.app.ui.navigation

import android.net.Uri

object GraphRoute {
    const val AUTH = "auth_graph"
    const val MAIN = "main_graph"
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
    const val PLAYER = "main/player"
    const val PLAYER_BOOK_ID_ARG = "bookId"
    const val PLAYER_START_SECONDS_ARG = "startSeconds"
    const val PLAYER_PATTERN =
        "$PLAYER?$PLAYER_BOOK_ID_ARG={$PLAYER_BOOK_ID_ARG}&$PLAYER_START_SECONDS_ARG={$PLAYER_START_SECONDS_ARG}"
    const val SERVERS = "main/servers"

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
}

object DetailRoute {
    const val BOOK_ID_ARG = "bookId"
    const val AUTHOR_NAME_ARG = "authorName"
    const val NARRATOR_NAME_ARG = "narratorName"
    const val SERIES_NAME_ARG = "seriesName"
    const val GENRE_NAME_ARG = "genreName"
    const val COLLECTION_ID_ARG = "collectionId"
    const val COLLECTION_NAME_ARG = "collectionName"
    const val PLAYLIST_ID_ARG = "playlistId"
    const val PLAYLIST_NAME_ARG = "playlistName"
    const val BOOK_PATTERN = "main/book/{$BOOK_ID_ARG}"
    const val AUTHOR_PATTERN = "main/author/{$AUTHOR_NAME_ARG}"
    const val NARRATOR_PATTERN = "main/narrator/{$NARRATOR_NAME_ARG}"
    const val SERIES_PATTERN = "main/series/{$SERIES_NAME_ARG}"
    const val GENRE_PATTERN = "main/genre/{$GENRE_NAME_ARG}"
    const val COLLECTION_PATTERN =
        "main/collection/{$COLLECTION_ID_ARG}?$COLLECTION_NAME_ARG={$COLLECTION_NAME_ARG}"
    const val PLAYLIST_PATTERN =
        "main/playlist/{$PLAYLIST_ID_ARG}?$PLAYLIST_NAME_ARG={$PLAYLIST_NAME_ARG}"

    fun book(bookId: String): String = "main/book/${Uri.encode(bookId)}"
    fun author(name: String): String = "main/author/${Uri.encode(name)}"
    fun narrator(name: String): String = "main/narrator/${Uri.encode(name)}"
    fun series(name: String): String = "main/series/${Uri.encode(name)}"
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
