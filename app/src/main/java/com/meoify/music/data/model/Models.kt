package com.meoify.music.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    TRACK,
    ALBUM,
    PLAYLIST,
    ARTIST,
    VIDEO,
    MIX,
}

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val plays: String? = null,
    val explicit: Boolean = false,
) {
    val subtitle: String get() = listOfNotNull(artist.ifBlank { null }, album).joinToString(" • ")
}

@Serializable
data class MediaCard(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val round: Boolean = false,
)

/** The signed-in YouTube Music account, as far as the web client exposes it. */
data class AccountInfo(
    val name: String? = null,
    val email: String? = null,
    val handle: String? = null,
    val avatarUrl: String? = null,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "You"

    val initials: String
        get() = displayName
            .split(' ', '.', '_', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "M" }
}

/** A titled row on Home, kept exactly as YouTube Music or Meoify named it. */
data class HomeShelf(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList(),
    val items: List<MediaCard> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && items.isEmpty()
}

data class HomeFeed(
    val quickPicks: List<Track> = emptyList(),
    val communityPlaylists: List<MediaCard> = emptyList(),
    val newReleases: List<MediaCard> = emptyList(),
    val topArtists: List<MediaCard> = emptyList(),
    val chartPlaylists: List<MediaCard> = emptyList(),
    /** Shelves sent by YouTube Music, in server order. */
    val shelves: List<HomeShelf> = emptyList(),
    /** Meoify's personalised shelves, shown after New releases. */
    val personalShelves: List<HomeShelf> = emptyList(),
)

/** A playlist parsed from a Spotify or YouTube Music link, before saving. */
data class ImportedPlaylist(
    val title: String,
    val tracks: List<Track>,
)

data class SearchResults(
    val query: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<MediaCard> = emptyList(),
    val artists: List<MediaCard> = emptyList(),
    val playlists: List<MediaCard> = emptyList(),
)

data class DetailPage(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val tracks: List<Track> = emptyList(),
    val sections: List<DetailSection> = emptyList(),
    val roundArt: Boolean = false,
    /** The signed-in account created this playlist, so it can be edited. */
    val owned: Boolean = false,
    /** PUBLIC, UNLISTED or PRIVATE for an owned playlist. */
    val privacy: String? = null,
    /** ON, PAUSED or OFF when YouTube Music exposes the setting. */
    val comments: String? = null,
)

/** The editable metadata of a playlist, as shown on YouTube Music. */
data class PlaylistEdit(
    val title: String,
    val description: String = "",
    val privacy: String = "PRIVATE",
    val comments: String = "ON",
)

data class DetailSection(
    val title: String,
    val items: List<MediaCard> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0L,
) {
    val artworkUrl: String? get() = tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
}

data class LibraryData(
    val liked: List<Track> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val recentSearches: List<String> = emptyList(),
) {
    fun isLiked(trackId: String): Boolean = liked.any { it.id == trackId }
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class AudioQuality(
    val title: String,
    val tag: String,
    val shortLabel: String,
    val description: String,
) {
    HI_RES(
        title = "Hi-Res Audio",
        tag = "24-BIT /192k",
        shortLabel = "Hi-Res",
        description = "Up to 24-bit / 192 kHz. Studio Master FLAC",
    ),
    CD_LOSSLESS(
        title = "CD Lossless",
        tag = "16-BIT / 44.1k",
        shortLabel = "CD FLAC",
        description = "16-bit / 44.1 kHz Bit-Exact CD FLAC",
    ),
    STANDARD_320(
        title = "Standard Quality",
        tag = "320 kbps",
        shortLabel = "320 kbps",
        description = "320 kbps High-Bitrate MP3 / AAC",
    ),
    YOUTUBE(
        title = "YouTube Music",
        tag = "YOUTUBE",
        shortLabel = "YT",
        description = "128-256 kbps YouTube Music AAC / Opus stream",
    ),
    DATA_SAVER(
        title = "Data Saver",
        tag = "96 kbps",
        shortLabel = "96 kbps",
        description = "96 kbps High Efficiency AAC",
    ),
    AUTO(
        title = "Auto",
        tag = "WIFI/DATA",
        shortLabel = "Auto",
        description = "Adapts to your connection. Wi-Fi uses Hi-Res; mobile data stays balanced.",
    ),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val quality: AudioQuality = AudioQuality.AUTO,
    /** Base URL of a hifi-api or Meoify 320 kbps server. */
    val losslessServer: String? = null,
    /** User-defined label shown for the lossless server in Settings. */
    val losslessServerName: String? = null,
    val historySync: Boolean = true,
    /** Submits listens to Last.fm when a track changes and after a qualifying listen. */
    val scrobblingEnabled: Boolean = false,
    val lastfmApiKey: String? = null,
    val lastfmApiSecret: String? = null,
    val lastfmSessionKey: String? = null,
    val lastfmUsername: String? = null,
    val ytCookies: String? = null,
    val accountName: String? = null,
    val localProfileName: String? = null,
    val localAvatarPath: String? = null,
    val libraryGridView: Boolean = false,
    /** Persisted Library sort mode (LibrarySort.name). */
    val librarySort: String = "NAME",
    /** Shows synced lyrics beside the artwork on Now Playing. */
    val lyricsPreview: Boolean = true,
    val onboardingCompleted: Boolean = false,
    /** Version whose home-screen update popup was skipped; cleared by a newer release. */
    val skippedUpdateVersion: String? = null,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
