package com.stillshelf.app.playback.controller

import android.media.MediaPlayer
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val isLoading: Boolean = false,
    val book: BookSummary? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

@Singleton
class PlaybackController @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUiState = MutableStateFlow(PlaybackUiState())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentBookId: String? = null
    private var lastSyncedPositionMs: Long = -1L
    private var syncInFlight = false
    private var cachedContinueListeningItem: ContinueListeningItem? = null
    private var playRequestJob: Job? = null
    private var playRequestToken: Long = 0L

    val uiState: StateFlow<PlaybackUiState> = mutableUiState.asStateFlow()

    fun playBook(bookId: String, startPositionMs: Long? = null) {
        if (bookId.isBlank()) return
        val requestToken = beginPlayRequest()
        if (currentBookId == bookId && mediaPlayer != null) {
            if (startPositionMs != null) {
                seekToPosition(startPositionMs)
            }
            resume()
            return
        }

        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        playRequestJob = scope.launch {
            when (val sourceResult = sessionRepository.fetchPlaybackSource(bookId)) {
                is AppResult.Success -> {
                    if (isStalePlayRequest(requestToken)) return@launch
                    sessionRepository.setLastPlayedBookId(sourceResult.value.book.id)
                    val progressResult = sessionRepository.fetchPlaybackProgress(sourceResult.value.book.id)
                    if (isStalePlayRequest(requestToken)) return@launch
                    val serverResumeMs = when (progressResult) {
                        is AppResult.Success -> {
                            ((progressResult.value?.currentTimeSeconds ?: 0.0) * 1000.0).toLong()
                        }

                        is AppResult.Error -> 0L
                    }
                    val resumeMs = startPositionMs ?: serverResumeMs
                    val progressPercent = when {
                        startPositionMs != null -> null
                        progressResult is AppResult.Success -> progressResult.value?.progressPercent
                        else -> null
                    }
                    val currentTimeSeconds = when {
                        startPositionMs != null -> (startPositionMs / 1000.0).coerceAtLeast(0.0)
                        progressResult is AppResult.Success -> progressResult.value?.currentTimeSeconds
                        else -> null
                    }
                    cachedContinueListeningItem = ContinueListeningItem(
                        book = sourceResult.value.book,
                        progressPercent = progressPercent,
                        currentTimeSeconds = currentTimeSeconds
                    )
                    if (isStalePlayRequest(requestToken)) return@launch
                    prepareAndPlay(
                        sourceResult.value.book.id,
                        sourceResult.value.book,
                        sourceResult.value.streamUrl,
                        resumeMs = resumeMs
                    )
                }
                is AppResult.Error -> {
                    if (isStalePlayRequest(requestToken)) return@launch
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = sourceResult.message
                        )
                    }
                }
            }
        }
    }

    private fun beginPlayRequest(): Long {
        playRequestJob?.cancel()
        playRequestToken += 1L
        return playRequestToken
    }

    private fun isStalePlayRequest(requestToken: Long): Boolean {
        return requestToken != playRequestToken
    }

    fun playBookFromPosition(bookId: String, startPositionMs: Long) {
        playBook(
            bookId = bookId,
            startPositionMs = startPositionMs.coerceAtLeast(0L)
        )
    }

    fun togglePlayPause() {
        if (uiState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        val duration = safeDuration(player)
        val current = safePosition(player)
        val target = if (duration > 0L) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        runCatching { player.seekTo(target.toInt()) }
        mutableUiState.update { it.copy(positionMs = target) }
        syncProgress(force = true, isFinished = false)
    }

    private fun seekToPosition(targetMs: Long) {
        val player = mediaPlayer ?: return
        val duration = safeDuration(player)
        val clamped = if (duration > 0L) {
            targetMs.coerceIn(0L, duration)
        } else {
            targetMs.coerceAtLeast(0L)
        }
        runCatching { player.seekTo(clamped.toInt()) }
        mutableUiState.update { it.copy(positionMs = clamped) }
        syncProgress(force = true, isFinished = false)
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        runCatching { player.pause() }
        updateProgress(player)
        syncProgress(force = true, isFinished = false)
        mutableUiState.update { it.copy(isPlaying = false) }
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        runCatching { player.start() }
        mutableUiState.update { it.copy(isPlaying = true, errorMessage = null) }
    }

    private fun prepareAndPlay(bookId: String, book: BookSummary, streamUrl: String, resumeMs: Long) {
        releasePlayer()

        val player = MediaPlayer()
        mediaPlayer = player
        currentBookId = bookId
        lastSyncedPositionMs = -1L
        mutableUiState.update {
            it.copy(
                isLoading = true,
                book = book,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null
            )
        }
        updateCachedFromUiState()

        player.setOnPreparedListener { prepared ->
            val duration = safeDuration(prepared)
            val clampedResume = if (duration > 0L) {
                resumeMs.coerceIn(0L, (duration - 1_000L).coerceAtLeast(0L))
            } else {
                resumeMs.coerceAtLeast(0L)
            }
            if (clampedResume > 0L) {
                runCatching { prepared.seekTo(clampedResume.toInt()) }
            }
            runCatching { prepared.start() }
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = true,
                    positionMs = clampedResume,
                    durationMs = duration
                )
            }
            updateCachedFromUiState()
            startProgressUpdates()
        }
        player.setOnCompletionListener { completed ->
            val duration = safeDuration(completed)
            mutableUiState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = duration,
                    durationMs = duration
                )
            }
            updateCachedFromUiState()
            syncProgress(force = true, isFinished = true)
        }
        player.setOnErrorListener { _, _, _ ->
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = "Playback failed. Try another book."
                )
            }
            true
        }

        runCatching {
            player.setDataSource(streamUrl)
            player.prepareAsync()
        }.onFailure { throwable ->
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = throwable.message ?: "Unable to start playback."
                )
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    updateProgress(it)
                    syncProgress(force = false, isFinished = false)
                }
                delay(500L)
            }
        }
    }

    private fun updateProgress(player: MediaPlayer) {
        mutableUiState.update {
            it.copy(
                positionMs = safePosition(player),
                durationMs = safeDuration(player)
            )
        }
        updateCachedFromUiState()
    }

    private fun safePosition(player: MediaPlayer): Long {
        return runCatching { player.currentPosition.toLong().coerceAtLeast(0L) }.getOrDefault(0L)
    }

    private fun safeDuration(player: MediaPlayer): Long {
        val duration = runCatching { player.duration.toLong() }.getOrDefault(0L)
        return duration.coerceAtLeast(0L)
    }

    private fun releasePlayer() {
        syncProgress(force = true, isFinished = false)
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.runCatching { release() }
        mediaPlayer = null
    }

    fun saveProgressSnapshot() {
        syncProgress(force = true, isFinished = false)
    }

    fun cacheContinueListeningItem(item: ContinueListeningItem?) {
        if (item != null) {
            cachedContinueListeningItem = item
        }
    }

    fun getCachedContinueListeningItem(): ContinueListeningItem? = cachedContinueListeningItem

    private fun syncProgress(force: Boolean, isFinished: Boolean) {
        val bookId = currentBookId ?: return
        val state = uiState.value
        val currentMs = state.positionMs.coerceAtLeast(0L)
        if (!force && lastSyncedPositionMs >= 0L && abs(currentMs - lastSyncedPositionMs) < 15_000L) {
            return
        }
        if (syncInFlight) return

        syncInFlight = true
        scope.launch(Dispatchers.IO) {
            sessionRepository.syncPlaybackProgress(
                bookId = bookId,
                currentTimeSeconds = currentMs / 1000.0,
                durationSeconds = state.durationMs.takeIf { it > 0L }?.div(1000.0),
                isFinished = isFinished
            )
            lastSyncedPositionMs = currentMs
            syncInFlight = false
        }
    }

    private fun updateCachedFromUiState() {
        val state = uiState.value
        val currentBook = state.book ?: return
        val durationSeconds = if (state.durationMs > 0L) {
            state.durationMs / 1000.0
        } else {
            currentBook.durationSeconds
        }
        val currentSeconds = state.positionMs.coerceAtLeast(0L) / 1000.0
        val progressPercent = durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (currentSeconds / it).coerceIn(0.0, 1.0) }

        cachedContinueListeningItem = ContinueListeningItem(
            book = currentBook,
            progressPercent = progressPercent,
            currentTimeSeconds = currentSeconds
        )
    }
}
