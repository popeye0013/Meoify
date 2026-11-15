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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.LocalPlaylist
import com.meoify.music.data.model.MediaCard as MediaCardModel
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.ui.viewmodel.YtLibraryViewModel
import com.meoify.music.ui.viewmodel.YtLibraryViewModelFactory

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
    val ytViewModel: YtLibraryViewModel = viewModel(
        factory = YtLibraryViewModelFactory(app.container.repository),
    )
    val ytPlaylists by ytViewModel.playlists.collectAsStateWithLifecycle()
    val ytLoading by ytViewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(settings.ytCookies) {
        if (settings.ytCookies != null) ytViewModel.load(force = true) else ytViewModel.clear()
    }

    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.Playlists.name) }
    val filter = LibraryFilter.valueOf(filterName)
    var gridView by rememberSaveable { mutableStateOf(false) }
    var menuPlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }
    var renamePlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deletePlaylist by remember { mutableStateOf<LocalPlaylist?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(filterName) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                    IconButton(onClick = { gridView = !gridView }) {
                        Icon(
                            imageVector = if (gridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (gridView) "Show as list" else "Show as grid",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onShowCreatePlaylist(true) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Create playlist",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
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
                Spacer(Modifier.height(8.dp))
            }
        }

        when (filter) {
            LibraryFilter.Playlists -> {
                if (settings.ytCookies != null) {
                    item {
                        SectionHeader(
                            title = "Your YouTube Music",
                            actionLabel = if (ytLoading) "Loading…" else null,
                        )
                    }
                    when {
                        ytPlaylists.isEmpty() && ytLoading -> item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(4) {
                                    Column(modifier = Modifier.width(150.dp)) {
                                        ShimmerBox(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(16.dp)),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        ShimmerBox(
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .height(12.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                    }
                                }
                            }
                        }

                        ytPlaylists.isEmpty() -> item {
                            Text(
                                text = "No saved playlists found on this account yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }

                        gridView -> items(
                            items = ytPlaylists.chunked(2).toList(),
                            key = { row -> "yt_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            PlaylistGridRow(row = row, onOpen = { actions.openPlaylist(it.id) })
                        }

                        else -> item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(ytPlaylists, key = { it.id }) { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { actions.openPlaylist(card.id) },
                                        width = 150.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    LikedSongsRow(
                        count = library.liked.size,
                        onClick = actions.openLiked,
                    )
                }

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
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { actions.openLocalPlaylist(playlist.id) },
                            onMenu = { menuPlaylist = playlist },
                        )
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
                TrackRow(
                    track = track,
                    onClick = {
                        actions.playTracks(library.recent, library.recent.indexOf(track), "Recently played")
                    },
                    onMoreClick = { actions.openTrackMenu(track) },
                )
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }

    menuPlaylist?.let { playlist ->
        PlaylistMenu(
            playlist = playlist,
            onDismiss = { menuPlaylist = null },
            onRename = {
                menuPlaylist = null
                renameValue = playlist.name
                renamePlaylist = playlist
            },
            onDelete = {
                menuPlaylist = null
                deletePlaylist = playlist
            },
        )
    }

    renamePlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { renamePlaylist = null },
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
                        if (renameValue.isNotBlank()) actions.renamePlaylist(playlist.id, renameValue)
                        renamePlaylist = null
                    },
                    enabled = renameValue.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renamePlaylist = null }) { Text("Cancel") } },
        )
    }

    deletePlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletePlaylist = null },
            title = { Text("Delete playlist?") },
            text = { Text("“${playlist.name}” will be removed from your library. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePlaylist(playlist.id)
                    deletePlaylist = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("Cancel") } },
        )
    }
}

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
                    AsyncImage(
                        model = playlist.artworkUrl,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMenu(
    playlist: LocalPlaylist,
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
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.tracks.size} songs",
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
