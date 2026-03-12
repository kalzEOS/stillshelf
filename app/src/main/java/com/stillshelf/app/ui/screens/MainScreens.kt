package com.stillshelf.app.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.components.UpdateNotesDialogContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
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
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.imageLoader
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.core.model.BookBookmark
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.BookmarkEntry
import com.stillshelf.app.core.model.ContinueListeningItem
import com.stillshelf.app.core.model.SeriesStackSummary
import com.stillshelf.app.core.util.formatDurationHoursMinutes
import com.stillshelf.app.core.util.formatHoursMinutesPrecise
import com.stillshelf.app.core.util.formatTimeLeftLabel
import com.stillshelf.app.core.util.hasMeaningfulStartedProgress
import com.stillshelf.app.core.util.hasFinishedProgress
import com.stillshelf.app.core.util.hasStartedProgress
import com.stillshelf.app.core.util.resolveListenActionLabel
import com.stillshelf.app.core.util.searchMetadataLabel
import com.stillshelf.app.core.util.timeLeftLabel
import com.stillshelf.app.domain.usecase.SkipIntroOutroUseCase
import com.stillshelf.app.domain.usecase.toUserMessage
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
import com.stillshelf.app.ui.theme.LocalMaterialDesignEnabled
import java.io.File
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
private val HomeTopBarLibrarySelectorMinWidth = 184.dp
private val HomeTopBarLibrarySelectorPreferredWidth = 236.dp

private val SeriesStackMinLayerExtent = 42.dp
private val SeriesStackStep = 5.dp
private val SeriesStackFrontShadow = 1.dp
private val SeriesStackBackShadow = 2.8.dp
private val SeriesStackCornerShape = RoundedCornerShape(6.dp)
private val SeriesStackBackgroundBlur = 44.dp

private fun stackedSeriesLayerExtent(baseExtent: Dp, layer: Int, shiftStep: Dp): Dp {
    // Shrink twice the diagonal shift so back-layer corners stay visible
    // on both top-right and bottom-left while preserving one-tile footprint.
    val shrink = shiftStep * (layer.coerceAtLeast(0) * 5)
    return (baseExtent - shrink).coerceAtLeast(SeriesStackMinLayerExtent)
}

private fun stackedSeriesLayerShadow(layer: Int, layerCount: Int): Dp {
    return if (layer == layerCount - 1) SeriesStackFrontShadow else SeriesStackBackShadow
}

@Composable
private fun SeriesStackCoverLayers(
    coverUrls: List<String>,
    contentDescription: String?,
    layerCount: Int,
    frameWidth: Dp,
    frameHeight: Dp,
    modifier: Modifier = Modifier
) {
    val resolvedCoverUrls = remember(coverUrls) { normalizeSeriesStackCoverUrls(coverUrls) }
    val displayCoverUrls = remember(resolvedCoverUrls, layerCount) {
        resolveSeriesStackDisplayCovers(
            coverUrls = resolvedCoverUrls,
            layerCount = layerCount
        )
    }
    val resolvedLayerCount = displayCoverUrls.size.coerceIn(2, 3)
    val layerShape = SeriesStackCornerShape
    Box(
        modifier = modifier.clipToBounds()
    ) {
        repeat(resolvedLayerCount) { layer ->
            val layerCardWidth = stackedSeriesLayerExtent(frameWidth, layer, SeriesStackStep)
            val layerCardHeight = stackedSeriesLayerExtent(frameHeight, layer, SeriesStackStep)
            val xOffset = (frameWidth - layerCardWidth).coerceAtLeast(0.dp)
            val yOffset = (frameHeight - layerCardHeight).coerceAtLeast(0.dp)
            val layerShadow = stackedSeriesLayerShadow(layer = layer, layerCount = resolvedLayerCount)
            FramedCoverImage(
                coverUrl = displayCoverUrls.getOrNull(layer),
                contentDescription = contentDescription,
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .width(layerCardWidth)
                    .height(layerCardHeight)
                    .shadow(elevation = layerShadow, shape = layerShape, clip = false)
                    .graphicsLayer(alpha = 1f),
                shape = layerShape,
                contentScale = ContentScale.Fit,
                backgroundBlur = SeriesStackBackgroundBlur
            )
        }
    }
}

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

internal fun normalizeSeriesStackCoverUrls(coverUrls: List<String>): List<String> {
    return coverUrls
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .toList()
}

