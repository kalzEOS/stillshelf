package com.stillshelf.app.data.repo

import com.stillshelf.app.core.database.LibraryDao
import com.stillshelf.app.core.database.LibraryEntity
import com.stillshelf.app.core.database.AppDatabase
import com.stillshelf.app.core.database.BookBookmarkEntity
import com.stillshelf.app.core.database.BookChapterEntity
import com.stillshelf.app.core.database.BookDetailEntity
import com.stillshelf.app.core.database.BookSummaryEntity
import com.stillshelf.app.core.database.DetailCacheDao
import com.stillshelf.app.core.database.DetailSyncStateEntity
import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.database.ServerEntity
import com.stillshelf.app.core.database.SeriesMembershipEntity
import com.stillshelf.app.core.database.SeriesSummaryEntity
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookProgressMutation
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.HomeFeed
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.model.PlaybackTrack
import com.stillshelf.app.core.model.SearchResults
import com.stillshelf.app.core.model.SeriesDetailEntry
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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

internal class DetailRefreshDeduper {
    private val refreshJobMutex = Mutex()
    private val inFlightDetailRefreshes = mutableMapOf<String, Deferred<AppResult<Unit>>>()

    suspend fun runDeduped(
        key: String,
        block: suspend () -> AppResult<Unit>
    ): AppResult<Unit> = coroutineScope {
        val deferred = refreshJobMutex.withLock {
            inFlightDetailRefreshes[key] ?: async {
                try {
                    block()
                } finally {
                    refreshJobMutex.withLock {
                        if (inFlightDetailRefreshes[key] === this@async) {
                            inFlightDetailRefreshes.remove(key)
                        }
                    }
                }
            }.also { created ->
                inFlightDetailRefreshes[key] = created
            }
        }
        deferred.await()
    }
}

internal fun selectLocalProgressOverride(
    mutation: BookProgressMutation?,
    fetchedProgressPercent: Double?,
    fetchedCurrentTimeSeconds: Double?,
    fetchedDurationSeconds: Double?,
    fetchedIsFinished: Boolean,
    progressEpsilon: Double,
    timeEpsilonSeconds: Double
): BookProgressMutation? {
    mutation ?: return null
    return if (
        progressMatchesMutationInternal(
            mutation = mutation,
            fetchedProgressPercent = fetchedProgressPercent,
            fetchedCurrentTimeSeconds = fetchedCurrentTimeSeconds,
            fetchedDurationSeconds = fetchedDurationSeconds,
            fetchedIsFinished = fetchedIsFinished,
            progressEpsilon = progressEpsilon,
            timeEpsilonSeconds = timeEpsilonSeconds
        )
    ) {
        null
    } else {
        mutation
    }
}

private fun progressMatchesMutationInternal(
    mutation: BookProgressMutation,
    fetchedProgressPercent: Double?,
    fetchedCurrentTimeSeconds: Double?,
    fetchedDurationSeconds: Double?,
    fetchedIsFinished: Boolean,
    progressEpsilon: Double,
    timeEpsilonSeconds: Double
): Boolean {
    return mutation.isFinished == fetchedIsFinished &&
        approxEqualsInternal(mutation.progressPercent, fetchedProgressPercent, progressEpsilon) &&
        approxEqualsInternal(mutation.currentTimeSeconds, fetchedCurrentTimeSeconds, timeEpsilonSeconds) &&
        approxEqualsInternal(mutation.durationSeconds, fetchedDurationSeconds, timeEpsilonSeconds)
}

