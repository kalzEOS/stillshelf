package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerControlPrefs(
    val skipForwardSeconds: Int = 15,
    val skipBackwardSeconds: Int = 15
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState
    private val mutablePreviewItem = MutableStateFlow<ContinueListeningItem?>(null)
    val previewItem: StateFlow<ContinueListeningItem?> = mutablePreviewItem.asStateFlow()
    private val mutableChapters = MutableStateFlow<List<BookChapter>>(emptyList())
    val chapters: StateFlow<List<BookChapter>> = mutableChapters.asStateFlow()
    private val mutableControlPrefs = MutableStateFlow(PlayerControlPrefs())
    val controlPrefs: StateFlow<PlayerControlPrefs> = mutableControlPrefs.asStateFlow()
    private var chaptersBookId: String? = null

    init {
        observeControlPrefs()
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
        playbackController.seekBy(deltaMs = -(controlPrefs.value.skipBackwardSeconds * 1000L))
    }

    fun onForwardClick() {
        playbackController.seekBy(deltaMs = (controlPrefs.value.skipForwardSeconds * 1000L))
    }

    fun onScrubProgress(progressFraction: Float) {
        playbackController.seekToProgress(progressFraction = progressFraction, commit = false)
    }

    fun onScrubProgressFinished(progressFraction: Float) {
        playbackController.seekToProgress(progressFraction = progressFraction, commit = true)
    }

    fun onDismissPlayer() {
        playbackController.saveProgressSnapshot()
    }

    fun seekToPositionMs(positionMs: Long, commit: Boolean) {
        playbackController.seekToPositionMs(positionMs = positionMs, commit = commit)
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

    private fun observeControlPrefs() {
        viewModelScope.launch {
            sessionPreferences.state.collect { pref ->
                mutableControlPrefs.update {
                    it.copy(
                        skipForwardSeconds = pref.skipForwardSeconds.coerceIn(10, 60),
                        skipBackwardSeconds = pref.skipBackwardSeconds.coerceIn(10, 60)
                    )
                }
            }
        }
    }
}
