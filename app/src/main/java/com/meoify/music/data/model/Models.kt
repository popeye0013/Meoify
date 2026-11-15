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

enum class AudioQuality { HIGH, NORMAL, SAVER }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val quality: AudioQuality = AudioQuality.HIGH,
    val blurPlayerBackground: Boolean = true,
    val animatedCanvas: Boolean = true,
    val canvasOverCellular: Boolean = false,
    val historySync: Boolean = true,
    val ytCookies: String? = null,
    val accountName: String? = null,
    val localProfileName: String? = null,
    val localAvatarPath: String? = null,
    val onboardingCompleted: Boolean = false,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val value: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
