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
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
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
import com.meoify.music.data.model.Track
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
import com.meoify.music.util.formatDuration

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

    // Key the ViewModel by destination: reusing one instance across different playlists/albums
    // kept showing the previously opened page.
    val viewModel: DetailViewModel = viewModel(
        key = destination.key,
        factory = DetailViewModelFactory(appContainer.repository, destination),
    )
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

    val currentTrackId = playerState.track?.id
    val isCurrentPage = currentTrackId != null && page.tracks.any { it.id == currentTrackId }
    val isPlayingPage = isCurrentPage && playerState.isPlaying

    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember(page.title) { mutableStateOf(page.title) }
    val localPlaylistId = (destination as? Destination.LocalPlaylist)?.id
    val canSave = page.tracks.isNotEmpty() &&
        localPlaylistId == null &&
        page.id != "liked" &&
        page.kind != MediaKind.ARTIST

    val kindLabel = when (page.kind) {
        MediaKind.ALBUM -> "Album"
        MediaKind.PLAYLIST -> "Playlist"
        MediaKind.ARTIST -> "Artist"
        else -> page.kind.name.lowercase().replaceFirstChar { it.uppercase() }
    }
    val meta = listOfNotNull(
        page.subtitle?.takeIf { it.isNotBlank() },
        if (page.tracks.isNotEmpty()) "${page.tracks.size} tracks" else null,
    ).joinToString(" • ")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 6.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = kindLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!page.subtitle.isNullOrBlank()) {
                            Text(
                                text = page.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(232.dp)
                            .shadow(20.dp, RoundedCornerShape(30.dp), clip = false)
                            .clip(RoundedCornerShape(30.dp))
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
                                    modifier = Modifier.size(86.dp),
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
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                )
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
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
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (canSave) {
            item {
                FilledTonalButton(
                    onClick = {
                        actions.savePlaylist(page.title, page.tracks)
                        actions.showSnackbar("Saved “${page.title}” to your library")
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(50.dp),
                ) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to library", fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
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
            item { SectionHeader(title = "Tracks") }
            items(page.tracks, key = { it.id }) { track ->
                val idx = page.tracks.indexOf(track)
                TrackCard(
                    track = track,
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
                    TrackCard(
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

@Composable
private fun TrackCard(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(46.dp)) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    track.artist.takeIf { it.isNotBlank() },
                    track.album?.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (track.durationMs > 0) {
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options for ${track.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
