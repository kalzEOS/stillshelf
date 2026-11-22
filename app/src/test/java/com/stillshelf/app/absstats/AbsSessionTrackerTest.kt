package com.stillshelf.app.absstats

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AbsSessionTrackerTest {
    @Test fun midnightClosesPreviousDayBeforeChangingCumulativeTimestamp() {
        val tracker = AbsSessionTracker()
        val midnight = java.time.LocalDate.of(2026, 1, 2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        tracker.sample(sample(0).copy(wallMs = midnight - 1000))
        tracker.sample(sample(500).copy(wallMs = midnight - 500))
        val previousDay = tracker.sample(sample(1000).copy(wallMs = midnight)).single()
        assertEquals(midnight - 500, previousDay.updatedAt)
        assertEquals(500L, previousDay.listeningMs)
        val nextDay = tracker.sample(sample(1500, playing = false).copy(wallMs = midnight + 500)).single()
        assertNotEquals(previousDay.id, nextDay.id)
        assertEquals(midnight, nextDay.startedAt)
        assertEquals(500L, nextDay.listeningMs)
    }
    private fun sample(t: Long, position: Long = t, playing: Boolean = true, speed: Float = 1f, owner: String = "account") =
        AbsPlaybackSample("server", owner, "book", position, playing, speed, t, 1767225600000 + t)

    @Test fun cumulativeUuidSurvivesCheckpointsAndPauseDoesNotAddTime() {
        val tracker = AbsSessionTracker()
        var checkpoint: AbsRecordedSession? = null
        for (tick in 0..32) tracker.sample(sample(tick * 500L)).firstOrNull()?.let { checkpoint = it }
        val ended = tracker.sample(sample(16_500, playing = false)).single()
        assertEquals(checkpoint!!.id, ended.id)
        assertEquals(16_500L, ended.listeningMs)
        assertTrue(tracker.sample(sample(76_500, 16_500, false)).isEmpty())
        assertTrue(tracker.sample(null).isEmpty())
    }
    @Test fun seeksSamplingGapsAndBufferingDoNotInflateTimeAtTwoTimesSpeed() {
        val tracker = AbsSessionTracker()
        tracker.sample(sample(0, 0, speed = 2f))
        tracker.sample(sample(500, 1000, speed = 2f))
        tracker.sample(sample(1000, 500_000, speed = 2f)) // seek
        tracker.sample(sample(61_000, 620_000, speed = 2f)) // unobserved gap
        tracker.sample(sample(61_500, 620_000, false, speed = 2f)) // no advancement
        tracker.sample(sample(62_000, 620_000, true, speed = 2f))
        val result = tracker.sample(sample(62_500, 621_000, false, speed = 2f)).single()
        assertEquals(500L, result.listeningMs)
    }
    @Test fun accountChangeFlushesOnlyOriginalOwnersObservedTime() {
        val tracker = AbsSessionTracker()
        tracker.sample(sample(0))
        tracker.sample(sample(500))
        val old = tracker.sample(sample(1000, owner = "new-account")).single()
        assertEquals("account", old.owner)
        assertEquals(500L, old.listeningMs)
        val next = tracker.sample(sample(1500, playing = false, owner = "new-account")).single()
        assertNotEquals(old.id, next.id)
        assertEquals("new-account", next.owner)
    }
    @Test fun offlineQueueReopensAndStaleAcknowledgementCannotDeleteNewerCumulativeSnapshot() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val queue = AbsSessionQueue(context)
        val tracker = AbsSessionTracker()
        tracker.sample(sample(0))
        val saved = tracker.sample(sample(500, playing = false)).single()
        queue.save(saved)
        val reopened = AbsSessionQueue(context)
        assertEquals(saved, reopened.pending().single())
        val newer = saved.copy(updatedAt = saved.updatedAt + 500, listeningMs = 1000)
        reopened.save(newer)
        reopened.acknowledge(saved)
        assertEquals(newer, reopened.pending().single())
        reopened.acknowledge(newer)
        assertTrue(reopened.pending().isEmpty())
    }
    @Test fun nativePayloadUsesSecondsStableUuidAndPodcastEpisode() {
        val session = AbsRecordedSession("server", "owner", java.util.UUID.randomUUID().toString(), "show::episode",
            1000, 2000, 5000, 6000, 1000)
        val payload = session.payload()
        assertEquals("show", payload.getString("libraryItemId"))
        assertEquals("episode", payload.getString("episodeId"))
        assertEquals("podcast", payload.getString("mediaType"))
        assertEquals(1.0, payload.getDouble("timeListening"), 0.0)
        assertEquals(session.id, AbsRecordedSession.from(session.stored()).id)
        assertFalse(session.stored().toString().contains("token"))
    }
}
