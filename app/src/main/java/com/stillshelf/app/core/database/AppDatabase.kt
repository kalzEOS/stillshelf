package com.stillshelf.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServerEntity::class,
        LibraryEntity::class,
        BookSummaryEntity::class,
        BookDetailEntity::class,
        BookChapterEntity::class,
        BookBookmarkEntity::class,
        SeriesSummaryEntity::class,
        SeriesMembershipEntity::class,
        DetailSyncStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun libraryDao(): LibraryDao
    abstract fun detailCacheDao(): DetailCacheDao
}
