package com.stillshelf.app.data.api

import java.io.IOException
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
    val imagePath: String? = null
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
    val timeSeconds: Double?
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
    val libraryId: String? = null
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
        val body = JSONObject()
            .put("name", name)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val libraryRequest = Request.Builder()
            .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
            .header("Authorization", authHeaderValue(authToken))
            .post(body)
            .build()

        runCatching {
            val libraryResponse = runCatching { executeRequestWithRetry(libraryRequest) }.getOrNull()
            val parsedLibrary = libraryResponse?.let(::parseCollectionFromResponse)
            if (parsedLibrary != null) return@runCatching parsedLibrary

            val fallbackBody = JSONObject()
                .put("name", name)
                .put("libraryId", libraryId)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val fallbackRequest = Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(fallbackBody)
                .build()
            parseCollectionFromResponse(executeRequestWithRetry(fallbackRequest))
                ?: throw IOException("Collection created but no id returned.")
        }
    }

    suspend fun createCollectionWithBook(
        baseUrl: String,
        authToken: String,
        libraryId: String,
        name: String,
        bookId: String
    ): Result<AudiobookshelfCollectionDto> = withContext(Dispatchers.IO) {
        val booksArray = JSONArray().put(bookId)
        val libraryBody = JSONObject()
            .put("name", name)
            .put("books", booksArray)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val globalBody = JSONObject()
            .put("name", name)
            .put("libraryId", libraryId)
            .put("books", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val attempts = listOf(
            Request.Builder()
                .url(buildUrl(baseUrl, "api/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(globalBody)
                .build(),
            Request.Builder()
                .url(buildUrl(baseUrl, "api/libraries/$libraryId/collections"))
                .header("Authorization", authHeaderValue(authToken))
                .post(libraryBody)
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
            throw IOException("Unable to create collection with initial book.", lastError)
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
        val idsBody = JSONObject()
            .put("bookIds", JSONArray().put(bookId))
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val booksBody = JSONObject()
            .put("books", JSONArray().put(bookId))
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
                .url(buildUrl(baseUrl, "api/collections/$collectionId/batch/add"))
                .header("Authorization", authHeaderValue(authToken))
                .post(booksBody)
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
                .post(booksBody)
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
                .build()
        )

        runCatching {
            var lastError: Throwable? = null
            val failedUrls = mutableListOf<String>()
            attempts.forEach { request ->
                val attempt = runCatching { executeRequestWithRetry(request) }
                if (attempt.isSuccess) return@runCatching Unit
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
                            ?: item.optDoubleOrNull("currentTime")
                    )
                )
            }
        }
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
                if (id.isBlank() || name.isBlank()) continue
                add(AudiobookshelfCollectionDto(id = id, name = name, libraryId = libraryId))
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
        if (id.isBlank() || name.isBlank()) return null
        return AudiobookshelfCollectionDto(id = id, name = name, libraryId = libraryId)
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
