package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.NavidromeTrack
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalListeningRecorderTest {
    private val epoch = MutableStateFlow(0L)
    private val writes = mutableListOf<LocalListen>()
    private val history = mockk<LocalListeningRepository>(relaxed = true) {
        every { this@mockk.epoch } returns this@LocalListeningRecorderTest.epoch
        coEvery { record(any(), any()) } answers {
            if (secondArg<Long>() == this@LocalListeningRecorderTest.epoch.value) writes += firstArg<LocalListen>()
        }
    }
    private val track = NavidromeTrack("track", "Song", "Artist", "Album", "album", "artist", null, 60, null, "stream", null, null)

    @Test fun restartingAtSavedPositionContinuesQualifiedEventWithoutCountingPausedTime() = runTest {
        val saved = LocalListen("saved-event", localIdentity("library"), "track", "artist", "album", "Song", "Artist", "Album",
            null, 60, 1000, 35_000, "Artist", "Album", "Song", positionMs = 35_000)
        coEvery { history.resumable("library", "track", 35_000) } returns saved
        val recorder = LocalListeningRecorder(history, backgroundScope)
        // Queue restoration can expose a paused zero position before the actual saved position.
        recorder.sample(track, false, "library", true, 0, 5000, 0)
        recorder.sample(track, false, "library", true, 1_000, 5001, 35_000)
        runCurrent()
        repeat(11) { second ->
            recorder.sample(track, true, "library", true, 2_000L + second * 1000, 5002L + second, 35_000L + second * 1000)
            runCurrent()
        }
        recorder.transition(); runCurrent()
        assertEquals(setOf("saved-event"), writes.map { it.id }.toSet())
        assertEquals(45_000L, writes.last().listenedMs)
        assertEquals(1000L, writes.last().startedAtSeconds)
        assertTrue(writes.last().ended)
    }

    @Test fun delayedResumeLookupCannotAttachToANewRepeatOfTheSameTrack() = runTest {
        val saved = LocalListen("old", localIdentity("library"), "track", "artist", "album", "Song", "Artist", "Album",
            null, 60, 1000, 35_000, "Artist", "Album", "Song", positionMs = 35_000)
        val gate = kotlinx.coroutines.CompletableDeferred<LocalListen?>()
        coEvery { history.resumable(any(), any(), any()) } coAnswers { gate.await() }
        val recorder = LocalListeningRecorder(history, backgroundScope)
        recorder.sample(track, true, "library", true, 0, 2000, 35_000)
        runCurrent()
        recorder.transition()
        repeat(36) { second ->
            recorder.sample(track, true, "library", true, 1000L + second * 1000, 2001L + second, second * 1000L)
            runCurrent()
        }
        val freshId = writes.last().id
        assertNotEquals("old", freshId)
        gate.complete(saved); runCurrent()
        recorder.transition(); runCurrent()
        assertEquals(freshId, writes.last().id)
        assertTrue(writes.filter { it.id == "old" }.all { it.ended })
    }

    @Test fun offRecordsNothingAndEnabledUsesElapsedPlaybackRatherThanTrackLength() = runTest {
        val recorder = LocalListeningRecorder(history, backgroundScope)
        repeat(40) { recorder.sample(track, true, "library", false, it * 1000L, 1000L + it) }
        runCurrent()
        assertTrue(writes.isEmpty())
        repeat(36) { recorder.sample(track, true, "library", true, (it + 40) * 1000L, 1040L + it); runCurrent() }
        recorder.sample(track, false, "library", true, 76_000, 1076); runCurrent()
        assertEquals(1, writes.map { it.id }.distinct().size)
        assertEquals(1040L, writes.first().startedAtSeconds)
        assertEquals(36_000L, writes.last().listenedMs)
        assertTrue(writes.last().listenedMs < track.durationSeconds!! * 1000L)
    }

    @Test fun pausedTimeAndLargeObservationGapsNeverQualifyAListen() = runTest {
        val recorder = LocalListeningRecorder(history, backgroundScope)
        recorder.sample(track, true, "library", true, 0, 1000)
        recorder.sample(track, true, "library", true, 300_000, 1300)
        recorder.sample(track, false, "library", true, 301_000, 1301)
        repeat(40) { recorder.sample(track, false, "library", true, 302_000L + it * 1000, 1302L + it) }
        runCurrent()
        assertTrue(writes.isEmpty())
    }

    @Test fun repeatStartsANewEventAndDeletingHistoryRequiresFreshQualification() = runTest {
        val recorder = LocalListeningRecorder(history, backgroundScope)
        fun sample(from: Int, to: Int) { for (second in from..to) {
            recorder.sample(track, true, "library", true, second * 1000L, 1000L + second)
        } }
        sample(0, 35); runCurrent()
        recorder.transition()
        sample(36, 70); runCurrent()
        assertEquals(2, writes.map { it.id }.distinct().size)
        epoch.value++
        writes.clear()
        sample(71, 90); runCurrent()
        assertTrue(writes.isEmpty())
        sample(91, 105); runCurrent()
        assertEquals(1, writes.map { it.id }.distinct().size)
    }
}
