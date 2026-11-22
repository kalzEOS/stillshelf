package com.stillshelf.app.absstats

import android.content.Context
import android.util.AtomicFile
import com.stillshelf.app.core.database.ServerDao
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.datastore.SecureTokenStorage
import com.stillshelf.app.core.network.ActiveServerEndpointResolver
import com.stillshelf.app.core.network.authorizationHeaderValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray

internal class AbsStatisticsAccount(val key: String, val serverId: String, val token: String)
internal fun absAccountKey(serverId: String, token: String): String = MessageDigest.getInstance("SHA-256")
    .digest("$serverId\n$token".toByteArray()).joinToString("") { "%02x".format(it) }
data class AbsStatisticsSnapshot(val statistics: AbsListeningStatistics? = null, val year: AbsYearSummary? = null,
    val updatedAt: Long? = null, val cached: Boolean = false, val partial: Boolean = false,
    val podcastHistoryIncomplete: Boolean = false, val localAdditionalMs: Long = 0,
    val localOnly: Boolean = false, val localMergePending: Boolean = false)

@Singleton
class AbsStatisticsRepository @Inject constructor(private val preferences: SessionPreferences,
    private val servers: ServerDao, private val secrets: SecureTokenStorage,
    private val endpoints: ActiveServerEndpointResolver, client: OkHttpClient,
    @ApplicationContext private val context: Context) {
    private val http = client.newBuilder().callTimeout(25, TimeUnit.SECONDS).build()
    internal val localJournal = AbsLocalJournal(context)
    internal val localAccounts = AbsLocalAccounts(context)
    internal fun accountDisplayName(key: String): String? = localAccounts.displayName(key)
    private val identityAttempts = mutableMapOf<String, Long>()
    internal suspend fun retainLocal(session: AbsRecordedSession) {
        if (session.retainLocally == false || !localAccounts.recording(session.owner) || session.startedAt <= localAccounts.clearedAt(session.owner)) return
        val date = java.time.Instant.ofEpochMilli(session.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        localJournal.save(AbsLocalObservation(session, session.libraryId, session.title.ifBlank { "Listening session" }, session.author, date))
    }
    internal suspend fun checkpoint(queue: AbsSessionQueue, session: AbsRecordedSession) = queue.stateMutex.withLock {
        if (session.startedAt > localAccounts.clearedAt(session.owner)) {
            retainLocal(session)
            queue.save(session)
        }
    }
    internal suspend fun localRecords(key: String): List<AbsLocalObservation> {
        val owner = localAccounts.owner(key)
        return localAccounts.aliases(key).flatMap { localJournal.observations(it) }
            .map { it.copy(session = it.session.copy(owner = owner)) }
            .groupBy { it.session.id }.values.map { rows -> rows.reduce { a, b -> newerAbsObservation(a, b) } }
    }
    private suspend fun verifyLocalAccount(account: AbsStatisticsAccount) = withContext(Dispatchers.IO) {
        if (localAccounts.verified(account.key) && localAccounts.displayName(account.key) != null) return@withContext
        val server = servers.getById(account.serverId) ?: error("No server")
        val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
        val me = withTimeoutOrNull(5_000) {
            JSONObject(http.absRequest(Request.Builder().url("$base/api/me")
                .header("Authorization", authorizationHeaderValue(account.token)).build()))
        } ?: error("No authenticated user identity")
        val id = me.optionalText("id") ?: error("No authenticated user identity")
        check(accountForServer(account.serverId)?.key == account.key)
        localAccounts.bind(account.key, account.serverId, id, me.optionalText("username"))
    }
    internal suspend fun localToolsAccount(expectedKey: String? = null): Pair<AbsStatisticsAccount, String> {
        val account = account() ?: error("Sign in to Audiobookshelf first")
        check(expectedKey == null || expectedKey == account.key) { "Account changed; open these controls again" }
        verifyLocalAccount(account)
        check(account()?.key == account.key) { "Account changed" }
        val serverName = servers.getById(account.serverId)?.name ?: "Audiobookshelf"
        return account to "$serverName · ${localAccounts.displayName(account.key) ?: "your logged-in account"}"
    }
    internal suspend fun resolveRecordingAccount(serverId: String): AbsStatisticsAccount? {
        val account = accountForServer(serverId) ?: return null
        try { verifyLocalAccount(account) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep the conservative decision and normal server reporting. */ }
        return account.takeIf { accountForServer(serverId)?.key == it.key }
    }
    internal suspend fun clearLocal(key: String) {
        val account = account()?.takeIf { it.key == key } ?: error("Account changed")
        // Use the injected queue's shared synchronization through the application entry point.
        val sharedQueue = dagger.hilt.android.EntryPointAccessors.fromApplication(context, AbsSessionSyncEntryPoint::class.java).absSessionQueue()
        sharedQueue.syncMutex.withLock {
          sharedQueue.stateMutex.withLock {
            check(account()?.key == account.key)
            val aliases = localAccounts.aliases(key)
            localAccounts.markCleared(key)
            aliases.forEach { sharedQueue.removeOwner(it); localJournal.clear(it) }
          }
        }
    }
    private suspend fun combined(key: String, native: JSONArray): AbsLocalMerge =
        mergeAbsLocalHistory(native, localRecords(key), localAccounts.owner(key))
    private val lock = Mutex()
    private val cacheLock = Any()
    private val coverageAttempts = mutableMapOf<String, Long>()
    private val episodeIds = java.util.concurrent.ConcurrentHashMap<Triple<String, String, String>, Pair<String, Long>>()
    private fun incompletePodcastHistory(key: String, library: String?) = podcastGaps(read(key, "podcast-coverage")?.optJSONObject("data"))
        .any { library == null || it.libraryId == library }
    internal suspend fun account(): AbsStatisticsAccount? {
        val id = preferences.state.first().activeServerId ?: return null
        return accountForServer(id)
    }
    internal suspend fun accountForServer(id: String): AbsStatisticsAccount? {
        val token = secrets.getToken(id)?.takeIf { it.isNotBlank() } ?: return null
        return AbsStatisticsAccount(absAccountKey(id, token), id, token)
    }
    internal fun scopes() = preferences.state.map { account()?.key to it.activeLibraryId }.distinctUntilChanged()
    private fun file(key: String, part: String) = AtomicFile(File(File(context.noBackupFilesDir, "abs-listening-statistics").apply { mkdirs() }, "$key-$part.json"))
    private fun read(key: String, part: String): JSONObject? = synchronized(cacheLock) { runCatching {
        file(key, part).openRead().bufferedReader().use { JSONObject(it.readText()) }
    }.getOrNull() }
    private fun write(key: String, part: String, data: JSONObject): JSONObject = synchronized(cacheLock) {
        val wrapped = JSONObject().put("updatedAt", System.currentTimeMillis()).put("data", data)
        val target = file(key, part)
        val stream = target.startWrite()
        try { stream.write(wrapped.toString().toByteArray()); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        wrapped
    }
    internal suspend fun cached(key: String, year: Int, libraryId: String? = null): AbsStatisticsSnapshot = withContext(Dispatchers.IO) {
        val local = localRecords(key)
        if (local.isNotEmpty()) {
            val index = read(key, "session-index")
            val native = index?.optJSONObject("data")?.optJSONArray("sessions")
            if (native == null && libraryId == null) {
                val core = read(key, "all")
                val statistics = core?.optJSONObject("data")?.let { runCatching { parseAbsStatistics(it) }.getOrNull() }
                if (statistics != null) {
                    val annual = read(key, year.toString())?.optJSONObject("data")?.let { runCatching { parseAbsYear(it, year) }.getOrNull() }
                    // A summary alone cannot identify overlapping UUIDs. Preserve it
                    // and retain local records separately until a complete index arrives.
                    return@withContext AbsStatisticsSnapshot(statistics, annual, core.nonnegative("updatedAt")?.toLong(),
                        cached = true, partial = true, podcastHistoryIncomplete = incompletePodcastHistory(key, null),
                        localMergePending = true)
                }
            }
            val relevant = local.filter { libraryId == null || it.libraryId == libraryId }
            val merge = mergeAbsLocalHistory(native ?: JSONArray(), relevant, localAccounts.owner(key))
            val data = absLibraryStatistics(merge.sessions, libraryId, year)
            val finished = if (libraryId == null) read(key, year.toString())?.optJSONObject("data")?.let {
                runCatching { parseAbsYear(it, year).booksFinished }.getOrNull()
            } else null
            return@withContext AbsStatisticsSnapshot(data.first, data.second.copy(booksFinished = finished),
                index?.nonnegative("updatedAt")?.toLong(), cached = native != null, partial = native == null,
                podcastHistoryIncomplete = incompletePodcastHistory(key, libraryId), localAdditionalMs = merge.additionalMs,
                localOnly = native == null)
        }
        if (libraryId != null) {
            val index = read(key, "session-index")
            val sessions = index?.optJSONObject("data")?.optJSONArray("sessions")
            val data = sessions?.let { runCatching { absLibraryStatistics(it, libraryId, year) }.getOrNull() }
            return@withContext AbsStatisticsSnapshot(data?.first, data?.second, index?.nonnegative("updatedAt")?.toLong(), cached = data != null,
                podcastHistoryIncomplete = incompletePodcastHistory(key, libraryId))
        }
        val core = read(key, "all"); val annual = read(key, year.toString())
        AbsStatisticsSnapshot(core?.optJSONObject("data")?.let { runCatching { parseAbsStatistics(it) }.getOrNull() },
            annual?.optJSONObject("data")?.let { runCatching { parseAbsYear(it, year) }.getOrNull() },
            core?.nonnegative("updatedAt")?.toLong(), cached = core != null, podcastHistoryIncomplete = incompletePodcastHistory(key, null))
    }
    internal suspend fun refresh(key: String, year: Int, force: Boolean, libraryId: String? = null): AbsStatisticsSnapshot = withContext(Dispatchers.IO) { lock.withLock {
        val active = account()?.takeIf { it.key == key } ?: throw CancellationException("Account changed")
        val now = System.currentTimeMillis()
        if ((!localAccounts.verified(key) || localAccounts.displayName(key) == null) && now - (identityAttempts[key] ?: 0) !in 0..300_000) {
            identityAttempts[key] = now
            try { verifyLocalAccount(active) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Offline records remain credential-isolated. */ }
        }
        if (localRecords(key).isNotEmpty()) {
            val ok = try { completeSessionIndex(key, force); true }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (ok) refreshPodcastCoverage(key, force)
            if (libraryId == null) refreshNativeYear(active, year, force)
            val result = cached(key, year, libraryId)
            return@withLock result.copy(cached = !ok, partial = !ok || result.localOnly || result.localMergePending)
        }
        if (libraryId != null) {
            val ok = try { completeSessionIndex(key, force); true }
                catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            if (ok) refreshPodcastCoverage(key, force)
            return@withLock cached(key, year, libraryId).copy(cached = !ok, partial = !ok)
        }
        val account = account()?.takeIf { it.key == key } ?: throw CancellationException("Account changed")
        val server = servers.getById(account.serverId) ?: error("No server")
        val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
        suspend fun fetch(part: String, path: String, validate: (JSONObject) -> Unit): Boolean {
            val saved = read(key, part)
            val timestamp = saved?.nonnegative("updatedAt")?.toLong() ?: 0
            val valid = saved?.optJSONObject("data")?.let { runCatching { validate(it) }.isSuccess } == true
            if (!force && valid && System.currentTimeMillis() - timestamp in 0..300_000) return true
            return try {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder().url("$base/api/$path").header("Authorization", authorizationHeaderValue(account.token)).get().build()
                val data = JSONObject(http.absRequest(request))
                validate(data)
                currentCoroutineContext().ensureActive()
                if (account()?.key != key) throw CancellationException("Account changed")
                write(key, part, data)
                true
            } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        }
        val coreOk = fetch("all", "me/listening-stats") { parseAbsStatistics(it) }
        val yearOk = fetch(year.toString(), "me/stats/year/$year") { parseAbsYear(it, year) }
        if (coreOk) refreshPodcastCoverage(key, force)
        val result = cached(key, year)
        result.copy(cached = !coreOk, partial = !coreOk || !yearOk)
    } }

    private suspend fun refreshNativeYear(account: AbsStatisticsAccount, year: Int, force: Boolean) {
        val saved = read(account.key, year.toString())
        if (!force && saved != null && System.currentTimeMillis() - saved.optLong("updatedAt") in 0..300_000) return
        try {
            val server = servers.getById(account.serverId) ?: return
            val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
            val data = JSONObject(http.absRequest(Request.Builder().url("$base/api/me/stats/year/$year")
                .header("Authorization", authorizationHeaderValue(account.token)).build()))
            parseAbsYear(data, year)
            if (account()?.key == account.key) write(account.key, year.toString(), data)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Preserve native-only completion counts from cache. */ }
    }

    internal suspend fun history(key: String, page: Int, libraryId: String? = null): AbsHistoryPage = withContext(Dispatchers.IO) {
        require(page >= 0)
        if (localRecords(key).isNotEmpty()) return@withContext lock.withLock {
            var offline = false
            val index = try { completeSessionIndex(key, false) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { offline = true; read(key, "session-index")?.optJSONObject("data") }
            val merged = combined(key, index?.optJSONArray("sessions") ?: JSONArray()).sessions
            val rows = (0 until merged.length()).map { merged.getJSONObject(it) }
                .filter { libraryId == null || it.optionalText("libraryId") == libraryId }
            AbsHistoryPage(parseAbsSessions(JSONArray(rows.drop(page * 30).take(30))), rows.size,
                (page + 1L) * 30 < rows.size, cached = offline)
        }
        if (libraryId != null) return@withContext lock.withLock {
            var offline = false
            val index = try { completeSessionIndex(key, false) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { offline = true; read(key, "session-index")?.optJSONObject("data") ?: throw e }
            val rows = index.getJSONArray("sessions")
            val filtered = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                .filter { it.optionalText("libraryId") == libraryId }
            AbsHistoryPage(parseAbsSessions(JSONArray(filtered.drop(page * 30).take(30))), filtered.size,
                (page + 1L) * 30 < filtered.size, cached = offline)
        }
        val account = account()?.takeIf { it.key == key } ?: throw CancellationException("Account changed")
        val part = "history-$page"
        try {
            val server = servers.getById(account.serverId) ?: error("No server")
            val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
            val request = Request.Builder().url("$base/api/me/listening-sessions?itemsPerPage=30&page=$page")
                .header("Authorization", authorizationHeaderValue(account.token)).build()
            val json = JSONObject(http.absRequest(request))
            val parsed = parseAbsHistory(json)
            if (account()?.key != key) throw CancellationException("Account changed")
            write(key, part, json)
            parsed
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { read(key, part)?.optJSONObject("data")?.let { parseAbsHistory(it).copy(cached = true) } ?: throw e }
    }

    private suspend fun refreshPodcastCoverage(key: String, force: Boolean) {
        val now = System.currentTimeMillis()
        val saved = read(key, "podcast-coverage")
        val attempted = maxOf(saved?.nonnegative("updatedAt")?.toLong() ?: 0, coverageAttempts[key] ?: 0)
        if (!force && now - attempted in 0..300_000) return
        coverageAttempts[key] = now
        try {
            withTimeoutOrNull(15_000) {
                val account = account()?.takeIf { it.key == key } ?: return@withTimeoutOrNull
                val server = servers.getById(account.serverId) ?: return@withTimeoutOrNull
                val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
                suspend fun get(path: String) = JSONObject(http.absRequest(Request.Builder().url("$base/api/$path")
                    .header("Authorization", authorizationHeaderValue(account.token)).build()))
                val progress = get("me/progress").getJSONArray("mediaProgress")
                val hasPodcastProgress = (0 until progress.length()).any { progress.optJSONObject(it)?.optionalText("episodeId") != null }
                val sessions = if (hasPodcastProgress) completeSessionIndex(key, false).getJSONArray("sessions") else JSONArray()
                val previous = podcastGaps(saved?.optJSONObject("data"))
                var gaps = missingPodcastHistory(progress, sessions, previous, now)
                val libraries = gaps.mapNotNull { g -> g.libraryId?.let { g.showId to it } }.toMap().toMutableMap()
                val aliases = mutableMapOf<Pair<String, String>, String>()
                // Resolve each missing show's actual library; never attribute another library's progress to this one.
                gaps.map { it.showId }.distinct().take(20).forEach { show ->
                    val encoded = java.net.URLEncoder.encode(show, "UTF-8").replace("+", "%20")
                    try {
                        val detail = get("items/$encoded")
                        detail.optionalText("libraryId")?.let { libraries[show] = it }
                        val media = detail.optJSONObject("media")
                        val episodes = media?.optJSONArray("episodes") ?: media?.optJSONArray("podcastEpisodes") ?: JSONArray()
                        gaps.filter { it.showId == show }.forEach { gap ->
                            nativePodcastEpisodeId(episodes, gap.episodeId)?.let { aliases[show to gap.episodeId] = it }
                        }
                    }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { /* An inaccessible show must not hide verified gaps in other libraries. */ }
                }
                val normalizedProgress = JSONArray((0 until progress.length()).mapNotNull { progress.optJSONObject(it) }.map { row ->
                    val alias = aliases[row.optionalText("libraryItemId") to row.optionalText("episodeId")]
                    if (alias == null) row else nativePodcastSession(row, alias)
                })
                val normalizedPrevious = previous.map { it.copy(episodeId = aliases[it.showId to it.episodeId] ?: it.episodeId) }
                    .distinctBy { it.showId to it.episodeId }
                gaps = missingPodcastHistory(normalizedProgress, sessions, normalizedPrevious, now)
                gaps = gaps.map { it.copy(libraryId = libraries[it.showId] ?: it.libraryId) }
                currentCoroutineContext().ensureActive()
                if (account()?.key == key) write(key, "podcast-coverage", JSONObject().put("gaps", JSONArray(gaps.map { it.json() })))
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Keep the last verified coverage during outages. */ }
    }

    private suspend fun completeSessionIndex(key: String, force: Boolean): JSONObject {
        val saved = read(key, "session-index")
        if (!force && saved?.optJSONObject("data")?.optJSONArray("sessions") != null &&
            System.currentTimeMillis() - (saved.nonnegative("updatedAt")?.toLong() ?: 0) in 0..300_000) return saved.getJSONObject("data")
        return withTimeoutOrNull(60_000) {
            val account = account()?.takeIf { it.key == key } ?: throw CancellationException("Account changed")
            val server = servers.getById(account.serverId) ?: error("No server")
            val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
            val rows = linkedMapOf<String, JSONObject>()
            var page = 0
            var expected = 0
            do {
                val request = Request.Builder().url("$base/api/me/listening-sessions?itemsPerPage=100&page=$page")
                    .header("Authorization", authorizationHeaderValue(account.token)).build()
                val json = JSONObject(http.absRequest(request))
                val parsed = parseAbsHistory(json)
                expected = maxOf(expected, parsed.total)
                require(expected <= 50_000) { "History too large to aggregate on this device" }
                val sessions = json.getJSONArray("sessions")
                for (i in 0 until sessions.length()) {
                    val row = sessions.optJSONObject(i) ?: continue
                    row.optionalText("id")?.let { rows[it] = absStatisticsSession(row) }
                }
                page++
            } while (parsed.hasMore && page < 500)
            check(rows.size >= expected) { "History changed during loading; retry later" }
            if (account()?.key != key) throw CancellationException("Account changed")
            val index = JSONObject().put("sessions", JSONArray(rows.values.toList()))
            write(key, "session-index", index)
            // Only a newly fetched complete index establishes remote-observation evidence.
            localAccounts.aliases(key).forEach { localJournal.reconcile(it, index.getJSONArray("sessions")) }
            index
        } ?: error("Audiobookshelf history loading timed out")
    }

    /** Native ABS local-session protocol is cumulative and replaces the same UUID on retries. */
    internal suspend fun submitSession(account: AbsStatisticsAccount, session: JSONObject) = withContext(Dispatchers.IO) {
        check(accountForServer(account.serverId)?.key == account.key) { "Account changed" }
        val server = servers.getById(account.serverId) ?: error("No server")
        val base = endpoints.resolveForServer(server).effectiveBaseUrl.trimEnd('/')
        var payload = session
        session.optionalText("episodeId")?.let { original ->
            val show = session.getString("libraryItemId")
            val key = Triple(account.key, show, original)
            val now = System.currentTimeMillis()
            val cached = episodeIds[key]?.takeIf { now - it.second in 0..300_000 }?.first
            if (cached == "") throw AbsPodcastEpisodeUnavailable()
            val canonical = cached ?: run {
                val encoded = java.net.URLEncoder.encode(show, "UTF-8").replace("+", "%20")
                val detail = JSONObject(http.absRequest(Request.Builder().url("$base/api/items/$encoded?expanded=1")
                    .header("Authorization", authorizationHeaderValue(account.token)).build()))
                val media = detail.getJSONObject("media")
                val episodes = media.optJSONArray("episodes") ?: media.optJSONArray("podcastEpisodes") ?: JSONArray()
                nativePodcastEpisodeId(episodes, original)?.also { episodeIds[key] = it to now }
                    ?: run { episodeIds[key] = "" to now; throw AbsPodcastEpisodeUnavailable() }
            }
            // Keep the measured timestamp, duration and position. The native session API
            // uses that timestamp to protect any newer progress from another device.
            payload = nativePodcastSession(session, canonical)
        }
        val request = Request.Builder().url("$base/api/session/local")
            .header("Authorization", authorizationHeaderValue(account.token))
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.absRequest(request)
    }
}
