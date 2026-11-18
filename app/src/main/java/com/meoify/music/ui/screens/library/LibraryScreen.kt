package com.meoify.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.LocalPlaylist
import com.meoify.music.data.model.MediaCard as MediaCardModel
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.PlaylistCard
import com.meoify.music.ui.components.PlaylistListCard
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.TrackCard
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalBottomContentInset
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.navigation.LocalSignedIn
import com.meoify.music.ui.navigation.LocalYtLoading
import com.meoify.music.ui.navigation.LocalYtPlaylists

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
    val ytLoading = LocalYtLoading.current
    val signedIn = LocalSignedIn.current
    val currentTrackId = LocalPlayerState.current.track?.id

    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.Playlists.name) }
    val filter = LibraryFilter.valueOf(filterName)
    // The layout lives in settings so it survives opening a playlist and
    // coming back to the Library tab.
    val gridView = settings.libraryGridView
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
                            imageVector = if (gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
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
                item {
                    LikedSongsRow(
                        count = library.liked.size,
                        onClick = actions.openLiked,
                    )
                }

                if (signedIn) {
                    item {
                        SectionHeader(
                            title = "YouTube Music playlists",
                            actionLabel = if (ytLoading) "Syncing…" else "Refresh",
                            onAction = { app.container.ytLibrary.refresh() },
                        )
                    }

                    when {
                        ytPlaylists.isEmpty() && ytLoading -> items(if (gridView) 2 else 3) {
                            if (gridView) {
                                PlaylistGridSkeleton()
                            } else {
                                YtPlaylistSkeleton()
                            }
                        }

                        ytPlaylists.isEmpty() -> item {
                            EmptyLibrary(
                                title = "No playlists on this account",
                                message = "Playlists you create in Meoify are saved straight to your YouTube Music account.",
                                action = "New playlist",
                                onAction = { onShowCreatePlaylist(true) },
                            )
                        }

                        gridView -> items(
                            items = ytPlaylists.chunked(2).toList(),
                            key = { row -> "yt_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            PlaylistGridRow(
                                row = row,
                                onOpen = { actions.openPlaylist(it.id) },
                            )
                        }

                        else -> items(ytPlaylists, key = { it.id }) { card ->
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
                        val cards = library.playlists.map { it.toCard() }
                        items(
                            items = cards.chunked(2).toList(),
                            key = { row -> "local_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            PlaylistGridRow(
                                row = row,
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
                        TrackCard(
                            track = track,
                            isCurrent = currentTrackId == track.id,
                            onClick = {
                                actions.playTracks(library.liked, library.liked.indexOf(track), "Liked Songs")
                            },
                            onMoreClick = { actions.openTrackMenu(track) },
                            onSwipeRight = { actions.addToQueue(track) },
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
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { card ->
            PlaylistCard(
                card = card,
                onClick = { onOpen(card) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
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
private fun LikedSongsRow(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
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
            Spacer(Modifier.width(12.dp))
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistGridSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
                Spacer(Modifier.height(6.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
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
