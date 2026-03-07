package com.stillshelf.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
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
    private val serverAvatarUrisKey = stringPreferencesKey("server_avatar_uris")
    private val cachedHomeFeedLibraryIdKey = stringPreferencesKey("cached_home_feed_library_id")
    private val cachedHomeFeedPayloadKey = stringPreferencesKey("cached_home_feed_payload")
    private val cachedHomeFeedSavedAtKey = longPreferencesKey("cached_home_feed_saved_at")
    private val lastLibrarySyncAtMsKey = longPreferencesKey("last_library_sync_at_ms")
    private val updateCheckOnStartupKey = booleanPreferencesKey("update_check_on_startup")
    private val updateIncludePrereleasesKey = booleanPreferencesKey("update_include_prereleases")
    private val pendingUpdateApkPathKey = stringPreferencesKey("pending_update_apk_path")
    private val pendingUpdateVersionNameKey = stringPreferencesKey("pending_update_version_name")
    private val pendingFinishedRestoreSnapshotKey = stringPreferencesKey("pending_finished_restore_snapshot")

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
            serverAvatarUris = parseServerAvatarUris(prefs[serverAvatarUrisKey]),
            lastLibrarySyncAtMs = prefs[lastLibrarySyncAtMsKey],
            updateCheckOnStartup = prefs[updateCheckOnStartupKey] ?: true,
            updateIncludePrereleases = prefs[updateIncludePrereleasesKey] ?: false,
            pendingUpdateApkPath = prefs[pendingUpdateApkPathKey],
            pendingUpdateVersionName = prefs[pendingUpdateVersionNameKey]
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

    suspend fun setDownloadedBookIds(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(downloadedBookIdsKey)
            } else {
                prefs[downloadedBookIdsKey] = ids.sorted().joinToString(",")
            }
        }
    }

    suspend fun setServerAvatarUri(serverId: String, avatarUri: String?) {
        val normalizedServerId = serverId.trim()
        if (normalizedServerId.isBlank()) return
        dataStore.edit { prefs ->
            val current = parseServerAvatarUris(prefs[serverAvatarUrisKey]).toMutableMap()
            if (avatarUri.isNullOrBlank()) {
                current.remove(normalizedServerId)
            } else {
                current[normalizedServerId] = avatarUri.trim()
            }
            if (current.isEmpty()) {
                prefs.remove(serverAvatarUrisKey)
            } else {
                prefs[serverAvatarUrisKey] = encodeServerAvatarUris(current)
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

    private fun parseServerAvatarUris(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val node = JSONObject(raw)
            buildMap {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    if (key.isBlank()) continue
                    val value = node.optString(key).trim()
                    if (value.isNotBlank()) {
                        put(key, value)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeServerAvatarUris(values: Map<String, String>): String {
        val node = JSONObject()
        values.forEach { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isNotBlank() && normalizedValue.isNotBlank()) {
                node.put(normalizedKey, normalizedValue)
            }
        }
        return node.toString()
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
    val serverAvatarUris: Map<String, String> = emptyMap(),
    val lastLibrarySyncAtMs: Long? = null,
    val updateCheckOnStartup: Boolean = true,
    val updateIncludePrereleases: Boolean = false,
    val pendingUpdateApkPath: String? = null,
    val pendingUpdateVersionName: String? = null
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
