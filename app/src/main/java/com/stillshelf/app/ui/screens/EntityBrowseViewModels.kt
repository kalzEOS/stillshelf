package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntityBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null
)

data class SeriesBrowseCard(
    val id: String,
    val name: String,
    val subtitle: String?,
    val coverUrl: String?
)

data class SeriesBrowseUiState(
    val isLoading: Boolean = false,
    val series: List<SeriesBrowseCard> = emptyList(),
    val gridMode: Boolean = true,
    val errorMessage: String? = null
)

data class CollectionsBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val coverStackByCollectionId: Map<String, List<String>> = emptyMap(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

data class BookmarksBrowseUiState(
    val isLoading: Boolean = false,
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class AuthorsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(EntityBrowseUiState())
    val uiState: StateFlow<EntityBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchAuthorsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val filtered = result.value.filterNot { isStillShelfProbeCollection(it.name) }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = filtered
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class NarratorsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(EntityBrowseUiState())
    val uiState: StateFlow<EntityBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchNarratorsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = result.value
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class SeriesBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    companion object {
        private const val SERIES_BOOKS_PAGE_SIZE = 400
        private const val SERIES_BOOKS_MAX_PAGES = 100
    }

    private val mutableUiState = MutableStateFlow(SeriesBrowseUiState())
    val uiState: StateFlow<SeriesBrowseUiState> = mutableUiState.asStateFlow()

    init {
        restoreGridMode()
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun setGridMode(gridMode: Boolean) {
        mutableUiState.update { it.copy(gridMode = gridMode) }
        viewModelScope.launch {
            sessionPreferences.setSeriesBrowseGridMode(gridMode)
        }
    }

    private fun restoreGridMode() {
        viewModelScope.launch {
            val pref = sessionPreferences.state.first()
            mutableUiState.update { it.copy(gridMode = pref.seriesBrowseGridMode) }
        }
    }

    private fun refresh(forceRefresh: Boolean) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val seriesResult = sessionRepository.fetchSeriesForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val books = fetchAllBooksForSeriesMatching(forceRefresh = forceRefresh)
                    val booksBySeries = mutableMapOf<String, MutableList<BookSummary>>()
                    val booksBySeriesId = mutableMapOf<String, MutableList<BookSummary>>()
                    val detailCache = mutableMapOf<String, BookSummary?>()
                    books.forEach { book ->
                        seriesMatchKeys(book).forEach { key ->
                            booksBySeries.getOrPut(key) { mutableListOf() }.add(book)
                        }
                        book.seriesIds.forEach { seriesId ->
                            val normalizedId = seriesId.trim()
                            if (normalizedId.isNotBlank()) {
                                booksBySeriesId.getOrPut(normalizedId) { mutableListOf() }.add(book)
                            }
                        }
                    }

                    val cards = buildList {
                        seriesResult.value.forEach { series ->
                            val matchedBooks = booksBySeries[normalizeSeriesName(series.name)].orEmpty()
                                .ifEmpty { booksBySeriesId[series.id].orEmpty() }
                            val preferredCoverUrl = matchedBooks.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
                                ?: series.imageUrl
                                ?: matchedBooks.firstOrNull()?.coverUrl
                                ?: resolveSeriesCoverFromDetails(
                                    series = series,
                                    books = books,
                                    detailCache = detailCache
                                )
                            add(
                                SeriesBrowseCard(
                                    id = series.id,
                                    name = series.name,
                                    subtitle = series.subtitle ?: matchedBooks.takeIf { it.isNotEmpty() }?.size?.let { "$it books" },
                                    coverUrl = preferredCoverUrl
                                )
                            )
                        }
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            series = cards
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = seriesResult.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchAllBooksForSeriesMatching(forceRefresh: Boolean): List<BookSummary> {
        val books = mutableListOf<BookSummary>()
        var page = 0
        while (page < SERIES_BOOKS_MAX_PAGES) {
            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = SERIES_BOOKS_PAGE_SIZE,
                    page = page,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    val batch = result.value
                    if (batch.isEmpty()) break
                    books += batch
                    if (batch.size < SERIES_BOOKS_PAGE_SIZE) break
                    page += 1
                }

                is AppResult.Error -> break
            }
        }
        return books.distinctBy { it.id }
    }

    private suspend fun resolveSeriesCoverFromDetails(
        series: NamedEntitySummary,
        books: List<BookSummary>,
        detailCache: MutableMap<String, BookSummary?>
    ): String? {
        val normalizedSeriesName = normalizeSeriesName(series.name)
        books.forEach { book ->
            val detailBook = detailCache.getOrPut(book.id) {
                when (val detailResult = sessionRepository.fetchBookDetail(book.id, forceRefresh = false)) {
                    is AppResult.Success -> detailResult.value.book
                    is AppResult.Error -> null
                }
            } ?: return@forEach

            val matchesById = detailBook.seriesIds.any { candidateId ->
                seriesIdsLikelyMatch(candidateId, series.id)
            }
            val matchesByName = buildList {
                detailBook.seriesName?.let(::add)
                addAll(detailBook.seriesNames)
            }.any { candidateName ->
                val normalizedCandidate = normalizeSeriesName(candidateName)
                normalizedCandidate.isNotBlank() &&
                    (
                        normalizedCandidate == normalizedSeriesName ||
                            normalizedCandidate.contains(normalizedSeriesName) ||
                            normalizedSeriesName.contains(normalizedCandidate)
                        )
            }
            if (matchesById || matchesByName) {
                val resolvedCover = detailBook.coverUrl ?: book.coverUrl
                if (!resolvedCover.isNullOrBlank()) {
                    return resolvedCover
                }
            }
        }
        return null
    }
}

@HiltViewModel
class CollectionsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionsBrowseUiState())
    val uiState: StateFlow<CollectionsBrowseUiState> = mutableUiState.asStateFlow()
    private val inFlightCoverLoads = mutableSetOf<String>()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun refreshLibrary() {
        EntityCoverStackMemoryCache.clearCollections()
        mutableUiState.update { it.copy(coverStackByCollectionId = emptyMap()) }
        refresh(forceRefresh = true, reloadAllCoverStacks = true)
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createCollection(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection created.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun renameCollection(collectionId: String, name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.renameCollection(collectionId = collectionId, name = name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection renamed.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.deleteCollection(collectionId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection deleted.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private fun refresh(forceRefresh: Boolean, reloadAllCoverStacks: Boolean = false) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchCollectionsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val previousState = uiState.value
                    val filtered = result.value.filterNot { isStillShelfProbeCollection(it.name) }
                    val visibleIds = filtered.map { it.id }.toSet()
                    val cachedStacks = EntityCoverStackMemoryCache.collectionsForIds(visibleIds)
                    val retainedStacks = previousState.coverStackByCollectionId.filterKeys { id ->
                        visibleIds.contains(id)
                    }
                    val mergedStacks = retainedStacks + cachedStacks
                    val previousById = previousState.entities.associateBy { it.id }
                    val collectionsToReload = filtered.filter { collection ->
                        val previous = previousById[collection.id]
                        val hasCoverStack = mergedStacks.containsKey(collection.id)
                        reloadAllCoverStacks ||
                            !hasCoverStack ||
                            (previous != null && previous.subtitle != collection.subtitle)
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = filtered,
                            coverStackByCollectionId = mergedStacks
                        )
                    }
                    if (collectionsToReload.isNotEmpty()) {
                        loadCollectionCoverStacks(collections = collectionsToReload, forceRefresh = forceRefresh)
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadCollectionCoverStacks(
        collections: List<NamedEntitySummary>,
        forceRefresh: Boolean
    ) {
        collections.forEach { collection ->
            if (!inFlightCoverLoads.add(collection.id)) return@forEach

            viewModelScope.launch {
                try {
                    val covers = when (
                        val result = sessionRepository.fetchCollectionBooks(
                            collectionId = collection.id,
                            forceRefresh = forceRefresh
                        )
                    ) {
                        is AppResult.Success -> result.value
                            .mapNotNull { it.coverUrl?.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .take(3)

                        is AppResult.Error -> emptyList()
                    }

                    mutableUiState.update { state ->
                        if (state.entities.none { it.id == collection.id }) {
                            state
                        } else {
                            val current = state.coverStackByCollectionId[collection.id].orEmpty()
                            if (current == covers) {
                                state
                            } else {
                                val updatedStacks = if (covers.isEmpty()) {
                                    state.coverStackByCollectionId - collection.id
                                } else {
                                    state.coverStackByCollectionId + (collection.id to covers)
                                }
                                EntityCoverStackMemoryCache.updateCollection(
                                    collectionId = collection.id,
                                    covers = covers
                                )
                                state.copy(coverStackByCollectionId = updatedStacks)
                            }
                        }
                    }
                } finally {
                    inFlightCoverLoads.remove(collection.id)
                }
            }
        }
    }
}

@HiltViewModel
class PlaylistsBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionsBrowseUiState())
    val uiState: StateFlow<CollectionsBrowseUiState> = mutableUiState.asStateFlow()
    private val inFlightCoverLoads = mutableSetOf<String>()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun refreshLibrary() {
        EntityCoverStackMemoryCache.clearPlaylists()
        mutableUiState.update { it.copy(coverStackByCollectionId = emptyMap()) }
        refresh(forceRefresh = true, reloadAllCoverStacks = true)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist created.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.renamePlaylist(playlistId = playlistId, name = name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist renamed.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.deletePlaylist(playlistId)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist deleted.") }
                    refresh(forceRefresh = true)
                }
                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private fun refresh(forceRefresh: Boolean, reloadAllCoverStacks: Boolean = false) {
        if (uiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchPlaylistsForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    val previousState = uiState.value
                    val visibleIds = result.value.map { it.id }.toSet()
                    val cachedStacks = EntityCoverStackMemoryCache.playlistsForIds(visibleIds)
                    val retainedStacks = previousState.coverStackByCollectionId.filterKeys { id ->
                        visibleIds.contains(id)
                    }
                    val mergedStacks = retainedStacks + cachedStacks
                    val previousById = previousState.entities.associateBy { it.id }
                    val playlistsToReload = result.value.filter { playlist ->
                        val previous = previousById[playlist.id]
                        val hasCoverStack = mergedStacks.containsKey(playlist.id)
                        reloadAllCoverStacks ||
                            !hasCoverStack ||
                            (previous != null && previous.subtitle != playlist.subtitle)
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            entities = result.value,
                            coverStackByCollectionId = mergedStacks
                        )
                    }
                    if (playlistsToReload.isNotEmpty()) {
                        loadPlaylistCoverStacks(playlists = playlistsToReload, forceRefresh = forceRefresh)
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadPlaylistCoverStacks(
        playlists: List<NamedEntitySummary>,
        forceRefresh: Boolean
    ) {
        playlists.forEach { playlist ->
            if (!inFlightCoverLoads.add(playlist.id)) return@forEach

            viewModelScope.launch {
                try {
                    val covers = when (
                        val result = sessionRepository.fetchPlaylistBooks(
                            playlistId = playlist.id,
                            forceRefresh = forceRefresh
                        )
                    ) {
                        is AppResult.Success -> result.value
                            .mapNotNull { it.coverUrl?.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .take(3)

                        is AppResult.Error -> emptyList()
                    }

                    mutableUiState.update { state ->
                        if (state.entities.none { it.id == playlist.id }) {
                            state
                        } else {
                            val current = state.coverStackByCollectionId[playlist.id].orEmpty()
                            if (current == covers) {
                                state
                            } else {
                                val updatedStacks = if (covers.isEmpty()) {
                                    state.coverStackByCollectionId - playlist.id
                                } else {
                                    state.coverStackByCollectionId + (playlist.id to covers)
                                }
                                EntityCoverStackMemoryCache.updatePlaylist(
                                    playlistId = playlist.id,
                                    covers = covers
                                )
                                state.copy(coverStackByCollectionId = updatedStacks)
                            }
                        }
                    }
                } finally {
                    inFlightCoverLoads.remove(playlist.id)
                }
            }
        }
    }
}

@HiltViewModel
class BookmarksBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BookmarksBrowseUiState())
    val uiState: StateFlow<BookmarksBrowseUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false, silent = false)
    }

    fun refresh() {
        refresh(forceRefresh = true, silent = false)
    }

    fun refreshSilent() {
        refresh(forceRefresh = false, silent = true)
    }

    private fun refresh(forceRefresh: Boolean, silent: Boolean) {
        if (uiState.value.isLoading) return
        if (!silent || uiState.value.bookmarks.isEmpty()) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookmarksForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            bookmarks = result.value
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

private object EntityCoverStackMemoryCache {
    private val collectionStacks = mutableMapOf<String, List<String>>()
    private val playlistStacks = mutableMapOf<String, List<String>>()

    @Synchronized
    fun collectionsForIds(ids: Set<String>): Map<String, List<String>> {
        val stacks = LinkedHashMap<String, List<String>>(ids.size)
        ids.forEach { id -> collectionStacks[id]?.let { stacks[id] = it } }
        return stacks
    }

    @Synchronized
    fun playlistsForIds(ids: Set<String>): Map<String, List<String>> {
        val stacks = LinkedHashMap<String, List<String>>(ids.size)
        ids.forEach { id -> playlistStacks[id]?.let { stacks[id] = it } }
        return stacks
    }

    @Synchronized
    fun updateCollection(collectionId: String, covers: List<String>) {
        if (covers.isEmpty()) {
            collectionStacks.remove(collectionId)
        } else {
            collectionStacks[collectionId] = covers
        }
    }

    @Synchronized
    fun updatePlaylist(playlistId: String, covers: List<String>) {
        if (covers.isEmpty()) {
            playlistStacks.remove(playlistId)
        } else {
            playlistStacks[playlistId] = covers
        }
    }

    @Synchronized
    fun clearCollections() {
        collectionStacks.clear()
    }

    @Synchronized
    fun clearPlaylists() {
        playlistStacks.clear()
    }
}

private fun normalizeSeriesName(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

private fun seriesMatchKeys(book: BookSummary): Set<String> {
    return buildSet {
        book.seriesName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add(normalizeSeriesName(it)) }
        book.seriesNames.forEach { seriesName ->
            val normalized = normalizeSeriesName(seriesName)
            if (normalized.isNotBlank()) {
                add(normalized)
            }
        }
    }
}

private fun seriesIdsLikelyMatch(candidateId: String, targetId: String): Boolean {
    val normalizedCandidate = candidateId.trim()
    val normalizedTarget = targetId.trim()
    if (normalizedCandidate.isBlank() || normalizedTarget.isBlank()) return false
    return normalizedCandidate.equals(normalizedTarget, ignoreCase = true) ||
        normalizedCandidate.endsWith(normalizedTarget, ignoreCase = true) ||
        normalizedTarget.endsWith(normalizedCandidate, ignoreCase = true)
}

private fun isStillShelfProbeCollection(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized.startsWith("stillshelf probe") ||
        normalized.startsWith("stillshelf dup probe") ||
        normalized.startsWith("stillshelf probe add")
}
