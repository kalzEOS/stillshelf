package com.stillshelf.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "libraries",
    primaryKeys = ["serverId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["serverId"]), Index(value = ["id"])]
)
data class LibraryEntity(
    val id: String,
    val serverId: String,
    val name: String
)
