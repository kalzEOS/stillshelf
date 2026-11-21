package com.stillshelf.app.scrobbling

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class LocalHistoryImportException(message: String) : Exception(message)
data class LocalHistoryImportResult(val added: Long, val skipped: Long)

/** Reads only the native record in a StillShelf backup; nothing is extracted or submitted. */
internal object LocalListeningImport {
    private fun valid(condition: Boolean) { if (!condition) throw LocalHistoryImportException("This file is not a valid supported StillShelf history backup. No history was imported.") }
    suspend fun read(file: File, accept: suspend (LocalListen) -> Boolean): LocalHistoryImportResult {
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                val names = mutableSetOf<String>()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    valid(names.size < 16 && names.add(entries.nextElement().name))
                }
                val manifest = zip.getEntry("manifest.json")
                val records = zip.getEntry("stillshelf-history.jsonl")
                valid(manifest != null && records != null && !manifest.isDirectory && !records.isDirectory)
                val header = zip.getInputStream(manifest).use { stream ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (true) {
                        val size = stream.read(buffer)
                        if (size < 0) break
                        valid(bytes.size() + size <= 16_384)
                        bytes.write(buffer, 0, size)
                    }
                    JSONObject(bytes.toString("UTF-8"))
                }
                valid(header.getString("format") == "stillshelf-local-listening-history" && number(header, "version") == 1L &&
                    header.getString("timestamp_unit") == "unix_seconds" && header.getString("listening_duration_unit") == "milliseconds")
                val expected = number(header, "listen_count")
                valid(expected in 0..1_000_000)
                var added = 0L; var count = 0L; var characters = 0L
                zip.getInputStream(records).bufferedReader(Charsets.UTF_8).use { reader ->
                    // Bound each line before allocating/parsing it, including highly compressed input.
                    val line = StringBuilder()
                    suspend fun consume() {
                        if (line.isEmpty()) return
                        currentCoroutineContext().ensureActive()
                        valid(++count <= expected)
                        if (accept(parse(JSONObject(line.toString())))) added++
                        line.setLength(0)
                    }
                    while (true) {
                        val ch = reader.read()
                        if (ch == -1) { consume(); break }
                        valid(++characters <= 512L * 1024 * 1024)
                        if (ch == 10) consume() else {
                            valid(line.length < 65_536)
                            if (ch != 13) line.append(ch.toChar())
                        }
                    }
                }
                valid(count == expected)
                return LocalHistoryImportResult(added, count - added)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: LocalHistoryImportException) { throw e }
        catch (_: org.json.JSONException) { throw LocalHistoryImportException("The backup contains invalid history data. No history was imported.") }
        catch (_: java.util.zip.ZipException) { throw LocalHistoryImportException("The backup ZIP could not be read. No history was imported.") }
        catch (_: java.io.IOException) { throw LocalHistoryImportException("The backup could not be read completely. No history was imported.") }
    }
    private fun number(json: JSONObject, key: String): Long {
        val raw = json.get(key)
        valid(raw is Number && raw.toString().matches(Regex("[0-9]+")))
        return raw.toString().toLongOrNull() ?: throw LocalHistoryImportException("The backup contains an invalid number. No history was imported.")
    }
    private fun text(json: JSONObject, key: String, required: Boolean = false): String {
        val value = json.get(key)
        valid(value is String)
        return (value as String).also { valid(it.length <= 4096 && (!required || it.isNotBlank()) && '\u0000' !in it) }
    }
    private fun optionalText(json: JSONObject, key: String) = if (!json.has(key) || json.isNull(key)) null else text(json, key)
    private fun optionalNumber(json: JSONObject, key: String) = if (!json.has(key) || json.isNull(key)) null else number(json, key)
    private fun key(vararg values: String) = localIdentity(values.joinToString("") { val v = it.trim().lowercase(Locale.ROOT); "${v.length}:$v" })
    private fun parse(json: JSONObject): LocalListen {
        val title = text(json, "title", true); val artist = text(json, "artist", true); val album = text(json, "album")
        val timestamp = number(json, "started_at")
        valid(timestamp in 0..java.time.Instant.now().epochSecond + 86_400)
        val duration = optionalNumber(json, "track_duration_seconds")
        valid(duration == null || duration in 1..Int.MAX_VALUE.toLong())
        val observed = number(json, "listened_ms")
        valid(observed <= 366L * 86_400_000)
        val position = optionalNumber(json, "position_ms")
        val year = optionalNumber(json, "release_year")
        valid(year == null || year in 1000..java.time.LocalDate.now().year.toLong() + 1)
        val genres = if (!json.has("genres")) JSONArray() else json.getJSONArray("genres")
        valid(genres.length() <= 32)
        val tags = (0 until genres.length()).map { i -> genres.get(i).also { valid(it is String && it.length <= 4096) } as String }
            .map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase(Locale.ROOT) }
        return LocalListen(text(json, "id", true), text(json, "library_key", true), text(json, "track_id", true),
            optionalText(json, "artist_id"), optionalText(json, "album_id"), title, artist, album,
            optionalText(json, "recording_mbid"), duration?.toInt(), timestamp, observed,
            key(artist), key(artist, album), key(artist, album, title), position, ended = true,
            genresJson = JSONArray(tags).toString(), releaseYear = year?.toInt())
    }
}
