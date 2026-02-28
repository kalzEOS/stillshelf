package com.stillshelf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.SessionRepository
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.common.WideCoverBackgroundBlur
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.text.Regex

data class FacetBooksUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val books: List<BookSummary> = emptyList(),
    val authorImageUrl: String? = null,
    val errorMessage: String? = null
)

private val FacetBackTitleSpacing = 12.dp

data class GenreSummary(
    val name: String,
    val count: Int
)

data class GenresUiState(
    val isLoading: Boolean = false,
    val genres: List<GenreSummary> = emptyList(),
    val errorMessage: String? = null
)

private enum class AuthorLayoutMode {
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
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val authorName = savedStateHandle.get<String>(DetailRoute.AUTHOR_NAME_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(FacetBooksUiState(isLoading = true, title = authorName))
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val authorImageUrl = when (val authorsResult = sessionRepository.fetchAuthorsForActiveLibrary(limit = 400, page = 0)) {
                is AppResult.Success -> {
                    authorsResult.value.firstOrNull { it.name.equals(authorName, ignoreCase = true) }?.imageUrl
                }

                is AppResult.Error -> null
            }

            when (val result = sessionRepository.fetchBooksForActiveLibrary(limit = 400, page = 0)) {
                is AppResult.Success -> {
                    val books = result.value.filter {
                        it.authorName.equals(authorName, ignoreCase = true)
                    }.sortedBy { it.title.lowercase() }
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            books = books,
                            authorImageUrl = authorImageUrl
                        )
                    }
                }

