package com.stillshelf.app.absstats

import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

internal data class AbsLocalBackupPreview(val owner: String, val records: List<AbsLocalObservation>)

/** Parse fully before allowing any import transaction. Contains private history,
 * never authentication tokens; parsing has no network or submission side effects.
 */
internal object AbsLocalBackup {
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_RECORDS = 50_000
    private fun integer(json: JSONObject, field: String): Long {
        val raw = json.get(field)
        require(raw is Number && raw.toString().matches(Regex("[0-9]+"))) { "Invalid backup number" }
        return raw.toString().toLong()
    }
    fun write(output: OutputStream, owner: String, records: List<AbsLocalObservation>) {
        require(records.size <= MAX_RECORDS && records.all { it.session.owner == owner })
        val bytes = JSONObject().put("format", "stillshelf-abs-local-history").put("version", 1)
            .put("owner", owner).put("records", JSONArray(records.map { it.stored() }))
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "This history is too large for this backup format" }
        output.write(bytes)
    }
    fun preview(input: InputStream): AbsLocalBackupPreview {
        val buffer = ByteArray(8192)
        val collected = java.io.ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(collected.size() + count <= MAX_BYTES) { "Backup is too large" }
            collected.write(buffer, 0, count)
        }
        val bytes = collected.toByteArray()
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getString("format") == "stillshelf-abs-local-history" && integer(root, "version") == 1L)
        val owner = root.getString("owner")
        require(owner.matches(Regex("[a-f0-9]{64}")))
        val array = root.getJSONArray("records")
        require(array.length() <= MAX_RECORDS)
        val ids = mutableSetOf<String>()
        val records = (0 until array.length()).map { i ->
            val data = array.getJSONObject(i)
            val captured = data.getJSONObject("session")
            listOf("startedAt", "updatedAt", "startMs", "positionMs", "listeningMs").forEach { integer(captured, it) }
            listOf("acknowledgedMs", "observedOnServerMs").forEach { integer(data, it) }
            val row = AbsLocalObservation.from(data)
            val session = row.session
            require(session.owner == owner && ids.add(session.id) && java.util.UUID.fromString(session.id).toString() == session.id)
            require(session.serverId.isNotBlank() && session.itemId.isNotBlank())
            require(session.startedAt >= 0 && session.updatedAt >= session.startedAt &&
                session.updatedAt <= System.currentTimeMillis() + 86_400_000L)
            require(session.listeningMs in 0..366L * 86_400_000 && session.startMs >= 0 && session.positionMs >= 0)
            // Native reporting can continue after local retention is switched off.
            // Its acknowledgement may legitimately exceed the retained observation.
            require(row.acknowledgedMs in 0..366L * 86_400_000 && row.observedOnServerMs in 0..366L * 86_400_000)
            require(listOf(session.serverId, session.itemId, row.title, row.author, row.libraryId.orEmpty())
                .all { it.length <= 16_384 && '\u0000' !in it })
            LocalDate.parse(row.date)
            row
        }
        return AbsLocalBackupPreview(owner, records)
    }
}
