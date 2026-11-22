package com.stillshelf.app.absstats

import android.content.Context
import androidx.room.*
import java.io.File

@Entity(tableName = "observations", primaryKeys = ["owner", "id"])
internal data class AbsLocalEntry(val owner: String, val id: String, val json: String)
@Entity(tableName = "migrations")
internal data class AbsLocalMigration(@PrimaryKey val owner: String)
@Dao
internal interface AbsLocalDao {
    @Query("SELECT * FROM observations WHERE owner = :owner") suspend fun all(owner: String): List<AbsLocalEntry>
    @Query("SELECT * FROM observations WHERE owner = :owner AND id = :id") suspend fun one(owner: String, id: String): AbsLocalEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: AbsLocalEntry)
    @Query("DELETE FROM observations WHERE owner = :owner") suspend fun clear(owner: String)
    @Query("SELECT COUNT(*) FROM migrations WHERE owner = :owner") suspend fun migrated(owner: String): Int
    @Insert suspend fun mark(migration: AbsLocalMigration)
}
@Database(entities = [AbsLocalEntry::class, AbsLocalMigration::class], version = 1, exportSchema = false)
internal abstract class AbsLocalDatabase : RoomDatabase() {
    abstract fun records(): AbsLocalDao
    companion object {
        private val instances = mutableMapOf<String, AbsLocalDatabase>()
        @Synchronized fun get(context: Context): AbsLocalDatabase {
            val path = File(context.noBackupFilesDir, "abs-local-history.db").absolutePath
            return instances.getOrPut(path) { Room.databaseBuilder(context.applicationContext, AbsLocalDatabase::class.java, path).build() }
        }
    }
}
