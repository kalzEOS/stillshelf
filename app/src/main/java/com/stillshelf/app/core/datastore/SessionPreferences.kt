package com.stillshelf.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stillshelf.app.core.model.ServerConnectionMode
import com.stillshelf.app.core.model.ServerEndpointSwitchingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class SessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val activeServerIdKey = stringPreferencesKey("active_server_id")
    private val activeLibraryIdKey = stringPreferencesKey("active_library_id")
    private val requiresLibrarySelectionKey = booleanPreferencesKey("requires_library_selection")
    private val lastPlayedBookIdKey = stringPreferencesKey("last_played_book_id")
    private val hiddenBrowseSectionsKey = stringPreferencesKey("hidden_browse_sections")
    private val hiddenHomeSectionsKey = stringPreferencesKey("hidden_home_sections")
    private val browseSectionOrderKey = stringPreferencesKey("browse_section_order")
    private val homeSectionOrderKey = stringPreferencesKey("home_section_order")
    private val booksLayoutModeKey = stringPreferencesKey("books_layout_mode")
    private val booksStatusFilterKey = stringPreferencesKey("books_status_filter")
    private val booksSortKey = stringPreferencesKey("books_sort_key")
    private val booksCollapseSeriesKey = booleanPreferencesKey("books_collapse_series")
    private val authorLayoutModeKey = stringPreferencesKey("author_layout_mode")
    private val authorCollapseSeriesKey = booleanPreferencesKey("author_collapse_series")
    private val seriesBrowseGridModeKey = booleanPreferencesKey("series_browse_grid_mode")
    private val seriesDetailListModeKey = booleanPreferencesKey("series_detail_list_mode")
    private val seriesDetailCollapseSubseriesKey = booleanPreferencesKey("series_detail_collapse_subseries")
    private val collectionDetailListModeKey = booleanPreferencesKey("collection_detail_list_mode")
    private val playlistDetailListModeKey = booleanPreferencesKey("playlist_detail_list_mode")
    private val downloadedListModeKey = booleanPreferencesKey("downloaded_list_mode")
    private val immersivePlayerEnabledKey = booleanPreferencesKey("immersive_player_enabled")
    private val appThemeModeKey = stringPreferencesKey("app_theme_mode")
    private val materialDesignEnabledKey = booleanPreferencesKey("material_design_enabled")
    private val playerBottomToolsStyleKey = stringPreferencesKey("player_bottom_tools_style")
    private val skipForwardSecondsKey = intPreferencesKey("skip_forward_seconds")
    private val skipBackwardSecondsKey = intPreferencesKey("skip_backward_seconds")
    private val softToneLevelKey = floatPreferencesKey("soft_tone_level")
    private val boostLevelKey = floatPreferencesKey("boost_level")
    private val lockScreenControlModeKey = stringPreferencesKey("lock_screen_control_mode")
    private val lastBookDetailTabKey = stringPreferencesKey("last_book_detail_tab")
    private val downloadedBookIdsKey = stringPreferencesKey("downloaded_book_ids")
    private val serverEndpointSwitchingConfigsKey = stringPreferencesKey("server_endpoint_switching_configs")
    private val cachedHomeFeedLibraryIdKey = stringPreferencesKey("cached_home_feed_library_id")
    private val cachedHomeFeedPayloadKey = stringPreferencesKey("cached_home_feed_payload")
    private val cachedHomeFeedSavedAtKey = longPreferencesKey("cached_home_feed_saved_at")
    private val lastLibrarySyncAtMsKey = longPreferencesKey("last_library_sync_at_ms")
    private val updateCheckOnStartupKey = booleanPreferencesKey("update_check_on_startup")
    private val updateIncludePrereleasesKey = booleanPreferencesKey("update_include_prereleases")
    private val pendingUpdateApkPathKey = stringPreferencesKey("pending_update_apk_path")
    private val pendingUpdateVersionNameKey = stringPreferencesKey("pending_update_version_name")
    private val pendingFinishedRestoreSnapshotKey = stringPreferencesKey("pending_finished_restore_snapshot")
    private val playbackCheckpointSnapshotKey = stringPreferencesKey("playback_checkpoint_snapshot")
    private val recentSearchTermsKey = stringPreferencesKey("recent_search_terms")

    val state: Flow<SessionPreferenceState> = dataStore.data.map { prefs ->
        SessionPreferenceState(
            activeServerId = prefs[activeServerIdKey],
            activeLibraryId = prefs[activeLibraryIdKey],
            requiresLibrarySelection = prefs[requiresLibrarySelectionKey] ?: false,
            lastPlayedBookId = prefs[lastPlayedBookIdKey],
            hiddenBrowseSectionIds = parseCsv(prefs[hiddenBrowseSectionsKey]),
            hiddenHomeSectionIds = parseCsv(prefs[hiddenHomeSectionsKey]),
            browseSectionOrder = parseList(prefs[browseSectionOrderKey]),
            homeSectionOrder = parseList(prefs[homeSectionOrderKey]),
            booksLayoutMode = prefs[booksLayoutModeKey],
            booksStatusFilter = prefs[booksStatusFilterKey],
            booksSortKey = prefs[booksSortKey],
            booksCollapseSeries = prefs[booksCollapseSeriesKey] ?: true,
            authorLayoutMode = prefs[authorLayoutModeKey],
            authorCollapseSeries = prefs[authorCollapseSeriesKey] ?: true,
            seriesBrowseGridMode = prefs[seriesBrowseGridModeKey] ?: true,
            seriesDetailListMode = prefs[seriesDetailListModeKey] ?: true,
            seriesDetailCollapseSubseries = prefs[seriesDetailCollapseSubseriesKey] ?: true,
            collectionDetailListMode = prefs[collectionDetailListModeKey] ?: true,
            playlistDetailListMode = prefs[playlistDetailListModeKey] ?: true,
            downloadedListMode = prefs[downloadedListModeKey] ?: true,
            immersivePlayerEnabled = prefs[immersivePlayerEnabledKey] ?: false,
            appThemeMode = prefs[appThemeModeKey] ?: "follow_system",
            materialDesignEnabled = prefs[materialDesignEnabledKey] ?: false,
            playerBottomToolsStyle = prefs[playerBottomToolsStyleKey] ?: "dock",
            skipForwardSeconds = (prefs[skipForwardSecondsKey] ?: 15).coerceIn(5, 600),
            skipBackwardSeconds = (prefs[skipBackwardSecondsKey] ?: 15).coerceIn(5, 600),
            softToneLevel = (prefs[softToneLevelKey] ?: 0f).coerceIn(0f, 1f),
            boostLevel = (prefs[boostLevelKey] ?: 0f).coerceIn(0f, 1f),
            lockScreenControlMode = prefs[lockScreenControlModeKey] ?: "skip",
            lastBookDetailTab = prefs[lastBookDetailTabKey] ?: "About",
            downloadedBookIds = parseCsv(prefs[downloadedBookIdsKey]),
            serverEndpointSwitchingConfigs = parseServerEndpointSwitchingConfigs(
                prefs[serverEndpointSwitchingConfigsKey]
            ),
            lastLibrarySyncAtMs = prefs[lastLibrarySyncAtMsKey],
            updateCheckOnStartup = prefs[updateCheckOnStartupKey] ?: true,
            updateIncludePrereleases = prefs[updateIncludePrereleasesKey] ?: false,
            pendingUpdateApkPath = prefs[pendingUpdateApkPathKey],
            pendingUpdateVersionName = prefs[pendingUpdateVersionNameKey],
            recentSearchTerms = parseStringArray(prefs[recentSearchTermsKey])
        )
    }

    suspend fun setActiveServerId(serverId: String?) {
        dataStore.edit { prefs ->
            if (serverId == null) {
                prefs.remove(activeServerIdKey)
            } else {
                prefs[activeServerIdKey] = serverId
            }
        }
    }

    suspend fun setActiveLibraryId(libraryId: String?) {
        dataStore.edit { prefs ->
            if (libraryId == null) {
                prefs.remove(activeLibraryIdKey)
            } else {
                prefs[activeLibraryIdKey] = libraryId
            }
        }
    }

    suspend fun setRequiresLibrarySelection(required: Boolean) {
        dataStore.edit { prefs ->
            prefs[requiresLibrarySelectionKey] = required
        }
    }

    suspend fun setActiveSelection(serverId: String?, libraryId: String?) {
        dataStore.edit { prefs ->
            if (serverId == null) {
                prefs.remove(activeServerIdKey)
            } else {
                prefs[activeServerIdKey] = serverId
            }
            if (libraryId == null) {
                prefs.remove(activeLibraryIdKey)
            } else {
                prefs[activeLibraryIdKey] = libraryId
            }
        }
    }

    suspend fun setLastPlayedBookId(bookId: String?) {
        dataStore.edit { prefs ->
            if (bookId.isNullOrBlank()) {
                prefs.remove(lastPlayedBookIdKey)
            } else {
                prefs[lastPlayedBookIdKey] = bookId
            }
        }
    }

    suspend fun setHiddenBrowseSectionIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(hiddenBrowseSectionsKey)
            } else {
                prefs[hiddenBrowseSectionsKey] = ids.sorted().joinToString(",")
            }
        }
    }

    suspend fun setHiddenHomeSectionIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(hiddenHomeSectionsKey)
            } else {
                prefs[hiddenHomeSectionsKey] = ids.sorted().joinToString(",")
            }
        }
    }

    suspend fun setBrowseSectionOrder(ids: List<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(browseSectionOrderKey)
            } else {
                prefs[browseSectionOrderKey] = ids.joinToString(",")
            }
        }
    }

    suspend fun setHomeSectionOrder(ids: List<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(homeSectionOrderKey)
            } else {
                prefs[homeSectionOrderKey] = ids.joinToString(",")
            }
        }
    }

    suspend fun setBooksLayoutMode(mode: String) {
        dataStore.edit { prefs ->
            if (mode.isBlank()) {
                prefs.remove(booksLayoutModeKey)
            } else {
                prefs[booksLayoutModeKey] = mode
            }
        }
    }

    suspend fun setBooksStatusFilter(filter: String) {
        dataStore.edit { prefs ->
            if (filter.isBlank()) {
                prefs.remove(booksStatusFilterKey)
            } else {
                prefs[booksStatusFilterKey] = filter
            }
        }
    }

    suspend fun setBooksSortKey(sortKey: String) {
        dataStore.edit { prefs ->
            if (sortKey.isBlank()) {
                prefs.remove(booksSortKey)
            } else {
                prefs[booksSortKey] = sortKey
            }
        }
    }

    suspend fun setBooksCollapseSeries(collapse: Boolean) {
        dataStore.edit { prefs ->
            prefs[booksCollapseSeriesKey] = collapse
        }
    }

    suspend fun setAuthorLayoutMode(mode: String) {
        dataStore.edit { prefs ->
            if (mode.isBlank()) {
                prefs.remove(authorLayoutModeKey)
            } else {
                prefs[authorLayoutModeKey] = mode
            }
        }
    }

    suspend fun setAuthorCollapseSeries(collapse: Boolean) {
        dataStore.edit { prefs ->
            prefs[authorCollapseSeriesKey] = collapse
        }
    }

    suspend fun setSeriesBrowseGridMode(gridMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[seriesBrowseGridModeKey] = gridMode
        }
    }

    suspend fun setSeriesDetailListMode(listMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[seriesDetailListModeKey] = listMode
        }
    }

    suspend fun setSeriesDetailCollapseSubseries(collapse: Boolean) {
        dataStore.edit { prefs ->
            prefs[seriesDetailCollapseSubseriesKey] = collapse
        }
    }

    suspend fun setCollectionDetailListMode(listMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[collectionDetailListModeKey] = listMode
        }
    }

    suspend fun setPlaylistDetailListMode(listMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[playlistDetailListModeKey] = listMode
        }
    }

    suspend fun setDownloadedListMode(listMode: Boolean) {
        dataStore.edit { prefs ->
            prefs[downloadedListModeKey] = listMode
        }
    }

    suspend fun setImmersivePlayerEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[immersivePlayerEnabledKey] = enabled
        }
    }

    suspend fun setAppThemeMode(mode: String) {
        dataStore.edit { prefs ->
            if (mode.isBlank()) {
                prefs.remove(appThemeModeKey)
            } else {
                prefs[appThemeModeKey] = mode
            }
        }
    }

    suspend fun setMaterialDesignEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[materialDesignEnabledKey] = enabled
        }
    }

    suspend fun setPlayerBottomToolsStyle(style: String) {
        dataStore.edit { prefs ->
            prefs[playerBottomToolsStyleKey] = style.ifBlank { "dock" }
        }
    }

    suspend fun setSkipForwardSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[skipForwardSecondsKey] = seconds.coerceIn(5, 600)
        }
    }

    suspend fun setSkipBackwardSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[skipBackwardSecondsKey] = seconds.coerceIn(5, 600)
        }
    }

    suspend fun setSoftToneLevel(level: Float) {
        dataStore.edit { prefs ->
            prefs[softToneLevelKey] = level.coerceIn(0f, 1f)
        }
    }

    suspend fun setBoostLevel(level: Float) {
        dataStore.edit { prefs ->
            prefs[boostLevelKey] = level.coerceIn(0f, 1f)
        }
    }

    suspend fun setLockScreenControlMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[lockScreenControlModeKey] = mode.ifBlank { "skip" }
        }
    }

    suspend fun setLastBookDetailTab(tab: String) {
        dataStore.edit { prefs ->
            prefs[lastBookDetailTabKey] = tab.ifBlank { "About" }
        }
    }

    suspend fun setLastLibrarySyncAtMs(timestampMs: Long?) {
        dataStore.edit { prefs ->
            if (timestampMs == null) {
                prefs.remove(lastLibrarySyncAtMsKey)
            } else {
                prefs[lastLibrarySyncAtMsKey] = timestampMs.coerceAtLeast(0L)
            }
        }
    }

    suspend fun setUpdateCheckOnStartup(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[updateCheckOnStartupKey] = enabled
        }
    }

    suspend fun setUpdateIncludePrereleases(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[updateIncludePrereleasesKey] = enabled
        }
    }

    suspend fun setPendingUpdateInstall(apkPath: String?, versionName: String?) {
        dataStore.edit { prefs ->
            if (apkPath.isNullOrBlank()) {
                prefs.remove(pendingUpdateApkPathKey)
            } else {
                prefs[pendingUpdateApkPathKey] = apkPath.trim()
            }
            if (versionName.isNullOrBlank()) {
                prefs.remove(pendingUpdateVersionNameKey)
            } else {
                prefs[pendingUpdateVersionNameKey] = versionName.trim()
            }
        }
    }

    suspend fun getPendingFinishedRestoreSnapshot(): PendingFinishedRestoreSnapshot? {
        val raw = dataStore.data.first()[pendingFinishedRestoreSnapshotKey] ?: return null
        return runCatching {
            val node = JSONObject(raw)
            PendingFinishedRestoreSnapshot(
                bookId = node.optString("bookId").trim(),
                currentTimeSeconds = node.optDouble("currentTimeSeconds").coerceAtLeast(0.0),
                durationSeconds = node.takeIf { it.has("durationSeconds") }
                    ?.optDouble("durationSeconds")
                    ?.takeIf { it > 0.0 },
                wasFinished = node.optBoolean("wasFinished"),
                progressPercent = node.takeIf { it.has("progressPercent") }
                    ?.optDouble("progressPercent")
                    ?.coerceIn(0.0, 1.0)
            ).takeIf { it.bookId.isNotBlank() }
        }.getOrNull()
    }

    suspend fun setPendingFinishedRestoreSnapshot(snapshot: PendingFinishedRestoreSnapshot?) {
        dataStore.edit { prefs ->
            if (snapshot == null || snapshot.bookId.isBlank()) {
                prefs.remove(pendingFinishedRestoreSnapshotKey)
            } else {
                val node = JSONObject()
                    .put("bookId", snapshot.bookId)
                    .put("currentTimeSeconds", snapshot.currentTimeSeconds.coerceAtLeast(0.0))
                    .put("wasFinished", snapshot.wasFinished)
                snapshot.durationSeconds?.takeIf { it > 0.0 }?.let { node.put("durationSeconds", it) }
                snapshot.progressPercent?.coerceIn(0.0, 1.0)?.let { node.put("progressPercent", it) }
                prefs[pendingFinishedRestoreSnapshotKey] = node.toString()
            }
        }
    }

    suspend fun getPlaybackCheckpoint(serverId: String?, bookId: String): PlaybackCheckpointSnapshot? {
        val normalizedBookId = bookId.trim()
        if (normalizedBookId.isBlank()) return null
        val normalizedServerId = serverId?.trim().takeIf { !it.isNullOrBlank() }
        return getPlaybackCheckpoints().firstOrNull { checkpoint ->
            checkpoint.bookId == normalizedBookId && checkpoint.serverId == normalizedServerId
        }
    }

    suspend fun getPlaybackCheckpoints(): List<PlaybackCheckpointSnapshot> {
        val raw = dataStore.data.first()[playbackCheckpointSnapshotKey] ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.optJSONObject(index) ?: continue
                    val checkpoint = PlaybackCheckpointSnapshot(
                        serverId = node.optString("serverId").trim().takeIf { it.isNotBlank() },
                        bookId = node.optString("bookId").trim(),
                        currentTimeSeconds = node.optDouble("currentTimeSeconds").coerceAtLeast(0.0),
                        durationSeconds = node.takeIf { it.has("durationSeconds") }
                            ?.optDouble("durationSeconds")
                            ?.takeIf { it > 0.0 },
                        isFinished = node.optBoolean("isFinished"),
                        savedAtMs = node.optLong("savedAtMs").coerceAtLeast(0L)
                    )
                    if (checkpoint.bookId.isNotBlank()) {
                        add(checkpoint)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setPlaybackCheckpoint(snapshot: PlaybackCheckpointSnapshot?) {
        dataStore.edit { prefs ->
            val current = parsePlaybackCheckpoints(prefs[playbackCheckpointSnapshotKey]).toMutableList()
            if (snapshot == null || snapshot.bookId.isBlank()) {
                if (current.isEmpty()) {
                    prefs.remove(playbackCheckpointSnapshotKey)
                } else {
                    prefs[playbackCheckpointSnapshotKey] = encodePlaybackCheckpoints(current)
                }
                return@edit
            }
            val normalizedServerId = snapshot.serverId?.trim().takeIf { !it.isNullOrBlank() }
            val normalizedBookId = snapshot.bookId.trim()
            val updated = current
                .filterNot { it.bookId == normalizedBookId && it.serverId == normalizedServerId }
                .toMutableList()
            updated.add(
                snapshot.copy(
                    serverId = normalizedServerId,
                    bookId = normalizedBookId
                )
            )
            prefs[playbackCheckpointSnapshotKey] = encodePlaybackCheckpoints(updated)
        }
    }

    suspend fun clearPlaybackCheckpoint(serverId: String?, bookId: String) {
        val normalizedBookId = bookId.trim()
        if (normalizedBookId.isBlank()) return
        val normalizedServerId = serverId?.trim().takeIf { !it.isNullOrBlank() }
        dataStore.edit { prefs ->
            val updated = parsePlaybackCheckpoints(prefs[playbackCheckpointSnapshotKey])
                .filterNot { it.bookId == normalizedBookId && it.serverId == normalizedServerId }
            if (updated.isEmpty()) {
                prefs.remove(playbackCheckpointSnapshotKey)
            } else {
                prefs[playbackCheckpointSnapshotKey] = encodePlaybackCheckpoints(updated)
            }
        }
    }

    suspend fun setDownloadedBookIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(downloadedBookIdsKey)
            } else {
                prefs[downloadedBookIdsKey] = ids.sorted().joinToString(",")
            }
        }
    }

    suspend fun setServerEndpointSwitchingConfig(
        serverId: String,
        config: ServerEndpointSwitchingConfig?
    ) {
        val normalizedServerId = serverId.trim()
        if (normalizedServerId.isBlank()) return
        dataStore.edit { prefs ->
            val current = parseServerEndpointSwitchingConfigs(
                prefs[serverEndpointSwitchingConfigsKey]
            ).toMutableMap()
            if (config == null || !config.enabled && config.lanBaseUrl.isNullOrBlank() && config.wanBaseUrl.isNullOrBlank()) {
                current.remove(normalizedServerId)
            } else {
                current[normalizedServerId] = config.copy(
                    lanBaseUrl = config.lanBaseUrl?.trim()?.takeIf { it.isNotBlank() },
                    wanBaseUrl = config.wanBaseUrl?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            if (current.isEmpty()) {
                prefs.remove(serverEndpointSwitchingConfigsKey)
            } else {
                prefs[serverEndpointSwitchingConfigsKey] = encodeServerEndpointSwitchingConfigs(current)
            }
        }
    }

    suspend fun removeServerEndpointSwitchingConfig(serverId: String) {
        val normalizedServerId = serverId.trim()
        if (normalizedServerId.isBlank()) return
        dataStore.edit { prefs ->
            val current = parseServerEndpointSwitchingConfigs(
                prefs[serverEndpointSwitchingConfigsKey]
            ).toMutableMap()
            current.remove(normalizedServerId)
            if (current.isEmpty()) {
                prefs.remove(serverEndpointSwitchingConfigsKey)
            } else {
                prefs[serverEndpointSwitchingConfigsKey] = encodeServerEndpointSwitchingConfigs(current)
            }
        }
    }

    suspend fun toggleDownloadedBookId(bookId: String): Boolean {
        val trimmedId = bookId.trim()
        if (trimmedId.isEmpty()) return false
        var nowDownloaded = false
        dataStore.edit { prefs ->
            val current = parseCsv(prefs[downloadedBookIdsKey]).toMutableSet()
            if (current.contains(trimmedId)) {
                current.remove(trimmedId)
                nowDownloaded = false
            } else {
                current.add(trimmedId)
                nowDownloaded = true
            }
            if (current.isEmpty()) {
                prefs.remove(downloadedBookIdsKey)
            } else {
                prefs[downloadedBookIdsKey] = current.sorted().joinToString(",")
            }
        }
        return nowDownloaded
    }

    suspend fun getCachedHomeFeed(): CachedHomeFeedPayload? {
        val prefs = dataStore.data.first()
        val libraryId = prefs[cachedHomeFeedLibraryIdKey] ?: return null
        val payload = prefs[cachedHomeFeedPayloadKey] ?: return null
        val savedAtMs = prefs[cachedHomeFeedSavedAtKey] ?: 0L
        return CachedHomeFeedPayload(
            libraryId = libraryId,
            payload = payload,
            savedAtMs = savedAtMs
        )
    }

    suspend fun setCachedHomeFeed(
        libraryId: String,
        payload: String,
        savedAtMs: Long
    ) {
        dataStore.edit { prefs ->
            prefs[cachedHomeFeedLibraryIdKey] = libraryId
            prefs[cachedHomeFeedPayloadKey] = payload
            prefs[cachedHomeFeedSavedAtKey] = savedAtMs
        }
    }

    suspend fun clearCachedHomeFeed() {
        dataStore.edit { prefs ->
            prefs.remove(cachedHomeFeedLibraryIdKey)
            prefs.remove(cachedHomeFeedPayloadKey)
            prefs.remove(cachedHomeFeedSavedAtKey)
        }
    }

    suspend fun addRecentSearchTerm(term: String, maxItems: Int = 10) {
        val normalizedTerm = term.trim()
        if (normalizedTerm.isBlank()) return
        dataStore.edit { prefs ->
            val updated = buildList {
                add(normalizedTerm)
                parseStringArray(prefs[recentSearchTermsKey])
                    .filterNot { it.equals(normalizedTerm, ignoreCase = true) }
                    .take(maxItems.coerceAtLeast(1) - 1)
                    .forEach(::add)
            }
            prefs[recentSearchTermsKey] = encodeStringArray(updated)
        }
    }

    suspend fun clearRecentSearchTerms() {
        dataStore.edit { prefs ->
            prefs.remove(recentSearchTermsKey)
        }
    }

    private fun parseCsv(csv: String?): Set<String> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun parseList(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val source = JSONArray(raw)
            buildList {
                for (index in 0 until source.length()) {
                    val value = source.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseServerEndpointSwitchingConfigs(raw: String?): Map<String, ServerEndpointSwitchingConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val serverId = keys.next().trim()
                    if (serverId.isBlank()) continue
                    val node = root.optJSONObject(serverId) ?: continue
                    val mode = when (node.optString("mode").trim().lowercase()) {
                        "local" -> ServerConnectionMode.Local
                        "remote" -> ServerConnectionMode.Remote
                        else -> ServerConnectionMode.Auto
                    }
                    put(
                        serverId,
                        ServerEndpointSwitchingConfig(
                            enabled = node.optBoolean("enabled"),
                            lanBaseUrl = node.optString("lanBaseUrl").trim().takeIf { it.isNotBlank() },
                            wanBaseUrl = node.optString("wanBaseUrl").trim().takeIf { it.isNotBlank() },
                            connectionMode = mode
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeStringArray(values: List<String>): String {
        return JSONArray().apply {
            values.forEach { value ->
                val normalizedValue = value.trim()
                if (normalizedValue.isNotBlank()) {
                    put(normalizedValue)
                }
            }
        }.toString()
    }

    private fun encodeServerEndpointSwitchingConfigs(
        values: Map<String, ServerEndpointSwitchingConfig>
    ): String {
        val root = JSONObject()
        values.forEach { (serverId, config) ->
            val normalizedServerId = serverId.trim()
            if (normalizedServerId.isBlank()) return@forEach
            root.put(
                normalizedServerId,
                JSONObject()
                    .put("enabled", config.enabled)
                    .put(
                        "mode",
                        when (config.connectionMode) {
                            ServerConnectionMode.Auto -> "auto"
                            ServerConnectionMode.Local -> "local"
                            ServerConnectionMode.Remote -> "remote"
                        }
                    )
                    .apply {
                        config.lanBaseUrl?.trim()?.takeIf { it.isNotBlank() }?.let { put("lanBaseUrl", it) }
                        config.wanBaseUrl?.trim()?.takeIf { it.isNotBlank() }?.let { put("wanBaseUrl", it) }
                    }
            )
        }
        return root.toString()
    }

    private fun parsePlaybackCheckpoints(raw: String?): List<PlaybackCheckpointSnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val node = array.optJSONObject(index) ?: continue
                    val checkpoint = PlaybackCheckpointSnapshot(
                        serverId = node.optString("serverId").trim().takeIf { it.isNotBlank() },
                        bookId = node.optString("bookId").trim(),
                        currentTimeSeconds = node.optDouble("currentTimeSeconds").coerceAtLeast(0.0),
                        durationSeconds = node.takeIf { it.has("durationSeconds") }
                            ?.optDouble("durationSeconds")
                            ?.takeIf { it > 0.0 },
                        isFinished = node.optBoolean("isFinished"),
                        savedAtMs = node.optLong("savedAtMs").coerceAtLeast(0L)
                    )
                    if (checkpoint.bookId.isNotBlank()) {
                        add(checkpoint)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePlaybackCheckpoints(values: List<PlaybackCheckpointSnapshot>): String {
        return JSONArray().apply {
            values.forEach { checkpoint ->
                val normalizedBookId = checkpoint.bookId.trim()
                if (normalizedBookId.isBlank()) return@forEach
                put(
                    JSONObject()
                        .put("bookId", normalizedBookId)
                        .put("currentTimeSeconds", checkpoint.currentTimeSeconds.coerceAtLeast(0.0))
                        .put("isFinished", checkpoint.isFinished)
                        .put("savedAtMs", checkpoint.savedAtMs.coerceAtLeast(0L))
                        .apply {
                            checkpoint.serverId?.trim()?.takeIf { it.isNotBlank() }?.let { put("serverId", it) }
                            checkpoint.durationSeconds?.takeIf { it > 0.0 }?.let { put("durationSeconds", it) }
                        }
                )
            }
        }.toString()
    }
}

data class SessionPreferenceState(
    val activeServerId: String?,
    val activeLibraryId: String?,
    val requiresLibrarySelection: Boolean = false,
    val lastPlayedBookId: String? = null,
    val hiddenBrowseSectionIds: Set<String> = emptySet(),
    val hiddenHomeSectionIds: Set<String> = emptySet(),
    val browseSectionOrder: List<String> = emptyList(),
    val homeSectionOrder: List<String> = emptyList(),
    val booksLayoutMode: String? = null,
    val booksStatusFilter: String? = null,
    val booksSortKey: String? = null,
    val booksCollapseSeries: Boolean = true,
    val authorLayoutMode: String? = null,
    val authorCollapseSeries: Boolean = true,
    val seriesBrowseGridMode: Boolean = true,
    val seriesDetailListMode: Boolean = true,
    val seriesDetailCollapseSubseries: Boolean = true,
    val collectionDetailListMode: Boolean = true,
    val playlistDetailListMode: Boolean = true,
    val downloadedListMode: Boolean = true,
    val immersivePlayerEnabled: Boolean = false,
    val appThemeMode: String = "follow_system",
    val materialDesignEnabled: Boolean = false,
    val playerBottomToolsStyle: String = "dock",
    val skipForwardSeconds: Int = 15,
    val skipBackwardSeconds: Int = 15,
    val softToneLevel: Float = 0f,
    val boostLevel: Float = 0f,
    val lockScreenControlMode: String = "skip",
    val lastBookDetailTab: String = "About",
    val downloadedBookIds: Set<String> = emptySet(),
    val serverEndpointSwitchingConfigs: Map<String, ServerEndpointSwitchingConfig> = emptyMap(),
    val lastLibrarySyncAtMs: Long? = null,
    val updateCheckOnStartup: Boolean = true,
    val updateIncludePrereleases: Boolean = false,
    val pendingUpdateApkPath: String? = null,
    val pendingUpdateVersionName: String? = null,
    val recentSearchTerms: List<String> = emptyList()
)

data class CachedHomeFeedPayload(
    val libraryId: String,
    val payload: String,
    val savedAtMs: Long
)

data class PendingFinishedRestoreSnapshot(
    val bookId: String,
    val currentTimeSeconds: Double,
    val durationSeconds: Double?,
    val wasFinished: Boolean,
    val progressPercent: Double?
)

data class PlaybackCheckpointSnapshot(
    val serverId: String?,
    val bookId: String,
    val currentTimeSeconds: Double,
    val durationSeconds: Double?,
    val isFinished: Boolean,
    val savedAtMs: Long
)