                is AppResult.Error -> {
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            authorImageUrl = authorImageUrl,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val seriesName = savedStateHandle.get<String>(DetailRoute.SERIES_NAME_ARG).orEmpty()
    private val mutableUiState = MutableStateFlow(FacetBooksUiState(isLoading = true, title = seriesName))
    val uiState: StateFlow<FacetBooksUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBooksForActiveLibrary(limit = 400, page = 0)) {
                is AppResult.Success -> {
                    val normalizedSeries = normalizeSeriesKey(seriesName)
                    val matchedBooks = result.value.filter {
                        if (normalizedSeries.isBlank()) return@filter false
                        val candidate = it.seriesName?.let(::normalizeSeriesKey) ?: return@filter false
                        candidate == normalizedSeries ||
                            candidate.contains(normalizedSeries) ||
                            normalizedSeries.contains(candidate)
                    }
                    val books = matchedBooks
                        .map { book ->
                            val detail = when (val detailResult = sessionRepository.fetchBookDetail(book.id)) {
                                is AppResult.Success -> detailResult.value
                                is AppResult.Error -> null
                            }
                            val sequence = book.seriesSequence
                                ?: detail?.book?.seriesSequence
                                ?: extractSeriesSequenceFromChapter(detail?.chapters?.firstOrNull()?.title)
                                ?: extractSeriesSequenceFromTitle(book.title)
                            if (sequence != null) {
                                book.copy(seriesSequence = sequence)
                            } else {
                                book
                            }
                        }
                        .sortedWith(
                            compareBy<BookSummary> { it.seriesSequence ?: Double.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
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
}

private fun extractSeriesSequenceFromTitle(title: String): Double? {
    val match = Regex("(?i)\\bbook\\s*(\\d+(?:\\.\\d+)?)\\b").find(title) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun extractSeriesSequenceFromChapter(chapterTitle: String?): Double? {
    if (chapterTitle.isNullOrBlank()) return null
    val match = Regex("(?i)\\bbook\\s*(\\d+(?:\\.\\d+)?)\\b").find(chapterTitle) ?: return null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull()
}

private fun normalizeSeriesKey(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
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
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBooksForActiveLibrary(limit = 400, page = 0)) {
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
}

@HiltViewModel
class GenresBrowseViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GenresUiState(isLoading = true))
    val uiState: StateFlow<GenresUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBooksForActiveLibrary(limit = 400, page = 0)) {
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
        refresh()
    }

    fun refresh() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = sessionRepository.fetchBooksForActiveLibrary(limit = 400, page = 0)) {
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
}

@Composable
fun AuthorDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    viewModel: AuthorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var layoutMode by rememberSaveable { mutableStateOf(AuthorLayoutMode.Grid) }
    var collapseSeries by rememberSaveable { mutableStateOf(true) }
    var optionsExpanded by remember { mutableStateOf(false) }

    val displayBooks = remember(uiState.books) { uiState.books.sortedBy { it.title.lowercase() } }
    val displayEntries = remember(displayBooks, collapseSeries) {
        buildAuthorDisplayEntries(
            books = displayBooks,
            collapseSeries = collapseSeries
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
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
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { optionsExpanded = true }) {
                    Icon(imageVector = Icons.Outlined.MoreHoriz, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = optionsExpanded,
                    onDismissRequest = { optionsExpanded = false }
                ) {
                    DropdownMenuItem(
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
                            layoutMode = AuthorLayoutMode.Grid
                            optionsExpanded = false
                        }
                    )
                    DropdownMenuItem(
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
                            layoutMode = AuthorLayoutMode.List
                            optionsExpanded = false
                        }
                    )
                    DropdownMenuItem(
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
                            collapseSeries = !collapseSeries
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
            if (uiState.authorImageUrl.isNullOrBlank()) {
                Text(
                    text = uiState.title.split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .take(2)
                        .joinToString(""),
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                AsyncImage(
                    model = rememberCoverImageModel(uiState.authorImageUrl),
                    contentDescription = uiState.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "${displayBooks.size} books",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))

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
                Button(onClick = viewModel::refresh) {
                    Text("Retry")
                }
            }

            displayBooks.isEmpty() -> {
                Text(
                    text = "No books found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                if (layoutMode == AuthorLayoutMode.Grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        gridItems(displayEntries, key = { it.stableKey }) { entry ->
                            when (entry) {
                                is AuthorDisplayEntry.BookItem -> {
                                    AuthorGridBookItem(
                                        book = entry.book,
                                        onClick = { onBookClick(entry.book.id) }
                                    )
                                }

                                is AuthorDisplayEntry.SeriesItem -> {
                                    AuthorSeriesGridItem(
                                        entry = entry,
                                        onClick = { onSeriesClick(entry.seriesName) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(displayEntries, key = { it.stableKey }) { entry ->
                            when (entry) {
                                is AuthorDisplayEntry.BookItem -> {
                                    AuthorBookRow(
                                        book = entry.book,
                                        onClick = { onBookClick(entry.book.id) }
                                    )
                                }

                                is AuthorDisplayEntry.SeriesItem -> {
                                    AuthorSeriesListRow(
                                        entry = entry,
                                        onClick = { onSeriesClick(entry.seriesName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorGridBookItem(
    book: BookSummary,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
        }
        Row(
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(book.durationSeconds),
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = { menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark as Finished") },
                        onClick = { menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Collection") },
                        onClick = { menuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorSeriesGridItem(
    entry: AuthorDisplayEntry.SeriesItem,
    onClick: () -> Unit
) {
    val lead = entry.leadBook
    val layerCount = entry.count.coerceIn(3, 6)
    val frontOffsetX = 12.dp
    val frontOffsetY = 6.dp
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardGridCoverHeight + frontOffsetY + 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            repeat(layerCount - 1) { layer ->
                val depth = layerCount - 2 - layer
                val xOffset = (frontOffsetX - ((depth + 1) * 3).dp).coerceAtLeast(0.dp)
                val yOffset = (frontOffsetY - ((depth + 1) * 2).dp).coerceAtLeast(0.dp)
                val alpha = (0.34f + layer * 0.14f).coerceIn(0f, 1f)
                FramedCoverImage(
                    coverUrl = lead.coverUrl,
                    contentDescription = entry.seriesName,
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(StandardGridCoverWidth)
                        .height(StandardGridCoverHeight)
                        .graphicsLayer(alpha = alpha),
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
                )
            }
            FramedCoverImage(
                coverUrl = lead.coverUrl,
                contentDescription = entry.seriesName,
                modifier = Modifier
                    .offset(x = frontOffsetX, y = frontOffsetY)
                    .width(StandardGridCoverWidth)
                    .height(StandardGridCoverHeight),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = WideCoverBackgroundBlur
            )
        }
        Text(
            text = entry.seriesName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (entry.count == 1) "1 book" else "${entry.count} books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthorBookRow(
    book: BookSummary,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = WideCoverBackgroundBlur
            )
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
                    text = formatDuration(book.durationSeconds),
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

@Composable
private fun AuthorSeriesListRow(
    entry: AuthorDisplayEntry.SeriesItem,
    onClick: () -> Unit
) {
    val lead = entry.leadBook
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                modifier = Modifier.size(74.dp),
                contentAlignment = Alignment.Center
            ) {
                val layerCount = entry.count.coerceIn(2, 5)
                repeat(layerCount) { layer ->
                    val xOffset = (layer * 3).dp
                    val yOffset = (layer * 2).dp
                    val alpha = if (layer == layerCount - 1) 1f else 0.56f + (layer * 0.1f)
                    FramedCoverImage(
                        coverUrl = lead.coverUrl,
                        contentDescription = entry.seriesName,
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .size(64.dp)
                            .graphicsLayer(alpha = alpha),
                        shape = RoundedCornerShape(8.dp),
                        contentScale = ContentScale.Fit,
                        backgroundBlur = WideCoverBackgroundBlur
                    )
                }
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
    viewModel: SeriesDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var listMode by remember { mutableStateOf(true) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = viewModel::refresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .pullRefresh(refreshState)
    ) {
        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading series...",
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
                    text = "No books found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                val books = uiState.books
                val leadBook = books.first()
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
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = "Filter"
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { listMode = !listMode },
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

                    item {
                        SeriesCoverStack(leadBook = leadBook, layerCount = books.size.coerceAtMost(5))
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
                            text = "${books.size} books",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (listMode) {
                        itemsIndexed(books, key = { _, item -> item.id }) { index, book ->
                            val orderLabel = book.seriesSequence
                                ?.takeIf { it > 0.0 }
                                ?.let { sequence ->
                                    if (sequence % 1.0 == 0.0) {
                                        sequence.toInt().toString()
                                    } else {
                                        sequence.toString()
                                    }
                                }
                                ?: (index + 1).toString()
                            SeriesDetailBookRow(
                                book = book,
                                orderLabel = orderLabel,
                                onClick = { onBookClick(book.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    } else {
                        val bookRows = books.chunked(2)
                        itemsIndexed(bookRows, key = { index, _ -> "series-grid-row-$index" }) { _, rowBooks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowBooks.forEach { book ->
                                    SeriesDetailGridCard(
                                        book = book,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onBookClick(book.id) }
                                    )
                                }
                                if (rowBooks.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GenresBrowseScreen(
    onBackClick: () -> Unit,
    onGenreClick: (String) -> Unit,
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = "Genres",
            onBackClick = onBackClick
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
                                    .clickable { onGenreClick(item.name) }
                                    .padding(vertical = 13.dp),
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
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FacetBooksScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onBookClick = onBookClick,
        onRetry = viewModel::refresh,
        showMoreIcon = true
    )
}

@Composable
fun NarratorDetailScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    viewModel: NarratorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FacetBooksScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onBookClick = onBookClick,
        onRetry = viewModel::refresh
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FacetBooksScreen(
    uiState: FacetBooksUiState,
    onBackClick: () -> Unit,
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        FacetTopBar(
            title = uiState.title,
            onBackClick = onBackClick,
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
    showMoreIcon: Boolean = false
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showMoreIcon) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "More"
                )
            }
        }
    }
}

@Composable
private fun SeriesCoverStack(
    leadBook: BookSummary,
    layerCount: Int
) {
    val count = layerCount.coerceAtLeast(1)
    val posterWidth = 168.dp
    val posterHeight = 172.dp
    val stepX = 14f
    val stepY = 5f
    val startX = (-(stepX * (count - 1)) / 2f).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        for (layer in 0 until count) {
            val offsetX = startX + (layer * stepX).dp
            val offsetY = (layer * stepY).dp
            val isFront = layer == count - 1
            val alpha = if (isFront) 1f else (0.32f + (layer * 0.14f)).coerceAtMost(0.78f)
            FramedCoverImage(
                coverUrl = leadBook.coverUrl,
                contentDescription = leadBook.title,
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .width(posterWidth)
                    .height(posterHeight)
                    .graphicsLayer(alpha = alpha),
                shape = RoundedCornerShape(10.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = 44.dp
            )
        }
    }
}

@Composable
private fun SeriesDetailBookRow(
    book: BookSummary,
    orderLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(72.dp)
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
                text = formatDuration(book.durationSeconds),
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

@Composable
private fun SeriesDetailGridCard(
    book: BookSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FramedCoverImage(
            coverUrl = book.coverUrl,
            contentDescription = book.title,
            modifier = Modifier
                .width(StandardGridCoverWidth)
                .height(StandardGridCoverHeight)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(8.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = formatDuration(book.durationSeconds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FacetBookRow(
    book: BookSummary,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    text = formatDuration(book.durationSeconds),
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
    val seconds = durationSeconds?.toLong() ?: return ""
    if (seconds <= 0L) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
