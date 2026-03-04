package com.stillshelf.app.domain.usecase

import com.stillshelf.app.core.model.BookChapter
import java.util.Locale

enum class SkipIntroOutroFailureReason {
    NoChapters,
    NoMarkerInCurrentChapter,
    MissingDuration
}

data class SkipIntroOutroResult(
    val targetSeconds: Double? = null,
    val failureReason: SkipIntroOutroFailureReason? = null
)

object SkipIntroOutroUseCase {
    fun resolve(
        chapters: List<BookChapter>,
        currentPositionSeconds: Double,
        bookDurationSeconds: Double?
    ): SkipIntroOutroResult {
        val orderedChapters = chapters
            .filter { chapter -> chapter.startSeconds >= 0.0 }
            .sortedBy { chapter -> chapter.startSeconds }
        if (orderedChapters.isEmpty()) {
            return SkipIntroOutroResult(failureReason = SkipIntroOutroFailureReason.NoChapters)
        }

        val activeChapterIndex = findActiveChapterIndex(
            chapters = orderedChapters,
            currentPositionSeconds = currentPositionSeconds.coerceAtLeast(0.0)
        )
        val activeChapter = orderedChapters.getOrNull(activeChapterIndex)
            ?: return SkipIntroOutroResult(failureReason = SkipIntroOutroFailureReason.NoMarkerInCurrentChapter)

        if (!isIntroOutroChapterTitle(activeChapter.title)) {
            return SkipIntroOutroResult(
                failureReason = SkipIntroOutroFailureReason.NoMarkerInCurrentChapter
            )
        }

        val nextChapterStart = orderedChapters
            .getOrNull(activeChapterIndex + 1)
            ?.startSeconds
            ?.coerceAtLeast(0.0)
        val fallbackDuration = bookDurationSeconds?.takeIf { it > 0.0 }
        val targetSeconds = nextChapterStart ?: fallbackDuration
        return if (targetSeconds == null) {
            SkipIntroOutroResult(failureReason = SkipIntroOutroFailureReason.MissingDuration)
        } else {
            SkipIntroOutroResult(targetSeconds = targetSeconds)
        }
    }

    private fun findActiveChapterIndex(
        chapters: List<BookChapter>,
        currentPositionSeconds: Double
    ): Int {
        if (chapters.isEmpty()) return -1
        if (currentPositionSeconds <= chapters.first().startSeconds) return 0

        val index = chapters.indexOfFirst { chapter ->
            val start = chapter.startSeconds.coerceAtLeast(0.0)
            val end = chapter.endSeconds?.coerceAtLeast(start) ?: Double.POSITIVE_INFINITY
            currentPositionSeconds >= start && currentPositionSeconds < end
        }
        return if (index >= 0) index else chapters.lastIndex
    }

    private fun isIntroOutroChapterTitle(raw: String?): Boolean {
        val title = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (title.isBlank()) return false
        return title.contains("intro") ||
            title.contains("introduction") ||
            title.contains("foreword") ||
            title.contains("forward") ||
            title.contains("foreward") ||
            title.contains("prologue") ||
            title.contains("outro") ||
            title.contains("afterword") ||
            title.contains("epilogue") ||
            title.contains("credits")
    }
}

fun SkipIntroOutroFailureReason.toUserMessage(): String {
    return when (this) {
        SkipIntroOutroFailureReason.NoChapters -> "No chapters available."
        SkipIntroOutroFailureReason.NoMarkerInCurrentChapter -> "No intro/outro marker found in this chapter."
        SkipIntroOutroFailureReason.MissingDuration -> "Unable to skip chapter for this book."
    }
}
