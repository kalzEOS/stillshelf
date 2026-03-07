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
import com.stillshelf.app.core.model.BookmarkEntry
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
import com.stillshelf.app.core.util.UnfinishedProgressState
import com.stillshelf.app.core.util.resolveUnfinishedProgressState
import com.stillshelf.app.data.api.AudiobookshelfApi
import com.stillshelf.app.data.api.AudiobookshelfLibraryDto
import com.stillshelf.app.data.api.AudiobookshelfLibraryItemDto
import com.stillshelf.app.data.api.AudiobookshelfMediaProgressDto
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
import kotlinx.coroutines.awaitAll
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
        private const val CONTENT_CACHE_MAX_AGE_MS: Long = 20 * 60 * 1000L
        private const val DETAIL_CACHE_MAX_AGE_MS: Long = 30 * 60 * 1000L
        private const val FULL_LIBRARY_PAGE_SIZE: Int = 500
        private const val MAX_FULL_LIBRARY_PAGES: Int = 1_000
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

    private data class FinishedProgressSnapshot(
        val currentTimeSeconds: Double,
        val durationSeconds: Double?,
        val progressPercent: Double
    )

    private val cacheMutex = Mutex()
    private val finishedProgressSnapshots = mutableMapOf<String, FinishedProgressSnapshot>()
    private val booksCache = mutableMapOf<String, TimedCacheEntry<List<BookSummary>>>()
    private val authorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val narratorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val seriesCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val collectionsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val playlistsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val bookDetailCache = mutableMapOf<String, TimedCacheEntry<BookDetail>>()

    override fun observeSessionState(): Flow<SessionState> = sessionPreferences.state.map { prefState ->
        SessionState(
            activeServerId = prefState.activeServerId,
            activeLibraryId = prefState.activeLibraryId,
            requiresLibrarySelection = prefState.requiresLibrarySelection
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
        val duplicate = serverDao.getAll().firstOrNull { server ->
            server.id != existing.id &&
                normalizedBaseUrl(server.baseUrl).equals(normalizedBaseUrl, ignoreCase = true)
        }
        if (duplicate != null) {
            return AppResult.Error("A server with this base URL already exists.")
        }
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
            val nextServer = serverDao.getAll().firstOrNull { it.id != existing.id }
            secureTokenStorage.clearToken(existing.id)
            serverDao.deleteById(existing.id)
            sessionPreferences.setServerAvatarUri(existing.id, null)
            clearContentCaches()
            val session = sessionPreferences.state.first()
            if (session.activeServerId == existing.id) {
                sessionPreferences.setLastPlayedBookId(null)
                sessionPreferences.clearCachedHomeFeed()
                sessionPreferences.setActiveSelection(
                    serverId = nextServer?.id,
                    libraryId = null
                )
                sessionPreferences.setRequiresLibrarySelection(nextServer != null)
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

        sessionPreferences.setActiveSelection(serverId = server.id, libraryId = null)
        sessionPreferences.setLastPlayedBookId(null)
        sessionPreferences.clearCachedHomeFeed()
        sessionPreferences.setRequiresLibrarySelection(true)
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun setActiveLibrary(libraryId: String): AppResult<Unit> {
        val activeServerId = sessionPreferences.state.first().activeServerId
            ?: return AppResult.Error("No active server selected.")
        val library = libraryDao.getByServerAndId(activeServerId, libraryId)
            ?: return AppResult.Error("Library not found.")

        sessionPreferences.setActiveLibraryId(library.id)
        sessionPreferences.setRequiresLibrarySelection(false)
        return AppResult.Success(Unit)
    }

    override suspend fun signOutActiveSession(): AppResult<Unit> {
        return try {
            val session = sessionPreferences.state.first()
            val activeServerId = session.activeServerId
                ?: return AppResult.Error("No active server selected.")
            val nextServer = serverDao.getAll().firstOrNull { it.id != activeServerId }

            runCatching { secureTokenStorage.clearToken(activeServerId) }
            appDatabase.withTransaction {
                runCatching { libraryDao.deleteByServerId(activeServerId) }
                serverDao.deleteById(activeServerId)
            }
            sessionPreferences.setServerAvatarUri(activeServerId, null)
            sessionPreferences.setLastPlayedBookId(null)
            sessionPreferences.clearCachedHomeFeed()
            sessionPreferences.setActiveSelection(
                serverId = nextServer?.id,
                libraryId = null
            )
            sessionPreferences.setRequiresLibrarySelection(nextServer != null)
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
        val existingServer = serverDao.getAll().firstOrNull { server ->
            normalizedBaseUrl(server.baseUrl).equals(normalizedBaseUrl, ignoreCase = true)
        }
        if (existingServer != null && !secureTokenStorage.getToken(existingServer.id).isNullOrBlank()) {
            return AppResult.Error("This server already exists. Use the existing entry instead.")
        }

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

        val matchingServers = serverDao.getAll().filter { server ->
            normalizedBaseUrl(server.baseUrl).equals(normalizedBaseUrl, ignoreCase = true)
        }
        val matchingExistingServer = matchingServers.firstOrNull()
        val serverId = matchingExistingServer?.id ?: UUID.randomUUID().toString()
        return try {
            val serverEntity = (matchingExistingServer ?: ServerEntity(
                id = serverId,
                name = serverName.trim(),
                baseUrl = normalizedBaseUrl,
                createdAt = System.currentTimeMillis()
            )).copy(
                name = serverName.trim(),
                baseUrl = normalizedBaseUrl
            )

            appDatabase.withTransaction {
                serverDao.insert(serverEntity)
                replaceLibrariesForServer(
                    serverId = serverId,
                    libraries = libraries,
                    serverFallback = serverEntity
                )
                matchingServers
                    .asSequence()
                    .filter { it.id != serverId }
                    .forEach { duplicate ->
                        libraryDao.deleteByServerId(duplicate.id)
                        serverDao.deleteById(duplicate.id)
                        sessionPreferences.setServerAvatarUri(duplicate.id, null)
                    }
            }

            matchingServers
                .asSequence()
                .filter { it.id != serverId }
                .forEach { duplicate ->
                    runCatching { secureTokenStorage.clearToken(duplicate.id) }
                }

            secureTokenStorage.saveToken(serverId, token)
            sessionPreferences.setActiveSelection(serverId = serverId, libraryId = null)
            sessionPreferences.setLastPlayedBookId(null)
            sessionPreferences.clearCachedHomeFeed()
            sessionPreferences.setRequiresLibrarySelection(true)
            clearContentCaches()

            AppResult.Success(Unit)
        } catch (t: Throwable) {
            if (matchingExistingServer == null) {
                rollbackFailedServerSetup(serverId)
            }
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

        val items = itemsResult.getOrThrow()
        val mediaProgressByItemId = if (items.isEmpty()) {
            emptyMap()
        } else {
            audiobookshelfApi.getMediaProgress(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token
            ).getOrNull()
                ?.associateBy { it.libraryItemId }
                .orEmpty()
        }
        val books = items.map { item ->
            item.toBookSummary(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token
            ).withResolvedProgress(mediaProgressByItemId[item.id])
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

        val collections = coroutineScope {
            result.getOrThrow()
                .asSequence()
                .filterNot { it.name.startsWith("StillShelf Probe", ignoreCase = true) }
                .map { collection ->
                    async {
                        val resolvedCount = audiobookshelfApi.getCollectionBookIds(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token,
                            collectionId = collection.id
                        ).getOrNull()?.size ?: collection.itemCount ?: 0
                        NamedEntitySummary(
                            id = collection.id,
                            name = collection.name,
                            subtitle = "$resolvedCount books"
                        )
                    }
                }
                .toList()
                .awaitAll()
        }
        putCache(collectionsCache, cacheKey, collections)
        return AppResult.Success(collections)
    }

    override suspend fun fetchPlaylistsForActiveLibrary(
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
            suffix = "playlists"
        )
        if (!forceRefresh) {
            getFreshCache(playlistsCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(playlistsCache, cacheKey)

        val result = audiobookshelfApi.getPlaylists(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id
        )
        if (result.isFailure) {
            if (!staleCache.isNullOrEmpty()) {
                return AppResult.Success(staleCache)
            }
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to load playlists.",
                cause = result.exceptionOrNull()
            )
        }

        val playlists = coroutineScope {
            result.getOrThrow().map { playlist ->
                async {
                    val resolvedCount = audiobookshelfApi.getPlaylistBookIds(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        playlistId = playlist.id
                    ).getOrNull()?.size ?: playlist.itemCount
                    NamedEntitySummary(
                        id = playlist.id,
                        name = playlist.name,
                        subtitle = resolvedCount?.let { count -> "$count books" }
                    )
                }
            }.awaitAll()
        }
        putCache(playlistsCache, cacheKey, playlists)
        return AppResult.Success(playlists)
    }

    override suspend fun fetchCollectionBooks(
        collectionId: String,
        forceRefresh: Boolean
    ): AppResult<List<BookSummary>> {
        if (collectionId.isBlank()) return AppResult.Error("Invalid collection id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val bookIds = audiobookshelfApi.getCollectionBookIds(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = collectionId
        ).getOrElse { throwable ->
            return AppResult.Error(
                message = throwable.message ?: "Unable to load collection items.",
                cause = throwable
            )
        }
        if (bookIds.isEmpty()) {
            return AppResult.Success(emptyList())
        }

        return when (val booksResult = fetchAllBooksForActiveLibrary(forceRefresh = forceRefresh)) {
            is AppResult.Success -> {
                val normalizedIds = bookIds
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val books = booksResult.value
                    .filter { book ->
                        val id = book.id.trim()
                        id in normalizedIds ||
                            normalizedIds.any { candidate ->
                                candidate.equals(id, ignoreCase = true) ||
                                    candidate.endsWith(id, ignoreCase = true) ||
                                    id.endsWith(candidate, ignoreCase = true)
                            }
                    }
                    .sortedWith(compareBy { it.title.lowercase() })
                AppResult.Success(books)
            }
            is AppResult.Error -> booksResult
        }
    }

    override suspend fun fetchPlaylistBooks(
        playlistId: String,
        forceRefresh: Boolean
    ): AppResult<List<BookSummary>> {
        if (playlistId.isBlank()) return AppResult.Error("Invalid playlist id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val bookIds = audiobookshelfApi.getPlaylistBookIds(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = playlistId
        ).getOrElse { throwable ->
            return AppResult.Error(
                message = throwable.message ?: "Unable to load playlist items.",
                cause = throwable
            )
        }
        if (bookIds.isEmpty()) {
            return AppResult.Success(emptyList())
        }

        return when (val booksResult = fetchAllBooksForActiveLibrary(forceRefresh = forceRefresh)) {
            is AppResult.Success -> {
                val normalizedIds = bookIds
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val books = booksResult.value
                    .filter { book ->
                        val id = book.id.trim()
                        id in normalizedIds ||
                            normalizedIds.any { candidate ->
                                candidate.equals(id, ignoreCase = true) ||
                                    candidate.endsWith(id, ignoreCase = true) ||
                                    id.endsWith(candidate, ignoreCase = true)
                            }
                    }
                    .sortedWith(compareBy { it.title.lowercase() })
                AppResult.Success(books)
            }
            is AppResult.Error -> booksResult
        }
    }

    private suspend fun fetchAllBooksForActiveLibrary(forceRefresh: Boolean): AppResult<List<BookSummary>> {
        val booksById = LinkedHashMap<String, BookSummary>()
        var page = 0
        while (page < MAX_FULL_LIBRARY_PAGES) {
            when (
                val booksResult = fetchBooksForActiveLibrary(
                    limit = FULL_LIBRARY_PAGE_SIZE,
                    page = page,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Error -> return booksResult
                is AppResult.Success -> {
                    val pageBooks = booksResult.value
                    if (pageBooks.isEmpty()) {
                        break
                    }
                    val beforeCount = booksById.size
                    pageBooks.forEach { book ->
                        booksById.putIfAbsent(book.id.trim(), book)
                    }
                    if (pageBooks.size < FULL_LIBRARY_PAGE_SIZE || booksById.size == beforeCount) {
                        break
                    }
                    page += 1
                }
            }
        }
        return AppResult.Success(booksById.values.toList())
    }

    override suspend fun createCollection(name: String): AppResult<NamedEntitySummary> {
        val normalized = name.trim()
        if (normalized.isBlank()) return AppResult.Error("Collection name is required.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val result = audiobookshelfApi.createCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            name = normalized
        )
        val created = if (result.isSuccess) {
            result.getOrThrow()
        } else {
            val createError = result.exceptionOrNull()
            val errorTrace = buildString {
                var cursor: Throwable? = createError
                while (cursor != null) {
                    val text = cursor.message.orEmpty()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append(text)
                    }
                    cursor = cursor.cause
                }
            }
            val needsSeedBook = errorTrace.contains("no books", ignoreCase = true) ||
                errorTrace.contains("invalid collection data", ignoreCase = true)
            if (!needsSeedBook) {
                return AppResult.Error(
                    message = createError?.message ?: "Unable to create collection.",
                    cause = createError
                )
            }

            val seedBookId = audiobookshelfApi.getLibraryItems(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                libraryId = library.id,
                limit = 1,
                page = 0,
                sortBy = "media.metadata.title",
                desc = false
            ).getOrNull()?.firstOrNull()?.id

            if (seedBookId.isNullOrBlank()) {
                return AppResult.Error(
                    message = createError?.message ?: "Unable to create collection.",
                    cause = createError
                )
            }

            val seededCreate = audiobookshelfApi.createCollectionWithBook(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                libraryId = library.id,
                name = normalized,
                bookId = seedBookId
            )
            if (seededCreate.isFailure) {
                return AppResult.Error(
                    message = seededCreate.exceptionOrNull()?.message
                        ?: createError?.message
                        ?: "Unable to create collection.",
                    cause = seededCreate.exceptionOrNull() ?: createError
                )
            }
            val seededCollection = seededCreate.getOrThrow()
            // Best effort: keep UX as empty collection if server allows removing the seed item.
            runCatching {
                audiobookshelfApi.removeBookFromCollection(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    collectionId = seededCollection.id,
                    bookId = seedBookId
                )
            }
            seededCollection
        }

        clearContentCaches()
        return AppResult.Success(
            NamedEntitySummary(
                id = created.id,
                name = created.name.ifBlank { normalized }
            )
        )
    }

    override suspend fun renameCollection(collectionId: String, name: String): AppResult<Unit> {
        val normalizedId = collectionId.trim()
        val normalizedName = name.trim()
        if (normalizedId.isBlank()) return AppResult.Error("Invalid collection id.")
        if (normalizedName.isBlank()) return AppResult.Error("Collection name is required.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val result = audiobookshelfApi.renameCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = normalizedId,
            name = normalizedName
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to rename collection.",
                cause = result.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun renamePlaylist(playlistId: String, name: String): AppResult<Unit> {
        val normalizedId = playlistId.trim()
        val normalizedName = name.trim()
        if (normalizedId.isBlank()) return AppResult.Error("Invalid playlist id.")
        if (normalizedName.isBlank()) return AppResult.Error("Playlist name is required.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val result = audiobookshelfApi.renamePlaylist(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = normalizedId,
            name = normalizedName
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to rename playlist.",
                cause = result.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun createCollectionWithBook(
        name: String,
        bookId: String
    ): AppResult<NamedEntitySummary> {
        val normalizedName = name.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedName.isBlank()) return AppResult.Error("Collection name is required.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val createResult = audiobookshelfApi.createCollectionWithBook(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            name = normalizedName,
            bookId = normalizedBookId
        )
        if (createResult.isFailure) {
            return AppResult.Error(
                message = createResult.exceptionOrNull()?.message ?: "Unable to create collection.",
                cause = createResult.exceptionOrNull()
            )
        }
        clearContentCaches()
        val created = createResult.getOrThrow()
        return AppResult.Success(
            NamedEntitySummary(
                id = created.id,
                name = created.name.ifBlank { normalizedName }
            )
        )
    }

    override suspend fun createPlaylist(name: String): AppResult<NamedEntitySummary> {
        val normalized = name.trim()
        if (normalized.isBlank()) return AppResult.Error("Playlist name is required.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val result = audiobookshelfApi.createPlaylist(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            name = normalized
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to create playlist.",
                cause = result.exceptionOrNull()
            )
        }
        clearContentCaches()
        val created = result.getOrThrow()
        return AppResult.Success(
            NamedEntitySummary(
                id = created.id,
                name = created.name.ifBlank { normalized }
            )
        )
    }

    override suspend fun createPlaylistWithBook(
        name: String,
        bookId: String
    ): AppResult<NamedEntitySummary> {
        val normalizedName = name.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedName.isBlank()) return AppResult.Error("Playlist name is required.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val createResult = audiobookshelfApi.createPlaylistWithBook(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = library.id,
            name = normalizedName,
            bookId = normalizedBookId
        )
        if (createResult.isFailure) {
            return AppResult.Error(
                message = createResult.exceptionOrNull()?.message ?: "Unable to create playlist.",
                cause = createResult.exceptionOrNull()
            )
        }
        clearContentCaches()
        val created = createResult.getOrThrow()
        return AppResult.Success(
            NamedEntitySummary(
                id = created.id,
                name = created.name.ifBlank { normalizedName }
            )
        )
    }

    override suspend fun deleteCollection(collectionId: String): AppResult<Unit> {
        val normalizedId = collectionId.trim()
        if (normalizedId.isBlank()) return AppResult.Error("Invalid collection id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val result = audiobookshelfApi.deleteCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = normalizedId,
            libraryId = connection.library?.id
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to delete collection.",
                cause = result.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun deletePlaylist(playlistId: String): AppResult<Unit> {
        val normalizedId = playlistId.trim()
        if (normalizedId.isBlank()) return AppResult.Error("Invalid playlist id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val result = audiobookshelfApi.deletePlaylist(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = normalizedId,
            libraryId = connection.library?.id
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message ?: "Unable to delete playlist.",
                cause = result.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun addBookToCollection(collectionId: String, bookId: String): AppResult<Unit> {
        val normalizedCollectionId = collectionId.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedCollectionId.isBlank()) return AppResult.Error("Invalid collection id.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val addResult = audiobookshelfApi.addBookToCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = normalizedCollectionId,
            bookId = normalizedBookId
        )
        if (addResult.isFailure) {
            return AppResult.Error(
                message = addResult.exceptionOrNull()?.message ?: "Unable to add book to collection.",
                cause = addResult.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun addBookToPlaylist(playlistId: String, bookId: String): AppResult<Unit> {
        val normalizedPlaylistId = playlistId.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedPlaylistId.isBlank()) return AppResult.Error("Invalid playlist id.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val addResult = audiobookshelfApi.addBookToPlaylist(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = normalizedPlaylistId,
            bookId = normalizedBookId
        )
        if (addResult.isFailure) {
            return AppResult.Error(
                message = addResult.exceptionOrNull()?.message ?: "Unable to add book to playlist.",
                cause = addResult.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun removeBookFromCollection(collectionId: String, bookId: String): AppResult<Unit> {
        val normalizedCollectionId = collectionId.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedCollectionId.isBlank()) return AppResult.Error("Invalid collection id.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val removeResult = audiobookshelfApi.removeBookFromCollection(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            collectionId = normalizedCollectionId,
            bookId = normalizedBookId
        )
        if (removeResult.isFailure) {
            return AppResult.Error(
                message = removeResult.exceptionOrNull()?.message ?: "Unable to remove book from collection.",
                cause = removeResult.exceptionOrNull()
            )
        }
        clearContentCaches()
        return AppResult.Success(Unit)
    }

    override suspend fun removeBookFromPlaylist(playlistId: String, bookId: String): AppResult<Unit> {
        val normalizedPlaylistId = playlistId.trim()
        val normalizedBookId = bookId.trim()
        if (normalizedPlaylistId.isBlank()) return AppResult.Error("Invalid playlist id.")
        if (normalizedBookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val activeLibrary = connection.library ?: return AppResult.Error("No active library selected.")
        val existingPlaylistName = audiobookshelfApi.getPlaylists(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            libraryId = activeLibrary.id
        ).getOrNull()?.firstOrNull { playlist ->
            playlist.id.trim().equals(normalizedPlaylistId, ignoreCase = true)
        }?.name
        val removeResult = audiobookshelfApi.removeBookFromPlaylist(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = normalizedPlaylistId,
            bookId = normalizedBookId
        )
        if (removeResult.isFailure) {
            return AppResult.Error(
                message = removeResult.exceptionOrNull()?.message ?: "Unable to remove book from playlist.",
                cause = removeResult.exceptionOrNull()
            )
        }
        val verifyResult = audiobookshelfApi.getPlaylistBookIds(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            playlistId = normalizedPlaylistId
        )
        if (verifyResult.isSuccess) {
            val remainingIds = verifyResult.getOrThrow()
            if (containsLikelyMatchingId(remainingIds, normalizedBookId)) {
                return AppResult.Error("Unable to remove book from playlist.")
            }
        } else {
            val verifyError = verifyResult.exceptionOrNull()
            val verifyMessage = verifyError?.message.orEmpty()
            val playlistWasRemoved = verifyMessage.contains("404")
            if (playlistWasRemoved && !existingPlaylistName.isNullOrBlank()) {
                val recreateResult = audiobookshelfApi.createPlaylist(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    libraryId = activeLibrary.id,
                    name = existingPlaylistName
                )
                if (recreateResult.isFailure) {
                    return AppResult.Error(
                        message = recreateResult.exceptionOrNull()?.message
                            ?: "Removed book but failed to recreate empty playlist.",
                        cause = recreateResult.exceptionOrNull()
                    )
                }
            } else if (!playlistWasRemoved) {
                return AppResult.Error(
                    message = verifyError?.message ?: "Unable to verify playlist update.",
                    cause = verifyError
                )
            }
        }
        clearContentCaches()
        return AppResult.Success(Unit)
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
                val mediaProgressDeferred = async {
                    audiobookshelfApi.getMediaProgressForItem(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        itemId = bookId
                    ).getOrNull()
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
                    .filter { bookmark ->
                        bookmark.libraryItemId.matchesLibraryItemId(bookId)
                    }
                    .sortedBy { it.timeSeconds ?: Double.MAX_VALUE }
                    .map { it.toModel() }
                    .toList()

                val detail = detailResult.getOrThrow().toModel(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    bookmarks = bookmarks
                ).let { parsed ->
                    parsed.copy(
                        book = parsed.book.withResolvedProgress(mediaProgressDeferred.await())
                    )
                }
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
        if (isFinished) {
            runCatching { sessionPreferences.clearCachedHomeFeed() }
            clearContentCaches()
        }
        return AppResult.Success(Unit)
    }

    override suspend fun createBookmark(
        bookId: String,
        timeSeconds: Double,
        title: String?
    ): AppResult<Unit> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")

        val createResult = audiobookshelfApi.createBookmark(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId,
            timeSeconds = timeSeconds.coerceAtLeast(0.0),
            title = title
        )
        if (createResult.isFailure) {
            return AppResult.Error(
                message = createResult.exceptionOrNull()?.message ?: "Unable to add bookmark.",
                cause = createResult.exceptionOrNull()
            )
        }

        clearBookDetailCache(
            serverId = connection.server.id,
            libraryId = library.id,
            bookId = bookId
        )
        return AppResult.Success(Unit)
    }

    override suspend fun fetchBookmarksForActiveLibrary(
        forceRefresh: Boolean
    ): AppResult<List<BookmarkEntry>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val books = when (
            val booksResult = fetchBooksForActiveLibrary(
                limit = 400,
                page = 0,
                forceRefresh = forceRefresh
            )
        ) {
            is AppResult.Success -> booksResult.value
            is AppResult.Error -> {
                return AppResult.Error(
                    message = booksResult.message,
                    cause = booksResult.cause
                )
            }
        }

        val bookmarksResult = audiobookshelfApi.getBookmarks(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token
        )
        if (bookmarksResult.isFailure) {
            return AppResult.Error(
                message = bookmarksResult.exceptionOrNull()?.message ?: "Unable to load bookmarks.",
                cause = bookmarksResult.exceptionOrNull()
            )
        }

        val bookmarkEntries = bookmarksResult.getOrDefault(emptyList())
            .mapNotNull { rawBookmark ->
                val book = books.firstOrNull { book ->
                    rawBookmark.libraryItemId.matchesLibraryItemId(book.id)
                } ?: return@mapNotNull null
                BookmarkEntry(
                    book = book,
                    bookmark = rawBookmark.toModel()
                )
            }
            .sortedWith(
                compareByDescending<BookmarkEntry> { it.bookmark.createdAtMs ?: Long.MIN_VALUE }
                    .thenByDescending { it.bookmark.timeSeconds ?: Double.MIN_VALUE }
                    .thenBy { it.book.title.lowercase() }
            )

        return AppResult.Success(bookmarkEntries)
    }

    override suspend fun updateBookmark(
        bookId: String,
        bookmark: BookBookmark,
        newTitle: String
    ): AppResult<Unit> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val normalizedTitle = newTitle.trim()
        if (normalizedTitle.isBlank()) return AppResult.Error("Bookmark title can't be empty.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")

        val updateResult = audiobookshelfApi.updateBookmark(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId,
            bookmarkId = bookmark.id,
            timeSeconds = bookmark.timeSeconds,
            existingTitle = bookmark.title,
            newTitle = normalizedTitle
        )
        if (updateResult.isFailure) {
            return AppResult.Error(
                message = updateResult.exceptionOrNull()?.message ?: "Unable to edit bookmark.",
                cause = updateResult.exceptionOrNull()
            )
        }

        clearBookDetailCache(
            serverId = connection.server.id,
            libraryId = library.id,
            bookId = bookId
        )
        return AppResult.Success(Unit)
    }

    override suspend fun deleteBookmark(
        bookId: String,
        bookmark: BookBookmark
    ): AppResult<Unit> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")

        val deleteResult = audiobookshelfApi.deleteBookmark(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId,
            bookmarkId = bookmark.id,
            timeSeconds = bookmark.timeSeconds,
            title = bookmark.title
        )
        if (deleteResult.isFailure) {
            return AppResult.Error(
                message = deleteResult.exceptionOrNull()?.message ?: "Unable to delete bookmark.",
                cause = deleteResult.exceptionOrNull()
            )
        }

        clearBookDetailCache(
            serverId = connection.server.id,
            libraryId = library.id,
            bookId = bookId
        )
        return AppResult.Success(Unit)
    }

    override suspend fun markBookFinished(
        bookId: String,
        finished: Boolean,
        resetProgressWhenUnfinished: Boolean,
        preservedProgress: PlaybackProgress?
    ): AppResult<PlaybackProgress> {
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val existingProgress = if (finished || resetProgressWhenUnfinished) {
            null
        } else {
            audiobookshelfApi.getMediaProgressForItem(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                itemId = bookId
            ).getOrNull()
        }
        val detailDuration = if (finished || !resetProgressWhenUnfinished) {
            audiobookshelfApi.getItemDetail(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                itemId = bookId
            ).getOrNull()?.durationSeconds?.coerceAtLeast(0.0)
        } else {
            null
        }
        if (finished) {
            val serverProgressBeforeFinish = if (preservedProgress == null) {
                audiobookshelfApi.getMediaProgressForItem(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    itemId = bookId
                ).getOrNull()
            } else {
                null
            }
            val progressBeforeFinishCurrentTime = preservedProgress?.currentTimeSeconds
                ?: serverProgressBeforeFinish?.currentTimeSeconds
            val progressBeforeFinishDuration = preservedProgress?.durationSeconds
                ?: serverProgressBeforeFinish?.durationSeconds
                ?: detailDuration
            val progressBeforeFinishPercent = preservedProgress?.progressPercent
                ?: serverProgressBeforeFinish?.progressPercent
            val unfinishedState = resolveUnfinishedProgressState(
                currentTimeSeconds = progressBeforeFinishCurrentTime,
                durationSeconds = progressBeforeFinishDuration,
                progressPercent = progressBeforeFinishPercent
            )
            finishedProgressSnapshots[bookId] = FinishedProgressSnapshot(
                currentTimeSeconds = unfinishedState.currentTimeSeconds,
                durationSeconds = unfinishedState.durationSeconds,
                progressPercent = unfinishedState.progressPercent
            )
        }
        val storedFinishedSnapshot = if (!finished && !resetProgressWhenUnfinished) {
            finishedProgressSnapshots[bookId]
        } else {
            null
        }
        val targetDuration = if (finished) {
            val progressDuration = audiobookshelfApi.getMediaProgressForItem(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                itemId = bookId
            ).getOrNull()?.durationSeconds
            (detailDuration ?: progressDuration)?.coerceAtLeast(0.0)
        } else if (resetProgressWhenUnfinished) {
            null
        } else if (storedFinishedSnapshot != null) {
            storedFinishedSnapshot.durationSeconds
        } else {
            (existingProgress?.durationSeconds ?: detailDuration)?.coerceAtLeast(0.0)
        }
        val restoredUnfinishedState = when {
            finished || resetProgressWhenUnfinished -> null
            storedFinishedSnapshot != null -> UnfinishedProgressState(
                currentTimeSeconds = storedFinishedSnapshot.currentTimeSeconds,
                durationSeconds = storedFinishedSnapshot.durationSeconds,
                progressPercent = storedFinishedSnapshot.progressPercent
            )
            else -> resolveUnfinishedProgressState(
                currentTimeSeconds = existingProgress?.currentTimeSeconds,
                durationSeconds = targetDuration,
                progressPercent = existingProgress?.progressPercent
            )
        }
        val targetCurrentTime = when {
            finished && targetDuration != null -> targetDuration
            finished -> 0.0
            resetProgressWhenUnfinished -> 0.0
            else -> restoredUnfinishedState?.currentTimeSeconds ?: 0.0
        }
        val result = audiobookshelfApi.updateMediaProgressForItem(
            baseUrl = connection.server.baseUrl,
            authToken = connection.token,
            itemId = bookId,
            currentTimeSeconds = targetCurrentTime,
            durationSeconds = targetDuration,
            isFinished = finished
        )
        if (result.isFailure) {
            return AppResult.Error(
                message = result.exceptionOrNull()?.message
                    ?: if (finished) "Unable to mark as finished." else "Unable to mark as unfinished.",
                cause = result.exceptionOrNull()
            )
        }
        if (!finished || resetProgressWhenUnfinished) {
            finishedProgressSnapshots.remove(bookId)
        }
        runCatching { sessionPreferences.clearCachedHomeFeed() }
        clearContentCaches()
        val resolvedProgress = when {
            finished -> PlaybackProgress(
                progressPercent = 1.0,
                currentTimeSeconds = targetCurrentTime,
                durationSeconds = targetDuration
            )

            resetProgressWhenUnfinished -> PlaybackProgress(
                progressPercent = 0.0,
                currentTimeSeconds = 0.0,
                durationSeconds = targetDuration
            )

            else -> PlaybackProgress(
                progressPercent = restoredUnfinishedState?.progressPercent ?: 0.0,
                currentTimeSeconds = restoredUnfinishedState?.currentTimeSeconds ?: 0.0,
                durationSeconds = restoredUnfinishedState?.durationSeconds ?: targetDuration
            )
        }
        return AppResult.Success(resolvedProgress)
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
                val createdCollectionId = createdWithBook.getOrThrow().id
                val verifiedIds = audiobookshelfApi.getCollectionBookIds(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    collectionId = createdCollectionId
                ).getOrNull()
                if (verifiedIds == null || verifiedIds.contains(bookId)) {
                    clearContentCaches()
                    return AppResult.Success("Collections")
                }
                createdCollectionId
            } else {
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
        clearContentCaches()
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
                        ).withResolvedProgress(mediaProgressByItemId[item.id])
                    }
                val allBooks = if (allBooksResult.isSuccess) {
                    allBooksResult.getOrThrow().map { item ->
                        item.toBookSummary(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token
                        ).withResolvedProgress(mediaProgressByItemId[item.id])
                    }
                } else {
                    emptyList()
                }
                val listenAgain = allBooks
                    .asSequence()
                    .filter { it.isFinished }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareByDescending<BookSummary> { it.addedAtMs ?: 0L }
                            .thenBy { it.title.lowercase() }
                    )
                    .take(40)
                    .toList()
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
                    listenAgain = listenAgain,
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
        runCatching { sessionPreferences.setServerAvatarUri(serverId, null) }
        clearContentCaches()
    }

    private suspend fun getActiveConnection(requireLibrary: Boolean): AppResult<ActiveConnection> {
        val session = sessionPreferences.state.first()
        val activeServerId = session.activeServerId
            ?: return AppResult.Error("No active server selected.")
        val server = serverDao.getById(activeServerId)
            ?: run {
                sessionPreferences.setActiveSelection(serverId = null, libraryId = null)
                sessionPreferences.setRequiresLibrarySelection(false)
                return AppResult.Error("Active server not found.")
            }
        val token = secureTokenStorage.getToken(server.id)
            ?: run {
                sessionPreferences.setActiveSelection(serverId = null, libraryId = null)
                sessionPreferences.setRequiresLibrarySelection(false)
                return AppResult.Error("No saved session for this server. Please log in again.")
            }

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
        val library = libraryDao.getByServerAndId(server.id, activeLibraryId)
            ?: run {
                sessionPreferences.setActiveLibraryId(null)
                sessionPreferences.setRequiresLibrarySelection(true)
                return AppResult.Error("Active library not found.")
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

    private fun containsLikelyMatchingId(ids: Set<String>, targetId: String): Boolean {
        val normalizedTarget = targetId.trim()
        if (normalizedTarget.isBlank()) return false
        return ids.any { candidate ->
            val normalizedCandidate = candidate.trim()
            normalizedCandidate.equals(normalizedTarget, ignoreCase = true) ||
                normalizedCandidate.endsWith(normalizedTarget, ignoreCase = true) ||
                normalizedTarget.endsWith(normalizedCandidate, ignoreCase = true)
        }
    }

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
        playlistsCache.clear()
        bookDetailCache.clear()
    }

    private suspend fun clearBookDetailCache(
        serverId: String,
        libraryId: String,
        bookId: String
    ) {
        val key = contentCacheKey(serverId, libraryId, suffix = "bookDetail:$bookId")
        cacheMutex.withLock {
            bookDetailCache.remove(key)
        }
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
            "listenAgain",
            JSONArray().apply {
                feed.listenAgain.forEach { book ->
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
        val listenAgainItems = buildList {
            val source = root.optJSONArray("listenAgain") ?: JSONArray()
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
            listenAgain = listenAgainItems,
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
            .put("seriesNames", JSONArray(book.seriesNames))
            .put("seriesIds", JSONArray(book.seriesIds))
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
            seriesNames = source.optStringList("seriesNames"),
            seriesIds = source.optStringList("seriesIds"),
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

    private fun JSONObject.optStringList(key: String): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
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
            seriesNames = seriesNames,
            seriesIds = seriesIds,
            seriesSequence = seriesSequence,
            genres = genres,
            publishedYear = publishedYear,
            addedAtMs = addedAtMs,
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            isFinished = isFinished
        )
    }

    private fun BookSummary.withResolvedProgress(
        progress: AudiobookshelfMediaProgressDto?
    ): BookSummary {
        progress ?: return this
        val mergedCurrentTimeSeconds = progress.currentTimeSeconds?.coerceAtLeast(0.0) ?: currentTimeSeconds
        val progressFromApi = progress.progressPercent?.coerceIn(0.0, 1.0)
        val mergedDurationSeconds = progress.durationSeconds?.takeIf { it > 0.0 } ?: durationSeconds
        val derivedProgress = if (progressFromApi == null && mergedCurrentTimeSeconds != null && mergedDurationSeconds != null && mergedDurationSeconds > 0.0) {
            (mergedCurrentTimeSeconds / mergedDurationSeconds).coerceIn(0.0, 1.0)
        } else {
            null
        }
        val mergedProgressPercent = progressFromApi ?: derivedProgress ?: progressPercent
        val finishedFromProgress = (mergedProgressPercent ?: 0.0) >= 0.995
        return copy(
            progressPercent = mergedProgressPercent,
            currentTimeSeconds = mergedCurrentTimeSeconds,
            isFinished = isFinished || finishedFromProgress
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
                imagePath?.let { audiobookshelfApi.buildImagePathUrl(baseUrl, it, authToken) }
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
            seriesNames = seriesNames,
            seriesIds = seriesIds,
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
            seriesNames = seriesNames,
            seriesIds = seriesIds,
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
            timeSeconds = timeSeconds,
            createdAtMs = createdAtMs
        )
    }

    private fun String.matchesLibraryItemId(targetItemId: String): Boolean {
        val normalizedSource = trim()
        val normalizedTarget = targetItemId.trim()
        if (normalizedSource.isBlank() || normalizedTarget.isBlank()) return false
        return normalizedSource.equals(normalizedTarget, ignoreCase = true) ||
            normalizedSource.endsWith(normalizedTarget, ignoreCase = true) ||
            normalizedTarget.endsWith(normalizedSource, ignoreCase = true)
    }
}
