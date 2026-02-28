package com.stillshelf.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(library: LibraryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(libraries: List<LibraryEntity>)

    @Query("SELECT * FROM libraries WHERE serverId = :serverId ORDER BY name ASC")
    fun observeLibraries(serverId: String): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getLibraries(serverId: String): List<LibraryEntity>

    @Query("DELETE FROM libraries WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("SELECT * FROM libraries WHERE id = :libraryId LIMIT 1")
    suspend fun getById(libraryId: String): LibraryEntity?
}
