package com.meoify.music.ui.screens.detail

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.UiState
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.components.ArtistCard
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.Destination
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.ui.viewmodel.DetailViewModel
import com.meoify.music.ui.viewmodel.DetailViewModelFactory

@Composable
fun DetailScreen(destination: Destination, onBack: () -> Unit) {
    val appContainer = (androidx.compose.ui.platform.LocalContext.current.applicationContext as MeoifyApplication).container
    val library = LocalLibrary.current
    val actions = LocalAppActions.current
    val playerState = LocalPlayerState.current

    val page: DetailPage? = when (destination) {
        is Destination.Liked -> DetailPage(
            id = "liked",
            kind = MediaKind.PLAYLIST,
            title = "Liked Songs",
            subtitle = "${library.liked.size} songs",
            tracks = library.liked,
        )

        is Destination.LocalPlaylist -> {
            val pl = library.playlists.firstOrNull { it.id == destination.id }
            DetailPage(
                id = destination.id,
                kind = MediaKind.PLAYLIST,
                title = pl?.name ?: "Playlist",
                subtitle = "${pl?.tracks?.size ?: 0} songs",
                thumbnailUrl = pl?.artworkUrl,
                tracks = pl?.tracks ?: emptyList(),
            )
        }

        else -> null
    }

    if (page != null) {
        DetailContent(
            page = page,
            onBack = onBack,
            actions = actions,
            playerState = playerState,
            destination = destination,
        )
        return
    }

    val viewModel: DetailViewModel = viewModel(factory = DetailViewModelFactory(appContainer.repository, destination))
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = viewModel::load) { Text("Try again") }
            }
        }

        is UiState.Success -> {
            DetailContent(
                page = s.value,
                onBack = onBack,
                actions = actions,
                playerState = playerState,
                destination = destination,
            )
        }
    }
}

@Composable
private fun DetailContent(
    page: DetailPage,
    onBack: () -> Unit,
    actions: com.meoify.music.ui.navigation.AppActions,
    playerState: PlayerUiState,
    destination: Destination,
) {
    var accent by remember(page.id) { mutableStateOf(ArtworkColorCache.fallbackColor(page.title)) }
    LaunchedEffect(page.id) { accent = ArtworkColorCache.extract(page.thumbnailUrl, page.title) }
    val pair = ArtworkColorCache.gradientPair(page.id)
    val headerGradient = if (page.thumbnailUrl.isNullOrBlank()) {
        Brush.linearGradient(listOf(pair.first, pair.second))
    } else {
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.95f), ArtworkColorCache.darken(accent, 0.35f)))
    }

    val currentTrackId = playerState.track?.id
    val isCurrentPage = currentTrackId != null && page.tracks.any { it.id == currentTrackId }
    val isPlayingPage = isCurrentPage && playerState.isPlaying

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember(page.title) { mutableStateOf(page.title) }
    val localPlaylistId = (destination as? Destination.LocalPlaylist)?.id

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp)
                    .background(headerGradient),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More options", tint = Color.White)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Shuffle play") },
                                    leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        actions.playShuffled(page.tracks, page.title)
                                    },
                                )
                                if (localPlaylistId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Rename playlist") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            renameValue = page.title
                                            showRename = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete playlist") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            showDelete = true
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (page.thumbnailUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(listOf(pair.first, pair.second))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = when {
                                            page.id == "liked" -> Icons.Filled.Favorite
                                            page.kind == MediaKind.ARTIST -> Icons.Filled.Person
                                            page.kind == MediaKind.ALBUM -> Icons.Filled.Album
                                            else -> Icons.Filled.LibraryMusic
                                        },
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.92f),
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = page.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (page.kind) {
                                    MediaKind.ALBUM -> "ALBUM"
                                    MediaKind.PLAYLIST -> "PLAYLIST"
                                    MediaKind.ARTIST -> "ARTIST"
                                    else -> page.kind.name
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = page.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!page.subtitle.isNullOrBlank()) {
                                Text(
                                    text = page.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (isCurrentPage) {
                            actions.togglePlayPause()
                        } else if (page.tracks.isNotEmpty()) {
                            actions.playTracks(page.tracks, 0, page.title)
                        }
                    },
                    enabled = page.tracks.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isPlayingPage) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlayingPage) "Pause" else "Play", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { actions.playShuffled(page.tracks, page.title) },
                    enabled = page.tracks.isNotEmpty(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
        }

        if (!page.description.isNullOrBlank()) {
            item {
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        if (page.tracks.isEmpty() && page.sections.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Nothing here yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Songs you save will show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (page.tracks.isNotEmpty()) {
            item { SectionHeader(title = "Songs") }
            items(page.tracks, key = { it.id }) { track ->
                val idx = page.tracks.indexOf(track)
                TrackRow(
                    track = track,
                    index = if (page.kind == MediaKind.ALBUM) idx else null,
                    isCurrent = currentTrackId == track.id,
                    onClick = { actions.playTracks(page.tracks, idx, page.title) },
                    onMoreClick = { actions.openTrackMenu(track) },
                )
            }
        }

        page.sections.forEach { section ->
            item { SectionHeader(title = section.title) }
            if (section.items.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.items, key = { it.id }) { card ->
                            when (card.kind) {
                                MediaKind.ARTIST -> ArtistCard(card = card, onClick = { actions.openArtist(card.id) })
                                else -> MediaCard(
                                    card = card,
                                    onClick = {
                                        when (card.kind) {
                                            MediaKind.ALBUM -> actions.openAlbum(card.id)
                                            MediaKind.PLAYLIST, MediaKind.MIX -> actions.openPlaylist(card.id)
                                            else -> actions.search(card.title)
                                        }
                                    },
                                    width = 150.dp,
                                )
                            }
                        }
                    }
                }
            }
            if (section.tracks.isNotEmpty()) {
                items(section.tracks, key = { it.id }) { track ->
                    val idx = section.tracks.indexOf(track)
                    TrackRow(
                        track = track,
                        isCurrent = currentTrackId == track.id,
                        onClick = { actions.playTracks(section.tracks, idx, section.title) },
                        onMoreClick = { actions.openTrackMenu(track) },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showRename && localPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showRename = false },
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
                        if (renameValue.isNotBlank()) actions.renamePlaylist(localPlaylistId, renameValue)
                        showRename = false
                    },
                    enabled = renameValue.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    if (showDelete && localPlaylistId != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("“${page.title}” will be removed from your library. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePlaylist(localPlaylistId)
                    showDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}
