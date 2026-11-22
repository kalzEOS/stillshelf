package com.stillshelf.app.absstats

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AbsLocalHistoryTest {
    @Test fun continuedNativeAcknowledgementsRoundTripWithoutInflatingRetainedLocalTime() = runTest {
        val journal = AbsLocalJournal(RuntimeEnvironment.getApplication())
        val row = observation(15_000)
        journal.save(row)
        journal.acknowledge(row.session.copy(listeningMs = 20_000, updatedAt = row.session.updatedAt + 5000))
        val saved = journal.observations(owner).single()
        val bytes = java.io.ByteArrayOutputStream()
        AbsLocalBackup.write(bytes, owner, listOf(saved))
        val restored = AbsLocalBackup.preview(bytes.toByteArray().inputStream()).records.single()
        assertEquals(15_000L, restored.session.listeningMs)
        assertEquals(20_000L, restored.acknowledgedMs)
        assertEquals(15.0, mergeAbsLocalHistory(JSONArray(), listOf(restored), owner).sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
    }
    @Test fun oversizedAndFractionalBackupsAreRejectedBeforeImport() {
        val tooLarge = object : java.io.InputStream() {
            private var remaining = 32 * 1024 * 1024 + 1
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(length, remaining)
                java.util.Arrays.fill(buffer, offset, offset + count, 32.toByte())
                remaining -= count
                return count
            }
        }
        try { AbsLocalBackup.preview(tooLarge); fail("Oversized backup must fail") }
        catch (_: IllegalArgumentException) { }
        val row = observation()
        val output = java.io.ByteArrayOutputStream()
        AbsLocalBackup.write(output, owner, listOf(row))
        val json = JSONObject(output.toString("UTF-8"))
        json.getJSONArray("records").getJSONObject(0).getJSONObject("session").put("listeningMs", 12.5)
        try { AbsLocalBackup.preview(json.toString().byteInputStream()); fail("Fractional milliseconds must fail") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun legacyMigrationIsAtomicAndCannotResurrectClearedRecords() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val row = observation()
        val directory = java.io.File(context.noBackupFilesDir, "abs-local-history").apply { mkdirs() }
        java.io.File(directory, "$owner.json").writeText(JSONObject().put("version", 1)
            .put("records", JSONArray().put(row.stored())).toString())
        val journal = AbsLocalJournal(context)
        assertEquals(row, journal.observations(owner).single())
        journal.clear(owner)
        assertTrue(AbsLocalJournal(context).observations(owner).isEmpty())
    }
    @Test fun durableOwnerUsesVerifiedUserAndServerRatherThanCredentialsOrNames() {
        assertEquals(absLocalOwner("server", "user"), absLocalOwner("server", "user"))
        assertNotEquals(absLocalOwner("server", "user"), absLocalOwner("other-server", "user"))
        assertNotEquals(absLocalOwner("server", "user"), absLocalOwner("server", "other-user"))
        assertNotEquals(absLocalOwner("ab", "c"), absLocalOwner("a", "bc"))
        assertTrue(absLocalOwner("server", "user").matches(Regex("[a-f0-9]{64}")))
    }
    private val owner = "a".repeat(64)
    private fun observation(ms: Long = 20_000, id: String = UUID.randomUUID().toString()) = AbsLocalObservation(
        AbsRecordedSession("server", owner, id, "show::rss-guid", 1_000, 21_000, 0, 20_000, ms),
        "podcasts", "Episode", "Host", "2026-01-01")
    private fun native(row: AbsLocalObservation, seconds: Double) = JSONArray().put(row.statisticsRow().put("timeListening", seconds))

    @Test fun cumulativeRemoteAndLocalVersionsCountOnlyTheirUnion() {
        val local = observation()
        val merged = mergeAbsLocalHistory(native(local, 10.0), listOf(local), owner)
        assertEquals(20.0, merged.sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
        assertEquals(10_000L, merged.additionalMs)
        val accepted = mergeAbsLocalHistory(native(local, 20.0), listOf(local), owner)
        assertEquals(20.0, accepted.sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
        assertEquals(0L, accepted.additionalMs)
    }
    @Test fun remoteDeletionAndReductionAreNotUndoneByOldLocalHistory() {
        val observed = observation().copy(observedOnServerMs = 20_000)
        assertEquals(0, mergeAbsLocalHistory(JSONArray(), listOf(observed), owner).sessions.length())
        val edited = mergeAbsLocalHistory(native(observed, 5.0), listOf(observed), owner)
        assertEquals(5.0, edited.sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
        val newer = observed.copy(session = observed.session.copy(listeningMs = 30_000))
        val result = mergeAbsLocalHistory(native(observed, 5.0), listOf(newer), owner)
        assertEquals(15.0, result.sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
    }
    @Test fun acknowledgementBeforeServerIndexRefreshDoesNotHideMeasuredTime() {
        val local = observation().copy(acknowledgedMs = 20_000)
        val result = mergeAbsLocalHistory(JSONArray(), listOf(local), owner)
        assertEquals(20_000L, result.additionalMs)
        assertEquals(20.0, result.sessions.getJSONObject(0).getDouble("timeListening"), 0.0)
    }
    @Test fun repeatedListensRemainSeparateAndForeignAccountsStayOut() {
        val first = observation()
        val second = observation().copy(session = observation().session.copy(owner = "b".repeat(64)))
        val result = mergeAbsLocalHistory(JSONArray(), listOf(first, observation(), second), owner)
        assertEquals(2, result.sessions.length())
        assertEquals(40_000L, result.additionalMs)
    }
    @Test fun journalReopensRetainsAcknowledgedTimeAndRejectsStaleRevisions() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val journal = AbsLocalJournal(context)
        val row = observation()
        journal.save(row)
        journal.acknowledge(row.session)
        journal.save(row.copy(session = row.session.copy(listeningMs = 10_000, updatedAt = 11_000)))
        val reopened = AbsLocalJournal(context)
        val saved = reopened.observations(owner).single()
        assertEquals(20_000L, saved.session.listeningMs)
        assertEquals(20_000L, saved.acknowledgedMs)
        reopened.reconcile(owner, native(row, 20.0))
        assertEquals(20_000L, reopened.observations(owner).single().observedOnServerMs)
        assertTrue(reopened.observations("b".repeat(64)).isEmpty())
    }
    @Test fun conflictingUuidCannotOverwriteAnotherItem() {
        val row = observation()
        try {
            newerAbsObservation(row, row.copy(session = row.session.copy(itemId = "different")))
            fail("Conflicting identity must fail")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun backupRestoreIsIdempotentAndDoesNotScheduleSubmissions() = runTest {
        val row = observation()
        val bytes = java.io.ByteArrayOutputStream()
        AbsLocalBackup.write(bytes, owner, listOf(row))
        val preview = AbsLocalBackup.preview(bytes.toByteArray().inputStream())
        val journal = AbsLocalJournal(RuntimeEnvironment.getApplication())
        val destination = "c".repeat(64)
        journal.importVerified(preview, destination, "new-install-server")
        journal.importVerified(preview, destination, "new-install-server")
        val restored = journal.observations(destination).single()
        assertEquals(row.session.id, restored.session.id)
        assertEquals(row.session.listeningMs, restored.session.listeningMs)
        assertEquals("new-install-server", restored.session.serverId)
        assertEquals(destination, restored.session.owner)
        assertTrue(AbsSessionQueue(RuntimeEnvironment.getApplication()).pending().isEmpty())
    }
    @Test fun invalidBackupAndConflictingImportLeaveHistoryIntact() = runTest {
        val journal = AbsLocalJournal(RuntimeEnvironment.getApplication())
        val row = observation()
        journal.save(row)
        try {
            journal.importVerified(AbsLocalBackupPreview(owner, listOf(row.copy(session = row.session.copy(itemId = "other")))), owner, "server")
            fail("Conflict must not replace history")
        } catch (_: IllegalArgumentException) { }
        assertEquals(row, journal.observations(owner).single())
        val data = java.io.ByteArrayOutputStream()
        AbsLocalBackup.write(data, owner, listOf(row))
        val malformed = JSONObject(data.toString("UTF-8"))
        malformed.getJSONArray("records").getJSONObject(0).getJSONObject("session").put("listeningMs", -1)
        try {
            AbsLocalBackup.preview(malformed.toString().byteInputStream())
            fail("Invalid time must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(row, journal.observations(owner).single())
    }
}
