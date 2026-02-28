package com.stillshelf.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val activeServerIdKey = stringPreferencesKey("active_server_id")
    private val activeLibraryIdKey = stringPreferencesKey("active_library_id")
    private val lastPlayedBookIdKey = stringPreferencesKey("last_played_book_id")
    private val hiddenBrowseSectionsKey = stringPreferencesKey("hidden_browse_sections")
    private val hiddenHomeSectionsKey = stringPreferencesKey("hidden_home_sections")
    private val browseSectionOrderKey = stringPreferencesKey("browse_section_order")
    private val homeSectionOrderKey = stringPreferencesKey("home_section_order")
    private val booksLayoutModeKey = stringPreferencesKey("books_layout_mode")
    private val booksStatusFilterKey = stringPreferencesKey("books_status_filter")
    private val booksSortKey = stringPreferencesKey("books_sort_key")
    private val booksCollapseSeriesKey = booleanPreferencesKey("books_collapse_series")
    private val immersivePlayerEnabledKey = booleanPreferencesKey("immersive_player_enabled")
    private val appThemeModeKey = stringPreferencesKey("app_theme_mode")
    private val materialDesignEnabledKey = booleanPreferencesKey("material_design_enabled")
    private val cachedHomeFeedLibraryIdKey = stringPreferencesKey("cached_home_feed_library_id")
    private val cachedHomeFeedPayloadKey = stringPreferencesKey("cached_home_feed_payload")
    private val cachedHomeFeedSavedAtKey = longPreferencesKey("cached_home_feed_saved_at")

    val state: Flow<SessionPreferenceState> = dataStore.data.map { prefs ->
        SessionPreferenceState(
            activeServerId = prefs[activeServerIdKey],
            activeLibraryId = prefs[activeLibraryIdKey],
            lastPlayedBookId = prefs[lastPlayedBookIdKey],
            hiddenBrowseSectionIds = parseCsv(prefs[hiddenBrowseSectionsKey]),
            hiddenHomeSectionIds = parseCsv(prefs[hiddenHomeSectionsKey]),
            browseSectionOrder = parseList(prefs[browseSectionOrderKey]),
            homeSectionOrder = parseList(prefs[homeSectionOrderKey]),
            booksLayoutMode = prefs[booksLayoutModeKey],
            booksStatusFilter = prefs[booksStatusFilterKey],
            booksSortKey = prefs[booksSortKey],
            booksCollapseSeries = prefs[booksCollapseSeriesKey] ?: true,
            immersivePlayerEnabled = prefs[immersivePlayerEnabledKey] ?: false,
            appThemeMode = prefs[appThemeModeKey] ?: "follow_system",
            materialDesignEnabled = prefs[materialDesignEnabledKey] ?: false
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
}

data class SessionPreferenceState(
    val activeServerId: String?,
    val activeLibraryId: String?,
    val lastPlayedBookId: String? = null,
    val hiddenBrowseSectionIds: Set<String> = emptySet(),
    val hiddenHomeSectionIds: Set<String> = emptySet(),
    val browseSectionOrder: List<String> = emptyList(),
    val homeSectionOrder: List<String> = emptyList(),
    val booksLayoutMode: String? = null,
    val booksStatusFilter: String? = null,
    val booksSortKey: String? = null,
    val booksCollapseSeries: Boolean = true,
    val immersivePlayerEnabled: Boolean = false,
    val appThemeMode: String = "follow_system",
    val materialDesignEnabled: Boolean = false
)

data class CachedHomeFeedPayload(
    val libraryId: String,
    val payload: String,
    val savedAtMs: Long
)
