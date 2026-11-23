package com.meoify.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.meoify.music.data.remote.Artwork
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.MediaCard as MediaCardModel
import com.meoify.music.data.model.HomeShelf
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.UiState
import com.meoify.music.ui.components.ArtistCard
import com.meoify.music.ui.components.MediaCard
import com.meoify.music.ui.components.MixCard
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.ProfileAvatar
import com.meoify.music.ui.components.ShimmerBox
import com.meoify.music.ui.components.ArtworkImage
import com.meoify.music.ui.components.SongCard
import com.meoify.music.ui.components.rememberTrackCover
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalBottomContentInset
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.viewmodel.HomeViewModel
import com.meoify.music.ui.viewmodel.ProfileViewModel
import com.meoify.music.ui.viewmodel.ProfileViewModelFactory
import com.meoify.music.util.greetingForHour
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val appContainer = (LocalContext.current.applicationContext as MeoifyApplication).container
    val viewModel: HomeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(appContainer.repository) as T
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val library = LocalLibrary.current
    val actions = LocalAppActions.current
    val playerState = LocalPlayerState.current
    val currentTrackId = playerState.track?.id
    val settings by appContainer.settings.settings.collectAsStateWithLifecycle()
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(appContainer.repository),
    )
    val account by profileViewModel.account.collectAsStateWithLifecycle()

    LaunchedEffect(settings.ytCookies) {
        if (settings.ytCookies != null) profileViewModel.refresh(force = true) else profileViewModel.clear()
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Keep the last row scrollable above the floating mini player and
            // tab bar; the content itself draws behind them.
            contentPadding = PaddingValues(bottom = LocalBottomContentInset.current),
        ) {
            item {
                HomeHeader(
                    account = account,
                    signedIn = settings.ytCookies != null,
                    localName = settings.localProfileName,
                    localAvatar = settings.localAvatarPath?.let { File(it) },
                    onProfile = actions.openProfile,
                    onSearch = { query -> actions.search(query) },
                )
            }

            when (val s = state) {
                UiState.Loading -> {
                    item { HomeLoading() }
                }

                is UiState.Error -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Outlined.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "You're Offline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = s.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.load(true) }) { Text("Try again") }
                        }
                    }
                }

                is UiState.Success -> {
                    val feed = s.value
                    val quickTracks = (library.recent + feed.quickPicks).distinctBy { it.id }.take(6)
                    val madeForYou = feed.communityPlaylists.filterNot { it.title.contains("liked", ignoreCase = true) }
                    val quickPicks = feed.quickPicks.take(6)
                    val trendingTracks = if (feed.quickPicks.size > quickPicks.size) {
                        feed.quickPicks.drop(quickPicks.size).take(10)
                    } else {
                        feed.quickPicks.take(10)
                    }

                    if (quickTracks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Jump back in",
                                actionLabel = "Play all",
                                onAction = {
                                    actions.playTracks(quickTracks, 0, "Jump back in")
                                },
                            )
                        }
                        item {
                            QuickAccessGrid(
                                tracks = quickTracks,
                                currentTrackId = currentTrackId,
                                onPlay = { track ->
                                    actions.playTracks(quickTracks, quickTracks.indexOf(track), "Jump back in")
                                },
                            )
                        }
                    }

                    val shelves = feed.shelves.filterNot { it.isEmpty }
                    if (shelves.isNotEmpty()) {
                        items(shelves, key = { "shelf_${it.title}" }) { shelf ->
                            HomeShelfSection(
                                shelf = shelf,
                                currentTrackId = currentTrackId,
                                actions = actions,
                            )
                        }
                    } else {
                    if (quickPicks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Quick picks",
                                subtitle = "Trending songs worth playing now",
                                actionLabel = "See all",
                                onAction = { actions.search("trending music") },
                            )
                        }
                        item {
                            QuickPicksRail(
                                tracks = quickPicks,
                                currentTrackId = currentTrackId,
                                onPlay = { index ->
                                    actions.playTracks(quickPicks, index, "Quick picks")
                                },
                                onMore = { track -> actions.openTrackMenu(track, null) },
                            )
                        }
                    }

                    if (madeForYou.isNotEmpty()) {
                        item { SectionHeader(title = "Made for you") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(madeForYou, key = { it.id }) { card ->
                                    MixCard(
                                        card = card,
                                        onClick = { openCard(card, actions) },
                                    )
                                }
                            }
                        }
                    }

                    if (trendingTracks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Trending now",
                                subtitle = "What everyone is playing",
                                actionLabel = "See all",
                                onAction = { actions.search("trending music") },
                            )
                        }
                        items(
                            items = trendingTracks.chunked(2),
                            key = { row -> "trending_" + row.joinToString("|") { it.id } },
                        ) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { track ->
                                    val index = trendingTracks.indexOf(track)
                                    SongCard(
                                        track = track,
                                        isCurrent = currentTrackId == track.id,
                                        onClick = {
                                            actions.playTracks(trendingTracks, index, "Trending now")
                                        },
                                        onMoreClick = { actions.openTrackMenu(track, null) },
                                        modifier = Modifier.weight(1f),
                                        rank = index + 1,
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    }

                    if (feed.newReleases.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "New releases",
                                subtitle = "Fresh albums from the catalogue",
                            )
                        }
                        item {
                            CatalogCardRail(
                                cards = feed.newReleases,
                                badge = "NEW",
                                onOpen = { card -> actions.openAlbum(card.id) },
                            )
                        }
                    }

                    val personal = feed.personalShelves.filterNot { it.isEmpty }
                    items(personal, key = { "personal_${it.title}" }) { shelf ->
                        HomeShelfSection(
                            shelf = shelf,
                            currentTrackId = currentTrackId,
                            actions = actions,
                        )
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun openCard(card: MediaCardModel, actions: com.meoify.music.ui.navigation.AppActions) {
    when (card.kind) {
        MediaKind.ALBUM -> actions.openAlbum(card.id)
        MediaKind.PLAYLIST, MediaKind.MIX -> actions.openPlaylist(card.id)
        MediaKind.ARTIST -> actions.openArtist(card.id)
        else -> actions.search(card.title)
    }
}

/** One titled Home row. Songs and cards can share the same heading. */
@Composable
private fun HomeShelfSection(
    shelf: HomeShelf,
    currentTrackId: String?,
    actions: com.meoify.music.ui.navigation.AppActions,
) {
    val useCatalogCards = shelf.items.isNotEmpty() && (
        shelf.title.equals("Made for you", ignoreCase = true) ||
            shelf.title.equals("New releases", ignoreCase = true) ||
            shelf.title.contains("biggest hits", ignoreCase = true) ||
            shelf.title.contains("trending community playlists", ignoreCase = true) ||
            shelf.title.contains("from your library", ignoreCase = true) ||
            shelf.title.contains("fresh finds", ignoreCase = true) ||
            shelf.title.contains("band baaja baraat", ignoreCase = true)
        )
    if (useCatalogCards) {
        SectionHeader(title = shelf.title, subtitle = shelf.subtitle)
        CatalogCardRail(cards = shelf.items) { card -> openCard(card, actions) }
        return
    }

    if (shelf.tracks.isNotEmpty()) {
        SectionHeader(title = shelf.title, subtitle = shelf.subtitle)
        QuickPicksRail(
            tracks = shelf.tracks,
            currentTrackId = currentTrackId,
            onPlay = { index -> actions.playTracks(shelf.tracks, index, shelf.title) },
            onMore = { track -> actions.openTrackMenu(track, null) },
        )
    }
    if (shelf.items.isNotEmpty()) {
        if (shelf.tracks.isEmpty()) {
            SectionHeader(title = shelf.title, subtitle = shelf.subtitle)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.items, key = { it.id }) { card ->
                if (card.kind == MediaKind.ARTIST) {
                    ArtistCard(card = card, onClick = { actions.openArtist(card.id) })
                } else {
                    MediaCard(
                        card = card,
                        onClick = { openCard(card, actions) },
                        width = 150.dp,
                    )
                }
            }
        }
    }
}


