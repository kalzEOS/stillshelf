package com.stillshelf.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookChapter
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
    val displayTitle: String = "Nothing playing",
    val isPlaying: Boolean = false,
    val rewindSeconds: Int = 15,
    val errorMessage: String? = null
)

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val playbackController: PlaybackController,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MiniPlayerUiState())
    private var hadActivePlayback = false
    private val chapterCache = mutableMapOf<String, List<BookChapter>>()
    private var loadingChaptersForBookId: String? = null
    val uiState: StateFlow<MiniPlayerUiState> = mutableUiState.asStateFlow()

    init {
        observePlaybackState()
        observeSessionChanges()
        observeSkipSettings()
        refresh()
    }

    fun refresh() {
        val playbackState = playbackController.uiState.value
        val livePlaybackItem = playbackState.toMiniPlayerItem()
        if (livePlaybackItem != null) {
            hadActivePlayback = true
            playbackController.cacheContinueListeningItem(livePlaybackItem)
            ensureBookChapters(livePlaybackItem.book.id)
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    item = livePlaybackItem,
                    displayTitle = resolvePlayerTitle(livePlaybackItem),
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
                    result.value?.book?.id?.let(::ensureBookChapters)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            item = result.value,
                            displayTitle = resolvePlayerTitle(result.value),
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
                            displayTitle = "Nothing playing",
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
            val deltaMs = -(uiState.value.rewindSeconds * 1000L)
            playbackController.seekBy(deltaMs = deltaMs)
        }
    }

    private fun observeSkipSettings() {
        viewModelScope.launch {
            sessionPreferences.state.collect { pref ->
                mutableUiState.update {
                    it.copy(rewindSeconds = pref.skipBackwardSeconds.coerceIn(10, 60))
                }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            playbackController.uiState.collect { playbackState ->
                val livePlaybackItem = playbackState.toMiniPlayerItem()
                if (livePlaybackItem != null) {
                    hadActivePlayback = true
                    playbackController.cacheContinueListeningItem(livePlaybackItem)
                    ensureBookChapters(livePlaybackItem.book.id)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            item = livePlaybackItem,
                            displayTitle = resolvePlayerTitle(livePlaybackItem),
                            isPlaying = playbackState.isPlaying,
                            errorMessage = null
                        )
                    }
                } else if (hadActivePlayback) {
                    hadActivePlayback = false
                    refresh()
                } else {
                    mutableUiState.update { it.copy(isPlaying = false, displayTitle = "Nothing playing") }
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

    private fun ensureBookChapters(bookId: String) {
        if (bookId.isBlank()) return
        if (chapterCache.containsKey(bookId) || loadingChaptersForBookId == bookId) return
        loadingChaptersForBookId = bookId
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookDetail(bookId, forceRefresh = false)) {
                is AppResult.Success -> {
                    chapterCache[bookId] = result.value.chapters
                    val currentItem = uiState.value.item
                    if (currentItem?.book?.id == bookId) {
                        mutableUiState.update {
                            it.copy(displayTitle = resolvePlayerTitle(currentItem))
                        }
                    }
                }

                is AppResult.Error -> Unit
            }
            if (loadingChaptersForBookId == bookId) {
                loadingChaptersForBookId = null
            }
        }
    }

    private fun resolvePlayerTitle(item: ContinueListeningItem?): String {
        if (item == null) return "Nothing playing"
        val chapterTitle = findActiveChapterTitle(
            chapters = chapterCache[item.book.id].orEmpty(),
            positionSeconds = item.currentTimeSeconds ?: 0.0
        )
        return if (!chapterTitle.isNullOrBlank() && !chapterTitle.equals(item.book.title, ignoreCase = true)) {
            "${item.book.title} - $chapterTitle"
        } else {
            item.book.title
        }
    }
}

private fun findActiveChapterTitle(chapters: List<BookChapter>, positionSeconds: Double): String? {
    val index = findActiveChapterIndex(chapters, positionSeconds)
    if (index !in chapters.indices) return null
    return chapters[index].title.trim().takeIf { it.isNotBlank() }
}

private fun findActiveChapterIndex(chapters: List<BookChapter>, positionSeconds: Double): Int {
    if (chapters.isEmpty()) return -1
    val target = positionSeconds.coerceAtLeast(0.0)
    val index = chapters.indexOfFirst { chapter ->
        val end = chapter.endSeconds ?: Double.POSITIVE_INFINITY
        target >= chapter.startSeconds && target < end
    }
    return if (index >= 0) index else chapters.lastIndex
}

private fun com.stillshelf.app.playback.controller.PlaybackUiState.toMiniPlayerItem(): ContinueListeningItem? {
    val currentBook = book ?: return null
    val durationSeconds = currentBook.durationSeconds?.takeIf { it > 0.0 }
        ?: if (durationMs > 0L) durationMs / 1000.0 else null
    val positionSeconds = positionMs.coerceAtLeast(0L) / 1000.0
    val progress = durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { (positionSeconds / it).coerceIn(0.0, 1.0) }

    return ContinueListeningItem(
        book = currentBook.copy(
            durationSeconds = currentBook.durationSeconds?.takeIf { it > 0.0 } ?: durationSeconds
        ),
        progressPercent = progress,
        currentTimeSeconds = positionSeconds
    )
}
