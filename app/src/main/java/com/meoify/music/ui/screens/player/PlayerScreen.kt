package com.meoify.music.ui.screens.player

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.TrackLyrics
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val state = LocalPlayerState.current
    val activity = LocalContext.current as? Activity
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.meoify.music.MeoifyApplication
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val track = state.track

    var accent by remember(track?.id) { mutableStateOf(ArtworkColorCache.fallbackColor(track?.title ?: "meoify")) }
    LaunchedEffect(track?.id) { accent = ArtworkColorCache.extract(track?.thumbnailUrl, track?.title ?: "meoify") }

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<TrackLyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val view = activity?.window?.decorView ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNav
        }
    }

    LaunchedEffect(track?.id, showLyrics) {
        if (!showLyrics || track == null) return@LaunchedEffect
        if (lyrics != null) return@LaunchedEffect
        lyricsLoading = true
        lyrics = withContext(Dispatchers.IO) {
            runCatching { app.container.repository.trackLyrics(track) }.getOrNull()
        }
        lyricsLoading = false
    }

    LaunchedEffect(track?.id) {
        lyrics = null
        lyricsLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (track?.thumbnailUrl != null) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (settings.blurPlayerBackground && android.os.Build.VERSION.SDK_INT >= 31) {
                            Modifier.blur(70.dp)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.58f),
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close player",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Playing from ${state.sourceTitle}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    val t = track ?: return@IconButton
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "https://music.youtube.com/watch?v=${t.id}\n\n${t.title} — ${t.artist}",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share song"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
            }

            Crossfade(targetState = showLyrics, label = "playerMode") { inLyrics ->
                if (inLyrics) {
                    LyricsView(
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        positionMs = state.positionMs,
                        isPlaying = state.isPlaying,
                        onBack = { showLyrics = false },
                        onSeek = actions.seekTo,
                    )
                } else {
                    PlayerMainContent(
                        state = state,
                        track = track,
                        actions = actions,
                        onShowLyrics = { showLyrics = true },
                        onShowQueue = { showQueue = true },
                        onShare = {
                            val t = track ?: return@PlayerMainContent
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${t.id}\n\n${t.title} — ${t.artist}",
                                )
                            }
                            context.startActivity(Intent.createChooser(send, "Share song"))
                        },
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            state = state,
            onDismiss = { showQueue = false },
            onPlayAt = { index ->
                showQueue = false
                actions.playTracks(state.queue, index, state.sourceTitle)
            },
            onMore = { track ->
                showQueue = false
                actions.openTrackMenu(track)
            },
        )
    }
}

@Composable
private fun PlayerMainContent(
    state: PlayerUiState,
    track: Track?,
    actions: com.meoify.music.ui.navigation.AppActions,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 360.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            AsyncImage(
                model = track?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (track == null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                Color.Black,
                            ),
                        ),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(72.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { track?.let { actions.toggleLike(it) } }) {
                val liked = track != null && actions.isLiked(track.id)
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Remove from liked songs" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(0f) }
        val sliderValue = if (dragging) dragValue else state.progress
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                actions.seekTo((dragValue * state.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragging) (dragValue * state.durationMs).toLong() else state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f),
            )
            Text(
                text = formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(26.dp),
                )
            }
            IconButton(onClick = actions.skipPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Surface(
                onClick = actions.togglePlayPause,
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(70.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isPreparing || state.isBuffering) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            IconButton(onClick = actions.skipNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = actions.cycleRepeat) {
                Icon(
                    imageVector = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerAction(Icons.Filled.Lyrics, "Lyrics", onShowLyrics)
            PlayerAction(Icons.Filled.QueueMusic, "Queue", onShowQueue)
            PlayerAction(Icons.Filled.Share, "Share", onShare)
        }
    }
}

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.9f))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.66f))
    }
}

@Composable
private fun LyricsView(
    lyrics: TrackLyrics?,
    loading: Boolean,
    positionMs: Long,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = lyrics?.lines?.indexOfLast { it.timeMs <= positionMs } ?: -1

    LaunchedEffect(currentIndex, isPlaying) {
        if (currentIndex >= 0 && isPlaying) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back to player", tint = Color.White)
            }
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            lyrics == null || lyrics.isEmpty -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Lyrics aren't available for this track.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            lyrics.synced && lyrics.lines.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 80.dp, horizontal = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lyrics.lines.size) { index ->
                        val line = lyrics.lines[index]
                        val active = index == currentIndex
                        Text(
                            text = line.text,
                            style = if (active) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.42f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.timeMs) }
                                .padding(vertical = 9.dp),
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = lyrics.plain.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.45f,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Queue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = state.sourceTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${state.queue.size} songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(state.queue.size) { index ->
                    val track = state.queue[index]
                    TrackRow(
                        track = track,
                        isCurrent = track.id == state.track?.id,
                        onClick = { onPlayAt(index) },
                        onMoreClick = { onMore(track) },
                    )
                }
            }
        }
    }
}
