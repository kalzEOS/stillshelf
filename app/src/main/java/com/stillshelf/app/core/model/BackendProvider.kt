package com.stillshelf.app.core.model

enum class BackendProvider(
    val storageValue: String,
    val displayName: String
) {
    AUDIOBOOKSHELF(
        storageValue = "audiobookshelf",
        displayName = "Audiobookshelf"
    ),
    NAVIDROME(
        storageValue = "navidrome",
        displayName = "Navidrome"
    );

    companion object {
        fun fromStorageValue(value: String?): BackendProvider? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}
