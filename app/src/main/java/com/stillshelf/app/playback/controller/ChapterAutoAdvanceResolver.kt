package com.stillshelf.app.playback.controller

import com.stillshelf.app.core.model.BookChapter
import kotlin.math.abs

internal object ChapterAutoAdvanceResolver {
    private const val AUTO_ADVANCE_BOUNDARY_TOLERANCE_MS = 2_000L

    fun resolveNextChapterStartMs(
        chapters: List<BookChapter>,
        finishedStreamDurationMs: Long,
        bookDurationMs: Long?
    ): Long? {
        val orderedChapters = chapters
            .asSequence()
            .map { chapter ->
                ResolvedChapter(
                    startMs = (chapter.startSeconds * 1000.0).toLong().coerceAtLeast(0L),
                    endMs = chapter.endSeconds
                        ?.let { endSeconds -> (endSeconds * 1000.0).toLong().coerceAtLeast(0L) }
                )
            }
            .distinctBy { chapter -> chapter.startMs }
            .sortedBy { chapter -> chapter.startMs }
            .toList()
        if (orderedChapters.size < 2) return null

        val safeFinishedDurationMs = finishedStreamDurationMs.coerceAtLeast(0L)
        val fallbackBookDurationMs = bookDurationMs
            ?.takeIf { durationMs -> durationMs > 0L }
            ?: safeFinishedDurationMs

        val matchedChapterIndex = orderedChapters.indices
            .mapNotNull { index ->
                val boundaryMs = resolveChapterBoundaryMs(
                    chapters = orderedChapters,
                    index = index,
                    fallbackBookDurationMs = fallbackBookDurationMs
                ) ?: return@mapNotNull null
                val driftMs = abs(boundaryMs - safeFinishedDurationMs)
                if (driftMs > AUTO_ADVANCE_BOUNDARY_TOLERANCE_MS) {
                    null
                } else {
                    index to driftMs
                }
            }
            .minByOrNull { (_, driftMs) -> driftMs }
            ?.first
            ?: return null

        return orderedChapters.getOrNull(matchedChapterIndex + 1)?.startMs
    }

    private fun resolveChapterBoundaryMs(
        chapters: List<ResolvedChapter>,
        index: Int,
        fallbackBookDurationMs: Long
    ): Long? {
        val chapter = chapters.getOrNull(index) ?: return null
        val nextChapterStartMs = chapters.getOrNull(index + 1)?.startMs
        return chapter.endMs
            ?: nextChapterStartMs
            ?: fallbackBookDurationMs.takeIf { durationMs -> durationMs >= chapter.startMs }
    }

    private data class ResolvedChapter(
        val startMs: Long,
        val endMs: Long?
    )
}
