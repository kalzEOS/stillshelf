package com.stillshelf.app.core.model

data class Server(
    val id: String,
    val name: String,
    val baseUrl: String,
    val createdAt: Long
)

enum class ServerConnectionMode {
    Auto,
    Local,
    Remote
}

enum class ServerConnectionRoute {
    Default,
    Local,
    Remote
}

data class ServerEndpointSwitchingConfig(
    val enabled: Boolean = false,
    val lanBaseUrl: String? = null,
    val wanBaseUrl: String? = null,
    val connectionMode: ServerConnectionMode = ServerConnectionMode.Auto
)

data class ActiveServerConnectionStatus(
    val serverId: String,
    val effectiveBaseUrl: String,
    val route: ServerConnectionRoute,
    val connectionMode: ServerConnectionMode,
    val switchingEnabled: Boolean,
    val lanFallbackToRemote: Boolean = false,
    val lanBaseUrl: String? = null,
    val wanBaseUrl: String? = null
)

enum class EndpointReachabilityStatus {
    Checking,
    Reachable,
    Unavailable
}

data class ActiveEndpointHealth(
    val serverId: String,
    val endpointUrl: String,
    val reachabilityStatus: EndpointReachabilityStatus,
    val latencyMs: Long? = null,
    val checkedAtMs: Long = System.currentTimeMillis()
)

data class ActiveServerDataState(
    val serverId: String,
    val isStale: Boolean,
    val message: String? = null,
    val staleSinceMs: Long? = null
)

data class Library(
    val id: String,
    val serverId: String,
    val name: String,
    val mediaType: String? = null
) {
    val isPodcastLibrary: Boolean get() = mediaType?.lowercase() == "podcast"
}

data class BookSummary(
    val id: String,
    val libraryId: String,
    val title: String,
    val authorName: String,
    val narratorName: String?,
    val narratorNames: List<String> = emptyList(),
    val durationSeconds: Double?,
    val coverUrl: String?,
    val seriesName: String? = null,
    val seriesNames: List<String> = emptyList(),
    val seriesIds: List<String> = emptyList(),
    val seriesSequence: Double? = null,
    val genres: List<String> = emptyList(),
    val publishedYear: String? = null,
    val addedAtMs: Long? = null,
    val authorIds: List<String> = emptyList(),
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val isFinished: Boolean = false,
    val description: String? = null
)

data class NamedEntitySummary(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val description: String? = null
)

data class ContinueListeningItem(
    val book: BookSummary,
    val progressPercent: Double?,
    val currentTimeSeconds: Double?
)

data class BookChapter(
    val title: String,
    val startSeconds: Double,
    val endSeconds: Double?
)

data class BookBookmark(
    val id: String,
    val libraryItemId: String,
    val title: String?,
    val timeSeconds: Double?,
    val createdAtMs: Long? = null
)

data class BookmarkEntry(
    val book: BookSummary,
    val bookmark: BookBookmark
)

data class BookDetail(
    val book: BookSummary,
    val description: String?,
    val publishedYear: String?,
    val sizeBytes: Long?,
    val chapters: List<BookChapter>,
    val bookmarks: List<BookBookmark>
)

data class HomeFeed(
    val libraryName: String,
    val continueListening: List<ContinueListeningItem>,
    val recentlyAdded: List<BookSummary>,
    val listenAgain: List<BookSummary> = emptyList(),
    val recentSeries: List<SeriesStackSummary> = emptyList(),
    val authorImageUrls: Map<String, String> = emptyMap()
)

data class SeriesStackSummary(
    val seriesName: String,
    val leadBook: BookSummary,
    val count: Int,
    val coverUrls: List<String> = emptyList()
)

sealed interface SeriesDetailEntry {
    val stableId: String

    data class BookItem(
        val book: BookSummary
    ) : SeriesDetailEntry {
        override val stableId: String = "book:${book.id}"
    }

    data class SubseriesItem(
        val id: String,
        val name: String,
        val bookCount: Int,
        val coverUrl: String?,
        val sequenceLabel: String? = null
    ) : SeriesDetailEntry {
        override val stableId: String = "series:$id"
    }
}

data class PodcastShow(
    val id: String,
    val libraryId: String,
    val title: String,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val numEpisodes: Int,
    val addedAtMs: Long?
)

data class PodcastEpisode(
    val id: String,
    val showId: String,
    val title: String,
    val subtitle: String? = null,
    val description: String?,
    val pubDate: String?,
    val durationSeconds: Double?,
    val season: String?,
    val episode: String?,
    val audioUrl: String?,
    val enclosureUrl: String? = null,
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val isFinished: Boolean = false,
    val chapters: List<BookChapter> = emptyList()
)

data class PodcastShowDetail(
    val show: PodcastShow,
    val episodes: List<PodcastEpisode>,
    val rssError: String? = null
)

data class PodcastEpisodeMutation(
    val showId: String,
    val episodeId: String,
    val isFinished: Boolean,
    val currentTimeSeconds: Double,
    val durationSeconds: Double?
)

data class SearchResults(
    val books: List<BookSummary>,
    val authors: List<NamedEntitySummary>,
    val series: List<NamedEntitySummary>,
    val narrators: List<NamedEntitySummary>
)

data class PlaybackTrack(
    val startOffsetSeconds: Double,
    val durationSeconds: Double?,
    val streamUrl: String
)

data class PlaybackSource(
    val book: BookSummary,
    val streamUrl: String,
    val tracks: List<PlaybackTrack> = emptyList()
)

data class PlaybackProgress(
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val durationSeconds: Double?,
    val updatedAtMs: Long? = null
)

data class BookProgressMutation(
    val bookId: String,
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val durationSeconds: Double?,
    val isFinished: Boolean
)

data class RealtimeInvalidation(
    val serverId: String,
    val receivedAtMs: Long = System.currentTimeMillis()
)

data class SessionState(
    val activeServerId: String?,
    val activeLibraryId: String?,
    val requiresLibrarySelection: Boolean = false
)
