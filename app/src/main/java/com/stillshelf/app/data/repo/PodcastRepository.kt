package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.PlaybackSource
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.model.PodcastShowDetail
import com.stillshelf.app.core.util.AppResult

interface PodcastRepository {
    suspend fun fetchPodcastShows(forceRefresh: Boolean = false): AppResult<List<PodcastShow>>
    suspend fun fetchPodcastShowDetail(showId: String, forceRefresh: Boolean = false): AppResult<PodcastShowDetail>
    suspend fun fetchPodcastEpisodePlaybackSource(showId: String, episodeId: String): AppResult<PlaybackSource>
    suspend fun syncEpisodeProgress(
        showId: String,
        episodeId: String,
        currentTimeSeconds: Double,
        durationSeconds: Double?,
        isFinished: Boolean
    ): AppResult<Unit>
    suspend fun setPodcastLibraryId(serverId: String, libraryId: String?): AppResult<Unit>
    suspend fun fetchLibrariesWithMediaType(): AppResult<List<Library>>
    suspend fun checkForNewEpisodes(showId: String): AppResult<Unit>
}
