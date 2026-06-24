package com.stillshelf.app.data.repo

import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.model.PodcastShowDetail
import com.stillshelf.app.core.util.AppResult

interface PodcastRepository {
    suspend fun fetchPodcastShows(forceRefresh: Boolean = false): AppResult<List<PodcastShow>>
    suspend fun fetchPodcastShowDetail(showId: String, forceRefresh: Boolean = false): AppResult<PodcastShowDetail>
    suspend fun setPodcastLibraryId(serverId: String, libraryId: String?): AppResult<Unit>
    suspend fun fetchLibrariesWithMediaType(): AppResult<List<com.stillshelf.app.core.model.Library>>
}
