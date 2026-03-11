package com.stillshelf.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.stillshelf.app.core.datastore.SessionPreferences
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.core.util.formatDurationHoursMinutes
import com.stillshelf.app.core.util.hasFinishedProgress
import com.stillshelf.app.core.util.hasStartedProgress
import com.stillshelf.app.core.util.remainingTimeLabel
import com.stillshelf.app.core.model.SeriesDetailEntry
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.downloads.manager.BookDownloadManager
import com.stillshelf.app.downloads.manager.DownloadStatus
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.common.WideCoverBackgroundBlur
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.common.withBookProgressMutation
import com.stillshelf.app.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.text.Regex

data class FacetBooksUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val books: List<BookSummary> = emptyList(),
    val authorImageUrl: String? = null,
    val authorAbout: String? = null,
    val errorMessage: String? = null,
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressByBookId: Map<String, Int> = emptyMap(),
    val actionMessage: String? = null
)

data class SeriesDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val title: String = "",
    val entries: List<SeriesDetailEntry> = emptyList(),
    val canCollapseSubseries: Boolean = false,
    val errorMessage: String? = null,
    val downloadedBookIds: Set<String> = emptySet(),
    val downloadProgressByBookId: Map<String, Int> = emptyMap(),
    val actionMessage: String? = null
) {
    val books: List<BookSummary>
        get() = entries.mapNotNull { entry ->
            (entry as? SeriesDetailEntry.BookItem)?.book
        }

    val totalBookCount: Int
        get() = entries.sumOf { entry ->
            when (entry) {
                is SeriesDetailEntry.BookItem -> 1
                is SeriesDetailEntry.SubseriesItem -> entry.bookCount.coerceAtLeast(1)
            }
        }
}

private val FacetBackTitleSpacing = 12.dp
private val FacetSeriesStackMinLayerExtent = 42.dp
private val FacetSeriesStackStep = 5.dp
private val FacetSeriesStackFrontShadow = 1.dp
private val FacetSeriesStackBackShadow = 2.8.dp
private val FacetSeriesStackCornerShape = RoundedCornerShape(6.dp)
private val FacetSeriesStackBackgroundBlur = 44.dp

private fun facetStackedLayerExtent(baseExtent: Dp, layer: Int, shiftStep: Dp): Dp {
    val shrink = shiftStep * (layer.coerceAtLeast(0) * 5)
    return (baseExtent - shrink).coerceAtLeast(FacetSeriesStackMinLayerExtent)
}

private fun facetStackedLayerShadow(layer: Int, layerCount: Int): Dp {
    return if (layer == layerCount - 1) FacetSeriesStackFrontShadow else FacetSeriesStackBackShadow
}

@Composable
private fun FacetSeriesStackCoverLayers(
    coverUrl: String?,
    contentDescription: String?,
    layerCount: Int,
    frameWidth: Dp,
    frameHeight: Dp,
    modifier: Modifier = Modifier
) {
    val resolvedLayerCount = layerCount.coerceIn(2, 3)
    val layerShape = FacetSeriesStackCornerShape
    Box(
        modifier = modifier.clipToBounds()
    ) {
        repeat(resolvedLayerCount) { layer ->
            val cardWidth = facetStackedLayerExtent(frameWidth, layer, FacetSeriesStackStep)
            val cardHeight = facetStackedLayerExtent(frameHeight, layer, FacetSeriesStackStep)
            val xOffset = (frameWidth - cardWidth).coerceAtLeast(0.dp)
            val yOffset = (frameHeight - cardHeight).coerceAtLeast(0.dp)
            val layerShadow = facetStackedLayerShadow(layer = layer, layerCount = resolvedLayerCount)
            FramedCoverImage(
                coverUrl = coverUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .width(cardWidth)
                    .height(cardHeight)
                    .shadow(elevation = layerShadow, shape = layerShape, clip = false)
                    .graphicsLayer(alpha = 1f),
                shape = layerShape,
                contentScale = ContentScale.Fit,
                backgroundBlur = FacetSeriesStackBackgroundBlur
            )
        }
    }
}

data class GenreSummary(
    val name: String,
    val count: Int
)

data class GenresUiState(
    val isLoading: Boolean = false,
    val genres: List<GenreSummary> = emptyList(),
    val errorMessage: String? = null
)

enum class AuthorLayoutMode {
    Grid,
    List
}

private sealed interface AuthorDisplayEntry {
    val stableKey: String

    data class BookItem(val book: BookSummary) : AuthorDisplayEntry {
        override val stableKey: String = "book:${book.id}"
    }

    data class SeriesItem(
        val seriesName: String,
        val books: List<BookSummary>
    ) : AuthorDisplayEntry {
        override val stableKey: String = "series:${normalizeSeriesGroupKey(seriesName)}"
        val leadBook: BookSummary get() = books.first()
        val count: Int get() = books.size
    }
}

