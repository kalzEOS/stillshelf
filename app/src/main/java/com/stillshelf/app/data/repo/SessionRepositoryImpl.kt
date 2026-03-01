package com.stillshelf.app.data.repo

import com.stillshelf.app.core.database.LibraryDao
import com.stillshelf.app.core.database.LibraryEntity
import com.stillshelf.app.core.database.AppDatabase
import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.database.ServerEntity
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.HomeFeed
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.SearchResults
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server
import com.stillshelf.app.core.model.SessionState
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.api.AudiobookshelfApi
import com.stillshelf.app.data.api.AudiobookshelfLibraryDto
import com.stillshelf.app.data.api.AudiobookshelfLibraryItemDto
import com.stillshelf.app.data.api.AudiobookshelfBookDetailDto
import com.stillshelf.app.data.api.AudiobookshelfBookmarkDto
import com.stillshelf.app.data.api.AudiobookshelfNamedEntityDto
import com.stillshelf.app.data.mapper.toModel
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val serverDao: ServerDao,
    private val libraryDao: LibraryDao,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val audiobookshelfApi: AudiobookshelfApi
) : SessionRepository {
    companion object {
        private const val HOME_FEED_CACHE_MAX_AGE_MS: Long = 10 * 60 * 1000L
        private const val CONTENT_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
        private const val DETAIL_CACHE_MAX_AGE_MS: Long = 30 * 60 * 1000L
    }

    private data class TimedCacheEntry<T>(
        val value: T,
        val savedAtMs: Long
    )

    private data class ActiveConnection(
        val server: ServerEntity,
        val token: String,
        val library: LibraryEntity?
    )

    private val cacheMutex = Mutex()
    private val booksCache = mutableMapOf<String, TimedCacheEntry<List<BookSummary>>>()
    private val authorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val narratorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val seriesCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val collectionsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val bookDetailCache = mutableMapOf<String, TimedCacheEntry<BookDetail>>()

    override fun observeSessionState(): Flow<SessionState> = sessionPreferences.state.map { prefState ->
        SessionState(
            activeServerId = prefState.activeServerId,
            activeLibraryId = prefState.activeLibraryId
        )
    }

    override fun observeServers(): Flow<List<Server>> = serverDao.observeServers().map { servers ->
        servers.map { it.toModel() }
    }

    override suspend fun updateServer(
        serverId: String,
        name: String,
        baseUrl: String
    ): AppResult<Unit> {
        if (name.isBlank()) return AppResult.Error("Server name is required.")
        if (!isHttpUrl(baseUrl)) return AppResult.Error("Base URL must use http or https.")
        val existing = serverDao.getById(serverId) ?: return AppResult.Error("Server not found.")
        val normalizedBaseUrl = normalizedBaseUrl(baseUrl)
        return try {
            val updatedRows = serverDao.update(
                serverId = existing.id,
                name = name.trim(),
                baseUrl = normalizedBaseUrl
            )
            if (updatedRows <= 0) {
                AppResult.Error("Unable to update server.")
            } else {
                clearContentCaches()
                AppResult.Success(Unit)
            }
        } catch (t: Throwable) {
            AppResult.Error("Unable to update server.", t)
        }
    }

    override suspend fun deleteServer(serverId: String): AppResult<Unit> {
        val existing = serverDao.getById(serverId) ?: return AppResult.Error("Server not found.")
        return try {
            secureTokenStorage.clearToken(existing.id)
            serverDao.deleteById(existing.id)
            clearContentCaches()
            val session = sessionPreferences.state.first()
            if (session.activeServerId == existing.id) {
                val remainingServers = serverDao.getAll()
                val nextServer = remainingServers.firstOrNull()
                sessionPreferences.setActiveLibraryId(null)
                if (nextServer != null) {
                    sessionPreferences.setActiveServerId(nextServer.id)
                } else {
                    sessionPreferences.setActiveServerId(null)
                    sessionPreferences.setLastPlayedBookId(null)
                    sessionPreferences.clearCachedHomeFeed()
                }
            }
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to delete server.", t)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLibrariesForActiveServer(): Flow<List<Library>> {
        return sessionPreferences.state
            .map { it.activeServerId }
            .distinctUntilChanged()
            .flatMapLatest { serverId ->
                if (serverId.isNullOrBlank()) {
                    flowOf(emptyList())
                } else {
                    libraryDao.observeLibraries(serverId).map { libraries ->
                        libraries.map { it.toModel() }
                    }
                }
            }
    }

    override suspend fun setActiveServer(serverId: String): AppResult<Unit> {
        val server = serverDao.getById(serverId)
            ?: return AppResult.Error("Server not found.")

        val token = secureTokenStorage.getToken(server.id)
            ?: return AppResult.Error("No saved session for this server. Please log in again.")

        when (val syncResult = refreshLibrariesFromServer(server, token)) {
            is AppResult.Success -> Unit
            is AppResult.Error -> return syncResult
        }

        sessionPreferences.setActiveServerId(server.id)
        sessionPreferences.setActiveLibraryId(null)
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun setActiveLibrary(libraryId: String): AppResult<Unit> {
        val library = libraryDao.getById(libraryId)
            ?: return AppResult.Error("Library not found.")

        val activeServerId = sessionPreferences.state.first().activeServerId
            ?: return AppResult.Error("No active server selected.")
        if (library.serverId != activeServerId) {
            return AppResult.Error("Library does not belong to the active server.")
        }

        sessionPreferences.setActiveLibraryId(library.id)
        return AppResult.Success(Unit)
    }

    override suspend fun signOutActiveSession(): AppResult<Unit> {
        return try {
            val servers = serverDao.getAll()
            servers.forEach { server ->
                runCatching { secureTokenStorage.clearToken(server.id) }
            }
            serverDao.deleteAll()
            sessionPreferences.setLastPlayedBookId(null)
            sessionPreferences.clearCachedHomeFeed()
            sessionPreferences.setActiveLibraryId(null)
            sessionPreferences.setActiveServerId(null)
            clearContentCaches()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to sign out.", t)
        }
    }

    override suspend fun refreshLibrariesForActiveServer(): AppResult<Unit> {
        val connection = when (val result = getActiveConnection(requireLibrary = false)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        return refreshLibrariesFromServer(connection.server, connection.token)
    }

    override suspend fun addServerAndLogin(
        serverName: String,
        baseUrl: String,
        username: String,
        password: String
    ): AppResult<Unit> {
        if (serverName.isBlank()) {
            return AppResult.Error("Server name is required.")
        }
        if (!isHttpUrl(baseUrl)) {
            return AppResult.Error("Base URL must use http or https.")
        }
        if (username.isBlank() || password.isBlank()) {
            return AppResult.Error("Username and password are required.")
        }

        val normalizedBaseUrl = normalizedBaseUrl(baseUrl)
        val loginResult = audiobookshelfApi.login(
            baseUrl = normalizedBaseUrl,
            username = username.trim(),
            password = password
        )
        if (loginResult.isFailure) {
            return AppResult.Error(
                message = loginResult.exceptionOrNull()?.message ?: "Unable to login.",
                cause = loginResult.exceptionOrNull()
            )
        }

        val token = loginResult.getOrThrow()
        val librariesResult = audiobookshelfApi.getLibraries(
            baseUrl = normalizedBaseUrl,
            authToken = token
        )
        if (librariesResult.isFailure) {
            return AppResult.Error(
                message = librariesResult.exceptionOrNull()?.message ?: "Unable to fetch libraries.",
                cause = librariesResult.exceptionOrNull()
            )
        }

        val libraries = librariesResult.getOrThrow()
        if (libraries.isEmpty()) {
            return AppResult.Error("No libraries were returned for this account.")
        }

        val serverId = UUID.randomUUID().toString()
        return try {
            val serverEntity = ServerEntity(
                id = serverId,
                name = serverName.trim(),
                baseUrl = normalizedBaseUrl,
                createdAt = System.currentTimeMillis()
            )

            appDatabase.withTransaction {
                serverDao.insert(serverEntity)
                replaceLibrariesForServer(
                    serverId = serverId,
                    libraries = libraries,
                    serverFallback = serverEntity
                )
            }
            secureTokenStorage.saveToken(serverId, token)
            sessionPreferences.setActiveServerId(serverId)
            sessionPreferences.setActiveLibraryId(null)
            clearContentCaches()

            AppResult.Success(Unit)
        } catch (t: Throwable) {
            rollbackFailedServerSetup(serverId)
            AppResult.Error("Unable to save server session.", t)
        }
    }

    override suspend fun testServerConnection(baseUrl: String): AppResult<String> {
        if (!isHttpUrl(baseUrl)) {
            return AppResult.Error("Base URL must use http or https.")
        }

        val normalizedBaseUrl = normalizedBaseUrl(baseUrl)
        val result = audiobookshelfApi.getServerStatus(normalizedBaseUrl)
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to reach server.",
                cause = result.exceptionOrNull()
            )
        }

        val status = result.getOrThrow()
        if (!status.app.equals("audiobookshelf", ignoreCase = true)) {
            return AppResult.Error("Connected, but this is not an Audiobookshelf server.")
        }

        val versionSuffix = status.serverVersion?.let { " ($it)" }.orEmpty()
        return AppResult.Success("Connected to Audiobookshelf$versionSuffix")
    }

    override suspend fun fetchBooksForActiveLibrary(
        limit: Int,
        page: Int,
        forceRefresh: Boolean
    ): AppResult<List<BookSummary>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "books:$limit:$page"
        )
        if (!forceRefresh) {
            getFreshCache(booksCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(booksCache, cacheKey)

        val itemsResult = audiobookshelfApi.getLibraryItems(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            limit = limit,
            page = page,
            sortBy = "media.metadata.title",
            desc = false
        )

        if (itemsResult.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = itemsResult.exceptionOrNull()?.message ?: "Unable to load books.",
                cause = itemsResult.exceptionOrNull()
            )
        }

        val books = itemsResult.getOrThrow().map { item ->
            item.toBookSummary(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token
            )
        }
        putCache(booksCache, cacheKey, books)

        return AppResult.Success(books)
    }

    override suspend fun fetchAuthorsForActiveLibrary(
        limit: Int,
        page: Int,
        forceRefresh: Boolean
    ): AppResult<List<NamedEntitySummary>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "authors:$limit:$page"
        )
        if (!forceRefresh) {
            getFreshCache(authorsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(authorsCache, cacheKey)

        val result = audiobookshelfApi.getAuthors(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            limit = limit,
            page = page
        )
        if (result.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to load authors.",
                cause = result.exceptionOrNull()
            )
        }

        val authors = result.getOrThrow().map {
            it.toModel(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                includeAuthorImage = true
            )
        }
        putCache(authorsCache, cacheKey, authors)
        return AppResult.Success(authors)
    }

    override suspend fun fetchNarratorsForActiveLibrary(
        forceRefresh: Boolean
    ): AppResult<List<NamedEntitySummary>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "narrators"
        )
        if (!forceRefresh) {
            getFreshCache(narratorsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(narratorsCache, cacheKey)

        val result = audiobookshelfApi.getNarrators(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id
        )
        if (result.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to load narrators.",
                cause = result.exceptionOrNull()
            )
        }

        val narrators = result.getOrThrow().map {
            it.toModel(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token
            )
        }
        putCache(narratorsCache, cacheKey, narrators)
        return AppResult.Success(narrators)
    }

    override suspend fun fetchSeriesForActiveLibrary(
        limit: Int,
        page: Int,
        forceRefresh: Boolean
    ): AppResult<List<NamedEntitySummary>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "series:$limit:$page"
        )
        if (!forceRefresh) {
            getFreshCache(seriesCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(seriesCache, cacheKey)

        val result = audiobookshelfApi.getSeries(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            limit = limit,
            page = page
        )
        if (result.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to load series.",
                cause = result.exceptionOrNull()
            )
        }

        val series = result.getOrThrow().map {
            it.toModel(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token
            )
        }
        putCache(seriesCache, cacheKey, series)
        return AppResult.Success(series)
    }

    override suspend fun fetchCollectionsForActiveLibrary(
        forceRefresh: Boolean
    ): AppResult<List<NamedEntitySummary>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "collections"
        )
        if (!forceRefresh) {
            getFreshCache(collectionsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(collectionsCache, cacheKey)

        val result = audiobookshelfApi.getCollections(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id
        )
        if (result.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to load collections.",
                cause = result.exceptionOrNull()
            )
        }

        val collections = result.getOrThrow()
            .asSequence()
            .filterNot { it.name.startsWith("StillShelf Probe", ignoreCase = true) }
            .map {
                NamedEntitySummary(
                    id = it.id,
                    name = if (it.name.equals("StillShelf Favorites", ignoreCase = true)) {
                        "StillShelf Collection"
                    } else {
                        it.name
                    }
                )
            }
            .toList()
        putCache(collectionsCache, cacheKey, collections)
        return AppResult.Success(collections)
    }

    override suspend fun fetchBookDetail(
        bookId: String,
        forceRefresh: Boolean
    ): AppResult<BookDetail> {
        if (bookId.isBlank()) {
            return AppResult.Error("Invalid book id.")
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val cacheKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "bookDetail:$bookId"
        )
        if (!forceRefresh) {
            getFreshCache(bookDetailCache, cacheKey, DETAIL_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(bookDetailCache, cacheKey)

        return try {
            coroutineScope {
                val detailDeferred = async {
                    audiobookshelfApi.getItemDetail(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        itemId = bookId
                    )
                }
                val bookmarksDeferred = async {
                    audiobookshelfApi.getBookmarks(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token
                    )
                }

                val detailResult = detailDeferred.await()
                if (detailResult.isFailure) {
                    if (staleCache != null) {
                        return@coroutineScope AppResult.Success(staleCache)
                    }
                    return@coroutineScope AppResult.Error(
                        message = detailResult.exceptionOrNull()?.message ?: "Unable to load book detail.",
                        cause = detailResult.exceptionOrNull()
                    )
                }

                val bookmarks = bookmarksDeferred.await()
                    .getOrDefault(emptyList())
                    .asSequence()
                    .filter { it.libraryItemId == bookId }
                    .sortedBy { it.timeSeconds ?: Double.MAX_VALUE }
                    .map { it.toModel() }
                    .toList()

                val detail = detailResult.getOrThrow().toModel(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    bookmarks = bookmarks
                )
                putCache(bookDetailCache, cacheKey, detail)
                AppResult.Success(detail)
            }
        } catch (t: Throwable) {
            if (staleCache != null) {
                AppResult.Success(staleCache)
            } else {
                AppResult.Error("Unable to load book detail.", t)
            }
        }
    }

    override suspend fun fetchPlaybackSource(bookId: String): AppResult<PlaybackSource> {
        if (bookId.isBlank()) {
            return AppResult.Error("Invalid book id.")
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val detailResult = audiobookshelfApi.getItemDetail(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId
        )
        if (detailResult.isFailure) {
            return AppResult.Error(
                message = detailResult.exceptionOrNull()?.message ?: "Unable to load playback stream.",
                cause = detailResult.exceptionOrNull()
            )
        }

        val detail = detailResult.getOrThrow()
        val streamPath = detail.streamPath
            ?: return AppResult.Error("This book does not expose a playable audio stream.")

        return AppResult.Success(
            PlaybackSource(
                book = detail.toBookSummary(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token
                ),
                streamUrl = audiobookshelfApi.buildPlaybackUrl(
                    baseUrl = connection.server.baseUrl,
                    streamPath = streamPath,
                    authToken = connection.token
                )
            )
        )
    }

    override suspend fun fetchPlaybackProgress(bookId: String): AppResult<PlaybackProgress?> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val directResult = audiobookshelfApi.getMediaProgressForItem(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId
        )
        if (directResult.isSuccess) {
            val progress = directResult.getOrThrow()
            if (progress != null) {
                return AppResult.Success(
                    PlaybackProgress(
                        progressPercent = progress.progressPercent,
                        currentTimeSeconds = progress.currentTimeSeconds,
                        durationSeconds = progress.durationSeconds
                    )
                )
            }
        }

        val listResult = audiobookshelfApi.getMediaProgress(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token
        )
        if (listResult.isFailure) {
            return AppResult.Error(
                message = listResult.exceptionOrNull()?.message ?: "Unable to load playback progress.",
                cause = listResult.exceptionOrNull()
            )
        }
        val fallback = listResult.getOrThrow().firstOrNull { it.libraryItemId == bookId }
        return AppResult.Success(
            fallback?.let {
                PlaybackProgress(
                    progressPercent = it.progressPercent,
                    currentTimeSeconds = it.currentTimeSeconds,
                    durationSeconds = it.durationSeconds
                )
            }
        )
    }

    override suspend fun syncPlaybackProgress(
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val syncResult = audiobookshelfApi.updateMediaProgressForItem(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId,
            currentTimeSeconds = currentTimeSeconds.coerceAtLeast(0.0),
            durationSeconds = durationSeconds,
            isFinished = isFinished
        )
        if (syncResult.isFailure) {
            return AppResult.Error(
                message = syncResult.exceptionOrNull()?.message ?: "Unable to sync playback progress.",
                cause = syncResult.exceptionOrNull()
            )
        }
        return AppResult.Success(Unit)
    }

    override suspend fun addBookToDefaultCollection(bookId: String): AppResult<String> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val defaultCollectionName = "StillShelf Collection"
        val legacyCollectionName = "StillShelf Favorites"

        val collectionsResult = audiobookshelfApi.getCollections(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id
        )
        val existingCollection = collectionsResult.getOrDefault(emptyList())
            .firstOrNull {
                it.name.equals(defaultCollectionName, ignoreCase = true) ||
                    it.name.equals(legacyCollectionName, ignoreCase = true)
            }

        val collectionId = if (existingCollection != null) {
            existingCollection.id
        } else {
            val createdWithBook = audiobookshelfApi.createCollectionWithBook(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                libraryId = library.id,
                name = defaultCollectionName,
                bookId = bookId
            )
            if (createdWithBook.isSuccess) {
                return AppResult.Success("Collections")
            }

            val created = audiobookshelfApi.createCollection(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                libraryId = library.id,
                name = defaultCollectionName
            )
            if (created.isFailure) {
                return AppResult.Error(
                    message = createdWithBook.exceptionOrNull()?.message
                        ?: created.exceptionOrNull()?.message
                        ?: "Unable to create collection.",
                    cause = createdWithBook.exceptionOrNull() ?: created.exceptionOrNull()
                )
            }
            created.getOrThrow().id
        }

        val addResult = audiobookshelfApi.addBookToCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = collectionId,
            bookId = bookId
        )
        if (addResult.isFailure) {
            return AppResult.Error(
                message = addResult.exceptionOrNull()?.message ?: "Unable to add book to collection.",
                cause = addResult.exceptionOrNull()
            )
        }
        return AppResult.Success("Collections")
    }

    override suspend fun setLastPlayedBookId(bookId: String?): AppResult<Unit> {
        return try {
            sessionPreferences.setLastPlayedBookId(bookId)
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to save last played book.", t)
        }
    }

    override suspend fun searchActiveLibrary(
        query: String,
        limit: Int
    ): AppResult<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return AppResult.Success(
                SearchResults(
                    books = emptyList(),
                    authors = emptyList(),
                    series = emptyList(),
                    narrators = emptyList()
                )
            )
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")

        return try {
            coroutineScope {
                val booksDeferred = async {
                    audiobookshelfApi.getLibraryItems(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        libraryId = library.id,
                        limit = limit.coerceAtLeast(1),
                        page = 0,
                        searchQuery = trimmed
                    )
                }
                val entitiesDeferred = async {
                    audiobookshelfApi.searchLibrary(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        libraryId = library.id,
                        query = trimmed
                    )
                }

                val booksResult = booksDeferred.await()
                val entitiesResult = entitiesDeferred.await()
                if (booksResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = booksResult.exceptionOrNull()?.message ?: "Unable to search books.",
                        cause = booksResult.exceptionOrNull()
                    )
                }
                if (entitiesResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = entitiesResult.exceptionOrNull()?.message ?: "Unable to search entities.",
                        cause = entitiesResult.exceptionOrNull()
                    )
                }

                val books = booksResult.getOrThrow()
                    .map {
                        it.toBookSummary(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token
                        )
                    }
                val entities = entitiesResult.getOrThrow()

                AppResult.Success(
                    SearchResults(
                        books = books,
                        authors = entities.authors.map {
                            it.toModel(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token
                            )
                        },
                        series = entities.series.map {
                            it.toModel(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token
                            )
                        },
                        narrators = entities.narrators.map {
                            it.toModel(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token
                            )
                        }
                    )
                )
            }
        } catch (t: Throwable) {
            AppResult.Error("Unable to search library.", t)
        }
    }

    override suspend fun fetchMiniPlayerItem(): AppResult<ContinueListeningItem?> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val lastPlayedBookId = sessionPreferences.state.first().lastPlayedBookId

        val itemsResult = audiobookshelfApi.getItemsInProgress(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token
        )
        if (itemsResult.isFailure) {
            return AppResult.Error(
                message = itemsResult.exceptionOrNull()?.message ?: "Unable to load mini player item.",
                cause = itemsResult.exceptionOrNull()
            )
        }

        val progressResult = audiobookshelfApi.getMediaProgress(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token
        )
        if (progressResult.isFailure) {
            return AppResult.Error(
                message = progressResult.exceptionOrNull()?.message ?: "Unable to load mini player progress.",
                cause = progressResult.exceptionOrNull()
            )
        }

        val progressByItemId = progressResult.getOrThrow().associateBy { it.libraryItemId }
        val inProgressItems = itemsResult.getOrThrow().filter { it.libraryId == library.id }
        val preferredItem = if (lastPlayedBookId.isNullOrBlank()) {
            null
        } else {
            inProgressItems.firstOrNull { it.id == lastPlayedBookId }
        }

        if (preferredItem != null) {
            val progress = progressByItemId[preferredItem.id]
            return AppResult.Success(
                ContinueListeningItem(
                    book = preferredItem.toBookSummary(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token
                    ),
                    progressPercent = progress?.progressPercent,
                    currentTimeSeconds = progress?.currentTimeSeconds
                )
            )
        }

        if (!lastPlayedBookId.isNullOrBlank()) {
            val detailResult = audiobookshelfApi.getItemDetail(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                itemId = lastPlayedBookId
            )
            if (detailResult.isSuccess) {
                val detail = detailResult.getOrThrow()
                if (detail.libraryId == library.id) {
                    val progress = progressByItemId[detail.id]
                    return AppResult.Success(
                        ContinueListeningItem(
                            book = detail.toBookSummary(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token
                            ),
                            progressPercent = progress?.progressPercent,
                            currentTimeSeconds = progress?.currentTimeSeconds
                        )
                    )
                }
            }
        }

        val fallbackItem = inProgressItems.firstOrNull()
        if (fallbackItem == null) {
            return AppResult.Success(null)
        }
        val progress = progressByItemId[fallbackItem.id]

        return AppResult.Success(
            ContinueListeningItem(
                book = fallbackItem.toBookSummary(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token
                ),
                progressPercent = progress?.progressPercent,
                currentTimeSeconds = progress?.currentTimeSeconds
            )
        )
    }

    override suspend fun fetchCachedHomeFeed(maxAgeMs: Long?): AppResult<HomeFeed?> {
        val activeLibraryId = sessionPreferences.state.first().activeLibraryId
            ?: return AppResult.Success(null)
        val cached = sessionPreferences.getCachedHomeFeed()
            ?: return AppResult.Success(null)
        if (cached.libraryId != activeLibraryId) {
            return AppResult.Success(null)
        }
        val cacheAgeMs = (System.currentTimeMillis() - cached.savedAtMs).coerceAtLeast(0L)
        val effectiveMaxAgeMs = maxAgeMs ?: HOME_FEED_CACHE_MAX_AGE_MS
        if (cacheAgeMs > effectiveMaxAgeMs) {
            return AppResult.Success(null)
        }

        val parsed = runCatching { parseCachedHomeFeed(cached.payload) }.getOrNull()
            ?: return AppResult.Success(null)
        return AppResult.Success(parsed)
    }

    override suspend fun fetchHomeFeed(
        continueLimit: Int,
        recentlyAddedLimit: Int
    ): AppResult<HomeFeed> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")

        return try {
            coroutineScope {
                val inProgressDeferred = async {
                    audiobookshelfApi.getItemsInProgress(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token
                    )
                }
                val mediaProgressDeferred = async {
                    audiobookshelfApi.getMediaProgress(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token
                    )
                }
                val recentDeferred = async {
                    audiobookshelfApi.getLibraryItems(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        libraryId = library.id,
                        limit = recentlyAddedLimit.coerceAtLeast(1),
                        page = 0,
                        sortBy = "addedAt",
                        desc = true
                    )
                }
                val allBooksDeferred = async {
                    audiobookshelfApi.getLibraryItems(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        libraryId = library.id,
                        limit = 400,
                        page = 0,
                        sortBy = "media.metadata.title",
                        desc = false
                    )
                }
                val authorsDeferred = async {
                    audiobookshelfApi.getAuthors(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        libraryId = library.id,
                        limit = 300,
                        page = 0
                    )
                }

                val inProgressResult = inProgressDeferred.await()
                val mediaProgressResult = mediaProgressDeferred.await()
                val recentResult = recentDeferred.await()
                val allBooksResult = allBooksDeferred.await()
                val authorsResult = authorsDeferred.await()

                if (inProgressResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = inProgressResult.exceptionOrNull()?.message
                            ?: "Unable to load continue listening.",
                        cause = inProgressResult.exceptionOrNull()
                    )
                }
                if (mediaProgressResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = mediaProgressResult.exceptionOrNull()?.message
                            ?: "Unable to load playback progress.",
                        cause = mediaProgressResult.exceptionOrNull()
                    )
                }
                if (recentResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = recentResult.exceptionOrNull()?.message
                            ?: "Unable to load recently added books.",
                        cause = recentResult.exceptionOrNull()
                    )
                }

                val mediaProgressByItemId = mediaProgressResult.getOrThrow().associateBy { it.libraryItemId }

                val continueListening = inProgressResult.getOrThrow()
                    .asSequence()
                    .filter { it.libraryId == library.id }
                    .take(continueLimit.coerceAtLeast(1))
                    .map { item ->
                        val progress = mediaProgressByItemId[item.id]
                        ContinueListeningItem(
                            book = item.toBookSummary(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token
                            ),
                            progressPercent = progress?.progressPercent,
                            currentTimeSeconds = progress?.currentTimeSeconds
                        )
                    }
                    .toList()

                val recentlyAdded = recentResult.getOrThrow()
                    .map { item ->
                        item.toBookSummary(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token
                        )
                    }
                val allBooks = if (allBooksResult.isSuccess) {
                    allBooksResult.getOrThrow().map { item ->
                        item.toBookSummary(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token
                        )
                    }
                } else {
                    emptyList()
                }
                val seriesCountByKey = allBooks
                    .asSequence()
                    .mapNotNull { item ->
                        val series = item.seriesName?.trim().orEmpty()
                        if (series.isBlank()) {
                            null
                        } else {
                            normalizeSeriesKey(series) to 1
                        }
                    }
                    .groupingBy { it.first }
                    .fold(0) { acc, _ -> acc + 1 }
                val recentSeries = recentlyAdded
                    .asSequence()
                    .mapNotNull { book ->
                        val series = book.seriesName?.trim().orEmpty()
                        if (series.isBlank()) null else normalizeSeriesKey(series) to book
                    }
                    .distinctBy { (key, _) -> key }
                    .mapNotNull { (key, leadBook) ->
                        val count = seriesCountByKey[key] ?: 0
                        if (count <= 1) {
                            null
                        } else {
                            SeriesStackSummary(
                                seriesName = leadBook.seriesName.orEmpty(),
                                leadBook = leadBook,
                                count = count
                            )
                        }
                    }
                    .toList()
                val authorImageUrls = if (authorsResult.isSuccess) {
                    authorsResult.getOrThrow()
                        .asSequence()
                        .map { dto ->
                            dto.toModel(
                                baseUrl = connection.server.baseUrl,
                                authToken = connection.token,
                                includeAuthorImage = true
                            )
                        }
                        .filter { !it.imageUrl.isNullOrBlank() && it.name.isNotBlank() }
                        .associate { normalizeAuthorKey(it.name) to it.imageUrl.orEmpty() }
                } else {
                    emptyMap()
                }

                val feed = HomeFeed(
                    libraryName = library.name,
                    continueListening = continueListening,
                    recentlyAdded = recentlyAdded,
                    recentSeries = recentSeries,
                    authorImageUrls = authorImageUrls
                )
                runCatching {
                    sessionPreferences.setCachedHomeFeed(
                        libraryId = library.id,
                        payload = serializeHomeFeed(feed),
                        savedAtMs = System.currentTimeMillis()
                    )
                }
                AppResult.Success(feed)
            }
        } catch (t: Throwable) {
            AppResult.Error("Unable to load home feed.", t)
        }
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme == "http" || uri.scheme == "https"
    }

    private suspend fun refreshLibrariesFromServer(
        server: ServerEntity,
        token: String
    ): AppResult<Unit> {
        return try {
            val librariesResult = audiobookshelfApi.getLibraries(
                baseUrl = server.baseUrl,
                authToken = token
            )

            if (librariesResult.isFailure) {
                return AppResult.Error(
                    message = librariesResult.exceptionOrNull()?.message
                        ?: "Unable to refresh server libraries.",
                    cause = librariesResult.exceptionOrNull()
                )
            }

            val libraries = librariesResult.getOrThrow()
            if (libraries.isEmpty()) {
                return AppResult.Error("No libraries were returned for this server.")
            }

            appDatabase.withTransaction {
                replaceLibrariesForServer(
                    serverId = server.id,
                    libraries = libraries,
                    serverFallback = server
                )
            }
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to refresh server libraries.", t)
        }
    }

    private suspend fun replaceLibrariesForServer(
        serverId: String,
        libraries: List<AudiobookshelfLibraryDto>,
        serverFallback: ServerEntity? = null
    ) {
        val entities = libraries
            .asSequence()
            .map { it.id.trim() to it.name.trim() }
            .filter { (id, _) -> id.isNotEmpty() }
            .distinctBy { (id, _) -> id }
            .map { (id, name) ->
                LibraryEntity(
                    id = id,
                    serverId = serverId,
                    name = name.ifBlank { "Unnamed library" }
                )
            }
            .toList()

        ensureServerExists(serverId = serverId, serverFallback = serverFallback)
        libraryDao.deleteByServerId(serverId)
        if (entities.isEmpty()) return

        runCatching {
            libraryDao.insertAll(entities)
        }.getOrElse {
            // Fallback path: re-ensure parent and insert one-by-one so one bad row can't crash login.
            ensureServerExists(serverId = serverId, serverFallback = serverFallback)
            libraryDao.deleteByServerId(serverId)

            var insertedCount = 0
            entities.forEach { entity ->
                runCatching {
                    libraryDao.insert(entity)
                    insertedCount += 1
                }
            }

            if (insertedCount <= 0) {
                throw IllegalStateException("Unable to persist libraries for server.")
            }
        }
    }

    private suspend fun ensureServerExists(
        serverId: String,
        serverFallback: ServerEntity?
    ) {
        if (serverDao.getById(serverId) != null) return
        if (serverFallback != null) {
            serverDao.insert(serverFallback)
        }
        if (serverDao.getById(serverId) == null) {
            throw IllegalStateException("Server row not found for libraries sync.")
        }
    }

    private suspend fun rollbackFailedServerSetup(serverId: String) {
        runCatching { secureTokenStorage.clearToken(serverId) }
        runCatching { libraryDao.deleteByServerId(serverId) }
        runCatching { serverDao.deleteById(serverId) }
        clearContentCaches()
    }

    private suspend fun getActiveConnection(requireLibrary: Boolean): AppResult<ActiveConnection> {
        val session = sessionPreferences.state.first()
        val activeServerId = session.activeServerId
            ?: return AppResult.Error("No active server selected.")
        val server = serverDao.getById(activeServerId)
            ?: return AppResult.Error("Active server not found.")
        val token = secureTokenStorage.getToken(server.id)
            ?: return AppResult.Error("No saved session for this server. Please log in again.")

        if (!requireLibrary) {
            return AppResult.Success(
                ActiveConnection(
                    server = server,
                    token = token,
                    library = null
                )
            )
        }

        val activeLibraryId = session.activeLibraryId
            ?: return AppResult.Error("No active library selected.")
        val library = libraryDao.getById(activeLibraryId)
            ?: return AppResult.Error("Active library not found.")
        if (library.serverId != server.id) {
            return AppResult.Error("Active library does not belong to the active server.")
        }

        return AppResult.Success(
            ActiveConnection(
                server = server,
                token = token,
                library = library
            )
        )
    }

    private fun normalizedBaseUrl(value: String): String = value.trim().removeSuffix("/")

    private fun contentCacheKey(serverId: String, libraryId: String, suffix: String): String {
        return "$serverId|$libraryId|$suffix"
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

    private fun clearContentCaches() {
        booksCache.clear()
        authorsCache.clear()
        narratorsCache.clear()
        seriesCache.clear()
        collectionsCache.clear()
        bookDetailCache.clear()
    }

    private fun normalizeAuthorKey(name: String): String = name.trim().lowercase()
    private fun normalizeSeriesKey(name: String): String = name.trim().lowercase()

    private fun serializeHomeFeed(feed: HomeFeed): String {
        val root = JSONObject()
        root.put("libraryName", feed.libraryName)
        root.put(
            "authorImageUrls",
            JSONObject().apply {
                feed.authorImageUrls.forEach { (authorKey, imageUrl) ->
                    put(authorKey, imageUrl)
                }
            }
        )
        root.put(
            "continueListening",
            JSONArray().apply {
                feed.continueListening.forEach { item ->
                    put(
                        JSONObject()
                            .put("book", serializeBook(item.book))
                            .put("progressPercent", item.progressPercent)
                            .put("currentTimeSeconds", item.currentTimeSeconds)
                    )
                }
            }
        )
        root.put(
            "recentlyAdded",
            JSONArray().apply {
                feed.recentlyAdded.forEach { book ->
                    put(serializeBook(book))
                }
            }
        )
        root.put(
            "recentSeries",
            JSONArray().apply {
                feed.recentSeries.forEach { series ->
                    put(
                        JSONObject()
                            .put("seriesName", series.seriesName)
                            .put("count", series.count)
                            .put("leadBook", serializeBook(series.leadBook))
                    )
                }
            }
        )
        return root.toString()
    }

    private fun parseCachedHomeFeed(payload: String): HomeFeed {
        val root = JSONObject(payload)
        val continueItems = buildList {
            val source = root.optJSONArray("continueListening") ?: JSONArray()
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val book = item.optJSONObject("book")?.let(::parseBook) ?: continue
                add(
                    ContinueListeningItem(
                        book = book,
                        progressPercent = item.optDoubleOrNull("progressPercent"),
                        currentTimeSeconds = item.optDoubleOrNull("currentTimeSeconds")
                    )
                )
            }
        }
        val recentItems = buildList {
            val source = root.optJSONArray("recentlyAdded") ?: JSONArray()
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(parseBook(item))
            }
        }
        val recentSeries = buildList {
            val source = root.optJSONArray("recentSeries") ?: JSONArray()
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val seriesName = item.optString("seriesName").trim()
                val count = item.optInt("count")
                val leadBook = item.optJSONObject("leadBook")?.let(::parseBook) ?: continue
                if (seriesName.isNotBlank() && count > 1) {
                    add(
                        SeriesStackSummary(
                            seriesName = seriesName,
                            leadBook = leadBook,
                            count = count
                        )
                    )
                }
            }
        }
        return HomeFeed(
            libraryName = root.optString("libraryName").ifBlank { "Library" },
            continueListening = continueItems,
            recentlyAdded = recentItems,
            recentSeries = recentSeries,
            authorImageUrls = buildMap {
                val source = root.optJSONObject("authorImageUrls") ?: JSONObject()
                val keys = source.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next().orEmpty()
                    val key = rawKey.trim().lowercase()
                    val value = source.optString(rawKey).ifBlank { null } ?: continue
                    if (key.isNotBlank()) {
                        put(key, value)
                    }
                }
            }
        )
    }

    private fun serializeBook(book: BookSummary): JSONObject {
        return JSONObject()
            .put("id", book.id)
            .put("libraryId", book.libraryId)
            .put("title", book.title)
            .put("authorName", book.authorName)
            .put("narratorName", book.narratorName)
            .put("durationSeconds", book.durationSeconds)
            .put("coverUrl", book.coverUrl)
            .put("seriesName", book.seriesName)
            .put("seriesSequence", book.seriesSequence)
            .put("genres", JSONArray(book.genres))
            .put("publishedYear", book.publishedYear)
            .put("addedAtMs", book.addedAtMs)
            .put("progressPercent", book.progressPercent)
            .put("currentTimeSeconds", book.currentTimeSeconds)
            .put("isFinished", book.isFinished)
    }

    private fun parseBook(source: JSONObject): BookSummary {
        val genres = buildList {
            val array = source.optJSONArray("genres") ?: JSONArray()
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (!value.isNullOrBlank()) add(value)
            }
        }
        return BookSummary(
            id = source.optString("id"),
            libraryId = source.optString("libraryId"),
            title = source.optString("title"),
            authorName = source.optString("authorName").ifBlank { "Unknown Author" },
            narratorName = source.optStringOrNull("narratorName"),
            durationSeconds = source.optDoubleOrNull("durationSeconds"),
            coverUrl = source.optStringOrNull("coverUrl"),
            seriesName = source.optStringOrNull("seriesName"),
            seriesSequence = source.optDoubleOrNull("seriesSequence"),
            genres = genres,
            publishedYear = source.optStringOrNull("publishedYear"),
            addedAtMs = source.optLongOrNull("addedAtMs"),
            progressPercent = source.optDoubleOrNull("progressPercent"),
            currentTimeSeconds = source.optDoubleOrNull("currentTimeSeconds"),
            isFinished = source.optBoolean("isFinished")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).ifBlank { null }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun AudiobookshelfLibraryItemDto.toBookSummary(
        baseUrl: String,
        authToken: String
    ): BookSummary {
        return BookSummary(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = authorName,
            narratorName = narratorName,
            durationSeconds = durationSeconds,
            coverUrl = audiobookshelfApi.buildCoverUrl(baseUrl, id, authToken),
            seriesName = seriesName,
            seriesSequence = seriesSequence,
            genres = genres,
            publishedYear = publishedYear,
            addedAtMs = addedAtMs,
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            isFinished = isFinished
        )
    }

    private fun AudiobookshelfNamedEntityDto.toModel(
        baseUrl: String,
        authToken: String,
        includeAuthorImage: Boolean = false
    ): NamedEntitySummary {
        return NamedEntitySummary(
            id = id,
            name = name,
            subtitle = subtitle,
            description = description,
            imageUrl = if (includeAuthorImage) {
                imagePath?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: audiobookshelfApi.buildAuthorImageUrl(baseUrl, id, authToken)
            } else {
                null
            }
        )
    }

    private fun AudiobookshelfBookDetailDto.toModel(
        baseUrl: String,
        authToken: String,
        bookmarks: List<BookBookmark>
    ): BookDetail {
        val summary = BookSummary(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = authorName,
            narratorName = narratorName,
            durationSeconds = durationSeconds,
            coverUrl = audiobookshelfApi.buildCoverUrl(baseUrl, id, authToken),
            seriesName = seriesName,
            seriesSequence = seriesSequence,
            genres = genres,
            publishedYear = publishedYear
        )
        return BookDetail(
            book = summary,
            description = description,
            publishedYear = publishedYear,
            sizeBytes = sizeBytes,
            chapters = chapters.map {
                BookChapter(
                    title = it.title,
                    startSeconds = it.startSeconds,
                    endSeconds = it.endSeconds
                )
            },
            bookmarks = bookmarks
        )
    }

    private fun AudiobookshelfBookDetailDto.toBookSummary(
        baseUrl: String,
        authToken: String
    ): BookSummary {
        return BookSummary(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = authorName,
            narratorName = narratorName,
            durationSeconds = durationSeconds,
            coverUrl = audiobookshelfApi.buildCoverUrl(baseUrl, id, authToken),
            seriesName = seriesName,
            seriesSequence = seriesSequence,
            genres = genres,
            publishedYear = publishedYear
        )
    }

    private fun AudiobookshelfBookmarkDto.toModel(): BookBookmark {
        return BookBookmark(
            id = id,
            libraryItemId = libraryItemId,
            title = title,
            timeSeconds = timeSeconds
        )
    }
}