/**
 * SongCard-style rail for album/playlist cards, matching "More of what you
 * like": the same 152dp card, square artwork and title/subtitle below.
 */
@Composable
private fun CatalogCardRail(
    cards: List<MediaCardModel>,
    badge: String? = null,
    onOpen: (MediaCardModel) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            Surface(
                onClick = { onOpen(card) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(152.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
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
                        }
                        if (badge != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        Surface(
                            onClick = { onOpen(card) },
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Play ${card.title}",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    Text(
                        text = card.subtitle.orEmpty().ifBlank { "Playlist" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    account: com.meoify.music.data.model.AccountInfo?,
    signedIn: Boolean,
    localName: String?,
    localAvatar: Any?,
    onProfile: () -> Unit,
    onSearch: (String) -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Meoify",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${greetingForHour(hour)} · what do you want to hear today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .clickable(onClick = onProfile),
            ) {
                ProfileAvatar(
                    name = account?.displayName
                        ?: localName
                        ?: if (signedIn) "YouTube Music" else null,
                    url = account?.avatarUrl ?: localAvatar,
                    initials = account?.initials
                        ?: localName?.trim()?.take(1)?.uppercase(),
                    size = 38.dp,
                    ring = signedIn,
                )
            }
        }

        Surface(
            onClick = { onSearch("") },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Search songs, albums, artists",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val chips = listOf("All", "Pop", "Chill", "Workout", "Focus", "Party")
            items(chips) { chip ->
                FilterChip(
                    selected = chip == "All",
                    onClick = { onSearch(if (chip == "All") "" else "$chip music") },
                    label = { Text(chip) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(card: MediaCardModel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ArtworkImage(
            url = card.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.86f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PICKED FOR YOU",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.subtitle ?: "Picked from your listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
private fun QuickPicksRail(
    tracks: List<Track>,
    currentTrackId: String?,
    onPlay: (Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            SongCard(
                track = track,
                isCurrent = track.id == currentTrackId,
                onClick = { onPlay(index) },
                onMoreClick = { onMore(track) },
                modifier = Modifier.width(152.dp),
            )
        }
    }
}

@Composable
private fun QuickAccessGrid(
    tracks: List<Track>,
    currentTrackId: String?,
    onPlay: (Track) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tracks.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { track ->
                    val cover = rememberTrackCover(track)
                    val current = track.id == currentTrackId
                    Surface(
                        onClick = { onPlay(track) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtworkImage(
                                url = cover,
                                fallbackUrl = Artwork.songArt(track?.thumbnailUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp, end = 12.dp),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeLoading() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Featured playlist.
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(28.dp)),
        )
        // Jump back in tiles.
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.38f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    ShimmerBox(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }
            }
        }
        // Quick picks rail.
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.42f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                ShimmerBox(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
        }
        // Made for you rail.
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.42f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                ShimmerBox(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
        // Trending grid.
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.42f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerBox(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(14.dp))
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
                    }
                }
            }
        }
    }
}
