package com.stillshelf.app.ui.screens.podcasts

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.core.model.BookSummary
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.isPlaybackComplete
import com.stillshelf.app.core.util.resolvedProgressFraction
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.screens.PlayerViewModel
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private val screenHorizontalPadding = 16.dp

@Composable
fun PodcastShowDetailScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    onOpenEpisodeDetails: (showId: String, episodeId: String) -> Unit = { _, _ -> },
    onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit = { _, _, _ -> },
    viewModel: PodcastShowDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadedBookKeys by playerViewModel.downloadedBookKeys.collectAsStateWithLifecycle()
    val downloadProgressByUiKey by playerViewModel.downloadProgressByUiKey.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val episodeProgressCache = remember(uiState.show?.id) { mutableStateMapOf<String, Double>() }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val showSearchField = searchExpanded || uiState.episodeQuery.isNotBlank()
    val listState = rememberLazyListState()
    val activeEpisodeId = playerState.book?.id
        ?.takeIf { id -> uiState.show?.id?.let { showId -> id.startsWith("$showId::") } == true }
        ?.substringAfter("::")
    val activeEpisodeProgress = activeEpisodeId?.let { episodeId ->
        playerState.positionMs.takeIf { playerState.durationMs > 0L }?.let { positionMs ->
            (positionMs.toDouble() / playerState.durationMs.toDouble()).coerceIn(0.0, 1.0)
        } ?: uiState.episodes.firstOrNull { it.id == episodeId }?.resolvedProgressFraction(
            activePlaybackBookId = playerState.book?.id,
            activePlaybackPositionMs = playerState.positionMs,
            activePlaybackDurationMs = playerState.durationMs
        )
    }

    SideEffect {
        if (activeEpisodeId != null && activeEpisodeProgress != null) {
            episodeProgressCache[activeEpisodeId] = activeEpisodeProgress
        }
    }

    val queryIsNonBlank = uiState.episodeQuery.isNotBlank()
    LaunchedEffect(showSearchField) {
        if (!showSearchField) listState.animateScrollToItem(0)
    }
    LaunchedEffect(queryIsNonBlank) {
        if (queryIsNonBlank) listState.animateScrollToItem(1)
        else if (showSearchField) listState.animateScrollToItem(0)
    }

    val isCollapsed by remember {
        derivedStateOf {
            val heroInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "show-hero" }
            if (heroInfo == null) listState.firstVisibleItemIndex > 0 else false
        }
    }

    // Scroll-derived alpha for the compact title in the top bar.
    // 0f while hero is fully visible → 1f once hero is ~80% scrolled off.
    val compactTitleAlpha by remember(listState) {
        derivedStateOf {
            val heroInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "show-hero" }
            when {
                heroInfo == null && listState.firstVisibleItemIndex > 0 -> 1f
                heroInfo == null -> 0f
                heroInfo.size <= 0 -> 0f
                else -> {
                    val scrolled = (-heroInfo.offset.toFloat() / heroInfo.size.toFloat())
                    ((scrolled - 0.4f) / 0.4f).coerceIn(0f, 1f)
                }
            }
        }
    }

    val displayEpisodeCount = if (uiState.episodes.isNotEmpty()) {
        uiState.episodes.size
    } else {
        uiState.show?.numEpisodes ?: 0
    }
    val refreshEpisodes = {
        Toast.makeText(context, "Refreshing episodes...", Toast.LENGTH_SHORT).show()
        viewModel.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = screenHorizontalPadding, vertical = 14.dp)
    ) {
        // Top bar: back/home/title/refresh + search + filter
        ShowDetailHeader(
            show = uiState.show,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            isLoading = uiState.isLoading,
            episodeStatusFilter = uiState.episodeStatusFilter,
            episodeSortOrder = uiState.episodeSortOrder,
            searchExpanded = showSearchField,
            compactTitleAlpha = compactTitleAlpha,
            onRefresh = refreshEpisodes,
            onToggleSearch = {
                searchExpanded = !showSearchField
                if (showSearchField) viewModel.setEpisodeQuery("")
            },
            onSetEpisodeStatusFilter = viewModel::setEpisodeStatusFilter,
            onSetEpisodeSortOrder = viewModel::setEpisodeSortOrder
        )

        if (showSearchField) {
            OutlinedTextField(
                value = uiState.episodeQuery,
                onValueChange = viewModel::setEpisodeQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                placeholder = { Text("Search episodes…") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = if (uiState.episodeQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.setEpisodeQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                shape = RoundedCornerShape(12.dp)
            )
        }

        when {
            uiState.isLoading && uiState.show == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null && uiState.show == null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Retry") }
                    }
                }
            }
            else -> {
                if (uiState.syncError != null) {
                    Text(
                        text = uiState.syncError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EpisodeList(
                        modifier = Modifier.fillMaxSize(),
                        listState = listState,
                        isCollapsed = isCollapsed,
                        isSearchActive = showSearchField,
                        episodeCount = displayEpisodeCount,
                        show = uiState.show,
                        episodes = uiState.episodes,
                        continueListeningEpisode = uiState.continueListeningEpisode,
                        showId = uiState.show?.id ?: "",
                        downloadedBookKeys = downloadedBookKeys,
                        downloadProgressByUiKey = downloadProgressByUiKey,
                        allowDeviceDownloads = uiState.podcastDownloadLocal,
                        episodeQuery = uiState.episodeQuery,
                        playingBookId = playerState.book?.id,
                        playingPositionMs = playerState.positionMs,
                        playingDurationMs = playerState.durationMs,
                        episodeProgressCache = episodeProgressCache,
                        isPlayerPlaying = playerState.isPlaying,
                        rssWarning = uiState.rssWarning,
                        onOpenEpisodeDetails = { showId, episodeId ->
                            onOpenEpisodeDetails(showId, episodeId)
                        },
                        onPlayEpisode = onPlayEpisode,
                        onPlayEpisodeRequest = { episodeId ->
                            viewModel.playEpisode(episodeId, onPlayEpisode)
                        },
                        onTogglePlayPause = playerViewModel::onPlayPauseClick,
                        onMarkPlayed = viewModel::markEpisodePlayed,
                        onMarkUnplayed = viewModel::markEpisodeUnplayed,
                        onToggleDownload = viewModel::toggleEpisodeDownload,
                        onResetProgress = { episodeId ->
                            viewModel.resetEpisodeProgress(episodeId)
                            val showId = uiState.show?.id
                            if (showId != null) {
                                playerViewModel.stopAndResetIfCurrentBook("$showId::$episodeId")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowDetailHeader(
    show: PodcastShow?,
    onBackClick: (() -> Unit)?,
    onHomeClick: (() -> Unit)?,
    isLoading: Boolean,
    episodeStatusFilter: EpisodeStatusFilter,
    episodeSortOrder: EpisodeSortOrder,
    searchExpanded: Boolean,
    compactTitleAlpha: Float = 1f,
    onRefresh: () -> Unit,
    onToggleSearch: () -> Unit,
    onSetEpisodeStatusFilter: (EpisodeStatusFilter) -> Unit,
    onSetEpisodeSortOrder: (EpisodeSortOrder) -> Unit
) {
    val menuActive = episodeStatusFilter != EpisodeStatusFilter.All || episodeSortOrder != EpisodeSortOrder.Newest
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (onHomeClick != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onHomeClick) {
                    Icon(
                        imageVector = Icons.Outlined.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = show?.title ?: "Podcast",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(compactTitleAlpha)
        )
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search episodes",
                tint = if (searchExpanded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        var filterMenuExpanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { filterMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = "Episode filter",
                    tint = if (menuActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppDropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false }
            ) {
                EpisodeStatusFilter.entries.forEach { filter ->
                    AppDropdownMenuItem(
                        text = { Text(filter.label) },
                        trailingIcon = {
                            if (episodeStatusFilter == filter) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onSetEpisodeStatusFilter(filter)
                            filterMenuExpanded = false
                        }
                    )
                }
                HorizontalDivider()
                EpisodeSortOrder.entries.forEach { sortOrder ->
                    val isSelected = episodeSortOrder == sortOrder
                    AppDropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(sortOrder.label)
                                if (isSelected) {
                                    Text(
                                        text = sortOrder.hint,
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
                            onSetEpisodeSortOrder(sortOrder)
                            filterMenuExpanded = false
                        }
                    )
                }
            }
        }
        if (!isLoading) {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CollapsedShowBar(show: PodcastShow, episodeCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FramedCoverImage(
                    coverUrl = show.coverUrl,
                    contentDescription = show.title,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!show.author.isNullOrBlank()) {
                        Text(
                            text = show.author,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (episodeCount > 0) {
                        Text(
                            text = "$episodeCount episode${if (episodeCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ShowHero(show: PodcastShow, episodeCount: Int) {
    var descriptionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        FramedCoverImage(
            coverUrl = show.coverUrl,
            contentDescription = show.title,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = show.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!show.author.isNullOrBlank()) {
                Text(
                    text = show.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (episodeCount > 0) {
                Text(
                    text = "$episodeCount episode${if (episodeCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!show.description.isNullOrBlank()) {
                Text(
                    text = show.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = if (descriptionExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = { descriptionExpanded = !descriptionExpanded },
                    modifier = Modifier.padding(top = 0.dp)
                ) {
                    Text(
                        text = if (descriptionExpanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    isCollapsed: Boolean,
    isSearchActive: Boolean = false,
    episodeCount: Int,
    show: PodcastShow?,
    episodes: List<PodcastEpisode>,
    continueListeningEpisode: PodcastEpisode?,
    showId: String,
    downloadedBookKeys: Set<String>,
    downloadProgressByUiKey: Map<String, Int>,
    allowDeviceDownloads: Boolean,
    episodeQuery: String,
    playingBookId: String?,
    playingPositionMs: Long,
    playingDurationMs: Long,
    episodeProgressCache: Map<String, Double>,
    isPlayerPlaying: Boolean,
    rssWarning: String?,
    onOpenEpisodeDetails: (showId: String, episodeId: String) -> Unit,
    onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit,
    onPlayEpisodeRequest: (episodeId: String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onMarkPlayed: (episodeId: String) -> Unit,
    onMarkUnplayed: (episodeId: String) -> Unit,
    onToggleDownload: (episodeId: String) -> Unit,
    onResetProgress: (episodeId: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember(listState, isSearchActive) {
        derivedStateOf { !isSearchActive && listState.firstVisibleItemIndex > 4 }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (show != null) {
                item(key = "show-hero") {
                    ShowHero(show = show, episodeCount = episodeCount)
                }
            }

            if (!rssWarning.isNullOrBlank()) {
                item(key = "rss-warning") {
                    Text(
                        text = rssWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }

            if (episodes.isEmpty()) {
                item(key = "episodes-empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (episodeQuery.isNotBlank()) "No episodes match \"$episodeQuery\""
                            else "No episodes found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                if (continueListeningEpisode != null && !isSearchActive) {
                    item(key = "continue-listening-${continueListeningEpisode.id}") {
                        val episode = continueListeningEpisode
                        val episodeBookId = "$showId::${episode.id}"
                        val showLibraryId = show?.libraryId
                        val downloadUiKey = showLibraryId?.let { "$it|$episodeBookId" }
                        val isLocallyDownloaded = when {
                            downloadUiKey != null -> downloadedBookKeys.contains(downloadUiKey)
                            else -> downloadedBookKeys.contains(episodeBookId)
                        }
                        val activeDownloadProgressPercent = downloadUiKey?.let { downloadProgressByUiKey[it] }
                            ?: downloadProgressByUiKey[episodeBookId]
                        val isCurrent = playingBookId == episodeBookId
                        val statusLabel = when {
                            isCurrent && isPlayerPlaying -> "Playing"
                            isCurrent -> "Paused"
                            else -> "Last played"
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f),
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                EpisodeRow(
                                    episode = episode,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlayerPlaying,
                                    activeDurationMs = if (isCurrent) playingDurationMs else 0L,
                                    progressFraction = episode.resolvedProgressFraction(
                                        activePlaybackBookId = playingBookId,
                                        activePlaybackPositionMs = playingPositionMs,
                                        activePlaybackDurationMs = playingDurationMs
                                    ),
                                    cachedProgressFraction = episodeProgressCache[episode.id],
                                    isDownloaded = isLocallyDownloaded,
                                    downloadProgressPercent = activeDownloadProgressPercent,
                                    allowDeviceDownloads = allowDeviceDownloads,
                                    onOpenDetails = { onOpenEpisodeDetails(showId, episode.id) },
                                    onPlay = { onPlayEpisodeRequest(episode.id) },
                                    onTogglePlayPause = onTogglePlayPause,
                                    onMarkPlayed = { onMarkPlayed(episode.id) },
                                    onMarkUnplayed = { onMarkUnplayed(episode.id) },
                                    onToggleDownload = { onToggleDownload(episode.id) },
                                    onResetProgress = { onResetProgress(episode.id) }
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                        )
                    }
                    item(key = "episodes-section-label") {
                        Text(
                            text = "All Episodes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                items(episodes, key = { it.id }) { episode ->
                    val episodeBookId = "$showId::${episode.id}"
                    val showLibraryId = show?.libraryId
                    val downloadUiKey = showLibraryId?.let { "$it|$episodeBookId" }
                    val isLocallyDownloaded = when {
                        downloadUiKey != null -> downloadedBookKeys.contains(downloadUiKey)
                        else -> downloadedBookKeys.contains(episodeBookId)
                    }
                    val activeDownloadProgressPercent = downloadUiKey?.let { downloadProgressByUiKey[it] }
                        ?: downloadProgressByUiKey[episodeBookId]
                    val isCurrent = playingBookId == episodeBookId
                    val currentProgress = episode.resolvedProgressFraction(
                        activePlaybackBookId = playingBookId,
                        activePlaybackPositionMs = playingPositionMs,
                        activePlaybackDurationMs = playingDurationMs
                    )
                    val cachedProgress = episodeProgressCache[episode.id]
                    EpisodeRow(
                        episode = episode,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlayerPlaying,
                        activeDurationMs = if (isCurrent) playingDurationMs else 0L,
                        progressFraction = currentProgress,
                        cachedProgressFraction = cachedProgress,
                        isDownloaded = isLocallyDownloaded,
                        downloadProgressPercent = activeDownloadProgressPercent,
                        allowDeviceDownloads = allowDeviceDownloads,
                        onOpenDetails = { onOpenEpisodeDetails(showId, episode.id) },
                        onPlay = { onPlayEpisodeRequest(episode.id) },
                        onTogglePlayPause = onTogglePlayPause,
                        onMarkPlayed = { onMarkPlayed(episode.id) },
                        onMarkUnplayed = { onMarkUnplayed(episode.id) },
                        onToggleDownload = { onToggleDownload(episode.id) },
                        onResetProgress = { onResetProgress(episode.id) }
                    )
                    HorizontalDivider()
                }
            }
        }

        AnimatedVisibility(
            visible = show != null && isCollapsed && !isSearchActive,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            if (show != null) {
                CollapsedShowBar(show = show, episodeCount = episodeCount)
            }
        }

        if (showScrollToTop) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 128.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Scroll to top"
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    isCurrent: Boolean,
    isPlaying: Boolean,
    activeDurationMs: Long = 0L,
    progressFraction: Double?,
    cachedProgressFraction: Double?,
    isDownloaded: Boolean,
    downloadProgressPercent: Int?,
    allowDeviceDownloads: Boolean,
    onOpenDetails: () -> Unit,
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onToggleDownload: () -> Unit,
    onResetProgress: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayText = episode.subtitle?.ifBlank { null } ?: episode.description
    val hasDisplayText = !displayText.isNullOrBlank()
    val primaryColor = MaterialTheme.colorScheme.primary
    val displayedProgress = progressFraction ?: cachedProgressFraction ?: episode.resolvedProgressFraction()
    val isPlaybackComplete = displayedProgress?.let { it >= 0.995 } ?: episode.isPlaybackComplete()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left column: play/pause, visualizer, and (at bottom) played checkmark
        Column(
            modifier = Modifier.padding(top = 1.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(modifier = Modifier.size(36.dp)) {
                    val buttonIcon = when {
                        isPlaying -> Icons.Outlined.Pause
                        else -> Icons.Outlined.PlayArrow
                    }
                    val buttonContentDescription = when {
                        isPlaying -> "Pause episode"
                        isCurrent -> "Resume episode"
                        else -> "Play episode"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable(onClick = if (isCurrent && !isPlaybackComplete) onTogglePlayPause else onPlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = buttonContentDescription,
                            tint = if (isCurrent) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                PodcastPlayingVisualizer(
                    isPlaying = isPlaying,
                    isCurrent = isCurrent,
                    color = primaryColor
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isPlaybackComplete) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Played episode",
                        modifier = Modifier.size(11.dp),
                        tint = primaryColor
                    )
                }
            }
        }

        // Episode content — tap opens the episode details page
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenDetails),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else if (isPlaybackComplete) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
            )
            if (hasDisplayText) {
                Text(
                    text = displayText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = buildList {
                episode.pubDate?.let { add(it) }
                val effectiveDuration = if (isCurrent && activeDurationMs > 0L)
                    maxOf(episode.durationSeconds ?: 0.0, activeDurationMs / 1000.0).takeIf { it > 0.0 }
                else
                    episode.durationSeconds
                effectiveDuration?.let { add(formatDuration(it)) }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val progress = displayedProgress
            if (progress != null && progress > 0.01) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val hasActiveDownload = downloadProgressPercent != null && downloadProgressPercent in 0..99
        val showDownloaded = isDownloaded || hasActiveDownload
        val showDownloadAction = allowDeviceDownloads || showDownloaded
        val showRemoteAvailable = episode.audioUrl != null && !showDownloaded

        // Download indicator — 24dp badge matching book list style
        if (showDownloaded) {
            val activeProgress = downloadProgressPercent?.coerceIn(0, 100)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                if (hasActiveDownload) {
                    CircularProgressIndicator(
                        progress = { (activeProgress ?: 0) / 100f },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = primaryColor
                    )
                    Text(
                        text = "${activeProgress ?: 0}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 7.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(13.dp),
                        tint = primaryColor
                    )
                }
            }
        } else if (showRemoteAvailable) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudQueue,
                    contentDescription = "Available on server",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // "..." episode menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Episode options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (!episode.isFinished) {
                    AppDropdownMenuItem(
                        text = { Text("Mark as played") },
                        leadingIcon = {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { menuExpanded = false; onMarkPlayed() }
                    )
                } else {
                    AppDropdownMenuItem(
                        text = { Text("Mark as unplayed") },
                        leadingIcon = {
                            Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { menuExpanded = false; onMarkUnplayed() }
                    )
                }
                AppDropdownMenuItem(
                    text = { Text("Reset progress") },
                    leadingIcon = {
                        Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = { menuExpanded = false; onResetProgress() }
                )
                if (showDownloadAction) {
                    AppDropdownMenuItem(
                        text = { Text(if (showDownloaded || hasActiveDownload) "Remove Download" else "Download") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = { menuExpanded = false; onToggleDownload() }
                    )
                }
            }
        }

    }
}

@Composable
private fun PodcastPlayingVisualizer(
    isPlaying: Boolean,
    isCurrent: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val playTransitionProgress by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(durationMillis = if (isCurrent) 260 else 420, easing = FastOutSlowInEasing),
        label = "podcastVisualizerPlayState"
    )
    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "podcastVisualizer")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 720, easing = LinearEasing)),
            label = "podcastVisualizerPhase"
        )
        animatedPhase
    } else 0f
    var pausedPhase by remember { mutableStateOf(0f) }
    if (isPlaying) SideEffect { pausedPhase = phase }
    val resolvedPhase = if (isPlaying) phase else pausedPhase
    val offsets = remember { listOf(0f, 0.28f, 0.56f, 0.82f) }
    Box(modifier = modifier.width(24.dp).height(12.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            offsets.forEach { offset ->
                val fraction = ((sin((resolvedPhase + offset) * (2.0 * PI)).toFloat() + 1f) / 2f)
                val targetH = lerp(3.dp, 11.dp, fraction)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .align(Alignment.Bottom)
                        .height(lerp(2.dp, targetH, playTransitionProgress))
                        .background(color.copy(alpha = if (isCurrent) 1f else 0f))
                )
            }
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}
