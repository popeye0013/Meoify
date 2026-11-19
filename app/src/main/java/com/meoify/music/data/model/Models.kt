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

data class HomeFeed(
    val quickPicks: List<Track> = emptyList(),
    val communityPlaylists: List<MediaCard> = emptyList(),
    val newReleases: List<MediaCard> = emptyList(),
    val topArtists: List<MediaCard> = emptyList(),
    val chartPlaylists: List<MediaCard> = emptyList(),
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
    val shortLabel: String,
    val description: String,
) {
    AUTO(
        title = "Auto",
        shortLabel = "Auto",
        description = "Adapts to your connection. Wi-Fi uses Hi-Res; mobile data stays balanced.",
    ),
    HI_RES(
        title = "Lossless (Hi-Res)",
        shortLabel = "Hi-Res",
        description = "Up to 24-bit FLAC from a hifi-api/Tidal server",
    ),
    STANDARD(
        title = "Standard quality (320 kbps)",
        shortLabel = "320 kbps",
        description = "320 kbps AAC from a hifi-api or Meoify 320 server",
    ),
    YOUTUBE_MUSIC(
        title = "Balanced (256 kbps)",
        shortLabel = "256 kbps",
        description = "256 kbps • YouTube Music AAC/Opus stream",
    ),
    SAVER(
        title = "Data Saver",
        shortLabel = "Data Saver",
        description = "Low-bitrate audio to reduce mobile data use",
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
    val ytCookies: String? = null,
    val accountName: String? = null,
    val localProfileName: String? = null,
    val localAvatarPath: String? = null,
    val libraryGridView: Boolean = false,
    val onboardingCompleted: Boolean = false,
    /** Version whose home-screen update popup was skipped; cleared by a newer release. */
    val skippedUpdateVersion: String? = null,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
