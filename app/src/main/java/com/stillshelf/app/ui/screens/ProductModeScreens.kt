package com.stillshelf.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stillshelf.app.core.model.BackendProvider

@Composable
fun BackendSelectionScreen(
    hasAudiobookshelfSession: Boolean,
    onBackendSelected: (BackendProvider) -> Unit
) {
    val cards = listOf(
        BackendSelectionCardState(
            provider = BackendProvider.AUDIOBOOKSHELF,
            eyebrow = if (hasAudiobookshelfSession) {
                "Resume existing library"
            } else {
                "Current product mode"
            },
            title = "Audiobookshelf",
            description = "Long-form listening with libraries, chapters, bookmarks, sleep timer, and resume-first playback.",
            detail = if (hasAudiobookshelfSession) {
                "Your active ABS session is still available on this branch."
            } else {
                "Uses the existing StillShelf login and library flow unchanged."
            },
            icon = Icons.Outlined.AutoStories,
            gradient = listOf(Color(0xFF1F2430), Color(0xFF3D4557))
        ),
        BackendSelectionCardState(
            provider = BackendProvider.NAVIDROME,
            eyebrow = "Music mode",
            title = "Navidrome",
            description = "Music-first shell with artists, albums, playlists, queue behavior, and a separate listening flow.",
            detail = "Connect directly to your Navidrome server and browse your real music library inside StillShelf.",
            icon = Icons.Outlined.LibraryMusic,
            gradient = listOf(Color(0xFF0F5132), Color(0xFF198754))
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("One app shell, two product modes") },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurface
            )
        )

        Text(
            text = "Choose the backend this install should open into.",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Audiobookshelf stays intact. Navidrome gets its own prototype shell instead of being forced through the audiobook model.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        cards.forEach { card ->
            BackendSelectionCard(
                state = card,
                onClick = { onBackendSelected(card.provider) }
            )
        }
    }
}

@Composable
fun NavidromeMockScreen(
    onSwitchMode: () -> Unit
) {
    val tabs = remember {
        listOf("Listen", "Artists", "Albums", "Playlists")
    }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var trackIndex by rememberSaveable { mutableIntStateOf(0) }
    val queue = remember {
        listOf(
            MockTrack("Satellite Heart", "Night Drive", "5:12"),
            MockTrack("Signals in Rain", "Night Drive", "3:46"),
            MockTrack("Glass Harbor", "Kepler Mono", "4:28"),
            MockTrack("Northbound Echo", "Static Bloom", "6:01")
        )
    }
    val nowPlaying = queue[trackIndex]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF08120F), Color(0xFF0E221A), Color(0xFF152C22))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 132.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Navidrome prototype") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = Color(0xFF143225),
                                disabledLabelColor = Color(0xFFE4FFF1)
                            )
                        )
                        Button(
                            onClick = onSwitchMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDEF7E8),
                                contentColor = Color(0xFF103423)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Switch mode")
                        }
                    }
                    Text(
                        text = "Music mode gets its own shell.",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color(0xFFF3FFF8)
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF1C5038), Color(0xFF0D2A1D))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Prototype goals",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFC9F1DA)
                            )
                            Text(
                                text = "Separate artists, albums, playlists, and queue-first playback without inheriting ABS library assumptions.",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MockStatPill("Artists", "128")
                                MockStatPill("Albums", "642")
                                MockStatPill("Playlists", "24")
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF10231A).copy(alpha = 0.92f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFE7FFF2)
                        ) {
                            tabs.forEachIndexed { index, label ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { Text(label) }
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedTabIndex) {
                                0 -> ListenTabContent(queue = queue)
                                1 -> CategoryTabContent(
                                    title = "Artist lanes",
                                    description = "A music-first home should lead with artist identity, recent albums, and mix entry points.",
                                    items = listOf(
                                        MockTile("Night Drive", "Synthwave quartet", Icons.Outlined.Radio),
                                        MockTile("Kepler Mono", "Ambient electronics", Icons.Outlined.Waves),
                                        MockTile("Static Bloom", "Alt-pop duo", Icons.Outlined.Headphones)
                                    )
                                )

                                2 -> CategoryTabContent(
                                    title = "Album grid",
                                    description = "Albums stay album-native here instead of being flattened into book cards or chapter lists.",
                                    items = listOf(
                                        MockTile("Afterglow Atlas", "12 tracks", Icons.Outlined.Album),
                                        MockTile("Blue Transit", "9 tracks", Icons.Outlined.Album),
                                        MockTile("Signals", "16 tracks", Icons.Outlined.Album)
                                    )
                                )

                                else -> CategoryTabContent(
                                    title = "Playlist flow",
                                    description = "Queue management, playlist entry points, and play-next actions belong to the music mode.",
                                    items = listOf(
                                        MockTile("Late Night Mix", "42 tracks", Icons.AutoMirrored.Outlined.PlaylistPlay),
                                        MockTile("Focus Drift", "27 tracks", Icons.AutoMirrored.Outlined.QueueMusic),
                                        MockTile("Morning Repeat", "18 tracks", Icons.Outlined.MusicNote)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFE9FFF3)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nowPlaying.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF0B2418)
                        )
                        Text(
                            text = "${nowPlaying.artist} • ${nowPlaying.duration}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF466655)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                trackIndex = if (trackIndex == 0) queue.lastIndex else trackIndex - 1
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = "Previous track",
                                tint = Color(0xFF103423)
                            )
                        }
                        IconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                imageVector = if (isPlaying) {
                                    Icons.Outlined.Equalizer
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color(0xFF103423)
                            )
                        }
                        IconButton(
                            onClick = {
                                trackIndex = if (trackIndex == queue.lastIndex) 0 else trackIndex + 1
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = "Next track",
                                tint = Color(0xFF103423)
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFC7E9D6))
                Text(
                    text = "Mock player profile: queue-first transport, track navigation, and music-native browse surfaces.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF466655)
                )
            }
        }
    }
}

@Composable
private fun BackendSelectionCard(
    state: BackendSelectionCardState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(brush = Brush.linearGradient(state.gradient))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = state.icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    text = state.eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.95f)
                )
                Text(
                    text = state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.76f)
                )
            }
        }
    }
}

@Composable
private fun ListenTabContent(queue: List<MockTrack>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Queue preview",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFE7FFF2)
        )
        Text(
            text = "The first music pass should optimize queueing and quick playback control, not chapters or audiobook progress workflows.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBED7C7)
        )
        queue.forEachIndexed { index, track ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF173327)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d".format(index + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF9DD1B2)
                        )
                        Column {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9CC6AE)
                            )
                        }
                    }
                    Text(
                        text = track.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CC6AE)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabContent(
    title: String,
    description: String,
    items: List<MockTile>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFE7FFF2)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBED7C7)
        )
        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF173327)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF224937)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color(0xFFE7FFF2)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CC6AE)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MockStatPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f)
            )
        }
    }
}

private data class BackendSelectionCardState(
    val provider: BackendProvider,
    val eyebrow: String,
    val title: String,
    val description: String,
    val detail: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

private data class MockTrack(
    val title: String,
    val artist: String,
    val duration: String
)

private data class MockTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)
