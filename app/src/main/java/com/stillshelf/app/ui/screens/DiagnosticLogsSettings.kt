package com.stillshelf.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Surface
import java.io.File

@Composable
fun DiagnosticLogsSettingsCard(
    enabled: Boolean,
    hasLogs: Boolean,
    actionsExpanded: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onActionsExpandedChange: (Boolean) -> Unit,
    onShowLogsClick: () -> Unit,
    onExportClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenIssuesClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSeeWhatCollectedClick: () -> Unit,
    containerColor: Color,
    border: androidx.compose.foundation.BorderStroke?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        border = border
    ) {
        Column {
            DiagnosticLogToggleRow(
                enabled = enabled,
                onEnabledChange = onEnabledChange
            )
            if (enabled || hasLogs) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    DiagnosticLogsActionsHeaderRow(
                        enabled = enabled,
                        hasLogs = hasLogs,
                        expanded = actionsExpanded,
                        onShowLogsClick = onShowLogsClick,
                        onClick = { onActionsExpandedChange(!actionsExpanded) }
                    )
                    AnimatedVisibility(visible = actionsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        )
                        {
                            OutlinedButton(
                                onClick = onSeeWhatCollectedClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("See what is being collected")
                            }
                            if (hasLogs) {
                                Text(
                                    text = "When you have logs, you can choose a file to export or share, delete old logs, or open GitHub Issues.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onExportClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Export diagnostic log")
                                }
                                OutlinedButton(
                                    onClick = onShareClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Share diagnostic log")
                                }
                                OutlinedButton(
                                    onClick = onOpenIssuesClick,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open GitHub Issues")
                                }
                                Button(
                                    onClick = onDeleteClick,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Delete diagnostic logs")
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
private fun DiagnosticLogToggleRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Enable diagnostic logs",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Stores local diagnostic logs to help troubleshoot crashes and playback issues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun DiagnosticLogsActionsHeaderRow(
    enabled: Boolean,
    hasLogs: Boolean,
    expanded: Boolean,
    onShowLogsClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
                .clickable(enabled = enabled || hasLogs) { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (enabled) "Diagnostic log actions" else "Saved logs available",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (enabled) {
                    "Tap to hide or show log actions."
                } else {
                    "Tap to export or share old logs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hasLogs) {
            TextButton(onClick = onShowLogsClick) {
                Text("Show logs")
            }
        }
        if (!enabled && hasLogs) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .graphicsLayer {
                    rotationZ = if (expanded) 90f else 0f
                }
        )
    }
}

@Composable
fun DiagnosticLogsBrowserDialog(
    logFiles: List<File>,
    onOpenLogClick: (File) -> Unit,
    onExportLogClick: (File) -> Unit,
    onShareLogClick: (File) -> Unit,
    onDeleteLogClick: (File) -> Unit,
    onDismissRequest: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Saved logs") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "These logs are stored in the app's private storage. Tap Open to view one in a popup window, or choose Export/Share on the file you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (logFiles.isEmpty()) {
                    Text(
                        text = "No log files are available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logFiles.forEach { file ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Saved locally. ${formatLogFileSize(file.length())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { onOpenLogClick(file) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open")
                                }
                                OutlinedButton(
                                    onClick = { onExportLogClick(file) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Export")
                                }
                                OutlinedButton(
                                    onClick = { onShareLogClick(file) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Share")
                                }
                                Button(
                                    onClick = { onDeleteLogClick(file) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DiagnosticLogViewerDialog(
    title: String,
    content: String,
    onDismissRequest: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(content) {
        withFrameNanos { }
        scrollState.scrollTo(scrollState.maxValue)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                SelectionContainer {
                    Text(
                        text = content.ifBlank { "The log file is empty." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DiagnosticLogsCollectedInfoDialog(
    onDismissRequest: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("What is collected") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Stores local diagnostic logs on this device to help troubleshoot crashes, playback issues, and network or cache problems. Logs are never uploaded automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionText(
                    title = "What’s included in logs:",
                    items = listOf(
                        "Timestamps for each log entry",
                        "App version",
                        "Android version",
                        "Device model",
                        "Backend in use (Audiobookshelf or Navidrome)",
                        "Random session ID",
                        "Lifecycle and app events such as backend changes, app foreground/background changes, update checks, sync actions, and cache clear events",
                        "Error messages and crash stack traces",
                        "Playback command traces (play, pause, seek, next/previous)",
                        "Playback state transitions (e.g. buffering, ready, idle, ended)",
                        "Audio focus changes and output-route changes",
                        "Home feed cache hits and misses",
                        "Content and detail cache clear events",
                        "Playback snapshot save, restore, and clear events",
                        "Network error types (without URLs, headers, or tokens)"
                    )
                )
                SectionText(
                    title = "What is NOT collected:",
                    items = listOf(
                        "Usernames or passwords",
                        "Auth tokens or API keys",
                        "Server URLs or IP addresses",
                        "Email addresses",
                        "Media names (books, songs, authors, albums)",
                        "File paths or library names",
                        "Request/response bodies, headers, or cookies"
                    )
                )
                Text(
                    text = "Sensitive data is not collected. In the unlikely event that any is captured (e.g. due to a bug), it is automatically redacted or hashed before being written.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SectionText(
    title: String,
    items: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatLogFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Unknown size"
    return when {
        sizeBytes < 1024L -> "$sizeBytes B"
        sizeBytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
    }
}
