package com.meoify.music.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.meoify.music.data.model.AudioQuality
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.TrackLyrics
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.AppActions
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val state = LocalPlayerState.current
    val activity = LocalContext.current as? Activity
    val app = (LocalContext.current.applicationContext as com.meoify.music.MeoifyApplication)
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val track = state.track

    var accent by remember(track?.id) { mutableStateOf(ArtworkColorCache.fallbackColor(track?.title ?: "meoify")) }
    LaunchedEffect(track?.id) { accent = ArtworkColorCache.extract(track?.thumbnailUrl, track?.title ?: "meoify") }

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<TrackLyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val view = activity?.window?.decorView ?: return@DisposableEffect onDispose { }
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

    BackHandler(enabled = showLyrics) { showLyrics = false }

    fun openVideo() {
        val t = track ?: return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://music.youtube.com/watch?v=${t.id}"))
        runCatching { context.startActivity(intent) }
            .onFailure { actions.showSnackbar("No app available to open the video") }
    }

    fun shareTrack() {
        val t = track ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${t.id}\n\n${t.title} — ${t.artist}")
        }
        context.startActivity(Intent.createChooser(send, "Share song"))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.50f),
                        Color.Black.copy(alpha = 0.88f),
                        Color.Black,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            if (!showLyrics) {
                NowPlayingHeader(
                    source = state.sourceTitle,
                    onClose = onClose,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onShare = ::shareTrack,
                    onShowQueue = { showQueue = true },
                    onOpenVideo = ::openVideo,
                )
            }

            Crossfade(targetState = showLyrics, label = "playerMode") { inLyrics ->
                if (inLyrics) {
                    LyricsView(
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        isPlaying = state.isPlaying,
                        trackTitle = track?.title.orEmpty(),
                        accent = accent,
                        onBack = { showLyrics = false },
                        onSeek = actions.seekTo,
                        onTogglePlay = actions.togglePlayPause,
                        onNext = actions.skipNext,
                        onPrevious = actions.skipPrevious,
                    )
                } else {
                    PlayerMainContent(
                        state = state,
                        track = track,
                        quality = settings.quality,
                        actions = actions,
                        accent = accent,
                        onShowLyrics = { showLyrics = true },
                        onToggleMute = { app.container.player.toggleMute() },
                        onQualityChange = { app.container.settings.setQuality(it) },
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
private fun NowPlayingHeader(
    source: String,
    onClose: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClose,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.8.sp,
                color = Color.White.copy(alpha = 0.78f),
            )
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.52f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            Surface(
                onClick = { onMenuOpenChange(true) },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowQueue()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open video") },
                    leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onOpenVideo()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerMainContent(
    state: PlayerUiState,
    track: Track?,
    quality: AudioQuality,
    actions: AppActions,
    accent: Color,
    onShowLyrics: () -> Unit,
    onToggleMute: () -> Unit,
    onQualityChange: (AudioQuality) -> Unit,
) {
    val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (wide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                MotionCanvas(
                    url = track?.thumbnailUrl,
                    accent = accent,
                    modifier = Modifier
                        .size(side)
                        .shadow(18.dp, RoundedCornerShape(28.dp), clip = false),
                    cornerRadius = 28.dp,
                )
            }
            Spacer(Modifier.width(20.dp))
            PlayerControls(
                state = state,
                track = track,
                quality = quality,
                actions = actions,
                accent = accent,
                onShowLyrics = onShowLyrics,
                onToggleMute = onToggleMute,
                onQualityChange = onQualityChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .padding(bottom = 8.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                MotionCanvas(
                    url = track?.thumbnailUrl,
                    accent = accent,
                    modifier = Modifier
                        .size(side)
                        .shadow(18.dp, RoundedCornerShape(28.dp), clip = false),
                    cornerRadius = 28.dp,
                )
            }
            PlayerControls(
                state = state,
                track = track,
                quality = quality,
                actions = actions,
                accent = accent,
                onShowLyrics = onShowLyrics,
                onToggleMute = onToggleMute,
                onQualityChange = onQualityChange,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    track: Track?,
    quality: AudioQuality,
    actions: AppActions,
    accent: Color,
    onShowLyrics: () -> Unit,
    onToggleMute: () -> Unit,
    onQualityChange: (AudioQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val liked = track != null && actions.isLiked(track.id)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            CircleAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Remove from liked songs" else "Like",
                tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                size = 46.dp,
                background = Color.White.copy(alpha = 0.10f),
                onClick = { track?.let { actions.toggleLike(it) } },
            )
            Spacer(Modifier.width(10.dp))
            CircleAction(
                icon = Icons.Filled.FormatQuote,
                contentDescription = "Lyrics",
                tint = Color.White,
                size = 46.dp,
                background = Color.White.copy(alpha = 0.10f),
                onClick = onShowLyrics,
            )
        }

        Spacer(Modifier.height(8.dp))

        WavySeekBar(
            progress = state.progress,
            accent = accent,
            durationMs = state.durationMs,
            onSeek = actions.seekTo,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.durationMs > 0) formatDuration(state.positionMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.60f),
            )
            Text(
                text = if (state.durationMs > 0) {
                    "-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0L))}"
                } else {
                    "--:--"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.60f),
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AudioPill(quality = quality, onQualityChange = onQualityChange)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircleAction(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                size = 54.dp,
                background = Color.White.copy(alpha = 0.10f),
                onClick = actions.skipPrevious,
            )
            Surface(
                onClick = actions.togglePlayPause,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isPreparing || state.isBuffering) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = if (state.isPlaying) "Pause" else "Play",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            CircleAction(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                size = 54.dp,
                background = Color.White.copy(alpha = 0.10f),
                onClick = actions.skipNext,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                active = state.shuffle,
                contentDescription = "Shuffle",
                onClick = actions.toggleShuffle,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(22.dp),
                )
            }

            PillButton(
                active = state.muted,
                contentDescription = if (state.muted) "Unmute" else "Mute",
                onClick = onToggleMute,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (state.muted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(22.dp),
                )
            }

            PillButton(
                active = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                contentDescription = "Repeat",
                onClick = actions.cycleRepeat,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    contentDescription = null,
                    tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.86f)
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioPill(
    quality: AudioQuality,
    onQualityChange: (AudioQuality) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.10f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Audio quality",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AudioQuality.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                AudioQuality.HIGH -> "High quality"
                                AudioQuality.NORMAL -> "Normal quality"
                                AudioQuality.SAVER -> "Data saver"
                            },
                        )
                    },
                    leadingIcon = {
                        if (option == quality) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onQualityChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun MotionCanvas(
    url: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.07f)),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.5f),
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
}

@Composable
private fun WavySeekBar(
    progress: Float,
    accent: Color,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    val shown = (if (dragging) dragProgress else progress).coerceIn(0f, 1f)
    val wave = rememberInfiniteTransition(label = "seekWave")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "seekWavePhase",
    )

    Canvas(
        modifier = modifier
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((dragProgress * durationMs).toLong())
                    },
                    onDragCancel = {
                        dragging = false
                        onSeek((dragProgress * durationMs).toLong())
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * shown
        val amplitude = 3.2.dp.toPx()
        val wavelength = size.width / 7.5f

        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )

        if (progressX > 0f) {
            clipRect(right = progressX) {
                val path = Path()
                path.moveTo(0f, centerY)
                var x = 0f
                while (x <= size.width) {
                    val y = centerY + sin((x / wavelength) * 2f * PI + phase).toFloat() * amplitude
                    path.lineTo(x, y)
                    x += 3f
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.95f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawPath(
                path = Path().apply {
                    moveTo(0f, centerY)
                    var x = 0f
                    while (x <= progressX) {
                        val y = centerY + sin((x / wavelength) * 2f * PI + phase).toFloat() * amplitude
                        lineTo(x, y)
                        x += 3f
                    }
                },
                color = accent.copy(alpha = 0.5f),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        drawCircle(
            color = accent.copy(alpha = 0.28f),
            radius = 15.dp.toPx(),
            center = Offset(progressX, centerY),
        )
        drawCircle(
            color = Color.White,
            radius = 8.5.dp.toPx(),
            center = Offset(progressX, centerY),
        )
        drawCircle(
            color = accent,
            radius = 4.6.dp.toPx(),
            center = Offset(progressX, centerY),
        )
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    background: Color = Color.White.copy(alpha = 0.14f),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
private fun PillButton(
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.12f)
        },
        modifier = modifier.height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LyricsView(
    lyrics: TrackLyrics?,
    loading: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    trackTitle: String,
    accent: Color,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = lyrics?.lines?.indexOfLast { it.timeMs <= positionMs } ?: -1

    LaunchedEffect(currentIndex, isPlaying) {
        if (currentIndex >= 0 && isPlaying) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Back to player",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LYRICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.55f),
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = trackTitle.ifBlank { "Now playing" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }

                lyrics == null || lyrics.isEmpty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Lyrics aren't available for this track.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }

                lyrics.synced && lyrics.lines.isNotEmpty() -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 70.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(lyrics.lines.size) { index ->
                        val line = lyrics.lines[index]
                        val active = index == currentIndex
                        Text(
                            text = line.text.ifBlank { "♪" },
                            style = if (active) {
                                MaterialTheme.typography.headlineSmall.copy(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = accent.copy(alpha = 0.85f),
                                        blurRadius = 22f,
                                    ),
                                )
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.38f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.timeMs) }
                                .padding(vertical = 9.dp),
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = lyrics.plain.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.5f,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        WavySeekBar(
            progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            accent = accent,
            durationMs = durationMs,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(56.dp),
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    size = 48.dp,
                    background = Color.White.copy(alpha = 0.10f),
                    onClick = onPrevious,
                )
                Spacer(Modifier.width(14.dp))
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                CircleAction(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    size = 48.dp,
                    background = Color.White.copy(alpha = 0.10f),
                    onClick = onNext,
                )
            }

            Text(
                text = if (durationMs > 0) "-${formatDuration((durationMs - positionMs).coerceAtLeast(0L))}" else "--:--",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
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
