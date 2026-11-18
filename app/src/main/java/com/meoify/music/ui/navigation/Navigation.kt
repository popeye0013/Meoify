package com.meoify.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import com.meoify.music.data.model.LibraryData
import com.meoify.music.data.model.MediaCard
import com.meoify.music.data.model.PlaylistEdit
import com.meoify.music.data.model.Track
import com.meoify.music.playback.PlayerUiState

sealed class Destination(val key: String) {
    data class Album(val id: String) : Destination("album:$id")
    data class Playlist(val id: String) : Destination("playlist:$id")
    data class Artist(val id: String) : Destination("artist:$id")
    data object Liked : Destination("liked")
    data class LocalPlaylist(val id: String) : Destination("local:$id")
}

data class AppActions(
    val playTracks: (List<Track>, Int, String) -> Unit,
    /** Starts a search result, then queues its genre mix (or [fallback]). */
    val playFromSearch: (Track, List<Track>) -> Unit,
    val playShuffled: (List<Track>, String) -> Unit,
    val playNext: (Track) -> Unit,
    val addToQueue: (Track) -> Unit,
    /** Queues a whole playlist or album after the current song. */
    val playNextTracks: (List<Track>) -> Unit,
    /** Appends a whole playlist or album to the end of the queue. */
    val addTracksToQueue: (List<Track>) -> Unit,
    val moveQueueItem: (Int, Int) -> Unit,
    val playNextInQueue: (Int) -> Unit,
    val removeFromQueue: (Int) -> Unit,
    val clearQueue: () -> Unit,
    val toggleLike: (Track) -> Unit,
    val togglePlayPause: () -> Unit,
    val pause: () -> Unit,
    val sleepTimer: (Int) -> Unit,
    val skipNext: () -> Unit,
    val skipPrevious: () -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val seekTo: (Long) -> Unit,
    val openTrackMenu: (Track) -> Unit,
    val openAlbum: (String?) -> Unit,
    val openArtist: (String?) -> Unit,
    val openPlaylist: (String) -> Unit,
    val openLiked: () -> Unit,
    val openLocalPlaylist: (String) -> Unit,
    val openProfile: () -> Unit,
    val openLibrary: () -> Unit,
    val openSettings: () -> Unit,
    val search: (String) -> Unit,
    val showSnackbar: (String) -> Unit,
    val openPlayer: () -> Unit,
    val isLiked: (String) -> Boolean,
    val addToPlaylist: (String, Track) -> Unit,
    val removeFromPlaylist: (String, String) -> Unit,
    val createPlaylist: (String) -> Unit,
    val createPlaylistWithTrack: (String, Track) -> Unit,
    val savePlaylist: (String, List<Track>) -> Unit,
    val deletePlaylist: (String) -> Unit,
    val renamePlaylist: (String, String) -> Unit,
    val editPlaylist: (String, PlaylistEdit) -> Unit,
    val clearRecentSearches: () -> Unit,
    val clearRecent: () -> Unit,
)

val LocalAppActions = compositionLocalOf<AppActions> { error("AppActions not provided") }
val LocalLibrary = compositionLocalOf<LibraryData> { LibraryData() }
val LocalPlayerState = compositionLocalOf<PlayerUiState> { PlayerUiState() }
val LocalLikedIds = compositionLocalOf<Set<String>> { emptySet() }

/**
 * Extra bottom padding scrollable screens should add to their content, so the
 * last item can scroll clear of the floating mini player and tab bar. The
 * content itself is drawn behind that chrome, which is why the bar area shows
 * the page instead of an opaque strip.
 */
val LocalBottomContentInset = compositionLocalOf { 0.dp }

/** Playlists in the signed-in YouTube Music account. Empty when signed out. */
val LocalYtPlaylists = compositionLocalOf<List<MediaCard>> { emptyList() }
val LocalYtLoading = compositionLocalOf { false }
/** Number of songs in the account's Liked Music, while signed in. */
val LocalYtLikedCount = compositionLocalOf { 0 }
val LocalSignedIn = compositionLocalOf { false }

@Composable
fun rememberAppActions(): AppActions = LocalAppActions.current
