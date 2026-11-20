package com.stillshelf.app.scrobbling

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.security.MessageDigest
import java.time.*
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalListeningRepository @Inject constructor(private val database: LocalListeningDatabase, private val dao: LocalListeningDao) {
    private val lock = Mutex()
    private val mutableEpoch = MutableStateFlow(0L)
    val epoch = mutableEpoch.asStateFlow()
    val changes = dao.changes()
    val insightChanges = dao.insightChanges()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    fun storageFailure() { mutableError.value = "Local history could not be saved. Check available device storage." }

    suspend fun record(listen: LocalListen, epoch: Long) = withContext(Dispatchers.IO) { lock.withLock {
        // An already queued write must never resurrect history after an explicit deletion.
        if (epoch == mutableEpoch.value) database.withTransaction {
            dao.insert(listen)
            val genres = org.json.JSONArray(listen.genresJson)
            dao.genres((0 until genres.length()).map { LocalListenGenre(listen.id, genres.getString(it)) })
            dao.duration(listen.id, listen.listenedMs, listen.positionMs, listen.ended)
            mutableError.value = null
        }
    } }

    suspend fun resumable(session: String, track: String, position: Long): LocalListen? = withContext(Dispatchers.IO) {
        dao.latest(localIdentity(session), track)?.takeIf {
            !it.ended && it.positionMs != null && position > 1_000 && position >= it.positionMs - 2_000
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) { lock.withLock {
        database.withTransaction { dao.deleteAll() }
        mutableEpoch.value++
    } }

    suspend fun statistics(sessionKey: String?): ListeningStatistics = withContext(Dispatchers.IO) { database.withTransaction {
        val summary = dao.summary()
        val artistPage = chartPage(ListeningChart.ARTISTS, 0, sessionKey)
        val albumPage = chartPage(ListeningChart.ALBUMS, 0, sessionKey)
        val trackPage = chartPage(ListeningChart.TRACKS, 0, sessionKey)
        ListeningStatistics("Local Listening History · On this device", summary.count, summary.milliseconds / 1000,
            artistPage.entries, albumPage.entries, trackPage.entries, dao.recent().map { it.entry(sessionKey) },
            note = mutableError.value ?: "Only includes qualified listens recorded on this device while local recording was enabled.",
            moreCharts = listOf(ListeningChart.ARTISTS to artistPage, ListeningChart.ALBUMS to albumPage,
                ListeningChart.TRACKS to trackPage).filter { it.second.hasMore }.map { it.first }.toSet(),
            listeningTimeNote = "Observed playback time for recorded listens")
    } }

    private fun LocalListen.entry(sessionKey: String?, plays: Long? = null, kind: String = "track"): ListeningEntry {
            val sameLibrary = sessionKey != null && libraryKey == localIdentity(sessionKey)
            return ListeningEntry(title = if (kind == "artist") artist else if (kind == "album") album else title,
                subtitle = if (kind == "artist") "" else artist, plays = plays,
                timestampSeconds = if (plays == null) startedAtSeconds else null,
                artistId = artistId.takeIf { sameLibrary && kind == "artist" },
                albumId = albumId.takeIf { sameLibrary && kind != "artist" }, albumName = album,
                musicBrainzRecordingId = recordingMbid)
    }

    suspend fun chartPage(chart: ListeningChart, offset: Int, sessionKey: String?): ListeningChartPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        val (field, kind) = when (chart) {
            ListeningChart.ARTISTS -> "artistKey" to "artist"
            ListeningChart.ALBUMS -> "albumKey" to "album"
            ListeningChart.TRACKS -> "trackKey" to "track"
        }
        val rows = dao.rankings(SimpleSQLiteQuery(
            "SELECT l.*, ranked.plays FROM local_listens l JOIN " +
                "(SELECT MAX(rowid) AS representative, COUNT(*) AS plays FROM local_listens " +
                (if (kind == "album") "WHERE album != '' " else "") +
                "GROUP BY $field ORDER BY plays DESC, $field LIMIT 101 OFFSET ?) ranked ON l.rowid = ranked.representative ORDER BY ranked.plays DESC, l.$field",
            arrayOf(offset)
        ))
        ListeningChartPage(rows.take(100).map { it.listen.entry(sessionKey, it.plays, kind) }, rows.size > 100)
    }

    suspend fun trends(range: TrendRange, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ListeningTrends =
        withContext(Dispatchers.IO) { database.withTransaction {
            val first = dao.summary().first?.let { Instant.ofEpochSecond(it).atZone(zone).toLocalDate() }
                ?: return@withTransaction ListeningTrends(emptyList(), true, true, "No local listens recorded yet.")
            val start = maxOf(range.start(today) ?: first, first)
            val heatmapStart = maxOf(today.minusYears(1).plusDays(1), first)
            val from = minOf(start, heatmapStart).atStartOfDay(zone).toEpochSecond()
            val until = today.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val days = dao.days(from, until).associateBy { LocalDate.parse(it.date) }
            fun points(since: LocalDate): List<TrendPoint> {
                val count = ChronoUnit.DAYS.between(since, today).coerceAtLeast(-1)
                require(count <= 36600) { "History range is too large to display." }
                return (0..count).map { offset -> val date = since.plusDays(offset); val day = days[date]
                    TrendPoint(date, day?.count ?: 0, (day?.milliseconds ?: 0) / 1000) }
            }
            val selected = points(start)
            val active = days.filterValues { it.count > 0 }.keys
            var date = if (today in active) today else today.minusDays(1)
            var streak = 0
            while (date in active) { streak++; date = date.minusDays(1) }
            ListeningTrends(selected, true, true, "", streak = streak,
                hour = dao.activeHour(start.atStartOfDay(zone).toEpochSecond(), until), heatmap = points(heatmapStart),
                durationNote = "Observed playback time for recorded listens")
        } }

    suspend fun export(output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        database.withTransaction { LocalListeningExport.write(dao, output) }
    }
}

internal fun localIdentity(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
