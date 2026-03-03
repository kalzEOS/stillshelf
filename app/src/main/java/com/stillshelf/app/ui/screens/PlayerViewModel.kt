package com.stillshelf.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookBookmark
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
    private val mutableBookmarks = MutableStateFlow<List<BookBookmark>>(emptyList())
    val bookmarks: StateFlow<List<BookBookmark>> = mutableBookmarks.asStateFlow()
    private val mutableActionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = mutableActionMessage.asStateFlow()
    private val mutableControlPrefs = MutableStateFlow(PlayerControlPrefs())
    val controlPrefs: StateFlow<PlayerControlPrefs> = mutableControlPrefs.asStateFlow()
    private var loadedBookId: String? = null

    init {
        observeControlPrefs()
        val bookId = savedStateHandle.get<String>(MainRoute.PLAYER_BOOK_ID_ARG).orEmpty()
        if (bookId.isNotBlank()) {
            loadBookMetadata(bookId)
            playbackController.playBook(bookId)
        } else if (playbackController.uiState.value.book == null) {
            val cachedItem = playbackController.getCachedContinueListeningItem()
            if (cachedItem != null) {
                mutablePreviewItem.value = cachedItem
                loadBookMetadata(cachedItem.book.id)
            } else {
                viewModelScope.launch {
                    when (val result = sessionRepository.fetchMiniPlayerItem()) {
                        is AppResult.Success -> {
                            mutablePreviewItem.value = result.value
                            result.value?.book?.id?.let(::loadBookMetadata)
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
                    loadBookMetadata(playbackState.book.id)
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

    fun jumpToSeconds(seconds: Double) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id ?: return
        val positionMs = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val playbackState = uiState.value
        if (playbackState.book?.id == bookId) {
            playbackController.seekToPositionMs(positionMs = positionMs, commit = true)
            if (!playbackState.isPlaying) {
                playbackController.togglePlayPause()
            }
        } else {
            playbackController.playBookFromPosition(bookId = bookId, startPositionMs = positionMs)
        }
    }

    fun addBookmark(positionSeconds: Double, title: String?) {
        val bookId = uiState.value.book?.id ?: previewItem.value?.book?.id
        if (bookId.isNullOrBlank()) {
            mutableActionMessage.value = "Unable to add bookmark right now."
            return
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.createBookmark(
                    bookId = bookId,
                    timeSeconds = positionSeconds.coerceAtLeast(0.0),
                    title = title?.trim().takeUnless { it.isNullOrBlank() }
                )
            ) {
                is AppResult.Success -> {
                    mutableActionMessage.value = "Bookmark added."
                    loadBookMetadata(bookId = bookId, forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableActionMessage.value = result.message
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableActionMessage.value = null
    }

    private fun loadBookMetadata(bookId: String, forceRefresh: Boolean = false) {
        if (bookId.isBlank()) return
        if (!forceRefresh && loadedBookId == bookId) return
        loadedBookId = bookId
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId, forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableChapters.value = result.value.chapters
                    mutableBookmarks.value = result.value.bookmarks
                }

                is AppResult.Error -> {
                    if (forceRefresh) {
                        mutableActionMessage.value = result.message
                    } else {
                        mutableChapters.value = emptyList()
                        mutableBookmarks.value = emptyList()
                    }
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
