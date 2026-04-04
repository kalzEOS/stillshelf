package com.stillshelf.app.ui.screens.navidrome

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
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
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MAX_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MIN_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_STEP_DB
import com.stillshelf.app.core.model.navidromeEqualizerBandFrequenciesHz
import kotlin.math.abs
import androidx.navigation.navArgument
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.stillshelf.app.core.model.NavidromeAlbum
import com.stillshelf.app.core.model.NavidromeAlbumDetail
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.model.NavidromeArtistDetail
import com.stillshelf.app.core.model.NavidromeLibrary
import com.stillshelf.app.core.model.NavidromeLibraryResyncProgress
import com.stillshelf.app.core.model.NavidromeLyricsLine
import com.stillshelf.app.core.model.NavidromeOutputDevice
import com.stillshelf.app.core.model.NavidromePlaylist
import com.stillshelf.app.core.model.NavidromePlaylistDetail
import com.stillshelf.app.core.model.NavidromePlayerState
import com.stillshelf.app.core.model.NavidromeQueueDisplayMode
import com.stillshelf.app.core.model.NavidromeRadio
import com.stillshelf.app.core.model.NavidromeServerScanProgress
import com.stillshelf.app.core.model.NavidromeServerScanStatus
import com.stillshelf.app.core.model.NavidromeTrack
import com.stillshelf.app.core.network.authorizationHeaderValue
import com.stillshelf.app.core.network.splitAuthenticatedUrl
import com.stillshelf.app.data.repo.NavidromeAlbumSortOption
import com.stillshelf.app.ui.components.AppDropdownMenu
import com.stillshelf.app.ui.components.AppDropdownMenuItem
import com.stillshelf.app.ui.common.rememberCoverImageModel
import com.stillshelf.app.ui.common.StandardGridCoverHeight
import com.stillshelf.app.ui.common.StandardGridCoverWidth
import com.stillshelf.app.ui.navigation.NavidromeRoute
import com.stillshelf.app.ui.screens.AppAppearanceViewModel
import com.stillshelf.app.ui.screens.AppScreenHorizontalPadding
import com.stillshelf.app.ui.screens.SettingsServerOption
import com.stillshelf.app.ui.screens.ToggleSectionItem
import com.stillshelf.app.ui.theme.AppThemeMode
import com.stillshelf.app.ui.theme.LocalMaterialDesignEnabled
import kotlin.math.min
import kotlin.math.roundToInt
import java.net.URI
import java.util.Locale
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavidromeLibraryDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

