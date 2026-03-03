package com.stillshelf.app.data.api

import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.stillshelf.app.core.network.addAuthTokenFragment
import com.stillshelf.app.core.network.authorizationHeaderValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AudiobookshelfLibraryDto(
    val id: String,
    val name: String
)

data class AudiobookshelfServerStatusDto(
    val app: String,
    val serverVersion: String?
)

data class AudiobookshelfLibraryItemDto(
    val id: String,
    val libraryId: String,
    val title: String,
    val authorName: String,
    val narratorName: String?,
    val durationSeconds: Double?,
    val seriesName: String?,
    val seriesSequence: Double?,
    val genres: List<String>,
    val publishedYear: String?,
    val addedAtMs: Long?,
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val isFinished: Boolean
)

data class AudiobookshelfMediaProgressDto(
    val libraryItemId: String,
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val durationSeconds: Double?
)

data class AudiobookshelfNamedEntityDto(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val imagePath: String? = null,
    val description: String? = null
)

data class AudiobookshelfSearchDto(
    val authors: List<AudiobookshelfNamedEntityDto>,
    val series: List<AudiobookshelfNamedEntityDto>,
    val narrators: List<AudiobookshelfNamedEntityDto>
)

data class AudiobookshelfChapterDto(
    val title: String,
    val startSeconds: Double,
    val endSeconds: Double?
)

data class AudiobookshelfBookmarkDto(
    val id: String,
    val libraryItemId: String,
    val title: String?,
    val timeSeconds: Double?,
    val createdAtMs: Long?
)

data class AudiobookshelfBookDetailDto(
    val id: String,
    val libraryId: String,
    val title: String,
    val authorName: String,
    val narratorName: String?,
    val durationSeconds: Double?,
    val description: String?,
    val publishedYear: String?,
    val seriesName: String?,
    val seriesSequence: Double?,
    val genres: List<String>,
    val sizeBytes: Long?,
    val chapters: List<AudiobookshelfChapterDto>,
    val streamPath: String?
)

data class AudiobookshelfCollectionDto(
    val id: String,
    val name: String,
    val libraryId: String? = null,
    val itemCount: Int? = null
)

data class AudiobookshelfPlaylistDto(
    val id: String,
    val name: String,
    val libraryId: String? = null,
    val itemCount: Int? = null
)

