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
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null,
    val isFinished: Boolean = false
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