internal fun resolveSeriesStackDisplayCovers(
    coverUrls: List<String>,
    layerCount: Int
): List<String?> {
    val normalized = normalizeSeriesStackCoverUrls(coverUrls)
    if (normalized.isEmpty()) {
        return List(layerCount.coerceIn(2, 3)) { null }
    }
    if (normalized.size == 1) {
        return List(layerCount.coerceIn(2, 3)) { normalized.first() }
    }
    val frontCover = normalized[0]
    val backCoverLeft = normalized.getOrNull(1)
    val backCoverRight = normalized.getOrNull(2)
    return if (normalized.size >= 3 && layerCount >= 3) {
        listOf(backCoverRight, backCoverLeft, frontCover)
    } else {
        listOf(backCoverLeft, frontCover)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
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
    val configuration = LocalConfiguration.current
    val homeStartInset = AppScreenHorizontalPadding
    val homeEndInset = AppScreenHorizontalPadding
    val homeInsetTotal = AppScreenHorizontalPadding * 2
    val homeShelfPosterWidth = StandardGridCoverWidth
    val homeShelfPosterHeight = StandardGridCoverHeight
    val continueListeningPosterWidth = 72.dp
    val continueListeningPosterHeight = 80.dp
    val continueListeningCardHeight = remember(configuration.fontScale) {
        (
            continueListeningPosterHeight +
                12.dp +
                ((configuration.fontScale - 1f).coerceAtLeast(0f) * 8f).dp
            ).coerceIn(96.dp, 124.dp)
    }
    val newestAuthorsGap = 8.dp
    val homeFullBleedModifier = remember(homeStartInset, homeEndInset) {
        Modifier
            .fillMaxWidth()
            .padding(start = homeStartInset, end = homeEndInset)
    }
    val homeCarouselModifier = remember {
        Modifier.fillMaxWidth()
    }
    val homeCarouselContentPadding = remember(homeStartInset) {
        PaddingValues(start = homeStartInset, end = 0.dp)
    }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isLibraryMenuExpanded by remember { mutableStateOf(false) }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        val availableHomeContentWidth = remember(maxWidth, homeInsetTotal) {
            (maxWidth - homeInsetTotal).coerceAtLeast(0.dp)
        }
        val continueListeningCardWidth = remember(availableHomeContentWidth, configuration.fontScale) {
            val widthFactor = if (configuration.fontScale > 1.05f) 0.84f else 0.8f
            (availableHomeContentWidth * widthFactor).coerceIn(266.dp, 336.dp)
        }
        val newestAuthorsChipWidth = remember(availableHomeContentWidth, newestAuthorsGap) {
            (
                (availableHomeContentWidth - (newestAuthorsGap * 2)) / 3f
                ).coerceIn(94.dp, 124.dp)
        }
        val newestAuthorsAvatarSize = remember(newestAuthorsChipWidth) {
            (newestAuthorsChipWidth * 0.78f).coerceIn(78.dp, 96.dp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                end = 0.dp,
                top = 12.dp,
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = homeStartInset, end = homeEndInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f)
                    ) {
                        val hasLibraries = menuUiState.libraries.isNotEmpty()
                        val libraryMenuWidth = HomeTopBarLibrarySelectorPreferredWidth
                            .coerceAtMost(maxWidth)
                            .coerceAtLeast(HomeTopBarLibrarySelectorMinWidth.coerceAtMost(maxWidth))
                        Row(
                            modifier = Modifier
                                .width(libraryMenuWidth)
                                .clip(RoundedCornerShape(10.dp))
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
                        AppDropdownMenu(
                            expanded = isLibraryMenuExpanded && hasLibraries,
                            onDismissRequest = { isLibraryMenuExpanded = false },
                            modifier = Modifier.width(libraryMenuWidth)
                        ) {
                            menuUiState.libraries.forEach { library ->
                                val isActive = menuUiState.activeLibraryId == library.id
                                AppDropdownMenuItem(
                                    text = {
                                        Text(
                                            text = library.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
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
                        AppDropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            AppDropdownMenuItem(
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
                            AppDropdownMenuItem(
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
                            HorizontalDivider()
                            AppDropdownMenuItem(
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
                            if (menuUiState.servers.isEmpty()) {
                                AppDropdownMenuItem(
                                    text = { Text("No servers") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Dns,
                                            contentDescription = null
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                val canSwitchServers = menuUiState.servers.size > 1 && !menuUiState.isSwitchingServer
                                menuUiState.servers.forEach { server ->
                                    val isActive = menuUiState.activeServerId == server.id
                                    AppDropdownMenuItem(
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
                                        enabled = canSwitchServers,
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
                Card(
                    modifier = homeFullBleedModifier,
                    colors = CardDefaults.cardColors(
                        containerColor = if (appearanceUiState.materialDesignEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
                            )
                        }
                    ),
                    border = if (appearanceUiState.materialDesignEnabled) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    } else {
                        BorderStroke(
                            width = 1.dp,
                            color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                                Color.White.copy(alpha = 0.14f)
                            } else {
                                Color.Black.copy(alpha = 0.1f)
                            }
                        )
                    },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        sectionContent()
                    }
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
                        item {
                            SectionTitle(
                                title = "Continue Listening",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            when {
                                uiState.isLoading && uiState.continueListening.isEmpty() -> {
                                    LazyRow(
                                        modifier = homeCarouselModifier,
                                        contentPadding = homeCarouselContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        item {
                                            ContinueListeningSkeletonCard(
                                                cardWidth = continueListeningCardWidth,
                                                cardHeight = continueListeningCardHeight,
                                                posterWidth = continueListeningPosterWidth,
                                                posterHeight = continueListeningPosterHeight
                                            )
                                        }
                                        item { PosterStub(86.dp, 108.dp, Color(0xFF3B2E45)) }
                                        item { PosterStub(86.dp, 108.dp, Color(0xFF2D3840)) }
                                    }
                                }

                                uiState.continueListening.isEmpty() -> {
                                    Text(
                                        text = "No books in progress yet.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                    )
                                }

                                else -> {
                                    LazyRow(
                                        modifier = homeCarouselModifier,
                                        contentPadding = homeCarouselContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(uiState.continueListening, key = { it.book.id }) { item ->
                                            ContinueListeningCard(
                                                item = item,
                                                cardWidth = continueListeningCardWidth,
                                                cardHeight = continueListeningCardHeight,
                                                posterWidth = continueListeningPosterWidth,
                                                posterHeight = continueListeningPosterHeight,
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
                                                onResetBookProgress = {
                                                    viewModel.resetBookProgress(item.book.id)
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
                        item {
                            SectionTitle(
                                title = "Listen Again",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            val books = uiState.listenAgain
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
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
                                            width = homeShelfPosterWidth,
                                            height = homeShelfPosterHeight,
                                            contentScale = ContentScale.Fit,
                                            showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                            downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                        )
                                        Row(
                                            modifier = Modifier.width(homeShelfPosterWidth),
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
                                                AppDropdownMenu(
                                                    expanded = menuExpanded,
                                                    onDismissRequest = { menuExpanded = false }
                                                ) {
                                                    AppDropdownMenuItem(
                                                        text = { Text("Add to Collection") },
                                                        leadingIcon = {
                                                            Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                        },
                                                        onClick = {
                                                            addToListBookId = book.id
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    AppDropdownMenuItem(
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
                                                        enabled = book.hasStartedProgress(),
                                                        onClick = {
                                                            if (book.hasFinishedProgress()) {
                                                                viewModel.markAsUnfinished(book.id)
                                                            } else {
                                                                viewModel.markAsFinished(book.id)
                                                            }
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    ResetBookProgressMenuItem(
                                                        showIcon = true,
                                                        onConfirm = {
                                                            viewModel.resetBookProgress(book.id)
                                                            menuExpanded = false
                                                        }
                                                    )
                                                    AppDropdownMenuItem(
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
                                                    AppDropdownMenuItem(
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
                        item {
                            SectionTitle(
                                title = "Recently Added",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            when {
                                uiState.isLoading && uiState.recentlyAdded.isEmpty() -> {
                                    LazyRow(
                                        modifier = homeCarouselModifier,
                                        contentPadding = homeCarouselContentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        item { PosterStub(homeShelfPosterWidth, homeShelfPosterHeight, Color(0xFFA33A31)) }
                                        item { PosterStub(homeShelfPosterWidth, homeShelfPosterHeight, Color(0xFF2F4A58)) }
                                        item { PosterStub(homeShelfPosterWidth, homeShelfPosterHeight, Color(0xFF8D6C3F)) }
                                    }
                                }

                                uiState.recentlyAdded.isEmpty() -> {
                                    Text(
                                        text = "No recently added books.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                    )
                                }

                                else -> {
                                    LazyRow(
                                        modifier = homeCarouselModifier,
                                        contentPadding = homeCarouselContentPadding,
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
                                                    width = homeShelfPosterWidth,
                                                    height = homeShelfPosterHeight,
                                                    contentScale = ContentScale.Fit,
                                                    showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                                    downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                                )
                                                Row(
                                                    modifier = Modifier.width(homeShelfPosterWidth),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = book.searchMetadataLabel(),
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
                                                                text = { Text("Add to Collection") },
                                                                leadingIcon = {
                                                                    Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                                },
                                                                onClick = {
                                                                    addToListBookId = book.id
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            AppDropdownMenuItem(
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
                                                                enabled = book.hasStartedProgress(),
                                                                onClick = {
                                                                    if (book.hasFinishedProgress()) {
                                                                        viewModel.markAsUnfinished(book.id)
                                                                    } else {
                                                                        viewModel.markAsFinished(book.id)
                                                                    }
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            ResetBookProgressMenuItem(
                                                                showIcon = true,
                                                                onConfirm = {
                                                                    viewModel.resetBookProgress(book.id)
                                                                    menuExpanded = false
                                                                }
                                                            )
                                                            AppDropdownMenuItem(
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
                        item {
                            SectionTitle(
                                title = "Recent Series",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            val seriesItems = uiState.recentSeries
                            if (seriesItems.isEmpty()) {
                                Text(
                                    text = "No recent series.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                )
                            } else {
                                LazyRow(
                                    modifier = homeCarouselModifier,
                                    contentPadding = homeCarouselContentPadding,
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
                        item {
                            SectionTitle(
                                title = "Discover",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item {
                            val discoverBooks = uiState.discoverBooks
                            if (discoverBooks.isEmpty()) {
                                Text(
                                    text = "No discover picks yet.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                )
                            } else {
                                LazyRow(
                                    modifier = homeCarouselModifier,
                                    contentPadding = homeCarouselContentPadding,
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
                                                width = homeShelfPosterWidth,
                                                height = homeShelfPosterHeight,
                                                contentScale = ContentScale.Fit,
                                                showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                                downloadProgressPercent = uiState.downloadProgressByBookId[book.id]
                                            )
                                            Row(
                                                modifier = Modifier.width(homeShelfPosterWidth),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = book.searchMetadataLabel(),
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
                                                            text = { Text("Add to Collection") },
                                                            leadingIcon = {
                                                                Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null)
                                                            },
                                                            onClick = {
                                                                addToListBookId = book.id
                                                                menuExpanded = false
                                                            }
                                                        )
                                                        AppDropdownMenuItem(
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
                                                            enabled = book.hasStartedProgress(),
                                                            onClick = {
                                                                if (book.hasFinishedProgress()) {
                                                                    viewModel.markAsUnfinished(book.id)
                                                                } else {
                                                                    viewModel.markAsFinished(book.id)
                                                                }
                                                                menuExpanded = false
                                                            }
                                                        )
                                                        ResetBookProgressMenuItem(
                                                            showIcon = true,
                                                            onConfirm = {
                                                                viewModel.resetBookProgress(book.id)
                                                                menuExpanded = false
                                                            }
                                                        )
                                                        AppDropdownMenuItem(
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

                    HomeSectionIds.NEWEST_AUTHORS -> {
                        item {
                            SectionTitle(
                                title = "Newest Authors",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                                )
                            } else {
                                LazyRow(
                                    modifier = homeCarouselModifier,
                                    contentPadding = homeCarouselContentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(newestAuthorsGap)
                                ) {
                                    items(authorNames, key = { it }) { author ->
                                        AuthorCircleChip(
                                            name = author,
                                            imageUrl = uiState.authorImageUrls[author.trim().lowercase()],
                                            chipWidth = newestAuthorsChipWidth,
                                            avatarSize = newestAuthorsAvatarSize,
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
                    Column(
                        modifier = Modifier.padding(start = homeStartInset, end = homeEndInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                    Column(
                        modifier = Modifier.padding(start = homeStartInset, end = homeEndInset),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
fun BrowseScreen(
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
                AppDropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false }
                ) {
                    BooksStatusFilter.entries.forEach { option ->
                        AppDropdownMenuItem(
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
                AppDropdownMenu(
                    expanded = optionsMenuExpanded,
                    onDismissRequest = { optionsMenuExpanded = false }
                ) {
                    AppDropdownMenuItem(
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
                    AppDropdownMenuItem(
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
                    AppDropdownMenuItem(
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
                        AppDropdownMenuItem(
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
                                            onResetProgress = { viewModel.resetBookProgress(entry.book.id) },
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
                                            onResetProgress = { viewModel.resetBookProgress(entry.leadBook.id) },
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
                                            onResetProgress = { viewModel.resetBookProgress(entry.book.id) },
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
                                            onResetProgress = { viewModel.resetBookProgress(entry.leadBook.id) },
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
    onResetProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.searchMetadataLabel()
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
                text = progressMetaLabel,
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
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text(downloadLabel) },
                        leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                        onClick = {
                            onToggleDownload()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
                        enabled = book.hasStartedProgress(),
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    ResetBookProgressMenuItem(
                        showIcon = true,
                        onConfirm = {
                            onResetProgress()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
    onResetProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    val book = entry.leadBook
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val layerCount = entry.count.coerceIn(2, 3)
    val frameWidth = StandardGridCoverWidth
    val frameHeight = StandardGridCoverHeight
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
            SeriesStackCoverLayers(
                coverUrls = listOfNotNull(book.coverUrl),
                contentDescription = entry.seriesName,
                layerCount = layerCount,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                modifier = Modifier
                    .width(frameWidth)
                    .height(frameHeight)
            )
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
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Add to Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = {
                            onAddToCollection()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
                        enabled = book.hasStartedProgress(),
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    ResetBookProgressMenuItem(
                        showIcon = true,
                        onConfirm = {
                            onResetProgress()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
    onResetProgress: () -> Unit,
    onToggleDownload: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val progressMetaLabel = book.searchMetadataLabel()
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
                text = progressMetaLabel,
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
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                AppDropdownMenuItem(
                    text = { Text(downloadLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = {
                        onToggleDownload()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
                    enabled = book.hasStartedProgress(),
                    onClick = {
                        onMarkFinished()
                        menuExpanded = false
                    }
                )
                ResetBookProgressMenuItem(
                    showIcon = true,
                    onConfirm = {
                        onResetProgress()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
    onResetProgress: () -> Unit,
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
            SeriesStackCoverLayers(
                coverUrls = entry.books
                    .asSequence()
                    .mapNotNull { it.coverUrl }
                    .toList(),
                contentDescription = entry.seriesName,
                layerCount = layerCount,
                frameWidth = frameSize,
                frameHeight = frameSize,
                modifier = Modifier.matchParentSize()
            )
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
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                AppDropdownMenuItem(
                    text = { Text("Add to Collection") },
                    leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    onClick = {
                        onAddToCollection()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
                    enabled = lead.hasStartedProgress(),
                    onClick = {
                        onMarkFinished()
                        menuExpanded = false
                    }
                )
                ResetBookProgressMenuItem(
                    showIcon = true,
                    onConfirm = {
                        onResetProgress()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
    viewModel: BookmarksBrowseViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarksSnackbarHostState = remember { SnackbarHostState() }
    var bookmarkMenuKey by remember { mutableStateOf<String?>(null) }
    var editingBookmarkEntry by remember { mutableStateOf<BookmarkEntry?>(null) }
    var editingBookmarkTitle by rememberSaveable { mutableStateOf("") }
    var deletingBookmarkEntry by remember { mutableStateOf<BookmarkEntry?>(null) }
    var confirmDeleteAllBookmarks by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        bookmarksSnackbarHostState.showSnackbar(message)
        viewModel.clearActionMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        BackTitle(
            title = "Bookmarks",
            onBackClick = onBackClick,
            onHomeClick = onHomeClick
        )
        if (uiState.bookmarks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { confirmDeleteAllBookmarks = true }
                ) {
                    Text("Delete all")
                }
            }
        }
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
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
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
                                Box {
                                    IconButton(
                                        onClick = {
                                            bookmarkMenuKey =
                                                "${item.book.id}:${item.bookmark.id}:${item.bookmark.createdAtMs ?: 0L}:${item.bookmark.timeSeconds ?: 0.0}"
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreHoriz,
                                            contentDescription = "Bookmark actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    AppDropdownMenu(
                                        expanded = bookmarkMenuKey ==
                                            "${item.book.id}:${item.bookmark.id}:${item.bookmark.createdAtMs ?: 0L}:${item.bookmark.timeSeconds ?: 0.0}",
                                        onDismissRequest = { bookmarkMenuKey = null }
                                    ) {
                                        AppDropdownMenuItem(
                                            text = { Text("Edit title") },
                                            onClick = {
                                                bookmarkMenuKey = null
                                                editingBookmarkEntry = item
                                                editingBookmarkTitle = item.bookmark.title.orEmpty()
                                            }
                                        )
                                        AppDropdownMenuItem(
                                            text = { Text("Delete bookmark") },
                                            onClick = {
                                                bookmarkMenuKey = null
                                                deletingBookmarkEntry = item
                                            }
                                        )
                                    }
                                }
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

            SnackbarHost(
                hostState = bookmarksSnackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )
        }
    }

    editingBookmarkEntry?.let { targetEntry ->
        AlertDialog(
            onDismissRequest = { editingBookmarkEntry = null },
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
                        viewModel.editBookmark(targetEntry, editingBookmarkTitle)
                        editingBookmarkEntry = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmarkEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingBookmarkEntry?.let { targetEntry ->
        AlertDialog(
            onDismissRequest = { deletingBookmarkEntry = null },
            title = { Text("Delete bookmark") },
            text = { Text("Remove this bookmark?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBookmark(targetEntry)
                        deletingBookmarkEntry = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBookmarkEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmDeleteAllBookmarks) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllBookmarks = false },
            title = { Text("Delete all bookmarks") },
            text = { Text("Remove all bookmarks?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllBookmarks()
                        confirmDeleteAllBookmarks = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllBookmarks = false }) {
                    Text("Cancel")
                }
            }
        )
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
                                        AppDropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            AppDropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    menuExpanded = false
                                                    nameInput = collection.name
                                                    renameTarget = collection
                                                }
                                            )
                                            AppDropdownMenuItem(
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
                                        AppDropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false }
                                        ) {
                                            AppDropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    menuExpanded = false
                                                    nameInput = playlist.name
                                                    renameTarget = playlist
                                                }
                                            )
                                            AppDropdownMenuItem(
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SeriesBrowseScreen(
    onSeriesClick: (String, String?) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    viewModel: SeriesBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            when {
                uiState.errorMessage != null && !uiState.isRefreshing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "No Series found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
                        }
                    }
                }

                uiState.series.isNotEmpty() -> {
                    if (uiState.gridMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            gridItems(uiState.series, key = { it.id }) { series ->
                                Column(
                                    modifier = Modifier.clickable { onSeriesClick(series.name, series.id) },
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
                                        SeriesStackCoverLayers(
                                            coverUrls = series.coverUrls,
                                            contentDescription = series.name,
                                            layerCount = layerCount,
                                            frameWidth = StandardGridCoverWidth,
                                            frameHeight = StandardGridCoverHeight,
                                            modifier = Modifier
                                                .width(StandardGridCoverWidth)
                                                .height(StandardGridCoverHeight)
                                        )
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                        .clickable { onSeriesClick(series.name, series.id) }
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
                                        SeriesStackCoverLayers(
                                            coverUrls = series.coverUrls,
                                            contentDescription = series.name,
                                            layerCount = layerCount,
                                            frameWidth = frameWidth,
                                            frameHeight = frameHeight,
                                            modifier = Modifier.matchParentSize()
                                        )
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

                uiState.isLoading -> {
                    Text(
                        text = "Loading Series...",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Series found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = viewModel::refresh) {
                            Text("Retry")
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
fun BrowseSectionScreen(
    title: String,
    emptyMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.GridView,
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
fun SearchScreen(
    onBookClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onNarratorClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    var collectionPickerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val query = uiState.query.trim()
    val normalized = query.lowercase()
    val matchedBooks = uiState.books
    val matchedAuthors = uiState.authors
    val matchedSeries = uiState.series
    val matchedNarrators = uiState.narrators
    val recentSearchTerms = uiState.recentSearchTerms
    val noMatches = normalized.isNotBlank() &&
        !uiState.isLoading &&
        uiState.errorMessage.isNullOrBlank() &&
        matchedBooks.isEmpty() &&
        matchedAuthors.isEmpty() &&
        matchedSeries.isEmpty() &&
        matchedNarrators.isEmpty()

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
            .padding(horizontal = AppScreenHorizontalPadding)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 14.dp)
        ) {
            if (normalized.isBlank()) {
                if (recentSearchTerms.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recently searched",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearRecentSearchTerms) {
                                    Text("Clear")
                                }
                            }
                        }
                        items(recentSearchTerms, key = { it.lowercase() }) { term ->
                            SearchRecentRow(
                                text = term,
                                onClick = {
                                    viewModel.useRecentSearchTerm(term)
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                } else {
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
                                isDownloaded = uiState.downloadedBookIds.contains(book.id),
                                downloadProgressPercent = uiState.downloadProgressByBookId[book.id],
                                onAddToCollection = { collectionPickerBookId = book.id },
                                onMarkFinished = {
                                    if (book.hasFinishedProgress()) {
                                        viewModel.markAsUnfinished(book.id)
                                    } else {
                                        viewModel.markAsFinished(book.id)
                                    }
                                },
                                onToggleDownload = { viewModel.toggleDownload(book.id) },
                                onClick = {
                                    viewModel.commitCurrentQuery()
                                    onBookClick(book.id)
                                }
                            )
                        }
                    }
                    if (matchedAuthors.isNotEmpty()) {
                        item { SectionTitle("Authors") }
                        items(matchedAuthors, key = { it.id }) { author ->
                            SearchEntityRow(
                                text = author.name,
                                onClick = {
                                    viewModel.commitCurrentQuery()
                                    onAuthorClick(author.name)
                                }
                            )
                        }
                    }
                    if (matchedSeries.isNotEmpty()) {
                        item { SectionTitle("Series") }
                        items(matchedSeries, key = { it.id }) { series ->
                            SearchEntityRow(
                                text = series.name,
                                onClick = {
                                    viewModel.commitCurrentQuery()
                                    onSeriesClick(series.name)
                                }
                            )
                        }
                    }
                    if (matchedNarrators.isNotEmpty()) {
                        item { SectionTitle("Narrators") }
                        items(matchedNarrators, key = { it.id }) { narrator ->
                            SearchEntityRow(
                                text = narrator.name,
                                onClick = {
                                    viewModel.commitCurrentQuery()
                                    onNarratorClick(narrator.name)
                                }
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.commitCurrentQuery()
                        keyboardController?.hide()
                    }
                ),
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
private fun SearchRecentRow(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
@Composable
private fun SearchBookRow(
    book: BookSummary,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleDownload: () -> Unit,
    onClick: () -> Unit
) {
    val metadataLabel = book.searchMetadataLabel()
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
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
            backgroundBlur = 44.dp,
            showDownloadIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent,
            downloadBadgeSize = 22.dp
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
            if (metadataLabel.isNotBlank()) {
                Text(
                    text = metadataLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
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
                    text = { Text("Add to Collection") },
                    leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                    onClick = {
                        onAddToCollection()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
                    enabled = book.hasStartedProgress(),
                    onClick = {
                        onMarkFinished()
                        menuExpanded = false
                    }
                )
                AppDropdownMenuItem(
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
fun DownloadsScreen() {
    DownloadsScreen(onBackClick = null, onHomeClick = null, onBookClick = {})
}

@Composable
fun DownloadsScreen(
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f))
                                .clickable { onBookClick(book.id) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
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
                                    text = book.searchMetadataLabel(),
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
                                AppDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Go to book") },
                                        onClick = {
                                            menuExpanded = false
                                            onBookClick(book.id)
                                        }
                                    )
                                    ResetBookProgressMenuItem(
                                        onConfirm = {
                                            menuExpanded = false
                                            viewModel.resetBookProgress(book.id)
                                        }
                                    )
                                    AppDropdownMenuItem(
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
                                    text = book.searchMetadataLabel(),
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
                                    AppDropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        AppDropdownMenuItem(
                                            text = { Text("Go to book") },
                                            onClick = {
                                                menuExpanded = false
                                                onBookClick(book.id)
                                            }
                                        )
                                        ResetBookProgressMenuItem(
                                            onConfirm = {
                                                menuExpanded = false
                                                viewModel.resetBookProgress(book.id)
                                            }
                                        )
                                        AppDropdownMenuItem(
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
fun SettingsScreen(
    onManageServers: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selected ->
        viewModel.setServerAvatarFromUri(selected)
    }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var skipForwardDialogVisible by remember { mutableStateOf(false) }
    var skipBackwardDialogVisible by remember { mutableStateOf(false) }
    var signOutDialogVisible by remember { mutableStateOf(false) }
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
    val lastSyncedValue = uiState.lastLibrarySyncAtMs
        ?.let { timestamp -> "Last synced ${formatLastSyncedTimestamp(timestamp)}" }

    LaunchedEffect(uiState.syncToastMessage) {
        val toastMessage = uiState.syncToastMessage ?: return@LaunchedEffect
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        viewModel.consumeSyncToastMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 18.dp),
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
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                            shape = CircleShape
                        )
                        .clickable { avatarPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUri = uiState.serverAvatarUri
                    if (!avatarUri.isNullOrBlank()) {
                        val avatarRequest = remember(avatarUri, context) {
                            val localFile = if (!avatarUri.contains("://")) {
                                File(avatarUri).takeIf { it.exists() }
                            } else {
                                null
                            }
                            val cacheKey = localFile?.let { file ->
                                "${file.absolutePath}:${file.length()}:${file.lastModified()}"
                            }
                            ImageRequest.Builder(context).apply {
                                cacheKey?.let {
                                    memoryCacheKey(it)
                                    diskCacheKey(it)
                                }
                            }
                                .data(localFile ?: avatarUri)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = avatarRequest,
                            contentDescription = "Server profile photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Add server profile photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(uiState.serverDisplayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.serverHost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (uiState.serverAvatarUri.isNullOrBlank()) {
                                "Tap photo to add"
                            } else {
                                "Tap photo to change"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!uiState.serverAvatarUri.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.clearServerAvatar()
                                    infoMessage = "Profile photo removed."
                                },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RemoveCircleOutline,
                                    contentDescription = "Remove server profile photo",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Remove")
                            }
                        }
                    }
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

        if (uiState.appUpdatesEnabled) {
            Text(
                text = "APP UPDATES",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = sectionCardColor),
                shape = RoundedCornerShape(18.dp),
                border = sectionCardBorder
            ) {
                SettingsRow(
                    title = "Check for Updates",
                    showChevronWhenUnselected = false,
                    onClick = {
                        infoMessage = null
                        if (!uiState.isCheckingForUpdates) {
                            viewModel.onCheckForUpdatesClick()
                        }
                    }
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Check on Startup",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.updateCheckOnStartupEnabled,
                        onCheckedChange = viewModel::setUpdateCheckOnStartupEnabled
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Include Pre-releases",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.includePrereleaseUpdates,
                        onCheckedChange = viewModel::setIncludePrereleaseUpdates
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = sectionCardColor),
            shape = RoundedCornerShape(18.dp),
            border = sectionCardBorder
        ) {
            SettingsRow(title = "Manage Servers", onClick = onManageServers)
            HorizontalDivider()
            SettingsRow(
                title = "Sync Libraries",
                value = lastSyncedValue,
                showChevronWhenValue = false,
                showChevronWhenUnselected = false,
                onClick = {
                    infoMessage = null
                    viewModel.onSyncLibrariesClick()
                }
            )
            HorizontalDivider()
            SettingsRow(
                title = "Sign Out",
                onClick = {
                    infoMessage = null
                    signOutDialogVisible = true
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
                onClick = onOpenAbout
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

        if (signOutDialogVisible) {
            AlertDialog(
                onDismissRequest = { signOutDialogVisible = false },
                title = { Text("Sign out of server?") },
                text = {
                    Text(
                        text = "You will be signed out from ${uiState.serverDisplayName}."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            signOutDialogVisible = false
                            viewModel.onSignOutClick()
                        }
                    ) {
                        Text("Sign Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { signOutDialogVisible = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (uiState.appUpdatesEnabled) uiState.availableUpdate?.let { release ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAvailableUpdateDialog,
                title = { Text("Update available") },
                text = {
                    UpdateNotesDialogContent(
                        versionName = release.versionName,
                        notes = release.body
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::installAvailableUpdate) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissAvailableUpdateDialog) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun AboutScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showReleaseHistory by rememberSaveable { mutableStateOf(false) }
    val openUrl: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
            .onFailure {
                Toast.makeText(context, "Unable to open link.", Toast.LENGTH_SHORT).show()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BackTitle(
            title = "About",
            onBackClick = onBackClick
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = uiState.appName,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = uiState.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.originStory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "v${uiState.versionName} (build ${uiState.versionCode})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Links", style = MaterialTheme.typography.titleMedium)
                AboutLinkRow(label = "Website", url = uiState.websiteUrl, onClick = { openUrl(uiState.websiteUrl) })
                HorizontalDivider()
                AboutLinkRow(label = "Support", url = uiState.supportUrl, onClick = { openUrl(uiState.supportUrl) })
                HorizontalDivider()
                AboutLinkRow(label = "Privacy", url = uiState.privacyUrl, onClick = { openUrl(uiState.privacyUrl) })
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Acknowledgements", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = uiState.acknowledgements,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openUrl(uiState.sourceUrl) }) {
                        Text("Source Code")
                    }
                    TextButton(onClick = { openUrl(uiState.licenseUrl) }) {
                        Text("License")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Release Notes", style = MaterialTheme.typography.titleMedium)
                uiState.currentRelease?.let { entry ->
                    AboutReleaseItem(
                        title = "Current build: ${entry.tagName}",
                        subtitle = entry.publishedDate?.let { "Published $it" },
                        notes = entry.notes
                    )
                }
                if (uiState.isLoadingReleaseNotes) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Loading release history...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.releaseNotesError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = viewModel::refreshReleaseNotes) {
                        Text("Retry")
                    }
                }
                if (uiState.releaseHistory.isNotEmpty()) {
                    TextButton(onClick = { showReleaseHistory = !showReleaseHistory }) {
                        Text(if (showReleaseHistory) "Hide history" else "Show history")
                    }
                    if (showReleaseHistory) {
                        uiState.releaseHistory.take(6).forEach { entry ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f))
                            AboutReleaseItem(
                                title = entry.tagName + if (entry.prerelease) " (prerelease)" else "",
                                subtitle = entry.publishedDate?.let { "Published $it" },
                                notes = entry.notes
                            )
                        }
                    }
                }
                TextButton(onClick = { openUrl(uiState.releasePageUrl) }) {
                    Text("View all releases")
                }
            }
        }
    }
}

@Composable
private fun AboutReleaseItem(
    title: String,
    subtitle: String?,
    notes: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = notes?.takeIf { it.isNotBlank() } ?: "No notes provided.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutLinkRow(
    label: String,
    url: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                ),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    selected: Boolean = false,
    showChevronWhenValue: Boolean = true,
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
            if (showChevronWhenValue) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

private fun formatLastSyncedTimestamp(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(timestampMs))
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 18.dp),
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
                            AppDropdownMenu(
                                expanded = expandedServerMenuId == server.id,
                                onDismissRequest = { expandedServerMenuId = null }
                            ) {
                                AppDropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        expandedServerMenuId = null
                                        editingServer = server
                                        editingName = server.name
                                        editingUrl = server.baseUrl
                                        editingError = null
                                    }
                                )
                                AppDropdownMenuItem(
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collectionPickerUiState by collectionPickerViewModel.uiState.collectAsStateWithLifecycle()
    val playbackUiState by viewModel.playbackUiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    var actionsExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    var addToListBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var bookmarkMenuId by remember { mutableStateOf<String?>(null) }
    var editingDetailBookmark by remember { mutableStateOf<BookBookmark?>(null) }
    var editingDetailBookmarkTitle by rememberSaveable { mutableStateOf("") }
    var deletingDetailBookmark by remember { mutableStateOf<BookBookmark?>(null) }
    val detailSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(viewModel, detailSnackbarHostState) {
        viewModel.markFinishedUndoEvents.collect {
            val result = detailSnackbarHostState.showSnackbar(
                message = "Marked as finished",
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoMarkAsFinished()
            }
        }
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
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onScreenStarted()
        }
    }
    val detailBook = uiState.detail?.book
    var savedIsSquareLikeDetailCover by rememberSaveable(detailBook?.coverUrl) {
        mutableStateOf<Boolean?>(null)
    }
    val isPlayingDetailBookNow = detailBook != null && playbackUiState.book?.id == detailBook.id
    val detailDurationSeconds = when {
        detailBook?.durationSeconds != null && detailBook.durationSeconds > 0.0 -> detailBook.durationSeconds
        isPlayingDetailBookNow && playbackUiState.durationMs > 0L -> playbackUiState.durationMs / 1000.0
        else -> null
    }
    val detailProgressPercent = (uiState.progressPercent ?: 0.0).coerceIn(0.0, 1.0)
    val detailChapters = uiState.detail?.chapters.orEmpty()
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
        isPlayingDetailBookNow &&
            resolvedDetailProgress < 0.995 &&
            (detailCurrentSeconds ?: 0.0) > 0.5 -> false
        else -> (detailBook?.isFinished == true) || finishedFromDetailProgress
    }
    val canToggleDetailFinished = hasMeaningfulStartedProgress(
        currentTimeSeconds = detailCurrentSeconds,
        durationSeconds = detailDurationSeconds,
        progressPercent = resolvedDetailProgress,
        isFinished = effectiveDetailFinished
    )
    val detailActiveChapterIndex = detailCurrentSeconds
        ?.let { position -> findActiveChapterIndex(detailChapters, position) }
        ?: -1

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = AppScreenHorizontalPadding,
                    end = AppScreenHorizontalPadding,
                    top = 2.dp,
                    bottom = 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
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
                    AppDropdownMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false }
                    ) {
                        AppDropdownMenuItem(
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
                        AppDropdownMenuItem(
                            text = { Text("Skip Intro & Outro") },
                            onClick = {
                                viewModel.skipIntroOrOutro()
                                actionsExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = {
                                Text(
                                    if (effectiveDetailFinished) {
                                        "Mark as Unfinished"
                                    } else {
                                        "Mark as Finished"
                                    }
                                )
                            },
                            enabled = canToggleDetailFinished,
                            onClick = {
                                if (effectiveDetailFinished) {
                                    viewModel.markAsUnfinished()
                                } else {
                                    viewModel.markAsFinished()
                                }
                                actionsExpanded = false
                            }
                        )
                        ResetBookProgressMenuItem(
                            onConfirm = {
                                viewModel.resetBookProgress()
                                actionsExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
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
                val effectiveDurationSeconds = when {
                    book.durationSeconds != null && book.durationSeconds > 0.0 -> book.durationSeconds
                    playbackUiState.book?.id == book.id && playbackUiState.durationMs > 0L -> {
                        playbackUiState.durationMs / 1000.0
                    }
                    else -> null
                }
                val hasProgress = hasMeaningfulStartedProgress(
                    currentTimeSeconds = when {
                        livePlaybackSeconds > 0.0 -> livePlaybackSeconds
                        else -> serverPlaybackSeconds.takeIf { it > 0.0 }
                    },
                    durationSeconds = effectiveDurationSeconds,
                    progressPercent = uiState.progressPercent,
                    isFinished = effectiveBookFinished
                )
                val listenLabel = resolveListenActionLabel(
                    isFinished = effectiveBookFinished,
                    hasProgress = hasProgress
                )
                val listenProgressFraction = if (effectiveBookFinished) {
                    0f
                } else if (!hasProgress) {
                    0f
                } else {
                    run {
                        val duration = effectiveDurationSeconds
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
                }
                val activeChapterIndex = detailActiveChapterIndex
                val chapterPositionSeconds = detailCurrentSeconds
                val seriesOrderLabel = resolveSeriesOrderLabel(
                    seriesSequence = book.seriesSequence,
                    book.title,
                    detail.chapters.firstOrNull()?.title
                )

                item {
                    val detailCoverModel = rememberCoverImageModel(book.coverUrl)
                    val detailCoverPainter = rememberAsyncImagePainter(model = detailCoverModel)
                    val detailCoverSuccessState = detailCoverPainter.state as? AsyncImagePainter.State.Success
                    val detailCoverIntrinsicWidth = detailCoverSuccessState?.result?.drawable?.intrinsicWidth?.takeIf { it > 0 }
                    val detailCoverIntrinsicHeight = detailCoverSuccessState?.result?.drawable?.intrinsicHeight?.takeIf { it > 0 }
                    val detailCoverAspectRatio = if (detailCoverIntrinsicWidth != null && detailCoverIntrinsicHeight != null) {
                        detailCoverIntrinsicWidth.toFloat() / detailCoverIntrinsicHeight.toFloat()
                    } else {
                        0.66f
                    }
                    LaunchedEffect(detailCoverIntrinsicWidth, detailCoverIntrinsicHeight) {
                        if (detailCoverIntrinsicWidth != null && detailCoverIntrinsicHeight != null) {
                            savedIsSquareLikeDetailCover = detailCoverAspectRatio in 0.97f..1.03f
                        }
                    }
                    val isSquareLikeDetailCover = savedIsSquareLikeDetailCover ?: (detailCoverAspectRatio in 0.97f..1.03f)
                    val detailCoverWidth = 240.dp
                    val detailFrameWidth = if (isSquareLikeDetailCover) detailCoverWidth else 276.dp
                    val detailCoverSlotHeight = if (isSquareLikeDetailCover) 240.dp else 296.dp
                    val detailCoverHeight = if (isSquareLikeDetailCover) 224.dp else 272.dp
                    val detailCoverContentScale = if (isSquareLikeDetailCover) ContentScale.Crop else ContentScale.Fit
                    val detailForcedFrameSideInset = if (isSquareLikeDetailCover) null else 36.dp
                    val detailSeriesBadgeYOffset = if (isSquareLikeDetailCover) 4.dp else (-4).dp
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Keep this painter in composition so square-cover detection updates once loaded.
                        Image(
                            painter = detailCoverPainter,
                            contentDescription = null,
                            modifier = Modifier
                                .size(1.dp)
                                .alpha(0f)
                        )
                        Box(
                            modifier = Modifier
                                .width(detailCoverWidth)
                                .height(detailCoverSlotHeight)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(detailFrameWidth)
                                    .height(detailCoverHeight)
                            ) {
                                BookPoster(
                                    book = book,
                                    width = detailFrameWidth,
                                    height = detailCoverHeight,
                                    fillMaxWidth = true,
                                    shape = RoundedCornerShape(10.dp),
                                    contentScale = detailCoverContentScale,
                                    limitInsetToAvoidVerticalLetterbox = true,
                                    forcedFrameSideInset = detailForcedFrameSideInset,
                                    disableBlurredFrame = false,
                                    showDownloadIndicator = uiState.downloadedBookIds.contains(book.id),
                                    downloadProgressPercent = uiState.downloadProgressPercent
                                )
                            }
                            seriesOrderLabel?.let { order ->
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = (-4).dp, y = detailSeriesBadgeYOffset)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    BookListenProgressButton(
                        text = listenLabel,
                        progress = if (hasProgress) listenProgressFraction else 0f,
                        materialDesignEnabled = appearanceUiState.materialDesignEnabled,
                        onClick = { onStartListening(book.id) },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DetailTabChip(
                            title = "About",
                            selected = uiState.selectedTab == "About",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setSelectedTab("About") }
                        )
                        DetailTabChip(
                            title = "Chapters",
                            selected = uiState.selectedTab == "Chapters",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setSelectedTab("Chapters") }
                        )
                        DetailTabChip(
                            title = "Bookmarks",
                            selected = uiState.selectedTab == "Bookmarks",
                            modifier = Modifier.weight(1f),
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
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
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
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f)
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
                                            AppDropdownMenu(
                                                expanded = bookmarkMenuId == bookmark.id,
                                                onDismissRequest = { bookmarkMenuId = null }
                                            ) {
                                                AppDropdownMenuItem(
                                                    text = { Text("Edit title") },
                                                    onClick = {
                                                        bookmarkMenuId = null
                                                        editingDetailBookmark = bookmark
                                                        editingDetailBookmarkTitle = bookmark.title.orEmpty()
                                                    }
                                                )
                                                AppDropdownMenuItem(
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
        if (uiState.isRefreshing && uiState.detail != null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
        SnackbarHost(
            hostState = detailSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 84.dp)
        )
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
fun PlayerScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    onGoToBook: ((String) -> Unit)? = null,
    collectionPickerViewModel: CollectionPickerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val downloadToolText = if (hasActivePlayerDownload) {
        "${activeDownloadProgressPercent}%"
    } else if (isBookDownloaded) {
        "Remove"
    } else {
        "Download"
    }
    LaunchedEffect(lifecycleOwner, bookId) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshBookMetadata()
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                viewModel.refreshBookMetadata()
            }
        }
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
        when {
            playbackUiState.book?.id == book.id &&
                progress < 0.995f &&
                positionSeconds > 0.5 -> false
            else -> finishedFromPlayback || book.hasFinishedProgress()
        }
    } else {
        false
    }
    val canTogglePlayerFinished = hasMeaningfulStartedProgress(
        currentTimeSeconds = positionSeconds,
        durationSeconds = durationSeconds.takeIf { it > 0.0 } ?: book?.durationSeconds,
        progressPercent = if (durationSeconds > 0.0) {
            (positionSeconds / durationSeconds).coerceIn(0.0, 1.0)
        } else {
            book?.progressPercent
        },
        isFinished = effectivePlayerFinished
    )
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
    val bottomToolsStyle = remember(appearanceUiState.playerBottomToolsStyle) {
        PlayerBottomToolsStyle.fromPreferenceValue(appearanceUiState.playerBottomToolsStyle)
    }
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
    val playerSnackbarHostState = remember { SnackbarHostState() }
    val speedLabel = formatPlaybackSpeedShort(playbackUiState.playbackSpeed)
    val speedToolValue = speedLabel
    val timerMode = playbackUiState.sleepTimerMode
    val timerRemainingMs = playbackUiState.sleepTimerRemainingMs?.coerceAtLeast(0L) ?: 0L
    val timerTotalMs = (playbackUiState.sleepTimerTotalMs ?: timerRemainingMs)
        .coerceAtLeast(if (timerMode != SleepTimerMode.Off) 1_000L else 0L)
    val timerIsActive = timerMode != SleepTimerMode.Off
    val timerLabel = if (timerIsActive) {
        if (timerRemainingMs > 0L) formatTimerChipLabel(timerRemainingMs) else "On"
    } else {
        null
    }
    val timerToolValue = if (timerLabel != null) "Timer $timerLabel" else "Timer"
    val selectedOutput = playbackUiState.outputDevices.firstOrNull { it.id == playbackUiState.selectedOutputDeviceId }
    val outputLabel = selectedOutput?.let { device ->
        if (device.typeLabel.equals("Phone speaker", ignoreCase = true)) {
            "Phone"
        } else {
            device.typeLabel
        }
    } ?: "Audio"
    val outputIcon = playerOutputToolIcon(selectedOutput?.typeLabel)
    val speedToolHighlighted = kotlin.math.abs(playbackUiState.playbackSpeed - 1.0f) >= 0.01f
    val bottomToolsBaseColor = if (immersiveEnabled) {
        // Keep immersive controls consistently dark/translucent across light and dark app themes.
        lerp(MaterialTheme.colorScheme.surface, Color.Black, 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val isMaterialNonImmersive = appearanceUiState.materialDesignEnabled && !immersiveEnabled
    val materialProgressAccent = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        // Light themes: deepen accent so the timeline reads as a strong "marker" stroke.
        lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.14f)
    } else {
        // Dark themes: lift accent so it still pops on dark surfaces.
        lerp(MaterialTheme.colorScheme.primary, Color.White, 0.2f)
    }
    val progressActiveColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.92f)
    } else if (appearanceUiState.materialDesignEnabled) {
        materialProgressAccent
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val menuItemTextColor = MaterialTheme.colorScheme.onSurface
    val bottomToolsPrimaryColor = if (immersiveEnabled) {
        Color.White
    } else if (isMaterialNonImmersive) {
        menuItemTextColor
    } else if (bottomToolsBaseColor.luminance() > 0.52f) {
        Color(0xFF1C1C1C)
    } else {
        Color(0xFFF5F5F5)
    }
    val bottomToolsSecondaryColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.72f)
    } else if (isMaterialNonImmersive) {
        menuItemTextColor
    } else if (bottomToolsBaseColor.luminance() > 0.52f) {
        Color(0xFF666666)
    } else {
        Color(0xFFCBCBCB)
    }
    val bottomToolsBorderColor = if (bottomToolsBaseColor.luminance() > 0.52f) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.2f)
    }
    val immersiveDockContainerColor = bottomToolsBaseColor.copy(alpha = 0.24f)
    val immersiveDockBorderColor = bottomToolsBorderColor.copy(alpha = 0.4f)
    val useFloatingChipsTools = bottomToolsStyle == PlayerBottomToolsStyle.FloatingChips
    val useSlimStripTools = bottomToolsStyle == PlayerBottomToolsStyle.SlimStrip
    val menuLikeContainerColor = if (appearanceUiState.materialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface.copy(
            alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
        )
    }
    val menuLikeBorderColor = if (appearanceUiState.materialDesignEnabled) {
        Color.Transparent
    } else if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }
    val nonMaterialDockMenuMatch = !appearanceUiState.materialDesignEnabled && !immersiveEnabled && useSlimStripTools
    val nonMaterialMenuLikeContainer = MaterialTheme.colorScheme.surface.copy(
        alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
    )
    val nonImmersiveMaterialPlayerContainer = MaterialTheme.colorScheme.surfaceVariant
    val nonMaterialMenuLikeBorder = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }
    val toolsOuterContainerColor = when {
        useFloatingChipsTools -> Color.Transparent
        useSlimStripTools -> if (nonMaterialDockMenuMatch) {
            nonMaterialMenuLikeContainer
        } else {
            if (immersiveEnabled) {
                immersiveDockContainerColor
            } else if (appearanceUiState.materialDesignEnabled) {
                // Match dock background to main play/pause button container in Material mode.
                nonImmersiveMaterialPlayerContainer
            } else {
                bottomToolsBaseColor.copy(alpha = 0.28f)
            }
        }
        else -> Color.Transparent
    }
    val toolsOuterBorderColor = when {
        useFloatingChipsTools -> Color.Transparent
        useSlimStripTools -> if (nonMaterialDockMenuMatch) {
            nonMaterialMenuLikeBorder
        } else {
            if (isMaterialNonImmersive) progressActiveColor else immersiveDockBorderColor
        }
        else -> Color.Transparent
    }
    val toolsItemContainerColor = when {
        useFloatingChipsTools -> if (immersiveEnabled) {
            immersiveDockContainerColor
        } else if (appearanceUiState.materialDesignEnabled) {
            nonImmersiveMaterialPlayerContainer
        } else {
            menuLikeContainerColor
        }
        useSlimStripTools -> Color.Transparent
        else -> Color.Transparent
    }
    val toolsItemBorderColor = when {
        useFloatingChipsTools -> if (immersiveEnabled) {
            immersiveDockBorderColor
        } else if (isMaterialNonImmersive) {
            progressActiveColor
        } else {
            menuLikeBorderColor
        }
        useSlimStripTools -> Color.Transparent
        else -> Color.Transparent
    }
    val toolsRowHorizontalPadding = when {
        useFloatingChipsTools -> 6.dp
        useSlimStripTools -> 6.dp
        else -> 0.dp
    }
    val toolsRowVerticalPadding = when {
        useFloatingChipsTools -> 1.dp
        useSlimStripTools -> 1.dp
        else -> 0.dp
    }
    val toolsRowSpacing = when {
        useFloatingChipsTools -> 2.dp
        useSlimStripTools -> 2.dp
        else -> 4.dp
    }
    val toolsShowIconBubble = useFloatingChipsTools
    val toolsItemHeight = when {
        useFloatingChipsTools -> 56.dp
        useSlimStripTools -> 56.dp
        else -> 64.dp
    }
    val mainPlayButtonContainer = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.96f)
    } else if (!appearanceUiState.materialDesignEnabled) {
        nonMaterialMenuLikeContainer
    } else {
        nonImmersiveMaterialPlayerContainer
    }
    val mainPlayButtonBorderColor = when {
        immersiveEnabled -> Color.Transparent
        !appearanceUiState.materialDesignEnabled -> nonMaterialMenuLikeBorder
        bottomToolsStyle == PlayerBottomToolsStyle.Flat -> Color.Transparent
        isMaterialNonImmersive -> progressActiveColor
        else -> immersiveDockBorderColor
    }
    val mainPlayButtonIconTint = if (immersiveEnabled) {
        Color.Black
    } else if (mainPlayButtonContainer.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
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
    val seekControlTint = if (isMaterialNonImmersive) progressActiveColor else primaryTextColor
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val effectiveHeightDp = (configuration.screenHeightDp.toFloat() / density.fontScale).coerceAtLeast(520f)
    val playerHorizontalPadding = (effectiveHeightDp * 0.025f).dp.coerceIn(14.dp, 20.dp)
    val playerVerticalPadding = (effectiveHeightDp * 0.015f).dp.coerceIn(8.dp, 16.dp)
    val coverTopGap = (effectiveHeightDp * 0.03f).dp.coerceIn(16.dp, 24.dp)
    val coverTitleGap = (effectiveHeightDp * 0.012f).dp.coerceIn(6.dp, 10.dp)
    val titleProgressGap = (effectiveHeightDp * 0.012f).dp.coerceIn(6.dp, 10.dp)
    val titleRowMinHeight = 44.dp
    val coverTimerBadgeBottomPadding = (effectiveHeightDp * 0.02f).dp.coerceIn(2.dp, 4.dp)
    val titleSectionHeight = titleRowMinHeight + coverTitleGap + titleProgressGap
    val titleRowVisualOffset = 8.dp
    val progressMetaGap = 3.dp
    val coverTargetWidth = (effectiveHeightDp * 0.42f).dp.coerceIn(286.dp, 332.dp)
    val controlsRowPadding = (effectiveHeightDp * 0.04f).dp.coerceIn(24.dp, 38.dp)
    val bottomToolsTopPadding = (effectiveHeightDp * 0.008f).dp.coerceIn(4.dp, 8.dp)
    val bottomToolsBottomPadding = (effectiveHeightDp * 0.004f).dp.coerceIn(1.dp, 4.dp)
    val playerTopOffset = (effectiveHeightDp * 0.02f).dp.coerceIn(8.dp, 16.dp)
    val controlsAreaMinHeight = (effectiveHeightDp * 0.17f).dp.coerceIn(116.dp, 148.dp)

    LaunchedEffect(actionMessage) {
        val latest = actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, latest, Toast.LENGTH_SHORT).show()
        viewModel.clearActionMessage()
    }
    LaunchedEffect(viewModel, playerSnackbarHostState) {
        viewModel.markFinishedUndoEvents.collect {
            val result = playerSnackbarHostState.showSnackbar(
                message = "Marked as finished",
                actionLabel = "Undo",
                withDismissAction = true
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoMarkAsFinished()
            }
        }
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
                                0.00f to Color.Black.copy(alpha = 0.74f),
                                0.24f to Color.Black.copy(alpha = 0.66f),
                                0.58f to Color.Black.copy(alpha = 0.60f),
                                1.00f to Color.Black.copy(alpha = 0.72f)
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
                subtitle = "Choose a book to start."
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = coverTimerBadgeBottomPadding)
                    ) {
                        PlayerTimerRunningBadge(
                            remainingMs = timerRemainingMs,
                            totalMs = timerTotalMs,
                            useAccentBackground = false,
                            materialDesignEnabled = appearanceUiState.materialDesignEnabled
                        )
                    }
                }
            }
        }
        val playerTitleRow: @Composable () -> Unit = {
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
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleSectionHeight),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.offset(y = titleRowVisualOffset)) {
                playerTitleRow()
            }
        }
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
        val progressMetaTextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
        val timeTextStyle = progressMetaTextStyle.copy(fontFamily = FontFamily.Monospace)
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
                style = progressMetaTextStyle,
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
                .weight(1f)
                .heightIn(min = controlsAreaMinHeight),
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
                    tint = seekControlTint,
                    isImmersive = immersiveEnabled,
                    onClick = viewModel::onRewindClick
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mainPlayButtonContainer)
                        .border(
                            width = if (mainPlayButtonBorderColor.alpha > 0f) 1.dp else 0.dp,
                            color = mainPlayButtonBorderColor,
                            shape = CircleShape
                        )
                        .clickable(
                            enabled = !playbackUiState.isLoading,
                            onClick = viewModel::onPlayPauseClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val mainPlayButtonIcon = if (playbackUiState.isPlaying) {
                        Icons.Outlined.Pause
                    } else {
                        if (immersiveEnabled || isMaterialNonImmersive) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.Outlined.PlayArrow
                        }
                    }
                    val mainPlayIconTint = if (!playbackUiState.isPlaying && isMaterialNonImmersive) {
                        progressActiveColor
                    } else {
                        mainPlayButtonIconTint
                    }
                    if (playbackUiState.isLoading) {
                        MainPlayButtonLoadingIndicator(
                            modifier = Modifier.size(width = 36.dp, height = 36.dp),
                            baseTint = if (mainPlayButtonContainer.luminance() > 0.5f) {
                                Color.Black.copy(alpha = 0.24f)
                            } else {
                                Color.White.copy(alpha = 0.28f)
                            },
                            sweepTint = if (mainPlayButtonContainer.luminance() > 0.5f) {
                                Color.Black.copy(alpha = 0.95f)
                            } else {
                                Color.White.copy(alpha = 0.95f)
                            }
                        )
                    } else {
                        Icon(
                            imageVector = mainPlayButtonIcon,
                            contentDescription = if (playbackUiState.isPlaying) "Pause" else "Play",
                            tint = mainPlayIconTint,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Seek15Button(
                    forward = true,
                    seconds = controlPrefs.skipForwardSeconds,
                    tint = seekControlTint,
                    isImmersive = immersiveEnabled,
                    onClick = viewModel::onForwardClick
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = bottomToolsTopPadding, bottom = bottomToolsBottomPadding),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = toolsOuterContainerColor),
                border = BorderStroke(1.dp, toolsOuterBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = toolsRowHorizontalPadding, vertical = toolsRowVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(toolsRowSpacing)
                ) {
                    PlayerBottomToolItem(
                        modifier = Modifier.weight(1f),
                        itemHeight = toolsItemHeight,
                        label = "Speed",
                        imageVector = Icons.Outlined.Tune,
                        topText = speedToolValue,
                        primaryColor = bottomToolsPrimaryColor,
                        secondaryColor = bottomToolsSecondaryColor,
                        containerColor = toolsItemContainerColor,
                        containerBorderColor = toolsItemBorderColor,
                        isImmersive = immersiveEnabled,
                        showIconBubble = toolsShowIconBubble,
                        showSecondaryLabelWhenValue = false,
                        isHighlighted = speedToolHighlighted,
                        onClick = {
                            showSpeedSheet = true
                        }
                    )
                    PlayerBottomToolItem(
                        modifier = Modifier.weight(1f),
                        itemHeight = toolsItemHeight,
                        label = "Timer",
                        imageVector = Icons.Outlined.Timer,
                        valueText = timerToolValue,
                        primaryColor = bottomToolsPrimaryColor,
                        secondaryColor = bottomToolsSecondaryColor,
                        containerColor = toolsItemContainerColor,
                        containerBorderColor = toolsItemBorderColor,
                        isImmersive = immersiveEnabled,
                        showIconBubble = toolsShowIconBubble,
                        showSecondaryLabelWhenValue = false,
                        isHighlighted = timerLabel != null,
                        onClick = {
                            timerSheetSessionKey += 1
                            showTimerSheet = true
                        }
                    )
                    PlayerBottomToolItem(
                        modifier = Modifier.weight(1f),
                        itemHeight = toolsItemHeight,
                        label = "Output",
                        imageVector = outputIcon,
                        valueText = outputLabel,
                        primaryColor = bottomToolsPrimaryColor,
                        secondaryColor = bottomToolsSecondaryColor,
                        containerColor = toolsItemContainerColor,
                        containerBorderColor = toolsItemBorderColor,
                        isImmersive = immersiveEnabled,
                        showIconBubble = toolsShowIconBubble,
                        showSecondaryLabelWhenValue = false,
                        onClick = {
                            viewModel.refreshAudioOutputs()
                            showOutputSheet = true
                        }
                    )
                    PlayerBottomToolItem(
                        modifier = Modifier.weight(1f),
                        itemHeight = toolsItemHeight,
                        label = "Download",
                        imageVector = Icons.Outlined.Download,
                        valueText = downloadToolText,
                        primaryColor = bottomToolsPrimaryColor,
                        secondaryColor = bottomToolsSecondaryColor,
                        containerColor = toolsItemContainerColor,
                        containerBorderColor = toolsItemBorderColor,
                        isImmersive = immersiveEnabled,
                        showIconBubble = toolsShowIconBubble,
                        showSecondaryLabelWhenValue = false,
                        isHighlighted = isBookDownloaded || hasActivePlayerDownload,
                        onClick = { viewModel.toggleDownload() }
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        PlayerBottomToolItem(
                            modifier = Modifier.fillMaxWidth(),
                            itemHeight = toolsItemHeight,
                            label = "More",
                            imageVector = Icons.Outlined.MoreHoriz,
                            primaryColor = bottomToolsPrimaryColor,
                            secondaryColor = bottomToolsSecondaryColor,
                            containerColor = toolsItemContainerColor,
                            containerBorderColor = toolsItemBorderColor,
                            isImmersive = immersiveEnabled,
                            showIconBubble = toolsShowIconBubble,
                            showSecondaryLabelWhenValue = false,
                            onClick = { bottomMenuExpanded = true }
                        )
                        AppDropdownMenu(
                            expanded = bottomMenuExpanded,
                            onDismissRequest = { bottomMenuExpanded = false }
                        ) {
                        Text(
                            text = "Bottom Tools Style",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                        AppDropdownMenuItem(
                            text = { Text("Flat") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Dns,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (bottomToolsStyle == PlayerBottomToolsStyle.Flat) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                appearanceViewModel.setPlayerBottomToolsStyle(
                                    PlayerBottomToolsStyle.Flat.toPreferenceValue()
                                )
                                bottomMenuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("Buttons") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.GridView,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (bottomToolsStyle == PlayerBottomToolsStyle.FloatingChips) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                appearanceViewModel.setPlayerBottomToolsStyle(
                                    PlayerBottomToolsStyle.FloatingChips.toPreferenceValue()
                                )
                                bottomMenuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("Dock") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ViewList,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (bottomToolsStyle == PlayerBottomToolsStyle.SlimStrip) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = {
                                appearanceViewModel.setPlayerBottomToolsStyle(
                                    PlayerBottomToolsStyle.SlimStrip.toPreferenceValue()
                                )
                                bottomMenuExpanded = false
                            }
                        )
                        HorizontalDivider()
                        AppDropdownMenuItem(
                            text = { Text("Skip Intro & Outro") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.SkipNext,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                bottomMenuExpanded = false
                                val skipResult = SkipIntroOutroUseCase.resolve(
                                    chapters = chapters,
                                    currentPositionSeconds = positionSeconds,
                                    bookDurationSeconds = durationSeconds.takeIf { it > 0.0 }
                                )
                                val targetSeconds = skipResult.targetSeconds
                                if (targetSeconds == null) {
                                    val message = skipResult.failureReason?.toUserMessage()
                                        ?: "Unable to skip chapter."
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } else {
                                    if (playbackUiState.book != null) {
                                        viewModel.seekToPositionMs(
                                            positionMs = (targetSeconds * 1000.0).toLong().coerceAtLeast(0L),
                                            commit = true
                                        )
                                    } else {
                                        viewModel.jumpToSeconds(targetSeconds)
                                    }
                                    Toast.makeText(context, "Skipped chapter.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        AppDropdownMenuItem(
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
                            enabled = canTogglePlayerFinished,
                            onClick = {
                                bottomMenuExpanded = false
                                if (effectivePlayerFinished) {
                                    viewModel.markAsUnfinished()
                                } else {
                                    viewModel.markAsFinished()
                                }
                            }
                        )
                        ResetBookProgressMenuItem(
                            showIcon = true,
                            onConfirm = {
                                bottomMenuExpanded = false
                                viewModel.resetBookProgress()
                            }
                        )
                        AppDropdownMenuItem(
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
                        AppDropdownMenuItem(
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
        }

        SnackbarHost(
            hostState = playerSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 92.dp)
        )

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
                    onPlayChapter = { chapterStart -> viewModel.jumpToSeconds(chapterStart) },
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PlayerSheetTabButton(
                title = "Chapters",
                selected = selectedTab == PlayerSheetTab.Chapters,
                onClick = { onSelectTab(PlayerSheetTab.Chapters) },
                modifier = Modifier.weight(1f)
            )
            PlayerSheetTabButton(
                title = "Bookmarks",
                selected = selectedTab == PlayerSheetTab.Bookmarks,
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
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
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
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
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
                                        AppDropdownMenu(
                                            expanded = bookmarkMenuId == bookmark.id,
                                            onDismissRequest = { bookmarkMenuId = null }
                                        ) {
                                            AppDropdownMenuItem(
                                                text = { Text("Edit title") },
                                                onClick = {
                                                    bookmarkMenuId = null
                                                    editingBookmark = bookmark
                                                    editingBookmarkTitle = bookmark.title.orEmpty()
                                                }
                                            )
                                            AppDropdownMenuItem(
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                }
            )
            .border(
                if (selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                } else {
                    BorderStroke(0.dp, Color.Transparent)
                },
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val touchTargetHeight = 28.dp
    val barHeight = 8.dp
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
            .height(touchTargetHeight)
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
                .align(Alignment.BottomStart)
                .clip(barShape)
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress)
                .height(barHeight)
                .align(Alignment.BottomStart)
                .clip(barShape)
                .background(activeColor)
        )
    }
}

@Composable
private fun PlayerBottomToolItem(
    modifier: Modifier = Modifier,
    itemHeight: Dp = 76.dp,
    label: String,
    imageVector: ImageVector,
    topText: String? = null,
    valueText: String? = null,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = Color.Transparent,
    containerBorderColor: Color = Color.Transparent,
    isImmersive: Boolean = false,
    showIconBubble: Boolean = true,
    showSecondaryLabelWhenValue: Boolean = true,
    preferLabelPrimaryWhenValue: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val resolvedTopText = topText?.trim().orEmpty()
    val useTopText = resolvedTopText.isNotBlank()
    val value = valueText?.trim().orEmpty()
    val showSecondaryText = !useTopText && value.isNotBlank() && showSecondaryLabelWhenValue
    val primaryText = if (value.isNotBlank() && preferLabelPrimaryWhenValue) {
        label
    } else if (value.isNotBlank()) {
        value
    } else {
        label
    }
    val secondaryText = if (!showSecondaryText) {
        null
    } else if (preferLabelPrimaryWhenValue) {
        value
    } else {
        label
    }
    val iconChipColor = if (isHighlighted) {
        primaryColor.copy(alpha = 0.2f)
    } else {
        primaryColor.copy(alpha = 0.12f)
    }
    val iconChipBorderColor = if (containerBorderColor.alpha > 0f) {
        containerBorderColor
    } else if (isImmersive) {
        Color.White.copy(alpha = 0.24f)
    } else {
        primaryColor.copy(alpha = 0.22f)
    }
    val topTextFontSize = when {
        resolvedTopText.length >= 5 -> 10.5.sp
        resolvedTopText.length == 4 -> 11.sp
        else -> 11.5.sp
    }
    val primaryValueColor = if (isHighlighted) primaryColor else primaryColor.copy(alpha = 0.96f)
    val itemShape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedContainerColor = if (isPressed) {
        if (isImmersive) {
            Color.White.copy(alpha = 0.22f)
        } else {
            primaryColor.copy(alpha = 0.16f)
        }
    } else {
        containerColor
    }
    Column(
        modifier = modifier
            .height(itemHeight)
            .clip(itemShape)
            .background(pressedContainerColor)
            .border(
                width = if (containerBorderColor.alpha > 0f) 1.dp else 0.dp,
                color = containerBorderColor,
                shape = itemShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = if (showSecondaryText) 0.dp else 1.dp,
                bottom = if (showSecondaryText) 2.dp else 4.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = if (useTopText) {
                if (showIconBubble) {
                    Modifier
                        .padding(top = if (showSecondaryText) 1.dp else 2.dp)
                        .size(if (showSecondaryText) 28.dp else 30.dp)
                } else {
                    Modifier
                        .padding(top = if (showSecondaryText) 2.dp else 4.dp)
                        .size(if (showSecondaryText) 22.dp else 24.dp)
                }
            } else if (showIconBubble) {
                Modifier
                    .padding(top = if (showSecondaryText) 1.dp else 2.dp)
                    .size(if (showSecondaryText) 28.dp else 30.dp)
                    .clip(CircleShape)
                    .background(iconChipColor)
                    .border(1.dp, iconChipBorderColor, CircleShape)
            } else {
                Modifier
                    .padding(top = if (showSecondaryText) 2.dp else 4.dp)
                    .size(if (showSecondaryText) 22.dp else 24.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            if (useTopText) {
                Text(
                    text = resolvedTopText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = topTextFontSize
                    ),
                    color = primaryColor,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                Icon(
                    imageVector = imageVector,
                    contentDescription = label,
                    tint = primaryColor,
                    modifier = Modifier.size(
                        when {
                            showSecondaryText && showIconBubble -> 19.dp
                            showSecondaryText -> 18.dp
                            showIconBubble -> 21.dp
                            else -> 20.dp
                        }
                    )
                )
            }
        }
        Text(
            text = primaryText,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = if (showSecondaryText) 9.5.sp else 10.5.sp
            ),
            color = primaryValueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (secondaryText != null) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 8.sp
                ),
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (showSecondaryLabelWhenValue) {
            Spacer(modifier = Modifier.height(11.dp))
        }
    }
}

private fun playerOutputToolIcon(typeLabel: String?): ImageVector {
    val label = typeLabel?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        label.contains("bluetooth") -> Icons.Outlined.GraphicEq
        label.contains("wired") || label.contains("usb") -> Icons.Outlined.GraphicEq
        label.contains("speaker") || label.contains("phone") -> Icons.Outlined.VolumeUp
        else -> Icons.Outlined.SettingsVoice
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

private enum class PlayerBottomToolsStyle {
    Flat,
    FloatingChips,
    SlimStrip;

    companion object
}

private fun PlayerBottomToolsStyle.toPreferenceValue(): String {
    return when (this) {
        PlayerBottomToolsStyle.Flat -> "flat"
        PlayerBottomToolsStyle.FloatingChips -> "buttons"
        PlayerBottomToolsStyle.SlimStrip -> "dock"
    }
}

private fun PlayerBottomToolsStyle.Companion.fromPreferenceValue(raw: String?): PlayerBottomToolsStyle {
    return when (raw?.lowercase()) {
        "flat" -> PlayerBottomToolsStyle.Flat
        "buttons" -> PlayerBottomToolsStyle.FloatingChips
        "dock" -> PlayerBottomToolsStyle.SlimStrip
        else -> PlayerBottomToolsStyle.SlimStrip
    }
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
    useAccentBackground: Boolean,
    materialDesignEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (totalMs > 0L) {
        (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val chipShape = RoundedCornerShape(999.dp)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val chipContainer = if (useAccentBackground) {
        if (materialDesignEnabled) {
            if (isDarkTheme) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        } else {
            if (isDarkTheme) {
                Color(0xFF4F5563).copy(alpha = 0.9f)
            } else {
                Color(0xFFE5E7ED).copy(alpha = 0.95f)
            }
        }
    } else if (isDarkTheme) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    }
    val chipBorder = if (useAccentBackground) {
        if (materialDesignEnabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.62f else 0.4f)
        } else {
            if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f)
        }
    } else if (isDarkTheme) {
        Color.White.copy(alpha = 0.2f)
    } else {
        Color.Black.copy(alpha = 0.12f)
    }
    val chipContentColor = if (isDarkTheme) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val timerProgressColor = if (useAccentBackground) {
        chipContentColor
    } else {
        MaterialTheme.colorScheme.primary
    }
    val timerTrackColor = if (useAccentBackground) {
        chipContentColor.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = chipShape,
                ambientColor = Color.Black.copy(alpha = 0.22f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(chipShape)
            .background(chipContainer)
            .border(width = 1.dp, color = chipBorder, shape = chipShape)
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
                color = timerProgressColor,
                trackColor = timerTrackColor
            )
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = chipContentColor,
                modifier = Modifier.size(10.dp)
            )
        }
        Text(
            text = formatTimerCountdownShort(remainingMs),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            ),
            color = chipContentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 52.dp)
        )
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
    materialDesignEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonShape = ButtonDefaults.shape
    val labelStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val accentColor = MaterialTheme.colorScheme.primary
    val baseColor = if (materialDesignEnabled) {
        // Keep the unshaved area as a darker accent tone.
        lerp(accentColor, Color.Black, 0.24f)
    } else {
        Color(0xFF1F2126)
    }
    val progressColor = if (materialDesignEnabled) {
        // The shaved progress region stays lighter than the accent.
        lerp(accentColor, Color.White, 0.24f)
    } else {
        listenButtonRemainingColor(baseColor)
    }
    val contentColor = if (
        materialDesignEnabled &&
        MaterialTheme.colorScheme.background.luminance() < 0.5f &&
        progressColor.luminance() > 0.45f
    ) {
        // Dark-material palettes can produce bright green fills; force dark label for contrast.
        Color.Black
    } else if (baseColor.luminance() > 0.55f) {
        Color.Black
    } else {
        Color.White
    }
    val clampedProgress = progress.coerceIn(0f, 1f)
    val progressFillFraction = if (clampedProgress >= 0.995f) 0f else clampedProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
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
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, style = labelStyle)
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
    isImmersive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedContainerColor = if (isPressed) {
        if (isImmersive) {
            Color.White.copy(alpha = 0.22f)
        } else {
            tint.copy(alpha = 0.14f)
        }
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(pressedContainerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
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
            val arrowHeadAngle = 270f
            val tailStartAngle = 40f
            drawArc(
                color = tint,
                startAngle = tailStartAngle,
                sweepAngle = arrowHeadAngle - tailStartAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val radius = arcSize / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val angle = Math.toRadians(arrowHeadAngle.toDouble())
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
private fun MainPlayButtonLoadingIndicator(
    modifier: Modifier = Modifier,
    baseTint: Color,
    sweepTint: Color
) {
    val transition = rememberInfiniteTransition(label = "main-play-button-loading")
    val sweepProgress by transition.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "main-play-button-loading-sweep"
    )
    val barHeights = remember { listOf(0.48f, 0.74f, 1f, 0.74f, 0.48f) }

    Canvas(modifier = modifier) {
        val barCount = barHeights.size
        if (barCount == 0) return@Canvas
        val clusterWidth = size.width * 0.68f
        val spacingRatio = 0.52f
        val barWidth = (
            clusterWidth / (barCount + (barCount - 1) * spacingRatio)
            ).coerceAtLeast(1f)
        val spacing = barWidth * spacingRatio
        val clusterStartX = (size.width - clusterWidth) / 2f
        val cornerRadius = barWidth / 2f
        val sweepCenterX = size.width * sweepProgress
        val sweepRadius = barWidth * 1.8f
        val maxBarHeight = size.height * 0.82f

        barHeights.forEachIndexed { index, heightFraction ->
            val left = clusterStartX + index * (barWidth + spacing)
            val barHeight = (maxBarHeight * heightFraction).coerceAtLeast(size.height * 0.3f)
            val top = (size.height - barHeight) / 2f
            val barCenterX = left + (barWidth / 2f)
            val distanceFraction = ((barCenterX - sweepCenterX) / sweepRadius).let { kotlin.math.abs(it) }
            val sweepStrength = (1f - distanceFraction.coerceIn(0f, 1f)).let { it * it }
            val tint = lerp(baseTint, sweepTint, sweepStrength)

            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )
        }
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

internal fun findActiveChapterIndex(
    chapters: List<BookChapter>,
    positionSeconds: Double
): Int {
    if (chapters.isEmpty()) return -1
    val position = positionSeconds.coerceAtLeast(0.0)
    val latestStartedIndex = chapters.indexOfLast { item -> position >= item.startSeconds }
    return if (latestStartedIndex >= 0) latestStartedIndex else 0
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                }
            )
            .border(
                if (selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                } else {
                    BorderStroke(0.dp, Color.Transparent)
                },
                shape = RoundedCornerShape(11.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
fun CustomizeScreen(
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
                .fillMaxWidth(),
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(0.dp)
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
                            .height(46.dp)
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
                    if (index < orderedRows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
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
    val materialDesignEnabled = LocalMaterialDesignEnabled.current
    val containerColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.primaryContainer
        materialDesignEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f)
    }
    val borderColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        materialDesignEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        selected -> MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    }
    val textColor = when {
        materialDesignEnabled && selected -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
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
            color = textColor
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
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
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
    val posterWidth = StandardGridCoverWidth
    val posterHeight = StandardGridCoverHeight
    val layerCount = series.count.coerceIn(2, 3)
    val frameWidth = posterWidth
    val frameHeight = posterHeight
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
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter
        ) {
            SeriesStackCoverLayers(
                coverUrls = listOfNotNull(book.coverUrl),
                contentDescription = series.seriesName,
                layerCount = layerCount,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                modifier = Modifier.matchParentSize()
            )
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
    chipWidth: Dp = 100.dp,
    avatarSize: Dp = 80.dp,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(chipWidth)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
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
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ContinueListeningCard(
    item: ContinueListeningItem,
    cardWidth: Dp = 266.dp,
    cardHeight: Dp = 98.dp,
    posterWidth: Dp = 72.dp,
    posterHeight: Dp = 80.dp,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    onGoToBook: () -> Unit,
    onAddToCollection: () -> Unit,
    onMarkFinished: () -> Unit,
    onResetBookProgress: () -> Unit,
    onRemoveFromContinueListening: () -> Unit,
    onToggleDownload: () -> Unit,
    onClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
    val downloadLabel = if (isDownloaded || hasActiveDownload) "Remove Download" else "Download"
    val canToggleFinished = hasMeaningfulStartedProgress(
        currentTimeSeconds = item.currentTimeSeconds ?: item.book.currentTimeSeconds,
        durationSeconds = item.book.durationSeconds,
        progressPercent = item.progressPercent ?: item.book.progressPercent,
        isFinished = item.book.hasFinishedProgress()
    )
    val fallbackCardColor = Color(0xFF665A2E)
    val dominantCoverColor = rememberDominantCoverColor(
        coverUrl = item.book.coverUrl,
        enabled = true
    )
    val containerColor = remember(dominantCoverColor) {
        val baseColor = dominantCoverColor ?: fallbackCardColor
        // Keep the cover identity, but boost saturation/value so cards don't look too muted.
        val vividBase = brightenAndSaturateCardColor(baseColor)
        val darkenAmount = when {
            vividBase.luminance() > 0.62f -> 0.32f
            vividBase.luminance() > 0.45f -> 0.2f
            else -> 0.1f
        }
        lerp(vividBase, Color.Black, darkenAmount)
    }
    val primaryTextColor = if (containerColor.luminance() > 0.45f) Color(0xFF1B1B1B) else Color.White
    val secondaryTextColor = if (containerColor.luminance() > 0.45f) Color(0xFF2F2F2F) else Color(0xFFD8D8D8)
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookPoster(
                    book = item.book,
                    width = posterWidth,
                    height = posterHeight,
                    shape = RoundedCornerShape(6.dp),
                    contentScale = ContentScale.Fit,
                    backgroundBlur = 42.dp,
                    showDownloadIndicator = isDownloaded,
                    downloadProgressPercent = downloadProgressPercent
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 28.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.book.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.book.authorName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.timeLeftLabel(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Continue listening actions",
                        tint = primaryTextColor
                    )
                }
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Go to Book") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        },
                        onClick = {
                            onGoToBook()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Add to Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CollectionsBookmark, contentDescription = null) },
                        onClick = {
                            onAddToCollection()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
                        enabled = canToggleFinished,
                        onClick = {
                            onMarkFinished()
                            menuExpanded = false
                        }
                    )
                    ResetBookProgressMenuItem(
                        showIcon = true,
                        onConfirm = {
                            onResetBookProgress()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Remove from Continue Listening") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = {
                            onRemoveFromContinueListening()
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
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
private fun ResetBookProgressMenuItem(
    showIcon: Boolean = false,
    onPrepareConfirm: () -> Unit = {},
    onConfirm: () -> Unit
) {
    var showConfirmation by remember { mutableStateOf(false) }
    AppDropdownMenuItem(
        text = { Text("Reset Book Progress") },
        leadingIcon = if (showIcon) {
            {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null
                )
            }
        } else {
            null
        },
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

private fun brightenAndSaturateCardColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv
    )
    // Favor richer saturation and avoid pastel wash-out.
    hsv[1] = (hsv[1] * 1.48f + 0.12f).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * 0.94f + 0.02f).coerceIn(0.22f, 0.86f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun ContinueListeningSkeletonCard(
    cardWidth: Dp = 266.dp,
    cardHeight: Dp = 98.dp,
    posterWidth: Dp = 72.dp,
    posterHeight: Dp = 80.dp
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF665A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PosterStub(
                width = posterWidth,
                height = posterHeight,
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
    limitInsetToAvoidVerticalLetterbox: Boolean = true,
    forcedFrameSideInset: Dp? = null,
    disableBlurredFrame: Boolean = false,
    showDownloadIndicator: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadBadgeSize: Dp = 30.dp
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
            backgroundBlur = backgroundBlur,
            limitInsetToAvoidVerticalLetterbox = limitInsetToAvoidVerticalLetterbox,
            forcedSideInset = forcedFrameSideInset,
            disableBlurredFrame = disableBlurredFrame
        )
        val progress = downloadProgressPercent?.coerceIn(0, 100)
        val showProgress = progress != null && progress in 0..99
        val showCompleted = showDownloadIndicator && !showProgress
        val badgeProgressSize = (downloadBadgeSize - 6.dp).coerceAtLeast(16.dp)
        val badgeIconSize = (downloadBadgeSize * 0.54f).coerceAtLeast(12.dp)
        val badgeStrokeWidth = when {
            downloadBadgeSize <= 22.dp -> 2.dp
            downloadBadgeSize <= 26.dp -> 2.2.dp
            else -> 2.4.dp
        }
        if (showProgress || showCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(downloadBadgeSize * 0.2f), y = downloadBadgeSize * 0.2f)
                    .size(downloadBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(badgeProgressSize),
                        strokeWidth = badgeStrokeWidth,
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
                        modifier = Modifier.size(badgeIconSize),
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

private fun formatProgressPercentLabel(progressFraction: Float): String {
    val percent = (progressFraction.coerceIn(0f, 1f) * 100f).toDouble()
    return if (percent < 1.0) {
        String.format(Locale.getDefault(), "%.1f%%", percent)
    } else {
        "${percent.toInt().coerceIn(0, 100)}%"
    }
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
