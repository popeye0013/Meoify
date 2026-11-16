package com.meoify.music.ui.screens.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.DetailPage
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.UiState
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.components.ArtistCard
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.ArtworkImage
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.TrackCard
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
    val fallbackAccent = remember(page.id) { ArtworkColorCache.fallbackColor(page.id) }
    var accent by remember(page.id) { mutableStateOf(fallbackAccent) }
    LaunchedEffect(page.thumbnailUrl, page.id) {
        accent = ArtworkColorCache.extract(page.thumbnailUrl, page.id)
    }
    // Artwork colour arrives after the cover loads; ease into it instead of
    // snapping, so the header and status bar shift together.
    val headerAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(450),
        label = "headerAccent",
    )
    val background = MaterialTheme.colorScheme.background
    val statusBarPx = with(LocalDensity.current) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }

    val currentTrackId = playerState.track?.id
    val isCurrentPage = currentTrackId != null && page.tracks.any { it.id == currentTrackId }
    val isPlayingPage = isCurrentPage && playerState.isPlaying

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

    val listState = rememberLazyListState()
    val headerScroll = if (listState.firstVisibleItemIndex == 0) {
        listState.firstVisibleItemScrollOffset.toFloat()
    } else {
        0f
    }
    var headerHeightPx by remember(page.id) { mutableStateOf(0f) }
    // Same colour as the first gradient stop, so the status bar and the header
    // read as one surface. It melts back into the background as the hero
    // scrolls away, so the strip never floats above a flat list.
    val tintBlend = if (headerHeightPx > 0f) {
        (headerScroll / headerHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val statusBarTint = lerp(lerp(background, headerAccent, 0.30f), background, tintBlend)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .drawWithContent {
                drawContent()
                if (statusBarPx > 0f) {
                    drawRect(
                        color = statusBarTint,
                        topLeft = Offset.Zero,
                        size = Size(size.width, statusBarPx),
                    )
                }
            },
    ) {
        item {
            DetailHeader(
                page = page,
                kindLabel = kindLabel,
                accent = headerAccent,
                scrollOffset = headerScroll,
                isPlayingPage = isPlayingPage,
                canSave = canSave,
                localPlaylistId = localPlaylistId,
                onBack = onBack,
                onPlay = {
                    if (isCurrentPage) {
                        actions.togglePlayPause()
                    } else if (page.tracks.isNotEmpty()) {
                        actions.playTracks(page.tracks, 0, page.title)
                    }
                },
                onShuffle = { actions.playShuffled(page.tracks, page.title) },
                onSave = {
                    actions.savePlaylist(page.title, page.tracks)
                    actions.showSnackbar("Saved “${page.title}” to your library")
                },
                onRename = {
                    renameValue = page.title
                    showRename = true
                },
                onDelete = { showDelete = true },
                onHeightChange = { headerHeightPx = it.height.toFloat() },
            )
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

/**
 * The playlist/album/artist hero.
 *
 * The header background starts directly below the status bar; [DetailContent]
 * paints the status bar with the same colour, so the two read as one surface.
 * Artwork, a tonal kind pill, the title, stat pills and the primary actions
 * follow in that order, tinted by the cover's dominant colour.
 */
@Composable
private fun DetailHeader(
    page: DetailPage,
    kindLabel: String,
    accent: Color,
    scrollOffset: Float,
    isPlayingPage: Boolean,
    canSave: Boolean,
    localPlaylistId: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSave: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onHeightChange: (androidx.compose.ui.unit.IntSize) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    var menuOpen by remember { mutableStateOf(false) }
    val totalMs = page.tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
    val coverScale = 1f + (scrollOffset / 2400f).coerceIn(0f, 0.07f)
    val coverShape = if (page.roundArt || page.kind == MediaKind.ARTIST) {
        CircleShape
    } else {
        RoundedCornerShape(34.dp)
    }
    val kindIcon = when (page.kind) {
        MediaKind.ALBUM -> Icons.Filled.Album
        MediaKind.ARTIST -> Icons.Filled.Person
        else -> Icons.Filled.LibraryMusic
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged(onHeightChange)
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    0f to accent.copy(alpha = 0.30f),
                    0.55f to background,
                    1f to background,
                ),
            )
            .padding(bottom = 2.dp),
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
            Spacer(Modifier.weight(1f))
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
                        enabled = page.tracks.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            onShuffle()
                        },
                    )
                    if (localPlaylistId != null) {
                        DropdownMenuItem(
                            text = { Text("Rename playlist") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(268.dp)
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                    }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = coverScale
                        scaleY = coverScale
                    }
                    .size(212.dp)
                    .shadow(
                        elevation = 26.dp,
                        shape = coverShape,
                        clip = false,
                        ambientColor = accent,
                        spotColor = accent,
                    )
                    .clip(coverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), coverShape),
                contentAlignment = Alignment.Center,
            ) {
                if (page.thumbnailUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(accent, MaterialTheme.colorScheme.surfaceContainerHigh),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = kindIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(80.dp),
                        )
                    }
                } else {
                    ArtworkImage(
                        url = page.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.22f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = kindIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = kindLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        if (!page.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            )
        }
        if (page.tracks.isNotEmpty() || totalMs > 0L) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page.tracks.isNotEmpty()) {
                    MetaPill(
                        icon = Icons.Filled.LibraryMusic,
                        label = "${page.tracks.size} ${if (page.tracks.size == 1) "song" else "songs"}",
                    )
                }
                durationLabel(totalMs)?.let { MetaPill(icon = Icons.Filled.Schedule, label = it) }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPlay,
                enabled = page.tracks.isNotEmpty(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
            ) {
                Icon(
                    imageVector = if (isPlayingPage) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlayingPage) "Pause" else "Play", fontWeight = FontWeight.Bold)
            }
            HeaderAction(
                onClick = onShuffle,
                icon = Icons.Filled.Shuffle,
                description = "Shuffle",
                enabled = page.tracks.isNotEmpty(),
            )
            if (canSave) {
                HeaderAction(
                    onClick = onSave,
                    icon = Icons.Filled.LibraryAdd,
                    description = "Save to library",
                )
            }
        }
    }
}

@Composable
private fun HeaderAction(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(58.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "1 hr 12 min" for a header stat, or null when no track reports a duration. */
private fun durationLabel(ms: Long): String? {
    if (ms <= 0L) return null
    val totalMinutes = (ms + 30_000L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        else -> "${minutes.coerceAtLeast(1)} min"
    }
}
