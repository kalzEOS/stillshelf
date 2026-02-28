package com.stillshelf.app.core.model

data class Server(
    val id: String,
    val name: String,
    val baseUrl: String,
    val createdAt: Long
)

data class Library(
    val id: String,
    val serverId: String,
    val name: String
)

data class BookSummary(
    val id: String,
    val libraryId: String,
    val title: String,
    val authorName: String,
    val narratorName: String?,
    val durationSeconds: Double?,
    val coverUrl: String?,
    val seriesName: String? = null,
    val seriesSequence: Double? = null,
    val genres: List<String> = emptyList(),
    val publishedYear: String? = null,
    val addedAtMs: Long? = null,
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val isFinished: Boolean = false
)

data class NamedEntitySummary(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val imageUrl: String? = null
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
    val timeSeconds: Double?
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
    val recentSeries: List<SeriesStackSummary> = emptyList(),
    val authorImageUrls: Map<String, String> = emptyMap()
)

data class SeriesStackSummary(
    val seriesName: String,
    val leadBook: BookSummary,
    val count: Int
)

data class SearchResults(
    val books: List<BookSummary>,
    val authors: List<NamedEntitySummary>,
    val series: List<NamedEntitySummary>,
    val narrators: List<NamedEntitySummary>
)

data class PlaybackSource(
    val book: BookSummary,
    val streamUrl: String
)

data class PlaybackProgress(
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val durationSeconds: Double?
)

data class SessionState(
    val activeServerId: String?,
    val activeLibraryId: String?
)
