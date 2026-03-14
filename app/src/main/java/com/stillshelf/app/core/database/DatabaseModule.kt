package com.stillshelf.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `book_summaries` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `authorName` TEXT NOT NULL,
                    `narratorName` TEXT,
                    `durationSeconds` REAL,
                    `coverUrl` TEXT,
                    `seriesName` TEXT,
                    `seriesNamesJson` TEXT NOT NULL,
                    `seriesIdsJson` TEXT NOT NULL,
                    `seriesSequence` REAL,
                    `genresJson` TEXT NOT NULL,
                    `publishedYear` TEXT,
                    `addedAtMs` INTEGER,
                    `progressPercent` REAL,
                    `currentTimeSeconds` REAL,
                    `isFinished` INTEGER NOT NULL,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `libraryId`, `id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_summaries_serverId_libraryId` ON `book_summaries` (`serverId`, `libraryId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_summaries_serverId_id` ON `book_summaries` (`serverId`, `id`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `book_details` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `description` TEXT,
                    `publishedYear` TEXT,
                    `sizeBytes` INTEGER,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `libraryId`, `bookId`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_details_serverId_libraryId` ON `book_details` (`serverId`, `libraryId`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `book_chapters` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `chapterIndex` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `startSeconds` REAL NOT NULL,
                    `endSeconds` REAL,
                    PRIMARY KEY(`serverId`, `libraryId`, `bookId`, `chapterIndex`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_chapters_serverId_libraryId_bookId` ON `book_chapters` (`serverId`, `libraryId`, `bookId`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `book_bookmarks` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `title` TEXT,
                    `timeSeconds` REAL,
                    `createdAtMs` INTEGER,
                    PRIMARY KEY(`serverId`, `libraryId`, `bookId`, `id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_book_bookmarks_serverId_libraryId_bookId` ON `book_bookmarks` (`serverId`, `libraryId`, `bookId`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `series_summaries` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `subtitle` TEXT,
                    `imageUrl` TEXT,
                    `description` TEXT,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `libraryId`, `id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_series_summaries_serverId_libraryId` ON `series_summaries` (`serverId`, `libraryId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_series_summaries_serverId_name` ON `series_summaries` (`serverId`, `name`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `series_memberships` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `seriesId` TEXT NOT NULL,
                    `collapseSubseries` INTEGER NOT NULL,
                    `stableId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `entryType` TEXT NOT NULL,
                    `bookId` TEXT,
                    `subseriesId` TEXT,
                    `displayName` TEXT,
                    `bookCount` INTEGER,
                    `coverUrl` TEXT,
                    `sequenceLabel` TEXT,
                    `updatedAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `libraryId`, `seriesId`, `collapseSubseries`, `stableId`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_series_memberships_serverId_libraryId_seriesId_collapseSubseries` ON `series_memberships` (`serverId`, `libraryId`, `seriesId`, `collapseSubseries`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `detail_sync_state` (
                    `serverId` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `resourceType` TEXT NOT NULL,
                    `resourceId` TEXT NOT NULL,
                    `resourceVariant` TEXT NOT NULL,
                    `lastSuccessfulSyncAtMs` INTEGER NOT NULL,
                    `lastAttemptedSyncAtMs` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `libraryId`, `resourceType`, `resourceId`, `resourceVariant`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_detail_sync_state_serverId_libraryId_resourceType` ON `detail_sync_state` (`serverId`, `libraryId`, `resourceType`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "stillshelf.db"
    )
        .addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideServerDao(database: AppDatabase): ServerDao = database.serverDao()

    @Provides
    @Singleton
    fun provideLibraryDao(database: AppDatabase): LibraryDao = database.libraryDao()

    @Provides
    @Singleton
    fun provideDetailCacheDao(database: AppDatabase): DetailCacheDao = database.detailCacheDao()
}