@Singleton
class AudiobookshelfApi @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val mePayloadMutex = Mutex()
    @Volatile private var cachedMePayload: String? = null
    @Volatile private var cachedMePayloadAtMs: Long = 0L
    @Volatile private var cachedMePayloadBaseUrl: String? = null
    @Volatile private var cachedMePayloadToken: String? = null

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${baseUrl.removeSuffix("/")}/login")
            .post(body)
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(response.code, response.body?.string()))
                }

                val responseBody = response.body?.string().orEmpty()
                val token = extractToken(responseBody)
                    ?: throw IOException("Login succeeded, but no auth token was returned.")

                token
            }
        }
    }

    suspend fun getLibraries(
        baseUrl: String,
        authToken: String
    ): Result<List<AudiobookshelfLibraryDto>> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "api/libraries")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseLibraries(executeRequestWithRetry(request))
        }
    }

    suspend fun getServerStatus(baseUrl: String): Result<AudiobookshelfServerStatusDto> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "status")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(extractErrorMessage(response.code, response.body?.string()))
                }

                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                AudiobookshelfServerStatusDto(
                    app = root.optString("app"),
                    serverVersion = root.optString("serverVersion").ifBlank { null }
                )
            }
        }
    }

    suspend fun getLibraryItems(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        limit: Int,
        page: Int,
        sortBy: String? = null,
        desc: Boolean? = null,
        searchQuery: String? = null
    ): Result<List<AudiobookshelfLibraryItemDto>> = withContext(Dispatchers.IO) {
        val query = buildMap<String, String> {
            put("limit", limit.toString())
            put("page", page.toString())
            sortBy?.let { put("sort", it) }
            desc?.let { put("desc", if (it) "1" else "0") }
            searchQuery?.takeIf { it.isNotBlank() }?.let { put("q", it) }
        }

        val url = buildUrl(baseUrl, "api/libraries/$libraryId/items", query)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            val body = executeRequestWithRetry(request)
            parseLibraryItems(body, arrayKey = "results")
        }
    }

    suspend fun searchLibrary(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        query: String
    ): Result<AudiobookshelfSearchDto> = withContext(Dispatchers.IO) {
        val url = buildUrl(
            baseUrl = baseUrl,
            path = "api/libraries/$libraryId/search",
            query = mapOf("q" to query)
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseSearchResponse(executeRequestWithRetry(request))
        }
    }

    suspend fun getAuthors(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        limit: Int,
        page: Int
    ): Result<List<AudiobookshelfNamedEntityDto>> = withContext(Dispatchers.IO) {
        val query = mapOf(
            "limit" to limit.toString(),
            "page" to page.toString()
        )
        val url = buildUrl(baseUrl, "api/libraries/$libraryId/authors", query)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseNamedEntities(executeRequestWithRetry(request), "results") { item ->
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isBlank() || name.isBlank()) return@parseNamedEntities null
                    AudiobookshelfNamedEntityDto(
                        id = id,
                        name = name,
                        subtitle = item.optString("numBooks").takeIf { it.isNotBlank() }?.let { "$it books" },
                        imagePath = item.optString("imagePath")
                            .ifBlank { item.optString("avatarPath") }
                            .ifBlank { null },
                        description = item.optString("description")
                            .ifBlank { item.optString("bio") }
                            .ifBlank { item.optString("descriptionPlain") }
                            .ifBlank { null }
                    )
                }
        }
    }

    suspend fun getSeries(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        limit: Int,
        page: Int
    ): Result<List<AudiobookshelfNamedEntityDto>> = withContext(Dispatchers.IO) {
        val query = mapOf(
            "limit" to limit.toString(),
            "page" to page.toString()
        )
        val url = buildUrl(baseUrl, "api/libraries/$libraryId/series", query)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseNamedEntities(executeRequestWithRetry(request), "results") { item ->
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isBlank() || name.isBlank()) return@parseNamedEntities null
                    val booksCount = item.optJSONArray("books")?.length()
                    AudiobookshelfNamedEntityDto(
                        id = id,
                        name = name,
                        subtitle = booksCount?.let { "$it books" },
                        imagePath = item.optString("imagePath").ifBlank { null }
                    )
                }
        }
    }

    suspend fun getNarrators(
        baseUrl: String,
        authToken: String,
        libraryId: String
    ): Result<List<AudiobookshelfNamedEntityDto>> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "api/libraries/$libraryId/narrators")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseNarrators(executeRequestWithRetry(request))
        }
    }

    suspend fun getItemsInProgress(
        baseUrl: String,
        authToken: String
    ): Result<List<AudiobookshelfLibraryItemDto>> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "api/me/items-in-progress")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            val body = executeRequestWithRetry(request)
            parseLibraryItems(body, arrayKey = "libraryItems")
        }
    }

    suspend fun getItemDetail(
        baseUrl: String,
        authToken: String,
        itemId: String
    ): Result<AudiobookshelfBookDetailDto> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "api/items/$itemId", mapOf("expanded" to "1"))
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseItemDetail(executeRequestWithRetry(request))
        }
    }

    suspend fun getMediaProgress(
        baseUrl: String,
        authToken: String
    ): Result<List<AudiobookshelfMediaProgressDto>> = withContext(Dispatchers.IO) {
        runCatching {
            parseMediaProgress(requestMePayload(baseUrl, authToken))
        }
    }

    suspend fun getMediaProgressForItem(
        baseUrl: String,
        authToken: String,
        itemId: String
    ): Result<AudiobookshelfMediaProgressDto?> = withContext(Dispatchers.IO) {
        val url = buildUrl(baseUrl, "api/me/progress/$itemId")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .get()
            .build()

        runCatching {
            parseSingleMediaProgress(executeRequestWithRetry(request), fallbackItemId = itemId)
        }
    }

    suspend fun updateMediaProgressForItem(
        baseUrl: String,
        authToken: String,
        itemId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val progress = durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (currentTimeSeconds / it).coerceIn(0.0, 1.0) }

        val body = JSONObject()
            .put("currentTime", currentTimeSeconds)
            .put("isFinished", isFinished)
            .apply {
                durationSeconds?.let { put("duration", it) }
                progress?.let { put("progress", it) }
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val url = buildUrl(baseUrl, "api/me/progress/$itemId")
        val patchRequest = Request.Builder()
            .url(url)
            .header("Authorization", authHeaderValue(authToken))
            .method("PATCH", body)
            .build()

        runCatching {
            runCatching { executeRequestWithRetry(patchRequest) }.onSuccess { return@runCatching Unit }

            val postRequest = Request.Builder()
                .url(url)
                .header("Authorization", authHeaderValue(authToken))
                .post(body)
                .build()
            executeRequestWithRetry(postRequest)
            Unit
        }
    }

    suspend fun getBookmarks(
        baseUrl: String,
        authToken: String
    ): Result<List<AudiobookshelfBookmarkDto>> = withContext(Dispatchers.IO) {
        runCatching {
            parseBookmarks(requestMePayload(baseUrl, authToken))
        }
    }

    suspend fun createBookmark(
        baseUrl: String,
        authToken: String,
        itemId: String,
        timeSeconds: Double,
        title: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedItemId = itemId.trim()
        val normalizedTime = timeSeconds.coerceAtLeast(0.0)
        val normalizedTitle = title?.trim().takeUnless { it.isNullOrBlank() }
        val verifyStartedAtMs = System.currentTimeMillis()
        val requestBodies = buildList {
            add(JSONObject().put("time", normalizedTime))
            add(JSONObject().put("timeSeconds", normalizedTime))
            add(JSONObject().put("currentTime", normalizedTime))
            add(JSONObject().put("position", normalizedTime))
        }.flatMap { baseBody ->
            buildList {
                add(baseBody)
                if (!normalizedTitle.isNullOrBlank()) {
                    add(JSONObject(baseBody.toString()).put("title", normalizedTitle))
                    add(JSONObject(baseBody.toString()).put("note", normalizedTitle))
                }
            }
        }.distinctBy { it.toString() }

        val requestBuilders = buildList {
            listOf(
                "api/me/item/$itemId/bookmark",
                "api/me/items/$itemId/bookmark",
                "api/items/$itemId/bookmark",
                "api/me/bookmarks",
                "api/bookmarks"
            ).forEach { path ->
                requestBodies.forEach { body ->
                    add(
                        Request.Builder()
                            .url(buildUrl(baseUrl, path))
                            .header("Authorization", authHeaderValue(authToken))
                            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                            .build()
                    )
                }
            }
        }

        runCatching {
            invalidateMePayloadCache()
            val baselineForItemCount = getBookmarks(baseUrl, authToken)
                .getOrNull()
                ?.count { bookmark ->
                    bookmark.libraryItemId.matchesLibraryItemId(normalizedItemId)
                }
            var lastError: Throwable? = null
            requestBuilders.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    invalidateMePayloadCache()
                    val latestBookmarks = getBookmarks(baseUrl, authToken).getOrNull()
                    if (latestBookmarks == null) {
                        return@runCatching Unit
                    }
                    val bookmarksForItem = latestBookmarks
                        ?.filter { bookmark -> bookmark.libraryItemId.matchesLibraryItemId(normalizedItemId) }
                        .orEmpty()
                    val verified = when {
                        baselineForItemCount != null && bookmarksForItem.size > baselineForItemCount -> true
                        else -> {
                            bookmarksForItem.any { bookmark ->
                                val timeMatches = bookmark.timeSeconds?.let { bookmarkTime ->
                                    kotlin.math.abs(bookmarkTime - normalizedTime) <= BOOKMARK_VERIFY_TIME_TOLERANCE_SECONDS
                                } ?: false
                                val titleMatches = !normalizedTitle.isNullOrBlank() &&
                                    bookmark.title?.trim()?.equals(normalizedTitle, ignoreCase = true) == true
                                val recentEnough = bookmark.createdAtMs?.let { createdAt ->
                                    createdAt >= (verifyStartedAtMs - BOOKMARK_VERIFY_RECENT_GRACE_MS)
                                } ?: true
                                recentEnough && (timeMatches || titleMatches)
                            }
                        }
                    }
                    if (verified) {
                        return@runCatching Unit
                    }
                    lastError = IOException("Bookmark create verification failed.")
                    return@forEach
                }
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to add bookmark.", lastError)
        }
    }

    suspend fun getCollections(
        baseUrl: String,
        authToken: String,
        libraryId: String
    ): Result<List<AudiobookshelfCollectionDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val libraryScoped = Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()
            val fallbackGlobal = Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()

            val scoped = runCatching { executeRequestWithRetry(libraryScoped) }
            if (scoped.isSuccess) return@runCatching parseCollections(scoped.getOrThrow())

            val global = parseCollections(executeRequestWithRetry(fallbackGlobal))
            val filtered = global.filter { it.libraryId == null || it.libraryId == libraryId }
            if (filtered.isNotEmpty()) filtered else global
        }
    }

    suspend fun createCollection(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        name: String
    ): Result<AudiobookshelfCollectionDto> = withContext(Dispatchers.IO) {
        runCatching {
            val attempts = listOf(
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/collections"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .put("books", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/collections"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .put("bookIds", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/collections"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("name", name)
                            .put("books", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("name", name)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()
            )

            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    parseCollectionFromResponse(attempt.getOrThrow())
                        ?.let { return@runCatching it }
                } else {
                    lastError = attempt.exceptionOrNull()
                }
            }

            // Some ABS builds return a sparse/empty create payload; verify by listing.
            val discovered = getCollections(
                baseUrl = baseUrl,
                authToken = authToken,
                libraryId = libraryId
            ).getOrNull()?.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    (it.libraryId == null || it.libraryId == libraryId)
            }
            if (discovered != null) return@runCatching discovered

            throw IOException("Unable to create collection.", lastError)
        }
    }

    suspend fun createCollectionWithBook(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        name: String,
        bookId: String
    ): Result<AudiobookshelfCollectionDto> = withContext(Dispatchers.IO) {
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("books", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("bookIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("libraryItemIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("books", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("bookIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryItemIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
        )

        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val response = runCatching { executeRequestWithRetry(request) }
                if (response.isSuccess) {
                    parseCollectionFromResponse(response.getOrThrow())
                        ?.let { return@runCatching it }
                } else {
                    lastError = response.exceptionOrNull()
                }
            }
            val discovered = getCollections(
                baseUrl = baseUrl,
                authToken = authToken,
                libraryId = libraryId
            ).getOrNull()?.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    (it.libraryId == null || it.libraryId == libraryId)
            }
            if (discovered != null) return@runCatching discovered

            throw IOException("Unable to create collection with initial book.", lastError)
        }
    }

    suspend fun getCollectionBookIds(
        baseUrl: String,
        authToken: String,
        collectionId: String
    ): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()
            parseCollectionBookIds(executeRequestWithRetry(request))
        }
    }

    suspend fun addBookToCollection(
        baseUrl: String,
        authToken: String,
        collectionId: String,
        bookId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val idBody = JSONObject()
            .put("id", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemIdBody = JSONObject()
            .put("libraryItemId", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val idsBody = JSONObject()
            .put("bookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemIdsBody = JSONObject()
            .put("libraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val booksBody = JSONObject()
            .put("books", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemsBody = JSONObject()
            .put("libraryItems", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val addIdsBody = JSONObject()
            .put("addBookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/book"))
                .header("Authorization", authHeaderValue(authToken))
                .post(idBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/book"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/book/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(idsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", addIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", idsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", libraryItemsBody)
                .build()
        )

        runCatching {
            var lastError: Throwable? = null
            val failedUrls = mutableListOf<String>()
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    val verifyIds = getCollectionBookIds(baseUrl, authToken, collectionId).getOrNull()
                    if (verifyIds == null || verifyIds.contains(bookId)) {
                        return@runCatching Unit
                    }
                }
                lastError = attempt.exceptionOrNull()
                failedUrls += request.url.encodedPath
            }
            val message = buildString {
                append("Unable to add book to collection.")
                if (failedUrls.isNotEmpty()) {
                    append(" Tried: ")
                    append(failedUrls.joinToString(", "))
                }
            }
            throw IOException(message, lastError)
        }
    }

    suspend fun renameCollection(
        baseUrl: String,
        authToken: String,
        collectionId: String,
        name: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val altBody = JSONObject()
            .put("collection", JSONObject().put("name", name))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", body)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PUT", body)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", altBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PUT", altBody)
                .build()
        )
        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to rename collection.", lastError)
        }
    }

    suspend fun deleteCollection(
        baseUrl: String,
        authToken: String,
        collectionId: String,
        libraryId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val attempts = buildList {
            add(
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                    .header("Authorization", authHeaderValue(authToken))
                    .delete()
                    .build()
            )
            if (!libraryId.isNullOrBlank()) {
                add(
                    Request.Builder()
                        .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections/$collectionId"))
                        .header("Authorization", authHeaderValue(authToken))
                        .delete()
                        .build()
                )
            }
        }
        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to delete collection.", lastError)
        }
    }

    suspend fun renamePlaylist(
        baseUrl: String,
        authToken: String,
        playlistId: String,
        name: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("name", name)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val altBody = JSONObject()
            .put("playlist", JSONObject().put("name", name))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", body)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PUT", body)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", altBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PUT", altBody)
                .build()
        )
        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to rename playlist.", lastError)
        }
    }

    suspend fun removeBookFromCollection(
        baseUrl: String,
        authToken: String,
        collectionId: String,
        bookId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val removeIdsBody = JSONObject()
            .put("removeBookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeLibraryItemIdsBody = JSONObject()
            .put("removeLibraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/book/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId/books/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections/$collectionId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeLibraryItemIdsBody)
                .build()
        )
        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to remove book from collection.", lastError)
        }
    }

    suspend fun getPlaylists(
        baseUrl: String,
        authToken: String,
        libraryId: String
    ): Result<List<AudiobookshelfPlaylistDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val libraryScoped = Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()
            val fallbackGlobal = Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()

            val scoped = runCatching { executeRequestWithRetry(libraryScoped) }
            if (scoped.isSuccess) return@runCatching parsePlaylists(scoped.getOrThrow())

            val global = parsePlaylists(executeRequestWithRetry(fallbackGlobal))
            val filtered = global.filter { it.libraryId == null || it.libraryId == libraryId }
            if (filtered.isNotEmpty()) filtered else global
        }
    }

    suspend fun createPlaylist(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        name: String
    ): Result<AudiobookshelfPlaylistDto> = withContext(Dispatchers.IO) {
        runCatching {
            val attempts = listOf(
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/playlists"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .put("books", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/playlists"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .put("bookIds", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/playlists"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("libraryId", libraryId)
                            .put("name", name)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("name", name)
                            .put("books", JSONArray())
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build(),
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                    .header("Authorization", authHeaderValue(authToken))
                    .post(
                        JSONObject()
                            .put("name", name)
                            .toString()
                            .toRequestBody(JSON_MEDIA_TYPE)
                    )
                    .build()
            )

            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    parsePlaylistFromResponse(attempt.getOrThrow())
                        ?.let { return@runCatching it }
                } else {
                    lastError = attempt.exceptionOrNull()
                }
            }

            val discovered = getPlaylists(
                baseUrl = baseUrl,
                authToken = authToken,
                libraryId = libraryId
            ).getOrNull()?.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    (it.libraryId == null || it.libraryId == libraryId)
            }
            if (discovered != null) return@runCatching discovered

            throw IOException("Unable to create playlist.", lastError)
        }
    }

    suspend fun createPlaylistWithBook(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        name: String,
        bookId: String
    ): Result<AudiobookshelfPlaylistDto> = withContext(Dispatchers.IO) {
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("books", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("bookIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryId", libraryId)
                        .put("libraryItemIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("books", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("bookIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists"))
                .header("Authorization", authHeaderValue(authToken))
                .post(
                    JSONObject()
                        .put("name", name)
                        .put("libraryItemIds", JSONArray().put(bookId))
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE)
                )
                .build()
        )

        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val response = runCatching { executeRequestWithRetry(request) }
                if (response.isSuccess) {
                    parsePlaylistFromResponse(response.getOrThrow())
                        ?.let { return@runCatching it }
                } else {
                    lastError = response.exceptionOrNull()
                }
            }

            val discovered = getPlaylists(
                baseUrl = baseUrl,
                authToken = authToken,
                libraryId = libraryId
            ).getOrNull()?.firstOrNull {
                it.name.equals(name, ignoreCase = true) &&
                    (it.libraryId == null || it.libraryId == libraryId)
            }
            if (discovered != null) return@runCatching discovered

            throw IOException("Unable to create playlist with initial book.", lastError)
        }
    }

    suspend fun getPlaylistBookIds(
        baseUrl: String,
        authToken: String,
        playlistId: String
    ): Result<Set<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()
            parsePlaylistBookIds(executeRequestWithRetry(request))
        }
    }

    private data class PlaylistState(
        val ids: Set<String>,
        val itemCount: Int?
    )

    private suspend fun getPlaylistState(
        baseUrl: String,
        authToken: String,
        playlistId: String
    ): Result<PlaylistState> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .get()
                .build()
            val raw = executeRequestWithRetry(request)
            val ids = parsePlaylistBookIds(raw)
            val itemCount = parsePlaylistFromResponse(raw)?.itemCount
            PlaylistState(ids = ids, itemCount = itemCount)
        }
    }

    suspend fun addBookToPlaylist(
        baseUrl: String,
        authToken: String,
        playlistId: String,
        bookId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val idBody = JSONObject()
            .put("id", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemIdBody = JSONObject()
            .put("libraryItemId", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val directBookIdBody = JSONObject()
            .put("bookId", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val directItemIdBody = JSONObject()
            .put("itemId", bookId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val idsBody = JSONObject()
            .put("bookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemIdsBody = JSONObject()
            .put("libraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemIdsBody = JSONObject()
            .put("itemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val booksBody = JSONObject()
            .put("books", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemsBody = JSONObject()
            .put("libraryItems", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemsBody = JSONObject()
            .put("items", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val addIdsBody = JSONObject()
            .put("addBookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val addLibraryItemIdsBody = JSONObject()
            .put("addLibraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val addItemIdsBody = JSONObject()
            .put("addItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/book"))
                .header("Authorization", authHeaderValue(authToken))
                .post(idBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/book"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/book"))
                .header("Authorization", authHeaderValue(authToken))
                .post(directBookIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/item"))
                .header("Authorization", authHeaderValue(authToken))
                .post(directItemIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/item"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/item"))
                .header("Authorization", authHeaderValue(authToken))
                .post(directBookIdBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/book/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/item/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/libraryitem/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(idsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", addIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", addLibraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", addItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", idsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", libraryItemsBody)
                .build()
        )

        runCatching {
            var lastError: Throwable? = null
            val failedUrls = mutableListOf<String>()
            val beforeIds = getPlaylistBookIds(baseUrl, authToken, playlistId).getOrNull()
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    val afterIds = getPlaylistBookIds(baseUrl, authToken, playlistId).getOrNull()
                    val confirmedAdded = afterIds?.contains(bookId) == true ||
                        (beforeIds != null && afterIds != null && afterIds.size > beforeIds.size)
                    if (confirmedAdded) {
                        return@runCatching Unit
                    }
                }
                lastError = attempt.exceptionOrNull()
                failedUrls += request.url.encodedPath
            }
            val message = buildString {
                append("Unable to add book to playlist.")
                if (failedUrls.isNotEmpty()) {
                    append(" Tried: ")
                    append(failedUrls.joinToString(", "))
                }
            }
            throw IOException(message, lastError)
        }
    }

    suspend fun deletePlaylist(
        baseUrl: String,
        authToken: String,
        playlistId: String,
        libraryId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val attempts = buildList {
            add(
                Request.Builder()
                    .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                    .header("Authorization", authHeaderValue(authToken))
                    .delete()
                    .build()
            )
            if (!libraryId.isNullOrBlank()) {
                add(
                    Request.Builder()
                        .url(buildUrl(baseUrl, "api/libraries/$libraryId/playlists/$playlistId"))
                        .header("Authorization", authHeaderValue(authToken))
                        .delete()
                        .build()
                )
            }
        }
        runCatching {
            var lastError: Throwable? = null
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
                lastError = attempt.exceptionOrNull()
            }
            throw IOException("Unable to delete playlist.", lastError)
        }
    }

    suspend fun removeBookFromPlaylist(
        baseUrl: String,
        authToken: String,
        playlistId: String,
        bookId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val removeIdsBody = JSONObject()
            .put("removeBookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeLibraryItemIdsBody = JSONObject()
            .put("removeLibraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeItemIdsBody = JSONObject()
            .put("removeItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeBooksBody = JSONObject()
            .put("removeBooks", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeItemsBody = JSONObject()
            .put("removeItems", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val removeLibraryItemsBody = JSONObject()
            .put("removeLibraryItems", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val idsBody = JSONObject()
            .put("bookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemIdsBody = JSONObject()
            .put("libraryItemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemIdsBody = JSONObject()
            .put("itemIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val booksBody = JSONObject()
            .put("books", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val libraryItemsBody = JSONObject()
            .put("libraryItems", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val itemsBody = JSONObject()
            .put("items", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/item/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/book/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/libraryitem/$bookId"))
                .header("Authorization", authHeaderValue(authToken))
                .delete()
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeLibraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeBooksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeLibraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(removeIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(removeLibraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(removeItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(removeBooksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(removeItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(idsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryItemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/batch/remove"))
                .header("Authorization", authHeaderValue(authToken))
                .post(itemsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/books"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeLibraryItemIdsBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/playlists/$playlistId/items"))
                .header("Authorization", authHeaderValue(authToken))
                .method("PATCH", removeItemIdsBody)
                .build()
        )
        runCatching {
            var lastError: Throwable? = null
            val failedUrls = mutableListOf<String>()
            val beforeState = getPlaylistState(
                baseUrl = baseUrl,
                authToken = authToken,
                playlistId = playlistId
            ).getOrNull()
            val beforeIds = beforeState?.ids.orEmpty()
            val beforeCount = beforeState?.itemCount ?: beforeState?.ids?.size
            val beforeContainsTarget = containsLikelyMatchingId(beforeIds, bookId)
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) {
                    var verifiedRemoval = false
                    repeat(3) {
                        val afterResult = getPlaylistState(
                            baseUrl = baseUrl,
                            authToken = authToken,
                            playlistId = playlistId
                        )
                        val afterState = afterResult.getOrNull()
                        if (afterState != null) {
                            val afterIds = afterState.ids
                            val afterCount = afterState.itemCount ?: afterIds.size
                            val removedById = beforeContainsTarget && !containsLikelyMatchingId(afterIds, bookId)
                            val removedByCount = beforeCount != null && afterCount < beforeCount
                            if (removedById || removedByCount) {
                                verifiedRemoval = true
                                return@repeat
                            }
                            delay(120)
                            return@repeat
                        }
                        val verifyError = afterResult.exceptionOrNull()
                        if (verifyError?.message?.contains("404") == true) {
                            // Some ABS variants remove the playlist when the last book is removed.
                            if (beforeCount != null && beforeCount <= 1) {
                                verifiedRemoval = true
                                return@repeat
                            }
                            throw IOException("Playlist not found (HTTP 404) after removing book.", verifyError)
                        }
                        delay(120)
                    }
                    if (verifiedRemoval) {
                        return@runCatching Unit
                    }
                }
                lastError = attempt.exceptionOrNull()
                failedUrls += request.url.encodedPath
            }
            val message = buildString {
                append("Unable to remove book from playlist.")
                if (failedUrls.isNotEmpty()) {
                    append(" Tried: ")
                    append(failedUrls.joinToString(", "))
                }
            }
            throw IOException(message, lastError)
        }
    }

    fun buildCoverUrl(
        baseUrl: String,
        libraryItemId: String,
        authToken: String
    ): String {
        val normalized = baseUrl.removeSuffix("/")
        val httpUrl = normalized.toHttpUrlOrNull()
            ?: return addAuthTokenFragment("$normalized/api/items/$libraryItemId/cover", authToken)

        val rawUrl = httpUrl.newBuilder()
            .addPathSegments("api/items/$libraryItemId/cover")
            .build()
            .toString()
        return addAuthTokenFragment(rawUrl, authToken)
    }

    fun buildPlaybackUrl(
        baseUrl: String,
        streamPath: String,
        authToken: String
    ): String {
        val normalized = baseUrl.removeSuffix("/")
        val baseHttpUrl = normalized.toHttpUrlOrNull()
            ?: return addAuthTokenFragment("${normalized}/${streamPath.removePrefix("/")}", authToken)
        val cleanedPath = streamPath.removePrefix("/")

        val rawUrl = baseHttpUrl.newBuilder()
            .addPathSegments(cleanedPath)
            .build()
            .toString()
        return addAuthTokenFragment(rawUrl, authToken)
    }

    fun buildAuthorImageUrl(
        baseUrl: String,
        authorId: String,
        authToken: String
    ): String {
        val normalized = baseUrl.removeSuffix("/")
        val httpUrl = normalized.toHttpUrlOrNull()
            ?: return addAuthTokenFragment("$normalized/api/authors/$authorId/image", authToken)

        val rawUrl = httpUrl.newBuilder()
            .addPathSegments("api/authors/$authorId/image")
            .build()
            .toString()
        return addAuthTokenFragment(rawUrl, authToken)
    }

    private fun authHeaderValue(token: String): String = authorizationHeaderValue(token)

    private fun parseLibraries(rawJson: String): List<AudiobookshelfLibraryDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sourceArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("libraries")
                    ?: root.optJSONArray("results")
                    ?: root.optJSONArray("items")
                    ?: JSONArray()
            }
        }

        val parsed = buildList {
            for (index in 0 until sourceArray.length()) {
                val item = sourceArray.optJSONObject(index) ?: continue
                val id = item.optString("id").ifBlank { item.optString("_id") }
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(AudiobookshelfLibraryDto(id = id, name = name))
                }
            }
        }

        return parsed.distinctBy { it.id }
    }

    private fun extractToken(rawJson: String): String? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null

        val root = JSONObject(trimmed)
        val user = root.optJSONObject("user")

        return root.optString("token").ifBlank {
            root.optString("authToken")
        }.ifBlank {
            root.optString("accessToken")
        }.ifBlank {
            user?.optString("token").orEmpty()
        }.ifBlank {
            user?.optString("authToken").orEmpty()
        }.ifBlank {
            user?.optString("accessToken").orEmpty()
        }.ifBlank {
            ""
        }.takeIf { it.isNotBlank() }
    }

    private fun parseLibraryItems(
        rawJson: String,
        arrayKey: String
    ): List<AudiobookshelfLibraryItemDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val sourceArray = root.optJSONArray(arrayKey) ?: JSONArray()

        return buildList {
            for (index in 0 until sourceArray.length()) {
                val item = sourceArray.optJSONObject(index) ?: continue
                val parsedItem = parseLibraryItem(item) ?: continue
                add(parsedItem)
            }
        }
    }

    private fun parseLibraryItem(item: JSONObject): AudiobookshelfLibraryItemDto? {
        val id = item.optString("id")
        val libraryId = item.optString("libraryId")
        val media = item.optJSONObject("media") ?: return null
        val metadata = media.optJSONObject("metadata")

        val title = metadata?.optString("title").orEmpty().ifBlank {
            item.optString("relPath")
        }
        if (id.isBlank() || libraryId.isBlank() || title.isBlank()) return null

        val progressNode = item.optJSONObject("mediaProgress")
            ?: item.optJSONObject("progress")
        val addedAtRaw = item.optLongOrNull("addedAt")
            ?: item.optLongOrNull("createdAt")
            ?: item.optLongOrNull("added")
        val addedAtMs = normalizeEpochMillis(addedAtRaw)
        val progress = progressNode?.optDoubleOrNull("progress")
            ?: item.optDoubleOrNull("progress")
        val currentTime = progressNode?.optDoubleOrNull("currentTime")
            ?: item.optDoubleOrNull("currentTime")
            ?: item.optDoubleOrNull("timeListening")
        val finished = when {
            progressNode?.has("isFinished") == true -> progressNode.optBoolean("isFinished")
            item.has("isFinished") -> item.optBoolean("isFinished")
            progress != null -> progress >= 0.995
            else -> false
        }

        return AudiobookshelfLibraryItemDto(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = metadata?.optString("authorName").orEmpty().ifBlank { "Unknown Author" },
            narratorName = metadata?.optString("narratorName").orEmpty().ifBlank { null },
            durationSeconds = media.optDoubleOrNull("duration"),
            seriesName = metadata?.optString("seriesName")?.ifBlank { null },
            seriesSequence = extractSeriesSequence(metadata),
            genres = metadata.optStringList("genres"),
            publishedYear = metadata?.optString("publishedYear")?.ifBlank { null },
            addedAtMs = addedAtMs,
            progressPercent = progress?.coerceIn(0.0, 1.0),
            currentTimeSeconds = currentTime?.coerceAtLeast(0.0),
            isFinished = finished
        )
    }

    private fun normalizeEpochMillis(value: Long?): Long? {
        value ?: return null
        if (value <= 0L) return null
        return if (value < 1_000_000_000_000L) value * 1000L else value
    }

    private fun parseMediaProgress(rawJson: String): List<AudiobookshelfMediaProgressDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val sourceArray = root.optJSONArray("mediaProgress") ?: JSONArray()

        return buildList {
            for (index in 0 until sourceArray.length()) {
                val item = sourceArray.optJSONObject(index) ?: continue
                val libraryItemId = item.optString("libraryItemId")
                if (libraryItemId.isBlank()) continue

                add(
                    AudiobookshelfMediaProgressDto(
                        libraryItemId = libraryItemId,
                        progressPercent = item.optDoubleOrNull("progress"),
                        currentTimeSeconds = item.optDoubleOrNull("currentTime"),
                        durationSeconds = item.optDoubleOrNull("duration")
                    )
                )
            }
        }
    }

    private fun parseBookmarks(rawJson: String): List<AudiobookshelfBookmarkDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val sourceArray = root.optJSONArray("bookmarks") ?: JSONArray()

        return buildList {
            for (index in 0 until sourceArray.length()) {
                val item = sourceArray.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val libraryItemId = item.optString("libraryItemId")
                    .ifBlank { item.optString("itemId") }
                    .ifBlank { item.optString("libraryItem") }
                if (libraryItemId.isBlank()) continue

                add(
                    AudiobookshelfBookmarkDto(
                        id = id.ifBlank { "$libraryItemId-$index" },
                        libraryItemId = libraryItemId,
                        title = item.optString("title")
                            .ifBlank { item.optString("note") }
                            .ifBlank { null },
                        timeSeconds = item.optDoubleOrNull("time")
                            ?: item.optDoubleOrNull("timeSeconds")
                            ?: item.optDoubleOrNull("startTime")
                            ?: item.optDoubleOrNull("position")
                            ?: item.optDoubleOrNull("currentTime"),
                        createdAtMs = item.optEpochMillis(
                            "createdAt",
                            "createdAtMs",
                            "addedAt",
                            "addedAtMs",
                            "updatedAt",
                            "updatedAtMs",
                            "timestamp",
                            "date"
                        )
                    )
                )
            }
        }
    }

    private fun JSONObject.optEpochMillis(vararg keys: String): Long? {
        keys.forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            val raw = opt(key) ?: return@forEach
            when (raw) {
                is Number -> return normalizeEpochMillis(raw.toLong())
                is String -> {
                    val trimmed = raw.trim()
                    if (trimmed.isBlank()) return@forEach
                    trimmed.toLongOrNull()?.let { epoch -> return normalizeEpochMillis(epoch) }
                    runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { epoch ->
                        return normalizeEpochMillis(epoch)
                    }
                }
            }
        }
        return null
    }

    private fun String.matchesLibraryItemId(targetItemId: String): Boolean {
        val normalizedSource = this.trim()
        val normalizedTarget = targetItemId.trim()
        if (normalizedSource.isBlank() || normalizedTarget.isBlank()) return false
        return normalizedSource.equals(normalizedTarget, ignoreCase = true) ||
            normalizedSource.endsWith(normalizedTarget, ignoreCase = true) ||
            normalizedTarget.endsWith(normalizedSource, ignoreCase = true)
    }

    private suspend fun requestMePayload(
        baseUrl: String,
        authToken: String
    ): String {
        val now = System.currentTimeMillis()
        if (cachedMePayloadBaseUrl == baseUrl &&
            cachedMePayloadToken == authToken &&
            now - cachedMePayloadAtMs <= ME_CACHE_TTL_MS
        ) {
            return cachedMePayload.orEmpty()
        }

        return runCatching {
            mePayloadMutex.withLock {
                val lockedNow = System.currentTimeMillis()
                if (cachedMePayloadBaseUrl == baseUrl &&
                    cachedMePayloadToken == authToken &&
                    lockedNow - cachedMePayloadAtMs <= ME_CACHE_TTL_MS
                ) {
                    return@withLock cachedMePayload.orEmpty()
                }

                val url = buildUrl(baseUrl, "api/me")
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", authHeaderValue(authToken))
                    .get()
                    .build()
                val payload = executeRequestWithRetry(request)
                cachedMePayload = payload
                cachedMePayloadAtMs = lockedNow
                cachedMePayloadBaseUrl = baseUrl
                cachedMePayloadToken = authToken
                payload
            }
        }.getOrThrow()
    }

    private suspend fun invalidateMePayloadCache() {
        mePayloadMutex.withLock {
            cachedMePayload = null
            cachedMePayloadAtMs = 0L
            cachedMePayloadBaseUrl = null
            cachedMePayloadToken = null
        }
    }

    private fun parseNamedEntities(
        rawJson: String,
        arrayKey: String,
        mapper: (JSONObject) -> AudiobookshelfNamedEntityDto?
    ): List<AudiobookshelfNamedEntityDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val sourceArray = root.optJSONArray(arrayKey) ?: JSONArray()

        return buildList {
            for (index in 0 until sourceArray.length()) {
                val obj = sourceArray.optJSONObject(index) ?: continue
                val parsed = mapper(obj) ?: continue
                add(parsed)
            }
        }
    }

    private fun parseNarrators(rawJson: String): List<AudiobookshelfNamedEntityDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return emptyList()

        val root = JSONObject(trimmed)
        val sourceArray = root.optJSONArray("narrators") ?: JSONArray()

        return buildList {
            for (index in 0 until sourceArray.length()) {
                val asString = sourceArray.optString(index)
                if (asString.isNotBlank()) {
                    add(
                        AudiobookshelfNamedEntityDto(
                            id = asString,
                            name = asString
                        )
                    )
                    continue
                }

                val asObject = sourceArray.optJSONObject(index) ?: continue
                val id = asObject.optString("id").ifBlank { asObject.optString("name") }
                val name = asObject.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    AudiobookshelfNamedEntityDto(
                        id = id,
                        name = name,
                        subtitle = asObject.optString("numBooks")
                            .takeIf { it.isNotBlank() }
                            ?.let { "$it books" }
                    )
                )
            }
        }
    }

    private fun parseSearchResponse(rawJson: String): AudiobookshelfSearchDto {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return AudiobookshelfSearchDto(
                authors = emptyList(),
                series = emptyList(),
                narrators = emptyList()
            )
        }
        val root = JSONObject(trimmed)
        val authors = parseEntityArray(root.optJSONArray("authors"), subtitleKey = "numBooks")
        val series = parseEntityArray(root.optJSONArray("series"), subtitleKey = "numBooks")
        val narrators = parseNarratorArray(root.optJSONArray("narrators"))
        return AudiobookshelfSearchDto(
            authors = authors,
            series = series,
            narrators = narrators
        )
    }

    private fun parseEntityArray(
        source: JSONArray?,
        subtitleKey: String? = null
    ): List<AudiobookshelfNamedEntityDto> {
        if (source == null) return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                val subtitle = subtitleKey?.let { key ->
                    item.optString(key).takeIf { it.isNotBlank() }?.let { "$it books" }
                }
                add(
                    AudiobookshelfNamedEntityDto(
                        id = id,
                        name = name,
                        subtitle = subtitle
                    )
                )
            }
        }
    }

    private fun parseNarratorArray(source: JSONArray?): List<AudiobookshelfNamedEntityDto> {
        if (source == null) return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val asString = source.optString(index)
                if (asString.isNotBlank()) {
                    add(AudiobookshelfNamedEntityDto(id = asString, name = asString))
                    continue
                }
                val asObj = source.optJSONObject(index) ?: continue
                val id = asObj.optString("id").ifBlank { asObj.optString("name") }
                val name = asObj.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    AudiobookshelfNamedEntityDto(
                        id = id,
                        name = name,
                        subtitle = asObj.optString("numBooks").takeIf { it.isNotBlank() }?.let { "$it books" }
                    )
                )
            }
        }
    }

    private fun parseSingleMediaProgress(rawJson: String, fallbackItemId: String): AudiobookshelfMediaProgressDto? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val root = JSONObject(trimmed)
        val source = root.optJSONObject("mediaProgress")
            ?: root.optJSONObject("progress")
            ?: root
        val libraryItemId = source.optString("libraryItemId")
            .ifBlank { source.optString("itemId") }
            .ifBlank { fallbackItemId }
        if (libraryItemId.isBlank()) return null

        return AudiobookshelfMediaProgressDto(
            libraryItemId = libraryItemId,
            progressPercent = source.optDoubleOrNull("progress"),
            currentTimeSeconds = source.optDoubleOrNull("currentTime")
                ?: source.optDoubleOrNull("time"),
            durationSeconds = source.optDoubleOrNull("duration")
        )
    }

    private fun parseCollections(rawJson: String): List<AudiobookshelfCollectionDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val source = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("results")
                ?: root.optJSONArray("collections")
                ?: root.optJSONArray("items")
                ?: JSONArray()
        }

        return buildList {
            for (index in 0 until source.length()) {
                val collection = source.optJSONObject(index) ?: continue
                val id = collection.optString("id")
                    .ifBlank { collection.optString("_id") }
                val name = collection.optString("name")
                val libraryId = collection.optString("libraryId").ifBlank { null }
                val itemCount = extractItemCount(collection)
                if (id.isBlank() || name.isBlank()) continue
                add(
                    AudiobookshelfCollectionDto(
                        id = id,
                        name = name,
                        libraryId = libraryId,
                        itemCount = itemCount
                    )
                )
            }
        }
    }

    private fun parseCollectionFromResponse(rawJson: String): AudiobookshelfCollectionDto? {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return null
        val root = JSONObject(trimmed)
        val collection = root.optJSONObject("collection") ?: root
        val id = collection.optString("id").ifBlank { collection.optString("_id") }
        val name = collection.optString("name")
        val libraryId = collection.optString("libraryId").ifBlank { null }
        val itemCount = extractItemCount(collection)
        if (id.isBlank() || name.isBlank()) return null
        return AudiobookshelfCollectionDto(
            id = id,
            name = name,
            libraryId = libraryId,
            itemCount = itemCount
        )
    }

    private fun parsePlaylists(rawJson: String): List<AudiobookshelfPlaylistDto> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val source = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("results")
                ?: root.optJSONArray("playlists")
                ?: root.optJSONArray("items")
                ?: JSONArray()
        }

        return buildList {
            for (index in 0 until source.length()) {
                val playlist = source.optJSONObject(index) ?: continue
                val id = playlist.optString("id")
                    .ifBlank { playlist.optString("_id") }
                val name = playlist.optString("name")
                val libraryId = playlist.optString("libraryId").ifBlank { null }
                val itemCount = extractItemCount(playlist)
                if (id.isBlank() || name.isBlank()) continue
                add(
                    AudiobookshelfPlaylistDto(
                        id = id,
                        name = name,
                        libraryId = libraryId,
                        itemCount = itemCount
                    )
                )
            }
        }
    }

    private fun parsePlaylistFromResponse(rawJson: String): AudiobookshelfPlaylistDto? {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return null
        val root = JSONObject(trimmed)
        val playlist = root.optJSONObject("playlist") ?: root
        val id = playlist.optString("id").ifBlank { playlist.optString("_id") }
        val name = playlist.optString("name")
        val libraryId = playlist.optString("libraryId").ifBlank { null }
        val itemCount = extractItemCount(playlist)
        if (id.isBlank() || name.isBlank()) return null
        return AudiobookshelfPlaylistDto(
            id = id,
            name = name,
            libraryId = libraryId,
            itemCount = itemCount
        )
    }

    private fun extractItemCount(entity: JSONObject): Int? {
        val countByKey = listOf(
            "numBooks",
            "bookCount",
            "numItems",
            "itemCount",
            "numLibraryItems",
            "numLibraryItemIds",
            "numTracks",
            "numEntries",
            "totalBooks",
            "totalItems",
            "totalLibraryItems",
            "totalTracks"
        ).firstNotNullOfOrNull { key ->
            entity.optLongOrNull(key)
                ?.takeIf { value -> value >= 0L }
                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                ?.toInt()
        }
        if (countByKey != null) return countByKey

        val countsFromArrays = listOf(
            "bookIds",
            "libraryItemIds",
            "itemIds",
            "books",
            "libraryItems",
            "items",
            "playlistItems"
        ).mapNotNull { key -> entity.optJSONArray(key)?.length() }
        return countsFromArrays.maxOrNull()
    }

    private fun parseCollectionBookIds(rawJson: String): Set<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return emptySet()
        val root = JSONObject(trimmed)
        val collection = root.optJSONObject("collection") ?: root
        val ids = linkedSetOf<String>()

        fun addId(value: String?) {
            val trimmedId = value?.trim().orEmpty()
            if (trimmedId.isNotBlank()) {
                ids += trimmedId
            }
        }

        fun readIdArray(array: JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                when (val entry = array.opt(index)) {
                    is String -> addId(entry)
                    is JSONObject -> {
                        addId(entry.optString("id").ifBlank { null })
                        addId(entry.optString("_id").ifBlank { null })
                        addId(entry.optString("libraryItemId").ifBlank { null })
                        val nestedBook = entry.optJSONObject("book")
                        if (nestedBook != null) {
                            addId(nestedBook.optString("id").ifBlank { null })
                            addId(nestedBook.optString("_id").ifBlank { null })
                            addId(nestedBook.optString("libraryItemId").ifBlank { null })
                        }
                    }
                }
            }
        }

        readIdArray(collection.optJSONArray("bookIds"))
        readIdArray(collection.optJSONArray("libraryItemIds"))
        readIdArray(collection.optJSONArray("books"))
        readIdArray(collection.optJSONArray("libraryItems"))
        readIdArray(collection.optJSONArray("items"))

        return ids
    }

    private fun parsePlaylistBookIds(rawJson: String): Set<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return emptySet()
        val root = JSONObject(trimmed)
        val playlist = root.optJSONObject("playlist") ?: root
        val ids = linkedSetOf<String>()

        fun addId(value: String?) {
            val trimmedId = value?.trim().orEmpty()
            if (trimmedId.isNotBlank()) {
                ids += trimmedId
            }
        }

        fun readIdArray(array: JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                when (val entry = array.opt(index)) {
                    is String -> addId(entry)
                    is JSONObject -> {
                        addId(entry.optString("id").ifBlank { null })
                        addId(entry.optString("_id").ifBlank { null })
                        addId(entry.optString("libraryItemId").ifBlank { null })
                        addId(entry.optString("itemId").ifBlank { null })
                        addId(entry.optString("bookId").ifBlank { null })

                        fun readNestedObject(obj: JSONObject?) {
                            if (obj == null) return
                            addId(obj.optString("id").ifBlank { null })
                            addId(obj.optString("_id").ifBlank { null })
                            addId(obj.optString("libraryItemId").ifBlank { null })
                            addId(obj.optString("itemId").ifBlank { null })
                            addId(obj.optString("bookId").ifBlank { null })
                        }

                        readNestedObject(entry.optJSONObject("book"))
                        readNestedObject(entry.optJSONObject("libraryItem"))
                        readNestedObject(entry.optJSONObject("item"))
                        readNestedObject(entry.optJSONObject("mediaItem"))
                        readNestedObject(entry.optJSONObject("libraryItems"))
                    }
                }
            }
        }

        readIdArray(playlist.optJSONArray("bookIds"))
        readIdArray(playlist.optJSONArray("libraryItemIds"))
        readIdArray(playlist.optJSONArray("itemIds"))
        readIdArray(playlist.optJSONArray("books"))
        readIdArray(playlist.optJSONArray("libraryItems"))
        readIdArray(playlist.optJSONArray("items"))
        readIdArray(playlist.optJSONArray("playlistItems"))

        return ids
    }

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

    private suspend fun executeRequestWithRetry(request: Request): String {
        var attempt = 0
        var backoffMs = 0L
        while (true) {
            if (backoffMs > 0L) delay(backoffMs)
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val code = response.code
            val retryAfterHeader = response.header("Retry-After")
            response.close()

            if (code == 429 && attempt < MAX_429_RETRIES) {
                val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
                backoffMs = ((retryAfterSeconds ?: (attempt + 1L)) * 1000L)
                    .coerceIn(500L, 10_000L)
                attempt += 1
                continue
            }
            if (code !in 200..299) {
                throw IOException(extractErrorMessage(code, body))
            }
            return body
        }
    }

    private fun parseItemDetail(rawJson: String): AudiobookshelfBookDetailDto {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            throw IOException("Invalid item detail response.")
        }

        val root = JSONObject(trimmed)
        val id = root.optString("id")
        val libraryId = root.optString("libraryId")
        val media = root.optJSONObject("media") ?: throw IOException("Item detail missing media.")
        val metadata = media.optJSONObject("metadata")
        val title = metadata?.optString("title").orEmpty().ifBlank { root.optString("relPath") }
        if (id.isBlank() || libraryId.isBlank() || title.isBlank()) {
            throw IOException("Item detail missing required fields.")
        }

        val chapters = buildList {
            val source = media.optJSONArray("chapters") ?: JSONArray()
            for (index in 0 until source.length()) {
                val chapter = source.optJSONObject(index) ?: continue
                val start = chapter.optDoubleOrNull("start") ?: continue
                add(
                    AudiobookshelfChapterDto(
                        title = chapter.optString("title").ifBlank { "Chapter ${index + 1}" },
                        startSeconds = start,
                        endSeconds = chapter.optDoubleOrNull("end")
                    )
                )
            }
        }

        return AudiobookshelfBookDetailDto(
            id = id,
            libraryId = libraryId,
            title = title,
            authorName = metadata?.optString("authorName").orEmpty().ifBlank { "Unknown Author" },
            narratorName = metadata?.optString("narratorName").orEmpty().ifBlank { null },
            durationSeconds = media.optDoubleOrNull("duration"),
            description = metadata?.optString("description")?.ifBlank { null },
            publishedYear = metadata?.optString("publishedYear")?.ifBlank { null },
            seriesName = metadata?.optString("seriesName")?.ifBlank { null },
            seriesSequence = extractSeriesSequence(metadata),
            genres = metadata.optStringList("genres"),
            sizeBytes = media.optLongOrNull("size") ?: root.optLongOrNull("size"),
            chapters = chapters,
            streamPath = media.optJSONArray("tracks")
                ?.optJSONObject(0)
                ?.optString("contentUrl")
                ?.ifBlank { null }
        )
    }

    private fun extractSeriesSequence(metadata: JSONObject?): Double? {
        metadata ?: return null
        metadata.optDoubleOrNull("seriesSequence")?.let { return it }
        metadata.optDoubleOrNull("sequence")?.let { return it }
        val seriesArray = metadata.optJSONArray("series") ?: return null
        for (index in 0 until seriesArray.length()) {
            val item = seriesArray.optJSONObject(index) ?: continue
            item.optDoubleOrNull("sequence")?.let { return it }
            item.optDoubleOrNull("seriesSequence")?.let { return it }
        }
        return null
    }

    private fun buildUrl(
        baseUrl: String,
        path: String,
        query: Map<String, String> = emptyMap()
    ): String {
        val normalized = baseUrl.removeSuffix("/")
        val baseHttpUrl = normalized.toHttpUrlOrNull()
            ?: throw IOException("Invalid server URL.")
        val builder = baseHttpUrl.newBuilder().addPathSegments(path)
        query.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun extractErrorMessage(code: Int, rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return "Server request failed (HTTP $code)."
        val trimmed = rawJson.trim()

        val parsed = runCatching {
            val root = JSONObject(trimmed)
            root.optString("message").ifBlank {
                root.optString("error")
            }
        }.getOrDefault("")
        if (parsed.isNotBlank()) return "$parsed (HTTP $code)"

        val htmlPreMessage = runCatching {
            val preStart = trimmed.indexOf("<pre>", ignoreCase = true)
            val preEnd = trimmed.indexOf("</pre>", ignoreCase = true)
            if (preStart >= 0 && preEnd > preStart) {
                trimmed.substring(preStart + 5, preEnd).trim()
            } else {
                ""
            }
        }.getOrDefault("")
        if (htmlPreMessage.isNotBlank()) return "$htmlPreMessage (HTTP $code)"

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            val plainMessage = trimmed
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(180)
            if (plainMessage.isNotBlank()) return "$plainMessage (HTTP $code)"
        }

        return "Server request failed (HTTP $code)."
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_429_RETRIES = 3
        const val ME_CACHE_TTL_MS = 2_500L
        const val BOOKMARK_VERIFY_TIME_TOLERANCE_SECONDS = 2.5
        const val BOOKMARK_VERIFY_RECENT_GRACE_MS = 8_000L
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key)) return null
    val value = optDouble(key)
    return if (value.isNaN()) null else value
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key)) return null
    val value = optLong(key)
    return if (value == 0L && !isNull(key) && optString(key).isBlank()) null else value
}

private fun JSONObject?.optStringList(key: String): List<String> {
    if (this == null || !has(key)) return emptyList()
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (!value.isNullOrBlank()) add(value)
        }
    }
}
