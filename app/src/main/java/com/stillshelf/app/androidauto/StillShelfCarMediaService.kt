package com.stillshelf.app.androidauto

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import com.stillshelf.app.MainActivity
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.datastore.SessionPreferenceState
import com.stillshelf.app.core.model.BackendProvider
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.playback.navidrome.NavidromePlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StillShelfCarMediaService : MediaBrowserServiceCompat() {
    private data class CarSessionSnapshot(
        val metadata: MediaMetadataCompat?,
        val playbackState: PlaybackStateCompat,
        val queue: List<MediaSessionCompat.QueueItem>? = null,
        val queueTitle: CharSequence? = null,
        val activeQueueItemId: Long = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
    )

    private data class SearchBrowseCandidate(
        val score: Int,
        val sortTitle: String,
        val item: MediaBrowserCompat.MediaItem
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entryPoint: StillShelfCarMediaEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            StillShelfCarMediaEntryPoint::class.java
        )
    }
    private val sessionRepository: SessionRepository by lazy { entryPoint.sessionRepository() }
    private val navidromeRepository: NavidromeRepository by lazy { entryPoint.navidromeRepository() }
    private val playbackController: PlaybackController by lazy { entryPoint.playbackController() }
    private val navidromePlayerController: NavidromePlayerController by lazy {
        entryPoint.navidromePlayerController()
    }
    private val sessionPreferences: SessionPreferences by lazy { entryPoint.sessionPreferences() }

    private lateinit var mediaSession: MediaSessionCompat
    @Volatile
    private var latestSelectedBackend: BackendProvider? = null
    @Volatile
    private var carSelectedBackendOverride: BackendProvider? = null

    override fun onCreate() {
        super.onCreate()
        // These singletons attach lifecycle observers in their constructors, so initialize
        // them on the main thread before any Android Auto browse work hops to Dispatchers.IO.
        navidromeRepository
        navidromePlayerController
        mediaSession = MediaSessionCompat(this, "StillShelfCarMedia").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(
                PendingIntent.getActivity(
                    this@StillShelfCarMediaService,
                    0,
                    Intent(this@StillShelfCarMediaService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
                )
            )
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        handlePlay()
                    }

                    override fun onPause() {
                        handlePause()
                    }

                    override fun onSkipToPrevious() {
                        handleSkipToPrevious()
                    }

                    override fun onSkipToNext() {
                        handleSkipToNext()
                    }

                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        serviceScope.launch {
                            handlePlayFromMediaId(mediaId.orEmpty())
                        }
                    }

                    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                        serviceScope.launch {
                            handlePlayFromSearch(query.orEmpty())
                        }
                    }

                    override fun onSkipToQueueItem(id: Long) {
                        handleSkipToQueueItem(id)
                    }

                    override fun onCustomAction(action: String?, extras: Bundle?) {
                        serviceScope.launch {
                            handleCustomAction(action.orEmpty())
                        }
                    }
                }
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        observePlaybackState()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(
            MEDIA_ID_ROOT,
            Bundle().apply {
                putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
            }
        )
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            result.sendResult(loadChildrenFor(parentId))
        }
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        serviceScope.launch(Dispatchers.IO) {
            result.sendResult(searchChildrenFor(query))
        }
    }

    private suspend fun loadChildrenFor(parentId: String): MutableList<MediaBrowserCompat.MediaItem> {
        val absAvailable = isAbsAvailable()
        val navAvailable = isNavidromeAvailable()
        val items = when {
            parentId == MEDIA_ID_ROOT -> buildRootItems(absAvailable, navAvailable)
            parentId == MEDIA_ID_ABS_ROOT && absAvailable -> buildAbsRootItems()
            parentId == MEDIA_ID_ABS_CONTINUE && absAvailable -> buildAbsContinueItems()
            parentId == MEDIA_ID_ABS_RECENT && absAvailable -> buildAbsRecentItems()
            parentId == MEDIA_ID_ABS_AUTHORS && absAvailable -> buildAbsAuthorItems()
            parentId.startsWith(MEDIA_ID_ABS_AUTHOR_PREFIX) && absAvailable -> {
                buildAbsAuthorBookItems(parentId.removePrefix(MEDIA_ID_ABS_AUTHOR_PREFIX))
            }
            parentId == MEDIA_ID_ABS_BOOKS && absAvailable -> buildAbsBookBrowseItems()
            parentId == MEDIA_ID_NAV_ROOT && navAvailable -> buildNavRootItems()
            parentId == MEDIA_ID_NAV_ARTISTS && navAvailable -> buildNavArtistItems()
            parentId.startsWith(MEDIA_ID_NAV_ARTIST_PREFIX) && navAvailable -> {
                buildNavArtistAlbumItems(parentId.removePrefix(MEDIA_ID_NAV_ARTIST_PREFIX))
            }
            parentId == MEDIA_ID_NAV_ALBUMS && navAvailable -> buildNavAlbumItems()
            parentId.startsWith(MEDIA_ID_NAV_ALBUM_PREFIX) && navAvailable -> {
                buildNavAlbumTrackItems(parentId.removePrefix(MEDIA_ID_NAV_ALBUM_PREFIX))
            }
            parentId == MEDIA_ID_NAV_SONGS && navAvailable -> buildNavSongItems()
            parentId == MEDIA_ID_NAV_PLAYLISTS && navAvailable -> buildNavPlaylistItems()
            parentId.startsWith(MEDIA_ID_NAV_PLAYLIST_PREFIX) && navAvailable -> {
                buildNavPlaylistTrackItems(parentId.removePrefix(MEDIA_ID_NAV_PLAYLIST_PREFIX))
            }
            else -> mutableListOf(buildMessageItem("message:$parentId", "Nothing here yet."))
        }
        return if (parentId == MEDIA_ID_ROOT) items else prependHomeShortcut(items)
    }

    private suspend fun searchChildrenFor(query: String): MutableList<MediaBrowserCompat.MediaItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return mutableListOf(
                buildMessageItem(
                    mediaId = "message:search_blank",
                    title = "Type or speak something to search."
                )
            )
        }

        val candidates = mutableListOf<SearchBrowseCandidate>()
        val normalizedQueryLower = normalizedQuery.lowercase()

        when (val result = sessionRepository.searchActiveLibrary(normalizedQuery, limit = 20)) {
            is AppResult.Success -> {
                result.value.books.forEach { book ->
                    val score = bookSearchScore(book, normalizedQueryLower)
                    if (score > 0) {
                        candidates += SearchBrowseCandidate(
                            score = score,
                            sortTitle = book.title.lowercase(),
                            item = buildPlayableItem(
                                mediaId = encodeAbsBookMediaId(book.id),
                                title = book.title,
                                subtitle = "Book • ${book.authorName}",
                                iconUri = book.coverUrl
                            )
                        )
                    }
                }

                result.value.authors.forEach { author ->
                    val score = entitySearchScore(author.name, normalizedQueryLower)
                    if (score > 0) {
                        candidates += SearchBrowseCandidate(
                            score = score,
                            sortTitle = author.name.lowercase(),
                            item = buildBrowsableItem(
                                mediaId = MEDIA_ID_ABS_AUTHOR_PREFIX + author.id,
                                title = author.name,
                                subtitle = "Author",
                                iconUri = author.imageUrl
                            )
                        )
                    }
                }
            }
            is AppResult.Error -> Unit
        }

        when (val result = navidromeRepository.search(normalizedQuery, forceRefresh = false)) {
            is AppResult.Success -> {
                result.value.tracks.forEach { track ->
                    val score = trackSearchScore(track, normalizedQueryLower)
                    if (score > 0) {
                        candidates += SearchBrowseCandidate(
                            score = score,
                            sortTitle = track.title.lowercase(),
                            item = buildPlayableItem(
                                mediaId = encodeNavSongsTrackMediaId(track.id),
                                title = track.title,
                                subtitle = "Song • ${track.artistName} • ${track.albumName}",
                                iconUri = track.coverUrl
                            )
                        )
                    }
                }

                result.value.albums.forEach { album ->
                    val score = albumSearchScore(album, normalizedQueryLower)
                    if (score > 0) {
                        candidates += SearchBrowseCandidate(
                            score = score,
                            sortTitle = album.name.lowercase(),
                            item = buildBrowsableItem(
                                mediaId = MEDIA_ID_NAV_ALBUM_PREFIX + album.id,
                                title = album.name,
                                subtitle = "Album • ${album.artistName}",
                                iconUri = album.coverUrl
                            )
                        )
                    }
                }

                result.value.artists.forEach { artist ->
                    val score = entitySearchScore(artist.name, normalizedQueryLower)
                    if (score > 0) {
                        candidates += SearchBrowseCandidate(
                            score = score,
                            sortTitle = artist.name.lowercase(),
                            item = buildBrowsableItem(
                                mediaId = MEDIA_ID_NAV_ARTIST_PREFIX + artist.id,
                                title = artist.name,
                                subtitle = "Artist",
                                iconUri = artist.imageUrl ?: artist.coverUrl
                            )
                        )
                    }
                }
            }
            is AppResult.Error -> Unit
        }

        val items = candidates
            .sortedWith(
                compareByDescending<SearchBrowseCandidate> { it.score }
                    .thenBy { it.sortTitle }
            )
            .map(SearchBrowseCandidate::item)
            .distinctBy { it.description.mediaId }
            .take(16)
            .toMutableList()

        if (items.isEmpty()) {
            items += buildMessageItem(
                mediaId = "message:search_empty:$normalizedQuery",
                title = "No results for \"$normalizedQuery\"."
            )
        }
        return items
    }

    private suspend fun handlePlayFromSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        val normalizedQueryLower = normalizedQuery.lowercase()

        val bestTrack = when (val navResult = navidromeRepository.search(normalizedQuery, forceRefresh = false)) {
            is AppResult.Success -> navResult.value.tracks
                .map { it to trackSearchScore(it, normalizedQueryLower) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
            is AppResult.Error -> null
        }

        val bestBook = when (val absResult = sessionRepository.searchActiveLibrary(normalizedQuery, limit = 20)) {
            is AppResult.Success -> absResult.value.books
                .map { it to bookSearchScore(it, normalizedQueryLower) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second > 0 }
            is AppResult.Error -> null
        }

        if ((bestTrack?.second ?: -1) >= (bestBook?.second ?: -1) && bestTrack != null) {
            carSelectedBackendOverride = BackendProvider.NAVIDROME
            navidromePlayerController.playTracks(listOf(bestTrack.first), startIndex = 0)
            publishCurrentSessionSnapshot()
            return
        }

        if (bestBook != null) {
            carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
            playbackController.playBook(bestBook.first.id)
            publishCurrentSessionSnapshot()
        }
    }

    private suspend fun isAbsAvailable(): Boolean {
        val session = sessionRepository.observeSessionState().first()
        return !session.activeServerId.isNullOrBlank() &&
            !session.activeLibraryId.isNullOrBlank() &&
            !session.requiresLibrarySelection
    }

    private suspend fun isNavidromeAvailable(): Boolean {
        return navidromeRepository.observeSession().first() != null
    }

    private fun buildRootItems(
        absAvailable: Boolean,
        navAvailable: Boolean
    ): MutableList<MediaBrowserCompat.MediaItem> {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (absAvailable) {
            items += buildBrowsableItem(
                mediaId = MEDIA_ID_ABS_ROOT,
                title = "Audiobookshelf",
                subtitle = "Audiobooks and podcasts"
            )
        }
        if (navAvailable) {
            items += buildBrowsableItem(
                mediaId = MEDIA_ID_NAV_ROOT,
                title = "Navidrome",
                subtitle = "Music library"
            )
        }
        if (items.isEmpty()) {
            items += buildMessageItem(
                mediaId = "message:signed_out",
                title = "Sign in on your phone first.",
                subtitle = "StillShelf needs an Audiobookshelf or Navidrome session before Android Auto can browse it."
            )
        }
        return items
    }

    private fun buildAbsRootItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return mutableListOf(
            buildBrowsableItem(
                mediaId = MEDIA_ID_ABS_CONTINUE,
                title = "Continue Listening",
                subtitle = "Resume your in-progress books"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_ABS_RECENT,
                title = "Recently Added",
                subtitle = "New books from your active library"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_ABS_AUTHORS,
                title = "Authors",
                subtitle = "Browse by author"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_ABS_BOOKS,
                title = "Books",
                subtitle = "Browse all books"
            )
        )
    }

    private suspend fun buildAbsContinueItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = sessionRepository.fetchHomeFeed(continueLimit = 24, recentlyAddedLimit = 1)) {
            is AppResult.Success -> {
                val items = result.value.continueListening
                    .map(::buildAbsContinueBookItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:abs_continue_empty", "Nothing in progress.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:abs_continue_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildAbsRecentItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = sessionRepository.fetchHomeFeed(continueLimit = 1, recentlyAddedLimit = 48)) {
            is AppResult.Success -> {
                val items = result.value.recentlyAdded
                    .map(::buildAbsBookItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:abs_recent_empty", "No recent books found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:abs_recent_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildAbsAuthorItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = fetchAllAbsAuthors()) {
            is AppResult.Success -> {
                val items = result.value
                    .sortedBy { it.name.lowercase() }
                    .map(::buildAbsAuthorItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:abs_authors_empty", "No authors found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:abs_authors_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildAbsAuthorBookItems(authorId: String): MutableList<MediaBrowserCompat.MediaItem> {
        val authorName = when (val authorsResult = fetchAllAbsAuthors()) {
            is AppResult.Success -> authorsResult.value.firstOrNull { it.id == authorId }?.name
            is AppResult.Error -> null
        }
        return when (val result = sessionRepository.fetchAllBooksForActiveLibrary(forceRefresh = false)) {
            is AppResult.Success -> {
                val items = result.value
                    .filter { book ->
                        book.authorIds.contains(authorId) ||
                            (
                                book.authorIds.isEmpty() &&
                                    authorName != null &&
                                    book.authorName.equals(authorName, ignoreCase = true)
                                )
                    }
                    .sortedBy { it.title.lowercase() }
                    .map(::buildAbsBookItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem(
                        mediaId = "message:abs_author_books_empty:$authorId",
                        title = "No books found for this author."
                    )
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:abs_author_books_error:$authorId",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildAbsBookBrowseItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = sessionRepository.fetchAllBooksForActiveLibrary(forceRefresh = false)) {
            is AppResult.Success -> {
                val items = result.value
                    .sortedBy { it.title.lowercase() }
                    .map(::buildAbsBookItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:abs_books_empty", "No books found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:abs_books_error",
                    title = result.message
                )
            )
        }
    }

    private fun buildNavRootItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return mutableListOf(
            buildBrowsableItem(
                mediaId = MEDIA_ID_NAV_ARTISTS,
                title = "Artists",
                subtitle = "Browse by artist"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_NAV_ALBUMS,
                title = "Albums",
                subtitle = "Browse all albums"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_NAV_SONGS,
                title = "Songs",
                subtitle = "Browse all tracks"
            ),
            buildBrowsableItem(
                mediaId = MEDIA_ID_NAV_PLAYLISTS,
                title = "Playlists",
                subtitle = "Browse your playlists"
            )
        )
    }

    private suspend fun buildNavArtistItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchArtists(forceRefresh = false)) {
            is AppResult.Success -> {
                val items = result.value
                    .map(::buildNavArtistItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:nav_artists_empty", "No artists found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_artists_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavArtistAlbumItems(artistId: String): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchArtistDetail(artistId, forceRefresh = false)) {
            is AppResult.Success -> {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>(
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_ARTIST_PLAY_PREFIX + artistId,
                        title = "Play Artist"
                    ),
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_ARTIST_SHUFFLE_PREFIX + artistId,
                        title = "Shuffle Artist"
                    )
                )
                items += result.value.albums
                    .map(::buildNavAlbumItem)
                if (items.size == 2) {
                    items += buildMessageItem(
                        mediaId = "message:nav_artist_albums_empty:$artistId",
                        title = "This artist has no albums."
                    )
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_artist_albums_error:$artistId",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavAlbumItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (
            val result = navidromeRepository.fetchAlbums(
                sort = NavidromeAlbumSortOption.ALBUM_TITLE,
                forceRefresh = false
            )
        ) {
            is AppResult.Success -> {
                val items = result.value
                    .map(::buildNavAlbumItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:nav_albums_empty", "No albums found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_albums_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavAlbumTrackItems(albumId: String): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = false)) {
            is AppResult.Success -> {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>(
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_ALBUM_PLAY_PREFIX + result.value.album.id,
                        title = "Play Album"
                    ),
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_ALBUM_SHUFFLE_PREFIX + result.value.album.id,
                        title = "Shuffle Album"
                    )
                )
                items += result.value.tracks
                    .map { track ->
                        buildPlayableItem(
                            mediaId = encodeNavAlbumTrackMediaId(result.value.album.id, track.id),
                            title = track.title,
                            subtitle = track.artistName,
                            iconUri = track.coverUrl
                        )
                    }
                if (items.size == 2) {
                    items += buildMessageItem("message:nav_album_empty:$albumId", "This album has no tracks.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_album_error:$albumId",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavSongItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchSongs(forceRefresh = false)) {
            is AppResult.Success -> {
                val items = result.value
                    .map { track ->
                        buildPlayableItem(
                            mediaId = encodeNavSongsTrackMediaId(track.id),
                            title = track.title,
                            subtitle = "${track.artistName} • ${track.albumName}",
                            iconUri = track.coverUrl
                        )
                    }
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:nav_songs_empty", "No songs found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_songs_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavPlaylistItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchPlaylists(forceRefresh = false)) {
            is AppResult.Success -> {
                val items = result.value
                    .map(::buildNavPlaylistItem)
                    .toMutableList()
                if (items.isEmpty()) {
                    items += buildMessageItem("message:nav_playlists_empty", "No playlists found.")
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_playlists_error",
                    title = result.message
                )
            )
        }
    }

    private suspend fun buildNavPlaylistTrackItems(playlistId: String): MutableList<MediaBrowserCompat.MediaItem> {
        return when (val result = navidromeRepository.fetchPlaylistDetail(playlistId, forceRefresh = false)) {
            is AppResult.Success -> {
                val items = mutableListOf<MediaBrowserCompat.MediaItem>(
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_PLAYLIST_PLAY_PREFIX + result.value.playlist.id,
                        title = "Play Playlist"
                    ),
                    buildPlayableItem(
                        mediaId = MEDIA_ID_NAV_PLAYLIST_SHUFFLE_PREFIX + result.value.playlist.id,
                        title = "Shuffle Playlist"
                    )
                )
                items += result.value.tracks
                    .map { track ->
                        buildPlayableItem(
                            mediaId = encodeNavPlaylistTrackMediaId(result.value.playlist.id, track.id),
                            title = track.title,
                            subtitle = track.artistName,
                            iconUri = track.coverUrl
                        )
                    }
                if (items.size == 2) {
                    items += buildMessageItem(
                        mediaId = "message:nav_playlist_empty:$playlistId",
                        title = "This playlist has no tracks."
                    )
                }
                items
            }
            is AppResult.Error -> mutableListOf(
                buildMessageItem(
                    mediaId = "message:nav_playlist_error:$playlistId",
                    title = result.message
                )
            )
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            combine(
                sessionPreferences.state,
                playbackController.uiState,
                navidromePlayerController.state
            ) { preferences, absState, navState ->
                latestSelectedBackend = preferences.selectedBackend
                if (
                    carSelectedBackendOverride == BackendProvider.AUDIOBOOKSHELF &&
                    absState.book == null &&
                    !absState.isLoading
                ) {
                    carSelectedBackendOverride = null
                }
                if (
                    carSelectedBackendOverride == BackendProvider.NAVIDROME &&
                    navState.currentTrack == null &&
                    !navState.isLoading
                ) {
                    carSelectedBackendOverride = null
                }
                syncSessionState(
                    preferences = preferences,
                    absState = absState,
                    navState = navState
                )
            }.collect { snapshot ->
                mediaSession.setMetadata(snapshot.metadata)
                mediaSession.setPlaybackState(snapshot.playbackState)
                mediaSession.setQueue(snapshot.queue)
                mediaSession.setQueueTitle(snapshot.queueTitle)
                mediaSession.isActive = snapshot.metadata != null
            }
        }
    }

    private fun syncSessionState(
        preferences: SessionPreferenceState,
        absState: PlaybackUiState,
        navState: com.stillshelf.app.core.model.NavidromePlayerState
    ): CarSessionSnapshot {
        val active = when (
            resolveControllableBackend(
                selectedBackend = preferences.selectedBackend,
                absState = absState,
                navState = navState
            )
        ) {
            BackendProvider.AUDIOBOOKSHELF -> ActiveSession.Audiobookshelf(absState)
            BackendProvider.NAVIDROME -> ActiveSession.Navidrome(navState)
            null -> null
        }

        if (active == null) {
            return CarSessionSnapshot(
                metadata = null,
                playbackState = buildPlaybackState(
                    state = PlaybackStateCompat.STATE_NONE,
                    positionMs = 0L,
                    actions = DEFAULT_PLAYBACK_ACTIONS
                )
            )
        }

        return when (active) {
            is ActiveSession.Audiobookshelf -> {
                val book = active.state.book
                val metadata = if (book == null) {
                    null
                } else {
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, encodeAbsBookMediaId(book.id))
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.authorName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, book.coverUrl)
                        .putLong(
                            MediaMetadataCompat.METADATA_KEY_DURATION,
                            active.state.durationMs.coerceAtLeast(0L)
                        )
                        .build()
                }
                CarSessionSnapshot(
                    metadata = metadata,
                    playbackState = buildPlaybackState(
                        state = if (active.state.isPlaying) {
                            PlaybackStateCompat.STATE_PLAYING
                        } else {
                            PlaybackStateCompat.STATE_PAUSED
                        },
                        positionMs = active.state.positionMs.coerceAtLeast(0L),
                        actions = DEFAULT_PLAYBACK_ACTIONS,
                        customActions = buildAbsCustomActions(active.state)
                    )
                )
            }
            is ActiveSession.Navidrome -> {
                val track = active.state.currentTrack
                val metadata = if (track == null) {
                    null
                } else {
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, encodeNavSongsTrackMediaId(track.id))
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artistName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.albumName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, track.coverUrl)
                        .putLong(
                            MediaMetadataCompat.METADATA_KEY_DURATION,
                            active.state.durationMs.toLong().coerceAtLeast(0L)
                        )
                        .build()
                }
                val queue = buildNavQueueItems(active.state.queue)
                CarSessionSnapshot(
                    metadata = metadata,
                    playbackState = buildPlaybackState(
                        state = if (active.state.isPlaying) {
                            PlaybackStateCompat.STATE_PLAYING
                        } else {
                            PlaybackStateCompat.STATE_PAUSED
                        },
                        positionMs = active.state.positionMs.toLong().coerceAtLeast(0L),
                        actions = DEFAULT_PLAYBACK_ACTIONS or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM,
                        customActions = buildNavCustomActions(
                            state = active.state,
                            preferences = preferences
                        ),
                        activeQueueItemId = active.state.currentIndex
                            .takeIf { it in active.state.queue.indices }
                            ?.toLong()
                            ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                    ),
                    queue = queue,
                    queueTitle = if (queue.isEmpty()) null else "Up Next",
                    activeQueueItemId = active.state.currentIndex
                        .takeIf { it in active.state.queue.indices }
                        ?.toLong()
                        ?: MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                )
            }
        }
    }

    private fun buildPlaybackState(
        state: Int,
        positionMs: Long,
        actions: Long,
        customActions: List<PlaybackStateCompat.CustomAction> = emptyList(),
        activeQueueItemId: Long = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
    ): PlaybackStateCompat {
        val builder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1f)
            .setActiveQueueItemId(activeQueueItemId)
        customActions.forEach(builder::addCustomAction)
        return builder.build()
    }

    private fun handlePlay() {
        val navState = navidromePlayerController.state.value
        val absState = playbackController.uiState.value
        when (
            resolveControllableBackend(
                selectedBackend = latestSelectedBackend,
                absState = absState,
                navState = navState
            )
        ) {
            BackendProvider.NAVIDROME -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                navidromePlayerController.play()
                serviceScope.launch { publishCurrentSessionSnapshot() }
            }
            BackendProvider.AUDIOBOOKSHELF -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.playCurrent()
                serviceScope.launch { publishCurrentSessionSnapshot() }
            }
            null -> Unit
        }
    }

    private fun handlePause() {
        val navState = navidromePlayerController.state.value
        val absState = playbackController.uiState.value
        when (
            resolveControllableBackend(
                selectedBackend = latestSelectedBackend,
                absState = absState,
                navState = navState
            )
        ) {
            BackendProvider.NAVIDROME -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                navidromePlayerController.pause()
                serviceScope.launch { publishCurrentSessionSnapshot() }
            }
            BackendProvider.AUDIOBOOKSHELF -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.pauseCurrent()
                serviceScope.launch { publishCurrentSessionSnapshot() }
            }
            null -> Unit
        }
    }

    private fun handleSkipToPrevious() {
        val navState = navidromePlayerController.state.value
        val absState = playbackController.uiState.value
        when (
            resolveControllableBackend(
                selectedBackend = latestSelectedBackend,
                absState = absState,
                navState = navState
            )
        ) {
            BackendProvider.NAVIDROME -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                navidromePlayerController.playPrevious()
            }
            BackendProvider.AUDIOBOOKSHELF, null -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.handleExternalPlaybackAction(PlaybackController.ACTION_REWIND)
            }
        }
    }

    private fun handleSkipToNext() {
        val navState = navidromePlayerController.state.value
        val absState = playbackController.uiState.value
        when (
            resolveControllableBackend(
                selectedBackend = latestSelectedBackend,
                absState = absState,
                navState = navState
            )
        ) {
            BackendProvider.NAVIDROME -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                navidromePlayerController.playNext()
            }
            BackendProvider.AUDIOBOOKSHELF, null -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.handleExternalPlaybackAction(PlaybackController.ACTION_FORWARD)
            }
        }
    }

    private fun handleSkipToQueueItem(id: Long) {
        if (id < 0L) return
        val navState = navidromePlayerController.state.value
        val absState = playbackController.uiState.value
        if (
            resolveControllableBackend(
                selectedBackend = latestSelectedBackend,
                absState = absState,
                navState = navState
            ) == BackendProvider.NAVIDROME
        ) {
            carSelectedBackendOverride = BackendProvider.NAVIDROME
            navidromePlayerController.playQueueIndex(id.toInt())
        }
    }

    private suspend fun handleCustomAction(action: String) {
        when (action) {
            CUSTOM_ACTION_ABS_SPEED_DOWN -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.decreasePlaybackSpeed()
            }
            CUSTOM_ACTION_ABS_SPEED_UP -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.increasePlaybackSpeed()
            }
            CUSTOM_ACTION_NAV_SHUFFLE -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                navidromePlayerController.shuffleQueue()
            }
            CUSTOM_ACTION_NAV_FAVORITE -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                toggleCurrentNavFavorite()
            }
        }
        publishCurrentSessionSnapshot()
    }

    private suspend fun handlePlayFromMediaId(mediaId: String) {
        when {
            mediaId.startsWith(MEDIA_ID_ABS_BOOK_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.AUDIOBOOKSHELF
                playbackController.playBook(mediaId.removePrefix(MEDIA_ID_ABS_BOOK_PREFIX))
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_ARTIST_PLAY_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavArtist(mediaId.removePrefix(MEDIA_ID_NAV_ARTIST_PLAY_PREFIX), shuffle = false)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_ARTIST_SHUFFLE_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavArtist(mediaId.removePrefix(MEDIA_ID_NAV_ARTIST_SHUFFLE_PREFIX), shuffle = true)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_ALBUM_PLAY_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavAlbum(mediaId.removePrefix(MEDIA_ID_NAV_ALBUM_PLAY_PREFIX), shuffle = false)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_ALBUM_SHUFFLE_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavAlbum(mediaId.removePrefix(MEDIA_ID_NAV_ALBUM_SHUFFLE_PREFIX), shuffle = true)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_PLAYLIST_PLAY_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavPlaylist(mediaId.removePrefix(MEDIA_ID_NAV_PLAYLIST_PLAY_PREFIX), shuffle = false)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_PLAYLIST_SHUFFLE_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                playNavPlaylist(mediaId.removePrefix(MEDIA_ID_NAV_PLAYLIST_SHUFFLE_PREFIX), shuffle = true)
                publishCurrentSessionSnapshot()
            }
            mediaId.startsWith(MEDIA_ID_NAV_SONGS_TRACK_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                val trackId = mediaId.removePrefix(MEDIA_ID_NAV_SONGS_TRACK_PREFIX)
                when (val result = navidromeRepository.fetchSongs(forceRefresh = false)) {
                    is AppResult.Success -> {
                        playNavTrackFromQueue(result.value, trackId)
                        publishCurrentSessionSnapshot()
                    }
                    is AppResult.Error -> Unit
                }
            }
            mediaId.startsWith(MEDIA_ID_NAV_ALBUM_TRACK_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                val encoded = mediaId.removePrefix(MEDIA_ID_NAV_ALBUM_TRACK_PREFIX)
                val separator = encoded.indexOf(':')
                if (separator <= 0) return
                val albumId = encoded.substring(0, separator)
                val trackId = encoded.substring(separator + 1)
                when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = false)) {
                    is AppResult.Success -> {
                        playNavTrackFromQueue(result.value.tracks, trackId)
                        publishCurrentSessionSnapshot()
                    }
                    is AppResult.Error -> Unit
                }
            }
            mediaId.startsWith(MEDIA_ID_NAV_PLAYLIST_TRACK_PREFIX) -> {
                carSelectedBackendOverride = BackendProvider.NAVIDROME
                val encoded = mediaId.removePrefix(MEDIA_ID_NAV_PLAYLIST_TRACK_PREFIX)
                val separator = encoded.indexOf(':')
                if (separator <= 0) return
                val playlistId = encoded.substring(0, separator)
                val trackId = encoded.substring(separator + 1)
                when (val result = navidromeRepository.fetchPlaylistDetail(playlistId, forceRefresh = false)) {
                    is AppResult.Success -> {
                        playNavTrackFromQueue(result.value.tracks, trackId)
                        publishCurrentSessionSnapshot()
                    }
                    is AppResult.Error -> Unit
                }
            }
        }
    }

    private suspend fun publishCurrentSessionSnapshot() {
        val preferences = sessionPreferences.state.first()
        val snapshot = syncSessionState(
            preferences = preferences,
            absState = playbackController.uiState.value,
            navState = navidromePlayerController.state.value
        )
        mediaSession.setMetadata(snapshot.metadata)
        mediaSession.setPlaybackState(snapshot.playbackState)
        mediaSession.setQueue(snapshot.queue)
        mediaSession.setQueueTitle(snapshot.queueTitle)
        mediaSession.isActive = snapshot.metadata != null
    }

    private suspend fun playNavArtist(artistId: String, shuffle: Boolean) {
        when (val result = navidromeRepository.fetchArtistDetail(artistId, forceRefresh = false)) {
            is AppResult.Success -> {
                val tracks = mutableListOf<NavidromeTrack>()
                result.value.albums.forEach { album ->
                    when (val detail = navidromeRepository.fetchAlbumDetail(album.id, forceRefresh = false)) {
                        is AppResult.Success -> tracks += detail.value.tracks
                        is AppResult.Error -> Unit
                    }
                }
                val distinctTracks = tracks.distinctBy { it.id }
                val queue = if (shuffle) distinctTracks.shuffled() else distinctTracks
                if (queue.isNotEmpty()) {
                    navidromePlayerController.playTracks(queue, startIndex = 0)
                }
            }
            is AppResult.Error -> Unit
        }
    }

    private suspend fun playNavAlbum(albumId: String, shuffle: Boolean) {
        when (val result = navidromeRepository.fetchAlbumDetail(albumId, forceRefresh = false)) {
            is AppResult.Success -> {
                val queue = if (shuffle) result.value.tracks.shuffled() else result.value.tracks
                if (queue.isNotEmpty()) {
                    navidromePlayerController.playTracks(queue, startIndex = 0)
                }
            }
            is AppResult.Error -> Unit
        }
    }

    private suspend fun playNavPlaylist(playlistId: String, shuffle: Boolean) {
        when (val result = navidromeRepository.fetchPlaylistDetail(playlistId, forceRefresh = false)) {
            is AppResult.Success -> {
                val queue = if (shuffle) result.value.tracks.shuffled() else result.value.tracks
                if (queue.isNotEmpty()) {
                    navidromePlayerController.playTracks(queue, startIndex = 0)
                }
            }
            is AppResult.Error -> Unit
        }
    }

    private fun playNavTrackFromQueue(queue: List<NavidromeTrack>, trackId: String) {
        val startIndex = queue.indexOfFirst { it.id == trackId }
        if (startIndex >= 0) {
            navidromePlayerController.playTracks(queue, startIndex = startIndex)
        }
    }

    private fun buildAbsContinueBookItem(item: ContinueListeningItem): MediaBrowserCompat.MediaItem {
        return buildPlayableItem(
            mediaId = encodeAbsBookMediaId(item.book.id),
            title = item.book.title,
            subtitle = item.book.authorName,
            iconUri = item.book.coverUrl
        )
    }

    private fun buildAbsBookItem(book: BookSummary): MediaBrowserCompat.MediaItem {
        return buildPlayableItem(
            mediaId = encodeAbsBookMediaId(book.id),
            title = book.title,
            subtitle = book.authorName,
            iconUri = book.coverUrl
        )
    }

    private fun buildAbsAuthorItem(author: NamedEntitySummary): MediaBrowserCompat.MediaItem {
        return buildBrowsableItem(
            mediaId = MEDIA_ID_ABS_AUTHOR_PREFIX + author.id,
            title = author.name,
            subtitle = author.subtitle,
            iconUri = author.imageUrl
        )
    }

    private suspend fun fetchAllAbsAuthors(): AppResult<List<NamedEntitySummary>> {
        val authors = mutableListOf<NamedEntitySummary>()
        var page = 0
        while (true) {
            when (
                val result = sessionRepository.fetchAuthorsForActiveLibrary(
                    limit = ABS_BROWSE_PAGE_SIZE,
                    page = page,
                    forceRefresh = false
                )
            ) {
                is AppResult.Success -> {
                    val batch = result.value
                    if (batch.isEmpty()) break
                    authors += batch
                    if (batch.size < ABS_BROWSE_PAGE_SIZE) break
                    page += 1
                }
                is AppResult.Error -> return result
            }
        }
        return AppResult.Success(authors.distinctBy { it.id })
    }

    private fun buildAbsCustomActions(state: PlaybackUiState): List<PlaybackStateCompat.CustomAction> {
        val currentLabel = speedStateLabel(state.playbackSpeed)
        return listOf(
            buildCustomAction(
                action = CUSTOM_ACTION_ABS_SPEED_DOWN,
                title = "Slower · $currentLabel",
                iconResId = android.R.drawable.ic_media_rew
            ),
            buildCustomAction(
                action = CUSTOM_ACTION_ABS_SPEED_UP,
                title = "Faster · $currentLabel",
                iconResId = android.R.drawable.ic_media_ff
            )
        )
    }

    private fun buildNavCustomActions(
        state: com.stillshelf.app.core.model.NavidromePlayerState,
        preferences: SessionPreferenceState
    ): List<PlaybackStateCompat.CustomAction> {
        val currentTrack = state.currentTrack
        val favoriteTitle = if (
            currentTrack != null &&
            currentTrack.id.isNotBlank() &&
            favoriteTrackIdsFor(preferences).contains(currentTrack.id)
        ) {
            "Unfavorite"
        } else {
            "Favorite"
        }
        return listOf(
            buildCustomAction(
                action = CUSTOM_ACTION_NAV_SHUFFLE,
                title = "Shuffle Queue",
                iconResId = android.R.drawable.ic_menu_rotate
            ),
            buildCustomAction(
                action = CUSTOM_ACTION_NAV_FAVORITE,
                title = favoriteTitle,
                iconResId = android.R.drawable.btn_star_big_off
            )
        )
    }

    private fun buildCustomAction(
        action: String,
        title: String,
        iconResId: Int
    ): PlaybackStateCompat.CustomAction {
        return PlaybackStateCompat.CustomAction.Builder(
            action,
            title,
            iconResId
        ).build()
    }

    private fun buildNavQueueItems(queue: List<NavidromeTrack>): List<MediaSessionCompat.QueueItem> {
        return queue.mapIndexed { index, track ->
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId(encodeNavSongsTrackMediaId(track.id))
                    .setTitle(track.title)
                    .setSubtitle(track.artistName)
                    .setIconUri(track.coverUrl?.let(android.net.Uri::parse))
                    .build(),
                index.toLong()
            )
        }
    }

    private fun formatPlaybackSpeed(speed: Float): String {
        val rounded = ((speed * 100).toInt() / 100f)
        return if (rounded % 1f == 0f) {
            "${rounded.toInt()}x"
        } else {
            "${rounded}x"
        }
    }

    private fun speedStateLabel(speed: Float): String {
        val formatted = formatPlaybackSpeed(speed)
        return if (kotlin.math.abs(speed - 1.0f) < 0.01f) {
            "Normal $formatted"
        } else {
            formatted
        }
    }

    private fun favoriteTrackIdsFor(preferences: SessionPreferenceState): Set<String> {
        val sessionKey = navidromeSessionKey(
            baseUrl = preferences.navidromeBaseUrl,
            username = preferences.navidromeUsername
        ) ?: return emptySet()
        return preferences.navidromeFavoriteTracksBySession[sessionKey]
            .orEmpty()
            .map { it.id }
            .toSet()
    }

    private suspend fun toggleCurrentNavFavorite() {
        val currentTrack = navidromePlayerController.state.value.currentTrack ?: return
        if (currentTrack.id.startsWith("radio:")) return
        val preferences = sessionPreferences.state.first()
        val sessionKey = navidromeSessionKey(
            baseUrl = preferences.navidromeBaseUrl,
            username = preferences.navidromeUsername
        ) ?: return
        sessionPreferences.toggleNavidromeFavoriteTrack(sessionKey, currentTrack)
    }

    private fun resolveControllableBackend(
        selectedBackend: BackendProvider?,
        absState: PlaybackUiState,
        navState: com.stillshelf.app.core.model.NavidromePlayerState
    ): BackendProvider? {
        return when {
            absState.isPlaying && absState.book != null -> BackendProvider.AUDIOBOOKSHELF
            navState.isPlaying && navState.currentTrack != null -> BackendProvider.NAVIDROME
            absState.isLoading -> BackendProvider.AUDIOBOOKSHELF
            navState.isLoading -> BackendProvider.NAVIDROME
            carSelectedBackendOverride == BackendProvider.AUDIOBOOKSHELF && absState.book != null -> {
                BackendProvider.AUDIOBOOKSHELF
            }
            carSelectedBackendOverride == BackendProvider.NAVIDROME && navState.currentTrack != null -> {
                BackendProvider.NAVIDROME
            }
            selectedBackend == BackendProvider.AUDIOBOOKSHELF && absState.book != null -> {
                BackendProvider.AUDIOBOOKSHELF
            }
            selectedBackend == BackendProvider.NAVIDROME && navState.currentTrack != null -> {
                BackendProvider.NAVIDROME
            }
            absState.book != null -> BackendProvider.AUDIOBOOKSHELF
            navState.currentTrack != null -> BackendProvider.NAVIDROME
            else -> null
        }
    }

    private fun navidromeSessionKey(
        baseUrl: String?,
        username: String?
    ): String? {
        val normalizedBaseUrl = baseUrl?.trim()?.trimEnd('/').orEmpty()
        val normalizedUsername = username?.trim().orEmpty()
        if (normalizedBaseUrl.isBlank() || normalizedUsername.isBlank()) return null
        return "$normalizedBaseUrl|$normalizedUsername"
    }

    private fun entitySearchScore(value: String, query: String): Int {
        val normalizedValue = value.lowercase()
        val tokens = normalizedValue.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        return when {
            normalizedValue == query -> 1_000
            tokens.any { it == query } -> 900
            normalizedValue.startsWith(query) -> 700
            tokens.any { it.startsWith(query) } -> 550
            normalizedValue.contains(query) -> 300
            else -> 0
        }
    }

    private fun bookSearchScore(book: BookSummary, query: String): Int {
        return entitySearchScore(book.title, query) + when {
            book.authorName.lowercase().contains(query) -> 40
            else -> 0
        }
    }

    private fun albumSearchScore(album: NavidromeAlbum, query: String): Int {
        return entitySearchScore(album.name, query) + when {
            album.artistName.lowercase().contains(query) -> 40
            else -> 0
        }
    }

    private fun trackSearchScore(track: NavidromeTrack, query: String): Int {
        return entitySearchScore(track.title, query) + when {
            track.artistName.lowercase().contains(query) -> 60
            track.albumName.lowercase().contains(query) -> 35
            else -> 0
        } + when {
            track.artistName.lowercase() == query -> 120
            else -> 0
        }
    }

    private fun buildNavAlbumItem(album: NavidromeAlbum): MediaBrowserCompat.MediaItem {
        return buildBrowsableItem(
            mediaId = MEDIA_ID_NAV_ALBUM_PREFIX + album.id,
            title = album.name,
            subtitle = album.artistName,
            iconUri = album.coverUrl
        )
    }

    private fun buildNavArtistItem(artist: NavidromeArtist): MediaBrowserCompat.MediaItem {
        val subtitle = buildString {
            append(artist.albumCount)
            append(if (artist.albumCount == 1) " album" else " albums")
        }
        return buildBrowsableItem(
            mediaId = MEDIA_ID_NAV_ARTIST_PREFIX + artist.id,
            title = artist.name,
            subtitle = subtitle,
            iconUri = artist.imageUrl ?: artist.coverUrl
        )
    }

    private fun buildNavPlaylistItem(playlist: NavidromePlaylist): MediaBrowserCompat.MediaItem {
        val subtitle = buildString {
            val count = playlist.songCount
            if (count != null) {
                append(count)
                append(if (count == 1) " song" else " songs")
            }
        }.ifBlank { null }
        return buildBrowsableItem(
            mediaId = MEDIA_ID_NAV_PLAYLIST_PREFIX + playlist.id,
            title = playlist.name,
            subtitle = subtitle,
            iconUri = playlist.artworkUrls.firstOrNull()
        )
    }

    private fun buildBrowsableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        iconUri: String? = null
    ): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(iconUri?.let(android.net.Uri::parse))
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun buildPlayableItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        iconUri: String? = null
    ): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIconUri(iconUri?.let(android.net.Uri::parse))
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun buildMessageItem(
        mediaId: String,
        title: String,
        subtitle: String? = null
    ): MediaBrowserCompat.MediaItem {
        return MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build(),
            0
        )
    }

    private fun prependHomeShortcut(
        items: MutableList<MediaBrowserCompat.MediaItem>
    ): MutableList<MediaBrowserCompat.MediaItem> {
        val result = mutableListOf<MediaBrowserCompat.MediaItem>()
        result += buildBrowsableItem(
            mediaId = MEDIA_ID_ROOT,
            title = "StillShelf Home",
            subtitle = "Choose Audiobookshelf or Navidrome"
        )
        result += items
        return result
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private sealed interface ActiveSession {
        data class Audiobookshelf(val state: PlaybackUiState) : ActiveSession
        data class Navidrome(
            val state: com.stillshelf.app.core.model.NavidromePlayerState
        ) : ActiveSession
    }

    private companion object {
        private const val ABS_BROWSE_PAGE_SIZE = 200

        private const val MEDIA_ID_ROOT = "root"
        private const val MEDIA_ID_ABS_ROOT = "abs_root"
        private const val MEDIA_ID_ABS_CONTINUE = "abs_continue"
        private const val MEDIA_ID_ABS_RECENT = "abs_recent"
        private const val MEDIA_ID_ABS_AUTHORS = "abs_authors"
        private const val MEDIA_ID_ABS_BOOKS = "abs_books"
        private const val MEDIA_ID_ABS_BOOK_PREFIX = "abs_book:"
        private const val MEDIA_ID_ABS_AUTHOR_PREFIX = "abs_author:"

        private const val MEDIA_ID_NAV_ROOT = "nav_root"
        private const val MEDIA_ID_NAV_ARTISTS = "nav_artists"
        private const val MEDIA_ID_NAV_ALBUMS = "nav_albums"
        private const val MEDIA_ID_NAV_SONGS = "nav_songs"
        private const val MEDIA_ID_NAV_PLAYLISTS = "nav_playlists"
        private const val MEDIA_ID_NAV_ARTIST_PREFIX = "nav_artist:"
        private const val MEDIA_ID_NAV_ALBUM_PREFIX = "nav_album:"
        private const val MEDIA_ID_NAV_PLAYLIST_PREFIX = "nav_playlist:"
        private const val MEDIA_ID_NAV_ARTIST_PLAY_PREFIX = "nav_artist_play:"
        private const val MEDIA_ID_NAV_ARTIST_SHUFFLE_PREFIX = "nav_artist_shuffle:"
        private const val MEDIA_ID_NAV_ALBUM_PLAY_PREFIX = "nav_album_play:"
        private const val MEDIA_ID_NAV_ALBUM_SHUFFLE_PREFIX = "nav_album_shuffle:"
        private const val MEDIA_ID_NAV_PLAYLIST_PLAY_PREFIX = "nav_playlist_play:"
        private const val MEDIA_ID_NAV_PLAYLIST_SHUFFLE_PREFIX = "nav_playlist_shuffle:"
        private const val MEDIA_ID_NAV_SONGS_TRACK_PREFIX = "nav_songs_track:"
        private const val MEDIA_ID_NAV_ALBUM_TRACK_PREFIX = "nav_album_track:"
        private const val MEDIA_ID_NAV_PLAYLIST_TRACK_PREFIX = "nav_playlist_track:"

        private const val DEFAULT_PLAYBACK_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT

        private const val CUSTOM_ACTION_ABS_SPEED_DOWN = "car_abs_speed_down"
        private const val CUSTOM_ACTION_ABS_SPEED_UP = "car_abs_speed_up"
        private const val CUSTOM_ACTION_NAV_SHUFFLE = "car_nav_shuffle"
        private const val CUSTOM_ACTION_NAV_FAVORITE = "car_nav_favorite"

        private fun encodeAbsBookMediaId(bookId: String): String {
            return MEDIA_ID_ABS_BOOK_PREFIX + bookId
        }

        private fun encodeNavSongsTrackMediaId(trackId: String): String {
            return MEDIA_ID_NAV_SONGS_TRACK_PREFIX + trackId
        }

        private fun encodeNavAlbumTrackMediaId(albumId: String, trackId: String): String {
            return MEDIA_ID_NAV_ALBUM_TRACK_PREFIX + albumId + ":" + trackId
        }

        private fun encodeNavPlaylistTrackMediaId(playlistId: String, trackId: String): String {
            return MEDIA_ID_NAV_PLAYLIST_TRACK_PREFIX + playlistId + ":" + trackId
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StillShelfCarMediaEntryPoint {
    fun sessionRepository(): SessionRepository
    fun navidromeRepository(): NavidromeRepository
    fun playbackController(): PlaybackController
    fun navidromePlayerController(): NavidromePlayerController
    fun sessionPreferences(): SessionPreferences
}
