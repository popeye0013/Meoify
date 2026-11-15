package com.meoify.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.AppContainer
import com.meoify.music.data.model.AppSettings
import com.meoify.music.data.model.LibraryData
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.UiState
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.navigation.AppActions
import com.meoify.music.ui.navigation.Destination
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import kotlinx.coroutines.launch

enum class Tab { Home, Search, Library }

@Composable
fun MeoifyApp(container: AppContainer) {
    val library by container.library.library.collectAsStateWithLifecycle(initialValue = LibraryData())
    val playerState by container.player.state.collectAsStateWithLifecycle(initialValue = PlayerUiState())
    val scope = rememberCoroutineScope()

    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    LaunchedEffect(Unit) {
        container.player.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    is com.meoify.music.playback.PlaybackEvent.Message -> event.text
                    is com.meoify.music.playback.PlaybackEvent.Failure -> event.text
                },
            )
        }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val detailStack = remember { mutableStateListOf<Destination>() }
    var showPlayer by remember { mutableStateOf(false) }
    var pendingSearch by remember { mutableStateOf<String?>(null) }
    var trackMenuTrack by remember { mutableStateOf<Track?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }

    val actions = remember(container, library, playerState) {
        AppActions(
            playTracks = { tracks, index, source ->
                container.player.playTracks(tracks, index, source)
                if (tracks.isNotEmpty()) {
                    container.library.addRecent(tracks.getOrNull(index) ?: tracks.first())
                }
                showPlayer = true
            },
            playShuffled = { tracks, source ->
                if (tracks.isNotEmpty()) {
                    container.player.playTracks(tracks.shuffled(), 0, source)
                    container.library.addRecent(tracks.first())
                    showPlayer = true
                }
            },
            playNext = { track -> container.player.playNext(track) },
            addToQueue = { track -> container.player.addToQueue(track) },
            toggleLike = { track -> container.library.toggleLike(track) },
            togglePlayPause = { container.player.togglePlayPause() },
            skipNext = { container.player.skipNext() },
            skipPrevious = { container.player.skipPrevious() },
            toggleShuffle = { container.player.toggleShuffle() },
            cycleRepeat = { container.player.cycleRepeat() },
            seekTo = { pos -> container.player.seekTo(pos) },
            openTrackMenu = { track -> trackMenuTrack = track },
            openAlbum = { id -> if (id != null) detailStack.add(Destination.Album(id)) },
            openArtist = { id -> if (id != null) detailStack.add(Destination.Artist(id)) },
            openPlaylist = { id -> detailStack.add(Destination.Playlist(id)) },
            openLiked = { detailStack.add(Destination.Liked) },
            openLocalPlaylist = { id -> detailStack.add(Destination.LocalPlaylist(id)) },
            openProfile = { settingsOpen = true },
            openLibrary = {
                detailStack.clear()
                tab = Tab.Library
            },
            openSettings = { settingsOpen = true },
            search = { query ->
                tab = Tab.Search
                pendingSearch = query
            },
            showSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            openPlayer = { showPlayer = true },
            isLiked = { id -> library.liked.any { it.id == id } },
            addToPlaylist = { id, track -> container.library.addToPlaylist(id, track) },
            removeFromPlaylist = { id, trackId -> container.library.removeFromPlaylist(id, trackId) },
            createPlaylist = { name -> container.library.createPlaylist(name) },
            savePlaylist = { name, tracks -> container.library.savePlaylist(name, tracks) },
            deletePlaylist = { id -> container.library.deletePlaylist(id) },
            renamePlaylist = { id, name -> container.library.renamePlaylist(id, name) },
            clearRecentSearches = { container.library.clearRecentSearches() },
            clearRecent = { container.library.clearRecent() },
        )
    }

    CompositionLocalProvider(
        LocalAppActions provides actions,
        LocalLibrary provides library,
        LocalPlayerState provides playerState,
    ) {
        MeoifyScaffold(
            tab = tab,
            onTabChange = { tab = it },
            detailStack = detailStack,
            showPlayer = showPlayer,
            onShowPlayerChange = { showPlayer = it },
            pendingSearch = pendingSearch,
            onConsumedPending = { pendingSearch = null },
            trackMenuTrack = trackMenuTrack,
            onTrackMenuDismiss = { trackMenuTrack = null },
            addToPlaylistTrack = addToPlaylistTrack,
            onAddToPlaylistDismiss = { addToPlaylistTrack = null },
            onAddToPlaylist = { addToPlaylistTrack = it },
            showCreatePlaylist = showCreatePlaylist,
            onShowCreatePlaylist = { showCreatePlaylist = it },
            newPlaylistName = newPlaylistName,
            onNewPlaylistNameChange = { newPlaylistName = it },
            settingsOpen = settingsOpen,
            onSettingsOpenChange = { settingsOpen = it },
            snackbarHostState = snackbarHostState,
        )
    }
}
