package com.stillshelf.app.absstats

import org.json.JSONArray
import org.json.JSONObject

/** A durable observation, separate from the transient delivery queue. */
internal data class AbsLocalObservation(
    val session: AbsRecordedSession,
    val libraryId: String?,
    val title: String,
    val author: String,
    val date: String,
    val acknowledgedMs: Long = 0,
    val observedOnServerMs: Long = 0,
) {
    fun stored() = JSONObject().put("session", session.stored())
        .put("libraryId", libraryId ?: JSONObject.NULL).put("title", title).put("author", author)
        .put("date", date).put("acknowledgedMs", acknowledgedMs).put("observedOnServerMs", observedOnServerMs)

    fun statisticsRow(): JSONObject = session.payload().put("libraryId", libraryId ?: JSONObject.NULL)
        .put("displayTitle", title).put("displayAuthor", author).put("date", date)
        .put("mediaMetadata", JSONObject(session.metadataJson).put("title", title).put("authorName", author))

    companion object {
        fun from(json: JSONObject) = AbsLocalObservation(AbsRecordedSession.from(json.getJSONObject("session")),
            json.optionalText("libraryId"), json.getString("title"), json.getString("author"), json.getString("date"),
            json.getLong("acknowledgedMs"), json.getLong("observedOnServerMs"))
    }
}

internal data class AbsLocalMerge(val sessions: JSONArray, val additionalMs: Long)

/** Merge against ONE coherent native session snapshot, never a paginated fragment or summary total.
 * Once a revision has been seen remotely, its disappearance is a remote deletion/edit,
 * not an invitation to resurrect it. Calling code persists reconciliation evidence only
 * after fetching a complete, fresh server index.
 */
internal fun mergeAbsLocalHistory(native: JSONArray, local: List<AbsLocalObservation>, owner: String): AbsLocalMerge {
    val rows = linkedMapOf<String, JSONObject>()
    for (i in 0 until native.length()) native.optJSONObject(i)?.let { row ->
        row.optionalText("id")?.let { rows[it] = JSONObject(row.toString()) }
    }
    var additional = 0L
    local.filter { it.session.owner == owner }.forEach { observation ->
        val session = observation.session
        val remote = rows[session.id]
        val remoteMs = remote?.nonnegative("timeListening")?.let { (it * 1000).toLong() } ?: 0L
        // Evidence from an earlier complete index survives cache eviction/remote deletion.
        val represented = maxOf(remoteMs, observation.observedOnServerMs)
        if (session.listeningMs > represented) {
            val extra = session.listeningMs - represented
            additional += extra
            val replacement = observation.statisticsRow()
            if (remote != null) {
                // Keep verified native identity and richer server metadata. Add only the
                // measured unsynced remainder, even if the remote record was edited down.
                val combined = JSONObject(remote.toString()).put("timeListening", (remoteMs + extra) / 1000.0)
                rows[session.id] = combined
            } else {
                replacement.put("timeListening", extra / 1000.0)
                rows[session.id] = replacement
            }
        }
    }
    val sorted = rows.values.sortedWith(compareByDescending<JSONObject> { it.optLong("startedAt") }.thenBy { it.optString("id") })
    return AbsLocalMerge(JSONArray(sorted), additional)
}

/** Accept stale callbacks without ever rolling a cumulative observation backwards. */
internal fun newerAbsObservation(old: AbsLocalObservation?, next: AbsLocalObservation): AbsLocalObservation {
    if (old == null) return next
    require(old.session.owner == next.session.owner && old.session.serverId == next.session.serverId &&
        old.session.id == next.session.id && old.session.itemId == next.session.itemId &&
        old.session.startedAt == next.session.startedAt) { "Conflicting listening-session identity" }
    val winner = if (next.session.listeningMs > old.session.listeningMs ||
        (next.session.listeningMs == old.session.listeningMs && next.session.updatedAt > old.session.updatedAt)) next else old
    return winner.copy(libraryId = winner.libraryId ?: old.libraryId ?: next.libraryId,
        acknowledgedMs = maxOf(old.acknowledgedMs, next.acknowledgedMs),
        observedOnServerMs = maxOf(old.observedOnServerMs, next.observedOnServerMs))
}
