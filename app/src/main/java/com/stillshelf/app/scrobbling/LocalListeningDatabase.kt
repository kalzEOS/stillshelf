package com.stillshelf.app.scrobbling

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Metadata snapshots have no foreign keys: removing a server or track must not erase history. */
@Entity(tableName = "local_listens", indices = [Index("startedAtSeconds"), Index("artistKey"), Index("albumKey"), Index("trackKey")])
data class LocalListen(
    @PrimaryKey val id: String,
    val libraryKey: String,
    val trackId: String,
    val artistId: String?,
    val albumId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val recordingMbid: String?,
    val durationSeconds: Int?,
    val startedAtSeconds: Long,
    val listenedMs: Long,
    val artistKey: String,
    val albumKey: String,
    val trackKey: String,
    val positionMs: Long? = null,
    @ColumnInfo(defaultValue = "0") val ended: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'") val genresJson: String = "[]",
    val releaseYear: Int? = null
)

@Entity(tableName = "local_listen_genres", primaryKeys = ["listenId", "genre"],
    foreignKeys = [ForeignKey(entity = LocalListen::class, parentColumns = ["id"], childColumns = ["listenId"], onDelete = ForeignKey.CASCADE)])
data class LocalListenGenre(val listenId: String, val genre: String)

data class LocalInsightAggregate(val label: String, val detail: String, val bucket: Int, val count: Long, val identity: String)

data class LocalListenRank(@Embedded val listen: LocalListen, val plays: Long)
data class LocalListenSummary(val count: Long, val milliseconds: Long, val first: Long?)
data class LocalListenDay(val date: String, val count: Long, val milliseconds: Long)

@Dao
interface LocalListeningDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(listen: LocalListen)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun genres(values: List<LocalListenGenre>)

    @RawQuery suspend fun insights(query: SupportSQLiteQuery): List<LocalInsightAggregate>

    @Query("UPDATE local_listens SET listenedMs = MAX(listenedMs, :milliseconds), positionMs = :position, ended = MAX(ended, :ended) WHERE id = :id")
    suspend fun duration(id: String, milliseconds: Long, position: Long?, ended: Boolean)

    @Query("SELECT * FROM local_listens WHERE libraryKey = :library AND trackId = :track ORDER BY rowid DESC LIMIT 1")
    suspend fun latest(library: String, track: String): LocalListen?

    @Query("SELECT COUNT(*) + COALESCE(SUM(listenedMs), 0) FROM local_listens")
    fun changes(): Flow<Long>

    @Query("SELECT COUNT(*) + COALESCE(MAX(rowid), 0) FROM local_listens")
    fun insightChanges(): Flow<Long>

    @Query("SELECT COUNT(*) AS `count`, COALESCE(SUM(listenedMs), 0) AS `milliseconds`, MIN(startedAtSeconds) AS `first` FROM local_listens")
    suspend fun summary(): LocalListenSummary

    @Query("SELECT * FROM local_listens ORDER BY startedAtSeconds DESC, id DESC LIMIT 100")
    suspend fun recent(): List<LocalListen>

    @RawQuery
    suspend fun rankings(query: SupportSQLiteQuery): List<LocalListenRank>

    @Query("SELECT strftime('%Y-%m-%d', startedAtSeconds, 'unixepoch', 'localtime') AS date, COUNT(*) AS count, SUM(listenedMs) AS milliseconds FROM local_listens WHERE startedAtSeconds >= :from AND startedAtSeconds < :until GROUP BY date ORDER BY date")
    suspend fun days(from: Long, until: Long): List<LocalListenDay>

    @Query("SELECT CAST(strftime('%H', startedAtSeconds, 'unixepoch', 'localtime') AS INTEGER) FROM local_listens WHERE startedAtSeconds >= :from AND startedAtSeconds < :until GROUP BY strftime('%H', startedAtSeconds, 'unixepoch', 'localtime') ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun activeHour(from: Long, until: Long): Int?

    @Query("DELETE FROM local_listens")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(rowid), 0) FROM local_listens")
    suspend fun lastRow(): Long

    @Query("SELECT * FROM local_listens WHERE rowid > :after AND rowid <= :last ORDER BY rowid LIMIT 500")
    suspend fun exportPage(after: Long, last: Long): List<LocalListen>

    @Query("SELECT rowid FROM local_listens WHERE id = :id")
    suspend fun rowNumber(id: String): Long
}

@Database(entities = [LocalListen::class, LocalListenGenre::class], version = 3, exportSchema = false)
abstract class LocalListeningDatabase : RoomDatabase() {
    abstract fun listens(): LocalListeningDao
}

@Module
@InstallIn(SingletonComponent::class)
object LocalListeningModule {
    internal val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_listens ADD COLUMN genresJson TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE local_listens ADD COLUMN releaseYear INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS local_listen_genres (listenId TEXT NOT NULL, genre TEXT NOT NULL, PRIMARY KEY(listenId, genre), FOREIGN KEY(listenId) REFERENCES local_listens(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        }
    }
    internal val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_listens ADD COLUMN positionMs INTEGER")
            db.execSQL("ALTER TABLE local_listens ADD COLUMN ended INTEGER NOT NULL DEFAULT 0")
        }
    }
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): LocalListeningDatabase = Room.databaseBuilder(
        context, LocalListeningDatabase::class.java, File(context.noBackupFilesDir, "local-listening.db").absolutePath
    ).addMigrations(migration1To2, migration2To3).build()

    @Provides fun dao(database: LocalListeningDatabase): LocalListeningDao = database.listens()
}
