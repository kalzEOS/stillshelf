package com.stillshelf.app.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

val navidromeEqualizerBandFrequenciesHz = listOf(32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
const val NAVIDROME_EQUALIZER_MIN_DB = -6f
const val NAVIDROME_EQUALIZER_MAX_DB = 6f
const val NAVIDROME_EQUALIZER_STEP_DB = 1f

fun flatNavidromeEqualizerBandLevels(): List<Float> = List(navidromeEqualizerBandFrequenciesHz.size) { 0f }

data class NavidromeServer(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val createdAt: Long
)

data class NavidromeSession(
    val serverId: String,
    val serverName: String?,
    val baseUrl: String,
    val username: String
)

data class NavidromeLibrary(
    val id: String,
    val name: String
)

data class NavidromeLibraryResyncProgress(
    val title: String,
    val detail: String,
    val completedSteps: Int,
    val totalSteps: Int
)

data class NavidromeServerScanStatus(
    val scanning: Boolean,
    val scannedCount: Int? = null,
    val folderCount: Int? = null,
    val lastScanLabel: String? = null
)

data class NavidromeServerScanProgress(
    val title: String,
    val detail: String,
    val status: NavidromeServerScanStatus? = null,
    val isRunning: Boolean = true
)

data class NavidromeArtist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverUrl: String?,
    val imageUrl: String?
)

data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String?,
    val year: Int?,
    val songCount: Int,
    val durationSeconds: Int?,
    val coverUrl: String?,
    val genre: String?
)

data class NavidromeTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumId: String?,
    val artistId: String?,
    val trackNumber: Int?,
    val durationSeconds: Int?,
    val coverUrl: String?,
    val streamUrl: String,
    val formatLabel: String?,
    val bitRateKbps: Int?
)

data class NavidromePlaylist(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val durationSeconds: Int? = null,
    val artworkUrls: List<String> = emptyList()
)

data class NavidromePlaylistDetail(
    val playlist: NavidromePlaylist,
    val tracks: List<NavidromeTrack>
)

data class NavidromeRadio(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String?
)

data class NavidromeArtistDetail(
    val artist: NavidromeArtist,
    val albums: List<NavidromeAlbum>
)

data class NavidromeAlbumDetail(
    val album: NavidromeAlbum,
    val tracks: List<NavidromeTrack>
)

data class NavidromeSearchResults(
    val artists: List<NavidromeArtist>,
    val albums: List<NavidromeAlbum>,
    val tracks: List<NavidromeTrack>
)

enum class NavidromeQueueDisplayMode {
    FULL,
    SONGS_TAB_PREVIEW
}

data class NavidromeHome(
    val recentAlbums: List<NavidromeAlbum>,
    val artists: List<NavidromeArtist>,
    val playlists: List<NavidromePlaylist>,
    val radios: List<NavidromeRadio>
)

data class NavidromeOutputDevice(
    val id: Int?,
    val name: String,
    val typeLabel: String
)

data class NavidromeEqualizerProfile(
    val id: String,
    val name: String,
    val bandLevelsDb: List<Float> = flatNavidromeEqualizerBandLevels()
) {
    fun normalizedBandLevelsDb(): List<Float> {
        return navidromeEqualizerBandFrequenciesHz.indices.map { index ->
            val rawLevel = bandLevelsDb.getOrNull(index)
            if (rawLevel == null || !rawLevel.isFinite()) {
                0f
            } else {
                rawLevel
                    .coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)
                    .let { level -> (level / NAVIDROME_EQUALIZER_STEP_DB).roundToInt().toFloat() * NAVIDROME_EQUALIZER_STEP_DB }
            }
        }
    }

    fun effectiveBandLevelsDb(): List<Float> {
        val normalizedLevels = normalizedBandLevelsDb()
        val peakLevel = normalizedLevels.maxOrNull() ?: 0f
        return normalizedLevels.map { level -> level - peakLevel }
    }

    fun isFlat(): Boolean = effectiveBandLevelsDb().all { abs(it) < 0.001f }
}

data class NavidromePlayerState(
    val currentTrack: NavidromeTrack? = null,
    val recentTracks: List<NavidromeTrack> = emptyList(),
    val queue: List<NavidromeTrack> = emptyList(),
    val queueDisplayMode: NavidromeQueueDisplayMode = NavidromeQueueDisplayMode.FULL,
    val currentIndex: Int = -1,
    val outputDevices: List<NavidromeOutputDevice> = emptyList(),
    val selectedOutputDeviceId: Int? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val errorMessage: String? = null
)
