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
import androidx.compose.material3.IconButton
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
import coil3.compose.AsyncImage
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
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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

                    val featured = feed.communityPlaylists.firstOrNull()
                    if (featured != null) {
                        item {
                            FeaturedCard(
                                card = featured,
                                onClick = { openCard(featured, actions) },
                            )
                        }
                    }

                    if (quickTracks.isNotEmpty()) {
                        item { SectionHeader(title = "Jump back in") }
                        item {
                            QuickAccessGrid(
                                tracks = quickTracks,
                                onPlay = { track ->
                                    actions.playTracks(quickTracks, quickTracks.indexOf(track), "Jump back in")
                                },
                            )
                        }
                    }

                    if (feed.communityPlaylists.isNotEmpty()) {
                        item { SectionHeader(title = "Made for you") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(feed.communityPlaylists, key = { it.id }) { card ->
                                    MediaCard(
                                        card = card,
                                        onClick = { openCard(card, actions) },
                                        width = 150.dp,
                                    )
                                }
                            }
                        }
                    }

                    if (feed.quickPicks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Trending now",
                                actionLabel = "See all",
                                onAction = { actions.search("trending music") },
                            )
                        }
                        items(feed.quickPicks.take(10), key = { it.id }) { track ->
                            val idx = feed.quickPicks.indexOf(track)
                            TrackRow(
                                track = track,
                                onClick = { actions.playTracks(feed.quickPicks, idx, "Trending now") },
                                onMoreClick = { actions.openTrackMenu(track) },
                            )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = card.thumbnailUrl,
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
private fun QuickAccessGrid(tracks: List<Track>, onPlay: (Track) -> Unit) {
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
                    Surface(
                        onClick = { onPlay(track) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .weight(1f)
                            .height(62.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(62.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp),
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
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(Modifier.fillMaxWidth().height(190.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.weight(1f).height(62.dp))
            ShimmerBox(Modifier.weight(1f).height(62.dp))
        }
        ShimmerBox(Modifier.fillMaxWidth().height(28.dp).padding(top = 8.dp))
        repeat(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
                }
            }
        }
    }
}
