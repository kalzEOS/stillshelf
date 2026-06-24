package com.stillshelf.app.ui.screens.podcasts

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stillshelf.app.core.model.PodcastShow
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.FramedCoverImage

private val screenHorizontalPadding = 16.dp

@Composable
fun PodcastsScreen(
    onBackClick: (() -> Unit)? = null,
    onHomeClick: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onShowClick: (showId: String) -> Unit = {},
    viewModel: PodcastsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = screenHorizontalPadding, vertical = 14.dp)
    ) {
        PodcastsHeader(
            onBackClick = onBackClick,
            onHomeClick = onHomeClick,
            isLoading = uiState.isLoading,
            onRefresh = if (uiState.podcastLibraryId != null) viewModel::refresh else null
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.podcastLibraryId == null -> {
                PodcastsNotConfiguredContent(onOpenSettings = onOpenSettings)
            }
            uiState.isLoading && uiState.shows.isEmpty() -> {
                PodcastsLoadingContent()
            }
            uiState.errorMessage != null && uiState.shows.isEmpty() -> {
                PodcastsErrorContent(
                    message = uiState.errorMessage!!,
                    onRetry = viewModel::refresh
                )
            }
            uiState.shows.isEmpty() -> {
                PodcastsEmptyContent()
            }
            else -> {
                PodcastsShowsGrid(
                    shows = uiState.shows,
                    onShowClick = onShowClick
                )
            }
        }
    }
}

@Composable
private fun PodcastsHeader(
    onBackClick: (() -> Unit)?,
    onHomeClick: (() -> Unit)?,
    isLoading: Boolean,
    onRefresh: (() -> Unit)?
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
            text = "Podcasts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        if (onRefresh != null && !isLoading) {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun PodcastsNotConfiguredContent(onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No podcast library selected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Choose a podcast library in Settings → Podcasts to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun PodcastsLoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PodcastsErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun PodcastsEmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No shows found in this library.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PodcastsShowsGrid(
    shows: List<PodcastShow>,
    onShowClick: (showId: String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(shows, key = { it.id }) { show ->
            PodcastShowCard(show = show, onClick = { onShowClick(show.id) })
        }
    }
}

@Composable
private fun PodcastShowCard(show: PodcastShow, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FramedCoverImage(
            coverUrl = show.coverUrl,
            contentDescription = show.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardGridCoverHeight),
            shape = RoundedCornerShape(8.dp)
        )
        Text(
            text = show.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!show.author.isNullOrBlank()) {
            Text(
                text = show.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
