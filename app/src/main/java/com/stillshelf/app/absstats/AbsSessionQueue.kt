package com.stillshelf.app.absstats

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Private durable snapshots, with no URLs or credentials. Acknowledgements cannot erase a newer snapshot. */
@Singleton
class AbsSessionQueue @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.noBackupFilesDir, "abs-pending-sessions")
    private val mutex = Mutex()
    internal val syncMutex = Mutex()
    // Short disk/clear operations must never wait for the uploader's network lock.
    internal val stateMutex = Mutex()
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        directory.mkdirs()
        return AtomicFile(File(directory, "$id.json"))
    }
    internal suspend fun save(session: AbsRecordedSession) = withContext(Dispatchers.IO) { mutex.withLock {
        val target = file(session.id)
        val stream = target.startWrite()
        try { stream.write(session.stored().toString().toByteArray()); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
    } }
    internal suspend fun pending(): List<AbsRecordedSession> = withContext(Dispatchers.IO) { mutex.withLock {
        directory.listFiles().orEmpty().filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
            .map { it.name.removeSuffix(".bak").removeSuffix(".json") }.distinct().mapNotNull { id ->
                runCatching { file(id).openRead().bufferedReader().use { AbsRecordedSession.from(JSONObject(it.readText())) } }.getOrNull()
            }.sortedBy { it.updatedAt }
    } }
    internal suspend fun acknowledge(session: AbsRecordedSession) = withContext(Dispatchers.IO) { mutex.withLock {
        val target = file(session.id)
        val current = runCatching { target.openRead().bufferedReader().use { AbsRecordedSession.from(JSONObject(it.readText())) } }.getOrNull()
        if (current == session) target.delete()
    } }
    internal suspend fun removeOwner(owner: String) {
        pending().filter { it.owner == owner }.forEach { acknowledge(it) }
    }
}
