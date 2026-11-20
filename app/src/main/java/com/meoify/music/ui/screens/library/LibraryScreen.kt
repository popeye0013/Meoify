package com.meoify.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.LocalPlaylist
import com.meoify.music.data.model.MediaCard as MediaCardModel
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import com.meoify.music.ui.components.ArtworkImage
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.PlaylistListCard
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.TrackCard
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalBottomContentInset
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.navigation.LocalSignedIn
import com.meoify.music.ui.navigation.LocalYtLoading
import com.meoify.music.ui.navigation.LocalYtPlaylists
import com.meoify.music.ui.theme.ArtworkColorCache

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onShowCreatePlaylist: (Boolean) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
) {
    val library = LocalLibrary.current
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val app = context.applicationContext as MeoifyApplication
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val ytPlaylists = LocalYtPlaylists.current
    var playlistQuery by remember { mutableStateOf("") }
    val filteredPlaylists = remember(ytPlaylists, playlistQuery) {
        if (playlistQuery.isBlank()) ytPlaylists
        else ytPlaylists.filter { it.title.contains(playlistQuery, ignoreCase = true) }
    }
    val ytLoading = LocalYtLoading.current
    val signedIn = LocalSignedIn.current
    val currentTrackId = LocalPlayerState.current.track?.id

    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.Playlists.name) }
    val filter = LibraryFilter.valueOf(filterName)
    // The layout lives in settings so it survives opening a playlist and
    // coming back to the Library tab.
    val gridView = settings.libraryGridView
    var menuTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    var renameTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrlDraft by remember { mutableStateOf("") }
    var importingPlaylist by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(filterName) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomContentInset.current),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (filter == LibraryFilter.Playlists) {
                    IconButton(onClick = { app.container.settings.setLibraryGridView(!gridView) }) {
                        Icon(
                            imageVector = if (gridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (gridView) "Show as list" else "Show as grid",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showCreateMenu = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Create or import playlist",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(
                        expanded = showCreateMenu,
                        onDismissRequest = { showCreateMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create playlist") },
                            leadingIcon = {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                showCreateMenu = false
                                onShowCreatePlaylist(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Import playlist") },
                            leadingIcon = {
                                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                            },
                            onClick = {
                                showCreateMenu = false
                                importUrlDraft = ""
                                importStatus = null
                                showImportDialog = true
                            },
                        )
                    }
                }
            }
        }

        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LibraryFilter.entries, key = { it.name }) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filterName = f.name },
                            label = {
                                Text(
                                    when (f) {
                                        LibraryFilter.Playlists -> "Playlists"
                                        LibraryFilter.Songs -> "Songs"
                                        LibraryFilter.Albums -> "Albums"
                                    },
                                )
                            },
                        )
                    }
                }
                if (filter == LibraryFilter.Playlists) {
                    OutlinedTextField(
                        value = playlistQuery,
                        onValueChange = { playlistQuery = it },
                        placeholder = { Text("Search playlists") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        when (filter) {
            LibraryFilter.Playlists -> {
                item {
                    LikedSongsRow(
                        count = library.liked.size,
                        onClick = actions.openLiked,
                    )
                }

                if (signedIn) {
                    item {
                        SectionHeader(
                            title = "Playlists",
                            actionLabel = if (ytLoading) "Syncing…" else "Refresh",
                            onAction = { app.container.ytLibrary.refresh() },
                        )
                    }

                    when {
                        filteredPlaylists.isEmpty() && ytLoading -> items(3) {
                            YtPlaylistSkeleton()
                        }

                        filteredPlaylists.isEmpty() -> item {
                            EmptyLibrary(
                                title = "No playlists on this account",
                                message = "Playlists you create in Meoify are saved straight to your YouTube Music account.",
                                action = "New playlist",
                                onAction = { onShowCreatePlaylist(true) },
                            )
                        }

                        gridView -> items(
                            items = filteredPlaylists.chunked(2).toList(),
                            key = { row -> "yt_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            PlaylistGridRow(row = row, onOpen = { actions.openPlaylist(it.id) })
                        }

                        else -> items(filteredPlaylists, key = { it.id }) { card ->
                            PlaylistListCard(
                                card = card,
                                onClick = { actions.openPlaylist(card.id) },
                            )
                        }
                    }
                } else {
                    if (library.playlists.isEmpty()) {
                        item {
                            EmptyLibrary(
                                title = "Create your first playlist",
                                message = "Collect the songs you love and keep them in one place.",
                                action = "New playlist",
                                onAction = { onShowCreatePlaylist(true) },
                            )
                        }
                    } else if (gridView) {
                        items(
                            items = library.playlists.chunked(2).toList(),
                            key = { row -> "local_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            PlaylistGridRow(
                                row = row.map { playlist ->
                                    MediaCardModel(
                                        id = playlist.id,
                                        kind = MediaKind.PLAYLIST,
                                        title = playlist.name,
                                        subtitle = when (playlist.tracks.size) {
                                            0 -> "Playlist"
                                            1 -> "1 song"
                                            else -> "${playlist.tracks.size} songs"
                                        },
                                        thumbnailUrl = playlist.artworkUrl,
                                    )
                                },
                                onOpen = { actions.openLocalPlaylist(it.id) },
                            )
                        }
                    } else {
                        items(library.playlists, key = { it.id }) { playlist ->
                            PlaylistListCard(
                                card = playlist.toCard(),
                                onClick = { actions.openLocalPlaylist(playlist.id) },
                            )
                        }
                    }
                }
            }

            LibraryFilter.Songs -> {
                if (library.liked.isEmpty()) {
                    item {
                        EmptyLibrary(
                            title = "No liked songs yet",
                            message = "Tap the heart on any song to save it here.",
                            action = null,
                            onAction = {},
                        )
                    }
                } else {
                    items(library.liked, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            onClick = {
                                actions.playTracks(library.liked, library.liked.indexOf(track), "Liked Songs")
                            },
                            onMoreClick = { actions.openTrackMenu(track) },
                        )
                    }
                }
            }

            LibraryFilter.Albums -> {
                val albums = (library.liked + library.recent)
                    .mapNotNull { track -> track.albumId?.let { id -> track.album to id } }
                    .distinctBy { it.second }
                if (albums.isEmpty()) {
                    item {
                        EmptyLibrary(
                            title = "No albums yet",
                            message = "Albums from your liked and recent songs appear here.",
                            action = null,
                            onAction = {},
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "From your library",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    items(albums.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { (title, id) ->
                                MediaCard(
                                    card = MediaCardModel(
                                        id = id,
                                        kind = MediaKind.ALBUM,
                                        title = title ?: "Album",
                                    ),
                                    onClick = { actions.openAlbum(id) },
                                    modifier = Modifier.weight(1f),
                                    width = null,
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (filter == LibraryFilter.Playlists && library.recent.isNotEmpty()) {
            item { SectionHeader(title = "Recently played") }
            items(library.recent.take(12), key = { "recent_${it.id}" }) { track ->
                TrackCard(
                    track = track,
                    isCurrent = currentTrackId == track.id,
                    onClick = {
                        actions.playTracks(library.recent, library.recent.indexOf(track), "Recently played")
                    },
                    onMoreClick = { actions.openTrackMenu(track) },
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    menuTarget?.let { target ->
        PlaylistMenu(
            target = target,
            onDismiss = { menuTarget = null },
            onRename = {
                menuTarget = null
                renameValue = target.title
                renameTarget = target
            },
            onDelete = {
                menuTarget = null
                deleteTarget = target
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) actions.renamePlaylist(target.id, renameValue.trim())
                        renameTarget = null
                    },
                    enabled = renameValue.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete playlist?") },
            text = {
                Text(
                    if (target.fromYouTube) {
                        "“${target.title}” will be deleted from your YouTube Music account. This can't be undone."
                    } else {
                        "“${target.title}” will be removed from your library. This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePlaylist(target.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!importingPlaylist) showImportDialog = false },
            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
            title = { Text("Import playlist") },
            text = {
                Column {
                    Text(
                        "Paste a Spotify or YouTube Music playlist link. Spotify songs " +
                            "are matched to YouTube Music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importUrlDraft,
                        onValueChange = { importUrlDraft = it },
                        label = { Text("Playlist URL") },
                        placeholder = {
                            Text("open.spotify.com/playlist/… or music.youtube.com/playlist?list=…")
                        },
                        leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !importingPlaylist,
                    )
                    if (importingPlaylist) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        importStatus?.let { status ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val raw = importUrlDraft.trim()
                        if (raw.isBlank()) {
                            actions.showSnackbar("Paste a playlist link first")
                            return@TextButton
                        }
                        val spotify = raw.contains("spotify", ignoreCase = true) ||
                            raw.startsWith("spotify:", ignoreCase = true)
                        importingPlaylist = true
                        importStatus = if (spotify) {
                            "Reading the Spotify playlist…"
                        } else {
                            "Loading the playlist…"
                        }
                        scope.launch {
                            val result = runCatching {
                                app.container.repository.importPlaylist(raw) { done, total ->
                                    if (total > 0) {
                                        scope.launch {
                                            importStatus = "Matching songs… $done of $total"
                                        }
                                    }
                                }
                            }
                            importingPlaylist = false
                            importStatus = null
                            result.fold(
                                onSuccess = { playlist ->
                                    showImportDialog = false
                                    val count = playlist.tracks.size
                                    val imported = "Imported “${playlist.title}” " +
                                        "($count track${if (count == 1) "" else "s"})"
                                    if (signedIn) {
                                        // Creating playlists on YouTube Music is
                                        // what the Library's Create action does,
                                        // so an import stays visible in the list.
                                        val id = app.container.ytLibrary
                                            .createPlaylist(playlist.title, playlist.tracks)
                                        if (id != null) {
                                            app.container.ytLibrary.refresh()
                                            actions.showSnackbar("$imported on YouTube Music")
                                        } else {
                                            app.container.library
                                                .savePlaylist(playlist.title, playlist.tracks)
                                            actions.showSnackbar("$imported on this device")
                                        }
                                    } else {
                                        app.container.library
                                            .savePlaylist(playlist.title, playlist.tracks)
                                        actions.showSnackbar(imported)
                                    }
                                },
                                onFailure = { error ->
                                    actions.showSnackbar(
                                        error.message?.takeIf { it.isNotBlank() }
                                            ?: "Couldn't import that playlist",
                                    )
                                },
                            )
                        }
                    },
                    enabled = !importingPlaylist,
                ) { Text(if (importingPlaylist) "Importing…" else "Import") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportDialog = false },
                    enabled = !importingPlaylist,
                ) { Text("Cancel") }
            },
        )
    }
}

private data class PlaylistTarget(
    val id: String,
    val title: String,
    val songCount: Int?,
    val fromYouTube: Boolean,
)

enum class LibraryFilter { Playlists, Songs, Albums }

@Composable
private fun PlaylistGridRow(
    row: List<MediaCardModel>,
    onOpen: (MediaCardModel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { card ->
            MediaCard(
                card = card,
                onClick = { onOpen(card) },
                modifier = Modifier.weight(1f),
                width = null,
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LikedSongsRow(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF7A122A), Color(0xFFFF7A9A))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$count ${if (count == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (!playlist.artworkUrl.isNullOrBlank()) {
                    ArtworkImage(
                        url = playlist.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val pair = ArtworkColorCache.gradientPair(playlist.id)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.tracks.size} ${if (playlist.tracks.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YtPlaylistRow(
    card: MediaCardModel,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (!card.thumbnailUrl.isNullOrBlank()) {
                    ArtworkImage(
                        url = card.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val pair = ArtworkColorCache.gradientPair(card.id)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.subtitle ?: "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Playlist options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YtPlaylistSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.height(6.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenu(
    target: PlaylistTarget,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    target.songCount != null ->
                        "${target.songCount} ${if (target.songCount == 1) "song" else "songs"}"
                    target.fromYouTube -> "Playlist on YouTube Music"
                    else -> "Playlist"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRename)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(14.dp))
                Text("Rename playlist", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Delete playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** A local playlist as the card model the Library renders. */
private fun LocalPlaylist.toCard(): MediaCardModel = MediaCardModel(
    id = id,
    kind = MediaKind.PLAYLIST,
    title = name,
    subtitle = when (tracks.size) {
        0 -> "Playlist"
        1 -> "1 song"
        else -> "${tracks.size} songs"
    },
    thumbnailUrl = artworkUrl,
)

@Composable
private fun EmptyLibrary(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
