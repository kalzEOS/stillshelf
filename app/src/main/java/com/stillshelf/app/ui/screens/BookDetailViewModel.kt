package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookDetail
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val isLoading: Boolean = false,
    val detail: BookDetail? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val progressPercent: Double? = null,
    val currentTimeSeconds: Double? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val bookId: String = savedStateHandle.get<String>(DetailRoute.BOOK_ID_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(BookDetailUiState(isLoading = true))
    val uiState: StateFlow<BookDetailUiState> = mutableUiState.asStateFlow()
    val playbackUiState: StateFlow<PlaybackUiState> = playbackController.uiState

    init {
        refresh()
    }

    fun refresh() {
        if (bookId.isBlank()) {
            mutableUiState.update { it.copy(isLoading = false, errorMessage = "Invalid book id.") }
            return
        }
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId)) {
                is AppResult.Success -> {
                    val progress = when (val progressResult = sessionRepository.fetchPlaybackProgress(bookId)) {
                        is AppResult.Success -> progressResult.value
                        is AppResult.Error -> null
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            detail = result.value,
                            actionMessage = null,
                            progressPercent = progress?.progressPercent,
                            currentTimeSeconds = progress?.currentTimeSeconds
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun addToCollection() {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.addBookToDefaultCollection(bookId)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(actionMessage = "Added to Collections")
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(actionMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    fun playChapter(startSeconds: Double) {
        if (bookId.isBlank()) return
        val positionMs = (startSeconds.coerceAtLeast(0.0) * 1000.0).toLong()
        playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
    }
}
