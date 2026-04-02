package com.stillshelf.app.playback.controller

private const val CHAPTER_INDEX_SEEK_TOLERANCE_MS = 200L

internal fun resolveCurrentChapterIndex(chapterStartsMs: List<Long>, positionMs: Long): Int {
    if (chapterStartsMs.isEmpty()) return 0
    val seekPositionMs = positionMs.coerceAtLeast(0L) + CHAPTER_INDEX_SEEK_TOLERANCE_MS
    return chapterStartsMs.indexOfLast { startMs -> startMs <= seekPositionMs }
        .takeIf { index -> index >= 0 }
        ?: 0
}
