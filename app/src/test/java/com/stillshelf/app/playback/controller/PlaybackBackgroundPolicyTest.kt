package com.stillshelf.app.playback.controller

import android.media.AudioManager
import androidx.media3.common.Player
import com.stillshelf.app.core.model.BookSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackgroundPolicyTest {

    @Test
    fun shouldKeepPlaybackSessionActive_requiresLoadedBook() {
        assertFalse(
            shouldKeepPlaybackSessionActive(
                book = null,
                hasActivePlayer = true
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_requiresActivePlayer() {
        assertFalse(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                hasActivePlayer = false
            )
        )
    }

    @Test
    fun shouldKeepPlaybackSessionActive_allowsForegroundSessionWhilePlayerExists() {
        assertTrue(
            shouldKeepPlaybackSessionActive(
                book = sampleBook(),
                hasActivePlayer = true
            )
        )
    }

    @Test
    fun shouldScheduleAbsPausedPlayerRelease_requiresBackgroundPausedPlayer() {
        assertTrue(
            shouldScheduleAbsPausedPlayerRelease(
                book = sampleBook(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                appInForeground = false
            )
        )
        assertFalse(
            shouldScheduleAbsPausedPlayerRelease(
                book = sampleBook(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                appInForeground = true
            )
        )
        assertFalse(
            shouldScheduleAbsPausedPlayerRelease(
                book = sampleBook(),
                hasActivePlayer = true,
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                appInForeground = false
            )
        )
        assertFalse(
            shouldScheduleAbsPausedPlayerRelease(
                book = sampleBook(),
                hasActivePlayer = true,
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_BUFFERING,
                appInForeground = false
            )
        )
    }

    @Test
    fun shouldContinuePlaybackSyncRetry_requiresPlaybackToStillBeActiveForSameBook() {
        assertFalse(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-1",
                isPlaybackActive = false
            )
        )
        assertFalse(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-2",
                isPlaybackActive = true
            )
        )
        assertTrue(
            shouldContinuePlaybackSyncRetry(
                allowBackgroundRetry = true,
                requestBookId = "book-1",
                currentBookId = "book-1",
                isPlaybackActive = true
            )
        )
    }

    @Test
    fun resolveResumeProgressUpdateMode_startsImmediatelyWhenAudioFocusIsGranted() {
        assertEquals(
            ResumeProgressUpdateMode.Immediate,
            resolveResumeProgressUpdateMode(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        )
    }

    @Test
    fun resolveResumeProgressUpdateMode_waitsForAudioFocusWhenPlaybackIsDelayed() {
        assertEquals(
            ResumeProgressUpdateMode.OnAudioFocusGain,
            resolveResumeProgressUpdateMode(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)
        )
    }

    @Test
    fun resolveResumeProgressUpdateMode_disablesUpdatesWhenAudioFocusFails() {
        assertEquals(
            ResumeProgressUpdateMode.Never,
            resolveResumeProgressUpdateMode(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        )
    }

    private fun sampleBook(): BookSummary {
        return BookSummary(
            id = "book-1",
            libraryId = "library-1",
            title = "Sample",
            authorName = "Author",
            narratorName = null,
            durationSeconds = 3600.0,
            coverUrl = null
        )
    }
}