@HiltViewModel
class AuthorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    private val authorName = savedStateHandle.get<String>(DetailRoute.AUTHOR_NAME_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(FacetBooksUiState(isLoading = true, title = authorName))
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()
    private val mutableLayoutMode = MutableStateFlow(AuthorLayoutMode.Grid)
    val layoutMode: StateFlow<AuthorLayoutMode> = mutableLayoutMode.asStateFlow()
    private val mutableCollapseSeries = MutableStateFlow(true)
    val collapseSeries: StateFlow<Boolean> = mutableCollapseSeries.asStateFlow()

    init {
        viewModelScope.launch { restoreUiPreferences() }
        observeDownloadedState()
        observeBookProgressMutations()
        refresh(forceRefresh = false)
    }

    fun setLayoutMode(value: AuthorLayoutMode) {
        mutableLayoutMode.value = value
        viewModelScope.launch {
            sessionPreferences.setAuthorLayoutMode(value.name)
        }
    }

    fun toggleCollapseSeries() {
        val nextValue = !mutableCollapseSeries.value
        mutableCollapseSeries.value = nextValue
        viewModelScope.launch {
            sessionPreferences.setAuthorCollapseSeries(nextValue)
        }
    }

    private suspend fun restoreUiPreferences() {
        val pref = sessionPreferences.state.first()
        mutableLayoutMode.value = pref.authorLayoutMode
            ?.let { raw -> enumValueOrNull<AuthorLayoutMode>(raw) }
            ?: AuthorLayoutMode.Grid
        mutableCollapseSeries.value = pref.authorCollapseSeries
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    fun markAsFinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = true)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as finished. Progress is now 100%.") }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun markAsUnfinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as unfinished.") }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun resetBookProgress(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    refresh(forceRefresh = true)
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun toggleDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val book = uiState.value.books.firstOrNull { it.id == bookId }
            if (book == null) {
                mutableUiState.update { it.copy(actionMessage = "Unable to find book for download.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(book)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = result.value.message) }
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(books = state.books.map { it.withBookProgressMutation(mutation) })
                }
            }
        }
    }

    private fun refresh(forceRefresh: Boolean) {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val matchedAuthor = when (
                val authorsResult = sessionRepository.fetchAuthorsForActiveLibrary(
                    limit = 400,
                    page = 0,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    authorsResult.value.firstOrNull { it.name.equals(authorName, ignoreCase = true) }
                }

                is AppResult.Error -> null
            }
            val authorImageUrl = matchedAuthor?.imageUrl
            val authorAbout = matchedAuthor?.description?.takeIf { it.isNotBlank() }

            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = 400,
                    page = 0,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    val books = result.value.filter {
                        doesBookMatchAuthor(it.authorName, authorName)
                    }.sortedBy { it.title.lowercase() }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = books,
                            authorImageUrl = authorImageUrl,
                            authorAbout = authorAbout
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            authorImageUrl = authorImageUrl,
                            authorAbout = authorAbout,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun observeDownloadedState() {
        viewModelScope.launch {
            bookDownloadManager.items.collect { items ->
                val downloadedIds = items
                    .filter { it.status == DownloadStatus.Completed }
                    .map { it.bookId }
                    .toSet()
                val progressByBookId = items
                    .filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
                    .associate { it.bookId to it.progressPercent.coerceIn(0, 100) }
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = downloadedIds,
                        downloadProgressByBookId = progressByBookId
                    )
                }
            }
        }
    }
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences,
    private val bookDownloadManager: BookDownloadManager
) : ViewModel() {
    companion object {
        private const val SERIES_BOOKS_PAGE_SIZE = 400
        private const val SERIES_BOOKS_MAX_PAGES = 100
    }

    private val seriesName = savedStateHandle.get<String>(DetailRoute.SERIES_NAME_ARG).orEmpty()
    private val seriesId = savedStateHandle.get<String>(DetailRoute.SERIES_ID_ARG)?.trim()?.takeIf { it.isNotBlank() }
    private val mutableUiState = MutableStateFlow(SeriesDetailUiState(isLoading = true, title = seriesName))
    val uiState: StateFlow<SeriesDetailUiState> = mutableUiState.asStateFlow()
    private val mutableListMode = MutableStateFlow(true)
    val listMode: StateFlow<Boolean> = mutableListMode.asStateFlow()
    private val mutableCollapseSubseries = MutableStateFlow(true)
    val collapseSubseries: StateFlow<Boolean> = mutableCollapseSubseries.asStateFlow()

    private data class SeriesMatchTarget(
        val id: String?,
        val expectedCount: Int?
    )

    private data class DetailedSeriesBook(
        val book: BookSummary,
        val seriesNames: List<String>,
        val seriesIds: List<String>
    )

    private data class ChildSeriesCandidate(
        val key: String,
        val id: String?,
        val name: String
    )

    private data class SeriesEntriesResult(
        val entries: List<SeriesDetailEntry>,
        val hasCollapsibleSubseries: Boolean
    )

    init {
        viewModelScope.launch {
            sessionPreferences.state.first().let { prefs ->
                mutableListMode.value = prefs.seriesDetailListMode
                mutableCollapseSubseries.value = prefs.seriesDetailCollapseSubseries
            }
        }
        observeDownloadedState()
        observeBookProgressMutations()
        refresh(forceRefresh = false, isUserRefresh = false)
    }

    fun setListMode(value: Boolean) {
        mutableListMode.value = value
        viewModelScope.launch {
            sessionPreferences.setSeriesDetailListMode(value)
        }
    }

    fun setCollapseSubseries(value: Boolean) {
        mutableCollapseSubseries.value = value
        viewModelScope.launch {
            sessionPreferences.setSeriesDetailCollapseSubseries(value)
        }
        refresh(forceRefresh = false, isUserRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true, isUserRefresh = true)
    }

    fun markAsFinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = true)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as finished. Progress is now 100%.") }
                    refresh(forceRefresh = true, isUserRefresh = false)
                }

                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun markAsUnfinished(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (val result = sessionRepository.markBookFinished(bookId = bookId, finished = false)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Marked as unfinished.") }
                    refresh(forceRefresh = true, isUserRefresh = false)
                }

                is AppResult.Error -> mutableUiState.update { it.copy(actionMessage = result.message) }
            }
        }
    }

    fun resetBookProgress(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    refresh(forceRefresh = true, isUserRefresh = false)
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun toggleDownload(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            val book = uiState.value.books.firstOrNull { it.id == bookId }
            if (book == null) {
                mutableUiState.update { it.copy(actionMessage = "Unable to find book for download.") }
                return@launch
            }
            when (val result = bookDownloadManager.toggleDownload(book)) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = result.value.message) }
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(
                        entries = state.entries.map { entry ->
                            when (entry) {
                                is SeriesDetailEntry.BookItem -> {
                                    SeriesDetailEntry.BookItem(entry.book.withBookProgressMutation(mutation))
                                }

                                is SeriesDetailEntry.SubseriesItem -> entry
                            }
                        }
                    )
                }
            }
        }
    }

    private fun refresh(forceRefresh: Boolean, isUserRefresh: Boolean) {
        mutableUiState.update {
            it.copy(
                isLoading = true,
                isRefreshing = isUserRefresh,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val collapseSubseries = mutableCollapseSubseries.value
            val targetSeries = resolveTargetSeriesTarget(forceRefresh = forceRefresh)
            val resolvedSeriesId = seriesId ?: targetSeries.id
            val serverResult: AppResult<SeriesEntriesResult>? = if (collapseSubseries && resolvedSeriesId != null) {
                when (val result = sessionRepository.fetchSeriesContentsForActiveLibrary(
                    seriesId = resolvedSeriesId,
                    collapseSubseries = true,
                    forceRefresh = forceRefresh
                )) {
                    is AppResult.Success -> AppResult.Success(
                        SeriesEntriesResult(
                            entries = result.value,
                            hasCollapsibleSubseries = result.value.any { entry -> entry is SeriesDetailEntry.SubseriesItem }
                        )
                    )

                    is AppResult.Error -> result
                }
            } else {
                null
            }
            val shouldLoadFallback = !collapseSubseries ||
                resolvedSeriesId == null ||
                serverResult !is AppResult.Success ||
                serverResult.value.entries.isEmpty() ||
                serverResult.value.entries.none { entry -> entry is SeriesDetailEntry.SubseriesItem }
            val fallbackResult = if (shouldLoadFallback) {
                fetchLegacySeriesEntries(
                    forceRefresh = forceRefresh,
                    targetSeries = targetSeries,
                    collapseSubseries = collapseSubseries
                )
            } else {
                null
            }
            val fallbackHasSubseries = fallbackResult is AppResult.Success &&
                fallbackResult.value.hasCollapsibleSubseries
            val serverHasSubseries = serverResult is AppResult.Success &&
                serverResult.value.hasCollapsibleSubseries
            val result = when {
                fallbackHasSubseries && !serverHasSubseries -> fallbackResult
                serverResult is AppResult.Success && serverResult.value.entries.isNotEmpty() -> serverResult
                fallbackResult is AppResult.Success && fallbackResult.value.entries.isNotEmpty() -> fallbackResult
                serverResult != null -> serverResult
                fallbackResult != null -> fallbackResult
                else -> AppResult.Success(SeriesEntriesResult(entries = emptyList(), hasCollapsibleSubseries = false))
            }
            when (result) {
                is AppResult.Success -> {
                    if (result.value.entries.isEmpty() && !forceRefresh) {
                        refresh(forceRefresh = true, isUserRefresh = false)
                        return@launch
                    }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            entries = result.value.entries,
                            canCollapseSubseries = result.value.hasCollapsibleSubseries
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun fetchLegacySeriesEntries(
        forceRefresh: Boolean,
        targetSeries: SeriesMatchTarget,
        collapseSubseries: Boolean
    ): AppResult<SeriesEntriesResult> {
        return when (val result = fetchAllBooksForSeriesMatching(forceRefresh = forceRefresh)) {
            is AppResult.Success -> {
                val normalizedSeries = normalizeSeriesKey(seriesName)
                val matchedByListMetadata = result.value.filter {
                    val matchesByName = matchesSeriesName(
                        normalizedSeries = normalizedSeries,
                        candidateSeriesNames = buildList {
                            it.seriesName?.let(::add)
                            addAll(it.seriesNames)
                        }
                    )
                    val matchesById = targetSeries.id != null &&
                        it.seriesIds.any { candidateId ->
                            seriesIdsLikelyMatch(candidateId, targetSeries.id)
                        }
                    matchesByName || matchesById
                }
                val matchedBooks = if (matchedByListMetadata.isNotEmpty()) {
                    matchedByListMetadata
                } else if (targetSeries.id != null) {
                    resolveSeriesBooksFromBookDetails(
                        books = result.value,
                        targetSeries = targetSeries,
                        normalizedSeries = normalizedSeries
                    )
                } else {
                    emptyList()
                }
                val detailedBooks = loadDetailedSeriesBooks(matchedBooks)
                val collapsedEntries = buildFallbackSeriesEntries(
                    books = detailedBooks,
                    targetSeries = targetSeries,
                    targetSeriesName = seriesName,
                    collapseSubseries = true
                )
                val displayedEntries = if (collapseSubseries) {
                    collapsedEntries
                } else {
                    buildFallbackSeriesEntries(
                        books = detailedBooks,
                        targetSeries = targetSeries,
                        targetSeriesName = seriesName,
                        collapseSubseries = false
                    )
                }
                AppResult.Success(
                    SeriesEntriesResult(
                        entries = displayedEntries,
                        hasCollapsibleSubseries = collapsedEntries.any { entry -> entry is SeriesDetailEntry.SubseriesItem }
                    )
                )
            }

            is AppResult.Error -> result
        }
    }

    private suspend fun loadDetailedSeriesBooks(books: List<BookSummary>): List<DetailedSeriesBook> = coroutineScope {
        books.map { book ->
            async {
                val detail = when (val detailResult = sessionRepository.fetchBookDetail(book.id, forceRefresh = false)) {
                    is AppResult.Success -> detailResult.value.book
                    is AppResult.Error -> null
                }
                val mergedBook = if (detail != null) {
                    val inferredSequence = extractSeriesSequenceFromText(detail.seriesName.orEmpty())
                        ?: extractSeriesSequenceFromText(detail.title)
                        ?: detail.seriesSequence
                        ?: book.seriesSequence
                    book.copy(
                        seriesName = detail.seriesName ?: book.seriesName,
                        seriesNames = if (detail.seriesNames.isNotEmpty()) detail.seriesNames else book.seriesNames,
                        seriesIds = if (detail.seriesIds.isNotEmpty()) detail.seriesIds else book.seriesIds,
                        seriesSequence = inferredSequence
                    )
                } else {
                    val inferredSequence = extractSeriesSequenceFromText(book.title)
                        ?: extractSeriesSequenceFromText(book.seriesName.orEmpty())
                        ?: book.seriesSequence
                    book.copy(seriesSequence = inferredSequence)
                }
                DetailedSeriesBook(
                    book = mergedBook,
                    seriesNames = detail?.seriesNames?.takeIf { it.isNotEmpty() } ?: mergedBook.seriesNames,
                    seriesIds = detail?.seriesIds?.takeIf { it.isNotEmpty() } ?: mergedBook.seriesIds
                )
            }
        }.awaitAll()
    }

    private fun buildFallbackSeriesEntries(
        books: List<DetailedSeriesBook>,
        targetSeries: SeriesMatchTarget,
        targetSeriesName: String,
        collapseSubseries: Boolean
    ): List<SeriesDetailEntry> {
        val sortedBooks = sortSeriesBooksForDisplay(
            books = books.map { it.book },
            targetSeriesName = targetSeriesName
        )
        if (!collapseSubseries) {
            return sortedBooks.map { book -> SeriesDetailEntry.BookItem(book) }
        }
        val detailedById = books.associateBy { it.book.id }
        val sortedDetailedBooks = sortedBooks.mapNotNull { book -> detailedById[book.id] }
        val normalizedSeries = normalizeSeriesKey(targetSeriesName)
        val childSeriesByBookId = sortedDetailedBooks.associate { detailed ->
            detailed.book.id to resolveChildSeriesCandidate(
                book = detailed,
                targetSeries = targetSeries,
                normalizedSeries = normalizedSeries
            )
        }
        val childGroups = sortedDetailedBooks
            .mapNotNull { detailed ->
                val child = childSeriesByBookId[detailed.book.id] ?: return@mapNotNull null
                child to detailed.book
            }
            .groupBy(keySelector = { it.first.key }, valueTransform = { it.second })
        val collapsibleChildKeys = childGroups
            .filterValues { groupedBooks ->
                groupedBooks.isNotEmpty() && groupedBooks.size < sortedDetailedBooks.size
            }
            .keys

        val emittedChildKeys = mutableSetOf<String>()
        return buildList {
            sortedDetailedBooks.forEach { detailed ->
                val child = childSeriesByBookId[detailed.book.id]
                if (child == null || child.key !in collapsibleChildKeys) {
                    add(SeriesDetailEntry.BookItem(detailed.book))
                    return@forEach
                }
                if (!emittedChildKeys.add(child.key)) {
                    return@forEach
                }
                val groupedBooks = childGroups[child.key].orEmpty()
                add(
                    SeriesDetailEntry.SubseriesItem(
                        id = child.id.orEmpty(),
                        name = child.name,
                        bookCount = groupedBooks.size.coerceAtLeast(1),
                        coverUrl = groupedBooks.firstOrNull()?.coverUrl
                    )
                )
            }
        }
    }

    private fun resolveChildSeriesCandidate(
        book: DetailedSeriesBook,
        targetSeries: SeriesMatchTarget,
        normalizedSeries: String
    ): ChildSeriesCandidate? {
        val candidateNames = if (book.seriesNames.isNotEmpty()) {
            book.seriesNames
        } else {
            listOfNotNull(book.book.seriesName)
        }
        return candidateNames.mapIndexedNotNull { index, name ->
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) return@mapIndexedNotNull null
            val candidateId = book.seriesIds.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
            val matchesTarget = (candidateId != null && targetSeries.id != null &&
                seriesIdsLikelyMatch(candidateId, targetSeries.id)) ||
                matchesSeriesName(
                    normalizedSeries = normalizedSeries,
                    candidateSeriesNames = listOf(trimmedName)
                )
            if (matchesTarget) {
                null
            } else {
                ChildSeriesCandidate(
                    key = candidateId ?: "series:${normalizeSeriesKey(trimmedName)}",
                    id = candidateId,
                    name = trimmedName
                )
            }
        }.firstOrNull()
    }

    private suspend fun resolveTargetSeriesTarget(forceRefresh: Boolean): SeriesMatchTarget {
        return when (val seriesResult = sessionRepository.fetchSeriesForActiveLibrary(forceRefresh = forceRefresh)) {
            is AppResult.Success -> {
                val normalized = normalizeSeriesKey(seriesName)
                val match = seriesResult.value.firstOrNull { series ->
                    normalizeSeriesKey(series.name) == normalized
                }
                SeriesMatchTarget(
                    id = match?.id?.trim()?.takeIf { it.isNotBlank() },
                    expectedCount = match?.subtitle?.let(::extractEntityCount)
                )
            }

            is AppResult.Error -> SeriesMatchTarget(id = null, expectedCount = null)
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

    private fun extractEntityCount(subtitle: String?): Int? {
        val value = subtitle?.trim().orEmpty().substringBefore(" ").toIntOrNull()
        return value?.takeIf { it >= 0 }
    }

    private suspend fun resolveSeriesBooksFromBookDetails(
        books: List<BookSummary>,
        targetSeries: SeriesMatchTarget,
        normalizedSeries: String
    ): List<BookSummary> {
        val resolved = mutableListOf<BookSummary>()
        val targetSeriesId = targetSeries.id
        val expectedCount = targetSeries.expectedCount
        books.forEach { book ->
            val detail = when (val detailResult = sessionRepository.fetchBookDetail(book.id, forceRefresh = false)) {
                is AppResult.Success -> detailResult.value.book
                is AppResult.Error -> null
            } ?: return@forEach

            val matchById = targetSeriesId != null &&
                detail.seriesIds.any { candidateId -> seriesIdsLikelyMatch(candidateId, targetSeriesId) }
            val matchByName = matchesSeriesName(
                normalizedSeries = normalizedSeries,
                candidateSeriesNames = buildList {
                    detail.seriesName?.let(::add)
                    addAll(detail.seriesNames)
                }
            )
            if (matchById || matchByName) {
                resolved += book.copy(
                    seriesName = detail.seriesName ?: book.seriesName,
                    seriesNames = if (detail.seriesNames.isNotEmpty()) detail.seriesNames else book.seriesNames,
                    seriesIds = if (detail.seriesIds.isNotEmpty()) detail.seriesIds else book.seriesIds,
                    seriesSequence = detail.seriesSequence ?: book.seriesSequence
                )
                if (expectedCount != null && expectedCount > 0 && resolved.size >= expectedCount) {
                    return resolved.distinctBy { it.id }
                }
            }
        }
        return resolved.distinctBy { it.id }
    }

    private suspend fun fetchAllBooksForSeriesMatching(forceRefresh: Boolean): AppResult<List<BookSummary>> {
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

                is AppResult.Error -> {
                    if (books.isEmpty()) {
                        return result
                    }
                    break
                }
            }
        }
        return AppResult.Success(books.distinctBy { it.id })
    }

    private fun observeDownloadedState() {
        viewModelScope.launch {
            bookDownloadManager.items.collect { items ->
                val ids = items
                    .filter { it.status == DownloadStatus.Completed }
                    .map { it.bookId }
                    .toSet()
                val progressByBookId = items
                    .filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading }
                    .associate { it.bookId to it.progressPercent.coerceIn(0, 100) }
                mutableUiState.update {
                    it.copy(
                        downloadedBookIds = ids,
                        downloadProgressByBookId = progressByBookId
                    )
                }
            }
        }
    }

    private suspend fun inferSeriesSequence(book: BookSummary): Double? {
        extractSeriesSequenceFromText(book.title)?.let { return it }
        extractSeriesSequenceFromText(book.seriesName.orEmpty())?.let { return it }

        return when (val detailResult = sessionRepository.fetchBookDetail(bookId = book.id, forceRefresh = false)) {
            is AppResult.Success -> {
                val detail = detailResult.value
                extractSeriesSequenceFromText(detail.book.seriesName.orEmpty())
                    ?: extractSeriesSequenceFromText(detail.book.title)
                    ?: detail.book.seriesSequence
                    ?: book.seriesSequence
            }

            is AppResult.Error -> book.seriesSequence
        }
    }
}

private fun extractSeriesSequenceFromTitle(title: String): Double? {
    val match = Regex("(?i)\\bbook\\s*(\\d+(?:\\.\\d+)?)\\b").find(title) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun extractSeriesSequenceFromText(text: String?): Double? {
    val value = text?.trim().orEmpty()
    if (value.isBlank()) return null
    return extractSeriesSequenceFromTitle(value)
        ?: Regex("(?i)#\\s*(\\d+(?:\\.\\d+)?)\\b").find(value)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}

private fun normalizeSeriesKey(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

internal fun matchesSeriesName(
    normalizedSeries: String,
    candidateSeriesNames: List<String>
): Boolean {
    if (normalizedSeries.isBlank()) return false
    return candidateSeriesNames.any { candidateName ->
        val normalizedCandidate = normalizeSeriesKey(candidateName)
        normalizedCandidate.isNotBlank() && normalizedCandidate == normalizedSeries
    }
}

internal fun sortSeriesBooksForDisplay(
    books: List<BookSummary>,
    targetSeriesName: String
): List<BookSummary> {
    val normalizedTargetSeries = normalizeSeriesKey(targetSeriesName)
    return books.sortedWith(
        compareBy<BookSummary> { book ->
            val normalizedPrimarySeries = normalizeSeriesKey(book.seriesName.orEmpty())
            if (
                normalizedTargetSeries.isNotBlank() &&
                normalizedPrimarySeries == normalizedTargetSeries
            ) {
                0
            } else {
                1
            }
        }
            .thenBy { book ->
                book.seriesName
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::cleanedSeriesDisplayName)
                    ?.lowercase()
                    .orEmpty()
            }
            .thenBy { it.seriesSequence ?: Double.MAX_VALUE }
            .thenBy { it.title.lowercase() }
    )
}

private fun normalizeSeriesGroupKey(value: String): String {
    val normalized = normalizeSeriesKey(value)
    if (normalized.isNotBlank()) return normalized
    return value.trim().lowercase()
}

private fun cleanedSeriesDisplayName(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun doesBookMatchAuthor(bookAuthorName: String, targetAuthorName: String): Boolean {
    val target = normalizeAuthorName(targetAuthorName)
    if (target.isBlank()) return false
    return splitAuthorsForMatching(bookAuthorName)
        .map(::normalizeAuthorName)
        .any { it == target }
}

private fun splitAuthorsForMatching(raw: String): List<String> {
    return raw
        .split(Regex("\\s*(?:,|;|&|/|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun normalizeAuthorName(raw: String): String {
    return raw
        .lowercase()
        .replace(Regex("[’‘`´]"), "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun List<BookSummary>.applySeriesStatusFilter(filter: BooksStatusFilter): List<BookSummary> {
    return when (filter) {
        BooksStatusFilter.All -> this
        BooksStatusFilter.Finished -> filter { it.hasFinishedStatusProgress() }
        BooksStatusFilter.InProgress -> filter { it.hasStartedStatusProgress() && !it.hasFinishedStatusProgress() }
        BooksStatusFilter.NotStarted -> filter { !it.hasStartedStatusProgress() && !it.hasFinishedStatusProgress() }
        BooksStatusFilter.NotFinished -> filter { !it.hasFinishedStatusProgress() }
    }
}

private fun List<SeriesDetailEntry>.applySeriesEntryStatusFilter(filter: BooksStatusFilter): List<SeriesDetailEntry> {
    if (filter == BooksStatusFilter.All) return this
    return mapNotNull { entry ->
        when (entry) {
            is SeriesDetailEntry.BookItem -> {
                val keep = when (filter) {
                    BooksStatusFilter.All -> true
                    BooksStatusFilter.Finished -> entry.book.hasFinishedStatusProgress()
                    BooksStatusFilter.InProgress -> entry.book.hasStartedStatusProgress() &&
                        !entry.book.hasFinishedStatusProgress()
                    BooksStatusFilter.NotStarted -> !entry.book.hasStartedStatusProgress() &&
                        !entry.book.hasFinishedStatusProgress()
                    BooksStatusFilter.NotFinished -> !entry.book.hasFinishedStatusProgress()
                }
                if (keep) entry else null
            }

            is SeriesDetailEntry.SubseriesItem -> null
        }
    }
}

private fun BookSummary.hasStartedStatusProgress(): Boolean {
    return hasStartedProgress()
}

private fun BookSummary.hasFinishedStatusProgress(): Boolean {
    return hasFinishedProgress()
}

private fun buildAuthorDisplayEntries(
    books: List<BookSummary>,
    collapseSeries: Boolean
): List<AuthorDisplayEntry> {
    if (!collapseSeries) return books.map { AuthorDisplayEntry.BookItem(it) }

    val entries = mutableListOf<AuthorDisplayEntry>()
    val emittedSeries = mutableSetOf<String>()
    books.forEach { book ->
        val rawSeriesName = book.seriesName?.trim().orEmpty()
        if (rawSeriesName.isBlank()) {
            entries += AuthorDisplayEntry.BookItem(book)
            return@forEach
        }
        val normalizedSeries = normalizeSeriesGroupKey(rawSeriesName)
        if (normalizedSeries.isBlank()) {
            entries += AuthorDisplayEntry.BookItem(book)
            return@forEach
        }
        if (!emittedSeries.add(normalizedSeries)) return@forEach

        val groupedBooks = books.filter { candidate ->
            val seriesName = candidate.seriesName?.trim().orEmpty()
            seriesName.isNotBlank() && normalizeSeriesGroupKey(seriesName) == normalizedSeries
        }.sortedWith(
            compareBy<BookSummary> { it.seriesSequence ?: Double.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )

        if (groupedBooks.size <= 1) {
            entries += AuthorDisplayEntry.BookItem(book)
        } else {
            val title = groupedBooks
                .firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) }
                ?.let(::cleanedSeriesDisplayName)
                ?.takeIf(String::isNotBlank)
                ?: cleanedSeriesDisplayName(rawSeriesName)
            entries += AuthorDisplayEntry.SeriesItem(
                seriesName = title,
                books = groupedBooks
            )
        }
    }
    return entries
}

@HiltViewModel
class NarratorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val narratorName = savedStateHandle.get<String>(DetailRoute.NARRATOR_NAME_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(FacetBooksUiState(isLoading = true, title = narratorName))
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()

    init {
        observeBookProgressMutations()
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = 400,
                    page = 0,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    val books = result.value.filter {
                        it.narratorName?.equals(narratorName, ignoreCase = true) == true
                    }.sortedBy { it.title.lowercase() }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = books
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(books = state.books.map { it.withBookProgressMutation(mutation) })
                }
            }
        }
    }
}

@HiltViewModel
class GenresBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GenresUiState(isLoading = true))
    val uiState: StateFlow<GenresUiState> = mutableUiState.asStateFlow()

    init {
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = 400,
                    page = 0,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    val map = linkedMapOf<String, Int>()
                    result.value
                        .flatMap { it.genres }
                        .forEach { genre ->
                            map[genre] = (map[genre] ?: 0) + 1
                        }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            genres = map.entries
                                .sortedBy { entry -> entry.key.lowercase() }
                                .map { GenreSummary(name = it.key, count = it.value) }
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
class GenreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val genreName = savedStateHandle.get<String>(DetailRoute.GENRE_NAME_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(FacetBooksUiState(isLoading = true, title = genreName))
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()

    init {
        observeBookProgressMutations()
        refresh(forceRefresh = false)
    }

    fun refresh() {
        refresh(forceRefresh = true)
    }

    private fun refresh(forceRefresh: Boolean) {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (
                val result = sessionRepository.fetchBooksForActiveLibrary(
                    limit = 400,
                    page = 0,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    val books = result.value.filter { item ->
                        item.genres.any { it.equals(genreName, ignoreCase = true) }
                    }.sortedBy { it.title.lowercase() }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = books
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    state.copy(books = state.books.map { it.withBookProgressMutation(mutation) })
                }
            }
        }
    }
}

@Composable
fun AuthorDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: AuthorDetailViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val collapseSeries by viewModel.collapseSeries.collectAsStateWithLifecycle()
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(addToListBookId) {
        if (!addToListBookId.isNullOrBlank()) {
            collectionPickerViewModel.loadDestinations(forceRefresh = false)
        }
    }
    LaunchedEffect(collectionPickerUiState.actionMessage) {
        val message = collectionPickerUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        collectionPickerViewModel.clearMessages()
    }
    LaunchedEffect(collectionPickerUiState.errorMessage) {
        val message = collectionPickerUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        collectionPickerViewModel.clearMessages()
    }

    val displayBooks = remember(uiState.books) { uiState.books.sortedBy { it.title.lowercase() } }
    val displayEntries = remember(displayBooks, collapseSeries) {
        buildAuthorDisplayEntries(
            books = displayBooks,
            collapseSeries = collapseSeries
        )
    }
    val aboutText = uiState.authorAbout?.takeIf { it.isNotBlank() }
        ?: "No author biography is available from this Audiobookshelf server."

    if (layoutMode == AuthorLayoutMode.Grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                AuthorDetailHeader(
                    title = uiState.title,
                    bookCount = displayBooks.size,
                    authorImageUrl = uiState.authorImageUrl,
                    aboutText = aboutText,
                    layoutMode = layoutMode,
                    collapseSeries = collapseSeries,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick,
                    onSetLayoutMode = viewModel::setLayoutMode,
                    onToggleCollapseSeries = viewModel::toggleCollapseSeries
                )
            }

            when {
                uiState.isLoading -> item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> item(span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                displayBooks.isEmpty() -> item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "No books found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    gridItems(displayEntries, key = { it.stableKey }) { entry ->
                        when (entry) {
                            is AuthorDisplayEntry.BookItem -> {
                                AuthorGridBookItem(
                                    book = entry.book,
                                    isDownloaded = uiState.downloadedBookIds.contains(entry.book.id),
                                    downloadProgressPercent = uiState.downloadProgressByBookId[entry.book.id],
                                    onClick = { onBookClick(entry.book.id) },
                                    onAddToCollection = { addToListBookId = entry.book.id },
                                    onMarkAsFinished = {
                                        if (entry.book.hasFinishedStatusProgress()) {
                                            viewModel.markAsUnfinished(entry.book.id)
                                        } else {
                                            viewModel.markAsFinished(entry.book.id)
                                        }
                                    },
                                    onResetBookProgress = { viewModel.resetBookProgress(entry.book.id) },
                                    onToggleDownload = { viewModel.toggleDownload(entry.book.id) }
                                )
                            }

                            is AuthorDisplayEntry.SeriesItem -> {
                                AuthorSeriesGridItem(
                                    entry = entry,
                                    isDownloaded = uiState.downloadedBookIds.contains(entry.leadBook.id),
                                    downloadProgressPercent = uiState.downloadProgressByBookId[entry.leadBook.id],
                                    onClick = { onSeriesClick(entry.seriesName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                AuthorDetailHeader(
                    title = uiState.title,
                    bookCount = displayBooks.size,
                    authorImageUrl = uiState.authorImageUrl,
                    aboutText = aboutText,
                    layoutMode = layoutMode,
                    collapseSeries = collapseSeries,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick,
                    onSetLayoutMode = viewModel::setLayoutMode,
                    onToggleCollapseSeries = viewModel::toggleCollapseSeries
                )
            }

            when {
                uiState.isLoading -> item {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                displayBooks.isEmpty() -> item {
                    Text(
                        text = "No books found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    items(displayEntries, key = { it.stableKey }) { entry ->
                        when (entry) {
                            is AuthorDisplayEntry.BookItem -> {
                                AuthorBookRow(
                                    book = entry.book,
                                    isDownloaded = uiState.downloadedBookIds.contains(entry.book.id),
                                    downloadProgressPercent = uiState.downloadProgressByBookId[entry.book.id],
                                    onClick = { onBookClick(entry.book.id) },
                                    onAddToCollection = { addToListBookId = entry.book.id },
                                    onMarkAsFinished = {
                                        if (entry.book.hasFinishedStatusProgress()) {
                                            viewModel.markAsUnfinished(entry.book.id)
                                        } else {
                                            viewModel.markAsFinished(entry.book.id)
                                        }
                                    },
                                    onResetBookProgress = { viewModel.resetBookProgress(entry.book.id) },
                                    onToggleDownload = { viewModel.toggleDownload(entry.book.id) }
                                )
                            }

                            is AuthorDisplayEntry.SeriesItem -> {
                                AuthorSeriesListRow(
                                    entry = entry,
                                    isDownloaded = uiState.downloadedBookIds.contains(entry.leadBook.id),
                                    downloadProgressPercent = uiState.downloadProgressByBookId[entry.leadBook.id],
                                    onClick = { onSeriesClick(entry.seriesName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val targetBookId = addToListBookId
    if (!targetBookId.isNullOrBlank()) {
        AddToListDialog(
            uiState = collectionPickerUiState,
            onDismiss = {
                addToListBookId = null
                collectionPickerViewModel.clearMessages()
            },
            onAddToExistingCollection = { collectionId ->
                collectionPickerViewModel.addBookToExistingCollection(
                    bookId = targetBookId,
                    collectionId = collectionId
                )
            },
            onCreateCollection = { name ->
                collectionPickerViewModel.createCollectionAndAddBook(
                    bookId = targetBookId,
                    name = name
                )
            },
            onAddToExistingPlaylist = { playlistId ->
                collectionPickerViewModel.addBookToExistingPlaylist(
                    bookId = targetBookId,
                    playlistId = playlistId
                )
            },
            onCreatePlaylist = { name ->
                collectionPickerViewModel.createPlaylistAndAddBook(
                    bookId = targetBookId,
                    name = name
                )
            }
        )
    }
}

@Composable
private fun AuthorDetailHeader(
    title: String,
    bookCount: Int,
    authorImageUrl: String?,
    aboutText: String,
    layoutMode: AuthorLayoutMode,
    collapseSeries: Boolean,
    onBackClick: () -> Unit,
    onHomeClick: (() -> Unit)?,
    onSetLayoutMode: (AuthorLayoutMode) -> Unit,
    onToggleCollapseSeries: () -> Unit
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable(title, aboutText) { mutableStateOf(false) }
    var aboutCanExpand by remember(title, aboutText) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back"
                )
            }
            if (onHomeClick != null) {
                Spacer(modifier = Modifier.width(FacetBackTitleSpacing))
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = "Home"
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { optionsExpanded = true }) {
                    Icon(imageVector = Icons.Outlined.MoreHoriz, contentDescription = "More")
                }
                AppDropdownMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { optionsExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Grid") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (layoutMode == AuthorLayoutMode.Grid) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            onSetLayoutMode(AuthorLayoutMode.Grid)
                            optionsExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("List") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (layoutMode == AuthorLayoutMode.List) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            onSetLayoutMode(AuthorLayoutMode.List)
                            optionsExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Collapse Series") },
                        trailingIcon = {
                            if (collapseSeries) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            onToggleCollapseSeries()
                            optionsExpanded = false
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(92.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            if (authorImageUrl.isNullOrBlank()) {
                Text(
                    text = title.split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .take(2)
                        .joinToString(""),
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                AsyncImage(
                    model = rememberCoverImageModel(authorImageUrl),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "$bookCount books",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = aboutText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (aboutExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                if (!aboutExpanded) {
                    aboutCanExpand = textLayoutResult.hasVisualOverflow
                }
            }
        )
        if (aboutCanExpand || aboutExpanded) {
            Text(
                text = if (aboutExpanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { aboutExpanded = !aboutExpanded }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AuthorGridBookItem(
    book: BookSummary,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onClick: () -> Unit,
    onAddToCollection: () -> Unit,
    onMarkAsFinished: () -> Unit,
    onResetBookProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.remainingStatusTimeLabel().ifBlank { formatDuration(book.durationSeconds) }
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .width(StandardGridCoverWidth)
                    .height(StandardGridCoverHeight),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = WideCoverBackgroundBlur
            )
            DownloadBadge(
                isDownloaded = isDownloaded,
                downloadProgressPercent = downloadProgressPercent,
                badgeSize = 24.dp,
                iconSize = 13.dp,
                progressRingSize = 20.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressMetaLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Book actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text(downloadLabel) },
                        onClick = {
                            menuExpanded = false
                            onToggleDownload()
                        }
                    )
                    AppDropdownMenuItem(
                        text = {
                            Text(
                                if (book.hasFinishedStatusProgress()) {
                                    "Mark as Unfinished"
                                } else {
                                    "Mark as Finished"
                                }
                            )
                        },
                        enabled = book.hasStartedStatusProgress(),
                        onClick = {
                            menuExpanded = false
                            onMarkAsFinished()
                        }
                    )
                    ResetBookProgressMenuItem(
                        onConfirm = {
                            menuExpanded = false
                            onResetBookProgress()
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Add to Collection") },
                        onClick = {
                            menuExpanded = false
                            onAddToCollection()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorSeriesGridItem(
    entry: AuthorDisplayEntry.SeriesItem,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onClick: () -> Unit
) {
    val lead = entry.leadBook
    val layerCount = entry.count.coerceIn(2, 3)
    val frameHeight = StandardGridCoverHeight
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(frameHeight)
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter
        ) {
            FacetSeriesStackCoverLayers(
                coverUrl = lead.coverUrl,
                contentDescription = entry.seriesName,
                layerCount = layerCount,
                frameWidth = StandardGridCoverWidth,
                frameHeight = StandardGridCoverHeight,
                modifier = Modifier
                    .width(StandardGridCoverWidth)
                    .height(StandardGridCoverHeight)
            )
            DownloadBadge(
                isDownloaded = isDownloaded,
                downloadProgressPercent = downloadProgressPercent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
            )
        }
        Row(
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entry.count == 1) "1 book" else "${entry.count} books",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AuthorBookRow(
    book: BookSummary,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onClick: () -> Unit,
    onAddToCollection: () -> Unit,
    onMarkAsFinished: () -> Unit,
    onResetBookProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.remainingStatusTimeLabel().ifBlank { formatDuration(book.durationSeconds) }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(88.dp)) {
                FramedCoverImage(
                    coverUrl = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
                )
                DownloadBadge(
                    isDownloaded = isDownloaded,
                    downloadProgressPercent = downloadProgressPercent,
                    badgeSize = 22.dp,
                    iconSize = 12.dp,
                    progressRingSize = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progressMetaLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Book actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text(downloadLabel) },
                        onClick = {
                            menuExpanded = false
                            onToggleDownload()
                        }
                    )
                    AppDropdownMenuItem(
                        text = {
                            Text(
                                if (book.hasFinishedStatusProgress()) {
                                    "Mark as Unfinished"
                                } else {
                                    "Mark as Finished"
                                }
                            )
                        },
                        enabled = book.hasStartedStatusProgress(),
                        onClick = {
                            menuExpanded = false
                            onMarkAsFinished()
                        }
                    )
                    ResetBookProgressMenuItem(
                        onConfirm = {
                            menuExpanded = false
                            onResetBookProgress()
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Add to Collection") },
                        onClick = {
                            menuExpanded = false
                            onAddToCollection()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorSeriesListRow(
    entry: AuthorDisplayEntry.SeriesItem,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onClick: () -> Unit
) {
    val lead = entry.leadBook
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                val layerCount = entry.count.coerceIn(2, 3)
                val frameSize = 88.dp
                FacetSeriesStackCoverLayers(
                    coverUrl = lead.coverUrl,
                    contentDescription = entry.seriesName,
                    layerCount = layerCount,
                    frameWidth = frameSize,
                    frameHeight = frameSize,
                    modifier = Modifier.matchParentSize()
                )
                DownloadBadge(
                    isDownloaded = isDownloaded,
                    downloadProgressPercent = downloadProgressPercent,
                    badgeSize = 22.dp,
                    iconSize = 12.dp,
                    progressRingSize = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.count == 1) "1 book" else "${entry.count} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun SeriesDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String, String?) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val listMode by viewModel.listMode.collectAsStateWithLifecycle()
    val collapseSubseries by viewModel.collapseSubseries.collectAsStateWithLifecycle()
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var subseriesViewMenuExpanded by remember { mutableStateOf(false) }
    var statusFilterRaw by rememberSaveable { mutableStateOf(BooksStatusFilter.All.name) }
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val statusFilter = enumValueOrNull<BooksStatusFilter>(statusFilterRaw) ?: BooksStatusFilter.All
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh
    )
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(addToListBookId) {
        if (!addToListBookId.isNullOrBlank()) {
            collectionPickerViewModel.loadDestinations(forceRefresh = true)
        }
    }
    LaunchedEffect(collectionPickerUiState.actionMessage) {
        val message = collectionPickerUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        collectionPickerViewModel.clearMessages()
    }
    LaunchedEffect(collectionPickerUiState.errorMessage) {
        val message = collectionPickerUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        collectionPickerViewModel.clearMessages()
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
            .pullRefresh(refreshState)
        ) {
        when {
            uiState.errorMessage != null && !uiState.isRefreshing -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }

            uiState.entries.isNotEmpty() -> {
                val displayEntries = uiState.entries.applySeriesEntryStatusFilter(statusFilter)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                if (onHomeClick != null) {
                                    Spacer(modifier = Modifier.width(FacetBackTitleSpacing))
                                    IconButton(
                                        onClick = onHomeClick,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Home,
                                            contentDescription = "Home"
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Box {
                                    IconButton(
                                        onClick = { filterMenuExpanded = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FilterList,
                                            contentDescription = "Filter"
                                        )
                                    }
                                    AppDropdownMenu(
                                        expanded = filterMenuExpanded,
                                        onDismissRequest = { filterMenuExpanded = false }
                                    ) {
                                        BooksStatusFilter.entries.forEach { candidate ->
                                            AppDropdownMenuItem(
                                                text = { Text(candidate.label) },
                                                trailingIcon = {
                                                    if (candidate == statusFilter) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = null
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    statusFilterRaw = candidate.name
                                                    filterMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.setListMode(!listMode) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (listMode) {
                                            Icons.AutoMirrored.Outlined.ViewList
                                        } else {
                                            Icons.Outlined.GridView
                                        },
                                        contentDescription = if (listMode) "List" else "Grid"
                                    )
                                }
                            }
                            Text(
                                text = "Series",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    if (uiState.canCollapseSubseries) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box {
                                    OutlinedButton(
                                        onClick = { subseriesViewMenuExpanded = true }
                                    ) {
                                        Text("Sub-series View")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null
                                        )
                                    }
                                    AppDropdownMenu(
                                        expanded = subseriesViewMenuExpanded,
                                        onDismissRequest = { subseriesViewMenuExpanded = false }
                                    ) {
                                        AppDropdownMenuItem(
                                            text = { Text("Flat") },
                                            trailingIcon = {
                                                if (!collapseSubseries) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.setCollapseSubseries(false)
                                                subseriesViewMenuExpanded = false
                                            }
                                        )
                                        AppDropdownMenuItem(
                                            text = { Text("Nested") },
                                            trailingIcon = {
                                                if (collapseSubseries) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.setCollapseSubseries(true)
                                                subseriesViewMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (displayEntries.isEmpty()) {
                        item {
                            Text(
                                text = "No series items in this filter.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        return@LazyColumn
                    }

                    item {
                        SeriesCoverStack(
                            entries = displayEntries,
                            downloadedBookIds = uiState.downloadedBookIds,
                            downloadProgressByBookId = uiState.downloadProgressByBookId
                        )
                    }
                    item {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            text = if (displayEntries.totalSeriesBookCount() == 1) {
                                "1 book"
                            } else {
                                "${displayEntries.totalSeriesBookCount()} books"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (listMode) {
                        itemsIndexed(displayEntries, key = { _, item -> item.stableId }) { index, entry ->
                            when (entry) {
                                is SeriesDetailEntry.BookItem -> {
                                    val book = entry.book
                                    val orderLabel = formatSeriesOrderLabel(book.seriesSequence) ?: (index + 1).toString()
                                    SeriesDetailBookRow(
                                        book = book,
                                        orderLabel = orderLabel,
                                        onClick = { onBookClick(book.id) },
                                        isDownloaded = uiState.downloadedBookIds.contains(book.id),
                                        downloadProgressPercent = uiState.downloadProgressByBookId[book.id],
                                        onAddToCollection = { addToListBookId = book.id },
                                        onMarkAsFinished = {
                                            if (book.hasFinishedStatusProgress()) {
                                                viewModel.markAsUnfinished(book.id)
                                            } else {
                                                viewModel.markAsFinished(book.id)
                                            }
                                        },
                                        onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                        onToggleDownload = { viewModel.toggleDownload(book.id) }
                                    )
                                }

                                is SeriesDetailEntry.SubseriesItem -> {
                                    SeriesDetailSubseriesRow(
                                        entry = entry,
                                        onClick = { onSeriesClick(entry.name, entry.id) }
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    } else {
                        val entryRows = displayEntries.chunked(2)
                        itemsIndexed(entryRows, key = { index, _ -> "series-grid-row-$index" }) { rowIndex, rowEntries ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowEntries.forEachIndexed { columnIndex, entry ->
                                    when (entry) {
                                        is SeriesDetailEntry.BookItem -> {
                                            val book = entry.book
                                            val fallbackIndex = (rowIndex * 2) + columnIndex + 1
                                            val orderLabel = formatSeriesOrderLabel(book.seriesSequence) ?: fallbackIndex.toString()
                                            SeriesDetailGridCard(
                                                book = book,
                                                orderLabel = orderLabel,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onBookClick(book.id) },
                                                isDownloaded = uiState.downloadedBookIds.contains(book.id),
                                                downloadProgressPercent = uiState.downloadProgressByBookId[book.id],
                                                onAddToCollection = { addToListBookId = book.id },
                                                onMarkAsFinished = {
                                                    if (book.hasFinishedStatusProgress()) {
                                                        viewModel.markAsUnfinished(book.id)
                                                    } else {
                                                        viewModel.markAsFinished(book.id)
                                                    }
                                                },
                                                onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                                onToggleDownload = { viewModel.toggleDownload(book.id) }
                                            )
                                        }

                                        is SeriesDetailEntry.SubseriesItem -> {
                                            SeriesDetailSubseriesGridCard(
                                                entry = entry,
                                                modifier = Modifier.weight(1f),
                                                onClick = { onSeriesClick(entry.name, entry.id) }
                                            )
                                        }
                                    }
                                }
                                if (rowEntries.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            uiState.isLoading -> {
                Text(
                    text = "Loading series...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Text(
                    text = "No series items found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = refreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    val targetBookId = addToListBookId
    if (!targetBookId.isNullOrBlank()) {
        AddToListDialog(
            uiState = collectionPickerUiState,
            onDismiss = {
                addToListBookId = null
                collectionPickerViewModel.clearMessages()
            },
            onAddToExistingCollection = { collectionId ->
                collectionPickerViewModel.addBookToExistingCollection(
                    bookId = targetBookId,
                    collectionId = collectionId
                )
            },
            onCreateCollection = { name ->
                collectionPickerViewModel.createCollectionAndAddBook(
                    bookId = targetBookId,
                    name = name
                )
            },
            onAddToExistingPlaylist = { playlistId ->
                collectionPickerViewModel.addBookToExistingPlaylist(
                    bookId = targetBookId,
                    playlistId = playlistId
                )
            },
            onCreatePlaylist = { name ->
                collectionPickerViewModel.createPlaylistAndAddBook(
                    bookId = targetBookId,
                    name = name
                )
            }
        )
    }
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val collectionId = savedStateHandle.get<String>(DetailRoute.COLLECTION_ID_ARG).orEmpty()
    private val collectionName = savedStateHandle.get<String>(DetailRoute.COLLECTION_NAME_ARG).orEmpty()
    private val initialBooks = FacetBooksMemoryCache.collectionBooks(collectionId)
    private val mutableUiState = MutableStateFlow(
        FacetBooksUiState(
            isLoading = initialBooks.isEmpty(),
            title = collectionName.ifBlank { "Collection" },
            books = initialBooks
        )
    )
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()
    private val mutableListMode = MutableStateFlow(true)
    val listMode: StateFlow<Boolean> = mutableListMode.asStateFlow()

    init {
        viewModelScope.launch {
            mutableListMode.value = sessionPreferences.state.first().collectionDetailListMode
        }
        observeBookProgressMutations()
        refresh(forceRefresh = false, showLoader = initialBooks.isEmpty())
    }

    fun setListMode(value: Boolean) {
        mutableListMode.value = value
        viewModelScope.launch {
            sessionPreferences.setCollectionDetailListMode(value)
        }
    }

    fun refresh() {
        refresh(forceRefresh = true, showLoader = true)
    }

    fun removeBook(bookId: String) {
        if (collectionId.isBlank() || bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.removeBookFromCollection(
                    collectionId = collectionId,
                    bookId = bookId
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update {
                        val updatedBooks = it.books.filterNot { book -> book.id == bookId }
                        FacetBooksMemoryCache.updateCollectionBooks(collectionId, updatedBooks)
                        it.copy(
                            books = updatedBooks,
                            actionMessage = "Removed from collection."
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun resetBookProgress(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    refresh(forceRefresh = true, showLoader = false)
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    val updatedBooks = state.books.map { it.withBookProgressMutation(mutation) }
                    FacetBooksMemoryCache.updateCollectionBooks(collectionId, updatedBooks)
                    state.copy(books = updatedBooks)
                }
            }
        }
    }

    private fun refresh(forceRefresh: Boolean, showLoader: Boolean) {
        if (collectionId.isBlank()) {
            mutableUiState.update {
                it.copy(isLoading = false, errorMessage = "Invalid collection.")
            }
            return
        }
        mutableUiState.update {
            it.copy(
                isLoading = showLoader,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.fetchCollectionBooks(
                    collectionId = collectionId,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    FacetBooksMemoryCache.updateCollectionBooks(collectionId, result.value)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = result.value,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        val shouldShowError = showLoader || it.books.isEmpty()
                        it.copy(
                            isLoading = false,
                            errorMessage = if (shouldShowError) result.message else null
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CollectionDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listMode by viewModel.listMode.collectAsStateWithLifecycle()
    var manualRefreshInProgress by rememberSaveable { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && manualRefreshInProgress,
        onRefresh = {
            manualRefreshInProgress = true
            viewModel.refresh()
        }
    )
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) manualRefreshInProgress = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = uiState.title.ifBlank { "Collection" },
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            trailingIcon = if (listMode) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
            trailingIconDescription = if (listMode) "Grid mode" else "List mode",
            onTrailingIconClick = { viewModel.setListMode(!listMode) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.isLoading -> {
                    Text(
                        text = "Loading collection...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                uiState.books.isEmpty() -> {
                    Text(
                        text = "No books found in this collection.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    if (listMode) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(uiState.books, key = { it.id }) { book ->
                                CollectionBookRow(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                    onRemoveFromCollection = { viewModel.removeBook(book.id) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            gridItems(uiState.books, key = { it.id }) { book ->
                                FacetBookGridCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                    removeActionLabel = "Remove from collection",
                                    menuContentDescription = "Collection item actions",
                                    onRemove = { viewModel.removeBook(book.id) }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading && manualRefreshInProgress,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>(DetailRoute.PLAYLIST_ID_ARG).orEmpty()
    private val playlistName = savedStateHandle.get<String>(DetailRoute.PLAYLIST_NAME_ARG).orEmpty()
    private val initialBooks = FacetBooksMemoryCache.playlistBooks(playlistId)
    private val mutableUiState = MutableStateFlow(
        FacetBooksUiState(
            isLoading = initialBooks.isEmpty(),
            title = playlistName.ifBlank { "Playlist" },
            books = initialBooks
        )
    )
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()
    private val mutableListMode = MutableStateFlow(true)
    val listMode: StateFlow<Boolean> = mutableListMode.asStateFlow()

    init {
        viewModelScope.launch {
            mutableListMode.value = sessionPreferences.state.first().playlistDetailListMode
        }
        observeBookProgressMutations()
        refresh(forceRefresh = false, showLoader = initialBooks.isEmpty())
    }

    fun setListMode(value: Boolean) {
        mutableListMode.value = value
        viewModelScope.launch {
            sessionPreferences.setPlaylistDetailListMode(value)
        }
    }

    fun refresh() {
        refresh(forceRefresh = true, showLoader = true)
    }

    fun removeBook(bookId: String) {
        if (playlistId.isBlank() || bookId.isBlank()) return
        viewModelScope.launch {
            val wasLastVisibleBook = uiState.value.books.size <= 1
            when (
                val result = sessionRepository.removeBookFromPlaylist(
                    playlistId = playlistId,
                    bookId = bookId
                )
            ) {
                is AppResult.Success -> {
                    if (wasLastVisibleBook) {
                        FacetBooksMemoryCache.updatePlaylistBooks(playlistId, emptyList())
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                books = emptyList(),
                                errorMessage = null,
                                actionMessage = "Removed from playlist."
                            )
                        }
                    } else {
                        val updatedBooks = uiState.value.books.filterNot { book -> book.id == bookId }
                        FacetBooksMemoryCache.updatePlaylistBooks(playlistId, updatedBooks)
                        mutableUiState.update {
                            it.copy(
                                books = updatedBooks,
                                errorMessage = null,
                                actionMessage = "Removed from playlist."
                            )
                        }
                        refresh(forceRefresh = true, showLoader = false)
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update { it.copy(actionMessage = result.message) }
                }
            }
        }
    }

    fun resetBookProgress(bookId: String) {
        if (bookId.isBlank()) return
        viewModelScope.launch {
            when (
                val result = sessionRepository.markBookFinished(
                    bookId = bookId,
                    finished = false,
                    resetProgressWhenUnfinished = true
                )
            ) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(actionMessage = "Book progress reset.") }
                    refresh(forceRefresh = true, showLoader = false)
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

    private fun observeBookProgressMutations() {
        viewModelScope.launch {
            sessionRepository.observeBookProgressMutations().collect { mutation ->
                mutableUiState.update { state ->
                    val updatedBooks = state.books.map { it.withBookProgressMutation(mutation) }
                    FacetBooksMemoryCache.updatePlaylistBooks(playlistId, updatedBooks)
                    state.copy(books = updatedBooks)
                }
            }
        }
    }

    private fun refresh(forceRefresh: Boolean, showLoader: Boolean) {
        if (playlistId.isBlank()) {
            mutableUiState.update {
                it.copy(isLoading = false, errorMessage = "Invalid playlist.")
            }
            return
        }
        mutableUiState.update {
            it.copy(
                isLoading = showLoader,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            when (
                val result = sessionRepository.fetchPlaylistBooks(
                    playlistId = playlistId,
                    forceRefresh = forceRefresh
                )
            ) {
                is AppResult.Success -> {
                    FacetBooksMemoryCache.updatePlaylistBooks(playlistId, result.value)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = result.value,
                            errorMessage = null
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        val treatAsEmpty = result.message.contains("404") && it.books.isEmpty()
                        val shouldShowError = (showLoader || it.books.isEmpty()) && !treatAsEmpty
                        it.copy(
                            isLoading = false,
                            errorMessage = if (shouldShowError) result.message else null
                        )
                    }
                }
            }
        }
    }
}

private object FacetBooksMemoryCache {
    private val collectionBooksById = mutableMapOf<String, List<BookSummary>>()
    private val playlistBooksById = mutableMapOf<String, List<BookSummary>>()

    @Synchronized
    fun collectionBooks(collectionId: String): List<BookSummary> {
        return collectionBooksById[collectionId].orEmpty()
    }

    @Synchronized
    fun playlistBooks(playlistId: String): List<BookSummary> {
        return playlistBooksById[playlistId].orEmpty()
    }

    @Synchronized
    fun updateCollectionBooks(collectionId: String, books: List<BookSummary>) {
        collectionBooksById[collectionId] = books
    }

    @Synchronized
    fun updatePlaylistBooks(playlistId: String, books: List<BookSummary>) {
        playlistBooksById[playlistId] = books
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PlaylistDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listMode by viewModel.listMode.collectAsStateWithLifecycle()
    var manualRefreshInProgress by rememberSaveable { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && manualRefreshInProgress,
        onRefresh = {
            manualRefreshInProgress = true
            viewModel.refresh()
        }
    )
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) manualRefreshInProgress = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = uiState.title.ifBlank { "Playlist" },
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            trailingIcon = if (listMode) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
            trailingIconDescription = if (listMode) "Grid mode" else "List mode",
            onTrailingIconClick = { viewModel.setListMode(!listMode) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.isLoading -> {
                    Text(
                        text = "Loading playlist...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                uiState.books.isEmpty() -> {
                    Text(
                        text = "No books found in this playlist.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    if (listMode) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(uiState.books, key = { it.id }) { book ->
                                PlaylistBookRow(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                    onRemoveFromPlaylist = { viewModel.removeBook(book.id) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            gridItems(uiState.books, key = { it.id }) { book ->
                                FacetBookGridCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onResetBookProgress = { viewModel.resetBookProgress(book.id) },
                                    removeActionLabel = "Remove from playlist",
                                    menuContentDescription = "Playlist item actions",
                                    onRemove = { viewModel.removeBook(book.id) }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading && manualRefreshInProgress,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun CollectionBookRow(
    book: BookSummary,
    onClick: () -> Unit,
    onResetBookProgress: () -> Unit,
    onRemoveFromCollection: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val remainingTimeLabel = book.remainingStatusTimeLabel()
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.size(width = 56.dp, height = 78.dp),
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (remainingTimeLabel.isNotBlank()) {
                    Text(
                        text = remainingTimeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Collection item actions"
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Go to book") },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    ResetBookProgressMenuItem(
                        onConfirm = {
                            menuExpanded = false
                            onResetBookProgress()
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Remove from collection") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromCollection()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistBookRow(
    book: BookSummary,
    onClick: () -> Unit,
    onResetBookProgress: () -> Unit,
    onRemoveFromPlaylist: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val remainingTimeLabel = book.remainingStatusTimeLabel()
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.size(width = 56.dp, height = 78.dp),
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (remainingTimeLabel.isNotBlank()) {
                    Text(
                        text = remainingTimeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Playlist item actions"
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Go to book") },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    ResetBookProgressMenuItem(
                        onConfirm = {
                            menuExpanded = false
                            onResetBookProgress()
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Remove from playlist") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromPlaylist()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FacetBookGridCard(
    book: BookSummary,
    onClick: () -> Unit,
    onResetBookProgress: () -> Unit,
    removeActionLabel: String,
    menuContentDescription: String,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StandardGridCoverHeight),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = menuContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        AppDropdownMenuItem(
                            text = { Text("Go to book") },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        ResetBookProgressMenuItem(
                            onConfirm = {
                                menuExpanded = false
                                onResetBookProgress()
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text(removeActionLabel) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GenresBrowseScreen(
    onBackClick: () -> Unit,
    onGenreClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: GenresBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = viewModel::refresh
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = "Genres",
            onBackClick = onBackClick,
            onHomeClick = onHomeClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.isLoading -> {
                    Text(
                        text = "Loading genres...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                }

                uiState.genres.isEmpty() -> {
                    Text(
                        text = "No genres available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                        items(uiState.genres, key = { it.name }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                    .clickable { onGenreClick(item.name) }
                                    .padding(horizontal = 10.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${item.count}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun GenreDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FacetBooksScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onHomeClick = onHomeClick,
        onBookClick = onBookClick,
        onRetry = viewModel::refresh,
        showMoreIcon = true
    )
}

@Composable
fun NarratorDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: NarratorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FacetBooksScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onHomeClick = onHomeClick,
        onBookClick = onBookClick,
        onRetry = viewModel::refresh
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FacetBooksScreen(
    uiState: FacetBooksUiState,
    onBackClick: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    onBookClick: (String) -> Unit,
    onRetry: () -> Unit,
    showMoreIcon: Boolean = false
) {
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = onRetry
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = uiState.title,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            showMoreIcon = showMoreIcon
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.isLoading -> {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }

                uiState.books.isEmpty() -> {
                    Text(
                        text = "No books found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.books, key = { it.id }) { book ->
                            FacetBookRow(
                                book = book,
                                onClick = { onBookClick(book.id) }
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun FacetTopBar(
    title: String,
    onBackClick: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    showMoreIcon: Boolean = false,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIconDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }
        Spacer(modifier = Modifier.width(FacetBackTitleSpacing))
        if (onHomeClick != null) {
            IconButton(
                onClick = onHomeClick,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            }
            Spacer(modifier = Modifier.width(FacetBackTitleSpacing))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null && onTrailingIconClick != null) {
            IconButton(
                onClick = onTrailingIconClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = trailingIconDescription
                )
            }
        } else if (showMoreIcon) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(imageVector = Icons.Outlined.MoreHoriz, contentDescription = "More")
            }
        }
    }
}

@Composable
private fun ResetBookProgressMenuItem(
    onPrepareConfirm: () -> Unit = {},
    onConfirm: () -> Unit
) {
    var showConfirmation by remember { mutableStateOf(false) }
    AppDropdownMenuItem(
        text = { Text("Reset Book Progress") },
        onClick = {
            onPrepareConfirm()
            showConfirmation = true
        }
    )
    if (showConfirmation) {
        ResetBookProgressConfirmationDialog(
            onDismissRequest = { showConfirmation = false },
            onConfirm = {
                showConfirmation = false
                onConfirm()
            }
        )
    }
}

@Composable
private fun ResetBookProgressConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Reset Book Progress?") },
        text = { Text("This will set the book back to 0% and stop playback.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? {
    return runCatching { enumValueOf<T>(raw) }.getOrNull()
}

@Composable
private fun DownloadBadge(
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    modifier: Modifier = Modifier,
    badgeSize: androidx.compose.ui.unit.Dp = 30.dp,
    progressRingSize: androidx.compose.ui.unit.Dp = 24.dp,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp
) {
    val progress = downloadProgressPercent?.coerceIn(0, 100)
    val showProgress = progress != null && progress in 0..99
    val showCompleted = isDownloaded && !showProgress
    if (!showProgress && !showCompleted) return

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(progressRingSize),
                strokeWidth = 2.4.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Downloaded",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShadedStackCover(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier,
    shape: RoundedCornerShape,
    shadeAlpha: Float,
    contentScale: ContentScale = ContentScale.Fit
) {
    Box(modifier = modifier) {
        val shadedModifier = if (shadeAlpha > 0f) {
            Modifier
                .matchParentSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black.copy(alpha = shadeAlpha),
                        blendMode = BlendMode.SrcAtop
                    )
                }
        } else {
            Modifier.matchParentSize()
        }

        FramedCoverImage(
            coverUrl = coverUrl,
            contentDescription = contentDescription,
            modifier = shadedModifier,
            shape = shape,
            contentScale = contentScale,
            backgroundBlur = FacetSeriesStackBackgroundBlur,
            frameOverlayAlphaMultiplier = 0f,
            disableBlurredFrame = true
        )
    }
}

@Composable
private fun List<SeriesDetailEntry>.totalSeriesBookCount(): Int {
    return sumOf { entry ->
        when (entry) {
            is SeriesDetailEntry.BookItem -> 1
            is SeriesDetailEntry.SubseriesItem -> entry.bookCount.coerceAtLeast(1)
        }
    }
}

private data class SeriesCoverStackItem(
    val title: String,
    val coverUrl: String?,
    val bookId: String? = null
)

private fun SeriesDetailEntry.toSeriesCoverStackItem(): SeriesCoverStackItem {
    return when (this) {
        is SeriesDetailEntry.BookItem -> SeriesCoverStackItem(
            title = book.title,
            coverUrl = book.coverUrl,
            bookId = book.id
        )

        is SeriesDetailEntry.SubseriesItem -> SeriesCoverStackItem(
            title = name,
            coverUrl = coverUrl
        )
    }
}

@Composable
private fun SeriesCoverStack(
    entries: List<SeriesDetailEntry>,
    downloadedBookIds: Set<String>,
    downloadProgressByBookId: Map<String, Int>
) {
    if (entries.isEmpty()) return
    val covers = entries.map { it.toSeriesCoverStackItem() }
    val frontItem = covers[0]
    val frontModel = rememberCoverImageModel(
        coverUrl = frontItem.coverUrl,
        preferOriginalSize = true
    )
    val frontPainter = rememberAsyncImagePainter(model = frontModel)
    val frontSuccessState = frontPainter.state as? AsyncImagePainter.State.Success
    val frontIntrinsicWidth = frontSuccessState?.result?.drawable?.intrinsicWidth?.takeIf { it > 0 }
    val frontIntrinsicHeight = frontSuccessState?.result?.drawable?.intrinsicHeight?.takeIf { it > 0 }
    val hasResolvedFrontCoverDimensions = frontIntrinsicWidth != null && frontIntrinsicHeight != null
    val frontAspectRatio = if (frontIntrinsicWidth != null && frontIntrinsicHeight != null) {
        frontIntrinsicWidth.toFloat() / frontIntrinsicHeight.toFloat()
    } else {
        0.66f
    }
    val useSquareMultiBookStack = hasResolvedFrontCoverDimensions &&
        frontAspectRatio in 0.97f..1.03f &&
        covers.size >= 2
    fun pickCoverWithImage(vararg indices: Int): SeriesCoverStackItem? {
        return indices
            .asSequence()
            .mapNotNull { idx -> covers.getOrNull(idx) }
            .firstOrNull { !it.coverUrl.isNullOrBlank() }
    }

    val middleRightBook = pickCoverWithImage(1, 0)
    val middleLeftBook = pickCoverWithImage(2, 1, 0)
    val backRightBook = pickCoverWithImage(3, 1, 0)
    val backLeftBook = pickCoverWithImage(4, 2, 1, 0)
    val showMiddleRight = useSquareMultiBookStack && middleRightBook != null
    val showMiddleLeft = useSquareMultiBookStack && middleLeftBook != null
    val showBackRight = useSquareMultiBookStack && backRightBook != null
    val showBackLeft = useSquareMultiBookStack && backLeftBook != null
    val middleLayerShadeAlpha = 0.24f
    val backLayerShadeAlpha = 0.38f
    val centeredBackLayerCount = if (useSquareMultiBookStack) 0 else (covers.size - 1).coerceAtMost(2)
    val frontWidth = 190.dp
    val frontHeight = 200.dp
    val middleWidth = 250.dp
    val middleHeight = 182.dp
    val backWidth = 300.dp
    val backHeight = 166.dp
    val middleXOffset = 35.dp
    val backXOffset = 68.dp
    val middleYOffset = 8.dp
    val backYOffset = 16.dp
    val stackWidth = 358.dp
    val stackHeight = 208.dp
    val layerShape = FacetSeriesStackCornerShape
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Keep the painter active in composition so square-cover detection resolves.
        Image(
            painter = frontPainter,
            contentDescription = null,
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
        )
        val scale = (maxWidth / stackWidth).coerceIn(0f, 1f)
        val scaledStackWidth = stackWidth * scale
        val scaledStackHeight = stackHeight * scale
        val scaledFrontWidth = frontWidth * scale
        val scaledFrontHeight = frontHeight * scale
        val scaledMiddleWidth = middleWidth * scale
        val scaledMiddleHeight = middleHeight * scale
        val scaledBackWidth = backWidth * scale
        val scaledBackHeight = backHeight * scale
        val scaledMiddleXOffset = middleXOffset * scale
        val scaledBackXOffset = backXOffset * scale
        val scaledMiddleYOffset = middleYOffset * scale
        val scaledBackYOffset = backYOffset * scale
        Box(
            modifier = Modifier
                .width(scaledStackWidth)
                .height(scaledStackHeight),
            contentAlignment = Alignment.TopCenter
        ) {
            if (showBackLeft) {
                ShadedStackCover(
                    coverUrl = backLeftBook?.coverUrl,
                    contentDescription = backLeftBook?.title,
                    modifier = Modifier
                        .offset(x = -scaledBackXOffset, y = scaledBackYOffset)
                        .width(scaledBackWidth)
                        .height(scaledBackHeight),
                    shape = layerShape,
                    shadeAlpha = backLayerShadeAlpha,
                    contentScale = ContentScale.Fit
                )
            }
            if (showBackRight) {
                ShadedStackCover(
                    coverUrl = backRightBook?.coverUrl,
                    contentDescription = backRightBook?.title,
                    modifier = Modifier
                        .offset(x = scaledBackXOffset, y = scaledBackYOffset)
                        .width(scaledBackWidth)
                        .height(scaledBackHeight),
                    shape = layerShape,
                    shadeAlpha = backLayerShadeAlpha,
                    contentScale = ContentScale.Fit
                )
            }
            if (showMiddleLeft) {
                ShadedStackCover(
                    coverUrl = middleLeftBook?.coverUrl,
                    contentDescription = middleLeftBook?.title,
                    modifier = Modifier
                        .offset(x = -scaledMiddleXOffset, y = scaledMiddleYOffset)
                        .width(scaledMiddleWidth)
                        .height(scaledMiddleHeight),
                    shape = layerShape,
                    shadeAlpha = middleLayerShadeAlpha,
                    contentScale = ContentScale.Fit
                )
            }
            if (showMiddleRight) {
                ShadedStackCover(
                    coverUrl = middleRightBook?.coverUrl,
                    contentDescription = middleRightBook?.title,
                    modifier = Modifier
                        .offset(x = scaledMiddleXOffset, y = scaledMiddleYOffset)
                        .width(scaledMiddleWidth)
                        .height(scaledMiddleHeight),
                    shape = layerShape,
                    shadeAlpha = middleLayerShadeAlpha,
                    contentScale = ContentScale.Fit
                )
            }
            if (centeredBackLayerCount >= 2) {
                FramedCoverImage(
                    coverUrl = frontItem.coverUrl,
                    contentDescription = frontItem.title,
                    modifier = Modifier
                        .offset(y = scaledBackYOffset)
                        .width(scaledBackWidth)
                        .height(scaledBackHeight)
                        .shadow(elevation = 3.6.dp * scale, shape = layerShape, clip = false),
                    shape = layerShape,
                    contentScale = ContentScale.Fit,
                    backgroundBlur = FacetSeriesStackBackgroundBlur,
                    frameOverlayAlphaMultiplier = 0.22f
                )
            }
            if (centeredBackLayerCount >= 1) {
                FramedCoverImage(
                    coverUrl = frontItem.coverUrl,
                    contentDescription = frontItem.title,
                    modifier = Modifier
                        .offset(y = scaledMiddleYOffset)
                        .width(scaledMiddleWidth)
                        .height(scaledMiddleHeight)
                        .shadow(elevation = 2.9.dp * scale, shape = layerShape, clip = false),
                    shape = layerShape,
                    contentScale = ContentScale.Fit,
                    backgroundBlur = FacetSeriesStackBackgroundBlur,
                    frameOverlayAlphaMultiplier = 0.48f
                )
            }
            FramedCoverImage(
                coverUrl = frontItem.coverUrl,
                contentDescription = frontItem.title,
                modifier = Modifier
                    .width(scaledFrontWidth)
                    .height(scaledFrontHeight)
                    .shadow(elevation = FacetSeriesStackFrontShadow * scale, shape = layerShape, clip = false),
                shape = layerShape,
                contentScale = if (useSquareMultiBookStack) ContentScale.Crop else ContentScale.Fit,
                backgroundBlur = FacetSeriesStackBackgroundBlur,
                frameOverlayAlphaMultiplier = 0.86f,
                disableBlurredFrame = useSquareMultiBookStack
            )
            val frontBookId = frontItem.bookId
            if (frontBookId != null) {
                DownloadBadge(
                    isDownloaded = downloadedBookIds.contains(frontBookId),
                    downloadProgressPercent = downloadProgressByBookId[frontBookId],
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp * scale, y = 8.dp * scale)
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailSubseriesRow(
    entry: SeriesDetailEntry.SubseriesItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                FacetSeriesStackCoverLayers(
                    coverUrl = entry.coverUrl,
                    contentDescription = entry.name,
                    layerCount = entry.bookCount.coerceIn(2, 3),
                    frameWidth = 88.dp,
                    frameHeight = 88.dp,
                    modifier = Modifier.matchParentSize()
                )
                if (!entry.sequenceLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "#${entry.sequenceLabel}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.bookCount == 1) "1 book" else "${entry.bookCount} books",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeriesDetailSubseriesGridCard(
    entry: SeriesDetailEntry.SubseriesItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardGridCoverHeight)
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter
        ) {
            FacetSeriesStackCoverLayers(
                coverUrl = entry.coverUrl,
                contentDescription = entry.name,
                layerCount = entry.bookCount.coerceIn(2, 3),
                frameWidth = StandardGridCoverWidth,
                frameHeight = StandardGridCoverHeight,
                modifier = Modifier
                    .width(StandardGridCoverWidth)
                    .height(StandardGridCoverHeight)
            )
            if (!entry.sequenceLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "#${entry.sequenceLabel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(StandardGridCoverWidth)
        )
        Text(
            text = if (entry.bookCount == 1) "1 book" else "${entry.bookCount} books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(StandardGridCoverWidth)
        )
    }
}

@Composable
private fun SeriesDetailBookRow(
    book: BookSummary,
    orderLabel: String,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkAsFinished: () -> Unit,
    onResetBookProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.remainingStatusTimeLabel().ifBlank { formatDuration(book.durationSeconds) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = 44.dp
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "#$orderLabel",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val progress = downloadProgressPercent?.coerceIn(0, 100)
            val showProgress = progress != null && progress in 0..99
            val showCompleted = isDownloaded && !showProgress
            if (showProgress || showCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showProgress) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.authorName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = progressMetaLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                AppDropdownMenuItem(
                    text = { Text("Add to collection") },
                    onClick = {
                        menuExpanded = false
                        onAddToCollection()
                    }
                )
                AppDropdownMenuItem(
                    text = {
                        Text(
                            if (book.hasFinishedStatusProgress()) {
                                "Mark as Unfinished"
                            } else {
                                "Mark as Finished"
                            }
                        )
                    },
                    enabled = book.hasStartedStatusProgress(),
                    onClick = {
                        menuExpanded = false
                        onMarkAsFinished()
                    }
                )
                ResetBookProgressMenuItem(
                    onConfirm = {
                        menuExpanded = false
                        onResetBookProgress()
                    }
                )
                AppDropdownMenuItem(
                    text = { Text(downloadLabel) },
                    onClick = {
                        menuExpanded = false
                        onToggleDownload()
                    }
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailGridCard(
    book: BookSummary,
    orderLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkAsFinished: () -> Unit,
    onResetBookProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.remainingStatusTimeLabel().ifBlank { formatDuration(book.durationSeconds) }
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .height(StandardGridCoverHeight)
                .align(Alignment.CenterHorizontally)
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "#$orderLabel",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            val progress = downloadProgressPercent?.coerceIn(0, 100)
            val showProgress = progress != null && progress in 0..99
            val showCompleted = isDownloaded && !showProgress
            if (showProgress || showCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showProgress) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.4.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressMetaLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Add to collection") },
                        onClick = {
                            menuExpanded = false
                            onAddToCollection()
                        }
                    )
                    AppDropdownMenuItem(
                        text = {
                            Text(
                                if (book.hasFinishedStatusProgress()) {
                                    "Mark as Unfinished"
                                } else {
                                    "Mark as Finished"
                                }
                            )
                        },
                        enabled = book.hasStartedStatusProgress(),
                        onClick = {
                            menuExpanded = false
                            onMarkAsFinished()
                        }
                    )
                    ResetBookProgressMenuItem(
                        onConfirm = {
                            menuExpanded = false
                            onResetBookProgress()
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text(downloadLabel) },
                        onClick = {
                            menuExpanded = false
                            onToggleDownload()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FacetBookRow(
    book: BookSummary,
    onClick: () -> Unit
) {
    val progressMetaLabel = book.remainingStatusTimeLabel().ifBlank { formatDuration(book.durationSeconds) }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.size(width = 56.dp, height = 78.dp),
                shape = RoundedCornerShape(6.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progressMetaLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(durationSeconds: Double?): String {
    return formatDurationHoursMinutes(durationSeconds)
}

private fun BookSummary.remainingStatusTimeLabel(): String {
    return remainingTimeLabel()
}

private fun formatSeriesOrderLabel(seriesSequence: Double?): String? {
    val sequence = seriesSequence?.takeIf { it > 0.0 } ?: return null
    return if (sequence % 1.0 == 0.0) {
        sequence.toInt().toString()
    } else {
        sequence.toString()
    }
}
