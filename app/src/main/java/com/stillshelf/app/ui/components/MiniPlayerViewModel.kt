package com.stillshelf.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.playback.controller.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MiniPlayerUiState(
    val isLoading: Boolean = false,
    val item: ContinueListeningItem? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MiniPlayerUiState())
    private var hadActivePlayback = false
    val uiState: StateFlow<MiniPlayerUiState> = mutableUiState.asStateFlow()

    init {
        observePlaybackState()
        observeSessionChanges()
        refresh()
    }

    fun refresh() {
        val playbackState = playbackController.uiState.value
        val livePlaybackItem = playbackState.toMiniPlayerItem()
        if (livePlaybackItem != null) {
            hadActivePlayback = true
            playbackController.cacheContinueListeningItem(livePlaybackItem)
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    item = livePlaybackItem,
                    isPlaying = playbackState.isPlaying,
                    errorMessage = null
                )
            }
            return
        }
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchMiniPlayerItem()) {
                is AppResult.Success -> {
                    playbackController.cacheContinueListeningItem(result.value)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            item = result.value,
                            isPlaying = false,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            item = null,
                            isPlaying = false,
                            errorMessage = result.message
                        )
                    }
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

        val fallbackItem = uiState.value.item ?: return
        playbackController.playBook(fallbackItem.book.id)
    }

    fun onRewindClick() {
        val playbackState = playbackController.uiState.value
        if (playbackState.book != null) {
            playbackController.seekBy(deltaMs = -15_000L)
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            playbackController.uiState.collect { playbackState ->
                val livePlaybackItem = playbackState.toMiniPlayerItem()
                if (livePlaybackItem != null) {
                    hadActivePlayback = true
                    playbackController.cacheContinueListeningItem(livePlaybackItem)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            item = livePlaybackItem,
                            isPlaying = playbackState.isPlaying,
                            errorMessage = null
                        )
                    }
                } else if (hadActivePlayback) {
                    hadActivePlayback = false
                    refresh()
                } else {
                    mutableUiState.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            sessionRepository.observeSessionState()
                .map { session -> session.activeServerId to session.activeLibraryId }
                .distinctUntilChanged()
                .collect {
                    refresh()
            }
        }
    }
}

private fun com.stillshelf.app.playback.controller.PlaybackUiState.toMiniPlayerItem(): ContinueListeningItem? {
    val currentBook = book ?: return null
    val durationSeconds = if (durationMs > 0L) durationMs / 1000.0 else currentBook.durationSeconds
    val positionSeconds = positionMs.coerceAtLeast(0L) / 1000.0
    val progress = durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { (positionSeconds / it).coerceIn(0.0, 1.0) }

    return ContinueListeningItem(
        book = currentBook,
        progressPercent = progress,
        currentTimeSeconds = positionSeconds
    )
}
