package com.stillshelf.app.scrobbling

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Streams bounded pages; no credentials, server addresses, stream URLs or artwork URLs are exported. */
internal object LocalListeningExport {
    suspend fun write(dao: LocalListeningDao, output: OutputStream) {
        val last = dao.lastRow()
        ZipOutputStream(output.buffered()).use { zip ->
            suspend fun rows(name: String, serialize: (LocalListen) -> JSONObject) {
                zip.putNextEntry(ZipEntry(name))
                var after = 0L
                while (true) {
                    val page = dao.exportPage(after, last)
                    if (page.isEmpty()) break
                    page.forEach { zip.write((serialize(it).toString() + "\n").toByteArray(Charsets.UTF_8)) }
                    after = dao.rowNumber(page.last().id)
                }
                zip.closeEntry()
            }
            rows("stillshelf-history.jsonl", ::native)
            rows("listens.jsonl", ::listenBrainz)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("format", "stillshelf-local-listening-history").put("version", 1)
                .put("listen_count", dao.summary().count).put("timestamp_unit", "unix_seconds")
                .put("listening_duration_unit", "milliseconds").toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(README.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    internal fun native(row: LocalListen) = JSONObject().put("id", row.id).put("library_key", row.libraryKey)
        .put("track_id", row.trackId).put("artist_id", row.artistId).put("album_id", row.albumId)
        .put("title", row.title).put("artist", row.artist).put("album", row.album).put("recording_mbid", row.recordingMbid)
        .put("started_at", row.startedAtSeconds).put("track_duration_seconds", row.durationSeconds).put("listened_ms", row.listenedMs)
        .put("position_ms", row.positionMs).put("ended", row.ended)
        .put("genres", org.json.JSONArray(row.genresJson)).put("release_year", row.releaseYear)

    internal fun listenBrainz(row: LocalListen): JSONObject {
        val info = JSONObject().put("listening_from", "StillShelf").put("media_player", "StillShelf")
        row.durationSeconds?.let { info.put("duration_ms", it.toLong() * 1000) }
        row.recordingMbid?.let { info.put("recording_mbid", it) }
        val metadata = JSONObject().put("artist_name", row.artist).put("track_name", row.title).put("additional_info", info)
        if (row.album.isNotBlank()) metadata.put("release_name", row.album)
        return JSONObject().put("listened_at", row.startedAtSeconds).put("track_metadata", metadata)
    }

    private const val README = """StillShelf local listening history, version 1

stillshelf-history.jsonl is the complete portable record of qualified local listens.
Each UTF-8 line is a JSON object. started_at is integer UNIX seconds; listened_ms
is observed playing time, excluding pauses, seeks and unobserved process suspension.
Track lengths are separate from observed listening time. Duration checkpoints may
lag by up to five seconds if the process is killed. Library keys are opaque hashes;
no server credentials or addresses are included. IDs are local library references.

listens.jsonl uses ListenBrainz's documented listened_at / track_metadata schema:
https://listenbrainz.readthedocs.io/en/latest/users/json.html
https://listenbrainz.readthedocs.io/en/latest/users/api/core.html
An import tool can read each line and submit batches with listen_type=import.
This ZIP is a StillShelf archive, not a verified ListenBrainz account export.
Direct ZIP upload compatibility is not guaranteed. Nothing is uploaded by exporting.

Keep this archive private. It contains your listening history. Export regularly:
uninstalling StillShelf or losing this device may remove the on-device history.
This version exports history; restoring an archive into StillShelf is not supported.
"""
}
