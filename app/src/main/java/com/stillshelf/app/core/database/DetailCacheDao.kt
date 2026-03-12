package com.stillshelf.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetailCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookSummary(entity: BookSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookSummaries(entities: List<BookSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookDetail(entity: BookDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookChapters(entities: List<BookChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookBookmarks(entities: List<BookBookmarkEntity>)

    @Query(
        """
        SELECT * FROM book_summaries
        WHERE serverId = :serverId AND libraryId = :libraryId AND id = :bookId
        LIMIT 1
        """
    )
    suspend fun getBookSummary(serverId: String, libraryId: String, bookId: String): BookSummaryEntity?

    @Query(
        """
        SELECT * FROM book_details
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        LIMIT 1
        """
    )
    suspend fun getBookDetail(serverId: String, libraryId: String, bookId: String): BookDetailEntity?

    @Query(
        """
        SELECT * FROM book_chapters
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        ORDER BY chapterIndex ASC
        """
    )
    suspend fun getBookChapters(serverId: String, libraryId: String, bookId: String): List<BookChapterEntity>

    @Query(
        """
        SELECT * FROM book_bookmarks
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        ORDER BY CASE WHEN timeSeconds IS NULL THEN 1 ELSE 0 END, timeSeconds ASC
        """
    )
    suspend fun getBookBookmarks(serverId: String, libraryId: String, bookId: String): List<BookBookmarkEntity>

    @Query(
        """
        SELECT * FROM book_summaries
        WHERE serverId = :serverId AND libraryId = :libraryId AND id = :bookId
        LIMIT 1
        """
    )
    fun observeBookSummary(serverId: String, libraryId: String, bookId: String): Flow<BookSummaryEntity?>

    @Query(
        """
        SELECT * FROM book_details
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        LIMIT 1
        """
    )
    fun observeBookDetail(serverId: String, libraryId: String, bookId: String): Flow<BookDetailEntity?>

    @Query(
        """
        SELECT * FROM book_chapters
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        ORDER BY chapterIndex ASC
        """
    )
    fun observeBookChapters(serverId: String, libraryId: String, bookId: String): Flow<List<BookChapterEntity>>

    @Query(
        """
        SELECT * FROM book_bookmarks
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        ORDER BY CASE WHEN timeSeconds IS NULL THEN 1 ELSE 0 END, timeSeconds ASC
        """
    )
    fun observeBookBookmarks(serverId: String, libraryId: String, bookId: String): Flow<List<BookBookmarkEntity>>

    @Query(
        """
        SELECT * FROM book_summaries
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND seriesName = :seriesName COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun getFirstBookSummaryBySeriesName(
        serverId: String,
        libraryId: String,
        seriesName: String
    ): BookSummaryEntity?

    @Query(
        """
        DELETE FROM book_chapters
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        """
    )
    suspend fun deleteBookChapters(serverId: String, libraryId: String, bookId: String)

    @Query(
        """
        DELETE FROM book_bookmarks
        WHERE serverId = :serverId AND libraryId = :libraryId AND bookId = :bookId
        """
    )
    suspend fun deleteBookBookmarks(serverId: String, libraryId: String, bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesSummaries(entities: List<SeriesSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesMemberships(entities: List<SeriesMembershipEntity>)

    @Query(
        """
        SELECT * FROM series_summaries
        WHERE serverId = :serverId AND libraryId = :libraryId AND id = :seriesId
        LIMIT 1
        """
    )
    suspend fun getSeriesSummary(serverId: String, libraryId: String, seriesId: String): SeriesSummaryEntity?

    @Query(
        """
        SELECT * FROM series_summaries
        WHERE serverId = :serverId AND libraryId = :libraryId AND id = :seriesId
        LIMIT 1
        """
    )
    fun observeSeriesSummary(serverId: String, libraryId: String, seriesId: String): Flow<SeriesSummaryEntity?>

    @Query(
        """
        SELECT * FROM series_summaries
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND name = :seriesName COLLATE NOCASE
        LIMIT 1
        """
    )
    suspend fun getSeriesSummaryByName(
        serverId: String,
        libraryId: String,
        seriesName: String
    ): SeriesSummaryEntity?

    @Query(
        """
        SELECT * FROM series_memberships
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND seriesId = :seriesId
          AND collapseSubseries = :collapseSubseries
        ORDER BY position ASC
        """
    )
    suspend fun getSeriesMemberships(
        serverId: String,
        libraryId: String,
        seriesId: String,
        collapseSubseries: Boolean
    ): List<SeriesMembershipEntity>

    @Query(
        """
        SELECT * FROM series_memberships
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND seriesId = :seriesId
          AND collapseSubseries = :collapseSubseries
        ORDER BY position ASC
        """
    )
    fun observeSeriesMemberships(
        serverId: String,
        libraryId: String,
        seriesId: String,
        collapseSubseries: Boolean
    ): Flow<List<SeriesMembershipEntity>>

    @Query(
        """
        DELETE FROM series_memberships
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND seriesId = :seriesId
          AND collapseSubseries = :collapseSubseries
        """
    )
    suspend fun deleteSeriesMemberships(
        serverId: String,
        libraryId: String,
        seriesId: String,
        collapseSubseries: Boolean
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDetailSyncState(entity: DetailSyncStateEntity)

    @Query(
        """
        SELECT * FROM detail_sync_state
        WHERE serverId = :serverId
          AND libraryId = :libraryId
          AND resourceType = :resourceType
          AND resourceId = :resourceId
          AND resourceVariant = :resourceVariant
        LIMIT 1
        """
    )
    suspend fun getDetailSyncState(
        serverId: String,
        libraryId: String,
        resourceType: String,
        resourceId: String,
        resourceVariant: String
    ): DetailSyncStateEntity?

    @Query("DELETE FROM book_summaries WHERE serverId = :serverId")
    suspend fun deleteBookSummariesForServer(serverId: String)

    @Query("DELETE FROM book_details WHERE serverId = :serverId")
    suspend fun deleteBookDetailsForServer(serverId: String)

    @Query("DELETE FROM book_chapters WHERE serverId = :serverId")
    suspend fun deleteBookChaptersForServer(serverId: String)

    @Query("DELETE FROM book_bookmarks WHERE serverId = :serverId")
    suspend fun deleteBookBookmarksForServer(serverId: String)

    @Query("DELETE FROM series_summaries WHERE serverId = :serverId")
    suspend fun deleteSeriesSummariesForServer(serverId: String)

    @Query("DELETE FROM series_memberships WHERE serverId = :serverId")
    suspend fun deleteSeriesMembershipsForServer(serverId: String)

    @Query("DELETE FROM detail_sync_state WHERE serverId = :serverId")
    suspend fun deleteDetailSyncStateForServer(serverId: String)
}
