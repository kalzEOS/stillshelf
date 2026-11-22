package com.stillshelf.app.absstats

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

internal data class AbsPlaybackSample(val serverId: String, val owner: String, val itemId: String,
    val positionMs: Long, val playing: Boolean, val speed: Float, val elapsedMs: Long, val wallMs: Long,
    val libraryId: String? = null, val title: String = "", val author: String = "", val metadataJson: String = "{}",
    val retainLocally: Boolean? = null)

internal data class AbsRecordedSession(val serverId: String, val owner: String, val id: String,
    val itemId: String, val startedAt: Long, val updatedAt: Long, val startMs: Long,
    val positionMs: Long, val listeningMs: Long, val libraryId: String? = null,
    val title: String = "", val author: String = "", val metadataJson: String = "{}", val retainLocally: Boolean? = null) {
    fun payload(): JSONObject {
        val parts = itemId.split("::", limit = 2)
        return JSONObject().put("id", id).put("libraryItemId", parts[0])
            .put("episodeId", parts.getOrNull(1) ?: JSONObject.NULL)
            .put("mediaType", if (parts.size == 2) "podcast" else "book")
            .put("mediaPlayer", "StillShelf").put("playMethod", 3)
            .put("startedAt", startedAt).put("updatedAt", updatedAt)
            .put("startTime", startMs / 1000.0).put("currentTime", positionMs / 1000.0)
            .put("timeListening", listeningMs / 1000.0)
    }
    fun stored(): JSONObject = JSONObject().put("serverId", serverId).put("owner", owner)
        .put("id", id).put("itemId", itemId).put("startedAt", startedAt).put("updatedAt", updatedAt)
        .put("startMs", startMs).put("positionMs", positionMs).put("listeningMs", listeningMs)
        .put("libraryId", libraryId ?: JSONObject.NULL).put("title", title).put("author", author)
        .put("metadata", JSONObject(metadataJson))
        .put("retainLocally", retainLocally ?: JSONObject.NULL)
    companion object {
        fun from(json: JSONObject) = AbsRecordedSession(json.getString("serverId"), json.getString("owner"),
            json.getString("id"), json.getString("itemId"), json.getLong("startedAt"), json.getLong("updatedAt"),
            json.getLong("startMs"), json.getLong("positionMs"), json.getLong("listeningMs"),
            json.optionalText("libraryId"), json.optString("title"), json.optString("author"), json.optJSONObject("metadata")?.toString() ?: "{}",
            if (json.has("retainLocally") && !json.isNull("retainLocally")) json.getBoolean("retainLocally") else null)
    }
}

/** Counts observed wall time while audio advances; seeks, buffering, pauses and sampling gaps add no time. */
internal class AbsSessionTracker {
    private var previous: AbsPlaybackSample? = null
    private var session: AbsRecordedSession? = null
    private var persistedMs = 0L
    fun sample(next: AbsPlaybackSample?): List<AbsRecordedSession> {
        val output = mutableListOf<AbsRecordedSession>()
        val old = previous
        // Close yesterday's snapshot before advancing its timestamp into today. ABS updates
        // an existing local session's date from updatedAt, including all cumulative time.
        val changedDay = session != null && next != null && Instant.ofEpochMilli(session!!.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() !=
            Instant.ofEpochMilli(next.wallMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val same = !changedDay && old != null && next != null && old.serverId == next.serverId && old.owner == next.owner && old.itemId == next.itemId
        if (same) {
            val dt = next!!.elapsedMs - old!!.elapsedMs
            val movement = next.positionMs - old.positionMs
            val speed = old.speed.toDouble().takeIf { it.isFinite() && it > 0 } ?: 1.0
            if (old.playing && dt in 1..2_000 && movement > 0 && movement <= dt * speed + 1_000) {
                session = (session ?: AbsRecordedSession(old.serverId, old.owner, UUID.randomUUID().toString(),
                    old.itemId, old.wallMs, old.wallMs, old.positionMs, old.positionMs, 0,
                    old.libraryId, old.title, old.author, old.metadataJson, old.retainLocally)).let {
                    it.copy(updatedAt = next.wallMs, positionMs = next.positionMs, listeningMs = it.listeningMs + dt)
                }
            }
        }
        val current = session
        val end = !same || next?.playing != true || changedDay
        if (current != null && (end || current.listeningMs - persistedMs >= 15_000)) {
            output += current
            persistedMs = current.listeningMs
        }
        if (end) { session = null; persistedMs = 0 }
        previous = next
        return output
    }
}
