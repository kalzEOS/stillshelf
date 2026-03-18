package com.stillshelf.app.core.model

data class NavidromeSession(
    val baseUrl: String,
    val username: String
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
    val streamUrl: String
)

data class NavidromePlaylist(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val durationSeconds: Int? = null
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

data class NavidromeHome(
    val recentAlbums: List<NavidromeAlbum>,
    val artists: List<NavidromeArtist>,
    val playlists: List<NavidromePlaylist>
)

data class NavidromePlayerState(
    val currentTrack: NavidromeTrack? = null,
    val queue: List<NavidromeTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