private data class NavidromeHomeDestination(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

private data class NavidromePlaylistSelectionRequest(
    val label: String,
    val trackIds: List<String>
)

private const val NAVIDROME_PLAYER_QUEUE_PREVIEW_LIMIT = 50

internal data class NavidromeQueuePreviewItem(
    val queueIndex: Int,
    val track: NavidromeTrack
)

internal data class NavidromeQueuePreview(
    val items: List<NavidromeQueuePreviewItem>,
    val totalCount: Int
)

internal fun buildNavidromeQueuePreview(
    queue: List<NavidromeTrack>,
    currentTrack: NavidromeTrack,
    currentIndex: Int,
    limit: Int = NAVIDROME_PLAYER_QUEUE_PREVIEW_LIMIT
): NavidromeQueuePreview {
    val effectiveQueue = queue.ifEmpty { listOf(currentTrack) }
    if (effectiveQueue.isEmpty()) {
        return NavidromeQueuePreview(items = emptyList(), totalCount = 0)
    }
    val safeLimit = limit.coerceAtLeast(1)
    val fallbackIndex = effectiveQueue
        .indexOfFirst { it.id == currentTrack.id }
        .takeIf { it >= 0 }
        ?: 0
    val safeStartIndex = currentIndex
        .takeIf { it in effectiveQueue.indices }
        ?: fallbackIndex.coerceIn(0, effectiveQueue.lastIndex)
    val upcomingQueue = effectiveQueue.drop(safeStartIndex)
    return NavidromeQueuePreview(
        items = upcomingQueue
            .take(safeLimit)
            .mapIndexed { offset, track ->
                NavidromeQueuePreviewItem(
                    queueIndex = safeStartIndex + offset,
                    track = track
                )
            },
        totalCount = upcomingQueue.size
    )
}

private fun NavidromeTrack.toPlaylistSelectionRequest(): NavidromePlaylistSelectionRequest {
    return NavidromePlaylistSelectionRequest(
        label = title,
        trackIds = listOf(id)
    )
}

private fun NavidromeAlbumDetail.toPlaylistSelectionRequest(): NavidromePlaylistSelectionRequest? {
    val albumTrackIds = tracks.map(NavidromeTrack::id).filter(String::isNotBlank).distinct()
    if (albumTrackIds.isEmpty()) return null
    val songLabel = if (albumTrackIds.size == 1) "song" else "songs"
    return NavidromePlaylistSelectionRequest(
        label = "${albumTrackIds.size} $songLabel from ${album.name}",
        trackIds = albumTrackIds
    )
}

private fun NavidromePlaylistDetail.toPlaylistSelectionRequest(): NavidromePlaylistSelectionRequest? {
    val playlistTrackIds = tracks.map(NavidromeTrack::id).filter(String::isNotBlank).distinct()
    if (playlistTrackIds.isEmpty()) return null
    val songLabel = if (playlistTrackIds.size == 1) "song" else "songs"
    return NavidromePlaylistSelectionRequest(
        label = "${playlistTrackIds.size} $songLabel from ${playlist.name}",
        trackIds = playlistTrackIds
    )
}

enum class NavidromeAlbumsDisplayStyle(
    val label: String
) {
    GRID("Grid"),
    LIST("List")
}

private val NavidromeHomeTopBarLibrarySelectorMinWidth = 184.dp
private val NavidromeHomeTopBarLibrarySelectorPreferredWidth = 236.dp
private val NavidromeOverlayBottomContentPadding = 120.dp
private val LocalNavidromeBottomOverlayPadding = compositionLocalOf { NavidromeOverlayBottomContentPadding }

@Composable
fun NavidromeLoginRoute(
    onSwitchMode: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: NavidromeLoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showInsecureHttpWarning by remember { mutableStateOf(false) }
    val trimmedServerName = uiState.serverName.trim()
    val trimmedBaseUrl = uiState.baseUrl.trim()

    LaunchedEffect(uiState.loginSucceeded) {
        if (uiState.loginSucceeded) {
            onLoginSuccess()
            viewModel.clearLoginSucceeded()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.showCredentialsStep) {
                    Text(
                        text = "Add Navidrome Server",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    OutlinedTextField(
                        value = uiState.serverName,
                        onValueChange = viewModel::onServerNameChange,
                        label = { Text("Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.serverNameError != null,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.Words
                        )
                    )
                    uiState.serverNameError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = uiState.baseUrl,
                        onValueChange = viewModel::onBaseUrlChange,
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::onTestConnectionClick,
                            enabled = uiState.baseUrl.isNotBlank() && !uiState.isTestingConnection,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(vertical = 1.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Test Connection")
                            }
                        }

                        Button(
                            onClick = {
                                if (isHttpUrl(trimmedBaseUrl)) {
                                    showInsecureHttpWarning = true
                                } else {
                                    viewModel.continueToCredentials()
                                }
                            },
                            enabled = uiState.canContinue,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Continue")
                        }
                    }

                    uiState.connectionMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (uiState.connectionSuccess == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Button(
                        onClick = onSwitchMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                } else {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = trimmedServerName.ifBlank { "Navidrome" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = trimmedBaseUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = viewModel::submit,
                        enabled = uiState.canSubmit && !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign In")
                        }
                    }
                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = viewModel::backToServerStep,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                }
            }
        }

        if (showInsecureHttpWarning) {
            AlertDialog(
                onDismissRequest = { showInsecureHttpWarning = false },
                title = { Text("Use insecure HTTP?") },
                text = {
                    Text(
                        "This server uses an unencrypted connection. " +
                            "Your username, password, and playback data could be exposed on the network."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showInsecureHttpWarning = false
                            viewModel.continueToCredentials()
                        }
                    ) {
                        Text("Use HTTP")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInsecureHttpWarning = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NavidromeAppRoute(
    onSwitchMode: () -> Unit
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val playerViewModel: NavidromePlayerViewModel = hiltViewModel()
    val playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel()
    val downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val lyricsUiState by playerViewModel.lyricsUiState.collectAsStateWithLifecycle()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showMiniPlayer = playerState.currentTrack != null
    val showBottomPlayerShell = showMiniPlayer &&
        currentRoute != NavidromeRoute.SETTINGS &&
        currentRoute != NavidromeRoute.EQUALIZER &&
        currentRoute != NavidromeRoute.LYRICS_SOURCES &&
        currentRoute != NavidromeRoute.SERVERS &&
        currentRoute != NavidromeRoute.LOGIN
    var lockPlayerSheetDismiss by rememberSaveable { mutableStateOf(false) }
    val playerSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden || !lockPlayerSheetDismiss
        }
    )
    var showPlayerSheet by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val systemInsets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    val safeBottomInset = with(density) { (systemInsets?.bottom ?: 0).toDp() }
    var measuredBottomOverlayPadding by remember { mutableStateOf(0.dp) }
    val bottomOverlayPadding = if (showBottomPlayerShell) {
        measuredBottomOverlayPadding.takeIf { it > 0.dp } ?: (safeBottomInset + 96.dp)
    } else {
        safeBottomInset
    }

    fun navigateHome() {
        navController.navigate(NavidromeRoute.HOME) {
            popUpTo(NavidromeRoute.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    LaunchedEffect(downloadUiState.actionMessage, downloadUiState.errorMessage) {
        val message = downloadUiState.actionMessage ?: downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    val topHomeAction: (() -> Unit)? = if (
        currentRoute == NavidromeRoute.HOME ||
        showBottomPlayerShell
    ) {
        null
    } else {
        { navigateHome() }
    }
    fun dismissPlayerSheet(afterDismiss: (() -> Unit)? = null) {
        scope.launch {
            if (playerSheetState.currentValue != SheetValue.Hidden) {
                playerSheetState.hide()
            }
            showPlayerSheet = false
            afterDismiss?.invoke()
        }
    }

    if (showPlayerSheet && playerState.currentTrack != null) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!lockPlayerSheetDismiss) {
                    dismissPlayerSheet()
                }
            },
            sheetState = playerSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            NavidromeExpandedPlayerSheet(
                state = playerState,
                onDismiss = { dismissPlayerSheet() },
                onPrevious = playerViewModel::playPrevious,
                onPlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::playNext,
                onSelectTrack = playerViewModel::playQueueIndex,
                onSeekTo = playerViewModel::seekTo,
                onRefreshAudioOutputs = playerViewModel::refreshAudioOutputs,
                onSelectAudioOutput = playerViewModel::selectAudioOutputDevice,
                isFavorite = playerState.currentTrack?.id in favoriteTrackIds,
                isDownloaded = playerState.currentTrack?.id in downloadUiState.downloadedTrackIds,
                downloadProgressPercent = playerState.currentTrack
                    ?.id
                    ?.let(downloadUiState.trackProgressById::get),
                downloadedTrackIds = downloadUiState.downloadedTrackIds,
                trackProgressById = downloadUiState.trackProgressById,
                onToggleFavorite = playerViewModel::toggleFavoriteTrack,
                onToggleDownload = { track -> downloadsViewModel.toggleTrackDownload(track) },
                immersiveEnabled = appearanceUiState.navidromeImmersivePlayerEnabled,
                materialDesignEnabled = appearanceUiState.navidromeMaterialDesignEnabled,
                lyricsUiState = lyricsUiState,
                onAddToPlaylist = { track ->
                    pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                },
                onShowLyrics = playerViewModel::showLyrics,
                onDismissLyrics = playerViewModel::dismissLyrics,
                onClearLyricsCache = playerViewModel::clearLyricsCache,
                onLyricsModeChanged = { visible -> lockPlayerSheetDismiss = visible },
                onOpenAlbum = { albumId ->
                    dismissPlayerSheet {
                        navController.navigate(NavidromeRoute.album(albumId))
                    }
                },
                onOpenArtist = { artistId ->
                    dismissPlayerSheet {
                        navController.navigate(NavidromeRoute.artist(artistId))
                    }
                }
            )
        }
    }

    CompositionLocalProvider(LocalNavidromeBottomOverlayPadding provides bottomOverlayPadding) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = NavidromeRoute.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 260)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(durationMillis = 260)
                    )
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth },
                        animationSpec = tween(durationMillis = 260)
                    )
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 260)
                    )
                }
            ) {
            composable(NavidromeRoute.HOME) {
                NavidromeHomeRoute(
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) },
                    onOpenPlaylist = { navController.navigate(NavidromeRoute.playlist(it)) },
                    onOpenArtists = { navController.navigate(NavidromeRoute.ARTISTS) },
                    onOpenAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenNewestAlbums = { navController.navigate(NavidromeRoute.NEWEST_ALBUMS) },
                    onOpenRadios = { navController.navigate(NavidromeRoute.RADIOS) },
                    onOpenSongs = { navController.navigate(NavidromeRoute.SONGS) },
                    onOpenDownloaded = { navController.navigate(NavidromeRoute.DOWNLOADED) },
                    onOpenFavorites = { navController.navigate(NavidromeRoute.FAVORITES) },
                    onOpenPlaylists = { navController.navigate(NavidromeRoute.PLAYLISTS) },
                    onOpenSearch = { navController.navigate(NavidromeRoute.SEARCH) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) },
                    onOpenCustomize = { navController.navigate(NavidromeRoute.CUSTOMIZE) },
                    onOpenServers = { navController.navigate(NavidromeRoute.SERVERS) },
                    onOpenLyricsSources = { navController.navigate(NavidromeRoute.LYRICS_SOURCES) },
                    onSwitchMode = onSwitchMode,
                    playerState = playerState,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onOpenPlayer = {
                        if (playerState.currentTrack != null) {
                            showPlayerSheet = true
                        }
                    }
                )
            }
            composable(NavidromeRoute.LIBRARY) {
                NavidromeLibraryRoute(
                    onOpenArtists = { navController.navigate(NavidromeRoute.ARTISTS) },
                    onOpenAlbums = { navController.navigate(NavidromeRoute.ALBUMS) },
                    onOpenRadios = { navController.navigate(NavidromeRoute.RADIOS) },
                    onOpenNewestAlbums = { navController.navigate(NavidromeRoute.NEWEST_ALBUMS) },
                    onOpenSongs = { navController.navigate(NavidromeRoute.SONGS) },
                    onOpenDownloaded = { navController.navigate(NavidromeRoute.DOWNLOADED) },
                    onOpenFavoriteSongs = { navController.navigate(NavidromeRoute.FAVORITES) },
                    onOpenPlaylists = { navController.navigate(NavidromeRoute.PLAYLISTS) },
                    onOpenSettings = { navController.navigate(NavidromeRoute.SETTINGS) }
                )
            }
            composable(NavidromeRoute.DOWNLOADED) {
                NavidromeDownloadedRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.RADIOS) {
                NavidromeRadiosRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.SONGS) {
                NavidromeSongsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.FAVORITES) {
                NavidromeFavoriteSongsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.SEARCH) {
                NavidromeSearchRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.SETTINGS) {
                NavidromeSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onSwitchMode = onSwitchMode,
                    onOpenEqualizer = { navController.navigate(NavidromeRoute.EQUALIZER) },
                    onOpenLyricsSources = { navController.navigate(NavidromeRoute.LYRICS_SOURCES) },
                    onOpenServers = { navController.navigate(NavidromeRoute.SERVERS) }
                )
            }
            composable(NavidromeRoute.EQUALIZER) {
                NavidromeEqualizerRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.SERVERS) {
                NavidromeServersManagementRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onAddServer = { navController.navigate(NavidromeRoute.LOGIN) }
                )
            }
            composable(NavidromeRoute.LYRICS_SOURCES) {
                NavidromeLyricsSourcesRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.LOGIN) {
                NavidromeLoginRoute(
                    onSwitchMode = { navController.popBackStack() },
                    onLoginSuccess = {
                        navController.popBackStack()
                    }
                )
            }
            composable(NavidromeRoute.CUSTOMIZE) {
                NavidromeCustomizeRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction
                )
            }
            composable(NavidromeRoute.ARTISTS) {
                NavidromeArtistsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(NavidromeRoute.ALBUMS) {
                NavidromeAlbumsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(NavidromeRoute.NEWEST_ALBUMS) {
                NavidromeAlbumsRoute(
                    title = "Newest Albums",
                    lockedSort = NavidromeAlbumSortOption.RECENT,
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(NavidromeRoute.PLAYLISTS) {
                NavidromePlaylistsRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenPlaylist = { navController.navigate(NavidromeRoute.playlist(it)) }
                )
            }
            composable(
                route = NavidromeRoute.PLAYLIST_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.PLAYLIST_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromePlaylistDetailRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onFinished = { navController.popBackStack() },
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) },
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            composable(
                route = NavidromeRoute.ARTIST_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ARTIST_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeArtistDetailRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenAlbum = { navController.navigate(NavidromeRoute.album(it)) }
                )
            }
            composable(
                route = NavidromeRoute.ALBUM_PATTERN,
                arguments = listOf(navArgument(NavidromeRoute.ALBUM_ID_ARG) { type = NavType.StringType })
            ) {
                NavidromeAlbumDetailRoute(
                    onBack = { navController.popBackStack() },
                    onHome = topHomeAction,
                    onOpenArtist = { navController.navigate(NavidromeRoute.artist(it)) }
                )
            }
            }
            NavidromePlaylistPickerHost(
                pendingRequest = pendingPlaylistRequest,
                onDismiss = { pendingPlaylistRequest = null },
                viewModel = playlistPickerViewModel
            )
            if (safeBottomInset > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(safeBottomInset)
                        .background(MaterialTheme.colorScheme.background)
                )
            }
            AnimatedVisibility(
                visible = showBottomPlayerShell,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 260)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 260)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = 12.dp)
                        .padding(bottom = safeBottomInset + 6.dp)
                        .onSizeChanged {
                            measuredBottomOverlayPadding = with(density) { it.height.toDp() + safeBottomInset }
                        },
                    verticalAlignment = Alignment.Bottom
                ) {
                    NavidromeMiniPlayerBar(
                        state = playerState,
                        onPrevious = playerViewModel::playPrevious,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::playNext,
                        onOpenPlayer = {
                            if (playerState.currentTrack != null) {
                                showPlayerSheet = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (currentRoute != NavidromeRoute.HOME) {
                        val homeBubbleShape = RoundedCornerShape(24.dp)
                        val homeBubbleBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(54.dp)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(homeBubbleShape)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = homeBubbleShape
                                    )
                                    .border(width = 1.5.dp, color = homeBubbleBorderColor, shape = homeBubbleShape)
                                    .clickable(onClick = ::navigateHome),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = "Home",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeHomeRoute(
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenNewestAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenLyricsSources: () -> Unit,
    onSwitchMode: () -> Unit,
    playerState: NavidromePlayerState,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: NavidromeHomeViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel(),
    customizeViewModel: NavidromeCustomizeViewModel = hiltViewModel(),
    settingsViewModel: NavidromeSettingsViewModel = hiltViewModel(),
    homeMenuViewModel: NavidromeHomeMenuViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val customizeUiState by customizeViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val homeMenuUiState by homeMenuViewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.actionMessage, downloadUiState.errorMessage) {
        val message = downloadUiState.actionMessage ?: downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(
        uiState.errorMessage,
        uiState.recentAlbums,
        uiState.artists,
        uiState.playlists,
        uiState.radios
    ) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val hasVisibleContent = uiState.recentAlbums.isNotEmpty() ||
            uiState.artists.isNotEmpty() ||
            uiState.playlists.isNotEmpty() ||
            uiState.radios.isNotEmpty()
        if (!hasVisibleContent) return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }
    LaunchedEffect(homeMenuUiState.errorMessage) {
        val message = homeMenuUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        homeMenuViewModel.clearError()
    }
    LaunchedEffect(settingsUiState.activeServerId, settingsUiState.activeLibraryId) {
        if (settingsUiState.activeServerId != null) {
            viewModel.refresh(forceRefresh = false)
        }
    }
    NavidromeHomeScreen(
        uiState = uiState,
        customizeUiState = customizeUiState,
        libraryTitle = settingsUiState.availableLibraries
            .firstOrNull { it.id == settingsUiState.activeLibraryId }
            ?.name
            ?: settingsUiState.session?.serverName?.takeIf { it.isNotBlank() }?.let { "$it Music" }
            ?: settingsUiState.session?.username?.takeIf { it.isNotBlank() }?.let { "$it Music" }
            ?: "Navidrome Music",
        savedServers = homeMenuUiState.servers,
        activeServerId = homeMenuUiState.activeServerId,
        availableLibraries = settingsUiState.availableLibraries,
        activeLibraryId = settingsUiState.activeLibraryId,
        playerState = playerState,
        downloadUiState = downloadUiState,
        materialDesignEnabled = appearanceUiState.navidromeMaterialDesignEnabled,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onOpenPlaylist = onOpenPlaylist,
        onOpenArtists = onOpenArtists,
        onOpenAlbums = onOpenAlbums,
        onOpenNewestAlbums = onOpenNewestAlbums,
        onOpenRadios = onOpenRadios,
        onOpenSongs = onOpenSongs,
        onOpenDownloaded = onOpenDownloaded,
        onOpenFavorites = onOpenFavorites,
        onOpenPlaylists = onOpenPlaylists,
        onOpenSearch = onOpenSearch,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        onOpenCustomize = onOpenCustomize,
        onOpenServers = onOpenServers,
        onOpenLyricsSources = onOpenLyricsSources,
        onSelectServer = homeMenuViewModel::onServerSelected,
        onSelectLibrary = settingsViewModel::setActiveLibrary,
        onSwitchMode = onSwitchMode,
        onRenamePlaylist = viewModel::renamePlaylist,
        onDeletePlaylist = viewModel::deletePlaylist,
        onPlayPause = onPlayPause,
        onPlayTrack = playerViewModel::playTrack,
        onPlayAlbum = playerViewModel::playAlbum,
        onToggleTrackDownload = downloadsViewModel::toggleTrackDownload,
        onToggleAlbumDownload = downloadsViewModel::toggleAlbumDownload,
        onOpenPlayer = onOpenPlayer
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
private fun NavidromeHomeScreen(
    uiState: NavidromeHomeUiState,
    customizeUiState: NavidromeCustomizeUiState,
    libraryTitle: String,
    savedServers: List<com.stillshelf.app.ui.screens.SettingsServerOption>,
    activeServerId: String?,
    availableLibraries: List<NavidromeLibrary>,
    activeLibraryId: String?,
    playerState: NavidromePlayerState,
    downloadUiState: NavidromeDownloadsUiState,
    materialDesignEnabled: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenNewestAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCustomize: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenLyricsSources: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSelectLibrary: (String) -> Unit,
    onSwitchMode: () -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String, String) -> Unit,
    onPlayPause: () -> Unit,
    onPlayTrack: (NavidromeTrack) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit,
    onToggleTrackDownload: (NavidromeTrack) -> Unit,
    onToggleAlbumDownload: (NavidromeAlbum) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val homeStartInset = AppScreenHorizontalPadding
    val homeEndInset = AppScreenHorizontalPadding
    val homeInsetTotal = homeStartInset + homeEndInset
    val homeFullBleedModifier = remember(homeStartInset, homeEndInset) {
        Modifier
            .fillMaxWidth()
            .padding(start = homeStartInset, end = homeEndInset)
    }
    val homeCarouselModifier = remember { Modifier.fillMaxWidth() }
    val homeCarouselContentPadding = remember(homeStartInset, homeEndInset) {
        PaddingValues(start = homeStartInset, end = homeEndInset)
    }
    val homeShelfPosterWidth = StandardGridCoverWidth
    val homeShelfPosterHeight = StandardGridCoverHeight
    val configuration = LocalConfiguration.current
    val refreshState = rememberPullRefreshState(
        refreshing = uiState.isLoading,
        onRefresh = onRefresh
    )
    var isLibraryMenuExpanded by remember { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var randomAlbumsVersion by rememberSaveable { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var playlistNameInput by rememberSaveable { mutableStateOf("") }
    val randomAlbums = remember(randomAlbumsVersion, uiState.recentAlbums) {
        uiState.recentAlbums.shuffled().take(min(12, uiState.recentAlbums.size))
    }
    val listItemById = remember(
        onOpenAlbums,
        onOpenArtists,
        onOpenRadios,
        onOpenSongs,
        onOpenFavorites,
        onOpenPlaylists
    ) {
        mapOf(
            NavidromeListSectionIds.ARTISTS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.ARTISTS,
                label = "Artists",
                icon = Icons.Outlined.PersonOutline,
                onClick = onOpenArtists
            ),
            NavidromeListSectionIds.ALBUMS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.ALBUMS,
                label = "Albums",
                icon = Icons.Outlined.Album,
                onClick = onOpenAlbums
            ),
            NavidromeListSectionIds.RADIOS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.RADIOS,
                label = "Radios",
                icon = Icons.Outlined.GraphicEq,
                onClick = onOpenRadios
            ),
            NavidromeListSectionIds.NEWEST_ALBUMS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.NEWEST_ALBUMS,
                label = "Newest Albums",
                icon = Icons.Outlined.Album,
                onClick = onOpenNewestAlbums
            ),
            NavidromeListSectionIds.SONGS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.SONGS,
                label = "Songs",
                icon = Icons.Outlined.MusicNote,
                onClick = onOpenSongs
            ),
            NavidromeListSectionIds.DOWNLOADED to NavidromeHomeDestination(
                id = NavidromeListSectionIds.DOWNLOADED,
                label = "Downloaded",
                icon = Icons.Outlined.Download,
                onClick = onOpenDownloaded
            ),
            NavidromeListSectionIds.FAVORITES to NavidromeHomeDestination(
                id = NavidromeListSectionIds.FAVORITES,
                label = "Favorite Songs",
                icon = Icons.Outlined.Favorite,
                onClick = onOpenFavorites
            ),
            NavidromeListSectionIds.PLAYLISTS to NavidromeHomeDestination(
                id = NavidromeListSectionIds.PLAYLISTS,
                label = "Playlists",
                icon = Icons.Outlined.MusicNote,
                onClick = onOpenPlaylists
            )
        )
    }
    val orderedListItems = customizeUiState.listSections
        .mapNotNull { listItemById[it.id] }
        .filterNot { customizeUiState.hiddenListSectionIds.contains(it.id) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(refreshState)
    ) {
        val availableHomeContentWidth = remember(maxWidth, homeInsetTotal) {
            (maxWidth - homeInsetTotal).coerceAtLeast(0.dp)
        }
        val continueListeningPosterWidth = 72.dp
        val continueListeningPosterHeight = 80.dp
        val continueListeningCardWidth = remember(availableHomeContentWidth, configuration.fontScale) {
            val widthFactor = if (configuration.fontScale > 1.05f) 0.84f else 0.8f
            (availableHomeContentWidth * widthFactor).coerceIn(266.dp, 336.dp)
        }
        val continueListeningCardHeight = remember(configuration.fontScale) {
            (
                continueListeningPosterHeight +
                    12.dp +
                    ((configuration.fontScale - 1f).coerceAtLeast(0f) * 8f).dp
                ).coerceIn(96.dp, 124.dp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "nav-home-top-bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = homeStartInset, end = homeEndInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f)
                    ) {
                        val hasLibraryMenu = availableLibraries.isNotEmpty()
                        val libraryMenuWidth = NavidromeHomeTopBarLibrarySelectorPreferredWidth
                            .coerceAtMost(maxWidth)
                            .coerceAtLeast(NavidromeHomeTopBarLibrarySelectorMinWidth.coerceAtMost(maxWidth))
                        Row(
                            modifier = Modifier
                                .width(libraryMenuWidth)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = hasLibraryMenu) {
                                    isLibraryMenuExpanded = true
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = libraryTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasLibraryMenu) {
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
                            expanded = isLibraryMenuExpanded && hasLibraryMenu,
                            onDismissRequest = { isLibraryMenuExpanded = false },
                            modifier = Modifier.width(libraryMenuWidth)
                        ) {
                            availableLibraries.forEach { library ->
                                val isActive = library.id == activeLibraryId
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
                                    onClick = {
                                        isLibraryMenuExpanded = false
                                        onSelectLibrary(library.id)
                                    }
                                )
                            }
                        }
                    }
                    CircleActionButton(
                        icon = Icons.Outlined.Search,
                        contentDescription = "Search",
                        onClick = onOpenSearch
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
                                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenSettings()
                                }
                            )
                            AppDropdownMenuItem(
                                text = { Text("Customize") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenCustomize()
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
                            savedServers.forEach { server ->
                                AppDropdownMenuItem(
                                    text = {
                                        Text(
                                            text = server.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Outlined.Dns, contentDescription = null)
                                    },
                                    trailingIcon = if (server.id == activeServerId) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Active server"
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        if (server.id != activeServerId) {
                                            onSelectServer(server.id)
                                        }
                                    }
                                )
                            }
                            if (savedServers.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Manage servers",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Dns, contentDescription = null)
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onOpenServers()
                                }
                            )
                        }
                    }
                }
            }
            if (uiState.isOffline) {
                item(key = "nav-home-offline-warning") {
                    Card(
                        modifier = homeFullBleedModifier,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "You’re offline",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Some library pages may look incomplete until you reconnect. Previously opened items can still appear from cache, and your downloaded music is available in Downloaded.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(
                                onClick = onOpenDownloaded,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Open Downloaded")
                            }
                        }
                    }
                }
            }
            if (uiState.lyricsCacheSizeBytes >= NAVIDROME_LYRICS_CACHE_SOFT_WARNING_BYTES) {
                item(key = "nav-home-lyrics-cache-warning") {
                    val isStrongWarning = uiState.lyricsCacheSizeBytes >= NAVIDROME_LYRICS_CACHE_STRONG_WARNING_BYTES
                    val containerColor = if (isStrongWarning) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
                    }
                    val contentColor = if (isStrongWarning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    val borderColor = if (isStrongWarning) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    }
                    Card(
                        modifier = homeFullBleedModifier,
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = BorderStroke(1.dp, borderColor),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isStrongWarning) {
                                    "Lyrics cache is using a lot of storage"
                                } else {
                                    "Lyrics cache is getting large"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = contentColor
                            )
                            Text(
                                text = "Lyrics cache is currently using ${formatStorageSize(uiState.lyricsCacheSizeBytes)} on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor
                            )
                            TextButton(
                                onClick = onOpenLyricsSources,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Manage Cache")
                            }
                        }
                    }
                }
            }
            item(key = "nav-home-library-sections") {
                Card(
                    modifier = homeFullBleedModifier,
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (materialDesignEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surface.copy(
                                alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
                            )
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (materialDesignEnabled) {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        } else if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.Black.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        orderedListItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clickable(onClick = item.onClick),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(23.dp)
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp),
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
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
            }
            customizeUiState.personalizedSections.forEach { section ->
                if (customizeUiState.hiddenPersonalizedSectionIds.contains(section.id)) {
                    return@forEach
                }
                when (section.id) {
                    NavidromeHomeSectionIds.CONTINUE -> {
                        item(key = "${section.id}-title") {
                            SectionTitle(
                                title = "Continue Listening",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item(key = "${section.id}-content") {
                            when {
                                playerState.recentTracks.isEmpty() -> {
                                    Text(
                                        text = "No music in progress yet.",
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
                                        itemsIndexed(
                                            items = playerState.recentTracks.take(7),
                                            key = { index, track -> "${track.id}:$index" }
                                        ) { _, track ->
                                            NavidromeContinueListeningCard(
                                                track = track,
                                                isCurrent = playerState.currentTrack?.id == track.id,
                                                isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                                                isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                                                cardWidth = continueListeningCardWidth,
                                                cardHeight = continueListeningCardHeight,
                                                posterWidth = continueListeningPosterWidth,
                                                posterHeight = continueListeningPosterHeight,
                                                onPlayPause = onPlayPause,
                                                onPlayTrack = { onPlayTrack(track) },
                                                onToggleDownload = { onToggleTrackDownload(track) },
                                                onClick = {
                                                    if (playerState.currentTrack?.id == track.id) {
                                                        onOpenPlayer()
                                                    } else {
                                                        onPlayTrack(track)
                                                    }
                                                },
                                                onOpenAlbum = track.albumId?.let { albumId ->
                                                    { onOpenAlbum(albumId) }
                                                },
                                                onOpenArtist = track.artistId?.let { artistId ->
                                                    { onOpenArtist(artistId) }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.RECENTLY_ADDED -> {
                        item(key = "${section.id}-title") {
                            SectionTitle(
                                title = "Recently Added",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item(key = "${section.id}-content") {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.recentAlbums, key = { it.id }) { album ->
                                    NavidromeHomeAlbumCard(
                                        album = album,
                                        posterWidth = homeShelfPosterWidth,
                                        posterHeight = homeShelfPosterHeight,
                                        isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                            ?.let { it >= album.songCount && album.songCount > 0 }
                                            ?: false,
                                        downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                                        onClick = { onOpenAlbum(album.id) },
                                        onPlayAlbum = { onPlayAlbum(album.id, false) },
                                        onShuffleAlbum = { onPlayAlbum(album.id, true) },
                                        onToggleDownload = { onToggleAlbumDownload(album) },
                                        onOpenAlbum = { onOpenAlbum(album.id) },
                                        onOpenArtist = { album.artistId?.let(onOpenArtist) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.DISCOVER -> {
                        item(key = "${section.id}-title") {
                            SectionTitle(
                                title = "Discover",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item(key = "${section.id}-content") {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(randomAlbums, key = { it.id }) { album ->
                                    NavidromeHomeAlbumCard(
                                        album = album,
                                        posterWidth = homeShelfPosterWidth,
                                        posterHeight = homeShelfPosterHeight,
                                        isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                            ?.let { it >= album.songCount && album.songCount > 0 }
                                            ?: false,
                                        downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                                        onClick = { onOpenAlbum(album.id) },
                                        onPlayAlbum = { onPlayAlbum(album.id, false) },
                                        onShuffleAlbum = { onPlayAlbum(album.id, true) },
                                        onToggleDownload = { onToggleAlbumDownload(album) },
                                        onOpenAlbum = { onOpenAlbum(album.id) },
                                        onOpenArtist = { album.artistId?.let(onOpenArtist) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.ARTISTS -> if (uiState.artists.isNotEmpty()) {
                        item(key = "${section.id}-title") {
                            SectionTitle(
                                title = "Artists",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item(key = "${section.id}-content") {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.artists.take(12), key = { it.id }) { artist ->
                                    NavidromeHomeArtistCard(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist.id) }
                                    )
                                }
                            }
                        }
                    }

                    NavidromeHomeSectionIds.PLAYLISTS -> if (uiState.playlists.isNotEmpty()) {
                        item(key = "${section.id}-title") {
                            SectionTitle(
                                title = "Playlists",
                                modifier = Modifier.padding(start = homeStartInset, end = homeEndInset)
                            )
                        }
                        item(key = "${section.id}-content") {
                            LazyRow(
                                modifier = homeCarouselModifier,
                                contentPadding = homeCarouselContentPadding,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.playlists.take(12), key = { it.id }) { playlist ->
                                    NavidromeHomePlaylistCard(
                                        playlist = playlist,
                                        posterWidth = homeShelfPosterWidth,
                                        onClick = { onOpenPlaylist(playlist.id) },
                                        onRename = {
                                            playlistNameInput = playlist.name
                                            renameTarget = playlist
                                        },
                                        onDelete = {
                                            deleteTarget = playlist
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (uiState.errorMessage != null) {
                item(key = "nav-home-error") {
                    Box(modifier = homeFullBleedModifier) {
                        ErrorCard(uiState.errorMessage)
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

    renameTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    singleLine = true,
                    label = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget = null
                        onRenamePlaylist(playlist.id, playlistNameInput)
                    },
                    enabled = !uiState.isSubmitting
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

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeletePlaylist(playlist.id, playlist.name)
                    },
                    enabled = !uiState.isSubmitting
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
private fun NavidromeCustomizeRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeCustomizeViewModel = hiltViewModel()
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
        onBack()
    }

    fun saveAndExit() {
        pendingListRows?.let { viewModel.setListOrder(it.map(ToggleSectionItem::id)) }
        pendingPersonalizedRows?.let { viewModel.setPersonalizedOrder(it.map(ToggleSectionItem::id)) }
        pendingHiddenListSectionIds?.let(viewModel::setHiddenListSectionIds)
        pendingHiddenPersonalizedSectionIds?.let(viewModel::setHiddenPersonalizedSectionIds)
        cancelAndExit()
    }

    BackHandler(onBack = ::cancelAndExit)

    val effectiveListRows = pendingListRows ?: uiState.listSections
    val effectivePersonalizedRows = pendingPersonalizedRows ?: uiState.personalizedSections
    val effectiveHiddenListSectionIds = pendingHiddenListSectionIds ?: uiState.hiddenListSectionIds
    val effectiveHiddenPersonalizedSectionIds =
        pendingHiddenPersonalizedSectionIds ?: uiState.hiddenPersonalizedSectionIds
    val orderedRows = if (selectedTab == "Lists") effectiveListRows else effectivePersonalizedRows

    fun moveRow(source: List<ToggleSectionItem>, from: Int, to: Int): List<ToggleSectionItem> {
        if (from !in source.indices || to !in source.indices || from == to) return source
        val mutable = source.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        return mutable
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppScreenHorizontalPadding, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleActionButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = ::cancelAndExit
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (onHome != null) {
                CircleActionButton(
                    icon = Icons.Outlined.Home,
                    contentDescription = "Home",
                    onClick = onHome
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = "Customize",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            CircleActionButton(
                icon = Icons.Filled.Check,
                contentDescription = "Done",
                onClick = ::saveAndExit
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavidromeCustomizeTabChip(
                label = "Lists",
                selected = selectedTab == "Lists",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Lists" }
            )
            NavidromeCustomizeTabChip(
                label = "Personalized",
                selected = selectedTab == "Personalized",
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = "Personalized" }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
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
                                    pendingHiddenListSectionIds =
                                        toggleHiddenSection(effectiveHiddenListSectionIds, row.id)
                                } else {
                                    pendingHiddenPersonalizedSectionIds =
                                        toggleHiddenSection(effectiveHiddenPersonalizedSectionIds, row.id)
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
private fun NavidromeCustomizeTabChip(
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
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
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

private fun toggleHiddenSection(hidden: Set<String>, id: String): Set<String> {
    val next = hidden.toMutableSet()
    if (!next.add(id)) next.remove(id)
    return next
}

@Composable
private fun NavidromeLibraryRoute(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenRadios: () -> Unit,
    onOpenNewestAlbums: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenFavoriteSongs: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val items = remember {
        listOf(
            NavidromeLibraryDestination("Artists", Icons.Outlined.Person, onOpenArtists),
            NavidromeLibraryDestination("Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Radios", Icons.Outlined.GraphicEq, onOpenRadios),
            NavidromeLibraryDestination("Newest Albums", Icons.Outlined.Album, onOpenNewestAlbums),
            NavidromeLibraryDestination("Recently Played Albums", Icons.Outlined.Album, onOpenAlbums),
            NavidromeLibraryDestination("Songs", Icons.Outlined.MusicNote, onOpenSongs),
            NavidromeLibraryDestination("Downloaded", Icons.Outlined.Download, onOpenDownloaded),
            NavidromeLibraryDestination("Favorite Songs", Icons.Outlined.Favorite, onOpenFavoriteSongs),
            NavidromeLibraryDestination("Playlists", Icons.Outlined.QueueMusic, onOpenPlaylists)
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = NavidromeOverlayBottomContentPadding
        )
    ) {
        item {
            TopLevelHeader(
                title = "Library",
                onProfileClick = onOpenSettings,
                onEditClick = onOpenSettings
            )
        }
        item {
            Card(
                modifier = Modifier.padding(top = 10.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    items.forEachIndexed { index, destination ->
                        LibraryMenuRow(
                            label = destination.label,
                            icon = destination.icon,
                            onClick = destination.onClick
                        )
                        if (index != items.lastIndex) {
                            DividerLine()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeDownloadedRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    downloadedViewModel: NavidromeDownloadedViewModel = hiltViewModel()
) {
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode by downloadedViewModel.layoutMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDownloadRemoval by remember { mutableStateOf<NavidromeHomeDownloadEntry?>(null) }
    var showRemoveAllConfirmation by remember { mutableStateOf(false) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val collapseDistancePx = with(LocalDensity.current) {
        NavidromeLargeTitleCollapseDistance.roundToPx()
    }
    val collapseFraction by remember(layoutMode, listState, gridState, collapseDistancePx) {
        derivedStateOf {
            when (layoutMode) {
                NavidromeDownloadedLayoutMode.List -> calculateHeaderCollapseFraction(listState, collapseDistancePx)
                NavidromeDownloadedLayoutMode.Grid -> calculateHeaderCollapseFraction(gridState, collapseDistancePx)
            }
        }
    }

    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }

    NavidromeHeaderScaffold(
        title = "Downloaded",
        collapseFraction = collapseFraction,
        onBack = onBack,
        onHome = onHome,
        stickyHeaderVisible = false,
        stickyHeaderContent = null,
        containerColor = MaterialTheme.colorScheme.background,
        actions = {
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Downloaded options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
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
                            if (layoutMode == NavidromeDownloadedLayoutMode.Grid) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            downloadedViewModel.setLayoutMode(NavidromeDownloadedLayoutMode.Grid)
                            menuExpanded = false
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
                            if (layoutMode == NavidromeDownloadedLayoutMode.List) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            downloadedViewModel.setLayoutMode(NavidromeDownloadedLayoutMode.List)
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider()
                    AppDropdownMenuItem(
                        text = { Text("Remove All Downloads") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Remove,
                                contentDescription = null
                            )
                        },
                        enabled = downloadUiState.homeDownloads.isNotEmpty(),
                        onClick = {
                            menuExpanded = false
                            showRemoveAllConfirmation = true
                        }
                    )
                }
            }
        }
    ) { topInsetPadding ->
        when {
            downloadUiState.homeDownloads.isEmpty() || layoutMode == NavidromeDownloadedLayoutMode.List -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        NavidromeScrollingTitle(
                            title = "Downloaded",
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (downloadUiState.homeDownloads.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "No downloads yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Downloaded songs and albums will show up here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(downloadUiState.homeDownloads, key = { it.key }) { entry ->
                            NavidromeHomeDownloadedRow(
                                entry = entry,
                                onClick = {
                                    when (entry) {
                                        is NavidromeHomeDownloadedAlbum -> onOpenAlbum(entry.album.id)
                                        is NavidromeHomeDownloadedTrack -> playerViewModel.playTrack(entry.track)
                                    }
                                },
                                onPlayEntry = {
                                    when (entry) {
                                        is NavidromeHomeDownloadedAlbum -> playerViewModel.playAlbum(entry.album.id, false)
                                        is NavidromeHomeDownloadedTrack -> playerViewModel.playTrack(entry.track)
                                    }
                                },
                                onShuffleAlbum = if (entry is NavidromeHomeDownloadedAlbum) {
                                    { playerViewModel.playAlbum(entry.album.id, true) }
                                } else {
                                    null
                                },
                                onOpenAlbum = entry.albumId?.let { albumId ->
                                    { onOpenAlbum(albumId) }
                                },
                                onOpenArtist = entry.artistId?.let { artistId ->
                                    { onOpenArtist(artistId) }
                                },
                                onRequestRemove = { pendingDownloadRemoval = entry }
                            )
                        }
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NavidromeScrollingTitle(
                            title = "Downloaded",
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    items(downloadUiState.homeDownloads.size) { index ->
                        val entry = downloadUiState.homeDownloads[index]
                        NavidromeDownloadedGridCard(
                            entry = entry,
                            onClick = {
                                when (entry) {
                                    is NavidromeHomeDownloadedAlbum -> onOpenAlbum(entry.album.id)
                                    is NavidromeHomeDownloadedTrack -> playerViewModel.playTrack(entry.track)
                                }
                            },
                            onRequestRemove = { pendingDownloadRemoval = entry },
                            onPlayEntry = {
                                when (entry) {
                                    is NavidromeHomeDownloadedAlbum -> playerViewModel.playAlbum(entry.album.id, false)
                                    is NavidromeHomeDownloadedTrack -> playerViewModel.playTrack(entry.track)
                                }
                            },
                            onShuffleAlbum = if (entry is NavidromeHomeDownloadedAlbum) {
                                { playerViewModel.playAlbum(entry.album.id, true) }
                            } else {
                                null
                            },
                            onOpenAlbum = entry.albumId?.let { albumId ->
                                { onOpenAlbum(albumId) }
                            },
                            onOpenArtist = entry.artistId?.let { artistId ->
                                { onOpenArtist(artistId) }
                            }
                        )
                    }
                }
            }
        }
    }

    pendingDownloadRemoval?.let { entry ->
        val title = when (entry) {
            is NavidromeHomeDownloadedAlbum -> "Remove album download"
            is NavidromeHomeDownloadedTrack -> "Remove song download"
        }
        AlertDialog(
            onDismissRequest = { pendingDownloadRemoval = null },
            title = { Text(title) },
            text = {
                Text(
                    when (entry) {
                        is NavidromeHomeDownloadedAlbum -> "Remove \"${entry.album.name}\" from downloads on this device?"
                        is NavidromeHomeDownloadedTrack -> "Remove \"${entry.track.title}\" from downloads on this device?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDownloadRemoval = null
                        when (entry) {
                            is NavidromeHomeDownloadedAlbum -> downloadsViewModel.removeAlbumDownload(entry.album.id)
                            is NavidromeHomeDownloadedTrack -> downloadsViewModel.removeTrackDownload(entry.track.id)
                        }
                    },
                    enabled = !downloadUiState.isSubmitting
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownloadRemoval = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showRemoveAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveAllConfirmation = false },
            title = { Text("Remove all downloads") },
            text = { Text("Remove all downloaded music from this library on this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveAllConfirmation = false
                        downloadsViewModel.removeAllDownloads()
                    },
                    enabled = !downloadUiState.isSubmitting
                ) {
                    Text("Remove All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAllConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun NavidromeArtistsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel(),
    artistsViewModel: NavidromeArtistsViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutMode by artistsViewModel.layoutMode.collectAsStateWithLifecycle()
    val sortOption by artistsViewModel.sortOption.collectAsStateWithLifecycle()
    val searchQuery by artistsViewModel.searchQuery.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val showSearchField = searchExpanded || searchQuery.isNotBlank()
    val collapseDistancePx = with(LocalDensity.current) {
        NavidromeLargeTitleCollapseDistance.roundToPx()
    }
    val collapseFraction by remember(layoutMode, listState, gridState, collapseDistancePx) {
        derivedStateOf {
            when (layoutMode) {
                NavidromeArtistsLayoutMode.Grid -> calculateHeaderCollapseFraction(gridState, collapseDistancePx)
                NavidromeArtistsLayoutMode.List -> calculateHeaderCollapseFraction(listState, collapseDistancePx)
            }
        }
    }
    val stickySearchVisible = rememberNavidromeStickyHeaderVisibility(
        enabled = showSearchField,
        firstVisibleItemIndex = when (layoutMode) {
            NavidromeArtistsLayoutMode.Grid -> gridState.firstVisibleItemIndex
            NavidromeArtistsLayoutMode.List -> listState.firstVisibleItemIndex
        },
        firstVisibleItemScrollOffset = when (layoutMode) {
            NavidromeArtistsLayoutMode.Grid -> gridState.firstVisibleItemScrollOffset
            NavidromeArtistsLayoutMode.List -> listState.firstVisibleItemScrollOffset
        }
    )
    val displayedArtists = remember(uiState.artists, sortOption, searchQuery) {
        val filteredArtists = uiState.artists.filter { artist ->
            searchQuery.isBlank() || artist.name.contains(searchQuery.trim(), ignoreCase = true)
        }
        when (sortOption) {
            NavidromeArtistSortOption.NAME_ASC -> filteredArtists.sortedBy { it.name.lowercase(Locale.getDefault()) }
            NavidromeArtistSortOption.NAME_DESC -> filteredArtists.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            NavidromeArtistSortOption.MOST_ALBUMS -> filteredArtists.sortedWith(
                compareByDescending<NavidromeArtist> { it.albumCount }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
            NavidromeArtistSortOption.FEWEST_ALBUMS -> filteredArtists.sortedWith(
                compareBy<NavidromeArtist> { it.albumCount }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
        }
    }

    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    NavidromeHeaderScaffold(
        title = "Artists",
        collapseFraction = collapseFraction,
        onBack = onBack,
        onHome = onHome,
        stickyHeaderVisible = false,
        stickyHeaderContent = null,
        containerColor = MaterialTheme.colorScheme.background,
        actions = {
            RoundIconButton(
                icon = Icons.Outlined.Search,
                contentDescription = if (showSearchField) "Hide artist search" else "Show artist search",
                onClick = { searchExpanded = !showSearchField }
            )
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Artist options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
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
                            if (layoutMode == NavidromeArtistsLayoutMode.Grid) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            artistsViewModel.setLayoutMode(NavidromeArtistsLayoutMode.Grid)
                            menuExpanded = false
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
                            if (layoutMode == NavidromeArtistsLayoutMode.List) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        },
                        onClick = {
                            artistsViewModel.setLayoutMode(NavidromeArtistsLayoutMode.List)
                            menuExpanded = false
                        }
                    )
                    HorizontalDivider()
                    NavidromeArtistSortOption.entries.forEach { sort ->
                        AppDropdownMenuItem(
                            text = { Text("Sort: ${sort.label}") },
                            trailingIcon = {
                                if (sortOption == sort) {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                artistsViewModel.setSortOption(sort)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    ) { topInsetPadding ->
        when {
            uiState.isLoading -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        NavidromeScrollingTitle(
                            title = "Artists",
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search artists",
                                onQueryChange = artistsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    item { LoadingCard() }
                }
            }
            !uiState.errorMessage.isNullOrBlank() || displayedArtists.isEmpty() || layoutMode == NavidromeArtistsLayoutMode.List -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        NavidromeScrollingTitle(
                            title = "Artists",
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search artists",
                                onQueryChange = artistsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    when {
                        !uiState.errorMessage.isNullOrBlank() -> {
                            item { ErrorCard(uiState.errorMessage ?: "Unable to load artists.") }
                        }
                        displayedArtists.isEmpty() -> {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (searchQuery.isBlank()) "No artists yet" else "No matching artists",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (searchQuery.isBlank()) {
                                                "This server does not have any artists to show."
                                            } else {
                                                "Try a different artist name."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            items(displayedArtists) { artist ->
                                ArtistRow(
                                    artist = artist,
                                    isDownloaded = downloadUiState.fullyDownloadedAlbumCountByArtistId[artist.id]
                                        ?.let { it >= artist.albumCount && artist.albumCount > 0 }
                                        ?: false,
                                    downloadProgressPercent = downloadUiState.artistProgressById[artist.id],
                                    onClick = { onOpenArtist(artist.id) }
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NavidromeScrollingTitle(
                            title = "Artists",
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search artists",
                                onQueryChange = artistsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    items(displayedArtists.size) { index ->
                        val artist = displayedArtists[index]
                        ArtistGridCard(
                            artist = artist,
                            isDownloaded = downloadUiState.fullyDownloadedAlbumCountByArtistId[artist.id]
                                ?.let { it >= artist.albumCount && artist.albumCount > 0 }
                                ?: false,
                            downloadProgressPercent = downloadUiState.artistProgressById[artist.id],
                            onClick = { onOpenArtist(artist.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromeAlbumsRoute(
    title: String = "Albums",
    lockedSort: NavidromeAlbumSortOption? = null,
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeBrowseViewModel = hiltViewModel(),
    albumsViewModel: NavidromeAlbumsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayStyle by albumsViewModel.layoutMode.collectAsStateWithLifecycle()
    val searchQuery by albumsViewModel.searchQuery.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val showSearchField = searchExpanded || searchQuery.isNotBlank()
    val collapseDistancePx = with(LocalDensity.current) {
        NavidromeLargeTitleCollapseDistance.roundToPx()
    }
    val collapseFraction by remember(displayStyle, listState, gridState, collapseDistancePx) {
        derivedStateOf {
            when (displayStyle) {
                NavidromeAlbumsDisplayStyle.GRID -> calculateHeaderCollapseFraction(gridState, collapseDistancePx)
                NavidromeAlbumsDisplayStyle.LIST -> calculateHeaderCollapseFraction(listState, collapseDistancePx)
            }
        }
    }
    val stickySearchVisible = rememberNavidromeStickyHeaderVisibility(
        enabled = showSearchField,
        firstVisibleItemIndex = when (displayStyle) {
            NavidromeAlbumsDisplayStyle.GRID -> gridState.firstVisibleItemIndex
            NavidromeAlbumsDisplayStyle.LIST -> listState.firstVisibleItemIndex
        },
        firstVisibleItemScrollOffset = when (displayStyle) {
            NavidromeAlbumsDisplayStyle.GRID -> gridState.firstVisibleItemScrollOffset
            NavidromeAlbumsDisplayStyle.LIST -> listState.firstVisibleItemScrollOffset
        }
    )
    val displayedAlbums = remember(uiState.albums, uiState.albumSort, searchQuery) {
        val normalizedQuery = searchQuery.trim()
        uiState.albums.filter { album ->
            normalizedQuery.isBlank() ||
                album.name.contains(normalizedQuery, ignoreCase = true) ||
                album.artistName.contains(normalizedQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(lockedSort) {
        if (lockedSort != null && uiState.albumSort != lockedSort) {
            viewModel.setAlbumSort(lockedSort)
        }
    }
    NavidromeHeaderScaffold(
        title = title,
        collapseFraction = collapseFraction,
        onBack = onBack,
        onHome = onHome,
        stickyHeaderVisible = false,
        stickyHeaderContent = null,
        containerColor = MaterialTheme.colorScheme.background,
        actions = {
            RoundIconButton(
                icon = Icons.Outlined.Search,
                contentDescription = if (showSearchField) "Hide album search" else "Show album search",
                onClick = { searchExpanded = !showSearchField }
            )
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Album options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Grid view") },
                        leadingIcon = {
                            if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.GRID)
                            menuExpanded = false
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("List view") },
                        leadingIcon = {
                            if (displayStyle == NavidromeAlbumsDisplayStyle.LIST) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.LIST)
                            menuExpanded = false
                        }
                    )
                    if (lockedSort == null) {
                        DividerLine()
                        NavidromeAlbumSortOption.entries.forEach { sort ->
                            AppDropdownMenuItem(
                                text = { Text("Sort: ${sort.label}") },
                                leadingIcon = {
                                    if (uiState.albumSort == sort) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setAlbumSort(sort)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { topInsetPadding ->
        when {
            uiState.isLoading -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        NavidromeScrollingTitle(
                            title = title,
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search albums",
                                onQueryChange = albumsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    item { LoadingCard() }
                }
            }
            !uiState.errorMessage.isNullOrBlank() || displayedAlbums.isEmpty() || displayStyle == NavidromeAlbumsDisplayStyle.LIST -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        NavidromeScrollingTitle(
                            title = title,
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search albums",
                                onQueryChange = albumsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    when {
                        !uiState.errorMessage.isNullOrBlank() -> {
                            item { ErrorCard(uiState.errorMessage ?: "Unable to load albums.") }
                        }
                        displayedAlbums.isEmpty() -> {
                            item {
                                EmptyCard(
                                    if (searchQuery.isBlank()) {
                                        "No albums found."
                                    } else {
                                        "No albums match \"${searchQuery.trim()}\"."
                                    }
                                )
                            }
                        }
                        else -> {
                            item {
                                NavidromeTransportHeader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, bottom = 12.dp),
                                    onPlay = { playerViewModel.playAlbums(displayedAlbums) },
                                    onShuffle = { playerViewModel.playAlbums(displayedAlbums, shuffle = true) }
                                )
                            }
                            items(displayedAlbums) { album ->
                                AlbumRow(
                                    album = album,
                                    isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                        ?.let { it >= album.songCount && album.songCount > 0 }
                                        ?: false,
                                    downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                                    onClick = { onOpenAlbum(album.id) }
                                )
                                DividerLine()
                            }
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = NavidromeOverlayBottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NavidromeScrollingTitle(
                            title = title,
                            collapseFraction = collapseFraction,
                            topPadding = topInsetPadding
                        )
                    }
                    if (showSearchField) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NavidromeExpandableSearchField(
                                visible = true,
                                query = searchQuery,
                                label = "Search albums",
                                onQueryChange = albumsViewModel::onSearchQueryChange
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        NavidromeTransportHeader(
                            modifier = Modifier.fillMaxWidth(),
                            onPlay = { playerViewModel.playAlbums(displayedAlbums) },
                            onShuffle = { playerViewModel.playAlbums(displayedAlbums, shuffle = true) }
                        )
                    }
                    items(displayedAlbums.size) { index ->
                        val album = displayedAlbums[index]
                        AlbumGridCard(
                            album = album,
                            isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                ?.let { it >= album.songCount && album.songCount > 0 }
                                ?: false,
                            downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavidromePlaylistsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenPlaylist: (String) -> Unit,
    viewModel: NavidromePlaylistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<NavidromePlaylist?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val playlists = remember(uiState.playlists, uiState.searchQuery, uiState.sortOption) {
        val normalizedQuery = uiState.searchQuery.trim()
        val filtered = uiState.playlists.filter { playlist ->
            normalizedQuery.isBlank() || playlist.name.contains(normalizedQuery, ignoreCase = true)
        }
        when (uiState.sortOption) {
            NavidromePlaylistSortOption.NAME -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            NavidromePlaylistSortOption.DURATION -> filtered.sortedWith(
                compareByDescending<NavidromePlaylist> { it.durationSeconds ?: -1 }
                    .thenBy { it.name.lowercase(Locale.getDefault()) }
            )
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }
    LaunchedEffect(uiState.errorMessage, uiState.playlists) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        if (uiState.playlists.isEmpty()) return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }

    StandardTopScreen(
        title = "Playlists",
        onBack = onBack,
        onHome = onHome,
        topContent = {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                singleLine = true,
                label = { Text("Search playlists") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Playlist options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    NavidromePlaylistSortOption.entries.forEach { sort ->
                        AppDropdownMenuItem(
                            text = { Text(sort.label) },
                            leadingIcon = {
                                if (uiState.sortOption == sort) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                viewModel.setSortOption(sort)
                                menuExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    AppDropdownMenuItem(
                        text = { Text("Refresh Playlists") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            viewModel.refresh(forceRefresh = true)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            CircleActionButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Create playlist",
                onClick = {
                    nameInput = ""
                    createDialogVisible = true
                }
            )
        }
    ) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (playlists.isNotEmpty()) {
            items(playlists, key = { it.id }) { playlist ->
                var menuExpanded by remember { mutableStateOf(false) }
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Playlist actions"
                                )
                            }
                            NavidromePlaylistManagementMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                onRename = {
                                    nameInput = playlist.name
                                    renameTarget = playlist
                                    menuExpanded = false
                                },
                                onDelete = {
                                    deleteTarget = playlist
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load playlists.") }
        } else if (uiState.searchQuery.isNotBlank()) {
            item { EmptyCard("No playlists match \"${uiState.searchQuery.trim()}\".") }
        } else {
            item { EmptyCard("No playlists yet. Create one to start saving songs.") }
            item {
                Button(onClick = {
                    nameInput = ""
                    createDialogVisible = true
                }) {
                    Text("Create Playlist")
                }
            }
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
                    },
                    enabled = !uiState.isSubmitting
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

    renameTarget?.let { playlist ->
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
                        viewModel.renamePlaylist(playlist.id, nameInput)
                    },
                    enabled = !uiState.isSubmitting
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

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deletePlaylist(playlist.id, playlist.name)
                    },
                    enabled = !uiState.isSubmitting
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
private fun NavidromePlaylistDetailRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onFinished: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromePlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogVisible by rememberSaveable { mutableStateOf(false) }
    var deleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pendingTrackRemovalIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }
    LaunchedEffect(uiState.errorMessage, uiState.detail) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        if (uiState.detail == null) return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
    }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            onFinished()
        }
    }

    StandardTopScreen(
        title = "Playlist",
        onBack = onBack,
        onHome = onHome,
        actions = {
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Playlist options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
                        },
                        enabled = uiState.detail?.tracks?.isNotEmpty() == true,
                        onClick = {
                            val detail = uiState.detail ?: return@AppDropdownMenuItem
                            menuExpanded = false
                            playerViewModel.playTracks(detail.tracks, 0)
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Shuffle") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Shuffle, contentDescription = null)
                        },
                        enabled = uiState.detail?.tracks?.isNotEmpty() == true,
                        onClick = {
                            val detail = uiState.detail ?: return@AppDropdownMenuItem
                            menuExpanded = false
                            playerViewModel.playTracks(detail.tracks.shuffled(), 0)
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Add to Queue") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.QueueMusic, contentDescription = null)
                        },
                        enabled = uiState.detail?.tracks?.isNotEmpty() == true,
                        onClick = {
                            val detail = uiState.detail ?: return@AppDropdownMenuItem
                            menuExpanded = false
                            playerViewModel.addTracksToQueue(detail.tracks)
                            Toast.makeText(context, "Playlist added to queue", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider()
                    AppDropdownMenuItem(
                        text = { Text("Rename") },
                        enabled = uiState.detail != null,
                        onClick = {
                            menuExpanded = false
                            nameInput = uiState.detail?.playlist?.name.orEmpty()
                            renameDialogVisible = true
                        }
                    )
                    AppDropdownMenuItem(
                        text = { Text("Delete") },
                        enabled = uiState.detail != null,
                        onClick = {
                            menuExpanded = false
                            deleteDialogVisible = true
                        }
                    )
                }
            }
        }
    ) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            item {
                PlaylistDetailHero(
                    detail = detail,
                    onPlay = { playerViewModel.playTracks(detail.tracks, 0) },
                    onShuffle = {
                        val shuffledTracks = detail.tracks.shuffled()
                        if (shuffledTracks.isNotEmpty()) {
                            playerViewModel.playTracks(shuffledTracks, 0)
                        }
                    }
                )
            }
            if (detail.tracks.isEmpty()) {
                item { EmptyCard("No songs yet. Add songs from the Songs, Album, Search, or Favorite Songs screens.") }
            } else {
                itemsIndexed(detail.tracks, key = { index, track -> "${track.id}:$index" }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                        onClick = { playerViewModel.playTracks(detail.tracks, index) },
                        trailingContent = {
                            var rowMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { rowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "Playlist song options"
                                    )
                                }
                                NavidromeTrackActionsMenu(
                                    expanded = rowMenuExpanded,
                                    onDismissRequest = { rowMenuExpanded = false },
                                    onPlayTrack = {
                                        rowMenuExpanded = false
                                        playerViewModel.playTracks(detail.tracks, index)
                                    },
                                    playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                    onShowAlbum = track.albumId?.let { albumId ->
                                        {
                                            rowMenuExpanded = false
                                            onOpenAlbum(albumId)
                                        }
                                    },
                                    onShowArtist = track.artistId?.let { artistId ->
                                        {
                                            rowMenuExpanded = false
                                            onOpenArtist(artistId)
                                        }
                                    },
                                    extraActions = {
                                        HorizontalDivider()
                                        AppDropdownMenuItem(
                                            text = { Text("Add to Playlist") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.QueueMusic,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                rowMenuExpanded = false
                                                pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                                            }
                                        )
                                        AppDropdownMenuItem(
                                            text = { Text("Remove from Playlist") },
                                            onClick = {
                                                rowMenuExpanded = false
                                                pendingTrackRemovalIndex = index
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
        } else {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load playlist.") }
        }
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
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
                        renameDialogVisible = false
                        viewModel.renamePlaylist(nameInput)
                    },
                    enabled = !uiState.isSubmitting
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${uiState.detail?.playlist?.name.orEmpty()}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        viewModel.deletePlaylist()
                    },
                    enabled = !uiState.isSubmitting
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingTrackRemovalIndex?.let { index ->
        val track = uiState.detail?.tracks?.getOrNull(index)
        if (track != null) {
            AlertDialog(
                onDismissRequest = { pendingTrackRemovalIndex = null },
                title = { Text("Remove Song") },
                text = { Text("Remove \"${track.title}\" from this playlist?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingTrackRemovalIndex = null
                            viewModel.removeTrack(index, track.title)
                        },
                        enabled = !uiState.isSubmitting
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTrackRemovalIndex = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    NavidromePlaylistPickerHost(
        pendingRequest = pendingPlaylistRequest,
        onDismiss = { pendingPlaylistRequest = null },
        viewModel = playlistPickerViewModel
    )
}

@Composable
private fun NavidromeRadiosRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeRadiosViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    StandardTopScreen(title = "Radios", onBack = onBack, onHome = onHome) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.radios.isNotEmpty()) {
            items(uiState.radios.size) { index ->
                val radio = uiState.radios[index]
                val isCurrent = playerState.currentTrack?.id == "radio:${radio.id}"
                RadioRow(
                    radio = radio,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && playerState.isPlaying,
                    onClick = { playerViewModel.playRadios(uiState.radios, index) }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load radios.") }
        } else {
            item { EmptyCard("No radios found.") }
        }
    }
}

@Composable
private fun NavidromeFavoriteSongsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeFavoriteSongsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel(),
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingTrackRemoval by remember { mutableStateOf<NavidromeTrack?>(null) }
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(downloadUiState.actionMessage, downloadUiState.errorMessage) {
        val message = downloadUiState.actionMessage ?: downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    StandardTopScreen(
        title = "Favorite Songs",
        onBack = onBack,
        onHome = onHome,
        actions = {
            Box {
                RoundIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "Favorite song options",
                    onClick = { menuExpanded = true }
                )
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppDropdownMenuItem(
                        text = { Text("Clear Favorite Songs") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Favorite, contentDescription = null)
                        },
                        enabled = uiState.songs.isNotEmpty(),
                        onClick = {
                            menuExpanded = false
                            showClearAllConfirmation = true
                        }
                    )
                }
            }
        }
    ) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.songs.isNotEmpty()) {
            items(uiState.songs.size) { index ->
                val track = uiState.songs[index]
                TrackRow(
                    track = track,
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                    isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                    downloadProgressPercent = downloadUiState.trackProgressById[track.id],
                    onClick = { playerViewModel.playTracks(uiState.songs, index) },
                    trailingContent = {
                        var rowMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { rowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Favorite song options"
                                )
                            }
                            NavidromeTrackActionsMenu(
                                expanded = rowMenuExpanded,
                                onDismissRequest = { rowMenuExpanded = false },
                                onPlayTrack = {
                                    rowMenuExpanded = false
                                    playerViewModel.playTracks(uiState.songs, index)
                                },
                                playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                onShowAlbum = onOpenAlbum?.let { openAlbum ->
                                    track.albumId?.let { albumId ->
                                        {
                                            rowMenuExpanded = false
                                            openAlbum(albumId)
                                        }
                                    }
                                },
                                onShowArtist = onOpenArtist?.let { openArtist ->
                                    track.artistId?.let { artistId ->
                                        {
                                            rowMenuExpanded = false
                                            openArtist(artistId)
                                        }
                                    }
                                },
                                extraActions = {
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = {
                                            Text(
                                                if (downloadUiState.downloadedTrackIds.contains(track.id)) {
                                                    "Remove Download"
                                                } else {
                                                    "Download"
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Download,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            downloadsViewModel.toggleTrackDownload(track)
                                        }
                                    )
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.QueueMusic,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                                        }
                                    )
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = { Text("Remove Favorite") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Favorite,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            pendingTrackRemoval = track
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load favorite songs.") }
        } else {
            item { EmptyCard("No favorite songs yet.") }
        }
    }
    pendingTrackRemoval?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingTrackRemoval = null },
            title = { Text("Remove Favorite") },
            text = { Text("Remove \"${track.title}\" from Favorite Songs?") },
            dismissButton = {
                TextButton(onClick = { pendingTrackRemoval = null }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFavoriteTrack(track.id)
                        pendingTrackRemoval = null
                    }
                ) {
                    Text("Remove")
                }
            }
        )
    }
    NavidromePlaylistPickerHost(
        pendingRequest = pendingPlaylistRequest,
        onDismiss = { pendingPlaylistRequest = null },
        viewModel = playlistPickerViewModel
    )
    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            title = { Text("Clear Favorite Songs") },
            text = { Text("Remove all songs from this local Favorite Songs list?") },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearFavorites()
                        showClearAllConfirmation = false
                    }
                ) {
                    Text("Clear All")
                }
            }
        )
    }
}

@Composable
private fun NavidromeSongsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeSongsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val showSearchField = searchExpanded || uiState.searchQuery.isNotBlank()
    val displayedSongs = remember(uiState.songs, uiState.sortOption, uiState.searchQuery) {
        val normalizedQuery = uiState.searchQuery.trim()
        val filtered = uiState.songs.filter { track ->
            normalizedQuery.isBlank() ||
                track.title.contains(normalizedQuery, ignoreCase = true) ||
                track.artistName.contains(normalizedQuery, ignoreCase = true) ||
                track.albumName.contains(normalizedQuery, ignoreCase = true)
        }
        when (uiState.sortOption) {
            NavidromeSongSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            NavidromeSongSortOption.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase(Locale.getDefault()) }
            NavidromeSongSortOption.ARTIST -> filtered.sortedWith(
                compareBy<NavidromeTrack> { it.artistName.lowercase(Locale.getDefault()) }
                    .thenBy { it.albumName.lowercase(Locale.getDefault()) }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
            NavidromeSongSortOption.ALBUM -> filtered.sortedWith(
                compareBy<NavidromeTrack> { it.albumName.lowercase(Locale.getDefault()) }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
        }
    }
    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    StandardTopScreen(
        title = "Songs",
        onBack = onBack,
        onHome = onHome,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = if (showSearchField) "Hide song search" else "Show song search",
                    onClick = { searchExpanded = !showSearchField }
                )
                Box {
                    RoundIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = "Song options",
                        onClick = { menuExpanded = true }
                    )
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        NavidromeSongSortOption.entries.forEach { sort ->
                            AppDropdownMenuItem(
                                text = { Text("Sort: ${sort.label}") },
                                trailingIcon = {
                                    if (uiState.sortOption == sort) {
                                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setSortOption(sort)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        if (showSearchField) {
            item {
                NavidromeExpandableSearchField(
                    visible = true,
                    query = uiState.searchQuery,
                    label = "Search songs",
                    onQueryChange = viewModel::onSearchQueryChange
                )
            }
        }
        if (displayedSongs.isNotEmpty()) {
            item {
                NavidromeTransportHeader(
                    modifier = Modifier.fillMaxWidth(),
                    onPlay = {
                        playerViewModel.playTracks(
                            tracks = displayedSongs,
                            startIndex = 0,
                            queueDisplayMode = NavidromeQueueDisplayMode.SONGS_TAB_PREVIEW
                        )
                    },
                    onShuffle = {
                        playerViewModel.playTracks(
                            tracks = displayedSongs.shuffled(),
                            startIndex = 0,
                            queueDisplayMode = NavidromeQueueDisplayMode.SONGS_TAB_PREVIEW
                        )
                    }
                )
            }
        }
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (displayedSongs.isNotEmpty()) {
            items(displayedSongs.size) { index ->
                val track = displayedSongs[index]
                TrackRow(
                    track = track,
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                    isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                    downloadProgressPercent = downloadUiState.trackProgressById[track.id],
                    onClick = {
                        playerViewModel.playTracks(
                            tracks = displayedSongs,
                            startIndex = index,
                            queueDisplayMode = NavidromeQueueDisplayMode.SONGS_TAB_PREVIEW
                        )
                    },
                    trailingContent = {
                        var rowMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { rowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Song options"
                                )
                            }
                            NavidromeTrackActionsMenu(
                                expanded = rowMenuExpanded,
                                onDismissRequest = { rowMenuExpanded = false },
                                onPlayTrack = {
                                    rowMenuExpanded = false
                                    playerViewModel.playTracks(
                                        tracks = displayedSongs,
                                        startIndex = index,
                                        queueDisplayMode = NavidromeQueueDisplayMode.SONGS_TAB_PREVIEW
                                    )
                                },
                                playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                                onToggleDownload = {
                                    rowMenuExpanded = false
                                    downloadsViewModel.toggleTrackDownload(track)
                                },
                                onShowAlbum = track.albumId?.let { albumId ->
                                    {
                                        rowMenuExpanded = false
                                        onOpenAlbum(albumId)
                                    }
                                },
                                onShowArtist = track.artistId?.let { artistId ->
                                    {
                                        rowMenuExpanded = false
                                        onOpenArtist(artistId)
                                    }
                                },
                                extraActions = {
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.QueueMusic,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        } else if (uiState.errorMessage != null) {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load songs.") }
        } else {
            item {
                EmptyCard(
                    if (uiState.searchQuery.isBlank()) {
                        "No songs found."
                    } else {
                        "No songs match \"${uiState.searchQuery.trim()}\"."
                    }
                )
            }
        }
    }
    NavidromePlaylistPickerHost(
        pendingRequest = pendingPlaylistRequest,
        onDismiss = { pendingPlaylistRequest = null },
        viewModel = playlistPickerViewModel
    )
}

@Composable
private fun NavidromeSearchRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: NavidromeSearchViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val query = uiState.query.trim()
    val recentSearchTerms = uiState.recentSearchTerms
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }
    val noMatches = query.isNotBlank() &&
        !uiState.isLoading &&
        uiState.errorMessage.isNullOrBlank() &&
        uiState.results.artists.isEmpty() &&
        uiState.results.albums.isEmpty() &&
        uiState.results.tracks.isEmpty()
    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    StandardTopScreen(
        title = "Search",
        onBack = onBack,
        onHome = onHome,
        topContent = {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Artists, albums, songs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.submitSearch()
                })
            )
        }
    ) {
        if (query.isBlank()) {
            if (recentSearchTerms.isNotEmpty()) {
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
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxSize()
                            .padding(vertical = 24.dp),
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
                        Text(
                            text = "Search your music",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search by artist, album, or song",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            if (uiState.isLoading) {
                item { LoadingCard() }
            }
            if (uiState.results.artists.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(uiState.results.artists) { artist ->
                    ArtistRow(
                        artist = artist,
                        isDownloaded = downloadUiState.fullyDownloadedAlbumCountByArtistId[artist.id]
                            ?.let { it >= artist.albumCount && artist.albumCount > 0 }
                            ?: false,
                        downloadProgressPercent = downloadUiState.artistProgressById[artist.id],
                        onClick = {
                            viewModel.commitCurrentQuery()
                            onOpenArtist(artist.id)
                        }
                    )
                }
            }
            if (uiState.results.albums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(uiState.results.albums) { album ->
                    AlbumRow(
                        album = album,
                        isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                            ?.let { it >= album.songCount && album.songCount > 0 }
                            ?: false,
                        downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                        onClick = {
                            viewModel.commitCurrentQuery()
                            onOpenAlbum(album.id)
                        }
                    )
                }
            }
            if (uiState.results.tracks.isNotEmpty()) {
                item { SectionTitle("Songs") }
                itemsIndexed(uiState.results.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.currentTrack?.id == track.id,
                        isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                        isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                        downloadProgressPercent = downloadUiState.trackProgressById[track.id],
                        onClick = {
                            viewModel.commitCurrentQuery()
                            playerViewModel.playTracks(uiState.results.tracks, index)
                        },
                        trailingContent = {
                            var rowMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { rowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "Song options"
                                    )
                                }
                                NavidromeTrackActionsMenu(
                                    expanded = rowMenuExpanded,
                                    onDismissRequest = { rowMenuExpanded = false },
                                    onPlayTrack = {
                                        rowMenuExpanded = false
                                        viewModel.commitCurrentQuery()
                                        playerViewModel.playTracks(uiState.results.tracks, index)
                                    },
                                    playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                    isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                                    onToggleDownload = {
                                        rowMenuExpanded = false
                                        downloadsViewModel.toggleTrackDownload(track)
                                    },
                                    onShowAlbum = track.albumId?.let { albumId ->
                                        {
                                            rowMenuExpanded = false
                                            viewModel.commitCurrentQuery()
                                            onOpenAlbum(albumId)
                                        }
                                    },
                                    onShowArtist = track.artistId?.let { artistId ->
                                        {
                                            rowMenuExpanded = false
                                            viewModel.commitCurrentQuery()
                                            onOpenArtist(artistId)
                                        }
                                    },
                                    extraActions = {
                                        HorizontalDivider()
                                        AppDropdownMenuItem(
                                            text = { Text("Add to Playlist") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.QueueMusic,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                rowMenuExpanded = false
                                                pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
            if (uiState.errorMessage != null) {
                item { ErrorCard(uiState.errorMessage ?: "Search failed.") }
            }
            if (noMatches) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = 36.dp),
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
    }
    NavidromePlaylistPickerHost(
        pendingRequest = pendingPlaylistRequest,
        onDismiss = { pendingPlaylistRequest = null },
        viewModel = playlistPickerViewModel
    )
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
private fun NavidromeArtistDetailRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onOpenAlbum: (String) -> Unit,
    viewModel: NavidromeArtistDetailViewModel = hiltViewModel(),
    albumsViewModel: NavidromeAlbumsViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayStyle by albumsViewModel.layoutMode.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val bottomOverlayPadding = LocalNavidromeBottomOverlayPadding.current
    val context = LocalContext.current
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = "Artist",
            onBack = onBack,
            onHome = onHome,
            actions = {
                Box {
                    RoundIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = "Artist album options",
                        onClick = { menuExpanded = true }
                    )
                    AppDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        AppDropdownMenuItem(
                            text = { Text("Grid view") },
                            leadingIcon = {
                                if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.GRID)
                                menuExpanded = false
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("List view") },
                            leadingIcon = {
                                if (displayStyle == NavidromeAlbumsDisplayStyle.LIST) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                albumsViewModel.setLayoutMode(NavidromeAlbumsDisplayStyle.LIST)
                                menuExpanded = false
                            }
                        )
                        uiState.detail?.let { detail ->
                            HorizontalDivider()
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        if (
                                            downloadUiState.fullyDownloadedAlbumCountByArtistId[detail.artist.id]
                                                ?.let { it >= detail.artist.albumCount && detail.artist.albumCount > 0 }
                                                ?: false
                                        ) {
                                            "Remove Artist Downloads"
                                        } else {
                                            "Download Artist"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    downloadsViewModel.toggleArtistDownload(detail.artist, detail.albums)
                                }
                            )
                        }
                    }
                }
            }
        )
        if (uiState.isLoading) {
            LoadingCard()
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = bottomOverlayPadding + 25.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CenteredDetailHero(
                        title = detail.artist.name,
                        subtitle = "${detail.artist.albumCount} albums",
                        imageUrl = detail.artist.imageUrl ?: detail.artist.coverUrl,
                        circular = true,
                        showDownloadedIndicator = downloadUiState.fullyDownloadedAlbumCountByArtistId[detail.artist.id]
                            ?.let { it >= detail.artist.albumCount && detail.artist.albumCount > 0 }
                            ?: false,
                        downloadProgressPercent = downloadUiState.artistProgressById[detail.artist.id]
                    )
                }
                item { SectionTitle("Albums") }
                if (displayStyle == NavidromeAlbumsDisplayStyle.GRID) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            detail.albums.chunked(2).forEach { albumRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    albumRow.forEach { album ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            AlbumGridCard(
                                                album = album,
                                                isCurrent = playerState.currentTrack?.albumId == album.id,
                                                isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                                    ?.let { it >= album.songCount && album.songCount > 0 }
                                                    ?: false,
                                                downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                                                onClick = { onOpenAlbum(album.id) }
                                            )
                                        }
                                    }
                                    if (albumRow.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(detail.albums) { album ->
                        AlbumRow(
                            album = album,
                            isCurrent = playerState.currentTrack?.albumId == album.id,
                            isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[album.id]
                                ?.let { it >= album.songCount && album.songCount > 0 }
                                ?: false,
                            downloadProgressPercent = downloadUiState.albumProgressById[album.id],
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                }
            }
        } else {
            ErrorCard(uiState.errorMessage ?: "Unable to load artist.")
        }
    }
}

@Composable
private fun NavidromeAlbumDetailRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeAlbumDetailViewModel = hiltViewModel(),
    playerViewModel: NavidromePlayerViewModel = hiltViewModel(),
    playlistPickerViewModel: NavidromePlaylistPickerViewModel = hiltViewModel(),
    downloadsViewModel: NavidromeDownloadsViewModel = hiltViewModel(),
    onOpenArtist: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val downloadUiState by downloadsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingPlaylistRequest by remember { mutableStateOf<NavidromePlaylistSelectionRequest?>(null) }
    LaunchedEffect(downloadUiState.actionMessage) {
        val message = downloadUiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    LaunchedEffect(downloadUiState.errorMessage) {
        val message = downloadUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        downloadsViewModel.clearMessages()
    }
    StandardTopScreen(title = "Album", onBack = onBack, onHome = onHome) {
        if (uiState.isLoading) {
            item { LoadingCard() }
        } else if (uiState.detail != null) {
            val detail = uiState.detail!!
            item {
                AlbumDetailHero(
                    detail = detail,
                    onPlayAlbum = { playerViewModel.playTracks(detail.tracks, 0) },
                    onShuffleAlbum = {
                        val shuffledTracks = detail.tracks.shuffled()
                        playerViewModel.playTracks(shuffledTracks, 0)
                    },
                    onAddToPlaylist = detail.toPlaylistSelectionRequest()?.let { request ->
                        { pendingPlaylistRequest = request }
                    },
                    isDownloaded = downloadUiState.downloadedTrackCountByAlbumId[detail.album.id]
                        ?.let { it >= detail.album.songCount && detail.album.songCount > 0 }
                        ?: false,
                    downloadProgressPercent = downloadUiState.albumProgressById[detail.album.id],
                    onToggleDownload = {
                        downloadsViewModel.toggleAlbumDownload(detail.album, detail.tracks)
                    }
                )
            }
            items(detail.tracks.size) { index ->
                val track = detail.tracks[index]
                TrackRow(
                    track = track,
                    isCurrent = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.currentTrack?.id == track.id && playerState.isPlaying,
                    isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                    downloadProgressPercent = downloadUiState.trackProgressById[track.id],
                    onClick = { playerViewModel.playTracks(detail.tracks, index) },
                    trailingContent = {
                        var rowMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { rowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Song options"
                                )
                            }
                            NavidromeTrackActionsMenu(
                                expanded = rowMenuExpanded,
                                onDismissRequest = { rowMenuExpanded = false },
                                onPlayTrack = {
                                    rowMenuExpanded = false
                                    playerViewModel.playTracks(detail.tracks, index)
                                },
                                playLabel = if (playerState.currentTrack?.id == track.id) "Play Again" else "Play",
                                isDownloaded = downloadUiState.downloadedTrackIds.contains(track.id),
                                onToggleDownload = {
                                    rowMenuExpanded = false
                                    downloadsViewModel.toggleTrackDownload(track, detail.album.songCount)
                                },
                                onShowAlbum = null,
                                onShowArtist = onOpenArtist?.let { openArtist ->
                                    track.artistId?.let { artistId ->
                                        {
                                            rowMenuExpanded = false
                                            openArtist(artistId)
                                        }
                                    }
                                },
                                extraActions = {
                                    HorizontalDivider()
                                    AppDropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.QueueMusic,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            rowMenuExpanded = false
                                            pendingPlaylistRequest = track.toPlaylistSelectionRequest()
                                        }
                                    )
                                }
                            )
                        }
                    }
                )
            }
        } else {
            item { ErrorCard(uiState.errorMessage ?: "Unable to load album.") }
        }
    }
    NavidromePlaylistPickerHost(
        pendingRequest = pendingPlaylistRequest,
        onDismiss = { pendingPlaylistRequest = null },
        viewModel = playlistPickerViewModel
    )
}

@Composable
private fun NavidromeSettingsRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onSwitchMode: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyricsSources: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: NavidromeSettingsViewModel = hiltViewModel(),
    equalizerViewModel: NavidromeEqualizerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val equalizerUiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var signOutDialogVisible by rememberSaveable { mutableStateOf(false) }
    var resyncDialogVisible by rememberSaveable { mutableStateOf(false) }
    var serverScanDialogVisible by rememberSaveable { mutableStateOf(false) }
    val sectionCardColor = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val sectionCardBorder = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    } else {
        null
    }
    val activeServerName = uiState.savedServers.firstOrNull { it.id == uiState.activeServerId }?.name
        ?: uiState.session?.serverName
        ?: "Navidrome"
    val lastSyncedValue = uiState.lastLibrarySyncAtMs
        ?.let { timestamp -> "Last synced ${formatNavidromeLastSyncedTimestamp(timestamp)}" }
    LaunchedEffect(uiState.syncToastMessage) {
        val toastMessage = uiState.syncToastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(toastMessage)
        viewModel.consumeSyncToastMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StandardTopScreen(
            title = "Settings",
            onBack = onBack,
            onHome = onHome,
            containerColor = MaterialTheme.colorScheme.background
        ) {
        item {
            Text(
                text = "PRODUCT MODE",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                NavidromeSettingsRow(
                    title = "Selected backend",
                    value = "Navidrome",
                    showChevronWhenValue = false,
                    showChevronWhenUnselected = false,
                    onClick = null
                )
                DividerLine()
                NavidromeSettingsRow(
                    title = "Switch Product Mode",
                    value = "Return to backend selector",
                    onClick = onSwitchMode
                )
            }
        }
        item {
            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                SettingsSwitchRow(
                    title = "Material Design",
                    checked = appearanceUiState.navidromeMaterialDesignEnabled,
                    onCheckedChange = appearanceViewModel::setNavidromeMaterialDesignEnabled
                )
                DividerLine()
                SettingsSwitchRow(
                    title = "Immersive Player",
                    checked = appearanceUiState.navidromeImmersivePlayerEnabled,
                    onCheckedChange = appearanceViewModel::setNavidromeImmersivePlayerEnabled
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Follow System Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.FollowSystem,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.FollowSystem) }
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Light Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.Light,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.Light) }
                )
                DividerLine()
                ThemeSettingsRow(
                    title = "Dark Theme",
                    selected = appearanceUiState.navidromeThemeMode == AppThemeMode.Dark,
                    onClick = { appearanceViewModel.setNavidromeThemeMode(AppThemeMode.Dark) }
                )
            }
        }
        item {
            Text(
                text = "PLAYBACK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                NavidromeSettingsRow(
                    title = "Equalizer",
                    value = if (equalizerUiState.isEnabled) {
                        equalizerUiState.activeProfileName
                    } else {
                        "Off"
                    },
                    valueTextAlign = TextAlign.End,
                    onClick = onOpenEqualizer
                )
                DividerLine()
                NavidromeSettingsRow(
                    title = "Lyrics Sources",
                    value = uiState.lyricsSources.firstOrNull { it.id == uiState.activeLyricsSourceId }?.name
                        ?: if (uiState.lyricsSources.isEmpty()) "Not configured" else "Choose source",
                    valueTextAlign = TextAlign.End,
                    onClick = onOpenLyricsSources
                )
            }
        }
        item {
            Text(
                text = "SESSION",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                NavidromeSettingsRow(
                    title = "Manage Servers",
                    onClick = onOpenServers
                )
                DividerLine()
                NavidromeSyncStatusRow(
                    title = "Resync Library",
                    subtitle = lastSyncedValue,
                    onClick = { resyncDialogVisible = true }
                )
                DividerLine()
                NavidromeSyncStatusRow(
                    title = "Trigger Server Scan",
                    subtitle = "Ask Navidrome to scan for new or changed files.",
                    onClick = { serverScanDialogVisible = true }
                )
                DividerLine()
                NavidromeSettingsRow(
                    title = "Sign Out",
                    onClick = { signOutDialogVisible = true }
                )
            }
        }

        if (resyncDialogVisible) {
            item {
                AlertDialog(
                    onDismissRequest = { resyncDialogVisible = false },
                    title = { Text("Resync library?") },
                    text = {
                        Text(
                            "This refreshes the app's Navidrome library data from the server. It does not trigger a server-side filesystem scan."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                resyncDialogVisible = false
                                viewModel.onResyncLibraryClick()
                            }
                        ) {
                            Text("Resync")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resyncDialogVisible = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (serverScanDialogVisible) {
            item {
                AlertDialog(
                    onDismissRequest = { serverScanDialogVisible = false },
                    title = { Text("Trigger server scan?") },
                    text = {
                        Text(
                            "This asks Navidrome to scan its server library for new or changed music files. It does not refresh the app library by itself."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                serverScanDialogVisible = false
                                viewModel.onTriggerServerScanClick()
                            }
                        ) {
                            Text("Scan")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { serverScanDialogVisible = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        if (signOutDialogVisible) {
            item {
                AlertDialog(
                    onDismissRequest = { signOutDialogVisible = false },
                    title = { Text("Sign out of server?") },
                    text = {
                        Text("You will be signed out from $activeServerName.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                signOutDialogVisible = false
                                viewModel.signOut()
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
        }
        }
        AppThemedSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }

    uiState.resyncProgress?.let { progress ->
        NavidromeResyncProgressDialog(progress = progress)
    }

    if (uiState.showServerScanProgressDialog) {
        uiState.serverScanProgress?.let { progress ->
        NavidromeServerScanDialog(
            progress = progress,
            onDismiss = viewModel::dismissServerScanProgress
        )
        }
    }
}

@Composable
private fun NavidromeEqualizerRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeEqualizerViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val sectionCardColor = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val sectionCardBorder = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    } else {
        null
    }
    var activeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var editorMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var editorExpanded by rememberSaveable { mutableStateOf(false) }
    var showPreampDialog by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable(uiState.editorProfile.id) { mutableStateOf(uiState.editorProfile.name) }

    StandardTopScreen(
        title = "Equalizer",
        onBack = onBack,
        onHome = onHome,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        item {
            Text(
                text = "PLAYBACK",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                SettingsSwitchRow(
                    title = "Enable Equalizer",
                    checked = uiState.isEnabled,
                    onCheckedChange = viewModel::setEnabled
                )
                DividerLine()
                Box {
                    NavidromeSettingsRow(
                        title = "Active Equalizer",
                        value = uiState.activeProfileName,
                        valueTextAlign = TextAlign.End,
                        onClick = { activeMenuExpanded = true }
                    )
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        AppDropdownMenu(
                            expanded = activeMenuExpanded,
                            onDismissRequest = { activeMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 44.dp)
                        ) {
                            AppDropdownMenuItem(
                                text = { Text("Off") },
                                trailingIcon = {
                                    if (uiState.activeProfileId == null) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    activeMenuExpanded = false
                                    viewModel.setActiveProfile(null)
                                }
                            )
                            uiState.profiles.forEach { profile ->
                                AppDropdownMenuItem(
                                    text = { Text(profile.name) },
                                    trailingIcon = {
                                        if (uiState.activeProfileId == profile.id) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        activeMenuExpanded = false
                                        viewModel.setActiveProfile(profile.id)
                                    }
                                )
                            }
                        }
                    }
                }
                DividerLine()
                NavidromeSettingsRow(
                    title = "Preamp",
                    value = formatNavidromePreampLevelLabel(uiState.preampLevel),
                    valueTextAlign = TextAlign.End,
                    onClick = { showPreampDialog = true }
                )
            }
        }
        item {
            Text(
                text = "EQUALIZER EDITOR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
            )
        }
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                Box {
                    NavidromeSettingsRow(
                        title = "Equalizer",
                        value = if (editorExpanded) uiState.editorProfile.name else "Select",
                        valueTextAlign = TextAlign.End,
                        onClick = { editorMenuExpanded = true }
                    )
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        AppDropdownMenu(
                            expanded = editorMenuExpanded,
                            onDismissRequest = { editorMenuExpanded = false },
                            offset = DpOffset(x = 0.dp, y = 44.dp)
                        ) {
                            uiState.profiles.forEach { profile ->
                                AppDropdownMenuItem(
                                    text = { Text(profile.name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Tune,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = {
                                        if (uiState.isEditorPersisted && uiState.editorProfile.id == profile.id) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        editorMenuExpanded = false
                                        viewModel.selectEditorProfile(profile.id)
                                        editorExpanded = true
                                    }
                                )
                            }
                            if (uiState.profiles.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            AppDropdownMenuItem(
                                text = { Text("Create new Equalizer") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    editorMenuExpanded = false
                                    viewModel.createNewProfile()
                                    editorExpanded = true
                                }
                            )
                        }
                    }
                }
                if (editorExpanded) {
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Name",
                        value = uiState.editorProfile.name,
                        valueTextAlign = TextAlign.End,
                        onClick = {
                            nameDraft = uiState.editorProfile.name
                            showNameDialog = true
                        }
                    )
                    DividerLine()
                    NavidromeEqualizerChart(
                        bandLevelsDb = uiState.editorProfile.normalizedBandLevelsDb(),
                        onBandLevelChange = viewModel::updateBandLevel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                    )
                    DividerLine()
                    NavidromeEqualizerActionRow(
                        title = "Save",
                        enabled = uiState.canSave,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = viewModel::saveEditor
                    )
                    DividerLine()
                    NavidromeEqualizerActionRow(
                        title = "Delete",
                        enabled = uiState.canDelete,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = viewModel::deleteEditor
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Equalizer name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(40) },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateEditorName(nameDraft.trim())
                        showNameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPreampDialog) {
        NavidromeEffectSliderDialog(
            title = "Preamp",
            subtitle = "Increase overall playback loudness after EQ.",
            value = uiState.preampLevel,
            onValueChange = viewModel::setPreampLevel,
            onDismiss = { showPreampDialog = false }
        )
    }
}

@Composable
private fun NavidromeEqualizerActionRow(
    title: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun NavidromeEffectSliderDialog(
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
                    text = formatNavidromePreampLevelLabel(value),
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

@Composable
private fun NavidromeEqualizerChart(
    bandLevelsDb: List<Float>,
    onBandLevelChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedLevels = navidromeEqualizerBandFrequenciesHz.indices.map { index ->
        bandLevelsDb.getOrNull(index)?.coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB) ?: 0f
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val zeroGuideColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    val stepDotColor = accentColor.copy(alpha = 0.99f)
    val zeroStepDotColor = accentColor.copy(alpha = 0.95f)
    val chartHeight = 220.dp
    val chartVerticalInset = 10.dp
    val totalChartHeight = chartHeight + (chartVerticalInset * 2)
    val bandSpacing = 2.dp
    val controlButtonSize = 25.dp
    val controlSectionGap = 6.dp
    val chartLabelValues = remember {
        listOf(6f, 4f, 2f, 0f, -2f, -4f, -6f)
    }
    val chartGridValues = remember {
        (NAVIDROME_EQUALIZER_MAX_DB.toInt() downTo NAVIDROME_EQUALIZER_MIN_DB.toInt()).map(Int::toFloat)
    }
    val density = LocalDensity.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Spacer(modifier = Modifier.height(controlButtonSize + controlSectionGap))
            BoxWithConstraints(
                modifier = Modifier
                    .width(40.dp)
                    .requiredHeight(totalChartHeight)
            ) {
                val chartHeightPx = with(density) { chartHeight.toPx() }
                chartLabelValues.forEach { db ->
                    val labelOffset = with(density) {
                        (chartVerticalInset + levelToChartY(db, chartHeightPx).toDp() - 10.dp)
                            .coerceIn(0.dp, totalChartHeight - 20.dp)
                    }
                    Text(
                        text = if (db > 0) "${db.toInt()} dB" else if (db == 0f) "0 dB" else "${db.toInt()} dB",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (db == 0f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (db == 0f) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = labelOffset)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(bandSpacing)
            ) {
                normalizedLevels.forEachIndexed { index, levelDb ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        NavidromeEqualizerStepButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "Increase ${formatNavidromeEqualizerFrequencyLabel(navidromeEqualizerBandFrequenciesHz[index])}",
                            enabled = levelDb < NAVIDROME_EQUALIZER_MAX_DB,
                            size = controlButtonSize,
                            onClick = {
                                onBandLevelChange(
                                    index,
                                    snapNavidromeEqualizerLevel(levelDb + NAVIDROME_EQUALIZER_STEP_DB)
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(controlSectionGap))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(totalChartHeight)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = chartVerticalInset)
                ) {
                    val bandCount = navidromeEqualizerBandFrequenciesHz.size
                    val bandSpacingPx = bandSpacing.toPx()
                    val bandWidthPx = ((size.width - (bandSpacingPx * (bandCount - 1))) / bandCount)
                        .coerceAtLeast(0f)
                    chartGridValues.forEach { levelValue ->
                        val y = levelToChartY(levelValue, size.height)
                        val isZeroLine = abs(levelValue) < 0.001f
                        val isLabeledLine = levelValue.toInt() % 2 == 0
                        drawLine(
                            color = when {
                                isZeroLine -> zeroGuideColor
                                isLabeledLine -> guideColor.copy(alpha = 0.82f)
                                else -> guideColor.copy(alpha = 0.38f)
                            },
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = when {
                                isZeroLine -> 1.8.dp.toPx()
                                isLabeledLine -> 1.1.dp.toPx()
                                else -> 0.8.dp.toPx()
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = chartVerticalInset),
                    horizontalArrangement = Arrangement.spacedBy(bandSpacing)
                ) {
                    normalizedLevels.forEachIndexed { index, levelDb ->
                        NavidromeEqualizerBandSlider(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            levelDb = levelDb,
                            accentColor = accentColor,
                            trackColor = trackColor,
                            stepDotColor = stepDotColor,
                            zeroStepDotColor = zeroStepDotColor,
                            onLevelChange = { onBandLevelChange(index, it) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(controlSectionGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(bandSpacing)
            ) {
                normalizedLevels.forEachIndexed { index, levelDb ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        NavidromeEqualizerStepButton(
                            icon = Icons.Outlined.Remove,
                            contentDescription = "Decrease ${formatNavidromeEqualizerFrequencyLabel(navidromeEqualizerBandFrequenciesHz[index])}",
                            enabled = levelDb > NAVIDROME_EQUALIZER_MIN_DB,
                            size = controlButtonSize,
                            onClick = {
                                onBandLevelChange(
                                    index,
                                    snapNavidromeEqualizerLevel(levelDb - NAVIDROME_EQUALIZER_STEP_DB)
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(bandSpacing)
            ) {
                navidromeEqualizerBandFrequenciesHz.forEach { frequency ->
                    Text(
                        text = formatNavidromeEqualizerFrequencyLabel(frequency),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavidromeEqualizerBandSlider(
    levelDb: Float,
    accentColor: Color,
    trackColor: Color,
    stepDotColor: Color,
    zeroStepDotColor: Color,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val knobBorderColor = accentColor.copy(alpha = 0.99f)
    val knobFillColor = MaterialTheme.colorScheme.surface
    var lastStepIndex by remember { mutableIntStateOf(navidromeEqualizerStepIndex(levelDb)) }
    var isPressed by remember { mutableStateOf(false) }
    val knobRadius by animateDpAsState(
        targetValue = if (isPressed) 13.dp else 12.dp,
        animationSpec = tween(durationMillis = 120),
        label = "navidromeEqKnobRadius"
    )

    LaunchedEffect(levelDb) {
        lastStepIndex = navidromeEqualizerStepIndex(levelDb)
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                fun updateFromY(y: Float) {
                    val heightPx = size.height.toFloat().takeIf { it > 0f } ?: return
                    val ratio = 1f - (y / heightPx).coerceIn(0f, 1f)
                    val snappedLevel = snapNavidromeEqualizerLevel(
                        NAVIDROME_EQUALIZER_MIN_DB +
                            ((NAVIDROME_EQUALIZER_MAX_DB - NAVIDROME_EQUALIZER_MIN_DB) * ratio)
                    )
                    val stepIndex = navidromeEqualizerStepIndex(snappedLevel)
                    if (stepIndex != lastStepIndex) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastStepIndex = stepIndex
                    }
                    onLevelChange(snappedLevel)
                }
                detectTapGestures(
                    onPress = { offset ->
                        isPressed = true
                        updateFromY(offset.y)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { offset ->
                        updateFromY(offset.y)
                    }
                )
            }
            .pointerInput(Unit) {
                fun updateFromY(y: Float) {
                    val heightPx = size.height.toFloat().takeIf { it > 0f } ?: return
                    val ratio = 1f - (y / heightPx).coerceIn(0f, 1f)
                    val snappedLevel = snapNavidromeEqualizerLevel(
                        NAVIDROME_EQUALIZER_MIN_DB +
                            ((NAVIDROME_EQUALIZER_MAX_DB - NAVIDROME_EQUALIZER_MIN_DB) * ratio)
                    )
                    val stepIndex = navidromeEqualizerStepIndex(snappedLevel)
                    if (stepIndex != lastStepIndex) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastStepIndex = stepIndex
                    }
                    onLevelChange(snappedLevel)
                }
                detectDragGestures(
                    onDragStart = { offset ->
                        isPressed = true
                        updateFromY(offset.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromY(change.position.y)
                    },
                    onDragEnd = { isPressed = false },
                    onDragCancel = { isPressed = false }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = size.width / 2f
            val trackStrokeWidth = 5.dp.toPx()
            val activeTrackStrokeWidth = 6.5.dp.toPx()
            val zeroY = levelToChartY(0f, size.height)
            val markerY = levelToChartY(levelDb, size.height)
            val knobRadiusPx = knobRadius.toPx()
            drawLine(
                color = trackColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = trackStrokeWidth,
                cap = StrokeCap.Round
            )
            for (step in NAVIDROME_EQUALIZER_MAX_DB.toInt() downTo NAVIDROME_EQUALIZER_MIN_DB.toInt()) {
                val y = levelToChartY(step.toFloat(), size.height)
                drawCircle(
                    color = if (step == 0) zeroStepDotColor else stepDotColor,
                    radius = if (step == 0) 2.8.dp.toPx() else 2.35.dp.toPx(),
                    center = Offset(centerX, y)
                )
            }
            drawLine(
                color = accentColor,
                start = Offset(centerX, zeroY),
                end = Offset(centerX, markerY),
                strokeWidth = activeTrackStrokeWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = knobFillColor,
                radius = knobRadiusPx,
                center = Offset(centerX, markerY)
            )
            drawCircle(
                color = knobBorderColor,
                radius = knobRadiusPx,
                center = Offset(centerX, markerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun NavidromeEqualizerStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .clickable(enabled = enabled) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(12.dp)
        )
    }
}

private fun levelToChartY(levelDb: Float, heightPx: Float): Float {
    val clamped = levelDb.coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)
    val ratio = (NAVIDROME_EQUALIZER_MAX_DB - clamped) /
        (NAVIDROME_EQUALIZER_MAX_DB - NAVIDROME_EQUALIZER_MIN_DB)
    return heightPx * ratio
}

private fun snapNavidromeEqualizerLevel(levelDb: Float): Float {
    return ((levelDb.coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)) / NAVIDROME_EQUALIZER_STEP_DB)
        .roundToInt()
        .toFloat() * NAVIDROME_EQUALIZER_STEP_DB
}

private fun navidromeEqualizerStepIndex(levelDb: Float): Int {
    return ((snapNavidromeEqualizerLevel(levelDb) - NAVIDROME_EQUALIZER_MIN_DB) / NAVIDROME_EQUALIZER_STEP_DB)
        .roundToInt()
}

private fun formatNavidromeEqualizerFrequencyLabel(frequencyHz: Int): String {
    return if (frequencyHz >= 1_000) {
        val wholeK = frequencyHz / 1_000
        if (frequencyHz % 1_000 == 0) {
            "${wholeK}k"
        } else {
            "${frequencyHz / 1000f}k"
        }
    } else {
        frequencyHz.toString()
    }
}

private fun formatNavidromePreampLevelLabel(level: Float): String {
    val percent = (level.coerceIn(0f, 1f) * 100f).roundToInt()
    return "$percent%"
}

@Composable
private fun NavidromeServersManagementRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    onAddServer: () -> Unit,
    viewModel: NavidromeSettingsViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingServer by remember { mutableStateOf<com.stillshelf.app.ui.screens.SettingsServerOption?>(null) }
    var deletingServer by remember { mutableStateOf<com.stillshelf.app.ui.screens.SettingsServerOption?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var editingUrl by rememberSaveable { mutableStateOf("") }
    var editingError by rememberSaveable { mutableStateOf<String?>(null) }
    var advancedUrlActionTarget by remember { mutableStateOf<String?>(null) }
    var advancedUrlPickerTarget by remember { mutableStateOf<String?>(null) }
    var advancedUrlDialogTarget by remember { mutableStateOf<String?>(null) }
    var advancedUrlDraft by rememberSaveable { mutableStateOf("") }
    var advancedUrlError by rememberSaveable { mutableStateOf<String?>(null) }
    val activeServerName = uiState.savedServers.firstOrNull { it.id == uiState.activeServerId }?.name
        ?: uiState.session?.serverName
        ?: "Navidrome"
    val hasLocalServer = uiState.lanServerUrl.isNotBlank()
    val hasRemoteServer = uiState.wanServerUrl.isNotBlank()
    val hasRoutingPair = hasLocalServer && hasRemoteServer
    val sectionCardColor = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val sectionCardBorder = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    } else {
        null
    }
    val statusValue = remember(uiState.connectionStatusLabel, uiState.connectionLatencyMs) {
        buildString {
            append(uiState.connectionStatusLabel)
            uiState.connectionLatencyMs?.takeIf { uiState.connectionStatusLabel == "Reachable" }?.let { latencyMs ->
                append(" • ")
                append("$latencyMs ms")
            }
        }
    }
    fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    LaunchedEffect(uiState.syncToastMessage) {
        val message = uiState.syncToastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSyncToastMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StandardTopScreen(
            title = "Manage Servers",
            onBack = onBack,
            onHome = onHome,
            containerColor = MaterialTheme.colorScheme.background
        ) {
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                uiState.savedServers.forEachIndexed { index, server ->
                    var rowMenuExpanded by remember(server.id) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingServer = server
                                editingName = server.name
                                editingUrl = server.baseUrl
                                editingError = null
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = server.host,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (server.id == uiState.activeServerId) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(onClick = { rowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = "Server actions"
                                )
                            }
                            AppDropdownMenu(
                                expanded = rowMenuExpanded,
                                onDismissRequest = { rowMenuExpanded = false }
                            ) {
                                AppDropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        rowMenuExpanded = false
                                        deletingServer = server
                                    }
                                )
                            }
                        }
                    }
                    if (index < uiState.savedServers.lastIndex) {
                        DividerLine()
                    }
                }
            }
        }
        item {
            Button(
                onClick = onAddServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppScreenHorizontalPadding)
            ) {
                Text("Add Server")
            }
        }
        if (uiState.activeServerId != null) {
            item {
                Text(
                    text = "AUTOMATIC SERVER ROUTING",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
                )
            }
            item {
                GroupedSettingsCard(
                    containerColor = sectionCardColor,
                    border = sectionCardBorder
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "For: $activeServerName",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Set the local and remote addresses for this server. The app can use the local one on home Wi-Fi and the remote one everywhere else.\n",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tip: Set the server to \"Remote\" in the drop-down menu on the home screen for best results. Automatic Server Routing switches to the local address when you're on home Wi-Fi and falls back to the remote address if the local network is unavailable. If the selected server does not have a local address configured, this feature will have no effect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!hasRoutingPair) {
                            Text(
                                text = "Add both local and remote addresses to enable this feature.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DividerLine()
                    SettingsSwitchRow(
                        title = "Use Local Server at Home",
                        checked = uiState.automaticServerSwitchingEnabled,
                        enabled = hasRoutingPair || uiState.automaticServerSwitchingEnabled,
                        onCheckedChange = viewModel::setAutomaticServerSwitchingEnabled
                    )
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Connection",
                        value = uiState.currentConnectionLabel.ifBlank { "Not configured" },
                        trailingContentWidth = 168.dp,
                        valueTextAlign = TextAlign.End,
                        showChevronWhenValue = false,
                        showChevronWhenUnselected = false,
                        onClick = null
                    )
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Status",
                        value = statusValue,
                        trailingContentWidth = 168.dp,
                        valueTextAlign = TextAlign.End,
                        showChevronWhenValue = false,
                        showChevronWhenUnselected = false,
                        onClick = null
                    )
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Current endpoint",
                        titleMaxLines = 2,
                        value = formatServerAddressForDisplay(uiState.currentEndpointUrl),
                        trailingContentWidth = 216.dp,
                        valueTextAlign = TextAlign.End,
                        forceTitleTwoLineHeight = true,
                        showChevronWhenValue = false,
                        showChevronWhenUnselected = false,
                        onClick = null,
                        trailingActionIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                        trailingActionContentDescription = "Open current endpoint",
                        onTrailingActionClick = {
                            if (uiState.currentEndpointUrl.isNotBlank()) {
                                openUrl(uiState.currentEndpointUrl)
                            }
                        }
                    )
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Local\nServer",
                        value = uiState.lanServerUrl.takeIf { it.isNotBlank() }?.let(::formatServerAddressForDisplay)
                            ?: "Not set",
                        titleMaxLines = 2,
                        trailingContentWidth = 216.dp,
                        valueTextAlign = TextAlign.End,
                        forceTitleTwoLineHeight = true,
                        onClick = {
                            advancedUrlActionTarget = "LOCAL"
                            advancedUrlError = null
                        },
                        trailingActionIcon = uiState.lanServerUrl.takeIf { it.isNotBlank() }?.let { Icons.AutoMirrored.Outlined.OpenInNew },
                        trailingActionContentDescription = "Open local server",
                        onTrailingActionClick = uiState.lanServerUrl.takeIf { it.isNotBlank() }?.let {
                            { openUrl(it) }
                        }
                    )
                    DividerLine()
                    NavidromeSettingsRow(
                        title = "Remote\nServer",
                        value = uiState.wanServerUrl.takeIf { it.isNotBlank() }?.let(::formatServerAddressForDisplay)
                            ?: "Not set",
                        titleMaxLines = 2,
                        trailingContentWidth = 216.dp,
                        valueTextAlign = TextAlign.End,
                        forceTitleTwoLineHeight = true,
                        onClick = {
                            advancedUrlActionTarget = "REMOTE"
                            advancedUrlError = null
                        },
                        trailingActionIcon = uiState.wanServerUrl.takeIf { it.isNotBlank() }?.let { Icons.AutoMirrored.Outlined.OpenInNew },
                        trailingActionContentDescription = "Open remote server",
                        onTrailingActionClick = uiState.wanServerUrl.takeIf { it.isNotBlank() }?.let {
                            { openUrl(it) }
                        }
                    )
                }
            }
        }
        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
                )
            }
        }
        }
        AppThemedSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        )
    }

    editingServer?.let { server ->
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
                        viewModel.updateServer(server.id, editingName.trim(), trimmedUrl)
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

    deletingServer?.let { server ->
        AlertDialog(
            onDismissRequest = { deletingServer = null },
            title = { Text("Delete Server?") },
            text = { Text("Remove ${server.name} from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServer(server.id)
                        deletingServer = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingServer = null }) { Text("Cancel") }
            }
        )
    }

    advancedUrlActionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                advancedUrlActionTarget = null
                advancedUrlError = null
            },
            title = {
                Text(if (target == "REMOTE") "Remote Server" else "Local Server")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose how to fill this address. Saved server choices copy the URL as plain text only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavidromeDialogActionRow(
                        title = "Enter manually",
                        onClick = {
                            advancedUrlActionTarget = null
                            advancedUrlDialogTarget = target
                            advancedUrlDraft = if (target == "REMOTE") uiState.wanServerUrl else uiState.lanServerUrl
                            advancedUrlError = null
                        }
                    )
                    HorizontalDivider()
                    NavidromeDialogActionRow(
                        title = "Choose from saved servers",
                        onClick = {
                            advancedUrlActionTarget = null
                            advancedUrlPickerTarget = target
                        }
                    )
                    HorizontalDivider()
                    NavidromeDialogActionRow(
                        title = "Clear",
                        onClick = {
                            advancedUrlActionTarget = null
                            advancedUrlError = null
                            if (target == "REMOTE") {
                                viewModel.updateWanServerUrl("")
                            } else {
                                viewModel.updateLanServerUrl("")
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        advancedUrlActionTarget = null
                        advancedUrlError = null
                    }
                ) { Text("Close") }
            }
        )
    }

    advancedUrlPickerTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { advancedUrlPickerTarget = null },
            title = {
                Text(if (target == "REMOTE") "Choose Remote Server" else "Choose Local Server")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Selecting a saved server copies its address into this field. It does not create a link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.savedServers.forEachIndexed { index, server ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        NavidromeSettingsRow(
                            title = server.name,
                            value = server.host,
                            showChevronWhenValue = false,
                            onClick = {
                                if (target == "REMOTE") {
                                    viewModel.updateWanServerUrl(server.baseUrl)
                                } else {
                                    viewModel.updateLanServerUrl(server.baseUrl)
                                }
                                advancedUrlPickerTarget = null
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { advancedUrlPickerTarget = null }) {
                    Text("Close")
                }
            }
        )
    }

    advancedUrlDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                advancedUrlDialogTarget = null
                advancedUrlError = null
            },
            title = {
                Text(if (target == "REMOTE") "Remote Server" else "Local Server")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = advancedUrlDraft,
                        onValueChange = {
                            advancedUrlDraft = it.replace(" ", "")
                            advancedUrlError = null
                        },
                        label = { Text(if (target == "REMOTE") "Remote Server" else "Local Server") },
                        singleLine = true
                    )
                    advancedUrlError?.let { message ->
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
                        val trimmedUrl = advancedUrlDraft.trim()
                        val validUrl = trimmedUrl.isBlank() ||
                            trimmedUrl.startsWith("https://", ignoreCase = true) ||
                            trimmedUrl.startsWith("http://", ignoreCase = true)
                        if (!validUrl) {
                            advancedUrlError = "Server URL must start with http:// or https://"
                            return@TextButton
                        }
                        if (target == "REMOTE") {
                            viewModel.updateWanServerUrl(trimmedUrl)
                        } else {
                            viewModel.updateLanServerUrl(trimmedUrl)
                        }
                        advancedUrlDialogTarget = null
                        advancedUrlError = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        advancedUrlDialogTarget = null
                        advancedUrlError = null
                    }
                ) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NavidromeLyricsSourcesRoute(
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    viewModel: NavidromeSettingsViewModel = hiltViewModel(),
    appearanceViewModel: AppAppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearanceUiState by appearanceViewModel.uiState.collectAsStateWithLifecycle()
    var creatingSource by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<SettingsServerOption?>(null) }
    var deletingSource by remember { mutableStateOf<SettingsServerOption?>(null) }
    var confirmClearAllLyricsCache by remember { mutableStateOf(false) }
    var sourceName by rememberSaveable { mutableStateOf("") }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var dialogError by rememberSaveable { mutableStateOf<String?>(null) }
    val sectionCardColor = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val sectionCardBorder = if (appearanceUiState.navidromeMaterialDesignEnabled) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    } else {
        null
    }

    StandardTopScreen(
        title = "Lyrics Sources",
        onBack = onBack,
        onHome = onHome,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        item {
            GroupedSettingsCard(
                containerColor = sectionCardColor,
                border = sectionCardBorder
            ) {
                if (uiState.lyricsSources.isEmpty()) {
                    Text(
                        text = "No lyrics sources added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                    )
                } else {
                    uiState.lyricsSources.forEachIndexed { index, source ->
                        var rowMenuExpanded by remember(source.id) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editingSource = source
                                    sourceName = source.name
                                    sourceUrl = source.baseUrl
                                    dialogError = null
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Subtitles,
                                    contentDescription = null
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = source.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (source.id == uiState.activeLyricsSourceId) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Box(contentAlignment = Alignment.TopEnd) {
                                IconButton(onClick = { rowMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.MoreHoriz,
                                        contentDescription = "Lyrics source actions"
                                    )
                                }
                                AppDropdownMenu(
                                    expanded = rowMenuExpanded,
                                    onDismissRequest = { rowMenuExpanded = false }
                                ) {
                                    if (source.id != uiState.activeLyricsSourceId) {
                                        AppDropdownMenuItem(
                                            text = { Text("Set active") },
                                            onClick = {
                                                rowMenuExpanded = false
                                                viewModel.setActiveLyricsSource(source.id)
                                            }
                                        )
                                    }
                                    AppDropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = {
                                            rowMenuExpanded = false
                                            editingSource = source
                                            sourceName = source.name
                                            sourceUrl = source.baseUrl
                                            dialogError = null
                                        }
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            rowMenuExpanded = false
                                            deletingSource = source
                                        }
                                    )
                                }
                            }
                        }
                        if (index < uiState.lyricsSources.lastIndex) {
                            DividerLine()
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    creatingSource = true
                    sourceName = ""
                    sourceUrl = ""
                    dialogError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppScreenHorizontalPadding)
            ) {
                Text("Add Source")
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppScreenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { confirmClearAllLyricsCache = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All Lyrics Cache")
                }
                Text(
                    text = "Lyrics cache: ${formatStorageSize(uiState.lyricsCacheSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cached lyrics stay on this device so songs can reopen faster and still show offline. Clear them anytime if you want to free up space.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = AppScreenHorizontalPadding)
                )
            }
        }
    }

    if (creatingSource || editingSource != null) {
        AlertDialog(
            onDismissRequest = {
                creatingSource = false
                editingSource = null
                dialogError = null
            },
            title = {
                Text(if (editingSource != null) "Edit Lyrics Source" else "Add Lyrics Source")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = {
                            sourceName = it
                            dialogError = null
                        },
                        label = { Text("Source Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = {
                            sourceUrl = it.replace(" ", "")
                            dialogError = null
                        },
                        label = { Text("Base URL") },
                        singleLine = true
                    )
                    dialogError?.let { message ->
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
                        val trimmedName = sourceName.trim()
                        val trimmedUrl = sourceUrl.trim()
                        if (trimmedName.length < 2) {
                            dialogError = "Source name must be at least 2 characters."
                            return@TextButton
                        }
                        val validUrl = trimmedUrl.startsWith("https://", ignoreCase = true) ||
                            trimmedUrl.startsWith("http://", ignoreCase = true)
                        if (!validUrl) {
                            dialogError = "Base URL must start with http:// or https://"
                            return@TextButton
                        }
                        val source = editingSource
                        if (source != null) {
                            viewModel.updateLyricsSource(source.id, trimmedName, trimmedUrl)
                        } else {
                            viewModel.addLyricsSource(trimmedName, trimmedUrl)
                        }
                        creatingSource = false
                        editingSource = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creatingSource = false
                        editingSource = null
                        dialogError = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (confirmClearAllLyricsCache) {
        AlertDialog(
            onDismissRequest = { confirmClearAllLyricsCache = false },
            title = { Text("Clear all lyrics cache?") },
            text = {
                Text(
                    "This will remove all downloaded lyrics stored on this device. Lyrics can be downloaded again later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAllLyricsCache = false
                        viewModel.clearAllLyricsCache()
                    }
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClearAllLyricsCache = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deletingSource = null },
            title = { Text("Delete Source?") },
            text = { Text("Remove ${source.name} from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLyricsSource(source.id)
                        deletingSource = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingSource = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StandardTopScreen(
    title: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    stickySearchEnabled: Boolean = false,
    stickySearchContent: (@Composable () -> Unit)? = null,
    topContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val listState = rememberLazyListState()
    val collapseDistancePx = with(LocalDensity.current) {
        NavidromeLargeTitleCollapseDistance.roundToPx()
    }
    val collapseFraction by remember(listState, collapseDistancePx) {
        derivedStateOf {
            calculateHeaderCollapseFraction(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                collapseDistancePx = collapseDistancePx
            )
        }
    }
    val stickySearchVisible = rememberNavidromeStickyHeaderVisibility(
        enabled = stickySearchEnabled && stickySearchContent != null,
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
    )
    NavidromeHeaderScaffold(
        title = title,
        collapseFraction = collapseFraction,
        onBack = onBack,
        onHome = onHome,
        actions = actions,
        stickyHeaderVisible = stickySearchVisible,
        stickyHeaderContent = stickySearchContent,
        containerColor = containerColor
    ) { topInsetPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                NavidromeScrollingTitle(
                    title = title,
                    collapseFraction = collapseFraction,
                    topPadding = topInsetPadding
                )
            }
            if (topContent != null) {
                item { topContent() }
            }
            content()
        }
    }
}

private val NavidromePinnedHeaderHeight = 52.dp
private val NavidromeLargeTitleCollapseDistance = 64.dp
private val NavidromeTopContentInset = 60.dp
private val NavidromeStickySearchInset = 76.dp

@Composable
private fun NavidromeHeaderScaffold(
    title: String,
    collapseFraction: Float,
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    stickyHeaderVisible: Boolean = false,
    stickyHeaderContent: (@Composable () -> Unit)? = null,
    containerColor: Color,
    content: @Composable BoxScope.(Dp) -> Unit
) {
    val stickyHeaderInset by animateDpAsState(
        targetValue = if (stickyHeaderVisible && stickyHeaderContent != null) NavidromeStickySearchInset else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "navidromeStickyHeaderInset"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor)
    ) {
        content(NavidromeTopContentInset + stickyHeaderInset)
        if (stickyHeaderContent != null) {
            NavidromeStickyHeaderContent(
                visible = stickyHeaderVisible,
                containerColor = containerColor,
                content = stickyHeaderContent
            )
        }
        NavidromePinnedHeader(
            title = title,
            collapseFraction = collapseFraction,
            onBack = onBack,
            onHome = onHome,
            actions = actions,
            containerColor = containerColor
        )
    }
}

@Composable
private fun NavidromeStickyHeaderContent(
    visible: Boolean,
    containerColor: Color,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = NavidromePinnedHeaderHeight + 8.dp, start = 20.dp, end = 20.dp),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            initialOffsetY = { -it / 2 }
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            targetOffsetY = { -it / 2 }
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NavidromePinnedHeader(
    title: String,
    collapseFraction: Float,
    onBack: () -> Unit,
    onHome: (() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    containerColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    modifier = Modifier.size(42.dp),
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                if (onHome != null) {
                    CircleActionButton(
                        icon = Icons.Outlined.Home,
                        contentDescription = "Home",
                        onClick = onHome
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 92.dp)
                    .graphicsLayer {
                        alpha = collapseFraction
                        translationY = (1f - collapseFraction) * 10f
                    }
            )
        }
    }
}

@Composable
private fun NavidromeScrollingTitle(
    title: String,
    collapseFraction: Float,
    topPadding: Dp,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 4.dp)
            .graphicsLayer {
                alpha = 1f - collapseFraction
                translationY = -24f * collapseFraction
            }
    )
}

private fun calculateHeaderCollapseFraction(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseDistancePx: Int
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (collapseDistancePx <= 0) return 1f
    return (firstVisibleItemScrollOffset / collapseDistancePx.toFloat()).coerceIn(0f, 1f)
}

private fun calculateHeaderCollapseFraction(
    state: LazyListState,
    collapseDistancePx: Int
): Float = calculateHeaderCollapseFraction(
    firstVisibleItemIndex = state.firstVisibleItemIndex,
    firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
    collapseDistancePx = collapseDistancePx
)

private fun calculateHeaderCollapseFraction(
    state: LazyGridState,
    collapseDistancePx: Int
): Float = calculateHeaderCollapseFraction(
    firstVisibleItemIndex = state.firstVisibleItemIndex,
    firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
    collapseDistancePx = collapseDistancePx
)

@Composable
private fun rememberNavidromeStickyHeaderVisibility(
    enabled: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
): Boolean {
    var visible by remember(enabled) { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        visible = enabled
    }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var previousPosition = Long.MIN_VALUE
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val currentPosition = index.toLong() * 100_000L + offset.toLong()
                if (previousPosition == Long.MIN_VALUE) {
                    previousPosition = currentPosition
                    visible = true
                    return@collect
                }
                visible = when {
                    index == 0 && offset <= 8 -> true
                    currentPosition < previousPosition -> true
                    currentPosition > previousPosition && currentPosition > 32L -> false
                    else -> visible
                }
                previousPosition = currentPosition
            }
    }
    return visible
}

@Composable
private fun NavidromeExpandableSearchField(
    visible: Boolean,
    query: String,
    label: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            modifier = modifier
                .fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null)
            }
        )
    }
}

@Composable
private fun TopLevelHeader(
    title: String,
    onProfileClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF0F1))
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Account",
                    tint = Color(0xFFFF334B)
                )
            }
            EditButton(label = "Edit", onClick = onEditClick)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall
        )
    }
}

@Composable
private fun EditButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val density = LocalDensity.current
    val compactHeader = density.fontScale > 1.05f
    val titleStyle = if (compactHeader) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.size(42.dp),
            onClick = onBack
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }
        if (onHome != null) {
            CircleActionButton(
                icon = Icons.Outlined.Home,
                contentDescription = "Home",
                onClick = onHome
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (actions != null) {
            Row(
                modifier = Modifier.padding(end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
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
            contentDescription = contentDescription
        )
    }
}

@Composable
private fun NavidromeContinueListeningCard(
    track: NavidromeTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    cardWidth: Dp = 266.dp,
    cardHeight: Dp = 98.dp,
    posterWidth: Dp = 72.dp,
    posterHeight: Dp = 80.dp,
    onPlayPause: () -> Unit,
    onPlayTrack: () -> Unit,
    onToggleDownload: () -> Unit,
    onClick: () -> Unit,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val fallbackCardColor = Color(0xFF665A2E)
    val dominantCoverColor = rememberDominantNavidromeCoverColor(
        coverUrl = track.coverUrl,
        enabled = true
    )
    val containerColor = remember(dominantCoverColor) {
        val baseColor = dominantCoverColor ?: fallbackCardColor
        val vividBase = brightenAndSaturateNavidromeCardColor(baseColor)
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
                AlbumArt(
                    url = track.coverUrl,
                    width = posterWidth,
                    height = posterHeight,
                    shape = RoundedCornerShape(6.dp),
                    contentScale = ContentScale.Fit,
                    showDownloadedIndicator = isDownloaded
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 28.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        ),
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = track.albumName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        ),
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        if (isCurrent) {
                            onPlayPause()
                        } else {
                            onPlayTrack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isCurrent && isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                        tint = primaryTextColor
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
                NavidromeTrackActionsMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onPlayTrack = {
                        if (isCurrent) {
                            onPlayPause()
                        } else {
                            onPlayTrack()
                        }
                        menuExpanded = false
                    },
                    playLabel = if (isCurrent && isPlaying) "Pause" else if (isCurrent) "Resume" else "Play Now",
                    isDownloaded = isDownloaded,
                    onToggleDownload = {
                        onToggleDownload()
                        menuExpanded = false
                    },
                    onShowAlbum = onOpenAlbum?.let { action ->
                        {
                            action()
                            menuExpanded = false
                        }
                    },
                    onShowArtist = onOpenArtist?.let { action ->
                        {
                            action()
                            menuExpanded = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NavidromeHomeDownloadedRow(
    entry: NavidromeHomeDownloadEntry,
    onClick: () -> Unit,
    onPlayEntry: () -> Unit,
    onShuffleAlbum: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?,
    onRequestRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                url = entry.coverUrl,
                size = 52.dp,
                showDownloadedIndicator = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "Downloaded item actions"
                    )
                }
                NavidromeDownloadedHomeActionsMenu(
                    entry = entry,
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onPlayEntry = {
                        menuExpanded = false
                        onPlayEntry()
                    },
                    onShuffleAlbum = onShuffleAlbum?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    },
                    onRequestRemove = {
                        menuExpanded = false
                        onRequestRemove()
                    },
                    onOpenAlbum = onOpenAlbum?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    },
                    onOpenArtist = onOpenArtist?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    }
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun NavidromeDownloadedHomeActionsMenu(
    entry: NavidromeHomeDownloadEntry,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayEntry: () -> Unit,
    onShuffleAlbum: (() -> Unit)?,
    onRequestRemove: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        AppDropdownMenuItem(
            text = {
                Text(
                    when (entry) {
                        is NavidromeHomeDownloadedAlbum -> "Play Album"
                        is NavidromeHomeDownloadedTrack -> "Play Song"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            },
            onClick = onPlayEntry
        )
        if (entry is NavidromeHomeDownloadedAlbum && onShuffleAlbum != null) {
            AppDropdownMenuItem(
                text = { Text("Shuffle Album") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = null
                    )
                },
                onClick = onShuffleAlbum
            )
        }
        HorizontalDivider()
        AppDropdownMenuItem(
            text = { Text("Remove Download") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null
                )
            },
            onClick = onRequestRemove
        )
        when (entry) {
            is NavidromeHomeDownloadedAlbum -> {
                HorizontalDivider()
                AppDropdownMenuItem(
                    text = { Text("Show Album") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Album,
                            contentDescription = null
                        )
                    },
                    enabled = onOpenAlbum != null,
                    onClick = { onOpenAlbum?.invoke() }
                )
                AppDropdownMenuItem(
                    text = { Text("Show Artist") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null
                        )
                    },
                    enabled = onOpenArtist != null,
                    onClick = { onOpenArtist?.invoke() }
                )
            }

            is NavidromeHomeDownloadedTrack -> {
                if (onOpenAlbum != null || onOpenArtist != null) {
                    HorizontalDivider()
                }
                AppDropdownMenuItem(
                    text = { Text("Show Album") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Album,
                            contentDescription = null
                        )
                    },
                    enabled = onOpenAlbum != null,
                    onClick = { onOpenAlbum?.invoke() }
                )
                AppDropdownMenuItem(
                    text = { Text("Show Artist") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null
                        )
                    },
                    enabled = onOpenArtist != null,
                    onClick = { onOpenArtist?.invoke() }
                )
            }
        }
    }
}

@Composable
private fun NavidromeDownloadedGridCard(
    entry: NavidromeHomeDownloadEntry,
    onClick: () -> Unit,
    onRequestRemove: () -> Unit,
    onPlayEntry: () -> Unit,
    onShuffleAlbum: (() -> Unit)?,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            AlbumArt(
                url = entry.coverUrl,
                size = 168.dp,
                showDownloadedIndicator = false
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "Downloaded item actions",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                NavidromeDownloadedHomeActionsMenu(
                    entry = entry,
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    onPlayEntry = {
                        menuExpanded = false
                        onPlayEntry()
                    },
                    onShuffleAlbum = onShuffleAlbum?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    },
                    onRequestRemove = {
                        menuExpanded = false
                        onRequestRemove()
                    },
                    onOpenAlbum = onOpenAlbum?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    },
                    onOpenArtist = onOpenArtist?.let { action ->
                        {
                            menuExpanded = false
                            action()
                        }
                    }
                )
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = entry.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NavidromeHomeAlbumCard(
    album: NavidromeAlbum,
    posterWidth: Dp,
    posterHeight: Dp,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onToggleDownload: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenArtist: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(posterWidth)
            .clickable(onClick = onClick)
    ) {
        AlbumArt(
            url = album.coverUrl,
            size = posterWidth,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.width(posterWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "Album actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NavidromeAlbumActionsMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onPlayAlbum = {
                            onPlayAlbum()
                            menuExpanded = false
                        },
                        onShuffleAlbum = {
                            onShuffleAlbum()
                            menuExpanded = false
                        },
                        isDownloaded = isDownloaded,
                        onToggleDownload = {
                            onToggleDownload()
                            menuExpanded = false
                        },
                        onShowAlbum = {
                            onOpenAlbum()
                            menuExpanded = false
                        },
                        onShowArtist = onOpenArtist?.let { action ->
                            {
                                action()
                                menuExpanded = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavidromeAlbumActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayAlbum: (() -> Unit)? = null,
    onShuffleAlbum: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    onShowAlbum: () -> Unit,
    onShowArtist: (() -> Unit)?
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        onPlayAlbum?.let { action ->
            AppDropdownMenuItem(
                text = { Text("Play Album") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null
                    )
                },
                onClick = action
            )
        }
        onShuffleAlbum?.let { action ->
            AppDropdownMenuItem(
                text = { Text("Shuffle Album") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = null
                    )
                },
                onClick = action
            )
        }
        if (onPlayAlbum != null || onShuffleAlbum != null) {
            HorizontalDivider()
        }
        onToggleDownload?.let { action ->
            AppDropdownMenuItem(
                text = {
                    Text(if (isDownloaded) "Remove Download" else "Download")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null
                    )
                },
                onClick = action
            )
            HorizontalDivider()
        }
        AppDropdownMenuItem(
            text = { Text("Show Album") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Album,
                    contentDescription = null
                )
            },
            onClick = onShowAlbum
        )
        AppDropdownMenuItem(
            text = { Text("Show Artist") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            enabled = onShowArtist != null,
            onClick = { onShowArtist?.invoke() }
        )
    }
}

@Composable
private fun NavidromeTrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onPlayTrack: () -> Unit,
    playLabel: String,
    isDownloaded: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    onShowAlbum: (() -> Unit)?,
    onShowArtist: (() -> Unit)?,
    extraActions: (@Composable ColumnScope.() -> Unit)? = null
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        AppDropdownMenuItem(
            text = { Text(playLabel) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            },
            onClick = onPlayTrack
        )
        if (onToggleDownload != null) {
            AppDropdownMenuItem(
                text = { Text(if (isDownloaded) "Remove Download" else "Download") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null
                    )
                },
                onClick = onToggleDownload
            )
        }
        AppDropdownMenuItem(
            text = { Text("Show Album") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Album,
                    contentDescription = null
                )
            },
            enabled = onShowAlbum != null,
            onClick = { onShowAlbum?.invoke() }
        )
        AppDropdownMenuItem(
            text = { Text("Show Artist") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null
                )
            },
            enabled = onShowArtist != null,
            onClick = { onShowArtist?.invoke() }
        )
        extraActions?.invoke(this)
    }
}

@Composable
private fun NavidromePlaylistPickerHost(
    pendingRequest: NavidromePlaylistSelectionRequest?,
    onDismiss: () -> Unit,
    viewModel: NavidromePlaylistPickerViewModel
) {
    val request = pendingRequest ?: return
    if (request.trackIds.isEmpty()) return
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(request) {
        viewModel.loadPlaylists(forceRefresh = false, showLoader = true)
    }
    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessages()
        onDismiss()
    }

    NavidromeAddToPlaylistSheet(
        selectionLabel = request.label,
        trackCount = request.trackIds.size,
        uiState = uiState,
        onDismiss = {
            viewModel.clearMessages()
            onDismiss()
        },
        onAddToExistingPlaylist = { playlistId ->
            viewModel.addTracksToPlaylist(request.trackIds, playlistId)
        },
        onCreatePlaylist = { name ->
            viewModel.createPlaylistAndAddTracks(request.trackIds, name)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavidromeAddToPlaylistSheet(
    selectionLabel: String,
    trackCount: Int,
    uiState: NavidromePlaylistPickerUiState,
    onDismiss: () -> Unit,
    onAddToExistingPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showPlaylistInput by rememberSaveable { mutableStateOf(uiState.playlists.isEmpty()) }
    var newPlaylistName by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismissSheet: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }

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
                text = "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = selectionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.playlists.isNotEmpty()) {
                GroupedSettingsCard {
                    uiState.playlists.forEachIndexed { index, playlist ->
                        NavidromeDialogActionRow(
                            title = playlist.name,
                            subtitle = formatPlaylistSummary(playlist),
                            leadingContent = {
                                NavidromePlaylistArtwork(
                                    artworkUrls = playlist.artworkUrls,
                                    songCount = playlist.songCount,
                                    size = 48.dp,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        ) {
                            if (!uiState.isSubmitting) {
                                onAddToExistingPlaylist(playlist.id)
                            }
                        }
                        if (index != uiState.playlists.lastIndex) {
                            DividerLine()
                        }
                    }
                }
            } else if (!showPlaylistInput && !uiState.isLoading) {
                EmptyCard(
                    if (trackCount == 1) {
                        "No playlists yet. Create one to add this song."
                    } else {
                        "No playlists yet. Create one to add these songs."
                    }
                )
            }
            if (uiState.isLoading) {
                LoadingCard()
            }
            OutlinedButton(
                onClick = { showPlaylistInput = !showPlaylistInput },
                enabled = !uiState.isSubmitting
            ) {
                Text(if (showPlaylistInput) "Hide New Playlist" else "New Playlist")
            }
            if (showPlaylistInput) {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Playlist name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name)
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.trim().isNotBlank() && !uiState.isSubmitting
                ) {
                    Text("Create and Add")
                }
            }
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
private fun NavidromeHomeArtistCard(
    artist: NavidromeArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArtistArt(url = artist.imageUrl ?: artist.coverUrl, size = 84.dp)
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NavidromeHomePlaylistCard(
    playlist: NavidromePlaylist,
    posterWidth: Dp,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(posterWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NavidromePlaylistArtwork(
            artworkUrls = playlist.artworkUrls,
            songCount = playlist.songCount,
            size = posterWidth,
            shape = RoundedCornerShape(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.width(posterWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPlaylistSummary(playlist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "Playlist actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    NavidromePlaylistManagementMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onRename = {
                            onRename()
                            menuExpanded = false
                        },
                        onDelete = {
                            onDelete()
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavidromePlaylistManagementMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        AppDropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename
        )
        AppDropdownMenuItem(
            text = { Text("Delete") },
            onClick = onDelete
        )
    }
}

@Composable
private fun NavidromeHomePlaylistShelfCard(
    playlist: NavidromePlaylist,
    cardWidth: Dp,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NavidromePlaylistArtwork(
                    artworkUrls = playlist.artworkUrls,
                    songCount = playlist.songCount,
                    size = cardWidth - 24.dp,
                    shape = RoundedCornerShape(20.dp)
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatPlaylistSummary(playlist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<NavidromeAlbum>,
    onOpenAlbum: (String) -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            actionIcon = actionIcon,
            onActionClick = onActionClick
        )
        if (albums.isEmpty()) {
            EmptyCard("No albums found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(albums) { album ->
                    AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistShelf(
    title: String,
    artists: List<NavidromeArtist>,
    onOpenArtist: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        if (artists.isEmpty()) {
            EmptyCard("No artists found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(artists) { artist ->
                    Column(
                        modifier = Modifier
                            .width(92.dp)
                            .clickable(onClick = { onOpenArtist(artist.id) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArtistArt(
                            url = artist.imageUrl ?: artist.coverUrl,
                            size = 84.dp
                        )
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    title: String,
    playlists: List<NavidromePlaylist>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        if (playlists.isEmpty()) {
            EmptyCard("No playlists found.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists) { playlist ->
                    NavidromeHomePlaylistShelfCard(
                        playlist = playlist,
                        cardWidth = 168.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (actionIcon != null && onActionClick != null) {
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = title
                )
            }
        }
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
private fun AlbumGridCard(
    album: NavidromeAlbum,
    isCurrent: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArt(
            url = album.coverUrl,
            size = 168.dp,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isCurrent) "Playing" else album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) Color(0xFFFF5A5F) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumCard(
    album: NavidromeAlbum,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(122.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlbumArt(
            url = album.coverUrl,
            size = 122.dp,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryMenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFF334B)
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlbumRow(
    album: NavidromeAlbum,
    isCurrent: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                url = album.coverUrl,
                size = 58.dp,
                showDownloadedIndicator = isDownloaded,
                downloadProgressPercent = downloadProgressPercent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = listOfNotNull(album.artistName, album.year?.toString()).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) {
                Text(
                    text = "Playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5F)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun ArtistRow(
    artist: NavidromeArtist,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistArt(
                url = artist.imageUrl ?: artist.coverUrl,
                size = 54.dp,
                showDownloadedIndicator = isDownloaded,
                downloadProgressPercent = downloadProgressPercent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${artist.albumCount} albums",
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
        DividerLine()
    }
}

@Composable
private fun ArtistGridCard(
    artist: NavidromeArtist,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ArtistArt(
            url = artist.imageUrl ?: artist.coverUrl,
            size = 132.dp,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: NavidromePlaylist,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavidromePlaylistArtwork(
                artworkUrls = playlist.artworkUrls,
                songCount = playlist.songCount,
                size = 52.dp,
                shape = RoundedCornerShape(14.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatPlaylistSummary(playlist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun RadioRow(
    radio: NavidromeRadio,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = remember(radio.homePageUrl, radio.streamUrl) {
        formatRadioSubtitle(radio)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = radio.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) {
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5A5F)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null
                )
            }
        }
        DividerLine()
    }
}

@Composable
private fun TrackRow(
    track: NavidromeTrack,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                url = track.coverUrl,
                size = 44.dp,
                showDownloadedIndicator = isDownloaded,
                downloadProgressPercent = downloadProgressPercent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = "${track.artistName} • ${track.albumName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                trailingContent != null && isCurrent -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPlaying) "Playing" else "Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF5A5F)
                        )
                        trailingContent()
                    }
                }
                trailingContent != null -> {
                    trailingContent()
                }
                isCurrent -> {
                    Text(
                        text = if (isPlaying) "Playing" else "Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF5A5F)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null
                    )
                }
            }
        }
        DividerLine()
    }
}

private fun formatRadioSubtitle(radio: NavidromeRadio): String {
    val source = radio.homePageUrl?.ifBlank { null } ?: radio.streamUrl
    val host = source
        .substringAfter("://", source)
        .substringBefore('/')
        .substringBefore('?')
        .removePrefix("www.")
    return host.ifBlank { "Internet radio" }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
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

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    titleMaxLines: Int = 1,
    trailingContentWidth: Dp = 136.dp,
    trailingActionIcon: ImageVector? = null,
    trailingActionContentDescription: String? = null,
    onTrailingActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { rowIcon ->
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(rowIcon, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { rowSubtitle ->
                Text(
                    text = rowSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = trailingContentWidth)
        )
        if (trailingActionIcon != null && onTrailingActionClick != null) {
            IconButton(onClick = onTrailingActionClick) {
                Icon(
                    imageVector = trailingActionIcon,
                    contentDescription = trailingActionContentDescription
                )
            }
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
private fun GroupedSettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border
    ) {
        Column(content = content)
    }
}

@Composable
private fun AppThemedSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun NavidromeDialogActionRow(
    title: String,
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { metadata ->
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemeSettingsRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null
        )
    }
}

@Composable
private fun NavidromeSyncStatusRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        subtitle?.takeIf { it.isNotBlank() }?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NavidromeResyncProgressDialog(
    progress: NavidromeLibraryResyncProgress
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Resyncing library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = progress.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = progress.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Step ${(progress.completedSteps + 1).coerceAtMost(progress.totalSteps)} of ${progress.totalSteps}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun NavidromeServerScanDialog(
    progress: NavidromeServerScanProgress,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(progress.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress.isRunning) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = progress.detail,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = progress.detail,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                progress.status?.let { status ->
                    Text(
                        text = status.toDisplayText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (progress.isRunning) "Hide" else "Close")
            }
        }
    )
}

private fun NavidromeServerScanStatus.toDisplayText(): String {
    return buildList {
        if (scanning) {
            add("Scanning")
        } else {
            add("Idle")
        }
        scannedCount?.let { add("$it scanned") }
        folderCount?.let { add("$it folders") }
        lastScanLabel?.takeIf { it.isNotBlank() }?.let { add("Last scan $it") }
    }.joinToString(" • ")
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String
) {
    NavidromeSettingsRow(
        title = title,
        value = value,
        showChevronWhenValue = false,
        showChevronWhenUnselected = false,
        onClick = null
    )
}

@Composable
private fun NavidromeSettingsRow(
    title: String,
    value: String? = null,
    selected: Boolean = false,
    titleMaxLines: Int = 1,
    showChevronWhenValue: Boolean = true,
    showChevronWhenUnselected: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContentWidth: Dp? = null,
    valueTextAlign: TextAlign = TextAlign.Start,
    trailingActionIcon: ImageVector? = null,
    trailingActionContentDescription: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    forceTitleTwoLineHeight: Boolean = false
) {
    val resolvedTrailingContentWidth = trailingContentWidth ?: when {
        !value.isNullOrBlank() && trailingActionIcon != null -> 168.dp
        !value.isNullOrBlank() && onClick != null -> 144.dp
        !value.isNullOrBlank() -> 136.dp
        else -> 24.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 150.dp)
                .padding(end = 12.dp)
                .then(if (forceTitleTwoLineHeight) Modifier.heightIn(min = 44.dp) else Modifier),
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.widthIn(max = resolvedTrailingContentWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
        ) {
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = valueTextAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showChevronWhenValue && onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (trailingActionIcon != null && onTrailingActionClick != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                }
            } else if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            } else if (showChevronWhenUnselected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (trailingActionIcon != null && onTrailingActionClick != null) {
                Icon(
                    modifier = Modifier.clickable(onClick = onTrailingActionClick),
                    imageVector = trailingActionIcon,
                    contentDescription = trailingActionContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatNavidromeLastSyncedTimestamp(timestampMs: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(java.util.Date(timestampMs))
}

@Composable
private fun CenteredDetailHero(
    title: String,
    subtitle: String,
    imageUrl: String?,
    circular: Boolean,
    showDownloadedIndicator: Boolean = false,
    downloadProgressPercent: Int? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (circular) {
            ArtistArt(
                url = imageUrl,
                size = 118.dp,
                showDownloadedIndicator = showDownloadedIndicator,
                downloadProgressPercent = downloadProgressPercent
            )
        } else {
            AlbumArt(
                url = imageUrl,
                size = 160.dp,
                showDownloadedIndicator = showDownloadedIndicator,
                downloadProgressPercent = downloadProgressPercent
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AlbumDetailHero(
    detail: NavidromeAlbumDetail,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    onToggleDownload: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AlbumArt(
            url = detail.album.coverUrl,
            size = 188.dp,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Text(
            text = detail.album.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = detail.album.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = listOfNotNull(
                detail.album.year?.toString(),
                "${detail.album.songCount} tracks",
                detail.album.genre
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PlayArrow,
                label = "Play",
                onClick = onPlayAlbum
            )
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Shuffle,
                label = "Shuffle",
                onClick = onShuffleAlbum
            )
        }
        if (onAddToPlaylist != null) {
            OutlinedButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Album to Playlist")
            }
        }
        if (onToggleDownload != null) {
            OutlinedButton(
                onClick = onToggleDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDownloaded) "Remove Album Download" else "Download Album")
            }
        }
    }
}

@Composable
private fun PlaylistDetailHero(
    detail: NavidromePlaylistDetail,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NavidromePlaylistArtwork(
            artworkUrls = detail.playlist.artworkUrls,
            songCount = detail.playlist.songCount ?: detail.tracks.size,
            size = 188.dp,
            shape = RoundedCornerShape(24.dp)
        )
        Text(
            text = detail.playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = formatPlaylistSummary(
                detail.playlist.copy(songCount = detail.playlist.songCount ?: detail.tracks.size)
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PlayArrow,
                label = "Play",
                onClick = onPlay
            )
            PillActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Shuffle,
                label = "Shuffle",
                onClick = onShuffle
            )
        }
    }
}

@Composable
private fun PillActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun NavidromeTransportHeader(
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PillActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.PlayArrow,
            label = "Play",
            onClick = onPlay
        )
        PillActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Shuffle,
            label = "Shuffle",
            onClick = onShuffle
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NavidromeMiniPlayerBar(
    state: NavidromePlayerState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val isRadio = remember(track.id) { track.id.startsWith("radio:") }
    val shape = RoundedCornerShape(24.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    val transportButtonWidth = 32.dp
    val transportButtonHeight = 48.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .clickable(onClick = onOpenPlayer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(url = track.coverUrl, size = 30.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = androidx.compose.foundation.MarqueeAnimationMode.Immediately,
                        repeatDelayMillis = 2000,
                        initialDelayMillis = 1200,
                        spacing = MarqueeSpacing(36.dp)
                    )
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onPrevious,
                modifier = Modifier
                    .width(transportButtonWidth)
                    .height(transportButtonHeight)
            ) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .width(transportButtonWidth)
                    .height(transportButtonHeight)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isRadio && state.isPlaying -> Icons.Outlined.Stop
                            state.isPlaying -> Icons.Outlined.Pause
                            else -> Icons.Outlined.PlayArrow
                        },
                        contentDescription = when {
                            isRadio && state.isPlaying -> "Stop"
                            state.isPlaying -> "Pause"
                            else -> "Play"
                        },
                        tint = MaterialTheme.colorScheme.surface
                    )
                }
            }
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .width(transportButtonWidth)
                    .height(transportButtonHeight)
            ) {
                Icon(Icons.Outlined.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NavidromeExpandedPlayerSheet(
    state: NavidromePlayerState,
    lyricsUiState: NavidromeLyricsUiState,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onRefreshAudioOutputs: () -> Unit,
    onSelectAudioOutput: (Int?) -> Unit,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadedTrackIds: Set<String> = emptySet(),
    trackProgressById: Map<String, Int> = emptyMap(),
    onToggleFavorite: (NavidromeTrack) -> Boolean,
    onToggleDownload: ((NavidromeTrack) -> Unit)? = null,
    immersiveEnabled: Boolean = false,
    materialDesignEnabled: Boolean = false,
    onAddToPlaylist: ((NavidromeTrack) -> Unit)? = null,
    onShowLyrics: () -> Unit,
    onDismissLyrics: () -> Unit,
    onClearLyricsCache: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null
) {
    val track = state.currentTrack ?: return
    val isRadio = remember(track.id) { track.id.startsWith("radio:") }
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarTopInset = remember(view, density) {
        with(density) {
            (
                ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.statusBars())
                    ?.top
                    ?: 0
                ).toDp()
        }
    }
    val navigationBottomInset = remember(view, density) {
        with(density) {
            (
                ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())
                    ?.bottom
                    ?: 0
                ).toDp()
        }
    }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf(false) }
    var showLyricsMode by rememberSaveable { mutableStateOf(false) }
    var showOutputSheet by remember { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var renderQueue by rememberSaveable { mutableStateOf(false) }
    var bottomSectionHeightPx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()
    var queueScrollAnimationJob by remember { mutableStateOf<Job?>(null) }
    var scrollContainerTopOffsetPx by remember { mutableIntStateOf(0) }
    var queueCardTopOffsetPx by remember { mutableIntStateOf(0) }
    val queueOpenTopInsetPx = remember(statusBarTopInset, density) {
        with(density) {
            (statusBarTopInset + 12.dp).roundToPx()
        }
    }
    val outputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun dismissLyricsMode() {
        showLyricsMode = false
        onDismissLyrics()
    }
    LaunchedEffect(showLyricsMode) {
        onLyricsModeChanged(showLyricsMode)
    }
    val queuePreview = remember(state.queue, state.currentIndex, track) {
        buildNavidromeQueuePreview(
            queue = state.queue,
            currentTrack = track,
            currentIndex = state.currentIndex
        )
    }
    val displayedQueueItems = remember(state.queueDisplayMode, state.queue, queuePreview, track) {
        when (state.queueDisplayMode) {
            NavidromeQueueDisplayMode.SONGS_TAB_PREVIEW -> queuePreview.items
            NavidromeQueueDisplayMode.FULL -> state.queue.ifEmpty { listOf(track) }.mapIndexed { index, queuedTrack ->
                NavidromeQueuePreviewItem(queueIndex = index, track = queuedTrack)
            }
        }
    }
    val selectedOutput = remember(state.outputDevices, state.selectedOutputDeviceId) {
        state.outputDevices.firstOrNull { it.id == state.selectedOutputDeviceId }
    }
    val outputLabel = remember(selectedOutput) {
        selectedOutput?.let { device ->
            if (device.typeLabel.equals("Phone speaker", ignoreCase = true)) {
                "Phone"
            } else {
                device.typeLabel
            }
        } ?: "Output"
    }
    val outputIcon = remember(selectedOutput) {
        playerOutputToolIcon(selectedOutput?.typeLabel)
    }
    val dominantCoverColor = rememberDominantNavidromeCoverColor(
        coverUrl = track.coverUrl,
        enabled = immersiveEnabled
    )
    val playerCoverModel = rememberCoverImageModel(track.coverUrl, preferOriginalSize = true)
    val playerCoverPainter = rememberAsyncImagePainter(model = playerCoverModel)
    var lastSuccessfulPlayerCoverModel by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(playerCoverPainter.state, playerCoverModel) {
        if (playerCoverPainter.state is AsyncImagePainter.State.Success && playerCoverModel != null) {
            lastSuccessfulPlayerCoverModel = playerCoverModel
        }
    }
    val immersiveBackgroundModel = if (immersiveEnabled) {
        rememberCoverImageModel(track.coverUrl, preferOriginalSize = true)
    } else {
        null
    }
    val immersiveBaseColor = remember(dominantCoverColor) {
        dominantCoverColor?.let(::brightenAndSaturateNavidromeCardColor) ?: Color(0xFF26343B)
    }
    val nonMaterialSectionSurfaceColor = MaterialTheme.colorScheme.surface.copy(
        alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.96f else 0.98f
    )
    val nonMaterialSectionBorderColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }
    val useMaterialSectionSurface = materialDesignEnabled && !immersiveEnabled
    val sectionSurfaceColor = if (useMaterialSectionSurface) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        nonMaterialSectionSurfaceColor
    }
    val sectionSurfaceBorderColor = if (useMaterialSectionSurface) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    } else {
        nonMaterialSectionBorderColor
    }
    val playerBackgroundColor = if (immersiveEnabled) Color.Transparent else MaterialTheme.colorScheme.background
    val coverShellColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val menuShellColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val primaryTextColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val progressActiveColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val progressTrackColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    val transportButtonColor = if (immersiveEnabled) {
        lerp(immersiveBaseColor, Color.Black, 0.18f).copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val transportButtonBorderColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    val toolButtonContainerColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.12f)
    } else {
        sectionSurfaceColor
    }
    val toolButtonContentColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
    val toolButtonBorderColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.14f)
    } else {
        sectionSurfaceBorderColor
    }
    val queueCardColor = if (immersiveEnabled) {
        Color.Black.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val queueCardBorderColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val currentColorScheme = MaterialTheme.colorScheme
    val sheetWindow = remember(view) {
        (view.parent as? DialogWindowProvider)?.window
            ?: (view.context as? Activity)?.window
    }
    DisposableEffect(sheetWindow, view, immersiveEnabled, currentColorScheme.surface) {
        val window = sheetWindow
        if (window == null) {
            onDispose { }
        } else {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
            if (immersiveEnabled) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                insetsController.isAppearanceLightStatusBars = false
            } else {
                @Suppress("DEPRECATION")
                window.statusBarColor = currentColorScheme.background.toArgb()
                insetsController.isAppearanceLightStatusBars = currentColorScheme.background.luminance() > 0.5f
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            insetsController.isAppearanceLightNavigationBars = currentColorScheme.surface.luminance() > 0.5f
            onDispose {
                @Suppress("DEPRECATION")
                window.statusBarColor = if (immersiveEnabled) {
                    previousStatusBarColor
                } else {
                    previousStatusBarColor
                }
                insetsController.isAppearanceLightStatusBars = if (immersiveEnabled) {
                    previousLightStatusBars
                } else {
                    previousLightStatusBars
                }
                @Suppress("DEPRECATION")
                window.navigationBarColor = previousNavigationBarColor
                insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
    val resolvedDurationMs = remember(state.durationMs, track.durationSeconds) {
        state.durationMs.takeIf { it > 0 } ?: ((track.durationSeconds ?: 0) * 1000)
    }
    var sliderPosition by remember(state.positionMs, resolvedDurationMs) {
        mutableStateOf(
            if (resolvedDurationMs > 0) {
                state.positionMs.coerceIn(0, resolvedDurationMs).toFloat()
            } else {
                0f
            }
        )
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(playerBackgroundColor)
    ) {
        if (immersiveEnabled) {
            AsyncImage(
                model = immersiveBackgroundModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.98f)
                    .blur(32.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to immersiveBaseColor.copy(alpha = 0.34f),
                                0.28f to immersiveBaseColor.copy(alpha = 0.26f),
                                0.72f to immersiveBaseColor.copy(alpha = 0.18f),
                                1.00f to immersiveBaseColor.copy(alpha = 0.28f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.68f),
                                0.26f to Color.Black.copy(alpha = 0.56f),
                                0.68f to Color.Black.copy(alpha = 0.62f),
                                1.00f to Color.Black.copy(alpha = 0.76f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
            )
        }
        val usableSheetHeight = (maxHeight - statusBarTopInset - navigationBottomInset).coerceAtLeast(0.dp)
        val compactLayout = usableSheetHeight < 760.dp
        val veryCompactLayout = usableSheetHeight < 720.dp
        val queueExpandedLayout = showQueue
        val targetCoverSize = when {
            veryCompactLayout -> 200.dp
            usableSheetHeight < 680.dp -> 220.dp
            usableSheetHeight < 760.dp -> 242.dp
            else -> 280.dp
        }.coerceAtMost(maxWidth - 48.dp)
        val targetOuterSpacing = when {
            queueExpandedLayout -> 8.dp
            veryCompactLayout -> 8.dp
            compactLayout -> 10.dp
            else -> 14.dp
        }
        val targetTitleSpacing = when {
            queueExpandedLayout || veryCompactLayout -> 2.dp
            compactLayout -> 3.dp
            else -> 5.dp
        }
        val handleTopPadding = (statusBarTopInset + 16.dp).coerceIn(32.dp, 52.dp)
        val targetTopPadding = when {
            veryCompactLayout -> 6.dp
            compactLayout -> 8.dp
            else -> 12.dp
        }
        val targetTransportButtonSize = when {
            queueExpandedLayout || veryCompactLayout -> 80.dp
            else -> 88.dp
        }
        val targetTransportIconSize = when {
            queueExpandedLayout || veryCompactLayout -> 38.dp
            else -> 42.dp
        }
        val targetSkipButtonSize = when {
            queueExpandedLayout || veryCompactLayout -> 56.dp
            else -> 64.dp
        }
        val targetSkipIconSize = when {
            queueExpandedLayout || veryCompactLayout -> 32.dp
            else -> 36.dp
        }
        val lowerSectionLayoutT = ((usableSheetHeight - 620.dp) / 180.dp).coerceIn(0f, 1f)
        val lowerSectionBudget = (usableSheetHeight - targetCoverSize - 300.dp).coerceAtLeast(0.dp)
        val targetTransportSectionTopGap = if (queueExpandedLayout) {
            12.dp
        } else {
            minOf(
                lerp(start = 22.dp, stop = 92.dp, fraction = lowerSectionLayoutT),
                lowerSectionBudget * 0.72f
            )
        }
        val targetToolRowTopGap = if (queueExpandedLayout) {
            10.dp
        } else {
            minOf(
                lerp(start = 12.dp, stop = 28.dp, fraction = lowerSectionLayoutT),
                lowerSectionBudget * 0.24f
            )
        }
        val queueTransitionSpec = remember { tween<Dp>(durationMillis = 260) }
        val coverSize by animateDpAsState(targetValue = targetCoverSize, animationSpec = queueTransitionSpec, label = "navidromePlayerCoverSize")
        val outerSpacing by animateDpAsState(targetValue = targetOuterSpacing, animationSpec = queueTransitionSpec, label = "navidromePlayerOuterSpacing")
        val titleSpacing by animateDpAsState(targetValue = targetTitleSpacing, animationSpec = queueTransitionSpec, label = "navidromePlayerTitleSpacing")
        val topPadding by animateDpAsState(targetValue = targetTopPadding, animationSpec = queueTransitionSpec, label = "navidromePlayerTopPadding")
        val transportButtonSize by animateDpAsState(targetValue = targetTransportButtonSize, animationSpec = queueTransitionSpec, label = "navidromePlayerTransportButtonSize")
        val transportIconSize by animateDpAsState(targetValue = targetTransportIconSize, animationSpec = queueTransitionSpec, label = "navidromePlayerTransportIconSize")
        val skipButtonSize by animateDpAsState(targetValue = targetSkipButtonSize, animationSpec = queueTransitionSpec, label = "navidromePlayerSkipButtonSize")
        val skipIconSize by animateDpAsState(targetValue = targetSkipIconSize, animationSpec = queueTransitionSpec, label = "navidromePlayerSkipIconSize")
        val transportSectionTopGap by animateDpAsState(targetValue = targetTransportSectionTopGap, animationSpec = queueTransitionSpec, label = "navidromePlayerTransportSectionTopGap")
        val toolRowTopGap by animateDpAsState(targetValue = targetToolRowTopGap, animationSpec = queueTransitionSpec, label = "navidromePlayerToolRowTopGap")
        val progressSection: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavidromePlayerProgressBar(
                    progress = if (resolvedDurationMs > 0) {
                        (sliderPosition / resolvedDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    activeColor = progressActiveColor,
                    trackColor = progressTrackColor,
                    onProgressChange = { newProgress ->
                        sliderPosition = resolvedDurationMs * newProgress.coerceIn(0f, 1f)
                    },
                    onProgressChangeFinished = { finalProgress ->
                        val finalPosition = (resolvedDurationMs * finalProgress.coerceIn(0f, 1f)).roundToInt()
                        sliderPosition = finalPosition.toFloat()
                        onSeekTo(finalPosition)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMillis(sliderPosition.roundToInt()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                    Text(
                        text = formatTrackTechnicalDetails(track),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatDurationMillis(resolvedDurationMs),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        val transportRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavidromeTransportIconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(skipButtonSize),
                    icon = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    iconSize = skipIconSize,
                    tint = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    modifier = Modifier.size(transportButtonSize),
                    shape = CircleShape,
                    color = transportButtonColor,
                    border = BorderStroke(1.dp, transportButtonBorderColor),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isRadio && state.isPlaying -> Icons.Outlined.Stop
                                state.isPlaying -> Icons.Outlined.Pause
                                else -> Icons.Outlined.PlayArrow
                            },
                            contentDescription = when {
                                isRadio && state.isPlaying -> "Stop"
                                state.isPlaying -> "Pause"
                                else -> "Play"
                            },
                            tint = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(transportIconSize)
                        )
                    }
                }
                NavidromeTransportIconButton(
                    onClick = onNext,
                    modifier = Modifier.size(skipButtonSize),
                    icon = Icons.Outlined.SkipNext,
                    contentDescription = "Next",
                    iconSize = skipIconSize,
                    tint = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        val toolRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NavidromePlayerToolButton(
                    modifier = Modifier.weight(1f),
                    icon = outputIcon,
                    label = outputLabel,
                    containerColor = toolButtonContainerColor,
                    contentColor = toolButtonContentColor,
                    borderColor = toolButtonBorderColor,
                    onClick = {
                        onRefreshAudioOutputs()
                        showOutputSheet = true
                    }
                )
                NavidromePlayerToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.QueueMusic,
                    label = if (showQueue) "Hide Queue" else "Queue",
                    containerColor = toolButtonContainerColor,
                    contentColor = toolButtonContentColor,
                    borderColor = toolButtonBorderColor,
                    onClick = {
                        queueScrollAnimationJob?.cancel()
                        if (showQueue) {
                            showQueue = false
                            queueScrollAnimationJob = scope.launch {
                                contentScrollState.animateScrollTo(
                                    value = 0,
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                )
                                delay(220)
                                renderQueue = false
                                queueScrollAnimationJob = null
                            }
                        } else {
                            renderQueue = true
                            showQueue = true
                            queueScrollAnimationJob = scope.launch {
                                repeat(12) {
                                    if (queueCardTopOffsetPx > 0 && scrollContainerTopOffsetPx > 0) return@repeat
                                    delay(16)
                                }
                                contentScrollState.animateScrollTo(
                                    value = (queueCardTopOffsetPx - scrollContainerTopOffsetPx - queueOpenTopInsetPx)
                                        .coerceIn(0, contentScrollState.maxValue),
                                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                                )
                                queueScrollAnimationJob = null
                            }
                        }
                    }
                )
            }
        }
        val playerOptionsMenu: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.clickable(onClick = { isMenuExpanded = true }),
                    shape = CircleShape,
                    color = menuShellColor,
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "Player options",
                            modifier = Modifier.size(20.dp),
                            tint = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                AppDropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    if (!isRadio) {
                        AppDropdownMenuItem(
                            text = { Text(if (isFavorite) "Remove Favorite" else "Favorite") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Favorite, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                val added = onToggleFavorite(track)
                                Toast.makeText(
                                    context,
                                    if (added) "Added to Favorite Songs" else "Removed from Favorite Songs",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        onAddToPlaylist?.let { addToPlaylist ->
                            AppDropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.QueueMusic,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    addToPlaylist(track)
                                }
                            )
                        }
                        onToggleDownload?.let { toggleDownload ->
                            AppDropdownMenuItem(
                                text = {
                                    Text(
                                        if (isDownloaded) {
                                            "Remove Download"
                                        } else {
                                            "Download"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    toggleDownload(track)
                                }
                            )
                        }
                        AppDropdownMenuItem(
                            text = { Text("Show Lyrics") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Subtitles, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                showQueue = false
                                renderQueue = false
                                showLyricsMode = true
                                onShowLyrics()
                            }
                        )
                        AppDropdownMenuItem(
                            text = { Text("Clear Lyrics Cache") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                onClearLyricsCache()
                                Toast.makeText(context, "Lyrics cache cleared.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    onOpenAlbum?.takeIf { track.albumId != null }?.let { openAlbum ->
                        AppDropdownMenuItem(
                            text = { Text("Show Album") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Album, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                openAlbum(track.albumId!!)
                            }
                        )
                    }
                    onOpenArtist?.takeIf { track.artistId != null }?.let { openArtist ->
                        AppDropdownMenuItem(
                            text = { Text("Show Artist") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                            },
                            onClick = {
                                isMenuExpanded = false
                                openArtist(track.artistId!!)
                            }
                        )
                    }
                    AppDropdownMenuItem(
                        text = { Text("Track Details") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Tune, contentDescription = null)
                        },
                        onClick = {
                            isMenuExpanded = false
                            showTrackDetails = true
                        }
                    )
                }
            }
        }
        val metadataSectionHeight = when {
            veryCompactLayout -> 112.dp
            queueExpandedLayout || compactLayout -> 124.dp
            else -> 136.dp
        }
        val coverArtwork: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = coverShellColor,
                border = BorderStroke(1.dp, if (immersiveEnabled) Color.Transparent else sectionSurfaceBorderColor),
                tonalElevation = 3.dp
            ) {
                Box(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .width(coverSize)
                            .height(coverSize)
                    ) {
                        val coverShape = RoundedCornerShape(18.dp)
                        val fallbackIcon = if (isRadio) Icons.Outlined.GraphicEq else Icons.Outlined.Album
                        if (playerCoverModel == null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(coverShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = fallbackIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(92.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val shouldShowPreviousCover = playerCoverPainter.state !is AsyncImagePainter.State.Success &&
                                lastSuccessfulPlayerCoverModel != null &&
                                lastSuccessfulPlayerCoverModel != playerCoverModel
                            if (shouldShowPreviousCover) {
                                AsyncImage(
                                    model = lastSuccessfulPlayerCoverModel,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(coverShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            AsyncImage(
                                model = playerCoverModel,
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(coverShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        NavidromeDownloadBadge(
                            visible = isDownloaded || (downloadProgressPercent != null && downloadProgressPercent in 0..99),
                            isCompleted = isDownloaded && (downloadProgressPercent == null || downloadProgressPercent !in 0..99),
                            progressPercent = downloadProgressPercent,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }
        }
        val topContent: @Composable () -> Unit = {
            Spacer(modifier = Modifier.height(handleTopPadding))
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(
                        if (immersiveEnabled) {
                            Color.White.copy(alpha = 0.68f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
            )
            Spacer(modifier = Modifier.height(14.dp))
            coverArtwork()
        }
        val playerControlPanel: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = toolButtonContainerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = toolButtonBorderColor
                ),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metadataSectionHeight)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                maxLines = 2,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(end = 50.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(end = 50.dp),
                                verticalArrangement = Arrangement.spacedBy(titleSpacing)
                            ) {
                                Text(
                                    text = track.artistName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.albumName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box(modifier = Modifier.align(Alignment.TopEnd)) {
                            playerOptionsMenu()
                        }
                    }
                    progressSection()
                    transportRow()
                }
            }
        }
        val queueCardContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        queueCardTopOffsetPx = coordinates.positionInRoot().y.roundToInt()
                    }
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    modifier = Modifier.fillMaxWidth(),
                    visible = showQueue,
                    enter = slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = tween(durationMillis = 220)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 4 },
                        animationSpec = tween(durationMillis = 180)
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        color = queueCardColor,
                        border = BorderStroke(1.dp, queueCardBorderColor),
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Up Next",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayedQueueItems, key = { it.queueIndex }) { item ->
                                        PlayerQueueRow(
                                            track = item.track,
                                            isCurrent = item.queueIndex == state.currentIndex ||
                                                (state.currentIndex !in state.queue.indices && item.track.id == track.id),
                                            isDownloaded = item.track.id in downloadedTrackIds,
                                            downloadProgressPercent = trackProgressById[item.track.id],
                                            immersiveEnabled = immersiveEnabled,
                                            onClick = { onSelectTrack(item.queueIndex) }
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
        val hiddenPlayerContent: @Composable () -> Unit = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 6.dp)
            ) {
                val bottomSectionHeight = with(density) { bottomSectionHeightPx.toDp() }
                val artworkZoneHeight = (maxHeight - bottomSectionHeight)
                    .coerceAtLeast(coverSize + handleTopPadding + 40.dp)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(artworkZoneHeight),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(handleTopPadding))
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(100))
                            .background(
                                if (immersiveEnabled) {
                                    Color.White.copy(alpha = 0.68f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        coverArtwork()
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .onSizeChanged { bottomSectionHeightPx = it.height },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    playerControlPanel()
                    toolRow()
                }
            }
        }
        if (renderQueue) {
            if (showQueue) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize(animationSpec = tween(durationMillis = 260))
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 6.dp)
                        .onGloballyPositioned { coordinates ->
                            scrollContainerTopOffsetPx = coordinates.positionInRoot().y.roundToInt()
                        }
                        .verticalScroll(contentScrollState, enabled = true),
                    verticalArrangement = Arrangement.spacedBy(outerSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    topContent()
                    Spacer(modifier = Modifier.height(transportSectionTopGap))
                    playerControlPanel()
                    Spacer(modifier = Modifier.height(toolRowTopGap))
                    toolRow()
                    Spacer(modifier = Modifier.height(4.dp))
                    queueCardContent()
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    hiddenPlayerContent()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp + toolRowTopGap)
                    ) {
                        queueCardContent()
                    }
                }
            }
        } else {
            hiddenPlayerContent()
        }
    }
    if (showLyricsMode) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            NavidromeLyricsSheetContent(
                uiState = lyricsUiState,
                playbackPositionMs = state.positionMs,
                isPlaying = state.isPlaying,
                isRadio = isRadio,
                durationMs = state.durationMs,
                coverUrl = track.coverUrl,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onDismiss = ::dismissLyricsMode,
                immersiveEnabled = immersiveEnabled,
                immersiveBaseColor = immersiveBaseColor
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
            NavidromePlayerOutputSheet(
                outputDevices = state.outputDevices,
                selectedOutputDeviceId = state.selectedOutputDeviceId,
                onSelectOutput = { deviceId ->
                    onSelectAudioOutput(deviceId)
                    showOutputSheet = false
                }
            )
        }
    }
    if (showTrackDetails) {
        AlertDialog(
            onDismissRequest = { showTrackDetails = false },
            confirmButton = {
                TextButton(onClick = { showTrackDetails = false }) {
                    Text("Done")
                }
            },
            title = {
                Text(
                    text = "Track Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlayerMetadataRow(label = "Title", value = track.title)
                    PlayerMetadataRow(label = "Artist", value = track.artistName)
                    PlayerMetadataRow(label = "Album", value = track.albumName)
                    track.trackNumber?.let { trackNumber ->
                        PlayerMetadataRow(label = "Track", value = trackNumber.toString())
                    }
                    track.formatLabel?.takeIf { it.isNotBlank() }?.let { formatLabel ->
                        PlayerMetadataRow(label = "Format", value = formatLabel)
                    }
                    track.bitRateKbps?.takeIf { it > 0 }?.let { bitRate ->
                        PlayerMetadataRow(label = "Bitrate", value = "$bitRate kbps")
                    }
                    PlayerMetadataRow(
                        label = "Length",
                        value = formatDurationMillis(resolvedDurationMs)
                    )
                }
            }
        )
    }
}

@Composable
private fun NavidromeLyricsSheetContent(
    uiState: NavidromeLyricsUiState,
    playbackPositionMs: Int,
    isPlaying: Boolean,
    isRadio: Boolean,
    durationMs: Int,
    coverUrl: String?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    immersiveEnabled: Boolean = false,
    immersiveBaseColor: Color = Color(0xFF26343B)
) {
    val view = LocalView.current
    val overlayBackgroundModel = rememberCoverImageModel(coverUrl, preferOriginalSize = true)
    val headerTitleColor = if (immersiveEnabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
    val headerAlbumColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
    }
    val headerMetaColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    }
    val nonFocusedLyricBaseColor = when {
        immersiveEnabled -> Color.White
        MaterialTheme.colorScheme.background.luminance() < 0.5f -> Color.White
        else -> Color.Black
    }
    val loadingIndicatorColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.primary
    val emptyStateColor = if (immersiveEnabled) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
    val syncButtonColor = if (immersiveEnabled) {
        lerp(immersiveBaseColor, Color.Black, 0.18f).copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val syncButtonContentColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onPrimary
    val syncButtonBorderColor = if (immersiveEnabled) Color.White.copy(alpha = 0.18f) else Color.Transparent
    val closeButtonShellColor = syncButtonColor
    val closeButtonIconColor = syncButtonContentColor
    val closeButtonBorderColor = syncButtonBorderColor
    val lyricsHeaderTransportTint = closeButtonIconColor
    val lyricsHeaderTransportShellColor = closeButtonShellColor
    val lyricsHeaderTransportShellBorderColor = closeButtonBorderColor
    val lyricsHeaderTransportButtonColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.12f)
    } else {
        syncButtonContentColor.copy(alpha = 0.12f)
    }
    val lyricsHeaderTransportProgressTrackColor = if (immersiveEnabled) {
        closeButtonIconColor.copy(alpha = 0.14f)
    } else {
        closeButtonIconColor.copy(alpha = 0.22f)
    }
    val lyricsHeaderTransportProgressColor = if (immersiveEnabled) {
        closeButtonIconColor.copy(alpha = 0.42f)
    } else {
        closeButtonIconColor.copy(alpha = 0.72f)
    }
    val lyricsProgressFraction = remember(playbackPositionMs, durationMs) {
        if (durationMs > 0) {
            (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val lyricsWindow = remember(view) {
        (view.parent as? DialogWindowProvider)?.window
    }
    val lyricsListState = rememberLazyListState()
    val plainLyricsScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var autoSyncLyrics by remember(uiState.trackTitle, uiState.artistName) { mutableStateOf(true) }
    val showSyncLyricsButton = uiState.isSynced && !autoSyncLyrics
    val syncLyricsReservedWidth = 164.dp
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val latestPlaybackPositionMs by rememberUpdatedState(playbackPositionMs)
    val density = LocalDensity.current
    val centerPadding = remember(contentHeightPx, density) {
        with(density) {
            val viewportHeightDp = contentHeightPx.toDp()
            ((viewportHeightDp / 2f) - 28.dp).coerceAtLeast(140.dp)
        }
    }
    val lyricsBottomPadding = centerPadding + 96.dp
    val lyricsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    autoSyncLyrics = false
                }
                return Offset.Zero
            }
        }
    }
    val smoothedPlaybackPositionMs by produceState(
        initialValue = playbackPositionMs,
        key1 = uiState.trackId,
        key2 = isPlaying,
        key3 = durationMs
    ) {
        var anchorPositionMs = latestPlaybackPositionMs
        var anchorFrameNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val latestPositionMs = latestPlaybackPositionMs
            if (latestPositionMs != anchorPositionMs) {
                anchorPositionMs = latestPositionMs
                anchorFrameNanos = nowNanos
            }
            value = if (isPlaying) {
                val elapsedMs = ((nowNanos - anchorFrameNanos) / 1_000_000L).toInt()
                (anchorPositionMs + elapsedMs)
                    .coerceAtLeast(0)
                    .coerceAtMost(durationMs.takeIf { it > 0 } ?: Int.MAX_VALUE)
            } else {
                latestPositionMs
            }
        }
    }
    val syncProgressState = remember(uiState.lyrics, uiState.isSynced, smoothedPlaybackPositionMs) {
        resolveSyncedLyricsProgress(
            lyrics = uiState.lyrics,
            isSynced = uiState.isSynced,
            playbackPositionMs = smoothedPlaybackPositionMs + NAVIDROME_SYNC_FOCUS_LEAD_MS
        )
    }
    val currentLineIndex = syncProgressState?.currentIndex ?: -1
    val centeredVisibleIndex by remember(lyricsListState) {
        derivedStateOf {
            val layoutInfo = lyricsListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                -1
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: -1
            }
        }
    }
    val focusedLineIndex = if (uiState.isSynced) {
        if (autoSyncLyrics) currentLineIndex.coerceAtLeast(0) else centeredVisibleIndex.coerceAtLeast(0)
    } else {
        -1
    }
    val isPreStartFocus = syncProgressState?.isPreStart == true && autoSyncLyrics && focusedLineIndex == 0
    DisposableEffect(lyricsWindow, view, immersiveEnabled) {
        val window = lyricsWindow
        if (window == null) {
            onDispose { }
        } else {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
            val previousNavigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced
            } else {
                null
            }
            val previousStatusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced
            } else {
                null
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            insetsController.isAppearanceLightStatusBars = !immersiveEnabled
            insetsController.isAppearanceLightNavigationBars = !immersiveEnabled
            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                @Suppress("DEPRECATION")
                window.statusBarColor = previousStatusBarColor
                @Suppress("DEPRECATION")
                window.navigationBarColor = previousNavigationBarColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = previousNavigationBarContrastEnforced ?: true
                    window.isStatusBarContrastEnforced = previousStatusBarContrastEnforced ?: true
                }
                insetsController.isAppearanceLightStatusBars = previousLightStatusBars
                insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
    LaunchedEffect(uiState.isSynced, uiState.lyrics.size, autoSyncLyrics, currentLineIndex, contentHeightPx) {
        if (!autoSyncLyrics) return@LaunchedEffect
        if (!uiState.isSynced || uiState.lyrics.isEmpty()) return@LaunchedEffect
        val targetIndex = currentLineIndex.coerceAtLeast(0)
        if (targetIndex !in lyricsListState.layoutInfo.visibleItemsInfo.map { it.index }) {
            lyricsListState.scrollToItem(targetIndex)
            withFrameNanos { }
        }
        val layoutInfo = lyricsListState.layoutInfo
        val currentItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return@LaunchedEffect
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
        val currentCenter = currentItemInfo.offset + (currentItemInfo.size / 2f)
        val delta = currentCenter - viewportCenter
        if (abs(delta) > 0.5f) {
            lyricsListState.animateScrollBy(
                value = delta,
                animationSpec = tween(
                    durationMillis = NAVIDROME_SYNC_SCROLL_ANIMATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    LaunchedEffect(uiState.trackId) {
        autoSyncLyrics = true
        contentHeightPx = 0
        if (lyricsListState.firstVisibleItemIndex != 0 || lyricsListState.firstVisibleItemScrollOffset != 0) {
            lyricsListState.scrollToItem(0)
        }
        if (plainLyricsScrollState.value != 0) {
            plainLyricsScrollState.scrollTo(0)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
    ) {
        AsyncImage(
            model = overlayBackgroundModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (immersiveEnabled) 0.98f else 0.92f)
                .blur(if (immersiveEnabled) 32.dp else 36.dp)
        )
        if (immersiveEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to immersiveBaseColor.copy(alpha = 0.34f),
                                0.28f to immersiveBaseColor.copy(alpha = 0.26f),
                                0.72f to immersiveBaseColor.copy(alpha = 0.18f),
                                1.00f to immersiveBaseColor.copy(alpha = 0.28f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.68f),
                                0.26f to Color.Black.copy(alpha = 0.56f),
                                0.68f to Color.Black.copy(alpha = 0.62f),
                                1.00f to Color.Black.copy(alpha = 0.76f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                                0.38f to MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                                1.00f to MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = 22.dp,
                    end = 22.dp,
                    top = 16.dp,
                    bottom = 18.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = headerMetaColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp)
                    )
                    // ########## NV lyrics header transport controls start ##########
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 0.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = lyricsHeaderTransportShellColor,
                        border = BorderStroke(1.dp, lyricsHeaderTransportShellBorderColor),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = lyricsHeaderTransportButtonColor,
                                tonalElevation = 0.dp
                            ) {
                                NavidromeTransportIconButton(
                                    onClick = onPrevious,
                                    modifier = Modifier.fillMaxSize(),
                                    icon = Icons.Outlined.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = lyricsHeaderTransportTint,
                                    iconSize = 24.dp
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .drawWithCache {
                                        val strokeWidth = 2.dp.toPx()
                                        val inset = strokeWidth / 2f
                                        val arcSize = Size(
                                            width = size.width - strokeWidth,
                                            height = size.height - strokeWidth
                                        )
                                        onDrawWithContent {
                                            drawContent()
                                            drawArc(
                                                color = lyricsHeaderTransportProgressTrackColor,
                                                startAngle = -90f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                topLeft = Offset(inset, inset),
                                                size = arcSize,
                                                style = Stroke(width = strokeWidth)
                                            )
                                            if (lyricsProgressFraction > 0f) {
                                                drawArc(
                                                    color = lyricsHeaderTransportProgressColor,
                                                    startAngle = -90f,
                                                    sweepAngle = 360f * lyricsProgressFraction,
                                                    useCenter = false,
                                                    topLeft = Offset(inset, inset),
                                                    size = arcSize,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                )
                                            }
                                        }
                                    },
                                shape = CircleShape,
                                color = lyricsHeaderTransportButtonColor,
                                tonalElevation = 0.dp
                            ) {
                                IconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = when {
                                            isRadio && isPlaying -> Icons.Outlined.Stop
                                            isPlaying -> Icons.Outlined.Pause
                                            else -> Icons.Outlined.PlayArrow
                                        },
                                        contentDescription = when {
                                            isRadio && isPlaying -> "Stop"
                                            isPlaying -> "Pause"
                                            else -> "Play"
                                        },
                                        tint = lyricsHeaderTransportTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = lyricsHeaderTransportButtonColor,
                                tonalElevation = 0.dp
                            ) {
                                NavidromeTransportIconButton(
                                    onClick = onNext,
                                    modifier = Modifier.fillMaxSize(),
                                    icon = Icons.Outlined.SkipNext,
                                    contentDescription = "Next",
                                    tint = lyricsHeaderTransportTint,
                                    iconSize = 24.dp
                                )
                            }
                        }
                    }
                    // ########## NV lyrics header transport controls end ##########
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = CircleShape,
                        color = closeButtonShellColor,
                        border = BorderStroke(1.dp, closeButtonBorderColor),
                        tonalElevation = 0.dp
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close lyrics",
                                tint = closeButtonIconColor
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (showSyncLyricsButton) syncLyricsReservedWidth else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.trackTitle.isNotBlank()) {
                            Text(
                                text = uiState.trackTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = headerTitleColor
                            )
                            if (uiState.albumName.isNotBlank()) {
                                Text(
                                    text = uiState.albumName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = headerAlbumColor
                                )
                            }
                            if (uiState.artistName.isNotBlank()) {
                                Text(
                                    text = uiState.artistName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = headerMetaColor
                                )
                            }
                        }
                    }
                    if (showSyncLyricsButton) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable {
                                    autoSyncLyrics = true
                                    scope.launch {
                                        lyricsListState.animateScrollToItem(currentLineIndex.coerceAtLeast(0))
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = syncButtonColor,
                            border = BorderStroke(1.dp, syncButtonBorderColor),
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = syncButtonContentColor
                                )
                                Text(
                                    text = "Sync Lyrics",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = syncButtonContentColor
                                )
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp,
                            color = loadingIndicatorColor
                        )
                    }
                }

                uiState.lyrics.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "No lyrics found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = emptyStateColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                uiState.isSynced -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onSizeChanged { contentHeightPx = it.height }
                        ) {
                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(lyricsScrollConnection),
                                contentPadding = PaddingValues(
                                    top = centerPadding,
                                    bottom = lyricsBottomPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(uiState.lyrics) { index, line ->
                                    val lineDistance = if (focusedLineIndex >= 0) {
                                        abs(index - focusedLineIndex)
                                    } else {
                                        Int.MAX_VALUE
                                    }
                                    val isFocusedLine = index == focusedLineIndex
                                    val targetBlurRadius = if (isFocusedLine) {
                                        if (isPreStartFocus) 1.5.dp else 0.dp
                                    } else {
                                        minOf(lineDistance.toFloat() * 1.6f, 8f).dp
                                    }
                                    val targetScale = if (isFocusedLine) 1.08f else 0.92f
                                    val targetColor = if (isFocusedLine) {
                                        if (immersiveEnabled) {
                                            if (isPreStartFocus) Color.White.copy(alpha = 0.88f) else Color.White
                                        } else {
                                            if (isPreStartFocus) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        }
                                    } else {
                                        val alpha = when (lineDistance) {
                                            0 -> 1f
                                            1 -> 0.42f
                                            2 -> 0.22f
                                            else -> 0.08f
                                        }
                                        nonFocusedLyricBaseColor.copy(alpha = alpha)
                                    }
                                    val lyricAnimationSpec = remember(autoSyncLyrics) {
                                        tween<Float>(
                                            durationMillis = if (autoSyncLyrics) 100 else 180,
                                            easing = FastOutSlowInEasing
                                        )
                                    }
                                    val blurRadius by animateDpAsState(
                                        targetValue = targetBlurRadius,
                                        animationSpec = tween(
                                            durationMillis = if (autoSyncLyrics) 80 else 180,
                                            easing = FastOutSlowInEasing
                                        ),
                                        label = "navidromeLyricBlur"
                                    )
                                    val textScale by animateFloatAsState(
                                        targetValue = targetScale,
                                        animationSpec = lyricAnimationSpec,
                                        label = "navidromeLyricScale"
                                    )
                                    val textColor by animateColorAsState(
                                        targetValue = targetColor,
                                        animationSpec = tween(
                                            durationMillis = if (autoSyncLyrics) 100 else 180,
                                            easing = FastOutSlowInEasing
                                        ),
                                        label = "navidromeLyricColor"
                                    )
                                    Text(
                                        text = line.text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 26.dp)
                                            .graphicsLayer {
                                                scaleX = textScale
                                                scaleY = textScale
                                            }
                                            .blur(blurRadius),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = if (isFocusedLine) FontWeight.SemiBold else FontWeight.Normal,
                                        color = textColor,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 34.sp
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(plainLyricsScrollState)
                            .padding(bottom = lyricsBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        uiState.lyrics.forEach { line ->
                            Text(
                                text = line.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (immersiveEnabled) {
                                    Color.White.copy(alpha = 0.92f)
                                } else {
                                    Color.Black.copy(alpha = 0.86f)
                                },
                                lineHeight = 34.sp,
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
private fun NavidromePlayerToolButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NavidromeTransportIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: Dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "navidromeTransportScale"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint
        )
    }
}

private data class SyncedLyricsProgress(
    val currentIndex: Int,
    val isPreStart: Boolean
)

private const val NAVIDROME_SYNC_FOCUS_LEAD_MS = 60
private const val NAVIDROME_SYNC_SCROLL_ANIMATION_MS = 100

private fun resolveSyncedLyricsProgress(
    lyrics: List<NavidromeLyricsLine>,
    isSynced: Boolean,
    playbackPositionMs: Int
): SyncedLyricsProgress? {
    if (!isSynced || lyrics.isEmpty()) return null

    val timedLines = lyrics.mapIndexedNotNull { index, line ->
        line.timestampMs?.let { timestampMs -> index to timestampMs }
    }
    if (timedLines.isEmpty()) return null

    val firstTimedLine = timedLines.first()
    val currentTimedLinePosition = timedLines.indexOfLast { (_, timestampMs) ->
        timestampMs <= playbackPositionMs
    }

    if (currentTimedLinePosition < 0) {
        return SyncedLyricsProgress(
            currentIndex = firstTimedLine.first,
            isPreStart = true
        )
    }

    val currentTimedLine = timedLines[currentTimedLinePosition]

    return SyncedLyricsProgress(
        currentIndex = currentTimedLine.first,
        isPreStart = false
    )
}

@Composable
private fun PlayerMetadataRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavidromePlayerOutputSheet(
    outputDevices: List<NavidromeOutputDevice>,
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
                        imageVector = playerOutputToolIcon(device.typeLabel),
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
private fun PlayerStatPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PlayerQueueRow(
    track: NavidromeTrack,
    isCurrent: Boolean,
    isDownloaded: Boolean = false,
    downloadProgressPercent: Int? = null,
    immersiveEnabled: Boolean = false,
    onClick: () -> Unit
) {
    val currentBackgroundColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    }
    val primaryTextColor = if (immersiveEnabled) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (immersiveEnabled) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) {
                    currentBackgroundColor
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            url = track.coverUrl,
            size = 44.dp,
            showDownloadedIndicator = isDownloaded,
            downloadProgressPercent = downloadProgressPercent
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = primaryTextColor,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (isCurrent) "Playing" else track.durationSeconds?.let(::formatDuration) ?: "--:--",
            style = MaterialTheme.typography.bodySmall,
            color = if (isCurrent) {
                if (immersiveEnabled) Color.White else Color(0xFFFF5A5F)
            } else {
                secondaryTextColor
            }
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatDurationMillis(totalMs: Int): String {
    return formatDuration((totalMs.coerceAtLeast(0)) / 1000)
}

private fun formatPlaylistSummary(playlist: NavidromePlaylist): String {
    val parts = buildList {
        playlist.songCount?.let { count ->
            add(if (count == 1) "1 song" else "$count songs")
        } ?: add("Playlist")
        playlist.durationSeconds?.takeIf { it > 0 }?.let { duration ->
            add(formatDuration(duration))
        }
    }
    return parts.joinToString(" • ")
}

@Composable
private fun NavidromePlaylistArtwork(
    artworkUrls: List<String>,
    songCount: Int?,
    size: Dp,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val totalSongs = (songCount ?: artworkUrls.size).coerceAtLeast(0)
    val slotCount = when (totalSongs) {
        0 -> 0
        1 -> 1
        else -> 4
    }
    val tileGap = if (size >= 160.dp) 4.dp else 2.dp
    val tileShape = if (size >= 160.dp) RoundedCornerShape(14.dp) else RoundedCornerShape(8.dp)
    val tileSize = (size - (tileGap * 3)) / 2

    when (slotCount) {
        0 -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(if (size >= 160.dp) 54.dp else 24.dp)
                )
            }
        }

        1 -> {
            AlbumArt(
                url = artworkUrls.firstOrNull(),
                width = size,
                height = size,
                shape = shape,
                fallbackIcon = Icons.Outlined.MusicNote
            )
        }

        else -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(tileGap)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(tileGap)
                ) {
                    repeat(2) { rowIndex ->
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(tileGap)
                        ) {
                            repeat(2) { columnIndex ->
                                val slotIndex = (rowIndex * 2) + columnIndex
                                AlbumArt(
                                    url = artworkUrls.getOrNull(slotIndex),
                                    width = tileSize,
                                    height = tileSize,
                                    shape = tileShape,
                                    fallbackIcon = Icons.Outlined.MusicNote
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTrackTechnicalDetails(track: NavidromeTrack): String {
    val parts = buildList {
        track.formatLabel?.takeIf { it.isNotBlank() }?.let(::add)
        track.bitRateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
    }
    return parts.joinToString(separator = " • ")
}

private fun isHttpUrl(baseUrl: String): Boolean {
    return baseUrl.trim().startsWith("http://", ignoreCase = true)
}

private fun formatNavidromeServerLabel(baseUrl: String): String {
    val normalized = baseUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("www.")
        .ifBlank { "Navidrome" }
    return normalized
}

private fun formatServerAddressForDisplay(url: String): String {
    val trimmed = url.trim().removeSuffix("/")
    if (trimmed.isBlank()) return ""
    val parsedUri = runCatching { URI(trimmed) }.getOrNull()
    val host = parsedUri?.host.orEmpty()
    val port = parsedUri?.port ?: -1
    if (host.isBlank()) {
        return trimmed.removePrefix("https://").removePrefix("http://")
    }
    return if (port > 0) "$host:$port" else host
}

private fun formatCompactServerAddress(url: String): String {
    return url.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .removeSuffix("/")
}

@Composable
private fun NavidromePlayerProgressBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val touchTargetHeight = 30.dp
    val barHeight = 9.dp
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
private fun rememberDominantNavidromeCoverColor(
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
                averageNavidromeBitmapColor(bitmap)
            }.getOrNull()
        }
    }
    return dominantColorState.value
}

private fun averageNavidromeBitmapColor(bitmap: Bitmap): Color {
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

private fun brightenAndSaturateNavidromeCardColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = (hsv[1] * 1.48f + 0.12f).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * 0.94f + 0.02f).coerceIn(0.22f, 0.86f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun ProfileGlyph() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFF0F1)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = Color(0xFFFF334B)
        )
    }
}

@Composable
private fun AlbumArt(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    showDownloadedIndicator: Boolean = false,
    downloadProgressPercent: Int? = null
) {
    AlbumArt(
        url = url,
        width = size,
        height = size,
        showDownloadedIndicator = showDownloadedIndicator,
        downloadProgressPercent = downloadProgressPercent
    )
}

@Composable
private fun AlbumArt(
    url: String?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: Shape = RoundedCornerShape(18.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Outlined.Album,
    showDownloadedIndicator: Boolean = false,
    downloadProgressPercent: Int? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
    ) {
        if (url.isNullOrBlank()) {
            val fallbackIconSize = if (width >= 160.dp || height >= 160.dp) 92.dp else 28.dp
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    modifier = Modifier.size(fallbackIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
                contentScale = contentScale
            )
        }
        NavidromeDownloadBadge(
            visible = showDownloadedIndicator || (downloadProgressPercent != null && downloadProgressPercent in 0..99),
            isCompleted = showDownloadedIndicator && (downloadProgressPercent == null || downloadProgressPercent !in 0..99),
            progressPercent = downloadProgressPercent,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun ArtistArt(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    showDownloadedIndicator: Boolean = false,
    downloadProgressPercent: Int? = null
) {
    Box(modifier = Modifier.size(size)) {
        if (url.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null)
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        NavidromeDownloadBadge(
            visible = showDownloadedIndicator || (downloadProgressPercent != null && downloadProgressPercent in 0..99),
            isCompleted = showDownloadedIndicator && (downloadProgressPercent == null || downloadProgressPercent !in 0..99),
            progressPercent = downloadProgressPercent,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun NavidromeDownloadBadge(
    visible: Boolean,
    isCompleted: Boolean,
    progressPercent: Int?,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val progress = progressPercent?.coerceIn(0, 99)
    val showProgress = progress != null
    val badgeSize = if (showProgress) 30.dp else 18.dp
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(badgeSize)
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
        } else if (isCompleted) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "Downloaded",
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private const val NAVIDROME_LYRICS_CACHE_SOFT_WARNING_BYTES = 100L * 1024L * 1024L
private const val NAVIDROME_LYRICS_CACHE_STRONG_WARNING_BYTES = 250L * 1024L * 1024L

private fun formatStorageSize(bytes: Long?): String {
    val value = bytes ?: return "Unknown"
    if (value <= 0L) return "0 KB"
    val kb = value / 1024.0
    if (kb < 1024.0) {
        val displayKb = ((value + 1023L) / 1024L).coerceAtLeast(1L)
        return String.format(Locale.getDefault(), "%d KB", displayKb)
    }
    val mb = kb / 1024.0
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", mb)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    )
}
