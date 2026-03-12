package com.stillshelf.app.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "book_summaries",
    primaryKeys = ["serverId", "libraryId", "id"],
    indices = [
        Index(value = ["serverId", "libraryId"]),
        Index(value = ["serverId", "id"])
    ]
)
data class BookSummaryEntity(
    val serverId: String,
    val libraryId: String,
    val id: String,
    val title: String,
    val authorName: String,
    val narratorName: String?,
    val durationSeconds: Double?,
    val coverUrl: String?,
    val seriesName: String?,
    val seriesNamesJson: String,
    val seriesIdsJson: String,
    val seriesSequence: Double?,
    val genresJson: String,
    val publishedYear: String?,
    val addedAtMs: Long?,
    val progressPercent: Double?,
    val currentTimeSeconds: Double?,
    val isFinished: Boolean,
    val updatedAtMs: Long
)

@Entity(
    tableName = "book_details",
    primaryKeys = ["serverId", "libraryId", "bookId"],
    indices = [Index(value = ["serverId", "libraryId"])]
)
data class BookDetailEntity(
    val serverId: String,
    val libraryId: String,
    val bookId: String,
    val description: String?,
    val publishedYear: String?,
    val sizeBytes: Long?,
    val updatedAtMs: Long
)

@Entity(
    tableName = "book_chapters",
    primaryKeys = ["serverId", "libraryId", "bookId", "chapterIndex"],
    indices = [Index(value = ["serverId", "libraryId", "bookId"])]
)
data class BookChapterEntity(
    val serverId: String,
    val libraryId: String,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val startSeconds: Double,
    val endSeconds: Double?
)

@Entity(
    tableName = "book_bookmarks",
    primaryKeys = ["serverId", "libraryId", "bookId", "id"],
    indices = [Index(value = ["serverId", "libraryId", "bookId"])]
)
data class BookBookmarkEntity(
    val serverId: String,
    val libraryId: String,
    val bookId: String,
    val id: String,
    val title: String?,
    val timeSeconds: Double?,
    val createdAtMs: Long?
)

@Entity(
    tableName = "series_summaries",
    primaryKeys = ["serverId", "libraryId", "id"],
    indices = [
        Index(value = ["serverId", "libraryId"]),
        Index(value = ["serverId", "name"])
    ]
)
data class SeriesSummaryEntity(
    val serverId: String,
    val libraryId: String,
    val id: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    val description: String?,
    val updatedAtMs: Long
)

@Entity(
    tableName = "series_memberships",
    primaryKeys = ["serverId", "libraryId", "seriesId", "collapseSubseries", "stableId"],
    indices = [Index(value = ["serverId", "libraryId", "seriesId", "collapseSubseries"])]
)
data class SeriesMembershipEntity(
    val serverId: String,
    val libraryId: String,
    val seriesId: String,
    val collapseSubseries: Boolean,
    val stableId: String,
    val position: Int,
    val entryType: String,
    val bookId: String?,
    val subseriesId: String?,
    val displayName: String?,
    val bookCount: Int?,
    val coverUrl: String?,
    val sequenceLabel: String?,
    val updatedAtMs: Long
)

@Entity(
    tableName = "detail_sync_state",
    primaryKeys = ["serverId", "libraryId", "resourceType", "resourceId", "resourceVariant"],
    indices = [Index(value = ["serverId", "libraryId", "resourceType"])]
)
data class DetailSyncStateEntity(
    val serverId: String,
    val libraryId: String,
    val resourceType: String,
    val resourceId: String,
    val resourceVariant: String,
    val lastSuccessfulSyncAtMs: Long,
    val lastAttemptedSyncAtMs: Long
)
