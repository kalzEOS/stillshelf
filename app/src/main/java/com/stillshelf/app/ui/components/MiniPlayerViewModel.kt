package com.stillshelf.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.PodcastRepository
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.toLocalPlaybackSource
import com.stillshelf.app.playback.controller.PlaybackController
import com.stillshelf.app.playback.controller.secondsToPlaybackPositionMs
import com.stillshelf.app.ui.common.applyBookProgressMutation
import com.stillshelf.app.ui.common.withBookProgressMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    private val sessionPreferences: SessionPreferences,
    private val podcastRepository: PodcastRepository,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    private companion object {
        private const val PLAYBACK_START_TIMEOUT_MS = 6_000L
    }

    private val mutableUiState = MutableStateFlow(MiniPlayerUiState())
    private var hadActivePlayback = false
    private val chapterCache = mutableMapOf<String, List<BookChapter>>()
    private var loadingChaptersForBookId: String? = null
    private var playbackStartWatchdogJob: Job? = null
    val uiState: StateFlow<MiniPlayerUiState> = mutableUiState.asStateFlow()

    init {
        observePlaybackState()
        observeBookProgressMutations()
        observeSessionChanges()
        observeSkipSettings()
        refresh()
    }

    fun refresh() {
        val playbackState = playbackController.uiState.value
        val livePlaybackItem = playbackState.toMiniPlayerItem()
        if (livePlaybackItem != null) {
            hadActivePlayback = true
            if (!playbackState.isLoading) {
                cancelPlaybackStartWatchdog()
            }
            playbackController.cacheContinueListeningItem(livePlaybackItem)
            ensureBookChapters(livePlaybackItem.book.id)
            mutableUiState.update {
                it.copy(
                    isLoading = playbackState.isLoading,
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
            val lastPlayedId = sessionPreferences.state.first().lastPlayedBookId
            if (!lastPlayedId.isNullOrBlank() && lastPlayedId.contains("::")) {
                loadPodcastEpisodeItem(lastPlayedId)
            } else {
                loadAudiobookItem()
            }
        }
    }

    private suspend fun loadPodcastEpisodeItem(compoundId: String) {
        val (showId, episodeId) = compoundId.split("::", limit = 2)
        when (val result = podcastRepository.fetchPodcastEpisodePlaybackSource(showId, episodeId)) {
                        is AppResult.Success -> {
                            if (playbackController.uiState.value.toMiniPlayerItem() != null) {
                                mutableUiState.update { it.copy(isLoading = false, errorMessage = null) }
                                return
                            }
                            val book = result.value.book
                            val item = ContinueListeningItem(
                                book = book,
                                progressPercent = book.progressPercent,
                                currentTimeSeconds = book.currentTimeSeconds
                            )
                            mutableUiState.update {
                                it.copy(
                                    isLoading = false,
                                    item = item,
                                    displayTitle = book.title,
                                    isPlaying = false,
                                    errorMessage = null
                                )
                            }
                        }
            is AppResult.Error -> loadAudiobookItem()
        }
    }

    private suspend fun loadAudiobookItem(): Boolean {
        return when (val result = sessionRepository.fetchMiniPlayerItem()) {
            is AppResult.Success -> {
                playbackController.cacheContinueListeningItem(result.value)
                result.value?.book?.id?.let(::ensureBookChapters)
                if (playbackController.uiState.value.toMiniPlayerItem() != null) {
                    mutableUiState.update { it.copy(isLoading = false, errorMessage = null) }
                    return true
                }
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        item = result.value,
                        displayTitle = resolvePlayerTitle(result.value),
                        isPlaying = false,
                        errorMessage = null
                    )
                }
                result.value != null
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
                false
            }
        }
    }

    fun onPlayPauseClick() {
        val playbackState = playbackController.uiState.value
        if (playbackState.book != null) {
            val bookId = playbackState.book.id
            val isEpisodeFinished = playbackState.book.isFinished == true ||
                (playbackState.durationMs > 0L &&
                    playbackState.positionMs.toDouble() / playbackState.durationMs.toDouble() >= 0.995)
            if (bookId.contains("::") && (isEpisodeFinished || !playbackController.hasActivePlayer)) {
                val (showId, episodeId) = bookId.split("::", limit = 2)
                val resumeSeconds = if (isEpisodeFinished) 0.0 else playbackState.positionMs / 1000.0
                viewModelScope.launch {
                    when (val result = podcastRepository.fetchPodcastEpisodePlaybackSource(showId, episodeId)) {
                        is AppResult.Success -> {
                            val startMs = if (resumeSeconds > 0.0) (resumeSeconds * 1000.0).toLong() else null
                            val localDownload = bookDownloadManager
                                .getCompletedDownloadForPodcast(result.value.book.id)
                                ?.toLocalPlaybackSource(result.value.book)
                            playbackController.playFromSource(
                                localDownload ?: result.value,
                                startPositionMs = startMs
                            )
                        }
                        is AppResult.Error -> mutableUiState.update { it.copy(errorMessage = result.message) }
                    }
                }
                return
            }
            playbackController.togglePlayPause()
            return
        }

        val fallbackItem = uiState.value.item ?: return
        mutableUiState.update {
            it.copy(
                isLoading = true,
                isPlaying = false,
                errorMessage = null
            )
        }
        val fallbackStartPositionMs = secondsToPlaybackPositionMs(fallbackItem.currentTimeSeconds)
        if (fallbackStartPositionMs != null && fallbackStartPositionMs > 0L) {
            playbackController.playBookFromPosition(
                bookId = fallbackItem.book.id,
                startPositionMs = fallbackStartPositionMs
            )
        } else {
            playbackController.playBook(fallbackItem.book.id)
        }
        startPlaybackWatchdog(bookId = fallbackItem.book.id)
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
                    if (!playbackState.isLoading) {
                        cancelPlaybackStartWatchdog()
                    }
                    playbackController.cacheContinueListeningItem(livePlaybackItem)
                    ensureBookChapters(livePlaybackItem.book.id)
                    mutableUiState.update {
                        it.copy(
                            isLoading = playbackState.isLoading,
                            item = livePlaybackItem,
                            displayTitle = resolvePlayerTitle(livePlaybackItem),
                            isPlaying = playbackState.isPlaying,
                            errorMessage = null
                        )
                    }
                } else if (hadActivePlayback) {
                    if (!playbackState.isLoading) {
                        cancelPlaybackStartWatchdog()
                    }
                    hadActivePlayback = false
                    refresh()
                } else {
                    if (!playbackState.isLoading) {
                        cancelPlaybackStartWatchdog()
                    }
                    mutableUiState.update { currentState ->
                        val cachedItem = currentState.item
                        currentState.copy(
                            isLoading = playbackState.isLoading,
                            isPlaying = false,
                            displayTitle = resolvePlayerTitle(cachedItem),
                            errorMessage = playbackState.errorMessage
                        )
                    }
                }
            }
        }
    }

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                val playbackBookId = playbackController.uiState.value.book?.id
                if (playbackBookId == mutation.bookId) {
                    playbackController.applyBookProgressMutation(mutation)
                    return@collect
                }
                val currentItem = uiState.value.item ?: return@collect
                if (currentItem.book.id != mutation.bookId) return@collect
                mutableUiState.update { state ->
                    state.copy(
                        item = currentItem.withBookProgressMutation(mutation),
                        displayTitle = resolvePlayerTitle(currentItem.withBookProgressMutation(mutation)),
                        isPlaying = false
                    )
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
        if (bookId.isBlank() || bookId.contains("::")) return
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

    private fun startPlaybackWatchdog(bookId: String) {
        cancelPlaybackStartWatchdog()
        playbackStartWatchdogJob = viewModelScope.launch {
            delay(PLAYBACK_START_TIMEOUT_MS)
            val playbackState = playbackController.uiState.value
            val activeBookId = playbackState.book?.id
            if (activeBookId == bookId || !playbackState.isLoading) {
                return@launch
            }
            mutableUiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = playbackState.errorMessage ?: "Playback didn't start. Try again."
                )
            }
        }
    }

    private fun cancelPlaybackStartWatchdog() {
        playbackStartWatchdogJob?.cancel()
        playbackStartWatchdogJob = null
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
    val index = chapters.indexOfLast { chapter -> target >= chapter.startSeconds }
    return if (index >= 0) index else 0
}

private fun com.stillshelf.app.playback.controller.PlaybackUiState.toMiniPlayerItem(): ContinueListeningItem? {
    val currentBook = book ?: return null
    val durationSeconds = listOfNotNull(
        currentBook.durationSeconds?.takeIf { it > 0.0 },
        durationMs.takeIf { it > 0L }?.div(1000.0)
    ).maxOrNull()
    val positionSeconds = positionMs.coerceAtLeast(0L) / 1000.0
    val progress = durationSeconds
        ?.takeIf { it > 0.0 }
        ?.let { (positionSeconds / it).coerceIn(0.0, 1.0) }

    return ContinueListeningItem(
        book = currentBook.copy(
            durationSeconds = durationSeconds ?: currentBook.durationSeconds
        ),
        progressPercent = progress,
        currentTimeSeconds = positionSeconds
    )
}
