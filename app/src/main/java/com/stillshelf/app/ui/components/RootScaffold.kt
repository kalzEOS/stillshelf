package com.stillshelf.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.stillshelf.app.ui.navigation.MainTab

@Composable
@Suppress("UNUSED_PARAMETER")
fun RootScaffold(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    miniPlayerState: MiniPlayerUiState,
    onMiniPlayerHomeClick: (() -> Unit)? = null,
    onMiniPlayerRewind15: () -> Unit,
    onMiniPlayerPlayPause: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    showMiniPlayer: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val contentBottomInset = 0.dp
    val view = LocalView.current
    val density = LocalDensity.current
    val systemInsets = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())
    val safeTopInset = with(density) { (systemInsets?.top ?: 0).toDp() }
    val safeBottomInset = with(density) { (systemInsets?.bottom ?: 0).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = safeTopInset)
        ) {
            content(PaddingValues(bottom = contentBottomInset))
        }
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
            visible = showMiniPlayer,
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
                    .padding(horizontal = 12.dp)
                    .padding(bottom = safeBottomInset + 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                MiniPlayerBar(
                    state = miniPlayerState,
                    onRewind15 = onMiniPlayerRewind15,
                    onPlayPause = onMiniPlayerPlayPause,
                    onClick = onMiniPlayerClick,
                    modifier = Modifier.weight(1f)
                )
                if (onMiniPlayerHomeClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .clickable(onClick = onMiniPlayerHomeClick),
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
