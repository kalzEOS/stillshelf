package com.stillshelf.app.scrobbling

/** Saved accounts are shared by reading and sending; selecting one never copies its secrets. */
internal fun ListeningSettings.saveConnection(
    updated: ListeningConnection, editingId: String? = null, selectStatistics: Boolean = false
): ListeningSettings {
    val previous = savedConnections.firstOrNull { it.revision == editingId }
        ?: savedConnections.firstOrNull { sameListeningAccount(it, updated) }
    val merged = if (previous != null && sameListeningAccount(previous, updated)) ListeningConnection(
        updated.provider, updated.serverUrl, updated.username, updated.token.ifBlank { previous.token },
        updated.apiKey.ifBlank { previous.apiKey }, updated.apiSecret.ifBlank { previous.apiSecret },
        updated.revision, if (editingId == null) previous.allowSubmissions else updated.allowSubmissions) else updated
    val saved = retainAccountRevision(previous, merged)
    val rows = savedConnections.filterNot { it.revision == previous?.revision || it.revision == saved.revision } + saved
    val changedSender = previous != null && sendingConnection?.revision == previous.revision && previous.revision != saved.revision
    return copy(connections = rows, multipleConnections = true,
        connection = if (selectStatistics || connection?.revision == previous?.revision && previous != null) saved else connection,
        statisticsSource = if (selectStatistics) StatisticsSource.EXTERNAL else statisticsSource,
        sendingConnectionId = if (changedSender) null else sendingConnection?.revision,
        scrobblingEnabled = scrobblingEnabled && !changedSender)
}

internal fun ListeningSettings.withoutConnection(id: String): ListeningSettings {
    val sendingRemoved = sendingConnection?.revision == id
    return copy(connections = savedConnections.filterNot { it.revision == id }, multipleConnections = true,
        connection = connection?.takeUnless { it.revision == id },
        sendingConnectionId = sendingConnection?.revision?.takeUnless { it == id },
        scrobblingEnabled = scrobblingEnabled && !sendingRemoved)
}

internal fun sameListeningAccount(a: ListeningConnection, b: ListeningConnection): Boolean =
    a.provider == b.provider && a.serverUrl.trimEnd('/') == b.serverUrl.trimEnd('/') && a.username.equals(b.username, ignoreCase = true)

internal fun ListeningConnection.choiceLabel(connections: List<ListeningConnection>): String =
    if (connections.count { it.provider == provider } > 1) "${provider.label} · ${username.ifBlank { serverUrl }}" else provider.label
