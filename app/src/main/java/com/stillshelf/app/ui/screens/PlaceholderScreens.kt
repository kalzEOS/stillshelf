package com.stillshelf.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlin.math.cos
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.imageLoader
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.playback.controller.PlaybackOutputDevice
import com.stillshelf.app.playback.controller.SleepTimerMode
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.common.WideCoverBackgroundBlur
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.navigation.AuthRoute
import com.stillshelf.app.ui.navigation.BrowseRoute
import com.stillshelf.app.ui.navigation.MainRoute
import com.stillshelf.app.ui.navigation.MainTab
import com.stillshelf.app.ui.theme.AppThemeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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

enum class BooksSortKey(
    val label: String,
    val hint: String
) {
    Title(label = "Title", hint = "A - Z"),
    Author(label = "Author", hint = "A - Z"),
    PublicationDate(label = "Publication Date", hint = "Newest first"),
    DateAdded(label = "Date Added", hint = "Newest first"),
    Duration(label = "Duration", hint = "Longest first")
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
    customizeViewModel: CustomizeViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val menuUiState by menuViewModel.uiState.collectAsStateWithLifecycle()
    val customizeUiState by customizeViewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isLibraryMenuExpanded by remember { mutableStateOf(false) }
    var libraryMenuAnchorWidthPx by remember { mutableIntStateOf(0) }
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(menuViewModel) {
        menuViewModel.events.collect { event ->
            when (event) {
                HomeMenuEvent.NavigateToLibraryPicker -> onNavigateToRoute(MainRoute.LIBRARY_PICKER)
                is HomeMenuEvent.NavigateToLogin ->
                    onNavigateToRoute(AuthRoute.loginRoute(event.serverName, event.baseUrl))
            }
        }
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onHomeScreenVisibilityChanged(true)
                Lifecycle.Event.ON_STOP -> viewModel.onHomeScreenVisibilityChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHomeScreenVisibilityChanged(false)
        }
    }
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
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        val hasLibraries = menuUiState.libraries.isNotEmpty()
                        val libraryMenuWidth = with(LocalDensity.current) { libraryMenuAnchorWidthPx.toDp() }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .onGloballyPositioned { coordinates ->
                                    libraryMenuAnchorWidthPx = coordinates.size.width
                                }
                                .clickable(enabled = hasLibraries && !menuUiState.isSwitchingLibrary) {
                                    isLibraryMenuExpanded = true
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.libraryName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasLibraries) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isLibraryMenuExpanded) {
                                        Icons.Outlined.KeyboardArrowUp
                                    } else {
                                        Icons.Outlined.KeyboardArrowDown
                                    },
                                    contentDescription = "Switch library",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = isLibraryMenuExpanded && hasLibraries,
                            onDismissRequest = { isLibraryMenuExpanded = false },
                            modifier = if (libraryMenuAnchorWidthPx > 0) {
                                Modifier.width(libraryMenuWidth)
                            } else {
                                Modifier
                            }
                        ) {
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
                                        isLibraryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
                            if (menuUiState.servers.size > 1) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Servers",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                menuUiState.servers.forEach { server ->
                                    val isActive = menuUiState.activeServerId == server.id
                                    DropdownMenuItem(
                                        text = { Text(server.name) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Dns,
                                                contentDescription = null
                                            )
                                        },
                                        trailingIcon = {
                                            if (isActive) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Active server"
                                                )
                                            }
                                        },
                                        enabled = !menuUiState.isSwitchingServer,
                                        onClick = {
                                            menuViewModel.onServerSelected(server.id)
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
                val sectionContent: @Composable () -> Unit = {
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
                if (appearanceUiState.materialDesignEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            sectionContent()
                        }
                    }
                } else {
                    sectionContent()
                }
            }

            customizeUiState.personalizedSections.forEach { section ->
                if (customizeUiState.hiddenPersonalizedSectionIds.contains(section.id)) {
                    return@forEach
                }
                if (section.id == HomeSectionIds.LISTEN_AGAIN && uiState.listenAgain.isEmpty()) {
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
                                                isDownloaded = uiState.downloadedBookIds.contains(item.book.id),
                                                downloadProgressPercent = uiState.downloadProgressByBookId[item.book.id],
                                                onClick = { onOpenPlayer(item.book.id) },
                                                onGoToBook = { onOpenBook(item.book.id) },
                                                onAddToCollection = { addToListBookId = item.book.id },
                                                onMarkFinished = {
                                                    if (item.book.hasFinishedProgress()) {
                                                        viewModel.markAsUnfinished(item.book.id)
                                                    } else {
                                                        viewModel.markAsFinished(item.book.id)
                                                    }
                                                },
                                                onRemoveFromContinueListening = {
                                                    viewModel.removeFromContinueListening(item.book.id)
                                                },
                                                onToggleDownload = { viewModel.toggleDownload(item.book.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HomeSectionIds.LISTEN_AGAIN -> {
                        item { SectionTitle("Listen Again") }
                        item {
                            val books = uiState.listenAgain
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(books, key = { it.id }) { book ->
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clickable { onOpenBook(book.id) }
                                    ) {
                                        BookPoster(
                                            book = book,
                                            width = 92.dp,
                                            height = 118.dp,
                                            backgroundBlur = 64.dp,
                                            showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                        )
                                        Row(
                                            modifier = Modifier.width(92.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatDurationHoursMinutes(book.durationSeconds),
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
                                                        text = { Text("Add to Collection") },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            addToListBookId = book.id
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (book.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished"
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = if (book.hasFinishedProgress()) {
                                                                    Icons.Outlined.Refresh
                                                                } else {
                                                                    Icons.Outlined.CheckCircle
                                                                },
                                                                contentDescription = null
                                                            )
                                                        },
                                                        onClick = {
                                                            if (book.hasFinishedProgress()) {
                                                                viewModel.markAsUnfinished(book.id)
                                                            } else {
                                                                viewModel.markAsFinished(book.id)
                                                            }
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (uiState.downloadedBookIds.contains(book.id)) {
                                                                    "Remove Download"
                                                                } else {
                                                                    "Download"
                                                                }
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.Download, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            viewModel.toggleDownload(book.id)
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Remove from Listen Again") },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            viewModel.removeFromListenAgain(book.id)
                                                            menuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
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
                                            var menuExpanded by remember { mutableStateOf(false) }
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.clickable { onOpenBook(book.id) }
                                            ) {
                                                BookPoster(
                                                    book = book,
                                                    width = 92.dp,
                                                    height = 118.dp,
                                                    backgroundBlur = 64.dp,
                                                    showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                                    downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                                )
                                                Row(
                                                    modifier = Modifier.width(92.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = formatDurationHoursMinutes(book.durationSeconds),
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
                                                                text = { Text("Add to Collection") },
                                                                leadingIcon = {
                                                                    Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                                },
                                                                onClick = {
                                                                    addToListBookId = book.id
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        if (book.hasFinishedProgress()) {
                                                                            "Mark as Unfinished"
                                                                        } else {
                                                                            "Mark as Finished"
                                                                        }
                                                                    )
                                                                },
                                                                leadingIcon = {
                                                                    Icon(
                                                                        imageVector = if (book.hasFinishedProgress()) {
                                                                            Icons.Outlined.Refresh
                                                                        } else {
                                                                            Icons.Outlined.CheckCircle
                                                                        },
                                                                        contentDescription = null
                                                                    )
                                                                },
                                                                onClick = {
                                                                    if (book.hasFinishedProgress()) {
                                                                        viewModel.markAsUnfinished(book.id)
                                                                    } else {
                                                                        viewModel.markAsFinished(book.id)
                                                                    }
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Text(
                                                                        if (uiState.downloadedBookIds.contains(book.id)) {
                                                                            "Remove Download"
                                                                        } else {
                                                                            "Download"
                                                                        }
                                                                    )
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                                                },
                                                                onClick = {
                                                                    viewModel.toggleDownload(book.id)
                                                                    menuExpanded = false
                                                                }
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
                    }

                    HomeSectionIds.RECENT_SERIES -> {
                        item { SectionTitle("Recent Series") }
                        item {
                            val seriesItems = uiState.recentSeries
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
                                            isDownloaded = uiState.downloadedBookIds.contains(series.leadBook.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[series.leadBook.id],
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
                            val discoverBooks = uiState.discoverBooks
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
                                        var menuExpanded by remember { mutableStateOf(false) }
                                        Column(
                                            modifier = Modifier.clickable { onOpenBook(book.id) },
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            BookPoster(
                                                book = book,
                                                width = 92.dp,
                                                height = 118.dp,
                                                backgroundBlur = 64.dp,
                                                showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                                downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                            )
                                            Text(
                                                text = formatDurationHoursMinutes(book.durationSeconds),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                        text = { Text("Add to Collection") },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            addToListBookId = book.id
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (book.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished"
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = if (book.hasFinishedProgress()) {
                                                                    Icons.Outlined.Refresh
                                                                } else {
                                                                    Icons.Outlined.CheckCircle
                                                                },
                                                                contentDescription = null
                                                            )
                                                        },
                                                        onClick = {
                                                            if (book.hasFinishedProgress()) {
                                                                viewModel.markAsUnfinished(book.id)
                                                            } else {
                                                                viewModel.markAsFinished(book.id)
                                                            }
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                if (uiState.downloadedBookIds.contains(book.id)) {
                                                                    "Remove Download"
                                                                } else {
                                                                    "Download"
                                                                }
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.Download, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            viewModel.toggleDownload(book.id)
                                                            menuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
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
                                .distinctBy { it.trim().lowercase() }
                                .take(12)
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BrowsePlaceholderScreen(
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: BooksBrowseViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    var collectionPickerBookId by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(collectionPickerBookId) {
        if (!collectionPickerBookId.isNullOrBlank()) {
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
                        val isSelected = uiState.sortKey == option
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(option.label)
                                    if (isSelected) {
                                        Text(
                                            text = option.hint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            trailingIcon = {
                                if (isSelected) {
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
                                            onClick = { onBookClick(entry.book.id) },
                                            isDownloaded = uiState.downloadedBookIds.contains(entry.book.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[entry.book.id],
                                            onAddToCollection = { collectionPickerBookId = entry.book.id },
                                            onMarkFinished = {
                                                if (entry.book.hasFinishedProgress()) {
                                                    viewModel.markAsUnfinished(entry.book.id)
                                                } else {
                                                    viewModel.markAsFinished(entry.book.id)
                                                }
                                            },
                                            onToggleDownload = { viewModel.toggleDownload(entry.book.id) }
                                        )
                                    }

                                    is BooksGridEntry.SeriesStack -> {
                                        SeriesStackGridItem(
                                            entry = entry,
                                            onClick = { onSeriesClick(entry.seriesName) },
                                            isDownloaded = uiState.downloadedBookIds.contains(entry.leadBook.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[entry.leadBook.id],
                                            onAddToCollection = { collectionPickerBookId = entry.leadBook.id },
                                            onMarkFinished = {
                                                if (entry.leadBook.hasFinishedProgress()) {
                                                    viewModel.markAsUnfinished(entry.leadBook.id)
                                                } else {
                                                    viewModel.markAsFinished(entry.leadBook.id)
                                                }
                                            },
                                            onToggleDownload = { viewModel.toggleDownload(entry.leadBook.id) }
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
                                            onClick = { onBookClick(entry.book.id) },
                                            isDownloaded = uiState.downloadedBookIds.contains(entry.book.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[entry.book.id],
                                            onAddToCollection = { collectionPickerBookId = entry.book.id },
                                            onMarkFinished = {
                                                if (entry.book.hasFinishedProgress()) {
                                                    viewModel.markAsUnfinished(entry.book.id)
                                                } else {
                                                    viewModel.markAsFinished(entry.book.id)
                                                }
                                            },
                                            onToggleDownload = { viewModel.toggleDownload(entry.book.id) }
                                        )
                                    }

                                    is BooksGridEntry.SeriesStack -> {
                                        SeriesStackListItem(
                                            entry = entry,
                                            onClick = { onSeriesClick(entry.seriesName) },
                                            isDownloaded = uiState.downloadedBookIds.contains(entry.leadBook.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[entry.leadBook.id],
                                            onAddToCollection = { collectionPickerBookId = entry.leadBook.id },
                                            onMarkFinished = {
                                                if (entry.leadBook.hasFinishedProgress()) {
                                                    viewModel.markAsUnfinished(entry.leadBook.id)
                                                } else {
                                                    viewModel.markAsFinished(entry.leadBook.id)
                                                }
                                            },
                                            onToggleDownload = { viewModel.toggleDownload(entry.leadBook.id) }
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

    val targetBookId = collectionPickerBookId
    if (!targetBookId.isNullOrBlank()) {
        AddToListDialog(
            uiState = collectionPickerUiState,
            onDismiss = {
                collectionPickerBookId = null
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
private fun BookGridItem(
    book: BookSummary,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
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
                contentScale = ContentScale.Fit,
                showDownloadIndicator = isDownloaded,
                downloadProgressPercent = downloadProgressPercent
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
                    DropdownMenuItem(
                        text = { Text(downloadLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = {
                            onToggleDownload()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (book.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (book.hasFinishedProgress()) {
                                    Icons.Outlined.Refresh
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = {
                            onAddToCollection()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesStackGridItem(
    entry: BooksGridEntry.SeriesStack,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleDownload: () -> Unit
) {
    val book = entry.leadBook
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
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
        var menuExpanded by remember { mutableStateOf(false) }
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
                .width(frameWidth)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entry.count == 1) "1 book" else "${entry.count} books",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(22.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Series actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = {
                            onAddToCollection()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (book.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (book.hasFinishedProgress()) {
                                    Icons.Outlined.Refresh
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(downloadLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = {
                            onToggleDownload()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookListItem(
    book: BookSummary,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
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
            backgroundBlur = 44.dp,
            showDownloadIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
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
                DropdownMenuItem(
                    text = { Text(downloadLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = {
                        onToggleDownload()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (book.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (book.hasFinishedProgress()) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.CheckCircle
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onMarkFinished()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Collection") },
                    leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    onClick = {
                        onAddToCollection()
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SeriesStackListItem(
    entry: BooksGridEntry.SeriesStack,
    onClick: () -> Unit,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleDownload: () -> Unit
) {
    val lead = entry.leadBook
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
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
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "Series actions"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to Collection") },
                    leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    onClick = {
                        onAddToCollection()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (lead.hasFinishedProgress()) "Mark as Unfinished" else "Mark as Finished") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (lead.hasFinishedProgress()) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.CheckCircle
                            },
                            contentDescription = null
                        )
                    },
                    onClick = {
                        onMarkFinished()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(downloadLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = {
                        onToggleDownload()
                        menuExpanded = false
                    }
                )
            }
        }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BookmarksBrowseScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    onBookmarkClick: (bookId: String, startSeconds: Double?) -> Unit = { _, _ -> },
    viewModel: BookmarksBrowseViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && uiState.bookmarks.isEmpty(),
        onRefresh = viewModel::refresh
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshSilent()
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                viewModel.refreshSilent()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        BackTitle(
            title = "Bookmarks",
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
                uiState.bookmarks.isNotEmpty() -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(
                            items = uiState.bookmarks,
                            key = { item ->
                                "${item.book.id}:${item.bookmark.id}:${item.bookmark.createdAtMs ?: 0L}:${item.bookmark.timeSeconds ?: 0.0}"
                            }
                        ) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (appearanceUiState.materialDesignEnabled) {
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.68f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        onBookmarkClick(
                                            item.book.id,
                                            item.bookmark.timeSeconds
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookPoster(
                                    book = item.book,
                                    width = 42.dp,
                                    height = 56.dp,
                                    fillMaxWidth = false,
                                    shape = RoundedCornerShape(6.dp),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = item.bookmark.title?.ifBlank { null } ?: "Bookmark",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.book.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val bookmarkMeta = buildString {
                                        append(formatBookmarkDate(item.bookmark.createdAtMs))
                                        append(" ")
                                        append(formatBookmarkTime24(item.bookmark.createdAtMs))
                                        item.bookmark.timeSeconds?.let {
                                            append(" • ")
                                            append(formatSecondsAsHms(it))
                                        }
                                    }
                                    Text(
                                        text = bookmarkMeta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
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
                }

                uiState.isLoading -> {
                    Text(
                        text = "Loading bookmarks...",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unable to load bookmarks.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Bookmarks you add will appear here.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading && uiState.bookmarks.isEmpty(),
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CollectionsBrowseScreen(
    onCollectionClick: (com.stillshelf.app.core.model.NamedEntitySummary) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: CollectionsBrowseViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var manualRefreshInProgress by rememberSaveable { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && manualRefreshInProgress,
        onRefresh = {
            manualRefreshInProgress = true
            viewModel.refreshLibrary()
        }
    )
    var renameTarget by remember { mutableStateOf<com.stillshelf.app.core.model.NamedEntitySummary?>(null) }
    var deleteTarget by remember { mutableStateOf<com.stillshelf.app.core.model.NamedEntitySummary?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) manualRefreshInProgress = false
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh()
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                viewModel.refresh()
            }
        }
    }

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
                title = "Collections",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.entities.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        gridItems(uiState.entities, key = { it.id }) { collection ->
                            var menuExpanded by remember { mutableStateOf(false) }
                            val coverStack = uiState.coverStackByCollectionId[collection.id].orEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCollectionClick(collection) }
                                    .padding(bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(186.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    if (coverStack.isNotEmpty()) {
                                        val frontCover = coverStack.getOrNull(0)
                                        val backCoverLeft = coverStack.getOrNull(1)
                                        val backCoverRight = coverStack.getOrNull(2)
                                        val coverShape = RoundedCornerShape(8.dp)
                                        val layerWidth = 102.dp
                                        val layerHeight = 154.dp

                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            backCoverRight?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .offset(x = 14.dp, y = (-8).dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .graphicsLayer(alpha = 0.7f),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                            backCoverLeft?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .offset(x = (-14).dp, y = (-8).dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .graphicsLayer(alpha = 0.7f),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                            frontCover?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = collection.name,
                                                    modifier = Modifier
                                                        .offset(y = 4.dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .shadow(
                                                            elevation = 3.dp,
                                                            shape = coverShape,
                                                            clip = false
                                                        ),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CollectionsBookmark,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                }

                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = collection.subtitle.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 34.dp, height = 24.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                                .clickable { menuExpanded = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreHoriz,
                                                contentDescription = "Collection actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    menuExpanded = false
                                                    nameInput = collection.name
                                                    renameTarget = collection
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    menuExpanded = false
                                                    deleteTarget = collection
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (uiState.errorMessage == null) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = uiState.errorMessage ?: "No collections yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.errorMessage == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
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

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Collection") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Collection name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget = null
                        viewModel.renameCollection(target.id, nameInput)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Collection") },
            text = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteCollection(target.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NarratorsBrowseScreen(viewModel: NarratorsBrowseViewModel = hiltViewModel()) {
    NarratorsBrowseScreen(onNarratorClick = {}, onBackClick = null, viewModel = viewModel)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PlaylistsBrowseScreen(
    onPlaylistClick: (com.stillshelf.app.core.model.NamedEntitySummary) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: PlaylistsBrowseViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var manualRefreshInProgress by rememberSaveable { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading && manualRefreshInProgress,
        onRefresh = {
            manualRefreshInProgress = true
            viewModel.refreshLibrary()
        }
    )
    var createDialogVisible by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<com.stillshelf.app.core.model.NamedEntitySummary?>(null) }
    var deleteTarget by remember { mutableStateOf<com.stillshelf.app.core.model.NamedEntitySummary?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) manualRefreshInProgress = false
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh()
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                viewModel.refresh()
            }
        }
    }

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
                title = "Playlists",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Create playlist",
                onClick = {
                    nameInput = ""
                    createDialogVisible = true
                }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.entities.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        gridItems(uiState.entities, key = { it.id }) { playlist ->
                            var menuExpanded by remember { mutableStateOf(false) }
                            val coverStack = uiState.coverStackByCollectionId[playlist.id].orEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(186.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    if (coverStack.isNotEmpty()) {
                                        val frontCover = coverStack.getOrNull(0)
                                        val backCoverLeft = coverStack.getOrNull(1)
                                        val backCoverRight = coverStack.getOrNull(2)
                                        val coverShape = RoundedCornerShape(8.dp)
                                        val layerWidth = 102.dp
                                        val layerHeight = 154.dp

                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            backCoverRight?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .offset(x = 14.dp, y = (-8).dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .graphicsLayer(alpha = 0.7f),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                            backCoverLeft?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .offset(x = (-14).dp, y = (-8).dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .graphicsLayer(alpha = 0.7f),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                            frontCover?.let { coverUrl ->
                                                FramedCoverImage(
                                                    coverUrl = coverUrl,
                                                    contentDescription = playlist.name,
                                                    modifier = Modifier
                                                        .offset(y = 4.dp)
                                                        .width(layerWidth)
                                                        .height(layerHeight)
                                                        .shadow(
                                                            elevation = 3.dp,
                                                            shape = coverShape,
                                                            clip = false
                                                        ),
                                                    shape = coverShape,
                                                    contentScale = ContentScale.Crop,
                                                    backgroundBlur = WideCoverBackgroundBlur
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = playlist.subtitle.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 34.dp, height = 24.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                                .clickable { menuExpanded = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreHoriz,
                                                contentDescription = "Playlist actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    menuExpanded = false
                                                    nameInput = playlist.name
                                                    renameTarget = playlist
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    menuExpanded = false
                                                    deleteTarget = playlist
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.errorMessage == null) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Text(
                            text = uiState.errorMessage ?: "No playlists yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.errorMessage == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Button(onClick = {
                            nameInput = ""
                            createDialogVisible = true
                        }) {
                            Text("Create Playlist")
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

    if (createDialogVisible) {
        AlertDialog(
            onDismissRequest = { createDialogVisible = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createDialogVisible = false
                        viewModel.createPlaylist(nameInput)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { createDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget = null
                        viewModel.renamePlaylist(target.id, nameInput)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deletePlaylist(target.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                .clickable { onNarratorClick(entity.name) }
                                .padding(horizontal = 10.dp, vertical = 14.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackTitle(
                title = "Series",
                onBackClick = onBackClick,
                onHomeClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = if (uiState.gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = if (uiState.gridMode) "List mode" else "Grid mode",
                onClick = { viewModel.setGridMode(!uiState.gridMode) }
            )
        }
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
                if (uiState.gridMode) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(uiState.series, key = { it.id }) { series ->
                            val inferredCount = series.subtitle
                                .orEmpty()
                                .trim()
                                .substringBefore(" ")
                                .toIntOrNull()
                            val layerCount = inferredCount?.coerceIn(2, 3) ?: 3
                            val frameWidth = 92.dp
                            val frameHeight = 102.dp
                            val stackStepX = 5.dp
                            val stackStepY = 10.dp
                            val totalShiftX = stackStepX * (layerCount - 1)
                            val totalShiftY = stackStepY * (layerCount - 1)
                            val cardWidth = frameWidth - totalShiftX - 3.dp
                            val cardHeight = frameHeight - totalShiftY - 3.dp
                            val baseShiftX = (-4).dp
                            val baseShiftY = 1.dp
                            val layerShape = RoundedCornerShape(8.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                    .clickable { onSeriesClick(series.name) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(frameWidth)
                                        .height(frameHeight)
                                        .clipToBounds()
                                ) {
                                    repeat(layerCount) { layer ->
                                        val xOffset = baseShiftX + (stackStepX * layer)
                                        val yOffset = baseShiftY + (stackStepY * layer)
                                        val layerShadow = if (layer == layerCount - 1) 1.2.dp else 3.4.dp
                                        FramedCoverImage(
                                            coverUrl = series.coverUrl,
                                            contentDescription = series.name,
                                            modifier = Modifier
                                                .offset(x = xOffset, y = yOffset)
                                                .width(cardWidth)
                                                .height(cardHeight)
                                                .shadow(elevation = layerShadow, shape = layerShape, clip = false),
                                            shape = layerShape,
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
                                        text = series.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = series.subtitle.orEmpty(),
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            if (normalized.isBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
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
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
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
                        modifier = Modifier.align(Alignment.Center),
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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 6.dp),
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
    DownloadsPlaceholderScreen(onBackClick = null, onHomeClick = null, onBookClick = {})
}

@Composable
fun DownloadsPlaceholderScreen(
    onBookClick: (String) -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadItemById = remember(uiState.downloadItems) {
        uiState.downloadItems.associateBy { it.bookId }
    }
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }

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
                icon = if (uiState.listMode) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                contentDescription = "Downloaded layout",
                onClick = { viewModel.setListMode(!uiState.listMode) }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Loading downloaded books...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            uiState.books.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
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
                }
            }

            uiState.listMode -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(uiState.books, key = { it.id }) { book ->
                        val downloadItem = downloadItemById[book.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookClick(book.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BookPoster(
                                book = book,
                                width = 58.dp,
                                height = 80.dp,
                                shape = RoundedCornerShape(8.dp),
                                contentScale = ContentScale.Fit,
                                backgroundBlur = WideCoverBackgroundBlur,
                                showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                downloadProgressPercent = downloadItem?.progressPercent
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
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
                                    text = formatDurationHoursMinutes(book.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                downloadItem?.let { item ->
                                    if (item.status == com.stillshelf.app.downloads.manager.DownloadStatus.Downloading ||
                                        item.status == com.stillshelf.app.downloads.manager.DownloadStatus.Queued
                                    ) {
                                        Text(
                                            text = "Downloading ${item.progressPercent.coerceIn(0, 100)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "More"
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Go to book") },
                                        onClick = {
                                            menuExpanded = false
                                            onBookClick(book.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove Download") },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.removeDownload(book.id)
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    gridItems(uiState.books, key = { it.id }) { book ->
                        val downloadItem = downloadItemById[book.id]
                        var menuExpanded by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.clickable { onBookClick(book.id) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BookPoster(
                                book = book,
                                width = StandardGridCoverWidth,
                                height = StandardGridCoverHeight,
                                shape = RoundedCornerShape(10.dp),
                                contentScale = ContentScale.Fit,
                                backgroundBlur = WideCoverBackgroundBlur,
                                showDownloadIndicator = true,
                                downloadProgressPercent = downloadItem?.progressPercent
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatDurationHoursMinutes(book.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 34.dp, height = 24.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                            .clickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = "Book actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Go to book") },
                                            onClick = {
                                                menuExpanded = false
                                                onBookClick(book.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove Download") },
                                            onClick = {
                                                menuExpanded = false
                                                viewModel.removeDownload(book.id)
                                            }
                                        )
                                    }
                                }
                            }
                            downloadItem?.let { item ->
                                if (item.status == com.stillshelf.app.downloads.manager.DownloadStatus.Downloading ||
                                    item.status == com.stillshelf.app.downloads.manager.DownloadStatus.Queued
                                ) {
                                    Text(
                                        text = "Downloading ${item.progressPercent.coerceIn(0, 100)}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
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
fun SettingsPlaceholderScreen(
    onManageServers: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var skipForwardDialogVisible by remember { mutableStateOf(false) }
    var skipBackwardDialogVisible by remember { mutableStateOf(false) }
    val skipSecondChoices = remember { listOf(10, 15, 30, 45, 60) }
    val sectionCardColor = if (uiState.materialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val sectionCardBorder = if (uiState.materialDesignEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackTitle(
            title = "Settings",
            onBackClick = onBackClick
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(20.dp),
            border = sectionCardBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            }
        }

        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
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
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
        ) {
            SettingsRow(
                title = "Follow System Theme",
                selected = uiState.themeMode == AppThemeMode.FollowSystem,
                showChevronWhenUnselected = false,
                onClick = { viewModel.setThemeMode(AppThemeMode.FollowSystem) }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Light Theme",
                selected = uiState.themeMode == AppThemeMode.Light,
                showChevronWhenUnselected = false,
                onClick = { viewModel.setThemeMode(AppThemeMode.Light) }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Dark Theme",
                selected = uiState.themeMode == AppThemeMode.Dark,
                showChevronWhenUnselected = false,
                onClick = { viewModel.setThemeMode(AppThemeMode.Dark) }
            )
        }

        Text(
            text = "SKIP BUTTONS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
        ) {
            SettingsRow(
                title = "Skip Forward",
                value = "${uiState.skipForwardSeconds} seconds",
                onClick = { skipForwardDialogVisible = true }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Skip Backward",
                value = "${uiState.skipBackwardSeconds} seconds",
                onClick = { skipBackwardDialogVisible = true }
            )
        }

        Text(
            text = "LOCK SCREEN CONTROLS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
        ) {
            SettingsRow(
                title = "Next/Previous",
                selected = uiState.lockScreenControlMode == "next",
                showChevronWhenUnselected = false,
                onClick = {
                    viewModel.setLockScreenControlMode("next")
                    infoMessage = "Lock screen controls set to Next/Previous."
                }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Skip Forward/Back",
                selected = uiState.lockScreenControlMode == "skip",
                showChevronWhenUnselected = false,
                onClick = {
                    viewModel.setLockScreenControlMode("skip")
                    infoMessage = "Lock screen controls set to Skip Forward/Back."
                }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
        ) {
            SettingsRow(
                title = "About",
                onClick = { infoMessage = "StillShelf alpha build." }
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
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

        if (skipForwardDialogVisible) {
            AlertDialog(
                onDismissRequest = { skipForwardDialogVisible = false },
                title = { Text("Skip Forward") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        skipSecondChoices.forEach { seconds ->
                            SettingsRow(
                                title = "$seconds seconds",
                                selected = uiState.skipForwardSeconds == seconds,
                                showChevronWhenUnselected = false,
                                onClick = {
                                    viewModel.setSkipForwardSeconds(seconds)
                                    skipForwardDialogVisible = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { skipForwardDialogVisible = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (skipBackwardDialogVisible) {
            AlertDialog(
                onDismissRequest = { skipBackwardDialogVisible = false },
                title = { Text("Skip Backward") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        skipSecondChoices.forEach { seconds ->
                            SettingsRow(
                                title = "$seconds seconds",
                                selected = uiState.skipBackwardSeconds == seconds,
                                showChevronWhenUnselected = false,
                                onClick = {
                                    viewModel.setSkipBackwardSeconds(seconds)
                                    skipBackwardDialogVisible = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { skipBackwardDialogVisible = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    selected: Boolean = false,
    showChevronWhenUnselected: Boolean = true,
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
        } else if (showChevronWhenUnselected) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListDialog(
    uiState: CollectionPickerUiState,
    onDismiss: () -> Unit,
    onAddToExistingCollection: (String) -> Unit,
    onCreateCollection: (String) -> Unit,
    onAddToExistingPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCollectionInput by rememberSaveable { mutableStateOf(false) }
    var showPlaylistInput by rememberSaveable { mutableStateOf(false) }
    var newCollectionName by rememberSaveable { mutableStateOf("") }
    var newPlaylistName by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitNewCollection: () -> Unit = {
        val name = newCollectionName.trim()
        if (name.isNotBlank() && !uiState.isSubmitting) {
            onCreateCollection(name)
            newCollectionName = ""
            showCollectionInput = false
        }
    }
    val submitNewPlaylist: () -> Unit = {
        val name = newPlaylistName.trim()
        if (name.isNotBlank() && !uiState.isSubmitting) {
            onCreatePlaylist(name)
            newPlaylistName = ""
            showPlaylistInput = false
        }
    }

    val dismissSheet: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    BackHandler(enabled = true) {
        dismissSheet()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(100))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                )
            }
            Text(
                text = "Add to Collection",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            AddToListInlineSection(
                title = "Collections",
                newLabel = "New Collection",
                newName = newCollectionName,
                onNewNameChange = { newCollectionName = it },
                showInput = showCollectionInput,
                entities = uiState.collections,
                icon = Icons.Outlined.CollectionsBookmark,
                isLoading = uiState.isLoading,
                isSubmitting = uiState.isSubmitting,
                onNewClick = {
                    showCollectionInput = true
                    showPlaylistInput = false
                },
                onSubmitNew = submitNewCollection,
                onEntityClick = onAddToExistingCollection
            )
            AddToListInlineSection(
                title = "Playlists",
                newLabel = "New Playlist",
                newName = newPlaylistName,
                onNewNameChange = { newPlaylistName = it },
                showInput = showPlaylistInput,
                entities = uiState.playlists,
                icon = Icons.Outlined.MusicNote,
                isLoading = uiState.isLoading,
                isSubmitting = uiState.isSubmitting,
                onNewClick = {
                    showPlaylistInput = true
                    showCollectionInput = false
                },
                onSubmitNew = submitNewPlaylist,
                onEntityClick = onAddToExistingPlaylist
            )
            uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AddToListInlineSection(
    title: String,
    newLabel: String,
    newName: String,
    onNewNameChange: (String) -> Unit,
    showInput: Boolean,
    entities: List<com.stillshelf.app.core.model.NamedEntitySummary>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    isSubmitting: Boolean,
    onNewClick: () -> Unit,
    onSubmitNew: () -> Unit,
    onEntityClick: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(showInput) {
        if (showInput) {
            focusRequester.requestFocus()
        }
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!showInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting, onClick = onNewClick)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = newLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = newName,
                        onValueChange = onNewNameChange,
                        singleLine = true,
                        enabled = !isSubmitting,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { onSubmitNew() }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (newName.isBlank()) {
                                Text(
                                    text = newLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
            }
            if (entities.isNotEmpty() || (isLoading && entities.isEmpty())) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            }

            when {
                entities.isNotEmpty() -> {
                    entities.forEachIndexed { index, entity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitting) { onEntityClick(entity.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = entity.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            entity.subtitle
                                ?.trim()
                                ?.substringBefore(" ")
                                ?.toIntOrNull()
                                ?.let { count ->
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                        }
                        if (index < entities.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                }

                isLoading -> {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                }
            }
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
    var expandedServerMenuId by remember { mutableStateOf<String?>(null) }
    var editingServer by remember { mutableStateOf<com.stillshelf.app.core.model.Server?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingUrl by remember { mutableStateOf("") }
    var editingError by remember { mutableStateOf<String?>(null) }
    var deletingServer by remember { mutableStateOf<com.stillshelf.app.core.model.Server?>(null) }

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
                        Box {
                            IconButton(onClick = { expandedServerMenuId = server.id }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Server actions"
                                )
                            }
                            DropdownMenu(
                                expanded = expandedServerMenuId == server.id,
                                onDismissRequest = { expandedServerMenuId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        expandedServerMenuId = null
                                        editingServer = server
                                        editingName = server.name
                                        editingUrl = server.baseUrl
                                        editingError = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        expandedServerMenuId = null
                                        deletingServer = server
                                    }
                                )
                            }
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

        if (editingServer != null) {
            AlertDialog(
                onDismissRequest = {
                    editingServer = null
                    editingError = null
                },
                title = { Text("Edit Server") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = editingName,
                            onValueChange = {
                                editingName = it
                                editingError = null
                            },
                            label = { Text("Server Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editingUrl,
                            onValueChange = {
                                editingUrl = it.replace(" ", "")
                                editingError = null
                            },
                            label = { Text("Base URL") },
                            singleLine = true
                        )
                        editingError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val server = editingServer ?: return@TextButton
                            if (editingName.trim().length < 2) {
                                editingError = "Server name must be at least 2 characters."
                                return@TextButton
                            }
                            val trimmedUrl = editingUrl.trim()
                            val validUrl = trimmedUrl.startsWith("https://", ignoreCase = true) ||
                                trimmedUrl.startsWith("http://", ignoreCase = true)
                            if (!validUrl) {
                                editingError = "Base URL must start with http:// or https://"
                                return@TextButton
                            }
                            viewModel.updateServer(
                                serverId = server.id,
                                name = editingName.trim(),
                                baseUrl = trimmedUrl
                            )
                            editingServer = null
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            editingServer = null
                            editingError = null
                        }
                    ) { Text("Cancel") }
                }
            )
        }

        if (deletingServer != null) {
            val server = deletingServer
            AlertDialog(
                onDismissRequest = { deletingServer = null },
                title = { Text("Delete Server?") },
                text = { Text("Remove ${server?.name.orEmpty()} from this device?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = deletingServer ?: return@TextButton
                            deletingServer = null
                            viewModel.deleteServer(target.id)
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deletingServer = null }) { Text("Cancel") }
                }
            )
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
    viewModel: BookDetailViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val playbackUiState by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    var actionsExpanded by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var aboutExpanded by remember { mutableStateOf(false) }
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarkMenuId by remember { mutableStateOf<String?>(null) }
    var editingDetailBookmark by remember { mutableStateOf<BookBookmark?>(null) }
    var editingDetailBookmarkTitle by rememberSaveable { mutableStateOf("") }
    var deletingDetailBookmark by remember { mutableStateOf<BookBookmark?>(null) }

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
    val detailBook = uiState.detail?.book
    val isPlayingDetailBookNow = detailBook != null && playbackUiState.book?.id == detailBook.id
    val detailDurationSeconds = when {
        detailBook?.durationSeconds != null && detailBook.durationSeconds > 0.0 -> detailBook.durationSeconds
        isPlayingDetailBookNow && playbackUiState.durationMs > 0L -> playbackUiState.durationMs / 1000.0
        else -> null
    }
    val detailProgressPercent = (uiState.progressPercent ?: 0.0).coerceIn(0.0, 1.0)
    val detailCurrentSeconds = if (isPlayingDetailBookNow) {
        playbackUiState.positionMs.coerceAtLeast(0L) / 1000.0
    } else {
        uiState.currentTimeSeconds?.coerceAtLeast(0.0)
    }
    val detailProgressFromTime = if (
        detailDurationSeconds != null &&
        detailDurationSeconds > 0.0 &&
        detailCurrentSeconds != null
    ) {
        (detailCurrentSeconds / detailDurationSeconds).coerceIn(0.0, 1.0)
    } else {
        null
    }
    val resolvedDetailProgress = max(detailProgressPercent, detailProgressFromTime ?: 0.0)
    val finishedFromDetailProgress = resolvedDetailProgress >= 0.995
    val effectiveDetailFinished = when {
        isPlayingDetailBookNow && resolvedDetailProgress < 0.995 && (detailCurrentSeconds ?: 0.0) > 0.5 -> false
        else -> (detailBook?.isFinished == true) || finishedFromDetailProgress
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
                            text = {
                                val hasActiveDownload = (uiState.downloadProgressPercent ?: -1) in 0..99
                                Text(
                                    if (
                                        uiState.detail?.book?.id?.let(uiState.downloadedBookIds::contains) == true ||
                                        hasActiveDownload
                                    ) {
                                        "Remove Download"
                                    } else {
                                        "Download"
                                    }
                                )
                            },
                            onClick = {
                                viewModel.toggleDownload()
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Skip Intro & Outro (coming soon)") },
                            onClick = {
                                infoMessage = "Skip Intro/Outro is planned. It is not active yet."
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (effectiveDetailFinished) {
                                        "Mark as Unfinished"
                                    } else {
                                        "Mark as Finished"
                                    }
                                )
                            },
                            onClick = {
                                if (effectiveDetailFinished) {
                                    viewModel.markAsUnfinished()
                                } else {
                                    viewModel.markAsFinished()
                                }
                                actionsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Collection") },
                            onClick = {
                                addToListBookId = uiState.detail?.book?.id
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
                val isPlayingThisBook = playbackUiState.book?.id == book.id
                val livePlaybackSeconds = if (isPlayingThisBook) {
                    (playbackUiState.positionMs.coerceAtLeast(0L) / 1000.0)
                } else {
                    0.0
                }
                val serverPlaybackSeconds = uiState.currentTimeSeconds?.coerceAtLeast(0.0) ?: 0.0
                val effectiveBookFinished = effectiveDetailFinished
                val hasProgress = livePlaybackSeconds > 0.5 ||
                    serverPlaybackSeconds > 0.5 ||
                    (uiState.progressPercent ?: 0.0) > 0.001
                val listenLabel = if (effectiveBookFinished) {
                    "Start Listening"
                } else if (hasProgress) {
                    "Continue Listening"
                } else {
                    "Start Listening"
                }
                val listenProgressFraction = run {
                    val duration = when {
                        book.durationSeconds != null && book.durationSeconds > 0.0 -> book.durationSeconds
                        playbackUiState.book?.id == book.id && playbackUiState.durationMs > 0L -> {
                            playbackUiState.durationMs / 1000.0
                        }
                        else -> null
                    }
                    val resolved = when {
                        duration != null && duration > 0.0 && livePlaybackSeconds > 0.0 -> {
                            (livePlaybackSeconds / duration).coerceIn(0.0, 1.0)
                        }
                        duration != null && duration > 0.0 && serverPlaybackSeconds > 0.0 -> {
                            (serverPlaybackSeconds / duration).coerceIn(0.0, 1.0)
                        }
                        else -> (uiState.progressPercent ?: 0.0).coerceIn(0.0, 1.0)
                    }
                    resolved.toFloat().coerceIn(0f, 1f)
                }
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
                                contentScale = ContentScale.Fit,
                                showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                downloadProgressPercent = uiState.downloadProgressPercent
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
                    if (hasProgress) {
                        BookListenProgressButton(
                            text = listenLabel,
                            progress = listenProgressFraction,
                            onClick = { onStartListening(book.id) }
                        )
                    } else {
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
                }
                item {
                    Row(
                        modifier = if (appearanceUiState.materialDesignEnabled) {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(4.dp)
                        } else {
                            Modifier
                        },
                        horizontalArrangement = Arrangement.spacedBy(if (appearanceUiState.materialDesignEnabled) 6.dp else 16.dp)
                    ) {
                        DetailTabChip(
                            title = "About",
                            selected = uiState.selectedTab == "About",
                            materialDesignEnabled = appearanceUiState.materialDesignEnabled,
                            modifier = if (appearanceUiState.materialDesignEnabled) Modifier.weight(1f) else Modifier,
                            onClick = { viewModel.setSelectedTab("About") }
                        )
                        DetailTabChip(
                            title = "Chapters",
                            selected = uiState.selectedTab == "Chapters",
                            materialDesignEnabled = appearanceUiState.materialDesignEnabled,
                            modifier = if (appearanceUiState.materialDesignEnabled) Modifier.weight(1f) else Modifier,
                            onClick = { viewModel.setSelectedTab("Chapters") }
                        )
                        DetailTabChip(
                            title = "Bookmarks",
                            selected = uiState.selectedTab == "Bookmarks",
                            materialDesignEnabled = appearanceUiState.materialDesignEnabled,
                            modifier = if (appearanceUiState.materialDesignEnabled) Modifier.weight(1f) else Modifier,
                            onClick = { viewModel.setSelectedTab("Bookmarks") }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }

                when (uiState.selectedTab) {
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
                                            } else if (appearanceUiState.materialDesignEnabled) {
                                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
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
                                            text = formatChapterDurationForRow(
                                                chapter = chapter,
                                                index = index,
                                                chapters = detail.chapters,
                                                totalDurationSeconds = book.durationSeconds
                                            ),
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
                            items(
                                items = detail.bookmarks,
                                key = {
                                    "${it.id}:${it.createdAtMs ?: 0L}:${it.timeSeconds ?: 0.0}:${it.title.orEmpty()}"
                                }
                            ) { bookmark ->
                                val bookmarkSeconds = bookmark.timeSeconds
                                val isActiveBookmark = bookmarkSeconds != null &&
                                    chapterPositionSeconds != null &&
                                    kotlin.math.abs(bookmarkSeconds - chapterPositionSeconds) < 1.0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isActiveBookmark) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            } else if (appearanceUiState.materialDesignEnabled) {
                                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.68f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = bookmarkSeconds != null) {
                                            viewModel.playBookmark(bookmark)
                                            onStartListening(book.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = bookmark.title?.ifBlank { null } ?: "Bookmark",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = bookmark.timeSeconds?.let { formatSecondsAsHms(it) } ?: "--:--",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isActiveBookmark) {
                                            ChapterPlaybackIndicator(
                                                isPlaying = playbackUiState.isPlaying,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box {
                                            IconButton(
                                                onClick = {
                                                    bookmarkMenuId = bookmark.id
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.MoreHoriz,
                                                    contentDescription = "Bookmark actions",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = bookmarkMenuId == bookmark.id,
                                                onDismissRequest = { bookmarkMenuId = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Edit title") },
                                                    onClick = {
                                                        bookmarkMenuId = null
                                                        editingDetailBookmark = bookmark
                                                        editingDetailBookmarkTitle = bookmark.title.orEmpty()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete bookmark") },
                                                    onClick = {
                                                        bookmarkMenuId = null
                                                        deletingDetailBookmark = bookmark
                                                    }
                                                )
                                            }
                                        }
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

    editingDetailBookmark?.let { targetBookmark ->
        AlertDialog(
            onDismissRequest = { editingDetailBookmark = null },
            title = { Text("Edit bookmark title") },
            text = {
                OutlinedTextField(
                    value = editingDetailBookmarkTitle,
                    onValueChange = { editingDetailBookmarkTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editBookmark(targetBookmark, editingDetailBookmarkTitle)
                        editingDetailBookmark = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDetailBookmark = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingDetailBookmark?.let { targetBookmark ->
        AlertDialog(
            onDismissRequest = { deletingDetailBookmark = null },
            title = { Text("Delete bookmark") },
            text = { Text("Remove this bookmark?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBookmark(targetBookmark)
                        deletingDetailBookmark = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingDetailBookmark = null }) {
                    Text("Cancel")
                }
            }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerPlaceholderScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    onGoToBook: ((String) -> Unit)? = null,
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val playbackUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewItem by viewModel.previewItem.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val controlPrefs by viewModel.controlPrefs.collectAsStateWithLifecycle()
    val downloadedBookIds by viewModel.downloadedBookIds.collectAsStateWithLifecycle()
    val playerDownloadProgressPercent by viewModel.downloadProgressPercent.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val book = playbackUiState.book ?: previewItem?.book
    val bookId = book?.id
    val isBookDownloaded = bookId != null && downloadedBookIds.contains(bookId)
    val activeDownloadProgressPercent = playerDownloadProgressPercent?.coerceIn(0, 100)
    val hasActivePlayerDownload = activeDownloadProgressPercent != null && activeDownloadProgressPercent in 0..99
    val downloadToolLabel = if (isBookDownloaded || hasActivePlayerDownload) "Remove" else "Download"
    val downloadToolValue = if (hasActivePlayerDownload) {
        "${activeDownloadProgressPercent}%"
    } else {
        null
    }
    val durationSeconds = when {
        book?.durationSeconds != null && book.durationSeconds > 0.0 -> book.durationSeconds
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
    val effectivePlayerFinished = if (book != null) {
        val finishedFromPlayback = progress >= 0.995f
        val finishedFromBook = book.hasFinishedProgress()
        finishedFromPlayback || finishedFromBook
    } else {
        false
    }
    val activeChapterIndex = remember(chapters, positionSeconds) {
        findActiveChapterIndex(chapters, positionSeconds)
    }
    val chapterBounds = remember(chapters, activeChapterIndex, durationSeconds) {
        chapterWindow(
            chapters = chapters,
            index = activeChapterIndex,
            totalDurationSeconds = durationSeconds
        )
    }
    val chapterDurationSeconds = chapterBounds?.durationSeconds ?: durationSeconds
    val chapterStartSeconds = chapterBounds?.startSeconds ?: 0.0
    val chapterLocalSeconds = if (chapterDurationSeconds > 0.0) {
        (positionSeconds - chapterStartSeconds).coerceIn(0.0, chapterDurationSeconds)
    } else {
        positionSeconds
    }
    val chapterProgress = if (chapterDurationSeconds > 0.0) {
        (chapterLocalSeconds / chapterDurationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        progress
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbingProgress by remember { mutableStateOf(chapterProgress) }
    LaunchedEffect(chapterProgress, isScrubbing) {
        if (!isScrubbing) {
            scrubbingProgress = chapterProgress
        }
    }
    val effectiveProgress = if (isScrubbing) scrubbingProgress else chapterProgress
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
    val dismissPlayer: () -> Unit = {
        viewModel.onDismissPlayer()
        onBackClick?.invoke()
    }
    val dragDismissDistancePx = with(LocalDensity.current) { 120.dp.toPx() }
    val dragDismissVelocityPxPerSec = with(LocalDensity.current) { 1100.dp.toPx() }
    var playerDragOffsetPx by remember { mutableStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    var settleDragJob by remember { mutableStateOf<Job?>(null) }
    val verticalDragState = rememberDraggableState { delta ->
        settleDragJob?.cancel()
        playerDragOffsetPx = (playerDragOffsetPx + delta).coerceAtLeast(0f)
    }
    var bottomMenuExpanded by remember { mutableStateOf(false) }
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarkFeedbackActive by remember { mutableStateOf(false) }
    val bookmarkIconScale by animateFloatAsState(
        targetValue = if (bookmarkFeedbackActive) 1.18f else 1f,
        animationSpec = tween(durationMillis = 130),
        label = "bookmark-icon-feedback"
    )
    val chapterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showChapterSheet by rememberSaveable { mutableStateOf(false) }
    var chapterSheetTab by rememberSaveable { mutableStateOf(PlayerSheetTab.Chapters) }
    val outputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    val timerExpiredSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTimerSheet by rememberSaveable { mutableStateOf(false) }
    var timerSheetSessionKey by rememberSaveable { mutableIntStateOf(0) }
    val speedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }
    val speedLabel = formatPlaybackSpeedShort(playbackUiState.playbackSpeed)
    val timerRemainingMs = playbackUiState.sleepTimerRemainingMs ?: 0L
    val timerTotalMs = playbackUiState.sleepTimerTotalMs ?: timerRemainingMs
    val timerIsActive = playbackUiState.sleepTimerMode != SleepTimerMode.Off && timerRemainingMs > 0L
    val timerLabel = if (timerIsActive) formatTimerChipLabel(timerRemainingMs) else null
    val selectedOutput = playbackUiState.outputDevices.firstOrNull { it.id == playbackUiState.selectedOutputDeviceId }
    val outputLabel = selectedOutput?.typeLabel ?: "Output"
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val effectiveHeightDp = (configuration.screenHeightDp.toFloat() / density.fontScale).coerceAtLeast(520f)
    val playerHorizontalPadding = (effectiveHeightDp * 0.025f).dp.coerceIn(14.dp, 20.dp)
    val playerVerticalPadding = (effectiveHeightDp * 0.015f).dp.coerceIn(8.dp, 16.dp)
    val coverTopGap = (effectiveHeightDp * 0.03f).dp.coerceIn(16.dp, 24.dp)
    val coverTitleGap = (effectiveHeightDp * 0.035f).dp.coerceIn(16.dp, 30.dp)
    val titleProgressGap = coverTitleGap
    val progressMetaGap = (effectiveHeightDp * 0.012f).dp.coerceIn(8.dp, 14.dp)
    val coverTargetWidth = (effectiveHeightDp * 0.42f).dp.coerceIn(286.dp, 332.dp)
    val controlsRowPadding = (effectiveHeightDp * 0.04f).dp.coerceIn(24.dp, 38.dp)
    val bottomToolsTopPadding = (effectiveHeightDp * 0.012f).dp.coerceIn(8.dp, 14.dp)
    val bottomToolsBottomPadding = (effectiveHeightDp * 0.01f).dp.coerceIn(6.dp, 10.dp)
    val playerTopOffset = (effectiveHeightDp * 0.02f).dp.coerceIn(8.dp, 16.dp)

    LaunchedEffect(actionMessage) {
        val latest = actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, latest, Toast.LENGTH_SHORT).show()
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
        addToListBookId = null
        collectionPickerViewModel.clearMessages()
    }
    LaunchedEffect(collectionPickerUiState.errorMessage) {
        val message = collectionPickerUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        collectionPickerViewModel.clearMessages()
    }

    LaunchedEffect(bookmarkFeedbackActive) {
        if (bookmarkFeedbackActive) {
            delay(160L)
            bookmarkFeedbackActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = 0, y = playerDragOffsetPx.roundToInt()) }
            .draggable(
                state = verticalDragState,
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStarted = {
                    settleDragJob?.cancel()
                },
                onDragStopped = { velocity ->
                    val shouldDismiss = playerDragOffsetPx >= dragDismissDistancePx ||
                        velocity >= dragDismissVelocityPxPerSec
                    if (shouldDismiss) {
                        dismissPlayer()
                    } else {
                        settleDragJob?.cancel()
                        settleDragJob = dragScope.launch {
                            androidx.compose.animation.core.animate(
                                initialValue = playerDragOffsetPx,
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 200)
                            ) { value, _ ->
                                playerDragOffsetPx = value
                            }
                        }
                    }
                }
            )
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
                .navigationBarsPadding()
                .padding(horizontal = playerHorizontalPadding, vertical = playerVerticalPadding)
        ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        Spacer(modifier = Modifier.height(playerTopOffset + coverTopGap))
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
            val coverWidth = minOf(coverTargetWidth, maxWidth)
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
                    backgroundBlur = WideCoverBackgroundBlur,
                    showDownloadIndicator = isBookDownloaded,
                    downloadProgressPercent = activeDownloadProgressPercent
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
                if (timerIsActive) {
                    val timerYOffset = if (playerSeriesOrderLabel != null) 30.dp else 6.dp
                    PlayerTimerRunningBadge(
                        remainingMs = timerRemainingMs,
                        totalMs = timerTotalMs,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = 6.dp,
                                y = timerYOffset
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(coverTitleGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playerTitle,
                style = MaterialTheme.typography.titleMedium,
                color = primaryTextColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 10.dp)
                    .clickable {
                        chapterSheetTab = PlayerSheetTab.Chapters
                        showChapterSheet = true
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                        repeatDelayMillis = 2000,
                        initialDelayMillis = 1200,
                        spacing = MarqueeSpacing(48.dp)
                    )
            )
            IconButton(
                onClick = {
                    bookmarkFeedbackActive = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addBookmark(
                        positionSeconds = positionSeconds,
                        title = activeChapterTitle ?: playerTitle
                    )
                },
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = bookmarkIconScale
                        scaleY = bookmarkIconScale
                    }
            ) {
                Icon(
                    imageVector = if (bookmarkFeedbackActive) {
                        Icons.Filled.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    contentDescription = "Bookmark",
                    tint = secondaryTextColor
                )
            }
        }
        Spacer(modifier = Modifier.height(titleProgressGap))
        PlayerProgressBar(
            progress = effectiveProgress,
            activeColor = progressActiveColor,
            trackColor = progressTrackColor,
            onProgressChange = { newProgress ->
                isScrubbing = true
                scrubbingProgress = newProgress
                val chapterDurationMs = (chapterDurationSeconds * 1000.0).toLong()
                val chapterStartMs = (chapterStartSeconds * 1000.0).toLong()
                val targetMs = if (chapterDurationMs > 0L) {
                    chapterStartMs + (chapterDurationMs * newProgress.coerceIn(0f, 1f)).toLong()
                } else {
                    (durationSeconds * 1000.0 * newProgress.coerceIn(0f, 1f)).toLong()
                }
                viewModel.seekToPositionMs(positionMs = targetMs, commit = false)
            },
            onProgressChangeFinished = { finalProgress ->
                scrubbingProgress = finalProgress
                val chapterDurationMs = (chapterDurationSeconds * 1000.0).toLong()
                val chapterStartMs = (chapterStartSeconds * 1000.0).toLong()
                val targetMs = if (chapterDurationMs > 0L) {
                    chapterStartMs + (chapterDurationMs * finalProgress.coerceIn(0f, 1f)).toLong()
                } else {
                    (durationSeconds * 1000.0 * finalProgress.coerceIn(0f, 1f)).toLong()
                }
                viewModel.seekToPositionMs(positionMs = targetMs, commit = true)
                isScrubbing = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(progressMetaGap))
        val currentDisplaySeconds = chapterLocalSeconds.coerceAtLeast(0.0).toLong()
        val chapterDisplayDurationSeconds = chapterDurationSeconds
            .coerceAtLeast(0.0)
            .toLong()
            .coerceAtLeast(currentDisplaySeconds)
        val remainingDisplaySeconds = (chapterDisplayDurationSeconds - currentDisplaySeconds)
            .coerceAtLeast(0L)
        val wholeBookProgressPercent = formatProgressPercentLabel(progress)
        val wholeBookRemainingSeconds = (durationSeconds - positionSeconds)
            .coerceAtLeast(0.0)
            .toLong()
        val timeTextStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatSecondsAsHms(currentDisplaySeconds.toDouble()),
                style = timeTextStyle,
                color = secondaryTextColor,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(0.75f),
                maxLines = 1
            )
            Text(
                text = "${formatHoursMinutesPrecise(wholeBookRemainingSeconds.toDouble())} left • $wholeBookProgressPercent complete",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "-${formatSecondsAsHms(remainingDisplaySeconds.toDouble())}",
                style = timeTextStyle,
                color = secondaryTextColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.75f),
                maxLines = 1
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
                    .padding(horizontal = controlsRowPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Seek15Button(
                    forward = false,
                    seconds = controlPrefs.skipBackwardSeconds,
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
                    seconds = controlPrefs.skipForwardSeconds,
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
                    .padding(top = bottomToolsTopPadding, bottom = bottomToolsBottomPadding),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerBottomToolItem(
                    label = "Speed",
                    valueText = speedLabel,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = {
                        showSpeedSheet = true
                    }
                )
                PlayerBottomToolItem(
                    label = "Timer",
                    imageVector = if (timerLabel == null) Icons.Outlined.Timer else null,
                    valueText = timerLabel,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = {
                        timerSheetSessionKey += 1
                        showTimerSheet = true
                    }
                )
                PlayerBottomToolItem(
                    label = "Output",
                    imageVector = Icons.Outlined.SettingsVoice,
                    valueText = outputLabel,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = {
                        viewModel.refreshAudioOutputs()
                        showOutputSheet = true
                    }
                )
                PlayerBottomToolItem(
                    label = downloadToolLabel,
                    imageVector = Icons.Outlined.Download,
                    valueText = downloadToolValue,
                    primaryColor = primaryTextColor,
                    secondaryColor = secondaryTextColor,
                    onClick = { viewModel.toggleDownload() }
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
                            text = { Text("Skip Intro & Outro") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.SkipNext,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                bottomMenuExpanded = false
                                val activeChapter = chapters.getOrNull(activeChapterIndex)
                                if (activeChapter == null || !isIntroOutroChapterTitle(activeChapter.title)) {
                                    Toast.makeText(
                                        context,
                                        "No intro/outro marker found in this chapter.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    val nextChapter = chapters.getOrNull(activeChapterIndex + 1)
                                    if (nextChapter != null) {
                                        viewModel.jumpToSeconds(nextChapter.startSeconds)
                                        Toast.makeText(context, "Skipped chapter.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val endPositionMs = (durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
                                        viewModel.seekToPositionMs(positionMs = endPositionMs, commit = true)
                                        Toast.makeText(context, "Skipped chapter.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (effectivePlayerFinished) {
                                        "Mark as Unfinished"
                                    } else {
                                        "Mark as Finished"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (effectivePlayerFinished) {
                                        Icons.Outlined.Refresh
                                    } else {
                                        Icons.Outlined.CheckCircle
                                    },
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                bottomMenuExpanded = false
                                if (effectivePlayerFinished) {
                                    viewModel.markAsUnfinished()
                                } else {
                                    viewModel.markAsFinished()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Collection") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CollectionsBookmark,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                bottomMenuExpanded = false
                                addToListBookId = bookId
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Go to Book") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                bottomMenuExpanded = false
                                if (!bookId.isNullOrBlank()) {
                                    onGoToBook?.invoke(bookId)
                                }
                            }
                        )
                    }
                }
            }
        }
        }

        if (playbackUiState.sleepTimerExpiredPromptVisible) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSleepTimerExpiredPrompt,
                sheetState = timerExpiredSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                PlayerTimerExpiredSheet(
                    onClose = viewModel::dismissSleepTimerExpiredPrompt,
                    onExtendOneMinute = {
                        viewModel.extendSleepTimerOneMinute()
                        viewModel.dismissSleepTimerExpiredPrompt()
                    },
                    onReset = {
                        viewModel.clearSleepTimer()
                        viewModel.dismissSleepTimerExpiredPrompt()
                    }
                )
            }
        }

        if (showOutputSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOutputSheet = false },
                sheetState = outputSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                PlayerOutputSheet(
                    outputDevices = playbackUiState.outputDevices,
                    selectedOutputDeviceId = playbackUiState.selectedOutputDeviceId,
                    onSelectOutput = { deviceId ->
                        viewModel.selectAudioOutputDevice(deviceId)
                        showOutputSheet = false
                    }
                )
            }
        }

        if (showTimerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTimerSheet = false },
                sheetState = timerSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                PlayerTimerSheet(
                    sessionKey = timerSheetSessionKey,
                    sleepTimerMode = playbackUiState.sleepTimerMode,
                    sleepTimerRemainingMs = playbackUiState.sleepTimerRemainingMs,
                    onStartMinutes = { minutes ->
                        viewModel.startSleepTimerMinutes(minutes)
                        showTimerSheet = false
                    },
                    onStartEndOfChapter = {
                        viewModel.startSleepTimerEndOfChapter()
                        showTimerSheet = false
                    },
                    onTurnOff = {
                        viewModel.clearSleepTimer()
                        showTimerSheet = false
                    }
                )
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSpeedSheet = false },
                sheetState = speedSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                PlayerSpeedSheet(
                    currentSpeed = playbackUiState.playbackSpeed,
                    softToneLevel = playbackUiState.softToneLevel,
                    boostLevel = playbackUiState.boostLevel,
                    onSpeedChange = viewModel::setPlaybackSpeed,
                    onSoftToneLevelChange = viewModel::setSoftToneLevel,
                    onBoostLevelChange = viewModel::setBoostLevel
                )
            }
        }

        if (showChapterSheet && book != null) {
            ModalBottomSheet(
                onDismissRequest = { showChapterSheet = false },
                sheetState = chapterSheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ) {
                PlayerChapterBookmarkSheet(
                    book = book,
                    chapters = chapters,
                    bookmarks = bookmarks,
                    materialDesignEnabled = appearanceUiState.materialDesignEnabled,
                    selectedTab = chapterSheetTab,
                    activeChapterIndex = activeChapterIndex,
                    positionSeconds = positionSeconds,
                    isPlaying = playbackUiState.isPlaying,
                    timeLeftLabel = formatTimeLeftLabel(durationSeconds = durationSeconds, positionSeconds = positionSeconds),
                    onSelectTab = { chapterSheetTab = it },
                    onPlayChapter = { chapterStart ->
                        viewModel.jumpToSeconds(chapterStart)
                    },
                    onPlayBookmark = { bookmarkSeconds ->
                        viewModel.jumpToSeconds(bookmarkSeconds)
                    },
                    onEditBookmark = { bookmark, newTitle ->
                        viewModel.editBookmark(bookmark = bookmark, newTitle = newTitle)
                    },
                    onDeleteBookmark = { bookmark ->
                        viewModel.deleteBookmark(bookmark)
                    }
                )
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
}

private enum class PlayerSheetTab {
    Chapters,
    Bookmarks
}

@Composable
private fun PlayerChapterBookmarkSheet(
    book: BookSummary,
    chapters: List<BookChapter>,
    bookmarks: List<BookBookmark>,
    materialDesignEnabled: Boolean,
    selectedTab: PlayerSheetTab,
    activeChapterIndex: Int,
    positionSeconds: Double,
    isPlaying: Boolean,
    timeLeftLabel: String,
    onSelectTab: (PlayerSheetTab) -> Unit,
    onPlayChapter: (Double) -> Unit,
    onPlayBookmark: (Double) -> Unit,
    onEditBookmark: (BookBookmark, String) -> Unit,
    onDeleteBookmark: (BookBookmark) -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val statusBarTopPx = remember(view) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?.toFloat()
            ?: 0f
    }
    val sheetHeightFraction = if (screenHeightPx > 0f) {
        ((screenHeightPx - statusBarTopPx) / screenHeightPx).coerceIn(0.82f, 0.97f)
    } else {
        0.93f
    }
    val sheetHandleTopGap = (configuration.screenHeightDp * 0.01f).dp.coerceIn(8.dp, 14.dp)
    val sheetHandleTitleGap = (configuration.screenHeightDp * 0.008f).dp.coerceIn(8.dp, 12.dp)
    val sortedBookmarks = remember(bookmarks) {
        bookmarks.sortedWith(
            compareByDescending<BookBookmark> { it.createdAtMs ?: Long.MIN_VALUE }
                .thenBy { it.timeSeconds ?: Double.MAX_VALUE }
        )
    }
    var bookmarkMenuId by remember { mutableStateOf<String?>(null) }
    var editingBookmark by remember { mutableStateOf<BookBookmark?>(null) }
    var editingBookmarkTitle by rememberSaveable { mutableStateOf("") }
    var deletingBookmark by remember { mutableStateOf<BookBookmark?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeightFraction)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = sheetHandleTopGap, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.height(sheetHandleTitleGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
            ) {
                BookPoster(
                    book = book,
                    width = 48.dp,
                    height = 48.dp,
                    fillMaxWidth = true,
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                    text = timeLeftLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (materialDesignEnabled) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    }
                )
                .border(
                    if (materialDesignEnabled) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
                    } else {
                        BorderStroke(0.dp, Color.Transparent)
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerSheetTabButton(
                title = "Chapters",
                selected = selectedTab == PlayerSheetTab.Chapters,
                materialDesignEnabled = materialDesignEnabled,
                onClick = { onSelectTab(PlayerSheetTab.Chapters) },
                modifier = Modifier.weight(1f)
            )
            PlayerSheetTabButton(
                title = "Bookmarks",
                selected = selectedTab == PlayerSheetTab.Bookmarks,
                materialDesignEnabled = materialDesignEnabled,
                onClick = { onSelectTab(PlayerSheetTab.Bookmarks) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (selectedTab) {
            PlayerSheetTab.Chapters -> {
                if (chapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerSheetEmptyState(
                            icon = Icons.AutoMirrored.Outlined.ViewList,
                            title = "This book has no chapters"
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(chapters) { index, chapter ->
                            val isActiveChapter = index == activeChapterIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isActiveChapter) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else if (materialDesignEnabled) {
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable { onPlayChapter(chapter.startSeconds) }
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
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isActiveChapter) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = formatChapterDurationForRow(
                                            chapter = chapter,
                                            index = index,
                                            chapters = chapters,
                                            totalDurationSeconds = book.durationSeconds
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isActiveChapter) {
                                    ChapterPlaybackIndicator(
                                        isPlaying = isPlaying,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            PlayerSheetTab.Bookmarks -> {
                if (sortedBookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerSheetEmptyState(
                            icon = Icons.Outlined.BookmarkBorder,
                            title = "No bookmarks yet",
                            iconSize = 58.dp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(sortedBookmarks, key = { it.id }) { bookmark ->
                            val bookmarkSeconds = bookmark.timeSeconds
                            val isActiveBookmark = bookmarkSeconds != null &&
                                kotlin.math.abs(bookmarkSeconds - positionSeconds) < 1.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isActiveBookmark) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else if (materialDesignEnabled) {
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = bookmarkSeconds != null) {
                                        onPlayBookmark(bookmarkSeconds ?: 0.0)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.width(110.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = formatBookmarkDate(bookmark.createdAtMs),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = bookmark.timeSeconds?.let { formatSecondsAsHms(it) } ?: "--:--",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = formatBookmarkTime24(bookmark.createdAtMs),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isActiveBookmark) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = bookmark.title?.ifBlank { null } ?: "Bookmark",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isActiveBookmark) {
                                        ChapterPlaybackIndicator(
                                            isPlaying = isPlaying,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { bookmarkMenuId = bookmark.id },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreHoriz,
                                                contentDescription = "Bookmark actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = bookmarkMenuId == bookmark.id,
                                            onDismissRequest = { bookmarkMenuId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit title") },
                                                onClick = {
                                                    bookmarkMenuId = null
                                                    editingBookmark = bookmark
                                                    editingBookmarkTitle = bookmark.title.orEmpty()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete bookmark") },
                                                onClick = {
                                                    bookmarkMenuId = null
                                                    deletingBookmark = bookmark
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    editingBookmark?.let { targetBookmark ->
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("Edit bookmark title") },
            text = {
                OutlinedTextField(
                    value = editingBookmarkTitle,
                    onValueChange = { editingBookmarkTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEditBookmark(targetBookmark, editingBookmarkTitle)
                        editingBookmark = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingBookmark?.let { targetBookmark ->
        AlertDialog(
            onDismissRequest = { deletingBookmark = null },
            title = { Text("Delete bookmark") },
            text = { Text("Remove this bookmark?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBookmark(targetBookmark)
                        deletingBookmark = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBookmark = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlayerSheetTabButton(
    title: String,
    selected: Boolean,
    materialDesignEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) {
                    if (materialDesignEnabled) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    }
                } else {
                    Color.Transparent
                }
            )
            .border(
                if (materialDesignEnabled && selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                } else {
                    BorderStroke(0.dp, Color.Transparent)
                },
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerSheetEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconSize: Dp = 46.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    val topSlotHeight = 22.dp
    Column(
        modifier = Modifier
            .width(66.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topSlotHeight),
            contentAlignment = Alignment.Center
        ) {
            if (!valueText.isNullOrBlank()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = label,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val PlayerSpeedMin = 0.7f
private const val PlayerSpeedMax = 2.0f
private const val PlayerSpeedStep = 0.05f
private val PlayerSpeedPresetButtonHeight = 58.dp
private val PlayerSpeedPresets = listOf(0.7f, 1.0f, 1.2f, 1.5f, 1.7f, 2.0f)
private val PlayerTimerPresetMinutes = listOf(15, 30, 45, 60)
private const val PlayerTimerCustomMinMinutes = 1
private const val PlayerTimerCustomMaxMinutes = 360

private enum class PlayerTimerSelectionType {
    Preset,
    Custom,
    EndOfChapter
}

@Composable
private fun PlayerOutputSheet(
    outputDevices: List<PlaybackOutputDevice>,
    selectedOutputDeviceId: Int?,
    onSelectOutput: (Int?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Output",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose where audio plays",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (outputDevices.isEmpty()) {
            Text(
                text = "No output devices detected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            outputDevices.forEach { device ->
                val selected = device.id == selectedOutputDeviceId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                            }
                        )
                        .clickable { onSelectOutput(device.id) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsVoice,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.typeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PlayerTimerRunningBadge(
    remainingMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (totalMs > 0L) {
        (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.matchParentSize(),
                strokeWidth = 1.8.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(10.dp)
            )
        }
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatTimerCountdownShort(remainingMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlayerTimerExpiredSheet(
    onClose: () -> Unit,
    onExtendOneMinute: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close"
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Time's up",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Extend the timer or reset it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onExtendOneMinute,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Extend 1m")
            }
            TextButton(onClick = onReset) {
                Text("Reset timer")
            }
        }
    }
}

@Composable
private fun PlayerTimerSheet(
    sessionKey: Int,
    sleepTimerMode: SleepTimerMode,
    sleepTimerRemainingMs: Long?,
    onStartMinutes: (Int) -> Unit,
    onStartEndOfChapter: () -> Unit,
    onTurnOff: () -> Unit
) {
    val remainingMinutes = (((sleepTimerRemainingMs ?: 0L) + 59_999L) / 60_000L)
        .toInt()
        .coerceAtLeast(PlayerTimerCustomMinMinutes)
    val initialSelection = when {
        sleepTimerMode == SleepTimerMode.EndOfChapter -> PlayerTimerSelectionType.EndOfChapter
        sleepTimerMode == SleepTimerMode.Duration && remainingMinutes in PlayerTimerPresetMinutes -> PlayerTimerSelectionType.Preset
        sleepTimerMode == SleepTimerMode.Duration -> PlayerTimerSelectionType.Custom
        else -> PlayerTimerSelectionType.Preset
    }
    var selectionType by remember(sessionKey) { mutableStateOf(initialSelection) }
    var selectedPresetMinutes by remember(sessionKey) {
        mutableStateOf(
            when {
                sleepTimerMode == SleepTimerMode.Duration && remainingMinutes in PlayerTimerPresetMinutes -> remainingMinutes
                else -> PlayerTimerPresetMinutes.first()
            }
        )
    }
    var customMinutes by remember(sessionKey) {
        mutableStateOf(
            when {
                sleepTimerMode == SleepTimerMode.Duration && remainingMinutes !in PlayerTimerPresetMinutes -> {
                    remainingMinutes.coerceIn(PlayerTimerCustomMinMinutes, PlayerTimerCustomMaxMinutes)
                }
                else -> PlayerTimerCustomMinMinutes
            }
        )
    }
    var showCustomTimeDialog by remember { mutableStateOf(false) }
    var pickerHours by remember { mutableStateOf(customMinutes / 60) }
    var pickerMinutes by remember { mutableStateOf(customMinutes % 60) }
    val hapticFeedback = LocalHapticFeedback.current
    val buttonShape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(58.dp))
            Text(
                text = "Timer",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (selectionType) {
                        PlayerTimerSelectionType.Preset -> onStartMinutes(selectedPresetMinutes)
                        PlayerTimerSelectionType.Custom -> onStartMinutes(customMinutes)
                        PlayerTimerSelectionType.EndOfChapter -> onStartEndOfChapter()
                    }
                },
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(text = "Start")
            }
        }

        if (sleepTimerMode != SleepTimerMode.Off && (sleepTimerRemainingMs ?: 0L) > 0L) {
            Text(
                text = "Active: ${formatTimerRemainingLabel(sleepTimerMode, sleepTimerRemainingMs ?: 0L)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        PlayerTimerPresetMinutes.chunked(2).forEach { rowMinutes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowMinutes.forEach { minutes ->
                    val selected = selectionType == PlayerTimerSelectionType.Preset && selectedPresetMinutes == minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(buttonShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        shape = buttonShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectionType = PlayerTimerSelectionType.Preset
                                selectedPresetMinutes = minutes
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (minutes >= 60 && minutes % 60 == 0) {
                                "${minutes / 60} hr"
                            } else {
                                "$minutes min"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(buttonShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable {
                    selectionType = PlayerTimerSelectionType.Custom
                    pickerHours = (customMinutes / 60).coerceIn(0, PlayerTimerCustomMaxMinutes / 60)
                    pickerMinutes = (customMinutes % 60).coerceIn(0, 59)
                    showCustomTimeDialog = true
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Custom time",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selectionType == PlayerTimerSelectionType.Custom) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatCustomTimerLabel(customMinutes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(buttonShape)
                .background(
                    if (selectionType == PlayerTimerSelectionType.EndOfChapter) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                )
                .then(
                    if (selectionType == PlayerTimerSelectionType.EndOfChapter) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            shape = buttonShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable { selectionType = PlayerTimerSelectionType.EndOfChapter }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "End of chapter",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            if (selectionType == PlayerTimerSelectionType.EndOfChapter) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onTurnOff,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text("Off")
        }
    }
    if (showCustomTimeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTimeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val totalMinutes = ((pickerHours * 60) + pickerMinutes)
                            .coerceIn(PlayerTimerCustomMinMinutes, PlayerTimerCustomMaxMinutes)
                        customMinutes = totalMinutes
                        selectionType = PlayerTimerSelectionType.Custom
                        showCustomTimeDialog = false
                    }
                ) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimeDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Custom time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = formatCustomTimerLabel((pickerHours * 60) + pickerMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Hours", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = pickerHours.toFloat(),
                            onValueChange = { pickerHours = it.roundToInt().coerceIn(0, PlayerTimerCustomMaxMinutes / 60) },
                            valueRange = 0f..(PlayerTimerCustomMaxMinutes / 60).toFloat(),
                            steps = (PlayerTimerCustomMaxMinutes / 60 - 1).coerceAtLeast(0)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Minutes", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = pickerMinutes.toFloat(),
                            onValueChange = { pickerMinutes = it.roundToInt().coerceIn(0, 59) },
                            valueRange = 0f..59f,
                            steps = 58
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun PlayerSpeedSheet(
    currentSpeed: Float,
    softToneLevel: Float,
    boostLevel: Float,
    onSpeedChange: (Float) -> Unit,
    onSoftToneLevelChange: (Float) -> Unit,
    onBoostLevelChange: (Float) -> Unit
) {
    val normalizedSpeed = normalizePlaybackSpeed(currentSpeed)
    val normalizedSoftTone = softToneLevel.coerceIn(0f, 1f)
    val normalizedBoost = boostLevel.coerceIn(0f, 1f)
    val sliderSteps = (((PlayerSpeedMax - PlayerSpeedMin) / PlayerSpeedStep).toInt() - 1).coerceAtLeast(0)
    val buttonShape = RoundedCornerShape(12.dp)
    val selectedButtonColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val selectedButtonBorder = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
    val defaultSubLabelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val hapticFeedback = LocalHapticFeedback.current
    var showSoftToneDialog by remember { mutableStateOf(false) }
    var showBoostDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerEffectSlot(
                title = "Soft",
                valueLabel = formatEffectLevelLabel(normalizedSoftTone),
                icon = Icons.Outlined.VolumeDown,
                onClick = { showSoftToneDialog = true },
                modifier = Modifier.weight(1f)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatPlaybackSpeedExact(normalizedSpeed),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            PlayerEffectSlot(
                title = "Boost",
                valueLabel = formatEffectLevelLabel(normalizedBoost),
                icon = Icons.Outlined.VolumeUp,
                onClick = { showBoostDialog = true },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(
                icon = Icons.Outlined.RemoveCircleOutline,
                contentDescription = "Decrease speed",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSpeedChange(normalizePlaybackSpeed(normalizedSpeed - PlayerSpeedStep))
                }
            )
            Slider(
                value = normalizedSpeed,
                onValueChange = { onSpeedChange(normalizePlaybackSpeed(it)) },
                valueRange = PlayerSpeedMin..PlayerSpeedMax,
                steps = sliderSteps,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            CircleActionButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Increase speed",
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSpeedChange(normalizePlaybackSpeed(normalizedSpeed + PlayerSpeedStep))
                }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        PlayerSpeedPresets.chunked(3).forEach { presetRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetRow.forEach { preset ->
                    val isSelected = kotlin.math.abs(normalizedSpeed - preset) < 0.01f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PlayerSpeedPresetButtonHeight)
                            .clip(buttonShape)
                            .background(
                                if (isSelected) selectedButtonColor else MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.48f
                                )
                            )
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSpeedChange(preset)
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = selectedButtonBorder,
                                        shape = buttonShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (preset == 1.0f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = "1.0",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "DEFAULT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (isSelected) {
                                        defaultSubLabelColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        } else {
                            Text(
                                text = formatPlaybackSpeedPreset(preset),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSoftToneDialog) {
        PlayerEffectSliderDialog(
            title = "Soft",
            subtitle = "Reduce sharpness in higher frequencies.",
            value = normalizedSoftTone,
            onValueChange = onSoftToneLevelChange,
            onDismiss = { showSoftToneDialog = false }
        )
    }

    if (showBoostDialog) {
        PlayerEffectSliderDialog(
            title = "Boost",
            subtitle = "Increase spoken voice loudness.",
            value = normalizedBoost,
            onValueChange = onBoostLevelChange,
            onDismiss = { showBoostDialog = false }
        )
    }
}

@Composable
private fun PlayerEffectSlot(
    title: String,
    valueLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerEffectSliderDialog(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onValueChange(0f)
                    onDismiss()
                }
            ) {
                Text("Reset")
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatEffectLevelLabel(value),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = value.coerceIn(0f, 1f),
                    onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
                    valueRange = 0f..1f
                )
            }
        }
    )
}

private fun normalizePlaybackSpeed(rawSpeed: Float): Float {
    val clamped = rawSpeed.coerceIn(PlayerSpeedMin, PlayerSpeedMax)
    val steps = ((clamped - PlayerSpeedMin) / PlayerSpeedStep).roundToInt()
    return (PlayerSpeedMin + steps * PlayerSpeedStep).coerceIn(PlayerSpeedMin, PlayerSpeedMax)
}

private fun formatPlaybackSpeedExact(speed: Float): String {
    return String.format(Locale.getDefault(), "%.2fx", normalizePlaybackSpeed(speed))
}

private fun formatPlaybackSpeedPreset(speed: Float): String {
    return String.format(Locale.getDefault(), "%.1f", speed.coerceIn(PlayerSpeedMin, PlayerSpeedMax))
}

private fun formatPlaybackSpeedShort(speed: Float): String {
    val normalized = normalizePlaybackSpeed(speed)
    val text = String.format(Locale.getDefault(), "%.2f", normalized)
        .trimEnd('0')
        .trimEnd('.')
    return "${text}x"
}

private fun formatEffectLevelLabel(level: Float): String {
    val percent = (level.coerceIn(0f, 1f) * 100f).roundToInt()
    return "$percent%"
}

private fun formatTimerChipLabel(remainingMs: Long): String {
    val minutes = ((remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L).coerceAtLeast(1L)
    return if (minutes >= 60L && minutes % 60L == 0L) {
        "${minutes / 60L}h"
    } else {
        "${minutes}m"
    }
}

private fun formatTimerCountdownShort(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatCustomTimerLabel(totalMinutes: Int): String {
    val clamped = totalMinutes.coerceIn(PlayerTimerCustomMinMinutes, PlayerTimerCustomMaxMinutes)
    val hours = clamped / 60
    val minutes = clamped % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatTimerRemainingLabel(mode: SleepTimerMode, remainingMs: Long): String {
    val minutes = ((remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L).coerceAtLeast(1L)
    val minutesText = if (minutes >= 60L && minutes % 60L == 0L) {
        "${minutes / 60L} hr"
    } else {
        "$minutes min"
    }
    return if (mode == SleepTimerMode.EndOfChapter) {
        "End of chapter (about $minutesText)"
    } else {
        "$minutesText left"
    }
}

@Composable
private fun BookListenProgressButton(
    text: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonShape = ButtonDefaults.shape
    val baseColor = Color(0xFF1F2126)
    val contentColor = Color.White
    val progressColor = listenButtonRemainingColor(baseColor)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val progressFillFraction = if (clampedProgress >= 0.995f) 0f else clampedProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(buttonShape)
            .background(baseColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFillFraction)
                .background(progressColor)
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text)
        }
    }
}

private fun listenButtonRemainingColor(baseColor: Color): Color {
    return if (baseColor.luminance() < 0.5f) {
        lerp(baseColor, Color.White, 0.22f)
    } else {
        lerp(baseColor, Color.Black, 0.12f)
    }
}

@Composable
private fun Seek15Button(
    forward: Boolean,
    seconds: Int,
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
            text = seconds.coerceIn(10, 60).toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = tint
        )
    }
}

private data class ChapterWindow(
    val startSeconds: Double,
    val endSeconds: Double,
    val durationSeconds: Double
)

private fun chapterWindow(
    chapters: List<BookChapter>,
    index: Int,
    totalDurationSeconds: Double
): ChapterWindow? {
    if (index !in chapters.indices) return null
    val chapter = chapters[index]
    val start = chapter.startSeconds.coerceAtLeast(0.0)
    val fallbackEnd = when {
        index < chapters.lastIndex -> chapters[index + 1].startSeconds
        totalDurationSeconds > 0.0 -> totalDurationSeconds
        else -> start
    }
    val rawEnd = chapter.endSeconds ?: fallbackEnd
    val end = rawEnd.coerceAtLeast(start)
    return ChapterWindow(
        startSeconds = start,
        endSeconds = end,
        durationSeconds = (end - start).coerceAtLeast(0.0)
    )
}

private fun formatChapterDurationForRow(
    chapter: BookChapter,
    index: Int,
    chapters: List<BookChapter>,
    totalDurationSeconds: Double?
): String {
    val start = chapter.startSeconds.coerceAtLeast(0.0)
    val fallbackEnd = when {
        chapter.endSeconds != null -> chapter.endSeconds
        index < chapters.lastIndex -> chapters[index + 1].startSeconds
        totalDurationSeconds != null -> totalDurationSeconds
        else -> start
    } ?: start
    val duration = (fallbackEnd - start).coerceAtLeast(0.0)
    return formatDurationHoursMinutes(duration)
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
    materialDesignEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (materialDesignEnabled) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        Color.Transparent
                    }
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(
            modifier = modifier.clickable(onClick = onClick),
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
}

@Composable
fun CustomizePlaceholderScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onHomeClick: (() -> Unit)? = null,
    viewModel: CustomizeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticFeedback = LocalHapticFeedback.current
    var selectedTab by remember { mutableStateOf("Lists") }
    var pendingListRows by remember { mutableStateOf<List<ToggleSectionItem>?>(null) }
    var pendingPersonalizedRows by remember { mutableStateOf<List<ToggleSectionItem>?>(null) }
    var pendingHiddenListSectionIds by remember { mutableStateOf<Set<String>?>(null) }
    var pendingHiddenPersonalizedSectionIds by remember { mutableStateOf<Set<String>?>(null) }

    fun cancelAndExit() {
        pendingListRows = null
        pendingPersonalizedRows = null
        pendingHiddenListSectionIds = null
        pendingHiddenPersonalizedSectionIds = null
        onCancel()
    }

    fun saveAndExit() {
        pendingListRows?.let { viewModel.setListOrder(it.map(ToggleSectionItem::id)) }
        pendingPersonalizedRows?.let { viewModel.setPersonalizedOrder(it.map(ToggleSectionItem::id)) }
        pendingHiddenListSectionIds?.let(viewModel::setHiddenListSectionIds)
        pendingHiddenPersonalizedSectionIds?.let(viewModel::setHiddenPersonalizedSectionIds)
        pendingListRows = null
        pendingPersonalizedRows = null
        pendingHiddenListSectionIds = null
        pendingHiddenPersonalizedSectionIds = null
        onDone()
    }

    BackHandler(onBack = ::cancelAndExit)

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
                    onClick = {
                        pendingListRows = null
                        pendingPersonalizedRows = null
                        pendingHiddenListSectionIds = null
                        pendingHiddenPersonalizedSectionIds = null
                        onHomeClick()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            CircleActionButton(
                icon = Icons.Filled.Check,
                contentDescription = "Done",
                onClick = ::saveAndExit
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

        val effectiveListRows = pendingListRows ?: uiState.listSections
        val effectivePersonalizedRows = pendingPersonalizedRows ?: ensureListenAgainSection(uiState.personalizedSections)
        val effectiveHiddenListSectionIds = pendingHiddenListSectionIds ?: uiState.hiddenListSectionIds
        val effectiveHiddenPersonalizedSectionIds =
            pendingHiddenPersonalizedSectionIds ?: uiState.hiddenPersonalizedSectionIds
        val orderedRows = if (selectedTab == "Lists") effectiveListRows else effectivePersonalizedRows

        fun moveRow(
            source: List<ToggleSectionItem>,
            from: Int,
            to: Int
        ): List<ToggleSectionItem> = moveSectionRow(source = source, from = from, to = to)

        fun moveRowByDelta(rowId: String, delta: Int) {
            if (delta == 0) return
            val fromIndex = orderedRows.indexOfFirst { it.id == rowId }
            if (fromIndex < 0) return
            val toIndex = fromIndex + delta
            if (toIndex !in orderedRows.indices) return
            val updated = moveRow(orderedRows, fromIndex, toIndex)
            if (selectedTab == "Lists") {
                pendingListRows = updated
            } else {
                pendingPersonalizedRows = updated
            }
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            itemsIndexed(orderedRows, key = { _, item -> item.id }) { index, row ->
                val enabled = if (selectedTab == "Lists") {
                    !effectiveHiddenListSectionIds.contains(row.id)
                } else {
                    !effectiveHiddenPersonalizedSectionIds.contains(row.id)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clickable {
                            if (selectedTab == "Lists") {
                                pendingHiddenListSectionIds = toggleHiddenSection(
                                    hidden = effectiveHiddenListSectionIds,
                                    id = row.id
                                )
                            } else {
                                pendingHiddenPersonalizedSectionIds = toggleHiddenSection(
                                    hidden = effectiveHiddenPersonalizedSectionIds,
                                    id = row.id
                                )
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { moveRowByDelta(row.id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowUp,
                                contentDescription = "Move up",
                                tint = if (index > 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                        }
                        IconButton(
                            onClick = { moveRowByDelta(row.id, 1) },
                            enabled = index < orderedRows.lastIndex,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Move down",
                                tint = if (index < orderedRows.lastIndex) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                        .padding(horizontal = 10.dp, vertical = 14.dp)
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
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onClick: () -> Unit
) {
    val book = series.leadBook
    val posterWidth = 92.dp
    val posterHeight = 118.dp
    val layerCount = series.count.coerceIn(2, 3)
    val stackStepX = 5.dp
    val stackStepY = 8.dp
    val frameWidth = posterWidth + (stackStepX * (layerCount - 1))
    val frameHeight = posterHeight + (stackStepY * (layerCount - 1))
    Column(
        modifier = Modifier
            .width(frameWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
        ) {
            repeat(layerCount) { layer ->
                val xOffset = stackStepX * layer
                val yOffset = stackStepY * layer
                val layerShadow = if (layer == layerCount - 1) 1.2.dp else 2.8.dp
                FramedCoverImage(
                    coverUrl = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .width(posterWidth)
                        .height(posterHeight)
                        .shadow(elevation = layerShadow, shape = RoundedCornerShape(8.dp), clip = false)
                        .graphicsLayer(alpha = 1f),
                    shape = RoundedCornerShape(8.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = WideCoverBackgroundBlur
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
        Text(
            text = series.seriesName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(frameWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (series.count == 1) "1 book" else "${series.count} books",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(frameWidth)
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
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onGoToBook: () -> Unit,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onRemoveFromContinueListening: () -> Unit,
    onToggleDownload: () -> Unit,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
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
                shape = RoundedCornerShape(6.dp),
                showDownloadIndicator = isDownloaded,
                downloadProgressPercent = downloadProgressPercent
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
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Continue listening actions",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Go to Book") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        },
                        onClick = {
                            onGoToBook()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = {
                            onAddToCollection()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (item.book.hasFinishedProgress()) {
                                    "Mark as Unfinished"
                                } else {
                                    "Mark as Finished"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.book.hasFinishedProgress()) {
                                    Icons.Outlined.Refresh
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                contentDescription = null
                            )
                        },
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from Continue Listening") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                        onClick = {
                            onRemoveFromContinueListening()
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(downloadLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = {
                            onToggleDownload()
                            menuExpanded = false
                        }
                    )
                }
            }
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
    backgroundBlur: Dp = WideCoverBackgroundBlur,
    showDownloadIndicator: Boolean = false,
    downloadProgressPercent: Int? = null
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

    Box(modifier = posterModifier) {
        FramedCoverImage(
            coverUrl = book.coverUrl,
            contentDescription = book.title,
            modifier = Modifier.matchParentSize(),
            shape = shape,
            contentScale = contentScale,
            backgroundBlur = backgroundBlur
        )
        val progress = downloadProgressPercent?.coerceIn(0, 100)
        val showProgress = progress != null && progress in 0..99
        val showCompleted = showDownloadIndicator && !showProgress
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
        BooksStatusFilter.NotStarted -> filter { !it.hasStartedProgress() && !it.hasFinishedProgress() }
        BooksStatusFilter.NotFinished -> filter { !it.hasFinishedProgress() }
    }
}

private fun isIntroOutroChapterTitle(raw: String?): Boolean {
    val title = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (title.isBlank()) return false
    return title.contains("intro") ||
        title.contains("introduction") ||
        title.contains("prologue") ||
        title.contains("outro") ||
        title.contains("epilogue") ||
        title.contains("credits")
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
    if (hasFinishedProgress()) return true
    val normalized = normalizedProgressPercent()
    return normalized != null && normalized > 0.001
}

private fun BookSummary.hasFinishedProgress(): Boolean {
    val normalized = normalizedProgressPercent()
    return when {
        normalized != null -> normalized >= 0.995
        else -> isFinished
    }
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
        .split(Regex(",|;|\\s+and\\s+|\\s*&\\s*"))
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

private fun formatHoursMinutesPrecise(durationSeconds: Double?): String {
    val totalSeconds = durationSeconds?.coerceAtLeast(0.0) ?: 0.0
    val totalMinutes = (totalSeconds / 60.0).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}

private fun formatProgressPercentLabel(progressFraction: Float): String {
    val percent = (progressFraction.coerceIn(0f, 1f) * 100f).toDouble()
    return if (percent < 1.0) {
        String.format(Locale.getDefault(), "%.1f%%", percent)
    } else {
        "${percent.toInt().coerceIn(0, 100)}%"
    }
}

private fun formatTimeLeftLabel(durationSeconds: Double, positionSeconds: Double): String {
    val remainingSeconds = (durationSeconds - positionSeconds).coerceAtLeast(0.0)
    val readable = formatDurationHoursMinutes(remainingSeconds)
    return if (readable.isBlank()) "0m left" else "$readable left"
}

private fun formatBookmarkDate(createdAtMs: Long?): String {
    val timestamp = createdAtMs ?: return "Unknown date"
    val formatter = SimpleDateFormat("M/d/yyyy", Locale.getDefault())
    return runCatching {
        formatter.format(Date(timestamp))
    }.getOrDefault("Unknown date")
}

private fun formatBookmarkTime24(createdAtMs: Long?): String {
    val timestamp = createdAtMs ?: return "--:--"
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return runCatching {
        formatter.format(Date(timestamp))
    }.getOrDefault("--:--")
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
        String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", mb)
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
