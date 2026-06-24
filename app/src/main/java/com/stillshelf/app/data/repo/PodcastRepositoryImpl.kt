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
import com.stillshelf.app.core.model.PodcastShowDetail
import com.stillshelf.app.core.network.ActiveServerEndpointResolver
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.api.AudiobookshelfApi
import com.stillshelf.app.data.api.AudiobookshelfPodcastEpisodeDto
import com.stillshelf.app.data.api.AudiobookshelfPodcastShowDto
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
        return api.getPodcastShowDetail(
            baseUrl = creds.baseUrl,
            authToken = creds.token,
            showId = showId
        ).fold(
            onSuccess = { (showDto, episodeDtos) ->
                val show = showDto.toModel(creds.baseUrl, creds.token)
                val episodes = episodeDtos.map { it.toModel() }
                AppResult.Success(PodcastShowDetail(show = show, episodes = episodes))
            },
            onFailure = { e ->
                AppResult.Error("Failed to load podcast detail: ${e.message}", e)
            }
        )
    }

    override suspend fun fetchPodcastEpisodePlaybackSource(
        showId: String,
        episodeId: String
    ): AppResult<PlaybackSource> {
        val creds = when (val r = resolveCredentials()) {
            is AppResult.Success -> r.value
            is AppResult.Error -> return r
        }
        return api.getPodcastShowDetail(creds.baseUrl, creds.token, showId).fold(
            onSuccess = { (showDto, episodeDtos) ->
                val episodeDto = episodeDtos.firstOrNull { it.id == episodeId }
                    ?: return AppResult.Error("Episode not found.")
                val audioFileIno = episodeDto.audioUrl
                    ?: return AppResult.Error("This episode has no playable audio file.")
                val show = showDto.toModel(creds.baseUrl, creds.token)
                val streamUrl = api.buildEpisodeStreamUrl(creds.baseUrl, showId, audioFileIno, creds.token)
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
                    isFinished = episodeDto.isFinished
                )
                AppResult.Success(
                    PlaybackSource(
                        book = bookSummary,
                        streamUrl = streamUrl,
                        tracks = listOf(
                            PlaybackTrack(
                                startOffsetSeconds = 0.0,
                                durationSeconds = episodeDto.durationSeconds,
                                streamUrl = streamUrl
                            )
                        )
                    )
                )
            },
            onFailure = { e ->
                AppResult.Error("Failed to load episode: ${e.message}", e)
            }
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
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { e -> AppResult.Error("Failed to sync episode progress: ${e.message}", e) }
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
        description = description,
        pubDate = pubDate,
        durationSeconds = durationSeconds,
        season = season,
        episode = episode,
        audioUrl = audioUrl,
        progressPercent = progressPercent,
        currentTimeSeconds = currentTimeSeconds,
        isFinished = isFinished
    )
}
