package com.stillshelf.app.data.mapper

import com.stillshelf.app.core.database.LibraryEntity
import com.stillshelf.app.core.database.ServerEntity
import com.stillshelf.app.core.model.Library
import com.stillshelf.app.core.model.Server

fun ServerEntity.toModel(): Server = Server(
    id = id,
    name = name,
    baseUrl = baseUrl,
    createdAt = createdAt
)

fun LibraryEntity.toModel(): Library = Library(
    id = id,
    serverId = serverId,
    name = name
)
