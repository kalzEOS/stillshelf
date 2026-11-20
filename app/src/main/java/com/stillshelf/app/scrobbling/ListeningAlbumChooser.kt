package com.stillshelf.app.scrobbling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ListeningAlbumChooser(entry: ListeningEntry, onChoose: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Choose a library album") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose the current library album for ${entry.title}.")
            entry.albumChoices.forEach { album ->
                Column(Modifier.fillMaxWidth().testTag("library-album-choice-${album.id}").clickable { onChoose(album.id) }.padding(vertical = 10.dp)) {
                    Text(album.name, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(album.artistName, album.year?.toString(), album.songCount.takeIf { it > 0 }?.let { "$it tracks" }, entry.albumChoiceFormats[album.id]).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
