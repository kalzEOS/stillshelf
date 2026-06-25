package com.stillshelf.app.ui.screens.podcasts

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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.screens.PlayerViewModel
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val screenHorizontalPadding = 16.dp

@Composable
fun PodcastShowDetailScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit = { _, _, _ -> },
    viewModel: PodcastShowDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val showSearchField = searchExpanded || uiState.episodeQuery.isNotBlank()
    val listState = rememberLazyListState()

    // Only collapse when the hero is fully off screen — this aligns with when
    // the stickyHeader starts to "stick", so the bar appears without a layout jump.
    val isCollapsed by remember {
        derivedStateOf {
            val heroInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "show-hero" }
            if (heroInfo == null) listState.firstVisibleItemIndex > 0 else false
        }
    }

    // Use actual merged episode count (RSS + ABS) if available; fall back to ABS count.
    val displayEpisodeCount = if (uiState.episodes.isNotEmpty()) {
        uiState.episodes.size
    } else {
        uiState.show?.numEpisodes ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = screenHorizontalPadding, vertical = 14.dp)
    ) {
        ShowDetailHeader(
            show = uiState.show,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            isLoading = uiState.isLoading,
            onRefresh = viewModel::refresh
        )

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
                EpisodeList(
                    listState = listState,
                    isCollapsed = isCollapsed,
                    episodeCount = displayEpisodeCount,
                    show = uiState.show,
                    episodes = uiState.episodes,
                    showId = uiState.show?.id ?: "",
                    episodeQuery = uiState.episodeQuery,
                    showSearchField = showSearchField,
                    playingBookId = playerState.book?.id,
                    isPlayerPlaying = playerState.isPlaying,
                    rssWarning = uiState.rssWarning,
                    onToggleSearch = {
                        searchExpanded = !showSearchField
                        if (showSearchField) viewModel.setEpisodeQuery("")
                    },
                    onSearchQueryChange = viewModel::setEpisodeQuery,
                    onPlayEpisode = onPlayEpisode,
                    onTogglePlayPause = playerViewModel::onPlayPauseClick,
                    onMarkPlayed = viewModel::markEpisodePlayed,
                    onMarkUnplayed = viewModel::markEpisodeUnplayed,
                    onKeyboardHide = { keyboardController?.hide() }
                )
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
    onRefresh: () -> Unit
) {
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
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
    listState: LazyListState,
    isCollapsed: Boolean,
    episodeCount: Int,
    show: PodcastShow?,
    episodes: List<PodcastEpisode>,
    showId: String,
    episodeQuery: String,
    showSearchField: Boolean,
    playingBookId: String?,
    isPlayerPlaying: Boolean,
    rssWarning: String?,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onMarkPlayed: (episodeId: String) -> Unit,
    onMarkUnplayed: (episodeId: String) -> Unit,
    onKeyboardHide: () -> Unit
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        if (show != null) {
            item(key = "show-hero") {
                ShowHero(show = show, episodeCount = episodeCount)
            }

            // Sticky collapsed bar — zero height in natural position (hero visible),
            // full height when stuck at top (hero scrolled off). Because it overlays
            // the list when stuck, no content below shifts on appear/disappear.
            stickyHeader(key = "collapsed-bar") {
                if (isCollapsed) {
                    CollapsedShowBar(show = show, episodeCount = episodeCount)
                }
            }
        }

        item(key = "episode-search-bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search episodes",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showSearchField) {
            item(key = "episode-search-field") {
                OutlinedTextField(
                    value = episodeQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Search episodes…") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (episodeQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onKeyboardHide() }),
                    shape = RoundedCornerShape(12.dp)
                )
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
            items(episodes, key = { it.id }) { episode ->
                val episodeBookId = "$showId::${episode.id}"
                val isCurrent = playingBookId == episodeBookId
                EpisodeRow(
                    episode = episode,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlayerPlaying,
                    onPlay = { onPlayEpisode(showId, episode.id, episode.currentTimeSeconds) },
                    onTogglePlayPause = onTogglePlayPause,
                    onMarkPlayed = { onMarkPlayed(episode.id) },
                    onMarkUnplayed = { onMarkUnplayed(episode.id) }
                )
                HorizontalDivider()
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
    onPlay: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(episode.id) { mutableStateOf(false) }
    val displayText = episode.subtitle?.ifBlank { null } ?: episode.description
    val hasDisplayText = !displayText.isNullOrBlank()
    val primaryColor = MaterialTheme.colorScheme.primary

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = if (isCurrent) onTogglePlayPause else onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isPlaying -> Icons.Outlined.Pause
                            isCurrent -> Icons.Outlined.PlayArrow
                            episode.isFinished -> Icons.Outlined.CheckCircle
                            else -> Icons.Outlined.PlayArrow
                        },
                        contentDescription = when {
                            isPlaying -> "Pause episode"
                            isCurrent -> "Resume episode"
                            else -> "Play episode"
                        },
                        tint = when {
                            isCurrent -> primaryColor
                            episode.isFinished -> primaryColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
                PodcastPlayingVisualizer(
                    isPlaying = isPlaying,
                    isCurrent = isCurrent,
                    color = primaryColor
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { if (hasDisplayText) expanded = !expanded },
                        onLongClick = { menuExpanded = true }
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent)
                        MaterialTheme.colorScheme.primary
                    else if (episode.isFinished)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (hasDisplayText) {
                    Text(
                        text = displayText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                }
                val meta = buildList {
                    episode.pubDate?.let { add(it) }
                    episode.durationSeconds?.let { add(formatDuration(it)) }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val progress = episode.progressPercent
                if (progress != null && progress > 0.01 && !episode.isFinished) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (episode.audioUrl != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "On server",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (!episode.isFinished) {
                DropdownMenuItem(
                    text = { Text("Mark as played") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onMarkPlayed()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Mark as unplayed") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onMarkUnplayed()
                    }
                )
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
