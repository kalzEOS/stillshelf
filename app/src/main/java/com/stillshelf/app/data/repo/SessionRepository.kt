package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.HomeFeed
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.SearchResults
import com.stillshelf.app.core.model.SeriesDetailEntry
import com.stillshelf.app.core.model.RealtimeInvalidation
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import kotlinx.coroutines.flow.Flow

enum class LoginPersistenceMode {
    PersistentSecureOnly,
    PersistentAllowInsecureFallback,
    SessionOnly
}

interface SessionRepository {
    fun observeSessionState(): Flow<SessionState>
    fun observeBookProgressMutations(): Flow<BookProgressMutation>
    fun observeRealtimeInvalidations(): Flow<RealtimeInvalidation>
    fun observeServers(): Flow<List<Server>>
    suspend fun updateServer(serverId: String, name: String, baseUrl: String): AppResult<Unit>
    suspend fun deleteServer(serverId: String): AppResult<Unit>
    fun observeLibrariesForActiveServer(): Flow<List<Library>>
    suspend fun setActiveServer(serverId: String): AppResult<Unit>
    suspend fun setActiveLibrary(libraryId: String): AppResult<Unit>
    suspend fun signOutActiveSession(): AppResult<Unit>
    suspend fun refreshLibrariesForActiveServer(): AppResult<Unit>
    suspend fun addServerAndLogin(
        serverName: String,
        baseUrl: String,
        username: String,
        password: String,
        persistenceMode: LoginPersistenceMode = LoginPersistenceMode.PersistentSecureOnly
    ): AppResult<Unit>
    suspend fun testServerConnection(baseUrl: String): AppResult<String>
    suspend fun fetchBooksForActiveLibrary(
        limit: Int = 60,
        page: Int = 0,
        forceRefresh: Boolean = false
    ): AppResult<List<BookSummary>>
    suspend fun fetchAllBooksForActiveLibrary(
        forceRefresh: Boolean = false
    ): AppResult<List<BookSummary>>
    suspend fun fetchAuthorsForActiveLibrary(
        limit: Int = 60,
        page: Int = 0,
        forceRefresh: Boolean = false
    ): AppResult<List<NamedEntitySummary>>
    suspend fun fetchNarratorsForActiveLibrary(
        forceRefresh: Boolean = false
    ): AppResult<List<NamedEntitySummary>>
    suspend fun fetchSeriesForActiveLibrary(
        limit: Int = 60,
        page: Int = 0,
        forceRefresh: Boolean = false
    ): AppResult<List<NamedEntitySummary>>
    suspend fun fetchSeriesContentsForActiveLibrary(
        seriesId: String,
        collapseSubseries: Boolean = true,
        forceRefresh: Boolean = false
    ): AppResult<List<SeriesDetailEntry>>
    fun observeSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean = true
    ): Flow<List<SeriesDetailEntry>>
    fun observeSeriesSummary(
        seriesId: String
    ): Flow<NamedEntitySummary?>
    suspend fun resolveCachedSeriesIdForActiveLibrary(
        seriesName: String
    ): String?
    suspend fun resolveSeriesIdForActiveLibrary(
        seriesName: String
    ): String?
    suspend fun peekSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean = true
    ): List<SeriesDetailEntry>?
    suspend fun refreshSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean = true,
        policy: DetailRefreshPolicy = DetailRefreshPolicy.IfStale
    ): AppResult<Unit>
    suspend fun cacheSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean,
        entries: List<SeriesDetailEntry>
    ): AppResult<Unit>
    suspend fun fetchCollectionsForActiveLibrary(
        forceRefresh: Boolean = false
    ): AppResult<List<NamedEntitySummary>>
    suspend fun fetchPlaylistsForActiveLibrary(
        forceRefresh: Boolean = false
    ): AppResult<List<NamedEntitySummary>>
    suspend fun createCollection(name: String): AppResult<NamedEntitySummary>
    suspend fun createCollectionWithBook(name: String, bookId: String): AppResult<NamedEntitySummary>
    suspend fun createPlaylist(name: String): AppResult<NamedEntitySummary>
    suspend fun createPlaylistWithBook(name: String, bookId: String): AppResult<NamedEntitySummary>
    suspend fun renameCollection(collectionId: String, name: String): AppResult<Unit>
    suspend fun renamePlaylist(playlistId: String, name: String): AppResult<Unit>
    suspend fun deleteCollection(collectionId: String): AppResult<Unit>
    suspend fun deletePlaylist(playlistId: String): AppResult<Unit>
    suspend fun addBookToCollection(collectionId: String, bookId: String): AppResult<Unit>
    suspend fun addBookToPlaylist(playlistId: String, bookId: String): AppResult<Unit>
    suspend fun removeBookFromCollection(collectionId: String, bookId: String): AppResult<Unit>
    suspend fun removeBookFromPlaylist(playlistId: String, bookId: String): AppResult<Unit>
    suspend fun fetchCollectionBooks(
        collectionId: String,
        forceRefresh: Boolean = false
    ): AppResult<List<BookSummary>>
    suspend fun fetchPlaylistBooks(
        playlistId: String,
        forceRefresh: Boolean = false
    ): AppResult<List<BookSummary>>
    suspend fun fetchBookDetail(
        bookId: String,
        forceRefresh: Boolean = false
    ): AppResult<BookDetail>
    fun observeBookDetail(bookId: String): Flow<BookDetail?>
    suspend fun refreshBookDetail(
        bookId: String,
        policy: DetailRefreshPolicy = DetailRefreshPolicy.IfStale
    ): AppResult<Unit>
    suspend fun fetchPlaybackSource(bookId: String): AppResult<PlaybackSource>
    suspend fun fetchPlaybackProgress(bookId: String): AppResult<PlaybackProgress?>
    suspend fun syncPlaybackProgress(
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit>
    suspend fun syncPlaybackProgressForServer(
        serverId: String,
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit>
    suspend fun createBookmark(
        bookId: String,
        timeSeconds: Double,
        title: String? = null
    ): AppResult<Unit>
    suspend fun fetchBookmarksForActiveLibrary(
        forceRefresh: Boolean = false
    ): AppResult<List<BookmarkEntry>>
    suspend fun updateBookmark(
        bookId: String,
        bookmark: BookBookmark,
        newTitle: String
    ): AppResult<Unit>
    suspend fun deleteBookmark(
        bookId: String,
        bookmark: BookBookmark
    ): AppResult<Unit>
    suspend fun markBookFinished(
        bookId: String,
        finished: Boolean,
        resetProgressWhenUnfinished: Boolean = false,
        preservedProgress: PlaybackProgress? = null
    ): AppResult<PlaybackProgress>
    suspend fun addBookToDefaultCollection(bookId: String): AppResult<String>
    suspend fun setLastPlayedBookId(bookId: String?): AppResult<Unit>
    suspend fun searchActiveLibrary(query: String, limit: Int = 60): AppResult<SearchResults>
    suspend fun fetchMiniPlayerItem(): AppResult<ContinueListeningItem?>
    suspend fun fetchCachedHomeFeed(maxAgeMs: Long? = null): AppResult<HomeFeed?>
    suspend fun fetchHomeFeed(
        continueLimit: Int = 10,
        recentlyAddedLimit: Int = 24,
        forceRefreshDerivedContent: Boolean = false
    ): AppResult<HomeFeed>
}
