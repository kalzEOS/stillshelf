package com.stillshelf.app.ui.screens.podcasts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.core.model.BookChapter
import com.stillshelf.app.core.model.PodcastEpisode
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.core.util.formatDurationHoursMinutes
import com.stillshelf.app.core.util.hasPlaybackProgress
import com.stillshelf.app.core.util.isPlaybackComplete
import com.stillshelf.app.core.util.resolveListenActionLabel
import com.stillshelf.app.core.util.resolveStartedProgressSeconds
import com.stillshelf.app.core.util.resolvedProgressFraction
import com.stillshelf.app.ui.common.FramedCoverImage
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.screens.PlayerViewModel
import com.stillshelf.app.ui.theme.LocalMaterialDesignEnabled
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val screenHorizontalPadding = 16.dp

@Composable
fun PodcastEpisodeDetailScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    onGoToShow: (showId: String) -> Unit,
    onPlayEpisode: (showId: String, episodeId: String, startSeconds: Double?) -> Unit,
    viewModel: PodcastEpisodeDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(PodcastEpisodeDetailTab.About) }
    val compoundEpisodeId = uiState.show?.id?.let { showId ->
        uiState.episode?.id?.let { epId -> "$showId::$epId" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = screenHorizontalPadding, vertical = 14.dp)
    ) {
        PodcastEpisodeDetailTopBar(
            show = uiState.show,
            episode = uiState.episode,
            isLoading = uiState.isLoading,
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            onRefresh = viewModel::refresh,
            onGoToShow = onGoToShow,
            onMarkPlayed = viewModel::markEpisodePlayed,
            onMarkUnplayed = viewModel::markEpisodeUnplayed,
            onResetProgress = {
                viewModel.resetEpisodeProgress()
                compoundEpisodeId?.let { playerViewModel.stopAndResetIfCurrentBook(it) }
            }
        )

        if (uiState.errorMessage != null && uiState.show == null) {
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
                    TextButton(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                }
            }
            return
        }

        when {
            uiState.isLoading && uiState.show == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.show == null || uiState.episode == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "Episode not found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val show = uiState.show!!
                val episode = uiState.episode!!
                val activeEpisodeId = playbackState.book?.id
                    ?.takeIf { id -> id.startsWith("${show.id}::") }
                    ?.substringAfter("::")
                val isCurrent = activeEpisodeId == episode.id
                val liveProgress = episode.resolvedProgressFraction(
                    activePlaybackBookId = playbackState.book?.id,
                    activePlaybackPositionMs = playbackState.positionMs,
                    activePlaybackDurationMs = playbackState.durationMs
                )
                val currentProgress = if (isCurrent && playbackState.durationMs > 0L) {
                    (playbackState.positionMs.toDouble() / playbackState.durationMs.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    liveProgress
                }
                val hasProgress = episode.hasPlaybackProgress(
                    activePlaybackBookId = playbackState.book?.id,
                    activePlaybackPositionMs = playbackState.positionMs,
                    activePlaybackDurationMs = playbackState.durationMs
                )
                val isFinished = episode.isPlaybackComplete(
                    activePlaybackBookId = playbackState.book?.id,
                    activePlaybackPositionMs = playbackState.positionMs,
                    activePlaybackDurationMs = playbackState.durationMs
                )
                val listenLabel = resolveListenActionLabel(
                    isFinished = isFinished,
                    hasProgress = hasProgress
                )
                val startSeconds = resolveStartedProgressSeconds(
                    currentTimeSeconds = episode.currentTimeSeconds,
                    durationSeconds = episode.durationSeconds,
                    progressPercent = episode.progressPercent
                )
                val listenProgress = when {
                    isFinished -> 0f
                    currentProgress == null -> 0f
                    else -> currentProgress.toFloat().coerceIn(0f, 1f)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item {
                        PodcastEpisodeHero(
                            show = show,
                            episode = episode,
                            onGoToShow = onGoToShow
                        )
                    }
                    item {
                        PodcastListenProgressButton(
                            text = listenLabel,
                            progress = listenProgress,
                            materialDesignEnabled = LocalMaterialDesignEnabled.current,
                            onClick = {
                                onPlayEpisode(show.id, episode.id, startSeconds)
                            }
                        )
                    }
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
                            PodcastEpisodeTabChip(
                                title = "About",
                                selected = selectedTab == PodcastEpisodeDetailTab.About,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = PodcastEpisodeDetailTab.About }
                            )
                            PodcastEpisodeTabChip(
                                title = "Chapters",
                                selected = selectedTab == PodcastEpisodeDetailTab.Chapters,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedTab = PodcastEpisodeDetailTab.Chapters }
                            )
                        }
                    }

                    when (selectedTab) {
                        PodcastEpisodeDetailTab.About -> {
                            item {
                                val aboutText = episode.description?.ifBlank { null } ?: episode.subtitle?.ifBlank { null }
                                    ?: "No description available."
                                PodcastDetailAboutText(aboutText)
                            }
                            item {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            item {
                                PodcastDetailValueRow(title = "Show", value = show.title)
                            }
                            item {
                                PodcastDetailValueRow(
                                    title = "Published",
                                    value = episode.pubDate?.ifBlank { "Unknown" } ?: "Unknown"
                                )
                            }
                            item {
                                PodcastDetailValueRow(
                                    title = "Duration",
                                    value = formatDurationHoursMinutes(episode.durationSeconds).ifBlank { "Unknown" }
                                )
                            }
                            if (!episode.season.isNullOrBlank()) {
                                item {
                                    PodcastDetailValueRow(title = "Season", value = episode.season!!)
                                }
                            }
                            if (!episode.episode.isNullOrBlank()) {
                                item {
                                    PodcastDetailValueRow(title = "Episode", value = episode.episode!!)
                                }
                            }
                            item {
                                PodcastDetailValueRow(
                                    title = "Progress",
                                    value = if (isFinished) {
                                        "Finished"
                                    } else if (currentProgress != null) {
                                        "${formatProgressPercentLabel(currentProgress.toFloat())} complete"
                                    } else {
                                        "Not started"
                                    }
                                )
                            }
                        }

                        PodcastEpisodeDetailTab.Chapters -> {
                            if (episode.chapters.isEmpty()) {
                                item {
                                    PodcastCenteredEmptyState(
                                        title = "No chapters available",
                                        subtitle = "This episode does not expose chapter markers."
                                    )
                                }
                            } else {
                                val activeChapterIndex = if (isCurrent) {
                                    findActivePodcastChapterIndex(
                                        chapters = episode.chapters,
                                        positionSeconds = playbackState.positionMs.toDouble() / 1000.0
                                    )
                                } else {
                                    -1
                                }
                                itemsIndexed(episode.chapters, key = { index, chapter ->
                                    "${chapter.startSeconds}-$index-${chapter.title}"
                                }) { index, chapter ->
                                    val isActiveChapter = index == activeChapterIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isActiveChapter) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                            .clickable {
                                                onPlayEpisode(show.id, episode.id, chapter.startSeconds)
                                            }
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
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildList {
                                                    add(formatPodcastChapterStart(chapter.startSeconds))
                                                    chapter.endSeconds?.let { endSeconds ->
                                                        val durationSeconds = (endSeconds - chapter.startSeconds).takeIf { it > 0.0 }
                                                        if (durationSeconds != null) {
                                                            add(formatDurationHoursMinutes(durationSeconds))
                                                        }
                                                    }
                                                }.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isActiveChapter) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = "Active chapter",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
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
}

@Composable
private fun PodcastEpisodeDetailTopBar(
    show: PodcastShow?,
    episode: PodcastEpisode?,
    isLoading: Boolean,
    onBackClick: (() -> Unit)?,
    onHomeClick: (() -> Unit)?,
    onRefresh: () -> Unit,
    onGoToShow: (showId: String) -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onResetProgress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            CircleActionButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        if (onHomeClick != null) {
            CircleActionButton(
                icon = Icons.Outlined.Home,
                contentDescription = "Home",
                onClick = onHomeClick
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Text(
            text = "Podcast",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        val episodeReady = show != null && episode != null
        val showId = show?.id
        Box {
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Episode options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                AppDropdownMenuItem(
                    text = { Text("Go to Show") },
                    leadingIcon = {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    },
                    enabled = episodeReady,
                    onClick = {
                        if (showId != null) {
                            onGoToShow(showId)
                        }
                        menuExpanded = false
                    }
                )
                HorizontalDivider()
                if (episode?.isFinished != true) {
                    AppDropdownMenuItem(
                        text = { Text("Mark as Played") },
                        leadingIcon = {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        },
                        enabled = episodeReady,
                        onClick = {
                            onMarkPlayed()
                            menuExpanded = false
                        }
                    )
                } else {
                    AppDropdownMenuItem(
                        text = { Text("Mark as Unplayed") },
                        leadingIcon = {
                            Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null)
                        },
                        enabled = episodeReady,
                        onClick = {
                            onMarkUnplayed()
                            menuExpanded = false
                        }
                    )
                }
                ResetEpisodeProgressMenuItem(
                    showIcon = true,
                    onConfirm = {
                        onResetProgress()
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ResetEpisodeProgressMenuItem(
    showIcon: Boolean = false,
    onPrepareConfirm: () -> Unit = {},
    onConfirm: () -> Unit
) {
    var showConfirmation by remember { mutableStateOf(false) }
    AppDropdownMenuItem(
        text = { Text("Reset Episode Progress") },
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Reset Episode Progress?") },
            text = { Text("This will set the episode back to 0% and stop playback.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    onConfirm()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PodcastEpisodeHero(
    show: PodcastShow,
    episode: PodcastEpisode,
    onGoToShow: (showId: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        FramedCoverImage(
            coverUrl = show.coverUrl,
            contentDescription = episode.title,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!show.title.isBlank()) {
                Row(
                    modifier = Modifier.clickable { onGoToShow(show.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!episode.subtitle.isNullOrBlank()) {
                Text(
                    text = episode.subtitle!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val metadata = buildList {
                episode.pubDate?.let { add(it) }
                episode.durationSeconds?.let { add(formatDurationHoursMinutes(it)) }
                episode.season?.let { add("Season $it") }
                episode.episode?.let { add("Episode $it") }
            }.joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun PodcastListenProgressButton(
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
        androidx.compose.ui.graphics.lerp(accentColor, Color.Black, 0.24f)
    } else {
        Color(0xFF1F2126)
    }
    val progressColor = if (materialDesignEnabled) {
        androidx.compose.ui.graphics.lerp(accentColor, Color.White, 0.24f)
    } else {
        podcastListenButtonRemainingColor(baseColor)
    }
    val contentColor = if (
        materialDesignEnabled &&
        MaterialTheme.colorScheme.background.luminance() < 0.5f &&
        progressColor.luminance() > 0.45f
    ) {
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
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = text, style = labelStyle)
        }
    }
}

@Composable
private fun PodcastEpisodeTabChip(
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
private fun PodcastDetailAboutText(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val shouldCollapse = text.length > 280
    val displayText = if (shouldCollapse && !expanded) {
        text.take(280).trimEnd() + "..."
    } else {
        text
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (shouldCollapse) {
            Text(
                text = if (expanded) "Less" else "More",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun PodcastDetailValueRow(
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

@Composable
private fun PodcastCenteredEmptyState(
    title: String,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
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

private fun podcastListenButtonRemainingColor(baseColor: Color): Color {
    return if (baseColor.luminance() < 0.5f) {
        androidx.compose.ui.graphics.lerp(baseColor, Color.White, 0.22f)
    } else {
        androidx.compose.ui.graphics.lerp(baseColor, Color.Black, 0.12f)
    }
}

private fun formatProgressPercentLabel(progressFraction: Float): String {
    val percent = (progressFraction.coerceIn(0f, 1f) * 100f).toDouble()
    return if (percent < 1.0) {
        String.format(java.util.Locale.getDefault(), "%.1f%%", percent)
    } else {
        "${percent.roundToInt().coerceIn(0, 100)}%"
    }
}

private fun findActivePodcastChapterIndex(
    chapters: List<BookChapter>,
    positionSeconds: Double
): Int {
    if (chapters.isEmpty()) return -1
    val target = positionSeconds.coerceAtLeast(0.0)
    return chapters.indexOfLast { chapter -> target >= chapter.startSeconds }
}

private fun formatPodcastChapterStart(seconds: Double): String {
    val totalSeconds = seconds.roundToInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${secs.toString().padStart(2, '0')}"
    }
}

private enum class PodcastEpisodeDetailTab {
    About,
    Chapters
}
