package com.stillshelf.app.playback.navidrome

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NavidromePlayerController @Inject constructor() {
    private val mutableState = MutableStateFlow(NavidromePlayerState())
    val state: StateFlow<NavidromePlayerState> = mutableState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun playTracks(
        tracks: List<NavidromeTrack>,
        startIndex: Int
    ) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        mutableState.value = mutableState.value.copy(
            queue = tracks,
            currentIndex = index,
            currentTrack = tracks[index],
            isLoading = true,
            errorMessage = null
        )
        prepareTrack(tracks[index])
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            mutableState.value = mutableState.value.copy(isPlaying = false)
        } else {
            player.start()
            mutableState.value = mutableState.value.copy(isPlaying = true)
        }
    }

    fun playNext() {
        val state = mutableState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex in state.queue.indices) {
            playTracks(state.queue, nextIndex)
        }
    }

    fun playPrevious() {
        val state = mutableState.value
        val previousIndex = state.currentIndex - 1
        if (previousIndex in state.queue.indices) {
            playTracks(state.queue, previousIndex)
        }
    }

    fun stop() {
        releasePlayer()
        mutableState.value = NavidromePlayerState()
    }

    private fun prepareTrack(track: NavidromeTrack) {
        releasePlayer()
        val player = MediaPlayer()
        mediaPlayer = player

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(track.streamUrl)
            player.setOnPreparedListener {
                it.start()
                mutableState.value = mutableState.value.copy(
                    currentTrack = track,
                    isPlaying = true,
                    isLoading = false,
                    errorMessage = null
                )
            }
            player.setOnCompletionListener {
                val state = mutableState.value
                val nextIndex = state.currentIndex + 1
                if (nextIndex in state.queue.indices) {
                    playTracks(state.queue, nextIndex)
                } else {
                    mutableState.value = state.copy(isPlaying = false)
                }
            }
            player.setOnErrorListener { _, _, _ ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isPlaying = false,
                    errorMessage = "Playback failed for this track."
                )
                true
            }
            player.prepareAsync()
        } catch (t: Throwable) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                isPlaying = false,
                errorMessage = t.message ?: "Playback failed."
            )
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching {
            stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
