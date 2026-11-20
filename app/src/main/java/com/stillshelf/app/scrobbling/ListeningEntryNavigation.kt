package com.stillshelf.app.scrobbling

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class ListeningEntryNavigation(val busy: Boolean, val open: (ListeningEntry, String) -> Unit)

/** Artwork and external provider links never determine the primary row destination. */
@Composable
internal fun rememberListeningEntryNavigation(
    viewModel: ListeningStatisticsViewModel, onOpenArtist: ((String) -> Unit)?, onOpenAlbum: ((String) -> Unit)?
): ListeningEntryNavigation {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var unmatched by remember { mutableStateOf<ListeningEntry?>(null) }
    if (opening) AlertDialog(onDismissRequest = {}, title = { Text("Finding in your library…") },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    unmatched?.let { entry ->
        if (entry.albumChoices.isNotEmpty() && onOpenAlbum != null) {
            ListeningAlbumChooser(entry, { id -> unmatched = null; onOpenAlbum(id) }, { unmatched = null })
        } else {
        AlertDialog(onDismissRequest = { unmatched = null }, title = { Text("Could not match this entry") },
            text = { Text("StillShelf could not link ${entry.title} to a unique library item. It may be listed under a different name, or the library could not be reached. This does not mean it is missing from your library.") },
            confirmButton = { TextButton(onClick = { unmatched = null }) { Text("Close") } },
            dismissButton = {
                entry.detailsUrl?.toHttpUrlOrNull()?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url.toString()) }; unmatched = null }) { Text("Open provider website") }
                }
            })
        }
    }
    return ListeningEntryNavigation(opening) { entry, kind ->
        if (!opening) {
            opening = true
            scope.launch {
                try {
                    val resolved = viewModel.resolveForNavigation(entry, kind) ?: return@launch
                    when {
                        resolved.artistId != null && onOpenArtist != null -> onOpenArtist(resolved.artistId)
                        resolved.albumId != null && onOpenAlbum != null -> onOpenAlbum(resolved.albumId)
                        else -> unmatched = resolved
                    }
                } finally { opening = false }
            }
        }
    }
}
