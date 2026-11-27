package com.stillshelf.app.core.model

enum class NavidromeContinuousPlaybackMode(val key: String, val label: String) {
    SAME_ARTIST("same_artist", "Same artist"),
    SAME_GENRE("same_genre", "Same genre"),
    SIMILAR_ARTISTS("similar_artists", "Similar artists"),
    RANDOM("random", "Random tracks from library");

    companion object {
        fun fromKey(key: String?): NavidromeContinuousPlaybackMode =
            entries.firstOrNull { it.key == key } ?: SAME_ARTIST
    }
}
