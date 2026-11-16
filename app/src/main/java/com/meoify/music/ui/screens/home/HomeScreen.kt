package com.meoify.music.ui.screens.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.MediaCard as MediaCardModel
import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.UiState
import com.meoify.music.ui.components.ArtistCard
import com.meoify.music.ui.components.MediaCard
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
import com.meoify.music.ui.theme.ArtworkColorCache
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
                                text = "Couldn't reach YouTube Music",
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
                    val quickPicks = feed.quickPicks.take(6)
                    val featured = feed.communityPlaylists.firstOrNull()
                    val madeForYou = feed.communityPlaylists.let { playlists ->
                        // The first playlist is already the featured card above.
                        if (featured != null && playlists.size > 1) playlists.drop(1) else playlists
                    }
                    val trendingTracks = if (feed.quickPicks.size > quickPicks.size) {
                        feed.quickPicks.drop(quickPicks.size).take(10)
                    } else {
                        feed.quickPicks.take(10)
                    }

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
                                onMore = { track -> actions.openTrackMenu(track) },
                            )
                        }
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

                    if (featured != null) {
                        item {
                            FeaturedCard(
                                card = featured,
                                onClick = { openCard(featured, actions) },
                            )
                        }
                    }

                    if (madeForYou.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Made for you",
                                subtitle = "Playlists picked for your taste",
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(madeForYou, key = { "mfy_" + it.id }) { card ->
                                    MadeForYouCard(
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
                                        onMoreClick = { actions.openTrackMenu(track) },
                                        modifier = Modifier.weight(1f),
                                        rank = index + 1,
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    if (feed.newReleases.isNotEmpty()) {
                        item { SectionHeader(title = "New releases") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.newReleases, key = { it.id }) { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { actions.openAlbum(card.id) },
                                        width = 150.dp,
                                    )
                                }
                            }
                        }
                    }

                    if (feed.topArtists.isNotEmpty()) {
                        item { SectionHeader(title = "Top artists") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.topArtists, key = { it.id }) { card ->
                                    ArtistCard(card = card, onClick = { actions.openArtist(card.id) })
                                }
                            }
                        }
                    }

                    if (feed.chartPlaylists.isNotEmpty()) {
                        item { SectionHeader(title = "Charts") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.chartPlaylists, key = { it.id }) { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { actions.openPlaylist(card.id) },
                                        width = 150.dp,
                                    )
                                }
                            }
                        }
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
    val pair = ArtworkColorCache.gradientPair(card.id)
    val eyebrow = when (card.kind) {
        MediaKind.PLAYLIST -> "FEATURED PLAYLIST"
        MediaKind.MIX -> "FEATURED MIX"
        else -> "FEATURED"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        pair.first.copy(alpha = 0.32f),
                        pair.second.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                ),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                ArtworkImage(
                    url = card.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.subtitle ?: "Picked from your listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
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
private fun MadeForYouCard(card: MediaCardModel, onClick: () -> Unit) {
    val pair = ArtworkColorCache.gradientPair(card.id)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .width(240.dp)
            .height(136.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            listOf(
                                Color.Transparent,
                                pair.first.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 58.dp, bottom = 12.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.subtitle ?: "Made for you",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
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
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtworkImage(
                                url = cover,
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
        // Featured card.
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(26.dp)),
        )
        // Made for you rail.
        ShimmerBox(
            Modifier
                .fillMaxWidth(0.42f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        ShimmerBox(
            Modifier
                .width(250.dp)
                .height(136.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
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
