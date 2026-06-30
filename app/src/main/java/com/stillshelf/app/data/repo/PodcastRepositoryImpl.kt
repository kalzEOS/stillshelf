package com.stillshelf.app.data.repo

import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PlaybackTrack
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.model.PodcastEpisodeMutation
import com.stillshelf.app.core.model.PodcastShowDetail
import com.stillshelf.app.core.network.ActiveServerEndpointResolver
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.util.withCheckpointOverride
import com.stillshelf.app.data.api.AudiobookshelfApi
import com.stillshelf.app.data.api.AudiobookshelfPodcastEpisodeDto
import com.stillshelf.app.data.api.AudiobookshelfPodcastShowDto
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl @Inject constructor(
    private val api: AudiobookshelfApi,
    private val sessionPreferences: SessionPreferences,
    private val secureTokenStorage: SecureTokenStorage,
    private val serverDao: ServerDao,
    private val endpointResolver: ActiveServerEndpointResolver
) : PodcastRepository {

    private val episodeMutations = MutableSharedFlow<PodcastEpisodeMutation>(extraBufferCapacity = 8)

    private data class TimedCache<T>(val value: T, val savedAtMs: Long = System.currentTimeMillis()) {
        fun isFresh(maxAgeMs: Long) = System.currentTimeMillis() - savedAtMs < maxAgeMs
    }

    private companion object {
        const val SHOWS_CACHE_MAX_AGE_MS = 20 * 60 * 1000L
        const val SHOW_DETAIL_CACHE_MAX_AGE_MS = 20 * 60 * 1000L
        const val PODCAST_SHOWS_PAGE_SIZE = 100
        const val PODCAST_SHOWS_MAX_PAGES = 50
    }

    private var showsCache: TimedCache<List<PodcastShow>>? = null
    private var showsCacheKey: String = ""
    private val cacheLock = Any()
    private val showDetailCache = mutableMapOf<String, TimedCache<PodcastShowDetail>>()

    override fun getCachedPodcastShows(serverId: String, libraryId: String): List<PodcastShow>? {
        return synchronized(cacheLock) {
            if (showsCacheKey != "$serverId/$libraryId") return@synchronized null
            showsCache?.value
        }
    }

    override fun getCachedPodcastShowDetail(serverId: String, showId: String): PodcastShowDetail? =
        synchronized(cacheLock) { showDetailCache["$serverId/$showId"]?.value }

    private fun invalidateShowDetailCache(showId: String) {
        synchronized(cacheLock) {
            showDetailCache.keys.removeAll { it.endsWith("/$showId") }
        }
    }

    override fun observeEpisodeMutations(): Flow<PodcastEpisodeMutation> = episodeMutations.asSharedFlow()

    private data class ServerCredentials(
        val baseUrl: String,
        val token: String,
        val serverId: String
    )

    private suspend fun resolveCredentials(forceFreshEndpoint: Boolean = false): AppResult<ServerCredentials> {
        val prefs = sessionPreferences.state.first()
        val serverId = prefs.activeServerId
            ?: return AppResult.Error("No active server selected.")
        val server = serverDao.getById(serverId)
            ?: return AppResult.Error("Server not found.")
        val token = secureTokenStorage.getToken(serverId)
            ?: return AppResult.Error("No saved session for this server. Please log in again.")
        val resolvedStatus = if (forceFreshEndpoint) {
            endpointResolver.resolveFreshForServer(server)
        } else {
            endpointResolver.resolveForServer(server)
        }
        return AppResult.Success(
            ServerCredentials(
                baseUrl = resolvedStatus.effectiveBaseUrl,
                token = token,
                serverId = serverId
            )
        )
    }

    override suspend fun fetchPodcastShows(forceRefresh: Boolean): AppResult<List<PodcastShow>> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        val libraryIds = sessionPreferences.getPodcastLibraryIds().first()
        val libraryId = libraryIds[creds.serverId]
            ?: return AppResult.Error("No podcast library configured. Please select one in Settings → Podcasts.")
        val cacheKey = "${creds.serverId}/$libraryId"
        if (!forceRefresh) {
            synchronized(cacheLock) {
                if (showsCacheKey == cacheKey) {
                    showsCache?.takeIf { it.isFresh(SHOWS_CACHE_MAX_AGE_MS) }?.let {
                        return AppResult.Success(it.value)
                    }
                }
            }
        }
        val allDtos = mutableListOf<AudiobookshelfPodcastShowDto>()
        val seenIds = mutableSetOf<String>()
        var page = 0
        while (page < PODCAST_SHOWS_MAX_PAGES) {
            val pageDtos = api.getPodcastShows(
                baseUrl = creds.baseUrl,
                authToken = creds.token,
                libraryId = libraryId,
                limit = PODCAST_SHOWS_PAGE_SIZE,
                page = page
            ).getOrElse { e ->
                return AppResult.Error("Failed to load podcasts: ${e.message}", e)
            }
            val newDtos = pageDtos.filter { seenIds.add(it.id) }
            allDtos += newDtos
            if (pageDtos.size < PODCAST_SHOWS_PAGE_SIZE || newDtos.isEmpty()) break
            page += 1
        }
        val shows = allDtos.map { it.toModel(creds.baseUrl, creds.token) }
        synchronized(cacheLock) {
            showsCache = TimedCache(shows)
            showsCacheKey = cacheKey
        }
        return AppResult.Success(shows)
    }

    override suspend fun fetchPodcastShowDetail(
        showId: String,
        forceRefresh: Boolean
    ): AppResult<PodcastShowDetail> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        val cacheKey = "${creds.serverId}/$showId"
        if (!forceRefresh) {
            synchronized(cacheLock) { showDetailCache[cacheKey] }
                ?.takeIf { it.isFresh(SHOW_DETAIL_CACHE_MAX_AGE_MS) }
                ?.let { cached ->
                return AppResult.Success(applyLocalCheckpoints(cached.value, creds.serverId))
            }
        }
        val (showDto, absEpisodeDtos) = api.getPodcastShowDetail(creds.baseUrl, creds.token, showId)
            .getOrElse { e -> return AppResult.Error("Failed to load podcast detail: ${e.message}", e) }
        val show = showDto.toModel(creds.baseUrl, creds.token)
        var rssError: String? = null
        val allEpisodeDtos = showDto.feedUrl?.let { feedUrl ->
            val rssResult = api.fetchRssFeedEpisodes(feedUrl, showId)
            val rssEpisodes = rssResult.getOrElse { e ->
                rssError = "Could not load full episode list from feed: ${e.message}"
                emptyList()
            }
            mergeEpisodes(absEpisodeDtos, rssEpisodes)
        } ?: absEpisodeDtos
        val detail = PodcastShowDetail(
            show = show,
            episodes = allEpisodeDtos.map { it.toModel() },
            rssError = rssError
        )
        synchronized(cacheLock) {
            showDetailCache[cacheKey] = TimedCache(detail)
        }
        return AppResult.Success(applyLocalCheckpoints(detail, creds.serverId))
    }

    private suspend fun applyLocalCheckpoints(detail: PodcastShowDetail, serverId: String): PodcastShowDetail {
        val checkpoints = sessionPreferences.getPlaybackCheckpoints()
            .filter { it.serverId == serverId }
            .associateBy { it.bookId }
        if (checkpoints.isEmpty()) return detail
        val showId = detail.show.id
        val episodes = detail.episodes.map { episode ->
            val checkpoint = checkpoints["$showId::${episode.id}"] ?: return@map episode
            episode.withCheckpointOverride(checkpoint)
        }
        return detail.copy(episodes = episodes)
    }

    override suspend fun fetchPodcastEpisodePlaybackSource(
        showId: String,
        episodeId: String
    ): AppResult<PlaybackSource> {
        val creds = when (val r = resolveCredentials(forceFreshEndpoint = true)) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        val (showDto, absEpisodeDtos) = api.getPodcastShowDetail(creds.baseUrl, creds.token, showId)
            .getOrElse { e -> return AppResult.Error("Failed to load episode: ${e.message}", e) }

        // Prefer ABS (downloaded episode has a local stream URL); fall back to RSS feed
        val episodeDto = absEpisodeDtos.firstOrNull { it.id == episodeId }
            ?: showDto.feedUrl?.let { feedUrl ->
                api.fetchRssFeedEpisodes(feedUrl, showId).getOrNull()
                    ?.firstOrNull { it.id == episodeId }
            }
            ?: return AppResult.Error("Episode not found.")

        val streamUrl = when {
            episodeDto.audioUrl != null ->
                api.buildEpisodeStreamUrl(creds.baseUrl, showId, episodeDto.audioUrl, creds.token)
            episodeDto.enclosureUrl != null -> episodeDto.enclosureUrl
            else -> return AppResult.Error("This episode has no playable audio file.")
        }
        val show = showDto.toModel(creds.baseUrl, creds.token)
        val bookSummary = BookSummary(
            id = "${showId}::${episodeId}",
            libraryId = show.libraryId,
            title = episodeDto.title,
            authorName = show.author ?: show.title,
            narratorName = null,
            durationSeconds = episodeDto.durationSeconds,
            coverUrl = show.coverUrl,
            progressPercent = episodeDto.progressPercent,
            currentTimeSeconds = episodeDto.currentTimeSeconds,
            isFinished = episodeDto.isFinished,
            description = episodeDto.description
        )
        return AppResult.Success(
            PlaybackSource(
                book = bookSummary,
                streamUrl = streamUrl,
                tracks = episodeDto.chapters.takeIf { it.isNotEmpty() }?.map { chapter ->
                    PlaybackTrack(
                        startOffsetSeconds = chapter.startSeconds,
                        durationSeconds = chapter.endSeconds?.let { endSeconds ->
                            (endSeconds - chapter.startSeconds).takeIf { duration -> duration > 0.0 }
                        },
                        streamUrl = streamUrl
                    )
                } ?: listOf(
                    PlaybackTrack(
                        startOffsetSeconds = 0.0,
                        durationSeconds = episodeDto.durationSeconds,
                        streamUrl = streamUrl
                    )
                )
            )
        )
    }

    override suspend fun fetchPodcastEpisodeDownloadSource(
        showId: String,
        episodeId: String
    ): AppResult<PlaybackSource> {
        val creds = when (val r = resolveCredentials(forceFreshEndpoint = true)) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        val (showDto, absEpisodeDtos) = api.getPodcastShowDetail(creds.baseUrl, creds.token, showId)
            .getOrElse { e -> return AppResult.Error("Failed to load episode: ${e.message}", e) }

        val episodeDto = absEpisodeDtos.firstOrNull { it.id == episodeId }
            ?: showDto.feedUrl?.let { feedUrl ->
                api.fetchRssFeedEpisodes(feedUrl, showId).getOrNull()
                    ?.firstOrNull { it.id == episodeId }
            }
            ?: return AppResult.Error("Episode not found.")

        // Prefer enclosureUrl (public CDN) over ABS file URL — CDN needs no auth, so the token
        // survives redirects. But podcast feed URLs chain through analytics trackers (5+ hops),
        // which exceeds DownloadManager's redirect limit. Pre-resolve to the final URL first.
        val downloadUrl = episodeDto.enclosureUrl?.let { resolveRedirectUrl(it) }
            ?: episodeDto.audioUrl?.let { ino ->
                api.buildEpisodeStreamUrl(creds.baseUrl, showId, ino, creds.token)
            }
            ?: return AppResult.Error("This episode has no downloadable audio file.")

        val show = showDto.toModel(creds.baseUrl, creds.token)
        val bookSummary = BookSummary(
            id = "${showId}::${episodeId}",
            libraryId = show.libraryId,
            title = episodeDto.title,
            authorName = show.author ?: show.title,
            narratorName = null,
            durationSeconds = episodeDto.durationSeconds,
            coverUrl = show.coverUrl,
            progressPercent = episodeDto.progressPercent,
            currentTimeSeconds = episodeDto.currentTimeSeconds,
            isFinished = episodeDto.isFinished,
            description = episodeDto.description
        )
        return AppResult.Success(
            PlaybackSource(
                book = bookSummary,
                streamUrl = downloadUrl,
                tracks = listOf(
                    PlaybackTrack(
                        startOffsetSeconds = 0.0,
                        durationSeconds = episodeDto.durationSeconds,
                        streamUrl = downloadUrl
                    )
                )
            )
        )
    }

    override suspend fun syncEpisodeProgress(
        showId: String,
        episodeId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        invalidateShowDetailCache(showId)
        return api.updateEpisodeProgress(
            baseUrl = creds.baseUrl,
            authToken = creds.token,
            showId = showId,
            episodeId = episodeId,
            currentTimeSeconds = currentTimeSeconds,
            durationSeconds = durationSeconds,
            isFinished = isFinished
        ).fold(
            onSuccess = {
                episodeMutations.tryEmit(
                    PodcastEpisodeMutation(
                        showId = showId,
                        episodeId = episodeId,
                        isFinished = isFinished,
                        currentTimeSeconds = currentTimeSeconds,
                        durationSeconds = durationSeconds
                    )
                )
                AppResult.Success(Unit)
            },
            onFailure = { e ->
                val msg = e.message.orEmpty()
                if ("(HTTP 404)" in msg) {
                    // Episode not tracked in ABS (RSS-only episode with no ABS ID).
                    // Local state is already updated optimistically; silently emit the
                    // mutation so other screens stay consistent.
                    episodeMutations.tryEmit(
                        PodcastEpisodeMutation(
                            showId = showId,
                            episodeId = episodeId,
                            isFinished = isFinished,
                            currentTimeSeconds = currentTimeSeconds,
                            durationSeconds = durationSeconds
                        )
                    )
                    AppResult.Success(Unit)
                } else {
                    AppResult.Error("Failed to sync episode progress: ${e.message}", e)
                }
            }
        )
    }

    override suspend fun setPodcastLibraryId(serverId: String, libraryId: String?): AppResult<Unit> {
        return runCatching {
            sessionPreferences.setPodcastLibraryId(serverId, libraryId)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { e -> AppResult.Error("Failed to save podcast library: ${e.message}", e) }
        )
    }

    override suspend fun checkForNewEpisodes(showId: String): AppResult<Unit> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        invalidateShowDetailCache(showId)
        return api.checkForNewPodcastEpisodes(
            baseUrl = creds.baseUrl,
            authToken = creds.token,
            showId = showId
        ).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { e -> AppResult.Error("Failed to check for new episodes: ${e.message}", e) }
        )
    }

    override fun isUnsupportedCheckForNewEpisodesError(message: String): Boolean =
        "(HTTP 404)" in message


    override suspend fun fetchLibrariesWithMediaType(): AppResult<List<Library>> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        return api.getLibraries(creds.baseUrl, creds.token).fold(
            onSuccess = { dtos ->
                AppResult.Success(
                    dtos.map { dto ->
                        Library(
                            id = dto.id,
                            serverId = creds.serverId,
                            name = dto.name,
                            mediaType = dto.mediaType
                        )
                    }
                )
            },
            onFailure = { e ->
                AppResult.Error("Failed to load libraries: ${e.message}", e)
            }
        )
    }

    // Follows HTTP redirects for a public (no-auth) CDN URL and returns the final destination.
    // Podcast enclosure URLs chain through analytics trackers (5+ hops), exceeding Android's
    // DownloadManager redirect limit. Resolves via HEAD; falls back to original URL on error.
    private suspend fun resolveRedirectUrl(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            var current = url
            for (attempt in 0 until 15) {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                try {
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location") ?: break
                        current = if (location.startsWith("http")) location
                                  else URL(URL(current), location).toString()
                    } else {
                        break
                    }
                } finally {
                    conn.disconnect()
                }
            }
            current
        }.getOrDefault(url)
    }

    private fun AudiobookshelfPodcastShowDto.toModel(baseUrl: String, token: String): PodcastShow {
        val coverUrl = api.buildCoverUrl(baseUrl, id, token)
        return PodcastShow(
            id = id,
            libraryId = libraryId,
            title = title,
            author = author,
            description = description,
            coverUrl = coverUrl,
            numEpisodes = numEpisodes,
            addedAtMs = addedAtMs
        )
    }

    private fun AudiobookshelfPodcastEpisodeDto.toModel(): PodcastEpisode = PodcastEpisode(
        id = id,
        showId = showId,
        title = title,
        subtitle = subtitle,
        description = description,
        pubDate = pubDate,
        durationSeconds = durationSeconds,
        season = season,
        episode = episode,
        audioUrl = audioUrl,
        enclosureUrl = enclosureUrl,
        progressPercent = progressPercent,
        currentTimeSeconds = currentTimeSeconds,
        isFinished = isFinished,
        chapters = chapters
    )

    private fun mergeEpisodes(
        absEpisodes: List<AudiobookshelfPodcastEpisodeDto>,
        rssEpisodes: List<AudiobookshelfPodcastEpisodeDto>
    ): List<AudiobookshelfPodcastEpisodeDto> {
        if (rssEpisodes.isEmpty()) return absEpisodes
        // Index ABS episodes (downloaded, have progress data) by enclosure URL for O(1) merge
        val absByEnclosureUrl = absEpisodes
            .mapNotNull { episode -> episode.enclosureUrl?.normalizedEpisodeUrl()?.let { it to episode } }
            .toMap()
        return rssEpisodes.map { rssEp ->
            val normalizedRssUrl = rssEp.enclosureUrl?.normalizedEpisodeUrl()
            val abs = normalizedRssUrl?.let { absByEnclosureUrl[it] }
                ?: absEpisodes.firstOrNull { absEp ->
                    absEp.title.equals(rssEp.title, ignoreCase = true) &&
                        hasStrongMetadataMatch(absEp, rssEp)
                }
            if (abs != null) {
                // Downloaded: use ABS ID (needed for progress sync) + overlay any missing RSS metadata
                abs.copy(
                    description = abs.description ?: rssEp.description,
                    pubDate = abs.pubDate ?: rssEp.pubDate,
                    season = abs.season ?: rssEp.season,
                    episode = abs.episode ?: rssEp.episode,
                    durationSeconds = abs.durationSeconds ?: rssEp.durationSeconds,
                    chapters = abs.chapters.ifEmpty { rssEp.chapters }
                )
            } else {
                // Not downloaded: RSS data only, enclosureUrl used for streaming
                rssEp
            }
        }
    }

    private fun String.normalizedEpisodeUrl(): String {
        val parsed = trim().toHttpUrlOrNull() ?: return trim().removeSuffix("/")
        val normalizedPath = parsed.encodedPath.trimEnd('/')
        return parsed.newBuilder()
            .encodedPath(normalizedPath.ifBlank { "/" })
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    private fun durationsCompatible(left: Double?, right: Double?): Boolean {
        if (left == null || right == null) return true
        return kotlin.math.abs(left - right) <= 2.0
    }

    private fun hasStrongMetadataMatch(
        left: AudiobookshelfPodcastEpisodeDto,
        right: AudiobookshelfPodcastEpisodeDto
    ): Boolean {
        val samePubDate = left.pubDate != null && left.pubDate == right.pubDate
        val sameDuration = left.durationSeconds != null &&
            right.durationSeconds != null &&
            durationsCompatible(left.durationSeconds, right.durationSeconds)
        return samePubDate || sameDuration
    }
}
