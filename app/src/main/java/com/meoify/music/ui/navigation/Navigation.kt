package com.meoify.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.meoify.music.data.model.LibraryData
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
    val playShuffled: (List<Track>, String) -> Unit,
    val playNext: (Track) -> Unit,
    val addToQueue: (Track) -> Unit,
    val toggleLike: (Track) -> Unit,
    val togglePlayPause: () -> Unit,
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
    val createPlaylist: (String) -> String,
    val savePlaylist: (String, List<Track>) -> Unit,
    val deletePlaylist: (String) -> Unit,
    val renamePlaylist: (String, String) -> Unit,
    val clearRecentSearches: () -> Unit,
    val clearRecent: () -> Unit,
)

val LocalAppActions = compositionLocalOf<AppActions> { error("AppActions not provided") }
val LocalLibrary = compositionLocalOf<LibraryData> { LibraryData() }
val LocalPlayerState = compositionLocalOf<PlayerUiState> { PlayerUiState() }
val LocalLikedIds = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun rememberAppActions(): AppActions = LocalAppActions.current
