package com.stillshelf.app.absstats

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Durable ledger separate from delivery retries. Playback updates only the changed
 * record rather than rewriting years of history. All mutations are transactional.
 */
@Singleton
internal class AbsLocalJournal @Inject constructor(@ApplicationContext context: Context) {
    private val db = AbsLocalDatabase.get(context)
    private val dao = db.records()
    private val legacy = File(context.noBackupFilesDir, "abs-local-history")
    private fun AbsLocalEntry.observation() = AbsLocalObservation.from(JSONObject(json))
    private suspend fun put(record: AbsLocalObservation) {
        dao.put(AbsLocalEntry(record.session.owner, record.session.id, record.stored().toString()))
    }
    /** Migration and marker commit together. Retained legacy files can never
     * resurrect history after the user subsequently clears it.
     */
    private suspend fun migrate(owner: String) {
        require(owner.matches(Regex("[a-f0-9]{64}")))
        if (dao.migrated(owner) != 0) return
        val target = AtomicFile(File(legacy, "$owner.json"))
        if (target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()) {
            val json = target.openRead().bufferedReader().use { JSONObject(it.readText()) }
            require(json.getInt("version") == 1)
            val rows = json.getJSONArray("records")
            for (i in 0 until rows.length()) {
                val record = AbsLocalObservation.from(rows.getJSONObject(i))
                require(record.session.owner == owner)
                put(newerAbsObservation(dao.one(owner, record.session.id)?.observation(), record))
            }
        }
        dao.mark(AbsLocalMigration(owner))
    }
    suspend fun observations(owner: String): List<AbsLocalObservation> = withContext(Dispatchers.IO) {
        db.withTransaction { migrate(owner); dao.all(owner).map { it.observation() } }
    }
    suspend fun save(observation: AbsLocalObservation) = withContext(Dispatchers.IO) { db.withTransaction {
        val owner = observation.session.owner
        migrate(owner)
        put(newerAbsObservation(dao.one(owner, observation.session.id)?.observation(), observation))
    } }
    suspend fun clear(owner: String) = withContext(Dispatchers.IO) { db.withTransaction {
        migrate(owner); dao.clear(owner)
    } }
    /** Caller verifies the destination account. Import never queues submissions. */
    suspend fun importVerified(preview: AbsLocalBackupPreview, owner: String, serverId: String) =
        withContext(Dispatchers.IO) { db.withTransaction {
            migrate(owner)
            preview.records.forEach { original ->
                val mapped = original.copy(session = original.session.copy(owner = owner, serverId = serverId))
                put(newerAbsObservation(dao.one(owner, mapped.session.id)?.observation(), mapped))
            }
        } }
    suspend fun acknowledge(session: AbsRecordedSession) = withContext(Dispatchers.IO) { db.withTransaction {
        migrate(session.owner)
        dao.one(session.owner, session.id)?.observation()?.let {
            put(it.copy(acknowledgedMs = maxOf(it.acknowledgedMs, session.listeningMs)))
        }
    } }
    /** Only a fresh COMPLETE native index may establish remote evidence. */
    suspend fun reconcile(owner: String, completeNative: JSONArray) = withContext(Dispatchers.IO) { db.withTransaction {
        migrate(owner)
        val times = mutableMapOf<String, Long>()
        for (i in 0 until completeNative.length()) {
            val native = completeNative.optJSONObject(i) ?: continue
            val id = native.optionalText("id") ?: continue
            val ms = native.nonnegative("timeListening")?.let { (it * 1000).toLong() } ?: continue
            times[id] = ms
        }
        for (entry in dao.all(owner)) {
            val record = entry.observation()
            val ms = times[entry.id] ?: continue
            if (ms > record.observedOnServerMs) put(record.copy(observedOnServerMs = ms))
        }
    } }
}
