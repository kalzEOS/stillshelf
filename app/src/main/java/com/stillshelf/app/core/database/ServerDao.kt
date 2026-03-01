package com.stillshelf.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    fun observeServers(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: ServerEntity)

    @Query("SELECT * FROM servers WHERE id = :serverId LIMIT 1")
    suspend fun getById(serverId: String): ServerEntity?

    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    suspend fun getAll(): List<ServerEntity>

    @Query("UPDATE servers SET name = :name, baseUrl = :baseUrl WHERE id = :serverId")
    suspend fun update(serverId: String, name: String, baseUrl: String): Int

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteById(serverId: String)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()
}
