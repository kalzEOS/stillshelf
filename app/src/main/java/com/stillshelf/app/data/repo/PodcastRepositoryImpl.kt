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
import com.stillshelf.app.data.api.AudiobookshelfApi
import com.stillshelf.app.data.api.AudiobookshelfPodcastEpisodeDto
import com.stillshelf.app.data.api.AudiobookshelfPodcastShowDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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

    override fun observeEpisodeMutations(): Flow<PodcastEpisodeMutation> = episodeMutations.asSharedFlow()

    private data class ServerCredentials(
        val baseUrl: String,
        val token: String,
        val serverId: String
    )

    private suspend fun resolveCredentials(): AppResult<ServerCredentials> {
        val prefs = sessionPreferences.state.first()
        val serverId = prefs.activeServerId
            ?: return AppResult.Error("No active server selected.")
        val server = serverDao.getById(serverId)
            ?: return AppResult.Error("Server not found.")
        val token = secureTokenStorage.getToken(serverId)
            ?: return AppResult.Error("No saved session for this server. Please log in again.")
        val resolvedStatus = endpointResolver.resolveForServer(server)
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
        return api.getPodcastShows(
            baseUrl = creds.baseUrl,
            authToken = creds.token,
            libraryId = libraryId
        ).fold(
            onSuccess = { dtos ->
                AppResult.Success(dtos.map { it.toModel(creds.baseUrl, creds.token) })
            },
            onFailure = { e ->
                AppResult.Error("Failed to load podcasts: ${e.message}", e)
            }
        )
    }

    override suspend fun fetchPodcastShowDetail(
        showId: String,
        forceRefresh: Boolean
    ): AppResult<PodcastShowDetail> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
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
        return AppResult.Success(PodcastShowDetail(show = show, episodes = allEpisodeDtos.map { it.toModel() }, rssError = rssError))
    }

    override suspend fun fetchPodcastEpisodePlaybackSource(
        showId: String,
        episodeId: String
    ): AppResult<PlaybackSource> {
        val creds = when (val r = resolveCredentials()) {
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
        val creds = when (val r = resolveCredentials()) {
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

        // Prefer enclosureUrl (public CDN) over ABS file URL: CDN sends Content-Length and needs no
        // auth, avoiding the header-stripping issues Android DownloadManager has on redirects.
        val downloadUrl = episodeDto.enclosureUrl
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
        return api.checkForNewPodcastEpisodes(
            baseUrl = creds.baseUrl,
            authToken = creds.token,
            showId = showId
        ).fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { e -> AppResult.Error("Failed to check for new episodes: ${e.message}", e) }
        )
    }


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
            .filter { it.enclosureUrl != null }
            .associateBy { it.enclosureUrl!! }
        return rssEpisodes.map { rssEp ->
            val abs = rssEp.enclosureUrl?.let { absByEnclosureUrl[it] }
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
}
