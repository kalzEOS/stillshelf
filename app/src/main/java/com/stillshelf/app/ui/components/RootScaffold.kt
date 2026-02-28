package com.stillshelf.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.stillshelf.app.ui.navigation.MainTab

@Composable
@Suppress("UNUSED_PARAMETER")
fun RootScaffold(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    miniPlayerState: MiniPlayerUiState,
    onMiniPlayerRewind15: () -> Unit,
    onMiniPlayerPlayPause: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    showMiniPlayer: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val contentBottomInset = 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            content(PaddingValues(bottom = contentBottomInset))
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
            MiniPlayerBar(
                state = miniPlayerState,
                onRewind15 = onMiniPlayerRewind15,
                onPlayPause = onMiniPlayerPlayPause,
                onClick = onMiniPlayerClick,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}
