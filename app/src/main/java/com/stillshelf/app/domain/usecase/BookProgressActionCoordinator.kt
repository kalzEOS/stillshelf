package com.stillshelf.app.domain.usecase

import com.stillshelf.app.core.model.PlaybackProgress
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.ui.common.applyResolvedPlaybackProgress
import javax.inject.Inject

enum class BookProgressAction(
    val finishedState: Boolean,
    val resetProgressWhenUnfinished: Boolean,
    val defaultMessage: String
) {
    MarkFinished(
        finishedState = true,
        resetProgressWhenUnfinished = false,
        defaultMessage = "Marked as finished. Progress is now 100%."
    ),
    MarkUnfinished(
        finishedState = false,
        resetProgressWhenUnfinished = false,
        defaultMessage = "Marked as unfinished."
    ),
    ResetProgress(
        finishedState = false,
        resetProgressWhenUnfinished = true,
        defaultMessage = "Book progress reset."
    )
}

data class BookProgressActionResult(
    val action: BookProgressAction,
    val message: String,
    val resolvedProgress: PlaybackProgress,
    val finishedState: Boolean
)

class BookProgressActionCoordinator internal constructor(
    private val markBookFinished: suspend (String, Boolean, Boolean) -> AppResult<PlaybackProgress>,
    private val reconcilePlaybackProgress: (String, PlaybackProgress, Boolean) -> Unit
) {
    @Inject
    constructor(
        sessionRepository: SessionRepository,
        playbackController: PlaybackController
    ) : this(
        markBookFinished = { bookId, finished, resetProgressWhenUnfinished ->
            sessionRepository.markBookFinished(
                bookId = bookId,
                finished = finished,
                resetProgressWhenUnfinished = resetProgressWhenUnfinished
            )
        },
        reconcilePlaybackProgress = { bookId, progress, isFinished ->
            playbackController.applyResolvedPlaybackProgress(
                bookId = bookId,
                progress = progress,
                isFinished = isFinished
            )
        }
    )

    suspend operator fun invoke(
        bookId: String,
        action: BookProgressAction
    ): AppResult<BookProgressActionResult> {
        return when (
            val result = markBookFinished(
                bookId,
                action.finishedState,
                action.resetProgressWhenUnfinished
            )
        ) {
            is AppResult.Success -> {
                reconcilePlaybackProgress(bookId, result.value, action.finishedState)
                AppResult.Success(
                    BookProgressActionResult(
                        action = action,
                        message = action.defaultMessage,
                        resolvedProgress = result.value,
                        finishedState = action.finishedState
                    )
                )
            }

            is AppResult.Error -> result
        }
    }
}
