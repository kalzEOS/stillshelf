package com.stillshelf.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.imageLoader
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.common.WideCoverBackgroundBlur
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.navigation.BrowseRoute
import com.stillshelf.app.ui.navigation.MainRoute
import com.stillshelf.app.ui.navigation.MainTab
import com.stillshelf.app.ui.theme.AppThemeMode
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LibraryListItem(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

enum class BooksLayoutMode {
    Grid,
    List
}

enum class BooksStatusFilter(val label: String) {
    All("All"),
    Finished("Finished"),
    InProgress("In Progress"),
    NotStarted("Not Started"),
    NotFinished("Not Finished")
}

enum class BooksSortKey(val label: String) {
    Title("Title"),
    Author("Author"),
    PublicationDate("Publication Date"),
    DateAdded("Date Added"),
    Duration("Duration")
}

private val BackTitleSpacing = 12.dp

private sealed interface BooksGridEntry {
    val stableKey: String

    data class BookItem(val book: BookSummary) : BooksGridEntry {
        override val stableKey: String = "book:${book.id}"
    }

    data class SeriesStack(
        val seriesName: String,
        val books: List<BookSummary>
    ) : BooksGridEntry {
        override val stableKey: String = "series:${normalizeSeriesGroupKey(seriesName)}"
        val leadBook: BookSummary get() = books.first()
        val count: Int get() = books.size
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomePlaceholderScreen(
    onNavigateToRoute: (String) -> Unit,
    onOpenBook: (String) -> Unit = {},
    onOpenSeries: (String) -> Unit = {},
    onOpenAuthor: (String) -> Unit = {},
    onOpenPlayer: (String?) -> Unit = {},
    onHomeClick: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    menuViewModel: HomeMenuViewModel = hiltViewModel(),
    customizeViewModel: CustomizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val menuUiState by menuViewModel.uiState.collectAsStateWithLifecycle()
    val customizeUiState by customizeViewModel.uiState.collectAsStateWithLifecycle()
    var isMenuExpanded by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = viewModel::refresh
    )
    val listItemById = mapOf(
        ListSectionIds.BOOKS to LibraryListItem(
            id = ListSectionIds.BOOKS,
            title = "Books",
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            route = BrowseRoute.BOOKS
        ),
        ListSectionIds.AUTHORS to LibraryListItem(
            id = ListSectionIds.AUTHORS,
            title = "Authors",
            icon = Icons.Outlined.PersonOutline,
            route = BrowseRoute.AUTHORS
        ),
        ListSectionIds.NARRATORS to LibraryListItem(
            id = ListSectionIds.NARRATORS,
            title = "Narrators",
            icon = Icons.Outlined.GraphicEq,
            route = BrowseRoute.NARRATORS
        ),
        ListSectionIds.SERIES to LibraryListItem(
            id = ListSectionIds.SERIES,
            title = "Series",
            icon = Icons.Outlined.ViewInAr,
            route = BrowseRoute.SERIES
        ),
        ListSectionIds.COLLECTIONS to LibraryListItem(
            id = ListSectionIds.COLLECTIONS,
            title = "Collections",
            icon = Icons.Outlined.CollectionsBookmark,
            route = BrowseRoute.COLLECTIONS
        ),
        ListSectionIds.GENRES to LibraryListItem(
            id = ListSectionIds.GENRES,
            title = "Genres",
            icon = Icons.Outlined.Tag,
            route = BrowseRoute.GENRES
        ),
        ListSectionIds.BOOKMARKS to LibraryListItem(
            id = ListSectionIds.BOOKMARKS,
            title = "Bookmarks",
            icon = Icons.Outlined.BookmarkBorder,
            route = BrowseRoute.BOOKMARKS
        ),
        ListSectionIds.PLAYLISTS to LibraryListItem(
            id = ListSectionIds.PLAYLISTS,
            title = "Playlists",
            icon = Icons.Outlined.MusicNote,
            route = BrowseRoute.PLAYLISTS
        ),
        ListSectionIds.DOWNLOADED to LibraryListItem(
            id = ListSectionIds.DOWNLOADED,
            title = "Downloaded",
            icon = Icons.Outlined.Download,
            route = BrowseRoute.DOWNLOADED
        )
    )
    val orderedListItems = customizeUiState.listSections
        .mapNotNull { listItemById[it.id] }
        .filterNot { customizeUiState.hiddenListSectionIds.contains(it.id) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.libraryName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onHomeClick != null) {
                        CircleActionButton(
                            icon = Icons.Outlined.Home,
                            contentDescription = "Home",
                            onClick = onHomeClick
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    CircleActionButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search",
                        onClick = { onNavigateToRoute(MainTab.Search.route) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        CircleActionButton(
                            icon = Icons.Outlined.MoreHoriz,
                            contentDescription = "More",
                            onClick = { isMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onNavigateToRoute(MainRoute.SETTINGS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Customize") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onNavigateToRoute(MainRoute.CUSTOMIZE)
                                }
                            )
                            if (menuUiState.libraries.isNotEmpty()) {
                                HorizontalDivider()
                                menuUiState.libraries.forEach { library ->
                                    val isActive = menuUiState.activeLibraryId == library.id
                                    DropdownMenuItem(
                                        text = { Text(library.name) },
                                        trailingIcon = {
                                            if (isActive) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Active library"
                                                )
                                            }
                                        },
                                        enabled = !menuUiState.isSwitchingLibrary || isActive,
                                        onClick = {
                                            menuViewModel.switchLibrary(library.id)
                                            isMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    orderedListItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clickable { onNavigateToRoute(item.route) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(23.dp)
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp),
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < orderedListItems.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        }
                    }
                }
            }

            customizeUiState.personalizedSections.forEach { section ->
                if (customizeUiState.hiddenPersonalizedSectionIds.contains(section.id)) {
                    return@forEach
                }
                when (section.id) {
                    HomeSectionIds.CONTINUE -> {
                        item { SectionTitle("Continue Listening") }
                        item {
                            when {
                                uiState.isLoading && uiState.continueListening.isEmpty() -> {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        item { ContinueListeningSkeletonCard() }
                                        item { PosterStub(86.dp, 108.dp, Color(0xFF3B2E45)) }
                                        item { PosterStub(86.dp, 108.dp, Color(0xFF2D3840)) }
                                    }
                                }

                                uiState.continueListening.isEmpty() -> {
                                    Text(
                                        text = "No books in progress yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                else -> {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.continueListening, key = { it.book.id }) { item ->
                                            ContinueListeningCard(
                                                item = item,
                                                onClick = { onOpenPlayer(item.book.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeSectionIds.RECENTLY_ADDED -> {
                        item { SectionTitle("Recently Added") }
                        item {
                            when {
                                uiState.isLoading && uiState.recentlyAdded.isEmpty() -> {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        item { PosterStub(92.dp, 118.dp, Color(0xFFA33A31)) }
                                        item { PosterStub(92.dp, 118.dp, Color(0xFF2F4A58)) }
                                        item { PosterStub(92.dp, 118.dp, Color(0xFF8D6C3F)) }
                                    }
                                }

                                uiState.recentlyAdded.isEmpty() -> {
                                    Text(
                                        text = "No recently added books.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                else -> {
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.recentlyAdded, key = { it.id }) { book ->
                                            Box(modifier = Modifier.clickable { onOpenBook(book.id) }) {
                                                BookPoster(
                                                    book = book,
                                                    width = 92.dp,
                                                    height = 118.dp,
                                                    backgroundBlur = 64.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeSectionIds.RECENT_SERIES -> {
                        item { SectionTitle("Recent Series") }
                        item {
                            val seriesItems = uiState.recentSeries.take(6)
                            if (seriesItems.isEmpty()) {
                                Text(
                                    text = "No recent series.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(seriesItems, key = { it.seriesName }) { series ->
                                        SeriesStackCard(
                                            series = series,
                                            onClick = { onOpenSeries(series.seriesName) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HomeSectionIds.DISCOVER -> {
                        item { SectionTitle("Discover") }
                        item {
                            val discoverBooks = uiState.recentlyAdded.drop(1).take(4)
                            if (discoverBooks.isEmpty()) {
                                Text(
                                    text = "No discover picks yet.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(discoverBooks, key = { it.id }) { book ->
                                        Column(
                                            modifier = Modifier.clickable { onOpenBook(book.id) },
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            BookPoster(
                                                book = book,
                                                width = 92.dp,
                                                height = 118.dp,
                                                backgroundBlur = 64.dp
                                            )
                                            Text(
                                                text = formatDurationHoursMinutes(book.durationSeconds),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeSectionIds.NEWEST_AUTHORS -> {
                        item { SectionTitle("Newest Authors") }
                        item {
                            val authorNames = uiState.recentlyAdded
                                .flatMap { splitAuthorNames(it.authorName) }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .take(5)
                            if (authorNames.isEmpty()) {
                                Text(
                                    text = "No authors available.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(authorNames, key = { it }) { author ->
                                        AuthorCircleChip(
                                            name = author,
                                            imageUrl = uiState.authorImageUrls[author.trim().lowercase()],
                                            onClick = { onOpenAuthor(author) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }
            }

            menuUiState.errorMessage?.let { message ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = menuViewModel::clearError) {
                            Text("Dismiss")
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
fun BrowsePlaceholderScreen(
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: BooksBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh
    )
    val displayBooks = remember(uiState.books, uiState.statusFilter, uiState.sortKey) {
        val filtered = uiState.books.filterByStatus(uiState.statusFilter)
        filtered.sortedWith(sortComparator(uiState.sortKey))
    }
    val displayEntries = remember(displayBooks, uiState.collapseSeries) {
        buildBooksGridEntries(
            books = displayBooks,
            collapseSeries = uiState.collapseSeries
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackTitle(
                title = "Books",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            Box {
                CircleActionButton(
                    icon = Icons.Outlined.FilterList,
                    contentDescription = "Filter",
                    onClick = { statusMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false }
                ) {
                    BooksStatusFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (uiState.statusFilter == option) {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                viewModel.setStatusFilter(option)
                                statusMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                CircleActionButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Options",
                    onClick = { optionsMenuExpanded = true }
                )
                DropdownMenu(
                    expanded = optionsMenuExpanded,
                    onDismissRequest = { optionsMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Grid") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.GridView, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.layoutMode == BooksLayoutMode.Grid) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            viewModel.setLayoutMode(BooksLayoutMode.Grid)
                            optionsMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("List") },
                        leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Outlined.ViewList, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.layoutMode == BooksLayoutMode.List) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            viewModel.setLayoutMode(BooksLayoutMode.List)
                            optionsMenuExpanded = false
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Collapse Series") },
                        trailingIcon = {
                            if (uiState.collapseSeries) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            viewModel.toggleCollapseSeries()
                            optionsMenuExpanded = false
                        }
                    )
                    HorizontalDivider()
                    BooksSortKey.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            trailingIcon = {
                                if (uiState.sortKey == option) {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                viewModel.setSortKey(option)
                                optionsMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (uiState.isBootstrapping) {
                        Modifier
                    } else {
                        Modifier.pullRefresh(refreshState)
                    }
                )
        ) {
            when {
                uiState.isBootstrapping -> {
                    Spacer(modifier = Modifier.fillMaxSize())
                }

                uiState.books.isEmpty() && uiState.isLoading -> {
                    val placeholders = List(6) { it }
                    if (uiState.layoutMode == BooksLayoutMode.Grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            gridItems(placeholders, key = { it }) { index ->
                                val colors = listOf(
                                    Color(0xFFA1443E),
                                    Color(0xFF8A6E44),
                                    Color(0xFF445A66),
                                    Color(0xFF734246),
                                    Color(0xFFC1555A),
                                    Color(0xFF3A313E)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PosterStub(
                                        width = Dp.Unspecified,
                                        height = StandardGridCoverHeight,
                                        color = colors[index % colors.size],
                                        fillMaxWidth = true
                                    )
                                    Text(
                                        text = "Loading...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(placeholders, key = { it }) { index ->
                                val colors = listOf(
                                    Color(0xFFA1443E),
                                    Color(0xFF8A6E44),
                                    Color(0xFF445A66),
                                    Color(0xFF734246),
                                    Color(0xFFC1555A),
                                    Color(0xFF3A313E)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PosterStub(
                                        width = 56.dp,
                                        height = 82.dp,
                                        color = colors[index % colors.size],
                                        fillMaxWidth = false
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Loading...",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Loading...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                uiState.books.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "No books found in this library.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.errorMessage == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    if (uiState.layoutMode == BooksLayoutMode.Grid) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            gridItems(displayEntries, key = { it.stableKey }) { entry ->
                                when (entry) {
                                    is BooksGridEntry.BookItem -> {
                                        BookGridItem(
                                            book = entry.book,
                                            onClick = { onBookClick(entry.book.id) }
                                        )
                                    }

                                    is BooksGridEntry.SeriesStack -> {
                                        SeriesStackGridItem(
                                            entry = entry,
                                            onClick = { onSeriesClick(entry.seriesName) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(displayEntries, key = { it.stableKey }) { entry ->
                                when (entry) {
                                    is BooksGridEntry.BookItem -> {
                                        BookListItem(
                                            book = entry.book,
                                            onClick = { onBookClick(entry.book.id) }
                                        )
                                    }

                                    is BooksGridEntry.SeriesStack -> {
                                        SeriesStackListItem(
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

            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun BookGridItem(
    book: BookSummary,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val posterWidth = StandardGridCoverWidth
    val posterHeight = StandardGridCoverHeight
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            BookPoster(
                book = book,
                width = posterWidth,
                height = posterHeight,
                contentScale = ContentScale.Fit
            )
        }
        Row(
            modifier = Modifier
                .width(posterWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDurationHoursMinutes(book.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(22.dp)) {
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
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("Mark as Finished") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("Add to Collection") }, onClick = { menuExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun SeriesStackGridItem(
    entry: BooksGridEntry.SeriesStack,
    onClick: () -> Unit
) {
    val book = entry.leadBook
    val layerCount = entry.count.coerceIn(2, 3)
    val frameWidth = StandardGridCoverWidth
    val frameHeight = StandardGridCoverHeight
    val stackStepX = 5.dp
    val stackStepY = 10.dp
    val totalShiftX = stackStepX * (layerCount - 1)
    val totalShiftY = stackStepY * (layerCount - 1)
    val cardWidth = frameWidth - totalShiftX - 3.dp
    val cardHeight = frameHeight - totalShiftY - 3.dp
    val baseShiftX = (-4).dp
    val baseShiftY = 1.dp
    val layerShape = RoundedCornerShape(8.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(frameHeight)
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter
        ) {
            repeat(layerCount) { layer ->
                val xOffset = baseShiftX + (stackStepX * layer)
                val yOffset = baseShiftY + (stackStepY * layer)
                val alpha = 1f
                val layerShadow = if (layer == layerCount - 1) 1.2.dp else 3.4.dp
                FramedCoverImage(
                    coverUrl = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(cardWidth)
                        .height(cardHeight)
                        .shadow(elevation = layerShadow, shape = layerShape, clip = false)
                        .graphicsLayer(alpha = alpha.coerceIn(0f, 1f)),
                    shape = layerShape,
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
                )
            }
        }
        Row(
            modifier = Modifier
                .width(frameWidth)
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
private fun BookListItem(
    book: BookSummary,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookPoster(
            book = book,
            width = 88.dp,
            height = 88.dp,
            shape = RoundedCornerShape(6.dp),
            contentScale = ContentScale.Fit,
            backgroundBlur = 44.dp
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDurationHoursMinutes(book.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "Book actions"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(text = { Text("Download") }, onClick = { menuExpanded = false })
                DropdownMenuItem(text = { Text("Mark as Finished") }, onClick = { menuExpanded = false })
                DropdownMenuItem(text = { Text("Add to Collection") }, onClick = { menuExpanded = false })
            }
        }
    }
}

@Composable
private fun SeriesStackListItem(
    entry: BooksGridEntry.SeriesStack,
    onClick: () -> Unit
) {
    val lead = entry.leadBook
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(88.dp)
                .clipToBounds()
        ) {
            val layerCount = entry.books.size.coerceIn(2, 3)
            val frameSize = 88.dp
            val stackStepX = 4.dp
            val stackStepY = 7.dp
            val totalShiftX = stackStepX * (layerCount - 1)
            val totalShiftY = stackStepY * (layerCount - 1)
            val cardWidth = frameSize - totalShiftX - 3.dp
            val cardHeight = frameSize - totalShiftY - 3.dp
            val baseShiftX = (-4).dp
            val baseShiftY = 1.dp
            val layerShape = RoundedCornerShape(6.dp)
            repeat(layerCount) { layer ->
                val xOffset = baseShiftX + (stackStepX * layer)
                val yOffset = baseShiftY + (stackStepY * layer)
                val alpha = 1f
                val layerShadow = if (layer == layerCount - 1) 1.dp else 2.8.dp
                FramedCoverImage(
                    coverUrl = lead.coverUrl,
                    contentDescription = entry.seriesName,
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(cardWidth)
                        .height(cardHeight)
                        .shadow(elevation = layerShadow, shape = layerShape, clip = false)
                        .graphicsLayer(alpha = alpha.coerceIn(0f, 1f)),
                    shape = layerShape,
                    contentScale = ContentScale.Fit,
                    backgroundBlur = 44.dp
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

private fun buildBooksGridEntries(
    books: List<BookSummary>,
    collapseSeries: Boolean
): List<BooksGridEntry> {
    if (!collapseSeries) return books.map { BooksGridEntry.BookItem(it) }

    val emittedSeries = mutableSetOf<String>()
    val entries = mutableListOf<BooksGridEntry>()
    books.forEach { book ->
        val seriesName = book.seriesName?.trim().orEmpty()
        if (seriesName.isBlank()) {
            entries += BooksGridEntry.BookItem(book)
            return@forEach
        }

        val normalizedSeries = normalizeSeriesGroupKey(seriesName)
        if (normalizedSeries.isBlank() || emittedSeries.contains(normalizedSeries)) {
            return@forEach
        }

        val groupedBooks = books.filter {
            normalizeSeriesGroupKey(it.seriesName.orEmpty()) == normalizedSeries
        }
        emittedSeries += normalizedSeries

        if (groupedBooks.size <= 1) {
            entries += BooksGridEntry.BookItem(book)
        } else {
            entries += BooksGridEntry.SeriesStack(
                seriesName = seriesName,
                books = groupedBooks
            )
        }
    }
    return entries
}

@Composable
fun AuthorsBrowseScreen(
    onAuthorClick: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: AuthorsBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EntityBrowseScreen(
        title = "Authors",
        uiState = uiState,
        onRetry = viewModel::refresh,
        onEntityClick = { onAuthorClick(it.name) },
        onBackClick = onBackClick,
        onHomeClick = onHomeClick,
        showAvatar = true
    )
}

@Composable
fun CollectionsBrowseScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: CollectionsBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EntityBrowseScreen(
        title = "Collections",
        uiState = uiState,
        onRetry = viewModel::refresh,
        onBackClick = onBackClick,
        onHomeClick = onHomeClick
    )
}

@Composable
fun NarratorsBrowseScreen(viewModel: NarratorsBrowseViewModel = hiltViewModel()) {
    NarratorsBrowseScreen(onNarratorClick = {}, onBackClick = null, viewModel = viewModel)
}

@Composable
fun NarratorsBrowseScreen(
    onNarratorClick: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: NarratorsBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        BackTitle(
            title = "Narrators",
            onBackClick = onBackClick,
            onHomeClick = onHomeClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading Narrators...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.entities.isEmpty() && uiState.errorMessage == null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(52.dp)
                        )
                        Text(
                            text = "No narrators in this library yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    items(uiState.entities, key = { it.id }) { entity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNarratorClick(entity.name) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entity.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entity.subtitle.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesBrowseScreen(
    onSeriesClick: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: SeriesBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        BackTitle(
            title = "Series",
            onBackClick = onBackClick,
            onHomeClick = onHomeClick
        )
        Spacer(modifier = Modifier.height(10.dp))

        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading Series...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            uiState.series.isEmpty() -> {
                Text(
                    text = uiState.errorMessage ?: "No Series found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.errorMessage == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = viewModel::refresh) {
                    Text("Retry")
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    gridItems(uiState.series, key = { it.id }) { series ->
                        Column(
                            modifier = Modifier.clickable { onSeriesClick(series.name) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(StandardGridCoverHeight)
                                    .clipToBounds(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                val inferredCount = series.subtitle
                                    .orEmpty()
                                    .trim()
                                    .substringBefore(" ")
                                    .toIntOrNull()
                                val layerCount = inferredCount?.coerceIn(2, 3) ?: 3
                                val stackStepX = 5.dp
                                val stackStepY = 10.dp
                                val totalShiftX = stackStepX * (layerCount - 1)
                                val totalShiftY = stackStepY * (layerCount - 1)
                                val cardWidth = StandardGridCoverWidth - totalShiftX - 3.dp
                                val cardHeight = StandardGridCoverHeight - totalShiftY - 3.dp
                                val baseShiftX = (-4).dp
                                val baseShiftY = 1.dp
                                val layerShape = RoundedCornerShape(8.dp)
                                repeat(layerCount) { layer ->
                                    val xOffset = baseShiftX + (stackStepX * layer)
                                    val yOffset = baseShiftY + (stackStepY * layer)
                                    val alpha = 1f
                                    val layerShadow = if (layer == layerCount - 1) 1.2.dp else 3.4.dp
                                    FramedCoverImage(
                                        coverUrl = series.coverUrl,
                                        contentDescription = series.name,
                                        modifier = Modifier
                                            .offset(x = xOffset, y = yOffset)
                                            .width(cardWidth)
                                            .height(cardHeight)
                                            .shadow(elevation = layerShadow, shape = layerShape, clip = false)
                                            .graphicsLayer(alpha = alpha.coerceIn(0f, 1f)),
                                        shape = layerShape,
                                        contentScale = ContentScale.Fit,
                                        backgroundBlur = WideCoverBackgroundBlur
                                    )
                                }
                            }
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .width(StandardGridCoverWidth)
                                    .align(Alignment.CenterHorizontally),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = series.subtitle.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                ,
                                modifier = Modifier
                                    .width(StandardGridCoverWidth)
                                    .align(Alignment.CenterHorizontally),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrowseSectionPlaceholderScreen(
    title: String,
    emptyMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.GridView,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        BackTitle(
            title = title,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SearchPlaceholderScreen(
    onBookClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNarratorClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query = uiState.query.trim()
    val normalized = query.lowercase()
    val matchedBooks = uiState.books
    val matchedAuthors = uiState.authors
    val matchedSeries = uiState.series
    val matchedNarrators = uiState.narrators
    val noMatches = normalized.isNotBlank() &&
        !uiState.isLoading &&
        uiState.errorMessage.isNullOrBlank() &&
        matchedBooks.isEmpty() &&
        matchedAuthors.isEmpty() &&
        matchedSeries.isEmpty() &&
        matchedNarrators.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (normalized.isBlank()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Search your library", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Search by Title, Author, Series, and More",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (matchedBooks.isNotEmpty()) {
                        item { SectionTitle("Books") }
                        items(matchedBooks.take(20), key = { it.id }) { book ->
                            SearchBookRow(
                                book = book,
                                onClick = { onBookClick(book.id) }
                            )
                        }
                    }
                    if (matchedAuthors.isNotEmpty()) {
                        item { SectionTitle("Authors") }
                        items(matchedAuthors, key = { it.id }) { author ->
                            SearchEntityRow(text = author.name, onClick = { onAuthorClick(author.name) })
                        }
                    }
                    if (matchedSeries.isNotEmpty()) {
                        item { SectionTitle("Series") }
                        items(matchedSeries, key = { it.id }) { series ->
                            SearchEntityRow(text = series.name, onClick = { onSeriesClick(series.name) })
                        }
                    }
                    if (matchedNarrators.isNotEmpty()) {
                        item { SectionTitle("Narrators") }
                        items(matchedNarrators, key = { it.id }) { narrator ->
                            SearchEntityRow(
                                text = narrator.name,
                                onClick = { onNarratorClick(narrator.name) }
                            )
                        }
                    }
                    if (!uiState.errorMessage.isNullOrBlank()) {
                        item {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (noMatches) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No matches",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                placeholder = { Text("Search") },
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}

@Composable
private fun SearchBookRow(
    book: BookSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookPoster(
            book = book,
            width = 58.dp,
            height = 72.dp,
            shape = RoundedCornerShape(6.dp),
            backgroundBlur = 44.dp
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
private fun SearchEntityRow(
    text: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
fun DownloadsPlaceholderScreen() {
    DownloadsPlaceholderScreen(onBackClick = null, onHomeClick = null)
}

@Composable
fun DownloadsPlaceholderScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackTitle(
                title = "Downloaded",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = Icons.AutoMirrored.Outlined.ViewList,
                contentDescription = "Downloaded layout"
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(text = "Downloaded", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Downloaded books will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun SettingsPlaceholderScreen(
    onManageServers: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var lockScreenControl by remember { mutableStateOf("skip") }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackTitle(
            title = "Settings",
            onBackClick = onBackClick
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageServers)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(uiState.serverDisplayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.serverHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Active",
                    tint = Color(0xFF2EAE62)
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Immersive Player", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.immersivePlayerEnabled,
                    onCheckedChange = viewModel::setImmersivePlayerEnabled
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Material Design", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.materialDesignEnabled,
                    onCheckedChange = viewModel::setMaterialDesignEnabled
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsRow(
                title = "Follow System Theme",
                selected = uiState.themeMode == AppThemeMode.FollowSystem,
                onClick = { viewModel.setThemeMode(AppThemeMode.FollowSystem) }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Light Theme",
                selected = uiState.themeMode == AppThemeMode.Light,
                onClick = { viewModel.setThemeMode(AppThemeMode.Light) }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Dark Theme",
                selected = uiState.themeMode == AppThemeMode.Dark,
                onClick = { viewModel.setThemeMode(AppThemeMode.Dark) }
            )
        }

        Text(
            text = "SKIP BUTTONS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsRow(
                title = "Skip Forward",
                value = "15 seconds",
                onClick = { infoMessage = "Skip forward duration editor coming soon." }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Skip Backward",
                value = "15 seconds",
                onClick = { infoMessage = "Skip backward duration editor coming soon." }
            )
        }

        Text(
            text = "LOCK SCREEN CONTROLS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsRow(
                title = "Next/Previous",
                selected = lockScreenControl == "next",
                onClick = {
                    lockScreenControl = "next"
                    infoMessage = "Lock screen controls set to Next/Previous."
                }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Skip Forward/Back",
                selected = lockScreenControl == "skip",
                onClick = {
                    lockScreenControl = "skip"
                    infoMessage = "Lock screen controls set to Skip Forward/Back."
                }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsRow(
                title = "About",
                onClick = { infoMessage = "StillShelf alpha build." }
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            SettingsRow(title = "Manage Servers", onClick = onManageServers)
            HorizontalDivider()
            SettingsRow(
                title = "Sign Out",
                onClick = {
                    infoMessage = null
                    viewModel.onSignOutClick()
                }
            )
        }
        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = viewModel::clearError) {
                Text("Dismiss")
            }
        }
        infoMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (selected) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
        } else {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ServersManagementScreen(
    onBackClick: () -> Unit,
    onAddServerClick: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: ServerManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackTitle(
                title = "Servers",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                uiState.servers.forEachIndexed { index, server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveServer(server.id) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(text = server.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = server.baseUrl.removePrefix("https://").removePrefix("http://"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (server.id == uiState.activeServerId) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Active")
                        }
                    }
                    if (index < uiState.servers.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                Text(
                    text = "Add Server",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddServerClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = viewModel::clearError) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
fun BookDetailScreen(
    onBackClick: () -> Unit,
    onStartListening: (String) -> Unit = {},
    onOpenAuthor: (String) -> Unit = {},
    onHomeClick: (() -> Unit)? = null,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackUiState by viewModel.playbackUiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf("About") }
    var actionsExpanded by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var aboutExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(infoMessage) {
        val message = infoMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        infoMessage = null
    }
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleActionButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackClick
                )
                if (onHomeClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircleActionButton(
                        icon = Icons.Outlined.Home,
                        contentDescription = "Home",
                        onClick = onHomeClick
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                CircleActionButton(
                    icon = Icons.Outlined.Download,
                    contentDescription = "Download",
                    onClick = { infoMessage = "Download queued (placeholder)." }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    CircleActionButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = "More",
                        onClick = { actionsExpanded = true }
                    )
                    DropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            onClick = {
                                infoMessage = "Download queued (placeholder)."
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Skip Intro & Outro") },
                            onClick = {
                                infoMessage = "Skip Intro/Outro enabled (placeholder)."
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mark as Finished") },
                            onClick = {
                                infoMessage = "Marked finished (placeholder)."
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Collection") },
                            onClick = {
                                viewModel.addToCollection()
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Book") },
                            onClick = {
                                infoMessage = "Already viewing this book."
                                actionsExpanded = false
                            }
                        )
                    }
                }
            }
        }

        when {
            uiState.isLoading -> {
                item {
                    Text(
                        text = "Loading book...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.detail == null -> {
                item {
                    Text(
                        text = uiState.errorMessage ?: "Book not found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    Button(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                }
            }

            else -> {
                val detail = uiState.detail ?: return@LazyColumn
                val book = detail.book
                val livePlaybackSeconds = if (playbackUiState.book?.id == book.id) {
                    (playbackUiState.positionMs.coerceAtLeast(0L) / 1000.0)
                } else {
                    0.0
                }
                val serverPlaybackSeconds = uiState.currentTimeSeconds?.coerceAtLeast(0.0) ?: 0.0
                val hasProgress = livePlaybackSeconds > 0.5 ||
                    serverPlaybackSeconds > 0.5 ||
                    (uiState.progressPercent ?: 0.0) > 0.001
                val listenLabel = if (hasProgress) "Continue Listening" else "Start Listening"
                val chapterPositionSeconds = if (playbackUiState.book?.id == book.id) {
                    (playbackUiState.positionMs.coerceAtLeast(0L) / 1000.0)
                } else {
                    uiState.currentTimeSeconds?.coerceAtLeast(0.0)
                }
                val activeChapterIndex = chapterPositionSeconds
                    ?.let { position -> findActiveChapterIndex(detail.chapters, position) }
                    ?: -1
                val seriesOrderLabel = resolveSeriesOrderLabel(
                    seriesSequence = book.seriesSequence,
                    book.title,
                    detail.chapters.firstOrNull()?.title
                )

                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(320.dp)
                        ) {
                            BookPoster(
                                book = book,
                                width = 240.dp,
                                height = 320.dp,
                                fillMaxWidth = true,
                                shape = RoundedCornerShape(10.dp),
                                contentScale = ContentScale.Fit
                            )
                            seriesOrderLabel?.let { order ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = (-4).dp, y = (-4).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#$order",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.clickable { onOpenAuthor(book.authorName) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = book.authorName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                item {
                    val topMetadata = buildList {
                        detail.publishedYear?.let { add(it) }
                        formatDurationHoursMinutes(book.durationSeconds).ifBlank { null }?.let { add(it) }
                        book.genres.firstOrNull()?.let { add(it) }
                    }.joinToString(" · ")
                    if (topMetadata.isNotBlank()) {
                        Text(
                            text = topMetadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Button(
                        onClick = { onStartListening(book.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(listenLabel)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailTabChip(
                            title = "About",
                            selected = selectedTab == "About",
                            onClick = { selectedTab = "About" }
                        )
                        DetailTabChip(
                            title = "Chapters",
                            selected = selectedTab == "Chapters",
                            onClick = { selectedTab = "Chapters" }
                        )
                        DetailTabChip(
                            title = "Bookmarks",
                            selected = selectedTab == "Bookmarks",
                            onClick = { selectedTab = "Bookmarks" }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }

                when (selectedTab) {
                    "About" -> {
                        val about = detail.description?.ifBlank { null } ?: "No description available."
                        val shouldCollapse = about.length > 280
                        val aboutPreview = if (shouldCollapse && !aboutExpanded) {
                            about.take(280).trimEnd() + "..."
                        } else {
                            about
                        }
                        item {
                            Text(
                                text = aboutPreview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (shouldCollapse) {
                            item {
                                Text(
                                    text = if (aboutExpanded) "Less" else "More",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { aboutExpanded = !aboutExpanded }
                                )
                            }
                        }
                        item { Text(text = "Details", style = MaterialTheme.typography.titleLarge) }
                        item { DetailValueRow(title = "Released", value = detail.publishedYear ?: "Unknown") }
                        item {
                            DetailValueRow(
                                title = "Duration",
                                value = formatDurationHoursMinutes(book.durationSeconds).ifBlank { "Unknown" }
                            )
                        }
                        item {
                            DetailValueRow(
                                title = "Size",
                                value = formatFileSize(detail.sizeBytes)
                            )
                        }
                        if (book.genres.isNotEmpty()) {
                            item { Text(text = "Genres", style = MaterialTheme.typography.titleLarge) }
                            item {
                                Text(
                                    text = book.genres.joinToString(separator = " · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    "Chapters" -> {
                        if (detail.chapters.isEmpty()) {
                            item {
                                CenteredEmptyState(
                                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                                    title = "No chapters available",
                                    subtitle = "This audiobook does not include chapter markers."
                                )
                            }
                        } else {
                            itemsIndexed(detail.chapters) { index, chapter ->
                                val isActive = index == activeChapterIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable { viewModel.playChapter(chapter.startSeconds) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = formatSecondsAsHms(chapter.startSeconds),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isActive) {
                                        ChapterPlaybackIndicator(
                                            isPlaying = playbackUiState.isPlaying,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    else -> {
                        if (detail.bookmarks.isEmpty()) {
                            item {
                                CenteredEmptyState(
                                    icon = Icons.Outlined.BookmarkBorder,
                                    title = "No bookmarks yet",
                                    subtitle = "Your bookmarks will appear here."
                                )
                            }
                        } else {
                            items(detail.bookmarks, key = { it.id }) { bookmark ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = bookmark.title ?: "Bookmark",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    bookmark.timeSeconds?.let { bookmarkTime ->
                                        Text(
                                            text = formatSecondsAsHms(bookmarkTime),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerPlaceholderScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val playbackUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewItem by viewModel.previewItem.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val book = playbackUiState.book ?: previewItem?.book
    val durationSeconds = when {
        playbackUiState.durationMs > 0L -> playbackUiState.durationMs / 1000.0
        else -> previewItem?.book?.durationSeconds ?: 0.0
    }
    val positionSeconds = if (playbackUiState.book != null) {
        playbackUiState.positionMs / 1000.0
    } else {
        previewItem?.currentTimeSeconds ?: 0.0
    }
    val progress = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbingProgress by remember { mutableStateOf(progress) }
    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) {
            scrubbingProgress = progress
        }
    }
    val effectiveProgress = if (isScrubbing) scrubbingProgress else progress
    val activeChapterTitle = findActiveChapterTitle(chapters, positionSeconds)
    val playerTitle = if (
        book != null &&
        !activeChapterTitle.isNullOrBlank() &&
        !activeChapterTitle.equals(book.title, ignoreCase = true)
    ) {
        "${book.title} - $activeChapterTitle"
    } else {
        book?.title.orEmpty()
    }
    val playerSeriesOrderLabel = resolveSeriesOrderLabel(
        seriesSequence = book?.seriesSequence,
        book?.title,
        activeChapterTitle
    )
    val immersiveEnabled = appearanceUiState.immersivePlayerEnabled && !book?.coverUrl.isNullOrBlank()
    var dragDistance by remember { mutableStateOf(0f) }
    var bottomMenuExpanded by remember { mutableStateOf(false) }
    var playerInfoMessage by remember { mutableStateOf<String?>(null) }
    val speeds = remember { listOf(0.8f, 1.0f, 1.3f, 1.5f, 1.8f, 2.0f) }
    var speedIndex by rememberSaveable { mutableStateOf(2) }
    val speedLabel = "${speeds[speedIndex]}x"
    val playerTopOffset = 8.dp
    val mainPlayButtonContainer = if (immersiveEnabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val mainPlayButtonIconTint = if (mainPlayButtonContainer.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    val progressActiveColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val progressTrackColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val primaryTextColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onBackClick) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) dragDistance += dragAmount
                    },
                    onDragEnd = {
                        if (dragDistance > 110f) {
                            viewModel.onDismissPlayer()
                            onBackClick?.invoke()
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        if (immersiveEnabled) {
            AsyncImage(
                model = rememberCoverImageModel(book?.coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.98f)
                    .blur(28.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.34f),
                                0.24f to Color.Black.copy(alpha = 0.16f),
                                0.58f to MaterialTheme.colorScheme.background.copy(alpha = 0.08f),
                                1.00f to MaterialTheme.colorScheme.background.copy(alpha = 0.24f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        Spacer(modifier = Modifier.height(playerTopOffset))
        if (book == null) {
            CenteredEmptyState(
                icon = Icons.Outlined.PlayArrow,
                title = "Nothing playing",
                subtitle = "Start a book from Continue Listening."
            )
            return
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val coverWidth = minOf(332.dp, maxWidth)
            val coverHeight = coverWidth * (320f / 332f)
            Box(
                modifier = Modifier
                    .width(coverWidth)
                    .height(coverHeight)
            ) {
                BookPoster(
                    book = book,
                    width = coverWidth,
                    height = coverHeight,
                    fillMaxWidth = true,
                    shape = RoundedCornerShape(14.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
                )
                playerSeriesOrderLabel?.let { order ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#$order",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = playerTitle,
                style = MaterialTheme.typography.titleMedium,
                color = primaryTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
            )
            Icon(
                imageVector = Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = secondaryTextColor,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))
        PlayerProgressBar(
            progress = effectiveProgress,
            activeColor = progressActiveColor,
            trackColor = progressTrackColor,
            onProgressChange = { newProgress ->
                isScrubbing = true
                scrubbingProgress = newProgress
                viewModel.onScrubProgress(newProgress)
            },
            onProgressChangeFinished = { finalProgress ->
                scrubbingProgress = finalProgress
                viewModel.onScrubProgressFinished(finalProgress)
                isScrubbing = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        val remainingSeconds = (durationSeconds - positionSeconds).coerceAtLeast(0.0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSecondsAsHms(positionSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
            Text(
                text = "${formatDurationHoursMinutes(remainingSeconds)} left · ${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "-${formatSecondsAsHms(remainingSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
        }
        playerInfoMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Seek15Button(
                    forward = false,
                    tint = primaryTextColor,
                    onClick = viewModel::onRewindClick
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mainPlayButtonContainer)
                        .clickable(onClick = viewModel::onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackUiState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playbackUiState.isPlaying) "Pause" else "Play",
                        tint = mainPlayButtonIconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Seek15Button(
                    forward = true,
                    tint = primaryTextColor,
                    onClick = viewModel::onForwardClick
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerBottomToolItem(
                    label = "Speed",
                    valueText = speedLabel,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = {
                        speedIndex = (speedIndex + 1) % speeds.size
                        playerInfoMessage = "Speed set to ${speeds[speedIndex]}x."
                    }
                )
                PlayerBottomToolItem(
                    label = "Timer",
                    imageVector = Icons.Outlined.Timer,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = { playerInfoMessage = "Sleep timer controls coming soon." }
                )
                PlayerBottomToolItem(
                    label = "History",
                    imageVector = Icons.Outlined.Refresh,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = { playerInfoMessage = "History controls coming soon." }
                )
                PlayerBottomToolItem(
                    label = "Download",
                    imageVector = Icons.Outlined.Download,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = { playerInfoMessage = "Download controls coming soon." }
                )
                Box {
                    PlayerBottomToolItem(
                        label = "More",
                        imageVector = Icons.Outlined.MoreHoriz,
                        primaryColor = primaryTextColor,
                        secondaryColor = secondaryTextColor,
                        onClick = { bottomMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = bottomMenuExpanded,
                        onDismissRequest = { bottomMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                playerInfoMessage = "Share coming soon."
                                bottomMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Book") },
                            onClick = {
                                playerInfoMessage = "Already viewing this book."
                                bottomMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PlayerProgressBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val thumbSize = 11.dp
    val barHeight = 5.dp
    var widthPx by remember { mutableStateOf(0f) }
    var dragProgress by remember { mutableStateOf(clampedProgress) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(clampedProgress, isDragging) {
        if (!isDragging) {
            dragProgress = clampedProgress
        }
    }

    fun offsetToProgress(x: Float): Float {
        if (widthPx <= 0f) return clampedProgress
        return (x / widthPx).coerceIn(0f, 1f)
    }

    val draggableState = rememberDraggableState { delta ->
        if (widthPx <= 0f) return@rememberDraggableState
        isDragging = true
        dragProgress = (dragProgress + (delta / widthPx)).coerceIn(0f, 1f)
        onProgressChange(dragProgress)
    }
    val displayProgress = if (isDragging) dragProgress else clampedProgress

    BoxWithConstraints(
        modifier = modifier
            .height(thumbSize)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx, clampedProgress) {
                detectTapGestures { offset ->
                    val tappedProgress = offsetToProgress(offset.x)
                    isDragging = false
                    onProgressChange(tappedProgress)
                    onProgressChangeFinished(tappedProgress)
                }
            }
            .draggable(
                state = draggableState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                onDragStarted = { offset ->
                    isDragging = true
                    dragProgress = offsetToProgress(offset.x)
                    onProgressChange(dragProgress)
                },
                onDragStopped = {
                    onProgressChangeFinished(dragProgress)
                    isDragging = false
                }
            )
    ) {
        val barShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.CenterStart)
                .clip(barShape)
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress)
                .height(barHeight)
                .align(Alignment.CenterStart)
                .clip(barShape)
                .background(activeColor)
        )
        val maxOffset = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = maxOffset * displayProgress)
                .size(thumbSize)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}

@Composable
private fun PlayerBottomToolItem(
    label: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    valueText: String? = null,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = primaryColor
            )
        } else if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = secondaryColor,
            maxLines = 1
        )
    }
}

@Composable
private fun Seek15Button(
    forward: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { scaleX = if (forward) 1f else -1f }
        ) {
            val strokeWidth = 2.6.dp.toPx()
            val inset = 3.dp.toPx()
            val arcSize = size.minDimension - inset * 2
            drawArc(
                color = tint,
                startAngle = 20f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val radius = arcSize / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val angle = Math.toRadians(270.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            val head = 5.dp.toPx()
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(x - head, y - head * 0.55f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(x, y),
                end = androidx.compose.ui.geometry.Offset(x - head, y + head * 0.55f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        Text(
            text = "15",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tint
        )
    }
}

@Composable
private fun ChapterPlaybackIndicator(
    isPlaying: Boolean,
    tint: Color
) {
    if (!isPlaying) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = "Play chapter",
            tint = tint
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "chapter-playing-bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 470),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 510),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = Modifier
            .width(14.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((4.dp + 10.dp * bar1))
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((4.dp + 10.dp * bar2))
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((4.dp + 10.dp * bar3))
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
    }
}

@Composable
private fun rememberDominantCoverColor(
    coverUrl: String?,
    enabled: Boolean
): Color? {
    val context = LocalContext.current
    val dominantColorState = produceState<Color?>(initialValue = null, coverUrl, enabled) {
        if (!enabled || coverUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolvedCover = splitAuthenticatedUrl(coverUrl)
                val request = ImageRequest.Builder(context)
                    .data(resolvedCover.cleanUrl)
                    .apply {
                        resolvedCover.authToken?.takeIf { it.isNotBlank() }?.let { token ->
                            addHeader("Authorization", authorizationHeaderValue(token))
                        }
                    }
                    .allowHardware(false)
                    .size(64)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
                val bitmap = drawable.toBitmap(
                    width = 20,
                    height = 20,
                    config = Bitmap.Config.ARGB_8888
                )
                averageBitmapColor(bitmap)
            }.getOrNull()
        }
    }
    return dominantColorState.value
}

private fun averageBitmapColor(bitmap: Bitmap): Color {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L

    for (x in 0 until width) {
        for (y in 0 until height) {
            val pixel = bitmap.getPixel(x, y)
            red += android.graphics.Color.red(pixel)
            green += android.graphics.Color.green(pixel)
            blue += android.graphics.Color.blue(pixel)
            count += 1
        }
    }

    if (count == 0L) return Color(0xFF2B2D31)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt()
    )
}

private fun findActiveChapterTitle(
    chapters: List<BookChapter>,
    positionSeconds: Double
): String? {
    val chapterIndex = findActiveChapterIndex(chapters, positionSeconds)
    if (chapterIndex < 0 || chapterIndex >= chapters.size) return null
    return chapters[chapterIndex].title.trim().takeIf { it.isNotBlank() }
}

private fun findActiveChapterIndex(
    chapters: List<BookChapter>,
    positionSeconds: Double
): Int {
    if (chapters.isEmpty()) return -1
    val position = positionSeconds.coerceAtLeast(0.0)
    val index = chapters.indexOfFirst { item ->
        val end = item.endSeconds ?: Double.POSITIVE_INFINITY
        position >= item.startSeconds && position < end
    }
    return if (index >= 0) index else chapters.lastIndex
}

@Composable
private fun PlayerToolsCard(
    title: String,
    rows: List<String>
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            rows.forEachIndexed { index, row ->
                Text(
                    text = row,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun DetailTabChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selected) {
            HorizontalDivider(
                modifier = Modifier.width(32.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CustomizePlaceholderScreen(
    onDone: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: CustomizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf("Lists") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Customize",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (onHomeClick != null) {
                CircleActionButton(
                    icon = Icons.Outlined.Home,
                    contentDescription = "Home",
                    onClick = onHomeClick
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            CircleActionButton(
                icon = Icons.Filled.Check,
                contentDescription = "Done",
                onClick = onDone
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CustomizeTabChip(
                label = "Lists",
                selected = selectedTab == "Lists",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Lists" }
            )
            CustomizeTabChip(
                label = "Personalized",
                selected = selectedTab == "Personalized",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Personalized" }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        val rows = if (selectedTab == "Lists") uiState.listSections else uiState.personalizedSections
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(rows, key = { it.id }) { row ->
                val enabled = if (selectedTab == "Lists") {
                    !uiState.hiddenListSectionIds.contains(row.id)
                } else {
                    !uiState.hiddenPersonalizedSectionIds.contains(row.id)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clickable {
                            if (selectedTab == "Lists") {
                                viewModel.toggleListSection(row.id)
                            } else {
                                viewModel.togglePersonalizedSection(row.id)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (enabled) Color.Black else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (enabled) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            if (selectedTab == "Lists") {
                                viewModel.moveListSection(row.id)
                            } else {
                                viewModel.movePersonalizedSection(row.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizeTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun EntityBrowseScreen(
    title: String,
    uiState: EntityBrowseUiState,
    onRetry: () -> Unit,
    onEntityClick: ((com.stillshelf.app.core.model.NamedEntitySummary) -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    showAvatar: Boolean = false
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
        BackTitle(
            title = title,
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
                uiState.entities.isNotEmpty() -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.entities, key = { it.id }) { entity ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp)
                                        .clickable(enabled = onEntityClick != null) {
                                            onEntityClick?.invoke(entity)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (showAvatar) {
                                        if (!entity.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = rememberCoverImageModel(entity.imageUrl),
                                                contentDescription = entity.name,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = entity.name
                                                        .split(" ")
                                                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                                        .take(2)
                                                        .joinToString(""),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = entity.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entity.subtitle.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (onEntityClick != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            }
                        }
                    }
                }

                uiState.isLoading -> {
                    Text(
                        text = "Loading $title...",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "No $title found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.errorMessage == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Button(onClick = onRetry) {
                            Text("Retry")
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
private fun SeriesStackCard(
    series: SeriesStackSummary,
    onClick: () -> Unit
) {
    val book = series.leadBook
    val posterWidth = 122.dp
    val posterHeight = 160.dp
    val layerCount = series.count.coerceIn(3, 6)
    val frontOffsetX = 16.dp
    val frontOffsetY = 8.dp
    Column(
        modifier = Modifier
            .width(144.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(144.dp)
                .height(posterHeight + frontOffsetY + 4.dp)
        ) {
            repeat(layerCount - 1) { layer ->
                val depth = (layerCount - 2 - layer)
                val xOffset = (frontOffsetX - ((depth + 1) * 4).dp).coerceAtLeast(0.dp)
                val yOffset = (frontOffsetY - ((depth + 1) * 3).dp).coerceAtLeast(0.dp)
                val alpha = 1f
                FramedCoverImage(
                    coverUrl = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(posterWidth)
                        .height(posterHeight)
                        .graphicsLayer(alpha = alpha.coerceIn(0f, 1f)),
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
                )
            }
            FramedCoverImage(
                coverUrl = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .offset(x = frontOffsetX, y = frontOffsetY)
                    .width(posterWidth)
                    .height(posterHeight),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Fit,
                backgroundBlur = WideCoverBackgroundBlur
            )
        }
        Text(
            text = series.seriesName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (series.count == 1) "1 book" else "${series.count} books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthorCircleChip(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNullOrBlank()) {
                Text(
                    text = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                AsyncImage(
                    model = rememberCoverImageModel(imageUrl),
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "1 book",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ContinueListeningCard(
    item: ContinueListeningItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF665A2E))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .width(236.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookPoster(
                book = item.book,
                width = 64.dp,
                height = 84.dp,
                shape = RoundedCornerShape(6.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.book.authorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD8D8D8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimeLeft(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD8D8D8),
                    maxLines = 1
                )
            }
            Text("⋯", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ContinueListeningSkeletonCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF665A2E))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .width(236.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterStub(
                width = 64.dp,
                height = 84.dp,
                color = Color(0xFF1F1F23),
                shape = RoundedCornerShape(6.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PosterStub(width = 130.dp, height = 16.dp, color = Color(0xFF7D7449))
                PosterStub(width = 80.dp, height = 12.dp, color = Color(0xFF7D7449))
                PosterStub(width = 90.dp, height = 12.dp, color = Color(0xFF7D7449))
            }
        }
    }
}

@Composable
private fun BookPoster(
    book: BookSummary,
    width: Dp,
    height: Dp,
    fillMaxWidth: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    backgroundBlur: Dp = WideCoverBackgroundBlur
) {
    val posterModifier = if (fillMaxWidth) {
        Modifier
            .fillMaxWidth()
            .height(height)
    } else {
        Modifier
            .width(width)
            .height(height)
    }

    FramedCoverImage(
        coverUrl = book.coverUrl,
        contentDescription = book.title,
        modifier = posterModifier,
        shape = shape,
        contentScale = contentScale,
        backgroundBlur = backgroundBlur
    )
}

@Composable
private fun PosterStub(
    width: Dp,
    height: Dp,
    color: Color,
    fillMaxWidth: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val posterModifier = if (fillMaxWidth) {
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
    } else {
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
    }

    Box(modifier = posterModifier.background(color))
}

@Composable
private fun CenteredEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {}
) {
    IconButton(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BackTitle(
    title: String,
    onBackClick: (() -> Unit)?,
    onHomeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            CircleActionButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(BackTitleSpacing))
        }
        if (onHomeClick != null) {
            CircleActionButton(
                icon = Icons.Outlined.Home,
                contentDescription = "Home",
                onClick = onHomeClick
            )
            Spacer(modifier = Modifier.width(BackTitleSpacing))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailValueRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun List<BookSummary>.filterByStatus(filter: BooksStatusFilter): List<BookSummary> {
    return when (filter) {
        BooksStatusFilter.All -> this
        BooksStatusFilter.Finished -> filter { it.hasFinishedProgress() }
        BooksStatusFilter.InProgress -> filter { it.hasStartedProgress() && !it.hasFinishedProgress() }
        BooksStatusFilter.NotStarted -> filter { !it.hasStartedProgress() }
        BooksStatusFilter.NotFinished -> filter { !it.hasFinishedProgress() }
    }
}

private fun sortComparator(sortKey: BooksSortKey): Comparator<BookSummary> {
    return when (sortKey) {
        BooksSortKey.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        BooksSortKey.Author -> compareBy<BookSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.authorName.trim() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        BooksSortKey.PublicationDate -> compareByDescending<BookSummary> { parsePublishedYear(it.publishedYear) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        BooksSortKey.DateAdded -> compareByDescending<BookSummary> { it.addedAtMs ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        BooksSortKey.Duration -> compareByDescending<BookSummary> { it.durationSeconds ?: -1.0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
    }
}

private fun BookSummary.hasStartedProgress(): Boolean {
    val normalized = normalizedProgressPercent()
    return normalized != null && normalized > 0.001
}

private fun BookSummary.hasFinishedProgress(): Boolean {
    val normalized = normalizedProgressPercent()
    return isFinished || (normalized != null && normalized >= 0.995)
}

private fun BookSummary.normalizedProgressPercent(): Double? {
    progressPercent?.coerceIn(0.0, 1.0)?.let { return it }
    val duration = durationSeconds ?: return null
    if (duration <= 0.0) return null
    val current = currentTimeSeconds ?: return null
    return (current / duration).coerceIn(0.0, 1.0)
}

private fun parsePublishedYear(raw: String?): Int {
    raw ?: return Int.MIN_VALUE
    val fourDigits = raw.trim().take(4)
    return fourDigits.toIntOrNull() ?: Int.MIN_VALUE
}

private fun formatSeriesOrderLabel(seriesSequence: Double?): String? {
    val sequence = seriesSequence?.takeIf { it > 0.0 } ?: return null
    return if (sequence % 1.0 == 0.0) {
        sequence.toInt().toString()
    } else {
        sequence.toString()
    }
}

private fun extractSeriesOrderLabelFromText(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val bookMatch = Regex("(?i)\\bbook\\s*(\\d+(?:\\.\\d+)?)\\b").find(text)
    if (bookMatch != null) return bookMatch.groupValues.getOrNull(1)
    val hashMatch = Regex("#(\\d+(?:\\.\\d+)?)").find(text)
    return hashMatch?.groupValues?.getOrNull(1)
}

private fun resolveSeriesOrderLabel(seriesSequence: Double?, vararg textCandidates: String?): String? {
    formatSeriesOrderLabel(seriesSequence)?.let { return it }
    return textCandidates.firstNotNullOfOrNull { extractSeriesOrderLabelFromText(it) }
}

private fun splitAuthorNames(raw: String): List<String> {
    return raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(raw.trim()) }
}

private fun formatDurationHoursMinutes(durationSeconds: Double?): String {
    val totalSeconds = durationSeconds?.toLong() ?: return ""
    if (totalSeconds <= 0L) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${max(minutes, 1)}m"
    }
}

private fun formatTimeLeft(item: ContinueListeningItem): String {
    val duration = item.book.durationSeconds ?: return "In progress"
    val progress = item.progressPercent
    val current = item.currentTimeSeconds

    val remaining = when {
        progress != null -> duration * (1.0 - progress.coerceIn(0.0, 1.0))
        current != null -> duration - current
        else -> duration
    }.coerceAtLeast(0.0)

    return "${formatDurationHoursMinutes(remaining)} left".trim()
}

private fun formatFileSize(bytes: Long?): String {
    val value = bytes ?: return "Unknown"
    if (value <= 0L) return "Unknown"
    val mb = value / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format("%.1f GB", mb / 1024.0)
    } else {
        String.format("%.0f MB", mb)
    }
}

private fun formatSecondsAsHms(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

private fun normalizeSeriesGroupKey(value: String): String {
    return value
        .trim()
        .replace(Regex("\\s*#\\d+.*$"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
}
