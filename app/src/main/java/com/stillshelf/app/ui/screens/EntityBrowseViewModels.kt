package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.NamedEntitySummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.common.withBookProgressMutation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class EntityBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val errorMessage: String? = null
)

data class SeriesBrowseCard(
    val id: String,
    val name: String,
    val subtitle: String?,
    val coverUrls: List<String> = emptyList()
)

data class SeriesBrowseUiState(
    val isLoading: Boolean = false,
    val series: List<SeriesBrowseCard> = emptyList(),
    val gridMode: Boolean = true,
    val errorMessage: String? = null
)

internal data class SeriesBrowseCandidate(
    val card: SeriesBrowseCard,
    val matchedBookIds: Set<String>,
    val inferredParentKeys: Set<String> = emptySet()
)

data class CollectionsBrowseUiState(
    val isLoading: Boolean = false,
    val entities: List<NamedEntitySummary> = emptyList(),
    val coverStackByCollectionId: Map<String, List<String>> = emptyMap(),
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val hasLoadedOnce: Boolean = false
)

data class BookmarksBrowseUiState(
    val isLoading: Boolean = false,
    val bookmarks: List<BookmarkEntry> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null,
    val hasLoadedOnce: Boolean = false
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
                            val initialMatchedBooks = booksBySeries[normalizeSeriesName(series.name)].orEmpty()
                                .ifEmpty { booksBySeriesId[series.id].orEmpty() }
                            val expectedCount = series.subtitle
                                ?.trim()
                                ?.substringBefore(" ")
                                ?.toIntOrNull()
                            val matchedBooks = if (
                                initialMatchedBooks.isEmpty() ||
                                (expectedCount != null && initialMatchedBooks.size < expectedCount)
                            ) {
                                resolveSeriesBooksFromDetails(
                                    series = series,
                                    books = books,
                                    initialMatchedBooks = initialMatchedBooks,
                                    expectedCount = expectedCount,
                                    detailCache = detailCache
                                )
                            } else {
                                initialMatchedBooks
                            }
                            val preferredCoverUrls = resolvePreferredSeriesCovers(
                                series = series,
                                matchedBooks = matchedBooks
                            )
                            add(
                                SeriesBrowseCandidate(
                                    card = SeriesBrowseCard(
                                        id = series.id,
                                        name = series.name,
                                        subtitle = series.subtitle ?: matchedBooks.takeIf { it.isNotEmpty() }?.size?.let { "$it books" },
                                        coverUrls = preferredCoverUrls
                                    ),
                                    matchedBookIds = matchedBooks.map { it.id }.toSet(),
                                    inferredParentKeys = resolveExplicitParentSeriesKeys(
                                        series = series,
                                        matchedBooks = matchedBooks
                                    )
                                )
                            )
                        }
                    }
                    val visibleCards = filterNestedSeriesCards(cards)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            series = visibleCards
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

    private fun resolvePreferredSeriesCovers(
        series: NamedEntitySummary,
        matchedBooks: List<BookSummary>
    ): List<String> {
        return buildList {
            matchedBooks.forEach { book ->
                val coverUrl = book.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                add(coverUrl)
            }
            series.imageUrl?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
            .distinctBy { it.lowercase() }
            .take(3)
    }

    private suspend fun resolveSeriesBooksFromDetails(
        series: NamedEntitySummary,
        books: List<BookSummary>,
        initialMatchedBooks: List<BookSummary>,
        expectedCount: Int?,
        detailCache: MutableMap<String, BookSummary?>
    ): List<BookSummary> {
        val resolved = LinkedHashMap<String, BookSummary>()
        initialMatchedBooks.forEach { book -> resolved[book.id] = book }
        books.forEach { book ->
            if (expectedCount != null && expectedCount > 0 && resolved.size >= expectedCount) {
                return resolved.values.toList()
            }
            val detailBook = detailCache.getOrPut(book.id) {
                when (val detailResult = sessionRepository.fetchBookDetail(book.id, forceRefresh = false)) {
                    is AppResult.Success -> detailResult.value.book
                    is AppResult.Error -> null
                }
            } ?: return@forEach
            val matchesById = detailBook.seriesIds.any { candidateId ->
                seriesIdsLikelyMatch(candidateId, series.id)
            }
            val matchesByName = seriesMatchKeys(detailBook).contains(normalizeSeriesName(series.name))
            if (matchesById || matchesByName) {
                resolved[book.id] = book.copy(
                    seriesName = detailBook.seriesName ?: book.seriesName,
                    seriesNames = if (detailBook.seriesNames.isNotEmpty()) detailBook.seriesNames else book.seriesNames,
                    seriesIds = if (detailBook.seriesIds.isNotEmpty()) detailBook.seriesIds else book.seriesIds,
                    seriesSequence = detailBook.seriesSequence ?: book.seriesSequence,
                    coverUrl = detailBook.coverUrl ?: book.coverUrl
                )
            }
        }
        return resolved.values.toList()
    }

    private fun filterNestedSeriesCards(candidates: List<SeriesBrowseCandidate>): List<SeriesBrowseCard> {
        return candidates
            .filterNot { candidate -> isNestedSeriesCandidate(candidate, candidates) }
            .map { it.card }
    }

    private fun resolveExplicitParentSeriesKeys(
        series: NamedEntitySummary,
        matchedBooks: List<BookSummary>
    ): Set<String> {
        if (matchedBooks.isEmpty()) return emptySet()
        val selfKeys = buildSet {
            addAll(seriesIdentityKeys(series.id, series.name))
        }
        return matchedBooks
            .map { book ->
                buildSet {
                    book.seriesIds.forEach { seriesId ->
                        val normalized = seriesId.trim()
                        if (normalized.isNotBlank()) {
                            add(normalized)
                        }
                    }
                    addAll(seriesMatchKeys(book))
                } - selfKeys
            }
            .reduceOrNull { acc, keys -> acc.intersect(keys) }
            .orEmpty()
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
        refresh(forceRefresh = false, silent = false)
    }

    fun refresh() {
        refresh(forceRefresh = true, silent = false)
    }

    fun refreshSilent() {
        refresh(forceRefresh = true, silent = true)
    }

    fun refreshLibrary() {
        EntityCoverStackMemoryCache.clearCollections()
        refresh(forceRefresh = true, silent = false, reloadAllCoverStacks = true)
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createCollection(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Collection created.") }
                    refresh(forceRefresh = true, silent = true)
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
                    refresh(forceRefresh = true, silent = true)
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
                    refresh(forceRefresh = true, silent = true)
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

    private fun refresh(
        forceRefresh: Boolean,
        silent: Boolean,
        reloadAllCoverStacks: Boolean = false
    ) {
        if (uiState.value.isLoading) return
        if (!silent || !uiState.value.hasLoadedOnce) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

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
                            coverStackByCollectionId = mergedStacks,
                            errorMessage = null,
                            hasLoadedOnce = true
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
                            errorMessage = if (silent && it.hasLoadedOnce) it.errorMessage else result.message,
                            hasLoadedOnce = true
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
        refresh(forceRefresh = false, silent = false)
    }

    fun refresh() {
        refresh(forceRefresh = true, silent = false)
    }

    fun refreshSilent() {
        refresh(forceRefresh = true, silent = true)
    }

    fun refreshLibrary() {
        EntityCoverStackMemoryCache.clearPlaylists()
        refresh(forceRefresh = true, silent = false, reloadAllCoverStacks = true)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            when (val result = sessionRepository.createPlaylist(name)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Playlist created.") }
                    refresh(forceRefresh = true, silent = true)
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
                    refresh(forceRefresh = true, silent = true)
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
                    refresh(forceRefresh = true, silent = true)
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

    private fun refresh(
        forceRefresh: Boolean,
        silent: Boolean,
        reloadAllCoverStacks: Boolean = false
    ) {
        if (uiState.value.isLoading) return
        if (!silent || !uiState.value.hasLoadedOnce) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

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
                            coverStackByCollectionId = mergedStacks,
                            errorMessage = null,
                            hasLoadedOnce = true
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
                            errorMessage = if (silent && it.hasLoadedOnce) it.errorMessage else result.message,
                            hasLoadedOnce = true
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
        observeBookProgressMutations()
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
        if (!silent || !uiState.value.hasLoadedOnce) {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        viewModelScope.launch {
            when (val result = sessionRepository.fetchBookmarksForActiveLibrary(forceRefresh = forceRefresh)) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            bookmarks = result.value,
                            errorMessage = null,
                            hasLoadedOnce = true
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (silent && it.hasLoadedOnce) it.errorMessage else result.message,
                            hasLoadedOnce = true
                        )
                    }
                }
            }
        }
    }

    fun editBookmark(entry: BookmarkEntry, newTitle: String) {
        val normalizedTitle = newTitle.trim()
        if (normalizedTitle.isBlank()) {
            mutableUiState.update { it.copy(actionMessage = "Bookmark title can't be empty.") }
            return
        }
        val previousBookmarks = uiState.value.bookmarks
        mutableUiState.update { state ->
            state.copy(
                bookmarks = state.bookmarks.map { existing ->
                    if (bookmarkEntriesMatch(existing, entry)) {
                        existing.copy(bookmark = existing.bookmark.copy(title = normalizedTitle))
                    } else {
                        existing
                    }
                }
            )
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.updateBookmark(
                    bookId = entry.book.id,
                    bookmark = entry.bookmark,
                    newTitle = normalizedTitle
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Bookmark updated.") }
                    refresh(forceRefresh = true, silent = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            bookmarks = previousBookmarks,
                            actionMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun deleteBookmark(entry: BookmarkEntry) {
        val previousBookmarks = uiState.value.bookmarks
        mutableUiState.update { state ->
            state.copy(
                bookmarks = state.bookmarks.filterNot { existing ->
                    bookmarkEntriesMatch(existing, entry)
                }
            )
        }
        viewModelScope.launch {
            when (val result = sessionRepository.deleteBookmark(entry.book.id, entry.bookmark)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Bookmark deleted.") }
                    refresh(forceRefresh = true, silent = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            bookmarks = previousBookmarks,
                            actionMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun deleteAllBookmarks() {
        val previousBookmarks = uiState.value.bookmarks
        if (previousBookmarks.isEmpty()) return
        mutableUiState.update { it.copy(bookmarks = emptyList()) }
        viewModelScope.launch {
            var deletedCount = 0
            var failedCount = 0
            previousBookmarks.forEach { entry ->
                when (sessionRepository.deleteBookmark(entry.book.id, entry.bookmark)) {
                    is AppResult.Success -> deletedCount += 1
                    is AppResult.Error -> failedCount += 1
                }
            }
            refresh(forceRefresh = true, silent = true)
            mutableUiState.update {
                it.copy(
                    actionMessage = when {
                        failedCount == 0 -> "Deleted all bookmarks."
                        deletedCount == 0 -> "Unable to delete bookmarks."
                        else -> "Deleted $deletedCount bookmarks. $failedCount failed."
                    }
                )
            }
        }
    }

    fun clearActionMessage() {
        mutableUiState.update { it.copy(actionMessage = null) }
    }

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(
                        bookmarks = state.bookmarks.map { it.withBookProgressMutation(mutation) }
                    )
                }
            }
        }
    }

    private fun bookmarkEntriesMatch(source: BookmarkEntry, target: BookmarkEntry): Boolean {
        if (!source.book.id.equals(target.book.id, ignoreCase = true)) return false
        return bookmarksMatch(source.bookmark, target.bookmark)
    }

    private fun bookmarksMatch(source: BookBookmark, target: BookBookmark): Boolean {
        val sourceId = source.id.trim()
        val targetId = target.id.trim()
        if (sourceId.isNotBlank() && targetId.isNotBlank() && sourceId.equals(targetId, ignoreCase = true)) {
            return true
        }
        if (!source.libraryItemId.equals(target.libraryItemId, ignoreCase = true)) {
            return false
        }
        val sourceTime = source.timeSeconds
        val targetTime = target.timeSeconds
        val timeMatches = sourceTime != null && targetTime != null && abs(sourceTime - targetTime) <= 2.0
        val titleMatches = !source.title.isNullOrBlank() &&
            !target.title.isNullOrBlank() &&
            source.title.trim().equals(target.title.trim(), ignoreCase = true)
        return timeMatches || titleMatches
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

private fun seriesIdentityKeys(seriesId: String?, seriesName: String?): Set<String> {
    return buildSet {
        seriesId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it) }
        seriesName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeSeriesName)
            ?.takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }
}

internal fun isNestedSeriesCandidate(
    candidate: SeriesBrowseCandidate,
    allCandidates: List<SeriesBrowseCandidate>
): Boolean {
    val candidateBookIds = candidate.matchedBookIds
    if (candidateBookIds.isEmpty()) return false
    return allCandidates.any { other ->
        val otherKeys = seriesIdentityKeys(other.card.id, other.card.name)
        other.card.id != candidate.card.id &&
            other.matchedBookIds.size > candidateBookIds.size &&
            otherKeys.any { it in candidate.inferredParentKeys } &&
            candidateBookIds.all { bookId -> bookId in other.matchedBookIds }
    }
}

private fun isStillShelfProbeCollection(name: String): Boolean {
    val normalized = name.trim().lowercase()
    return normalized.startsWith("stillshelf probe") ||
        normalized.startsWith("stillshelf dup probe") ||
        normalized.startsWith("stillshelf probe add")
}