private fun approxEqualsInternal(left: Double?, right: Double?, epsilon: Double): Boolean {
    if (left == null && right == null) return true
    if (left == null || right == null) return false
    return kotlin.math.abs(left - right) <= epsilon
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val serverDao: ServerDao,
    private val libraryDao: LibraryDao,
    private val detailCacheDao: DetailCacheDao,
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
        private const val LOCAL_PROGRESS_OVERRIDE_MAX_AGE_MS: Long = 60 * 1000L
        private const val PROGRESS_MATCH_EPSILON: Double = 0.005
        private const val TIME_MATCH_EPSILON_SECONDS: Double = 1.0
        private const val DETAIL_RESOURCE_BOOK = "book"
        private const val DETAIL_RESOURCE_SERIES = "series"
        private const val DETAIL_RESOURCE_VARIANT_DEFAULT = ""
        private const val SERIES_ENTRY_TYPE_BOOK = "book"
        private const val SERIES_ENTRY_TYPE_SUBSERIES = "subseries"
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

    private data class LocalBookProgressOverride(
        val mutation: BookProgressMutation,
        val savedAtMs: Long = System.currentTimeMillis()
    )

    private val cacheMutex = Mutex()
    private val finishedProgressSnapshots = mutableMapOf<String, FinishedProgressSnapshot>()
    private val mutableBookProgressMutations = MutableSharedFlow<BookProgressMutation>(extraBufferCapacity = 32)
    private val localBookProgressOverrides = mutableMapOf<String, LocalBookProgressOverride>()
    private val booksCache = mutableMapOf<String, TimedCacheEntry<List<BookSummary>>>()
    private val authorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val narratorsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val seriesCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val collectionsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val playlistsCache = mutableMapOf<String, TimedCacheEntry<List<NamedEntitySummary>>>()
    private val refreshDeduper = DetailRefreshDeduper()

    override fun observeSessionState(): Flow<SessionState> = sessionPreferences.state.map { prefState ->
        SessionState(
            activeServerId = prefState.activeServerId,
            activeLibraryId = prefState.activeLibraryId,
            requiresLibrarySelection = prefState.requiresLibrarySelection
        )
    }

    override fun observeBookProgressMutations(): Flow<BookProgressMutation> = mutableBookProgressMutations.asSharedFlow()

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
            deletePersistedDetailCacheForServer(existing.id)
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
                deletePersistedDetailCacheForServer(activeServerId)
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
        password: String,
        persistenceMode: LoginPersistenceMode
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
                        deletePersistedDetailCacheForServer(duplicate.id)
                        sessionPreferences.setServerAvatarUri(duplicate.id, null)
                    }
            }

            matchingServers
                .asSequence()
                .filter { it.id != serverId }
                .forEach { duplicate ->
                    runCatching { secureTokenStorage.clearToken(duplicate.id) }
                }

            when (persistenceMode) {
                LoginPersistenceMode.PersistentSecureOnly -> {
                    secureTokenStorage.saveToken(
                        serverId = serverId,
                        token = token
                    )
                }

                LoginPersistenceMode.PersistentAllowInsecureFallback -> {
                    secureTokenStorage.saveToken(
                        serverId = serverId,
                        token = token,
                        allowInsecureStorage = true
                    )
                }

                LoginPersistenceMode.SessionOnly -> {
                    secureTokenStorage.saveToken(
                        serverId = serverId,
                        token = token,
                        persistAcrossRestarts = false
                    )
                }
            }
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
                .withLocalProgressOverride(connection.server.id)
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
        persistSeriesSummaries(
            serverId = connection.server.id,
            libraryId = library.id,
            series = series
        )
        putCache(seriesCache, cacheKey, series)
        return AppResult.Success(series)
    }

    override suspend fun fetchSeriesContentsForActiveLibrary(
        seriesId: String,
        collapseSubseries: Boolean,
        forceRefresh: Boolean
    ): AppResult<List<SeriesDetailEntry>> {
        if (seriesId.isBlank()) return AppResult.Error("Series not found.")

        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val normalizedSeriesId = seriesId.trim()
        val persistedLocal = if (forceRefresh) {
            null
        } else {
            readPersistedRawSeriesContents(
                serverId = connection.server.id,
                libraryId = library.id,
                seriesId = normalizedSeriesId
            )
        }
        val syncState = detailCacheDao.getDetailSyncState(
            serverId = connection.server.id,
            libraryId = library.id,
            resourceType = DETAIL_RESOURCE_SERIES,
            resourceId = normalizedSeriesId,
            resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT
        )
        val shouldRefresh = shouldRefreshDetail(
            policy = if (forceRefresh) DetailRefreshPolicy.Force else DetailRefreshPolicy.IfStale,
            localExists = persistedLocal != null,
            lastSuccessfulSyncAtMs = syncState?.lastSuccessfulSyncAtMs,
            maxAgeMs = CONTENT_CACHE_MAX_AGE_MS
        )
        if (!shouldRefresh && persistedLocal != null) {
            return AppResult.Success(
                presentPersistedSeriesContents(
                    serverId = connection.server.id,
                    libraryId = library.id,
                    seriesId = normalizedSeriesId,
                    collapseSubseries = collapseSubseries,
                    rawEntries = persistedLocal
                )
            )
        }
        val staleCache = if (forceRefresh) null else persistedLocal
        val refreshKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "seriesContents:$normalizedSeriesId"
        )
        return when (
            val refreshResult = runDedupedDetailRefresh("seriesContents:$refreshKey") {
                refreshPersistedSeriesContents(
                    connection = connection,
                    libraryId = library.id,
                    seriesId = normalizedSeriesId
                )
            }
        ) {
            is AppResult.Success -> {
                val cached = readPersistedRawSeriesContents(
                    serverId = connection.server.id,
                    libraryId = library.id,
                    seriesId = normalizedSeriesId
                )
                if (cached != null) {
                    AppResult.Success(
                        presentPersistedSeriesContents(
                            serverId = connection.server.id,
                            libraryId = library.id,
                            seriesId = normalizedSeriesId,
                            collapseSubseries = collapseSubseries,
                            rawEntries = cached
                        )
                    )
                } else if (!staleCache.isNullOrEmpty()) {
                    AppResult.Success(
                        presentPersistedSeriesContents(
                            serverId = connection.server.id,
                            libraryId = library.id,
                            seriesId = normalizedSeriesId,
                            collapseSubseries = collapseSubseries,
                            rawEntries = staleCache
                        )
                    )
                } else {
                    AppResult.Error("Unable to load series.")
                }
            }

            is AppResult.Error -> {
                if (!staleCache.isNullOrEmpty()) {
                    AppResult.Success(
                        presentPersistedSeriesContents(
                            serverId = connection.server.id,
                            libraryId = library.id,
                            seriesId = normalizedSeriesId,
                            collapseSubseries = collapseSubseries,
                            rawEntries = staleCache
                        )
                    )
                } else {
                    refreshResult
                }
            }
        }
    }

    override fun observeSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean
    ): Flow<List<SeriesDetailEntry>> {
        val normalizedSeriesId = seriesId.trim()
        if (normalizedSeriesId.isBlank()) return flowOf(emptyList())
        return sessionPreferences.state
            .map { state ->
                val serverId = state.activeServerId?.trim().orEmpty()
                val libraryId = state.activeLibraryId?.trim().orEmpty()
                if (serverId.isBlank() || libraryId.isBlank()) {
                    null
                } else {
                    serverId to libraryId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids == null) {
                    flowOf(emptyList())
                } else {
                    val (serverId, libraryId) = ids
                    combine(
                        detailCacheDao.observeSeriesMemberships(
                            serverId = serverId,
                            libraryId = libraryId,
                            seriesId = normalizedSeriesId,
                            collapseSubseries = false
                        ),
                        detailCacheDao.observeSeriesSummary(serverId, libraryId, normalizedSeriesId),
                        detailCacheDao.observeSeriesMemberships(
                            serverId = serverId,
                            libraryId = libraryId,
                            seriesId = normalizedSeriesId,
                            collapseSubseries = true
                        )
                    ) { rawMemberships, summaryEntity, legacyCollapsedMemberships ->
                        val rawEntries = rawMemberships.toSeriesDetailEntries(
                            serverId = serverId,
                            libraryId = libraryId
                        )
                        if (rawEntries.isNotEmpty()) {
                            presentPersistedSeriesContents(
                                serverId = serverId,
                                libraryId = libraryId,
                                seriesId = normalizedSeriesId,
                                collapseSubseries = collapseSubseries,
                                rawEntries = rawEntries,
                                seriesNameOverride = summaryEntity?.name
                            )
                        } else if (collapseSubseries) {
                            legacyCollapsedMemberships.toSeriesDetailEntries(
                                serverId = serverId,
                                libraryId = libraryId
                            )
                        } else {
                            emptyList()
                        }
                    }
                }
            }
    }

    override fun observeSeriesSummary(seriesId: String): Flow<NamedEntitySummary?> {
        val normalizedSeriesId = seriesId.trim()
        if (normalizedSeriesId.isBlank()) return flowOf(null)
        return sessionPreferences.state
            .map { state ->
                val serverId = state.activeServerId?.trim().orEmpty()
                val libraryId = state.activeLibraryId?.trim().orEmpty()
                if (serverId.isBlank() || libraryId.isBlank()) {
                    null
                } else {
                    serverId to libraryId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids == null) {
                    flowOf(null)
                } else {
                    val (serverId, libraryId) = ids
                    detailCacheDao.observeSeriesSummary(serverId, libraryId, normalizedSeriesId)
                        .map { entity -> entity?.toModel() }
                }
            }
    }

    override suspend fun resolveCachedSeriesIdForActiveLibrary(seriesName: String): String? {
        val normalizedSeriesName = seriesName.trim()
        if (normalizedSeriesName.isBlank()) return null
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return null
        }
        val library = connection.library ?: return null
        return resolveCachedSeriesId(
            serverId = connection.server.id,
            libraryId = library.id,
            seriesName = normalizedSeriesName
        )
    }

    override suspend fun resolveSeriesIdForActiveLibrary(seriesName: String): String? {
        val normalizedSeriesName = seriesName.trim()
        if (normalizedSeriesName.isBlank()) return null
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return null
        }
        val library = connection.library ?: return null
        resolveCachedSeriesId(
            serverId = connection.server.id,
            libraryId = library.id,
            seriesName = normalizedSeriesName
        )?.let { return it }

        val normalizedSeriesKey = normalizeSeriesKey(normalizedSeriesName)
        val fetchedSeries = when (
            val result = fetchSeriesForActiveLibrary(
                limit = FULL_LIBRARY_PAGE_SIZE,
                page = 0,
                forceRefresh = false
            )
        ) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return null
        }
        return fetchedSeries.firstOrNull { series ->
            normalizeSeriesKey(series.name) == normalizedSeriesKey
        }?.id?.trim()?.takeIf { it.isNotBlank() }
    }

    override suspend fun peekSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean
    ): List<SeriesDetailEntry>? {
        val normalizedSeriesId = seriesId.trim()
        if (normalizedSeriesId.isBlank()) return null
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return null
        }
        val library = connection.library ?: return null
        val rawEntries = readPersistedRawSeriesContents(
            serverId = connection.server.id,
            libraryId = library.id,
            seriesId = normalizedSeriesId
        )
        if (rawEntries != null) {
            return presentPersistedSeriesContents(
                serverId = connection.server.id,
                libraryId = library.id,
                seriesId = normalizedSeriesId,
                collapseSubseries = collapseSubseries,
                rawEntries = rawEntries
            )
        }
        return if (collapseSubseries) {
            readPersistedLegacyCollapsedSeriesContents(
                serverId = connection.server.id,
                libraryId = library.id,
                seriesId = normalizedSeriesId
            )
        } else {
            null
        }
    }

    override suspend fun refreshSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean,
        policy: DetailRefreshPolicy
    ): AppResult<Unit> {
        val normalizedSeriesId = seriesId.trim()
        if (normalizedSeriesId.isBlank()) {
            return AppResult.Error("Series not found.")
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val persistedLocal = readPersistedRawSeriesContents(
            serverId = connection.server.id,
            libraryId = library.id,
            seriesId = normalizedSeriesId
        )
        val syncState = detailCacheDao.getDetailSyncState(
            serverId = connection.server.id,
            libraryId = library.id,
            resourceType = DETAIL_RESOURCE_SERIES,
            resourceId = normalizedSeriesId,
            resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT
        )
        val shouldRefresh = shouldRefreshDetail(
            policy = policy,
            localExists = persistedLocal != null,
            lastSuccessfulSyncAtMs = syncState?.lastSuccessfulSyncAtMs,
            maxAgeMs = CONTENT_CACHE_MAX_AGE_MS
        )
        if (!shouldRefresh) return AppResult.Success(Unit)
        val refreshKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "seriesContents:$normalizedSeriesId"
        )
        return runDedupedDetailRefresh(
            key = "seriesContents:$refreshKey"
        ) {
            refreshPersistedSeriesContents(
                connection = connection,
                libraryId = library.id,
                seriesId = normalizedSeriesId
            )
        }
    }

    override suspend fun cacheSeriesDetail(
        seriesId: String,
        collapseSubseries: Boolean,
        entries: List<SeriesDetailEntry>
    ): AppResult<Unit> {
        val normalizedSeriesId = seriesId.trim()
        if (normalizedSeriesId.isBlank()) {
            return AppResult.Error("Series not found.")
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val rawEntries = entries.filterIsInstance<SeriesDetailEntry.BookItem>()
        if (rawEntries.isEmpty()) return AppResult.Success(Unit)
        return try {
            persistSeriesContentsSnapshot(
                serverId = connection.server.id,
                libraryId = library.id,
                seriesId = normalizedSeriesId,
                entries = rawEntries
            )
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to cache series.", t)
        }
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
                        val resolvedCount = collection.itemCount ?: audiobookshelfApi.getCollectionBookIds(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token,
                            collectionId = collection.id
                        ).getOrNull()?.size ?: 0
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
                    val resolvedCount = playlist.itemCount ?: audiobookshelfApi.getPlaylistBookIds(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token,
                        playlistId = playlist.id
                    ).getOrNull()?.size
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

    override suspend fun fetchAllBooksForActiveLibrary(
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
            suffix = "books:all"
        )
        if (!forceRefresh) {
            getFreshCache(booksCache, cacheKey, CONTENT_CACHE_MAX_AGE_MS)?.let { cached ->
                return AppResult.Success(cached)
            }
        }
        val staleCache = if (forceRefresh) null else getAnyCache(booksCache, cacheKey)

        val items = mutableListOf<AudiobookshelfLibraryItemDto>()
        var page = 0
        while (page < MAX_FULL_LIBRARY_PAGES) {
            val itemsResult = audiobookshelfApi.getLibraryItems(
                baseUrl = connection.server.baseUrl,
                authToken = connection.token,
                libraryId = library.id,
                limit = FULL_LIBRARY_PAGE_SIZE,
                page = page,
                sortBy = "media.metadata.title",
                desc = false
            )
            if (itemsResult.isFailure) {
                if (!staleCache.isNullOrEmpty()) {
                    return AppResult.Success(staleCache)
                }
                val throwable = itemsResult.exceptionOrNull()
                return AppResult.Error(
                    message = throwable?.message ?: "Unable to load books.",
                    cause = throwable
                )
            }

            val batch = itemsResult.getOrThrow()
                .filter { it.libraryId == library.id }
            if (batch.isEmpty()) {
                break
            }
            items += batch
            if (batch.size < FULL_LIBRARY_PAGE_SIZE) {
                break
            }
            page += 1
        }

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
        val books = items
            .distinctBy { it.id.trim() }
            .map { item ->
                item.toBookSummary(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token
                ).withResolvedProgress(mediaProgressByItemId[item.id])
                    .withLocalProgressOverride(connection.server.id)
            }
        putCache(booksCache, cacheKey, books)
        return AppResult.Success(books)
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
        val persistedLocal = if (forceRefresh) {
            null
        } else {
            readPersistedBookDetail(
                serverId = connection.server.id,
                libraryId = library.id,
                bookId = bookId
            )
        }
        val syncState = detailCacheDao.getDetailSyncState(
            serverId = connection.server.id,
            libraryId = library.id,
            resourceType = DETAIL_RESOURCE_BOOK,
            resourceId = bookId,
            resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT
        )
        val shouldRefresh = shouldRefreshDetail(
            policy = if (forceRefresh) DetailRefreshPolicy.Force else DetailRefreshPolicy.IfStale,
            localExists = persistedLocal != null,
            lastSuccessfulSyncAtMs = syncState?.lastSuccessfulSyncAtMs,
            maxAgeMs = DETAIL_CACHE_MAX_AGE_MS
        )
        if (!shouldRefresh && persistedLocal != null) {
            return AppResult.Success(persistedLocal)
        }
        val staleCache = if (forceRefresh) null else persistedLocal

        val refreshKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "bookDetail:$bookId"
        )
        return when (
            val refreshResult = runDedupedDetailRefresh("bookDetail:$refreshKey") {
                refreshPersistedBookDetail(
                    connection = connection,
                    libraryId = library.id,
                    bookId = bookId
                )
            }
        ) {
            is AppResult.Success -> {
                val cached = readPersistedBookDetail(
                    serverId = connection.server.id,
                    libraryId = library.id,
                    bookId = bookId
                )
                if (cached != null) {
                    AppResult.Success(cached)
                } else if (staleCache != null) {
                    AppResult.Success(staleCache)
                } else {
                    AppResult.Error("Unable to load book detail.")
                }
            }

            is AppResult.Error -> {
                if (staleCache != null) {
                    AppResult.Success(staleCache)
                } else {
                    refreshResult
                }
            }
        }
    }

    override fun observeBookDetail(bookId: String): Flow<BookDetail?> {
        val normalizedBookId = bookId.trim()
        if (normalizedBookId.isBlank()) return flowOf(null)
        return sessionPreferences.state
            .map { state ->
                val serverId = state.activeServerId?.trim().orEmpty()
                val libraryId = state.activeLibraryId?.trim().orEmpty()
                if (serverId.isBlank() || libraryId.isBlank()) {
                    null
                } else {
                    serverId to libraryId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids == null) {
                    flowOf(null)
                } else {
                    val (serverId, libraryId) = ids
                    combine(
                        detailCacheDao.observeBookSummary(serverId, libraryId, normalizedBookId),
                        detailCacheDao.observeBookDetail(serverId, libraryId, normalizedBookId),
                        detailCacheDao.observeBookChapters(serverId, libraryId, normalizedBookId),
                        detailCacheDao.observeBookBookmarks(serverId, libraryId, normalizedBookId)
                    ) { summary, detail, chapters, bookmarks ->
                        if (summary == null) {
                            null
                        } else {
                            BookDetail(
                                book = summary.toModel(),
                                description = detail?.description,
                                publishedYear = detail?.publishedYear ?: summary.publishedYear,
                                sizeBytes = detail?.sizeBytes,
                                chapters = chapters.map { chapter ->
                                    BookChapter(
                                        title = chapter.title,
                                        startSeconds = chapter.startSeconds,
                                        endSeconds = chapter.endSeconds
                                    )
                                },
                                bookmarks = bookmarks.map { bookmark ->
                                    BookBookmark(
                                        id = bookmark.id,
                                        libraryItemId = normalizedBookId,
                                        title = bookmark.title,
                                        timeSeconds = bookmark.timeSeconds,
                                        createdAtMs = bookmark.createdAtMs
                                    )
                                }
                            )
                        }
                    }
                }
            }
    }

    override suspend fun refreshBookDetail(
        bookId: String,
        policy: DetailRefreshPolicy
    ): AppResult<Unit> {
        if (bookId.isBlank()) {
            return AppResult.Error("Invalid book id.")
        }
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        val library = connection.library ?: return AppResult.Error("No active library selected.")
        val persistedLocal = readPersistedBookDetail(
            serverId = connection.server.id,
            libraryId = library.id,
            bookId = bookId
        )
        val syncState = detailCacheDao.getDetailSyncState(
            serverId = connection.server.id,
            libraryId = library.id,
            resourceType = DETAIL_RESOURCE_BOOK,
            resourceId = bookId,
            resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT
        )
        val shouldRefresh = shouldRefreshDetail(
            policy = policy,
            localExists = persistedLocal != null,
            lastSuccessfulSyncAtMs = syncState?.lastSuccessfulSyncAtMs,
            maxAgeMs = DETAIL_CACHE_MAX_AGE_MS
        )
        if (!shouldRefresh) return AppResult.Success(Unit)
        val refreshKey = contentCacheKey(
            serverId = connection.server.id,
            libraryId = library.id,
            suffix = "bookDetail:$bookId"
        )
        return runDedupedDetailRefresh(
            key = "bookDetail:$refreshKey"
        ) {
            refreshPersistedBookDetail(
                connection = connection,
                libraryId = library.id,
                bookId = bookId
            )
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
                ),
                tracks = detail.audioTracks.map { track ->
                    PlaybackTrack(
                        startOffsetSeconds = track.startOffsetSeconds.coerceAtLeast(0.0),
                        durationSeconds = track.durationSeconds?.takeIf { it >= 0.0 },
                        streamUrl = audiobookshelfApi.buildPlaybackUrl(
                            baseUrl = connection.server.baseUrl,
                            streamPath = track.contentUrl,
                            authToken = connection.token
                        )
                    )
                }
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
                        durationSeconds = progress.durationSeconds,
                        updatedAtMs = progress.updatedAtMs
                    ).withLocalProgressOverride(connection.server.id, bookId)
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
                    durationSeconds = it.durationSeconds,
                    updatedAtMs = it.updatedAtMs
                )
            }.withLocalProgressOverride(connection.server.id, bookId)
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
        return syncPlaybackProgressWithConnection(
            connection = connection,
            bookId = bookId,
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            isFinished = isFinished
        )
    }

    override suspend fun syncPlaybackProgressForServer(
        serverId: String,
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit> {
        if (serverId.isBlank()) return AppResult.Error("Invalid server id.")
        if (bookId.isBlank()) return AppResult.Error("Invalid book id.")
        val connection = when (val result = getConnectionForServer(serverId = serverId, requireLibrary = false)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }
        return syncPlaybackProgressWithConnection(
            connection = connection,
            bookId = bookId,
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            isFinished = isFinished
        )
    }

    private suspend fun syncPlaybackProgressWithConnection(
        connection: ActiveConnection,
        bookId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit> {
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
        putLocalProgressOverride(
            serverId = connection.server.id,
            mutation = BookProgressMutation(
                bookId = bookId,
                progressPercent = durationSeconds
                    ?.takeIf { it > 0.0 }
                    ?.let { duration -> (currentTimeSeconds.coerceAtLeast(0.0) / duration).coerceIn(0.0, 1.0) },
                currentTimeSeconds = currentTimeSeconds.coerceAtLeast(0.0),
                durationSeconds = durationSeconds,
                isFinished = isFinished
            )
        )
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

        return AppResult.Success(Unit)
    }

    override suspend fun fetchBookmarksForActiveLibrary(
        forceRefresh: Boolean
    ): AppResult<List<BookmarkEntry>> {
        val connection = when (val result = getActiveConnection(requireLibrary = true)) {
            is AppResult.Success -> result.value
            is AppResult.Error -> return result
        }

        val books = when (val booksResult = fetchAllBooksForActiveLibrary(forceRefresh = forceRefresh)) {
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
            title = bookmark.title,
            createdAtMs = bookmark.createdAtMs
        )
        if (deleteResult.isFailure) {
            return AppResult.Error(
                message = deleteResult.exceptionOrNull()?.message ?: "Unable to delete bookmark.",
                cause = deleteResult.exceptionOrNull()
            )
        }

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
            else -> UnfinishedProgressState(
                currentTimeSeconds = 0.0,
                durationSeconds = targetDuration,
                progressPercent = 0.0
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
        val mutation = BookProgressMutation(
            bookId = bookId,
            progressPercent = resolvedProgress.progressPercent,
            currentTimeSeconds = resolvedProgress.currentTimeSeconds,
            durationSeconds = resolvedProgress.durationSeconds,
            isFinished = finished
        )
        putLocalProgressOverride(connection.server.id, mutation)
        mutableBookProgressMutations.tryEmit(mutation)
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
                val progressDeferred = async {
                    audiobookshelfApi.getMediaProgress(
                        baseUrl = connection.server.baseUrl,
                        authToken = connection.token
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
                val progressResult = progressDeferred.await()
                val entitiesResult = entitiesDeferred.await()
                if (booksResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = booksResult.exceptionOrNull()?.message ?: "Unable to search books.",
                        cause = booksResult.exceptionOrNull()
                    )
                }
                if (progressResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = progressResult.exceptionOrNull()?.message ?: "Unable to load search progress.",
                        cause = progressResult.exceptionOrNull()
                    )
                }
                if (entitiesResult.isFailure) {
                    return@coroutineScope AppResult.Error(
                        message = entitiesResult.exceptionOrNull()?.message ?: "Unable to search entities.",
                        cause = entitiesResult.exceptionOrNull()
                    )
                }

                val progressByItemId = progressResult.getOrThrow().associateBy { it.libraryItemId }
                val books = booksResult.getOrThrow()
                    .map {
                        it.toBookSummary(
                            baseUrl = connection.server.baseUrl,
                            authToken = connection.token
                        ).withResolvedProgress(progressByItemId[it.id])
                            .withLocalProgressOverride(connection.server.id)
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
        recentlyAddedLimit: Int,
        forceRefreshDerivedContent: Boolean
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
                    fetchAllBooksForActiveLibrary(forceRefresh = forceRefreshDerivedContent)
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
                            .withLocalProgressOverride(connection.server.id)
                    }
                val allBooks = when (allBooksResult) {
                    is AppResult.Success -> allBooksResult.value
                    is AppResult.Error -> emptyList()
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
                detailCacheDao.deleteDetailSyncStateForServer(server.id)
            }
            clearContentCachesForServer(server.id)
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
        runCatching { deletePersistedDetailCacheForServer(serverId) }
        runCatching { sessionPreferences.setServerAvatarUri(serverId, null) }
        clearContentCaches()
    }

    private suspend fun getActiveConnection(requireLibrary: Boolean): AppResult<ActiveConnection> {
        val session = sessionPreferences.state.first()
        val activeServerId = session.activeServerId
            ?: return AppResult.Error("No active server selected.")
        return getConnectionForServer(serverId = activeServerId, requireLibrary = requireLibrary)
    }

    private suspend fun getConnectionForServer(
        serverId: String,
        requireLibrary: Boolean
    ): AppResult<ActiveConnection> {
        val normalizedServerId = serverId.trim()
        if (normalizedServerId.isBlank()) return AppResult.Error("Invalid server id.")
        val server = serverDao.getById(normalizedServerId)
            ?: run {
                val session = sessionPreferences.state.first()
                if (session.activeServerId == normalizedServerId) {
                    sessionPreferences.setActiveSelection(serverId = null, libraryId = null)
                    sessionPreferences.setRequiresLibrarySelection(false)
                    return AppResult.Error("Active server not found.")
                }
                return AppResult.Error("Server not found.")
            }
        val token = secureTokenStorage.getToken(server.id)
            ?: run {
                val session = sessionPreferences.state.first()
                if (session.activeServerId == server.id) {
                    sessionPreferences.setActiveSelection(serverId = null, libraryId = null)
                    sessionPreferences.setRequiresLibrarySelection(false)
                    return AppResult.Error("No saved session for this server. Please log in again.")
                }
                return AppResult.Error("No saved session for this server.")
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

        val session = sessionPreferences.state.first()
        val activeLibraryId = session.activeLibraryId
            ?: return AppResult.Error("No active library selected.")
        val library = libraryDao.getByServerAndId(server.id, activeLibraryId)
            ?: run {
                if (session.activeServerId == server.id) {
                    sessionPreferences.setActiveLibraryId(null)
                    sessionPreferences.setRequiresLibrarySelection(true)
                    return AppResult.Error("Active library not found.")
                }
                return AppResult.Error("Library not found.")
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

    private fun bookProgressOverrideKey(serverId: String, bookId: String): String {
        return "$serverId|$bookId"
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

    private suspend fun clearContentCaches() {
        cacheMutex.withLock {
            booksCache.clear()
            authorsCache.clear()
            narratorsCache.clear()
            seriesCache.clear()
            collectionsCache.clear()
            playlistsCache.clear()
        }
    }

    private suspend fun clearContentCachesForServer(serverId: String) {
        val normalizedPrefix = "$serverId|"
        cacheMutex.withLock {
            booksCache.keys.removeAll { it.startsWith(normalizedPrefix) }
            authorsCache.keys.removeAll { it.startsWith(normalizedPrefix) }
            narratorsCache.keys.removeAll { it.startsWith(normalizedPrefix) }
            seriesCache.keys.removeAll { it.startsWith(normalizedPrefix) }
            collectionsCache.keys.removeAll { it.startsWith(normalizedPrefix) }
            playlistsCache.keys.removeAll { it.startsWith(normalizedPrefix) }
        }
    }

    private suspend fun deletePersistedDetailCacheForServer(serverId: String) {
        detailCacheDao.deleteBookBookmarksForServer(serverId)
        detailCacheDao.deleteBookChaptersForServer(serverId)
        detailCacheDao.deleteBookDetailsForServer(serverId)
        detailCacheDao.deleteBookSummariesForServer(serverId)
        detailCacheDao.deleteSeriesMembershipsForServer(serverId)
        detailCacheDao.deleteSeriesSummariesForServer(serverId)
        detailCacheDao.deleteDetailSyncStateForServer(serverId)
    }

    private suspend fun runDedupedDetailRefresh(
        key: String,
        block: suspend () -> AppResult<Unit>
    ): AppResult<Unit> = refreshDeduper.runDeduped(key, block)

    private suspend fun refreshPersistedBookDetail(
        connection: ActiveConnection,
        libraryId: String,
        bookId: String
    ): AppResult<Unit> {
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
                            .withLocalProgressOverride(connection.server.id)
                    )
                }

                persistBookDetailSnapshot(
                    serverId = connection.server.id,
                    libraryId = libraryId,
                    detail = detail
                )
                AppResult.Success(Unit)
            }
        } catch (t: Throwable) {
            AppResult.Error("Unable to load book detail.", t)
        }
    }

    private suspend fun refreshPersistedSeriesContents(
        connection: ActiveConnection,
        libraryId: String,
        seriesId: String
    ): AppResult<Unit> {
        return try {
            val items = mutableListOf<AudiobookshelfLibraryItemDto>()
            var page = 0
            while (page < MAX_FULL_LIBRARY_PAGES) {
                val itemsResult = audiobookshelfApi.getLibraryItems(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    libraryId = libraryId,
                    limit = FULL_LIBRARY_PAGE_SIZE,
                    page = page,
                    filter = "series.$seriesId",
                    collapseSeries = false
                )
                if (itemsResult.isFailure) {
                    return AppResult.Error(
                        message = itemsResult.exceptionOrNull()?.message ?: "Unable to load series.",
                        cause = itemsResult.exceptionOrNull()
                    )
                }

                val batch = itemsResult.getOrThrow()
                    .filter { it.libraryId == libraryId }
                if (batch.isEmpty()) break
                items += batch
                if (batch.size < FULL_LIBRARY_PAGE_SIZE) break
                page += 1
            }

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
            val entries = items.map { item ->
                item.toSeriesDetailEntry(
                    baseUrl = connection.server.baseUrl,
                    authToken = connection.token,
                    mediaProgress = mediaProgressByItemId[item.id],
                    serverId = connection.server.id
                )
            }

            persistSeriesContentsSnapshot(
                serverId = connection.server.id,
                libraryId = libraryId,
                seriesId = seriesId,
                entries = entries
            )
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Error("Unable to load series.", t)
        }
    }

    private suspend fun persistSeriesSummaries(
        serverId: String,
        libraryId: String,
        series: List<NamedEntitySummary>
    ) {
        val updatedAtMs = System.currentTimeMillis()
        detailCacheDao.upsertSeriesSummaries(
            series.map { item ->
                SeriesSummaryEntity(
                    serverId = serverId,
                    libraryId = libraryId,
                    id = item.id,
                    name = item.name,
                    subtitle = item.subtitle,
                    imageUrl = item.imageUrl,
                    description = item.description,
                    updatedAtMs = updatedAtMs
                )
            }
        )
    }

    private suspend fun persistBookDetailSnapshot(
        serverId: String,
        libraryId: String,
        detail: BookDetail
    ) {
        val syncedAtMs = System.currentTimeMillis()
        appDatabase.withTransaction {
            detailCacheDao.upsertBookSummary(
                detail.book.toEntity(
                    serverId = serverId,
                    updatedAtMs = syncedAtMs
                )
            )
            detailCacheDao.upsertBookDetail(
                BookDetailEntity(
                    serverId = serverId,
                    libraryId = libraryId,
                    bookId = detail.book.id,
                    description = detail.description,
                    publishedYear = detail.publishedYear,
                    sizeBytes = detail.sizeBytes,
                    updatedAtMs = syncedAtMs
                )
            )
            detailCacheDao.deleteBookChapters(serverId, libraryId, detail.book.id)
            if (detail.chapters.isNotEmpty()) {
                detailCacheDao.insertBookChapters(
                    detail.chapters.mapIndexed { index, chapter ->
                        BookChapterEntity(
                            serverId = serverId,
                            libraryId = libraryId,
                            bookId = detail.book.id,
                            chapterIndex = index,
                            title = chapter.title,
                            startSeconds = chapter.startSeconds,
                            endSeconds = chapter.endSeconds
                        )
                    }
                )
            }
            detailCacheDao.deleteBookBookmarks(serverId, libraryId, detail.book.id)
            if (detail.bookmarks.isNotEmpty()) {
                detailCacheDao.insertBookBookmarks(
                    detail.bookmarks.map { bookmark ->
                        BookBookmarkEntity(
                            serverId = serverId,
                            libraryId = libraryId,
                            bookId = detail.book.id,
                            id = bookmark.id,
                            title = bookmark.title,
                            timeSeconds = bookmark.timeSeconds,
                            createdAtMs = bookmark.createdAtMs
                        )
                    }
                )
            }
            detailCacheDao.upsertDetailSyncState(
                DetailSyncStateEntity(
                    serverId = serverId,
                    libraryId = libraryId,
                    resourceType = DETAIL_RESOURCE_BOOK,
                    resourceId = detail.book.id,
                    resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT,
                    lastSuccessfulSyncAtMs = syncedAtMs,
                    lastAttemptedSyncAtMs = syncedAtMs
                )
            )
        }
    }

    private suspend fun persistSeriesContentsSnapshot(
        serverId: String,
        libraryId: String,
        seriesId: String,
        entries: List<SeriesDetailEntry>
    ) {
        val syncedAtMs = System.currentTimeMillis()
        appDatabase.withTransaction {
            val bookEntries = entries.mapNotNull { entry ->
                (entry as? SeriesDetailEntry.BookItem)?.book
            }
            if (bookEntries.isNotEmpty()) {
                detailCacheDao.upsertBookSummaries(
                    bookEntries.map { book ->
                        book.toEntity(
                            serverId = serverId,
                            updatedAtMs = syncedAtMs
                        )
                    }
                )
            }
            detailCacheDao.deleteSeriesMemberships(serverId, libraryId, seriesId, collapseSubseries = false)
            if (entries.isNotEmpty()) {
                detailCacheDao.upsertSeriesMemberships(
                    entries.mapIndexed { index, entry ->
                        when (entry) {
                            is SeriesDetailEntry.BookItem -> {
                                SeriesMembershipEntity(
                                    serverId = serverId,
                                    libraryId = libraryId,
                                    seriesId = seriesId,
                                    collapseSubseries = false,
                                    stableId = entry.stableId,
                                    position = index,
                                    entryType = SERIES_ENTRY_TYPE_BOOK,
                                    bookId = entry.book.id,
                                    subseriesId = null,
                                    displayName = null,
                                    bookCount = null,
                                    coverUrl = null,
                                    sequenceLabel = null,
                                    updatedAtMs = syncedAtMs
                                )
                            }

                            is SeriesDetailEntry.SubseriesItem -> {
                                SeriesMembershipEntity(
                                    serverId = serverId,
                                    libraryId = libraryId,
                                    seriesId = seriesId,
                                    collapseSubseries = false,
                                    stableId = entry.stableId,
                                    position = index,
                                    entryType = SERIES_ENTRY_TYPE_SUBSERIES,
                                    bookId = null,
                                    subseriesId = entry.id,
                                    displayName = entry.name,
                                    bookCount = entry.bookCount,
                                    coverUrl = entry.coverUrl,
                                    sequenceLabel = entry.sequenceLabel,
                                    updatedAtMs = syncedAtMs
                                )
                            }
                        }
                    }
                )
            }
            detailCacheDao.upsertDetailSyncState(
                DetailSyncStateEntity(
                    serverId = serverId,
                    libraryId = libraryId,
                    resourceType = DETAIL_RESOURCE_SERIES,
                    resourceId = seriesId,
                    resourceVariant = DETAIL_RESOURCE_VARIANT_DEFAULT,
                    lastSuccessfulSyncAtMs = syncedAtMs,
                    lastAttemptedSyncAtMs = syncedAtMs
                )
            )
        }
    }

    private suspend fun readPersistedBookDetail(
        serverId: String,
        libraryId: String,
        bookId: String
    ): BookDetail? {
        val summary = detailCacheDao.getBookSummary(serverId, libraryId, bookId) ?: return null
        val detail = detailCacheDao.getBookDetail(serverId, libraryId, bookId)
        val chapters = detailCacheDao.getBookChapters(serverId, libraryId, bookId)
        val bookmarks = detailCacheDao.getBookBookmarks(serverId, libraryId, bookId)
        return BookDetail(
            book = summary.toModel(),
            description = detail?.description,
            publishedYear = detail?.publishedYear ?: summary.publishedYear,
            sizeBytes = detail?.sizeBytes,
            chapters = chapters.map { chapter ->
                BookChapter(
                    title = chapter.title,
                    startSeconds = chapter.startSeconds,
                    endSeconds = chapter.endSeconds
                )
            },
            bookmarks = bookmarks.map { bookmark ->
                BookBookmark(
                    id = bookmark.id,
                    libraryItemId = bookId,
                    title = bookmark.title,
                    timeSeconds = bookmark.timeSeconds,
                    createdAtMs = bookmark.createdAtMs
                )
            }
        )
    }

    private suspend fun readPersistedRawSeriesContents(
        serverId: String,
        libraryId: String,
        seriesId: String
    ): List<SeriesDetailEntry>? {
        val memberships = detailCacheDao.getSeriesMemberships(
            serverId = serverId,
            libraryId = libraryId,
            seriesId = seriesId,
            collapseSubseries = false
        )
        if (memberships.isEmpty()) return null
        return memberships.toSeriesDetailEntries(serverId = serverId, libraryId = libraryId)
    }

    private suspend fun readPersistedLegacyCollapsedSeriesContents(
        serverId: String,
        libraryId: String,
        seriesId: String
    ): List<SeriesDetailEntry>? {
        val memberships = detailCacheDao.getSeriesMemberships(
            serverId = serverId,
            libraryId = libraryId,
            seriesId = seriesId,
            collapseSubseries = true
        )
        if (memberships.isEmpty()) return null
        return memberships.toSeriesDetailEntries(serverId = serverId, libraryId = libraryId)
    }

    private suspend fun List<SeriesMembershipEntity>.toSeriesDetailEntries(
        serverId: String,
        libraryId: String
    ): List<SeriesDetailEntry> {
        return mapNotNull { membership ->
            when (membership.entryType) {
                SERIES_ENTRY_TYPE_BOOK -> membership.bookId
                    ?.let { bookId -> detailCacheDao.getBookSummary(serverId, libraryId, bookId) }
                    ?.let { summary -> SeriesDetailEntry.BookItem(summary.toModel()) }

                SERIES_ENTRY_TYPE_SUBSERIES -> {
                    SeriesDetailEntry.SubseriesItem(
                        id = membership.subseriesId.orEmpty(),
                        name = membership.displayName.orEmpty(),
                        bookCount = membership.bookCount ?: 0,
                        coverUrl = membership.coverUrl,
                        sequenceLabel = membership.sequenceLabel
                    )
                }

                else -> null
            }
        }
    }

    private suspend fun presentPersistedSeriesContents(
        serverId: String,
        libraryId: String,
        seriesId: String,
        collapseSubseries: Boolean,
        rawEntries: List<SeriesDetailEntry>,
        seriesNameOverride: String? = null
    ): List<SeriesDetailEntry> {
        if (!collapseSubseries) return rawEntries
        val books = rawEntries.mapNotNull { (it as? SeriesDetailEntry.BookItem)?.book }
        if (books.isEmpty()) return emptyList()
        val seriesName = seriesNameOverride
            ?: detailCacheDao.getSeriesSummary(serverId, libraryId, seriesId)?.name
            ?: ""
        return buildCollapsedSeriesEntriesFromRaw(
            seriesId = seriesId,
            targetSeriesName = seriesName,
            books = books
        )
    }

    private fun buildCollapsedSeriesEntriesFromRaw(
        seriesId: String,
        targetSeriesName: String,
        books: List<BookSummary>
    ): List<SeriesDetailEntry> {
        val sortedBooks = sortSeriesBooksForRepository(
            books = books,
            targetSeriesName = targetSeriesName
        )
        if (sortedBooks.isEmpty()) return emptyList()
        val normalizedSeries = normalizeSeriesKeyForRepository(targetSeriesName)
        val childSeriesByBookId = sortedBooks.associate { book ->
            book.id to resolveChildSeriesCandidateForRepository(
                book = book,
                targetSeriesId = seriesId,
                normalizedSeries = normalizedSeries
            )
        }
        val childGroups = sortedBooks
            .mapNotNull { book ->
                val child = childSeriesByBookId[book.id] ?: return@mapNotNull null
                child to book
            }
            .groupBy(keySelector = { it.first.key }, valueTransform = { it.second })
        val collapsibleChildKeys = childGroups
            .filterValues { groupedBooks ->
                groupedBooks.isNotEmpty() && groupedBooks.size < sortedBooks.size
            }
            .keys

        val emittedChildKeys = mutableSetOf<String>()
        return buildList {
            sortedBooks.forEach { book ->
                val child = childSeriesByBookId[book.id]
                if (child == null || child.key !in collapsibleChildKeys) {
                    add(SeriesDetailEntry.BookItem(book))
                    return@forEach
                }
                if (!emittedChildKeys.add(child.key)) {
                    return@forEach
                }
                val groupedBooks = childGroups[child.key].orEmpty()
                add(
                    SeriesDetailEntry.SubseriesItem(
                        id = child.id.orEmpty(),
                        name = child.name,
                        bookCount = groupedBooks.size.coerceAtLeast(1),
                        coverUrl = groupedBooks.firstOrNull()?.coverUrl
                    )
                )
            }
        }
    }

    private data class RepositoryChildSeriesCandidate(
        val key: String,
        val id: String?,
        val name: String
    )

    private fun resolveChildSeriesCandidateForRepository(
        book: BookSummary,
        targetSeriesId: String,
        normalizedSeries: String
    ): RepositoryChildSeriesCandidate? {
        val candidateNames = if (book.seriesNames.isNotEmpty()) {
            book.seriesNames
        } else {
            listOfNotNull(book.seriesName)
        }
        return candidateNames.mapIndexedNotNull { index, name ->
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) return@mapIndexedNotNull null
            val candidateId = book.seriesIds.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
            val matchesTarget = (candidateId != null &&
                seriesIdsLikelyMatchForRepository(candidateId, targetSeriesId)) ||
                matchesSeriesNameForRepository(
                    normalizedSeries = normalizedSeries,
                    candidateSeriesNames = listOf(trimmedName)
                )
            if (matchesTarget) {
                null
            } else {
                RepositoryChildSeriesCandidate(
                    key = candidateId ?: "series:${normalizeSeriesKeyForRepository(trimmedName)}",
                    id = candidateId,
                    name = trimmedName
                )
            }
        }.firstOrNull()
    }

    private fun normalizeSeriesKeyForRepository(value: String): String {
        return value
            .trim()
            .replace(Regex("\\s*#\\d+.*$"), "")
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun matchesSeriesNameForRepository(
        normalizedSeries: String,
        candidateSeriesNames: List<String>
    ): Boolean {
        if (normalizedSeries.isBlank()) return false
        return candidateSeriesNames.any { candidateName ->
            val normalizedCandidate = normalizeSeriesKeyForRepository(candidateName)
            normalizedCandidate.isNotBlank() && normalizedCandidate == normalizedSeries
        }
    }

    private fun sortSeriesBooksForRepository(
        books: List<BookSummary>,
        targetSeriesName: String
    ): List<BookSummary> {
        val normalizedTargetSeries = normalizeSeriesKeyForRepository(targetSeriesName)
        return books.sortedWith(
            compareBy<BookSummary> { book ->
                val normalizedPrimarySeries = normalizeSeriesKeyForRepository(book.seriesName.orEmpty())
                if (
                    normalizedTargetSeries.isNotBlank() &&
                    normalizedPrimarySeries == normalizedTargetSeries
                ) {
                    0
                } else {
                    1
                }
            }
                .thenBy {
                    it.seriesName
                        ?.trim()
                        ?.replace(Regex("\\s*#\\d+.*$"), "")
                        ?.replace(Regex("\\s+"), " ")
                        ?.lowercase()
                        .orEmpty()
                }
                .thenBy { it.seriesSequence ?: Double.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    private fun seriesIdsLikelyMatchForRepository(candidateId: String, targetId: String): Boolean {
        val normalizedCandidate = candidateId.trim()
        val normalizedTarget = targetId.trim()
        if (normalizedCandidate.isBlank() || normalizedTarget.isBlank()) return false
        return normalizedCandidate.equals(normalizedTarget, ignoreCase = true) ||
            normalizedCandidate.endsWith(normalizedTarget, ignoreCase = true) ||
            normalizedTarget.endsWith(normalizedCandidate, ignoreCase = true)
    }

    private suspend fun resolveCachedSeriesId(
        serverId: String,
        libraryId: String,
        seriesName: String
    ): String? {
        detailCacheDao.getSeriesSummaryByName(
            serverId = serverId,
            libraryId = libraryId,
            seriesName = seriesName
        )?.id?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return detailCacheDao.getFirstBookSummaryBySeriesName(
            serverId = serverId,
            libraryId = libraryId,
            seriesName = seriesName
        )?.toModel()
            ?.seriesIds
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun BookSummary.toEntity(
        serverId: String,
        updatedAtMs: Long
    ): BookSummaryEntity {
        return BookSummaryEntity(
            serverId = serverId,
            libraryId = libraryId,
            id = id,
            title = title,
            authorName = authorName,
            narratorName = narratorName,
            durationSeconds = durationSeconds,
            coverUrl = coverUrl,
            seriesName = seriesName,
            seriesNamesJson = seriesNames.toJsonString(),
            seriesIdsJson = seriesIds.toJsonString(),
            seriesSequence = seriesSequence,
            genresJson = genres.toJsonString(),
            publishedYear = publishedYear,
            addedAtMs = addedAtMs,
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            isFinished = isFinished,
            updatedAtMs = updatedAtMs
        )
    }

    private fun BookSummaryEntity.toModel(): BookSummary {
        return BookSummary(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = authorName,
            narratorName = narratorName,
            durationSeconds = durationSeconds,
            coverUrl = coverUrl,
            seriesName = seriesName,
            seriesNames = seriesNamesJson.toJsonStringList(),
            seriesIds = seriesIdsJson.toJsonStringList(),
            seriesSequence = seriesSequence,
            genres = genresJson.toJsonStringList(),
            publishedYear = publishedYear,
            addedAtMs = addedAtMs,
            progressPercent = progressPercent,
            currentTimeSeconds = currentTimeSeconds,
            isFinished = isFinished
        )
    }

    private fun SeriesSummaryEntity.toModel(): NamedEntitySummary {
        return NamedEntitySummary(
            id = id,
            name = name,
            subtitle = subtitle,
            imageUrl = imageUrl,
            description = description
        )
    }

    private fun List<String>.toJsonString(): String = JSONArray(this).toString()

    private fun String?.toJsonStringList(): List<String> {
        val payload = this?.trim().orEmpty()
        if (payload.isBlank()) return emptyList()
        val array = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun seriesResourceVariant(collapseSubseries: Boolean): String {
        return "collapseSubseries=$collapseSubseries"
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

    private suspend fun AudiobookshelfLibraryItemDto.toSeriesDetailEntry(
        baseUrl: String,
        authToken: String,
        mediaProgress: AudiobookshelfMediaProgressDto?,
        serverId: String
    ): SeriesDetailEntry {
        val collapsed = collapsedSeries
        if (collapsed != null) {
            return SeriesDetailEntry.SubseriesItem(
                id = collapsed.id,
                name = collapsed.name,
                bookCount = collapsed.bookCount.coerceAtLeast(1),
                coverUrl = collapsed.libraryItemIds.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { libraryItemId ->
                        audiobookshelfApi.buildCoverUrl(baseUrl, libraryItemId, authToken)
                    }
                    ?: audiobookshelfApi.buildCoverUrl(baseUrl, id, authToken),
                sequenceLabel = collapsed.sequenceLabel
            )
        }

        val resolvedBook = toBookSummary(
            baseUrl = baseUrl,
            authToken = authToken
        ).withResolvedProgress(mediaProgress)
            .withLocalProgressOverride(serverId)
        return SeriesDetailEntry.BookItem(resolvedBook)
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
            durationSeconds = mergedDurationSeconds,
            progressPercent = mergedProgressPercent,
            currentTimeSeconds = mergedCurrentTimeSeconds,
            isFinished = isFinished || finishedFromProgress
        )
    }

    private suspend fun BookSummary.withLocalProgressOverride(serverId: String): BookSummary {
        val override = localBookProgressOverride(
            serverId = serverId,
            bookId = id,
            fetchedProgressPercent = progressPercent,
            fetchedCurrentTimeSeconds = currentTimeSeconds,
            fetchedDurationSeconds = durationSeconds,
            fetchedIsFinished = isFinished
        ) ?: return this
        return copy(
            durationSeconds = override.durationSeconds ?: durationSeconds,
            progressPercent = override.progressPercent,
            currentTimeSeconds = override.currentTimeSeconds,
            isFinished = override.isFinished
        )
    }

    private suspend fun PlaybackProgress?.withLocalProgressOverride(
        serverId: String,
        bookId: String
    ): PlaybackProgress? {
        val override = localBookProgressOverride(
            serverId = serverId,
            bookId = bookId,
            fetchedProgressPercent = this?.progressPercent,
            fetchedCurrentTimeSeconds = this?.currentTimeSeconds,
            fetchedDurationSeconds = this?.durationSeconds,
            fetchedIsFinished = this?.progressPercent?.let { it >= 0.995 } == true
        ) ?: return this
        return PlaybackProgress(
            progressPercent = override.progressPercent,
            currentTimeSeconds = override.currentTimeSeconds,
            durationSeconds = override.durationSeconds ?: this?.durationSeconds,
            updatedAtMs = this?.updatedAtMs
        )
    }

    private suspend fun putLocalProgressOverride(serverId: String, mutation: BookProgressMutation) {
        cacheMutex.withLock {
            localBookProgressOverrides[bookProgressOverrideKey(serverId, mutation.bookId)] =
                LocalBookProgressOverride(mutation = mutation)
        }
    }

    private suspend fun localBookProgressOverride(
        serverId: String,
        bookId: String,
        fetchedProgressPercent: Double?,
        fetchedCurrentTimeSeconds: Double?,
        fetchedDurationSeconds: Double?,
        fetchedIsFinished: Boolean
    ): BookProgressMutation? = cacheMutex.withLock {
        val key = bookProgressOverrideKey(serverId, bookId)
        val entry = localBookProgressOverrides[key] ?: return@withLock null
        if ((System.currentTimeMillis() - entry.savedAtMs) > LOCAL_PROGRESS_OVERRIDE_MAX_AGE_MS) {
            localBookProgressOverrides.remove(key)
            return@withLock null
        }
        val override = selectLocalProgressOverride(
            mutation = entry.mutation,
            fetchedProgressPercent = fetchedProgressPercent,
            fetchedCurrentTimeSeconds = fetchedCurrentTimeSeconds,
            fetchedDurationSeconds = fetchedDurationSeconds,
            fetchedIsFinished = fetchedIsFinished,
            progressEpsilon = PROGRESS_MATCH_EPSILON,
            timeEpsilonSeconds = TIME_MATCH_EPSILON_SECONDS
        )
        if (override == null) {
            localBookProgressOverrides.remove(key)
            return@withLock null
        }
        override
    }

    internal fun progressMatchesMutation(
        mutation: BookProgressMutation,
        fetchedProgressPercent: Double?,
        fetchedCurrentTimeSeconds: Double?,
        fetchedDurationSeconds: Double?,
        fetchedIsFinished: Boolean,
        progressEpsilon: Double = PROGRESS_MATCH_EPSILON,
        timeEpsilonSeconds: Double = TIME_MATCH_EPSILON_SECONDS
    ): Boolean {
        return progressMatchesMutationInternal(
            mutation = mutation,
            fetchedProgressPercent = fetchedProgressPercent,
            fetchedCurrentTimeSeconds = fetchedCurrentTimeSeconds,
            fetchedDurationSeconds = fetchedDurationSeconds,
            fetchedIsFinished = fetchedIsFinished,
            progressEpsilon = progressEpsilon,
            timeEpsilonSeconds = timeEpsilonSeconds
        )
    }

    private fun approxEquals(left: Double?, right: Double?, epsilon: Double): Boolean {
        return approxEqualsInternal(left, right, epsilon)
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
