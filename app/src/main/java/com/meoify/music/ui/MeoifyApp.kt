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
import com.meoify.music.data.model.PlaylistEdit
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.UiState
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.navigation.AppActions
import com.meoify.music.ui.navigation.Destination
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.navigation.LocalSignedIn
import com.meoify.music.ui.navigation.LocalYtLikedCount
import com.meoify.music.ui.navigation.LocalYtLoading
import com.meoify.music.ui.navigation.LocalYtPlaylists
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Tab { Home, Search, Library }

@Composable
fun MeoifyApp(
    container: AppContainer,
    initialLink: String? = null,
    onLinkConsumed: () -> Unit = {},
) {
    val library by container.library.library.collectAsStateWithLifecycle(initialValue = LibraryData())
    val playerState by container.player.state.collectAsStateWithLifecycle(initialValue = PlayerUiState())
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val ytState by container.ytLibrary.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext

    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    LaunchedEffect(settings.ytCookies) {
        container.repository.applyCookies(settings.ytCookies)
        if (settings.ytCookies != null) {
            container.ytLibrary.ensureLoaded()
        } else {
            container.ytLibrary.reset()
        }
    }

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
    var trackMenuPlaylistId by remember { mutableStateOf<String?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }

    // Open music.youtube.com links that Android hands to Meoify. Playlists go
    // straight to their page, ?q= links start a search, and plain song links
    // fall back to a hint since Meoify needs a catalogue match first.
    LaunchedEffect(initialLink) {
        val link = initialLink ?: return@LaunchedEffect
        val uri = runCatching { android.net.Uri.parse(link) }.getOrNull()
        val host = uri?.host.orEmpty()
        if (host.endsWith("music.youtube.com")) {
            val listId = uri?.getQueryParameter("list")
            val query = uri?.getQueryParameter("q")
            val videoId = uri?.getQueryParameter("v")
            when {
                !listId.isNullOrBlank() -> {
                    settingsOpen = false
                    detailStack.clear()
                    detailStack.add(Destination.Playlist(listId))
                }

                !query.isNullOrBlank() -> {
                    settingsOpen = false
                    detailStack.clear()
                    tab = Tab.Search
                    pendingSearch = query
                }

                !videoId.isNullOrBlank() -> {
                    settingsOpen = false
                    detailStack.clear()
                    tab = Tab.Search
                    pendingSearch = null
                    snackbarHostState.showSnackbar(
                        "Song links aren't supported yet — search for it in Meoify.",
                    )
                }
            }
        }
        onLinkConsumed()
    }

    val actions = remember(container, library, playerState) {
        AppActions(
            playTracks = { tracks, index, source ->
                container.player.playTracks(tracks, index, source)
                if (tracks.isNotEmpty()) {
                    container.library.addRecent(tracks.getOrNull(index) ?: tracks.first())
                }
                showPlayer = true
            },
            playFromSearch = { seed, fallback ->
                // Start the song right away; the genre mix is appended as soon
                // as YouTube Music returns it, so a slow lookup never delays
                // playback and the next search result never plays by mistake.
                container.player.playTracks(listOf(seed), 0, "Search")
                container.library.addRecent(seed)
                showPlayer = true
                scope.launch {
                    val mix = withTimeoutOrNull(7_000) {
                        container.repository.radioQueue(seed)
                    }.orEmpty()
                    val next = mix.ifEmpty { fallback }
                        .filterNot { it.id == seed.id }
                        .distinctBy { it.id }
                    if (next.isNotEmpty()) {
                        container.player.addTracksToQueue(next, expectedTrackId = seed.id)
                    }
                }
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
            playNextTracks = { tracks ->
                if (tracks.isNotEmpty()) container.player.addTracksToQueue(tracks, playNext = true)
            },
            addTracksToQueue = { tracks ->
                if (tracks.isNotEmpty()) container.player.addTracksToQueue(tracks)
            },
            moveQueueItem = { from, to -> container.player.moveQueueItem(from, to) },
            playNextInQueue = { index -> container.player.playNextInQueue(index) },
            removeFromQueue = { index -> container.player.removeFromQueue(index) },
            clearQueue = { container.player.clearQueue() },
            clearUpcoming = { container.player.clearUpcoming() },
            toggleLike = { track ->
                if (container.repository.signedIn) {
                    val wasLiked = library.liked.any { it.id == track.id }
                    scope.launch {
                        val ok = container.ytLibrary.setLiked(track, !wasLiked)
                        if (!ok) {
                            snackbarHostState.showSnackbar(
                                "Couldn't update Liked Music on YouTube Music",
                            )
                        }
                    }
                } else {
                    container.library.toggleLike(track)
                }
            },
            togglePlayPause = { container.player.togglePlayPause() },
            reloadCurrent = { container.player.reloadCurrent() },
            pause = { container.player.pause() },
            sleepTimer = { minutes -> container.player.setSleepTimer(minutes) },
            skipNext = { container.player.skipNext() },
            skipPrevious = { container.player.skipPrevious() },
            toggleShuffle = { container.player.toggleShuffle() },
            cycleRepeat = { container.player.cycleRepeat() },
            seekTo = { pos -> container.player.seekTo(pos) },
            openTrackMenu = { track, playlistId ->
                trackMenuTrack = track
                trackMenuPlaylistId = playlistId
            },
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
                settingsOpen = false
                detailStack.clear()
                tab = Tab.Search
                pendingSearch = query
            },
            showSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            openPlayer = { showPlayer = true },
            isLiked = { id -> library.liked.any { it.id == id } },
            addToPlaylist = { id, track ->
                if (container.repository.signedIn && !id.startsWith("local_")) {
                    scope.launch {
                        val ok = container.ytLibrary.addToPlaylist(id, track)
                        if (!ok) snackbarHostState.showSnackbar("Couldn't add to that playlist")
                    }
                } else {
                    container.library.addToPlaylist(id, track)
                }
            },
            removeFromPlaylist = { id, track ->
                if (id.startsWith("local_")) {
                    container.library.removeFromPlaylist(id, track.id)
                    container.playlistChanges.value = id
                    scope.launch { snackbarHostState.showSnackbar("Removed from playlist") }
                } else {
                    scope.launch {
                        val (ok, _) = container.repository.removeSongFromPlaylist(
                            id,
                            track.id,
                            track.setVideoId,
                        )
                        if (ok) {
                            container.ytLibrary.refresh()
                            container.playlistChanges.value = id
                        }
                    }
                }
            },
            createPlaylist = { name ->
                if (container.repository.signedIn) {
                    scope.launch {
                        val id = container.ytLibrary.createPlaylist(name)
                        snackbarHostState.showSnackbar(
                            if (id != null) {
                                "Playlist created on YouTube Music"
                            } else {
                                "Couldn't create playlist"
                            },
                        )
                    }
                } else {
                    container.library.createPlaylist(name)
                    scope.launch { snackbarHostState.showSnackbar("Playlist created") }
                }
            },
            createPlaylistWithTrack = { name, track ->
                if (container.repository.signedIn) {
                    scope.launch {
                        val id = container.ytLibrary.createPlaylist(name, listOf(track))
                        snackbarHostState.showSnackbar(
                            if (id != null) {
                                "Added to “$name” on YouTube Music"
                            } else {
                                "Couldn't create playlist"
                            },
                        )
                    }
                } else {
                    val id = container.library.createPlaylist(name)
                    container.library.addToPlaylist(id, track)
                }
            },
            savePlaylist = { name, tracks ->
                if (container.repository.signedIn) {
                    scope.launch {
                        val id = container.ytLibrary.createPlaylist(name, tracks)
                        snackbarHostState.showSnackbar(
                            if (id != null) {
                                "Saved to YouTube Music"
                            } else {
                                "Couldn't save playlist"
                            },
                        )
                    }
                } else {
                    container.library.savePlaylist(name, tracks)
                }
            },
            deletePlaylist = { id ->
                if (container.repository.signedIn && !id.startsWith("local_")) {
                    scope.launch {
                        val ok = container.ytLibrary.deletePlaylist(id)
                        snackbarHostState.showSnackbar(
                            if (ok) "Playlist deleted" else "Couldn't delete playlist",
                        )
                    }
                } else {
                    container.library.deletePlaylist(id)
                }
            },
            renamePlaylist = { id, name ->
                if (container.repository.signedIn && !id.startsWith("local_")) {
                    scope.launch {
                        val ok = container.ytLibrary.renamePlaylist(id, name)
                        if (!ok) snackbarHostState.showSnackbar("Couldn't rename playlist")
                    }
                } else {
                    container.library.renamePlaylist(id, name)
                }
            },
            editPlaylist = { id, edit ->
                if (container.repository.signedIn && !id.startsWith("local_")) {
                    scope.launch {
                        val ok = container.ytLibrary.editPlaylist(
                            id = id,
                            title = edit.title,
                            description = edit.description,
                            privacy = edit.privacy,
                            comments = edit.comments,
                        )
                        snackbarHostState.showSnackbar(
                            if (ok) "Playlist updated" else "Couldn't update playlist",
                        )
                    }
                } else {
                    container.library.renamePlaylist(id, edit.title)
                    scope.launch { snackbarHostState.showSnackbar("Playlist updated") }
                }
            },
            clearRecentSearches = { container.library.clearRecentSearches() },
            clearRecent = { container.library.clearRecent() },
        )
    }

    CompositionLocalProvider(
        LocalAppActions provides actions,
        LocalLibrary provides library,
        LocalPlayerState provides playerState,
        LocalYtPlaylists provides ytState.playlists,
        LocalYtLoading provides ytState.loading,
        LocalYtLikedCount provides ytState.likedCount,
        LocalSignedIn provides (settings.ytCookies != null),
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
            trackMenuPlaylistId = trackMenuPlaylistId,
            onTrackMenuDismiss = {
                trackMenuTrack = null
                trackMenuPlaylistId = null
            },
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
