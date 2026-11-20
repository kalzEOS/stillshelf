package com.stillshelf.app.scrobbling

import com.stillshelf.app.core.model.*
import com.stillshelf.app.core.util.AppResult
import com.stillshelf.app.data.repo.NavidromeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Match the recording/current album, never its old file ID, codec or provider artwork. */
internal fun matchListeningTrackEntry(entry: ListeningEntry, results: NavidromeSearchResults, artistId: String? = null, allowUnique: Boolean = true): ListeningEntry {
    fun same(a: String, b: String) = a.trim().equals(b.trim(), true) ||
        listeningLibraryName(a).let { it.isNotBlank() && it == listeningLibraryName(b) }
    val identified = results.tracks.filter { entry.musicBrainzRecordingId != null && it.musicBrainzId.equals(entry.musicBrainzRecordingId, true) }
    val named = results.tracks.filter {
        same(it.title, entry.title) && entry.subtitle.isNotBlank() && (same(it.artistName, entry.subtitle) || (artistId != null && it.artistId == artistId))
    }
    val compatible = named.filter {
        entry.musicBrainzRecordingId == null || it.musicBrainzId == null || it.musicBrainzId.equals(entry.musicBrainzRecordingId, true)
    }
    val identityConflict = identified.isEmpty() && compatible.isEmpty() && named.isNotEmpty()
    // Another tagged version may be offered explicitly, but never silently declared the same recording.
    val candidates = identified.ifEmpty { compatible.ifEmpty { named } }
    val albumMatches = if (entry.albumName.isBlank()) candidates else candidates.filter { same(it.albumName, entry.albumName) }
    val preferred = albumMatches.ifEmpty { candidates }
    val destinations = preferred.filter { !it.albumId.isNullOrBlank() }.groupBy { it.albumId!! }
    val unique = destinations.values.singleOrNull()?.firstOrNull()
    if (allowUnique && unique != null && !identityConflict && (identified.isNotEmpty() || entry.albumName.isBlank() || albumMatches.isNotEmpty()))
        return entry.copy(albumId = unique.albumId, artworkUrl = unique.coverUrl ?: entry.artworkUrl, albumChoices = emptyList(), albumChoiceFormats = emptyMap())
    val choices = destinations.map { (id, tracks) ->
        results.albums.firstOrNull { it.id == id } ?: tracks.first().let {
            NavidromeAlbum(id, it.albumName, it.artistName, it.artistId, null, 0, null, it.coverUrl, null)
        }
    }
    val formats = destinations.mapValues { (_, tracks) -> tracks.mapNotNull { it.formatLabel?.takeIf(String::isNotBlank) }.distinct().joinToString(" / ") }
    return entry.copy(albumChoices = choices, albumChoiceFormats = formats.filterValues(String::isNotBlank))
}

/** A tap may inspect up to six current albums; scrolling never fans out album-track requests. */
internal suspend fun findListeningTrackInAlbums(
    entry: ListeningEntry, repository: NavidromeRepository,
    onPartial: (ListeningEntry) -> Unit = {},
    resolveArtist: suspend (ListeningEntry) -> ListeningEntry
): ListeningEntry {
    val artist = resolveArtist(ListeningEntry(entry.subtitle, musicBrainzArtistId = entry.musicBrainzArtistId))
    val id = artist.artistId ?: return entry
    val detail = (repository.fetchArtistDetail(id, forceRefresh = true) as? AppResult.Success)?.value ?: return entry
    val title = listeningLibraryName(entry.albumName)
    val albums = detail.albums.distinctBy { it.id }.sortedByDescending {
        when {
            entry.musicBrainzReleaseId != null && it.musicBrainzId.equals(entry.musicBrainzReleaseId, true) -> 3
            title.isNotBlank() && listeningLibraryName(it.name) == title -> 2
            title.isNotBlank() && listeningLibraryName(it.name).startsWith(title) -> 1
            else -> 0
        }
    }.take(6)
    val requests = Semaphore(3)
    val full = mutableListOf<NavidromeAlbumDetail>()
    val resultsLock = Mutex()
    fun match(allowUnique: Boolean) = matchListeningTrackEntry(entry,
        NavidromeSearchResults(listOf(detail.artist), full.map { it.album }, full.flatMap { it.tracks }), id, allowUnique)
    coroutineScope { albums.map { album -> launch {
        val loaded = requests.withPermit {
            try { (repository.fetchAlbumDetail(album.id, forceRefresh = true) as? AppResult.Success)?.value }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        if (loaded != null) resultsLock.withLock {
            full += loaded
            // If another album times out, offer the confirmed candidates for explicit selection.
            // A partial discography cannot establish that a returned recording is unique.
            match(allowUnique = false).takeIf { it.albumChoices.isNotEmpty() }?.let(onPartial)
        }
    } }.joinAll() }
    return match(allowUnique = full.size == albums.size)
}
