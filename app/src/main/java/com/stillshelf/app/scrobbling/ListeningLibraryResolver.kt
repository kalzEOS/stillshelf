package com.stillshelf.app.scrobbling

import com.stillshelf.app.data.repo.NavidromeRepository
import com.stillshelf.app.core.model.NavidromeSearchResults
import com.stillshelf.app.core.model.NavidromeArtist
import com.stillshelf.app.core.util.AppResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer

/** Resolve displayed entries using bounded searches and the cached artist index, never listening history. */
internal class ListeningLibraryResolver(private val repository: NavidromeRepository, private val aliases: ListeningArtistAliases? = null) {
    private val requests = Semaphore(3)
    suspend fun resolve(entry: ListeningEntry, kind: String, forceRefresh: Boolean = false): ListeningEntry =
        if (forceRefresh) resolveInLibrary(entry, kind, true) // A tap must not wait behind background enrichment.
        else requests.withPermit { resolveInLibrary(entry, kind, false) }

    private suspend fun resolveInLibrary(entry: ListeningEntry, kind: String, forceRefresh: Boolean): ListeningEntry {
        if (entry.title.isBlank() || repository.currentAuth() == null) return entry
        var fallback = entry
        val resolved = withTimeoutOrNull(8_000) {
            suspend fun search(query: String): ListeningEntry {
                val matches = (repository.search(query, forceRefresh) as? AppResult.Success)?.value ?: return entry
                return matchListeningLibraryEntry(entry, kind, matches)
            }
            var found = search(entry.title)
            fallback = found
            if (!found.hasLibraryDestination() && kind == "artist") {
                // search3 returns only a small ranked page and can miss Arabic/alternate names.
                // getArtists is a cached, server/library-scoped index and also carries stable MBIDs.
                val artists = try { (repository.fetchArtists() as? AppResult.Success)?.value.orEmpty() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
                found = matchListeningLibraryEntry(entry, kind, NavidromeSearchResults(artists, emptyList(), emptyList()))
                if (!found.hasLibraryDestination() && entry.musicBrainzArtistId != null && aliases != null) {
                    // Only a tap may request public aliases; scrolling never fans out metadata calls.
                    val names = aliases.names(entry.musicBrainzArtistId, allowFetch = forceRefresh)
                    val candidates = artists.filter { artist -> names.any { listeningLibraryName(it) == listeningLibraryName(artist.name) } }
                    found = matchListeningLibraryEntry(entry.copy(title = candidates.singleOrNull()?.name ?: entry.title), kind,
                        NavidromeSearchResults(candidates, emptyList(), emptyList())).copy(title = entry.title)
                }
            }
            val query = listeningLibraryName(entry.title)
            if (!found.hasLibraryDestination() && query.isNotBlank() && query != entry.title) {
                val retried = search(query)
                if (retried.hasLibraryDestination() || retried.albumChoices.isNotEmpty()) found = retried
            }
            fallback = found
            if (!found.hasLibraryDestination() && kind == "album" && entry.subtitle.isNotBlank()) {
                try {
                    // Reuse the artist's cached discography, not an arbitrary first search result.
                    val artist = resolveInLibrary(ListeningEntry(entry.subtitle, musicBrainzArtistId = entry.musicBrainzArtistId), "artist", forceRefresh)
                    val detail = artist.artistId?.let { (repository.fetchArtistDetail(it) as? AppResult.Success)?.value }
                    if (detail != null) {
                        val albums = detail.albums.distinctBy { it.id }
                        val contextual = matchListeningLibraryEntry(entry, kind, NavidromeSearchResults(emptyList(),
                            albums.map { it.copy(artistName = entry.subtitle) }, emptyList()))
                        found = if (contextual.hasLibraryDestination()) contextual else contextual.copy(albumChoices =
                            contextual.albumChoices.ifEmpty { albums }.map { candidate -> albums.first { it.id == candidate.id } })
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { /* Retain valid search candidates. */ }
            }
            if (!found.hasLibraryDestination() && kind == "track" && forceRefresh && entry.subtitle.isNotBlank()) {
                try {
                    val current = findListeningTrackInAlbums(entry, repository, onPartial = { fallback = it }) {
                        resolveInLibrary(it, "artist", true)
                    }
                    if (current.hasLibraryDestination() || current.albumChoices.isNotEmpty()) found = current
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { /* Retain valid search candidates. */ }
            }
            found
        } ?: fallback
        return if (kind == "artist" && resolved.artistId != null && resolved.artworkUrl == null)
            resolved.copy(artworkUrl = repository.listeningCoverArtUrl("ar-${resolved.artistId}"))
        else resolved
    }
}

internal fun matchListeningLibraryEntry(entry: ListeningEntry, kind: String, results: NavidromeSearchResults): ListeningEntry {
    fun same(left: String, right: String) = left.trim().equals(right.trim(), ignoreCase = true) ||
        listeningLibraryName(left).let { it.isNotBlank() && it == listeningLibraryName(right) }
    fun artistMatches(): List<NavidromeArtist> {
        val id = entry.musicBrainzArtistId
        val identified = results.artists.filter { id != null && it.musicBrainzId.equals(id, ignoreCase = true) }
        return if (identified.isNotEmpty()) identified else results.artists.filter {
            same(it.name, entry.title) && (id == null || it.musicBrainzId == null || it.musicBrainzId.equals(id, ignoreCase = true))
        }
    }
    return when (kind) {
        "artist" -> artistMatches().distinctBy { it.id }.singleOrNull()?.let {
            entry.copy(artistId = it.id, artworkUrl = it.imageUrl ?: it.coverUrl ?: entry.artworkUrl)
        }
        "album" -> {
            val identified = results.albums.filter { entry.musicBrainzReleaseId != null && it.musicBrainzId.equals(entry.musicBrainzReleaseId, true) }
            val matches = identified.ifEmpty { results.albums.filter {
                same(it.name, entry.title) && entry.subtitle.isNotBlank() && same(it.artistName, entry.subtitle)
            } }.distinctBy { it.id }
            matches.singleOrNull()?.let { entry.copy(albumId = it.id, artworkUrl = it.coverUrl ?: entry.artworkUrl, albumChoices = emptyList()) }
                ?: entry.copy(albumChoices = matches)
        }
        else -> matchListeningTrackEntry(entry, results)
    } ?: entry
}

internal fun ListeningEntry.hasLibraryDestination() = artistId != null || albumId != null

/** Canonical spelling only: no guessed transliteration, substring, or fuzzy artist selection. */
internal fun listeningLibraryName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .lowercase(java.util.Locale.ROOT)
    .replace(Regex("[\\p{M}\\p{Cf}ـ]"), "")
    .replace('ى', 'ي').replace('ی', 'ي').replace('ک', 'ك')
    .replace(Regex("['’‘ʼ`\"“”]"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
