package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.PlaybackUiState
import com.stillshelf.app.ui.navigation.MainRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState
    private val mutablePreviewItem = MutableStateFlow<ContinueListeningItem?>(null)
    val previewItem: StateFlow<ContinueListeningItem?> = mutablePreviewItem.asStateFlow()
    private val mutableChapters = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters: StateFlow<List<BookChapter>> = mutableChapters.asStateFlow()
    private var chaptersBookId: String? = null

    init {
        val bookId = savedStateHandle.get<String>(MainRoute.PLAYER_BOOK_ID_ARG).orEmpty()
        if (bookId.isNotBlank()) {
            loadChapters(bookId)
            playbackController.playBook(bookId)
        } else if (playbackController.uiState.value.book == null) {
            val cachedItem = playbackController.getCachedContinueListeningItem()
            if (cachedItem != null) {
                mutablePreviewItem.value = cachedItem
                loadChapters(cachedItem.book.id)
            } else {
                viewModelScope.launch {
                    when (val result = sessionRepository.fetchMiniPlayerItem()) {
                        is AppResult.Success -> {
                            mutablePreviewItem.value = result.value
                            result.value?.book?.id?.let(::loadChapters)
                        }

                        is AppResult.Error -> Unit
                    }
                }
            }
        }

        viewModelScope.launch {
            playbackController.uiState.collect { playbackState ->
                if (playbackState.book != null) {
                    mutablePreviewItem.value = null
                    loadChapters(playbackState.book.id)
                }
            }
        }
    }

    fun onPlayPauseClick() {
        val playbackState = playbackController.uiState.value
        if (playbackState.book != null) {
            playbackController.togglePlayPause()
            return
        }
        val previewBookId = previewItem.value?.book?.id.orEmpty()
        if (previewBookId.isNotBlank()) {
            playbackController.playBook(previewBookId)
        }
    }

    fun onRewindClick() {
        playbackController.seekBy(deltaMs = -15_000L)
    }

    fun onForwardClick() {
        playbackController.seekBy(deltaMs = 15_000L)
    }

    fun onDismissPlayer() {
        playbackController.saveProgressSnapshot()
    }

    private fun loadChapters(bookId: String) {
        if (bookId.isBlank() || chaptersBookId == bookId) return
        chaptersBookId = bookId
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId)) {
                is AppResult.Success -> {
                    mutableChapters.value = result.value.chapters
                }

                is AppResult.Error -> {
                    mutableChapters.value = emptyList()
                }
            }
        }
    }
}
