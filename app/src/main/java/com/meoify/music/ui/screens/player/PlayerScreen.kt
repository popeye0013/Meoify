package com.meoify.music.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    var fullArtwork by remember { mutableStateOf(false) }
    val onCellular = remember(track?.id, settings.canvasOverCellular) { isCellular(context) }
    val canvasAnimated = settings.animatedCanvas && (!onCellular || settings.canvasOverCellular)
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

    fun shareTrack() {
        val t = track ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${t.id}\n\n${t.title} — ${t.artist}")
        }
        context.startActivity(Intent.createChooser(send, "Share song"))
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
                            accent.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NowPlayingHeader(
                source = state.sourceTitle,
                onClose = onClose,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onShare = ::shareTrack,
                onShowQueue = { showQueue = true },
                onShowLyrics = { showLyrics = true },
                onFullScreen = { fullArtwork = true },
            )

            Crossfade(targetState = showLyrics, label = "playerMode") { inLyrics ->
                if (inLyrics) {
                    LyricsView(
                        lyrics = lyrics,
                        loading = lyricsLoading,
                        positionMs = state.positionMs,
                        isPlaying = state.isPlaying,
                        artworkUrl = track?.thumbnailUrl,
                        accent = accent,
                        animate = canvasAnimated && state.isPlaying,
                        onBack = { showLyrics = false },
                        onSeek = actions.seekTo,
                    )
                } else {
                    PlayerMainContent(
                        state = state,
                        track = track,
                        quality = settings.quality,
                        actions = actions,
                        accent = accent,
                        canvasAnimated = canvasAnimated,
                        onOpenArtwork = { fullArtwork = true },
                        onShowLyrics = { showLyrics = true },
                        onShowQueue = { showQueue = true },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = fullArtwork && track != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                MotionCanvas(
                    url = track?.thumbnailUrl,
                    accent = accent,
                    animate = canvasAnimated && state.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    showPlaceholder = false,
                    onClick = { fullArtwork = false },
                )
                Surface(
                    onClick = { fullArtwork = false },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(14.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Exit full screen artwork",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 22.dp),
                ) {
                    Text(
                        text = track?.title.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.artist.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    onShowLyrics: () -> Unit,
    onFullScreen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
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
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.6.sp,
                color = Color.White.copy(alpha = 0.82f),
            )
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White.copy(alpha = 0.9f),
                )
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
                    text = { Text("Lyrics") },
                    leadingIcon = { Icon(Icons.Filled.FormatQuote, contentDescription = null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onShowLyrics()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Full screen artwork") },
                    leadingIcon = { Icon(Icons.Filled.Fullscreen, contentDescription = null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onFullScreen()
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
    canvasAnimated: Boolean,
    onOpenArtwork: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(bottom = 10.dp),
    ) {
        // Animated album canvas — takes whatever vertical space is left over.
        MotionCanvas(
            url = track?.thumbnailUrl,
            accent = accent,
            animate = canvasAnimated && state.isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp, bottom = 12.dp),
            cornerRadius = 14.dp,
            onClick = onOpenArtwork,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            CircleAction(
                icon = if (track != null && actions.isLiked(track.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (track != null && actions.isLiked(track.id)) "Remove from liked songs" else "Like",
                tint = if (track != null && actions.isLiked(track.id)) MaterialTheme.colorScheme.primary else Color.White,
                onClick = { track?.let { actions.toggleLike(it) } },
            )
            Spacer(Modifier.width(8.dp))
            CircleAction(
                icon = Icons.Filled.FormatQuote,
                contentDescription = "Lyrics",
                tint = Color.White,
                onClick = onShowLyrics,
            )
        }

        Spacer(Modifier.height(8.dp))

        var seeking by remember { mutableStateOf(false) }
        var seekValue by remember { mutableStateOf(0f) }
        WavySeekBar(
            progress = state.progress,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.26f),
            onProgressChange = { value ->
                seeking = true
                seekValue = value
            },
            onSeek = { value ->
                seeking = false
                actions.seekTo((value * state.durationMs).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPosition = if (seeking) (seekValue * state.durationMs).toLong() else state.positionMs
            val shownRemaining = (state.durationMs - shownPosition).coerceAtLeast(0L)
            Text(
                text = if (state.durationMs > 0) formatDuration(shownPosition) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.62f),
            )
            Text(
                text = if (state.durationMs > 0) "-${formatDuration(shownRemaining)}" else "--:--",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.62f),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (quality) {
                            AudioQuality.HIGH -> "Audio • High"
                            AudioQuality.NORMAL -> "Audio • Normal"
                            AudioQuality.SAVER -> "Audio • Saver"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircleAction(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                size = 56.dp,
                background = Color.White.copy(alpha = 0.12f),
                onClick = actions.skipPrevious,
            )
            Surface(
                onClick = actions.togglePlayPause,
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f),
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
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = if (state.isPlaying) "Pause" else "Play",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            CircleAction(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                size = 56.dp,
                background = Color.White.copy(alpha = 0.12f),
                onClick = actions.skipNext,
            )
        }

        Spacer(Modifier.height(14.dp))

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
                active = false,
                contentDescription = "Queue",
                onClick = onShowQueue,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.86f),
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
private fun MotionCanvas(
    url: String?,
    accent: Color,
    animate: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    showPlaceholder: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "albumCanvas")
    val zoom by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "canvasZoom",
    )
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "canvasDrift",
    )
    val scale = if (animate) zoom else 1f
    val shift = if (animate) drift else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.07f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = shift * 16f
                        translationY = shift * -12f
                    },
            )
        } else if (showPlaceholder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
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

        if (animate) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            start = Offset(shift * 220f, 0f),
                            end = Offset(shift * 220f + 420f, 620f),
                        ),
                    ),
            )
        }

        // Gradient blur that blends the canvas bottom into the ambient background.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            0.86f to accent.copy(alpha = 0.42f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun WavySeekBar(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onProgressChange: (Float) -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    val shown = if (dragging) dragProgress else progress.coerceIn(0f, 1f)

    val transition = rememberInfiniteTransition(label = "waveSeek")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(5_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(dragProgress)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(dragProgress)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        dragging = false
                        onSeek(dragProgress)
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * shown
        val track = 6.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = track,
            cap = StrokeCap.Round,
        )

        if (progressX > 0f) {
            clipRect(right = progressX) {
                val amplitude = 5.dp.toPx()
                val body = Path().apply {
                    moveTo(0f, centerY)
                    var x = 0f
                    while (x <= size.width + 2f) {
                        val y = centerY + sin((x / size.width) * 6f * PI.toFloat() + phase) * amplitude
                        lineTo(x, y)
                        x += 3f
                    }
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(body, color = activeColor.copy(alpha = 0.20f))
                drawWave(centerY, amplitude, phase, activeColor.copy(alpha = 0.95f), 2.4f.dp.toPx(), 0f)
                drawWave(
                    centerY,
                    amplitude * 0.62f,
                    phase + 1.5f,
                    activeColor.copy(alpha = 0.48f),
                    1.4f.dp.toPx(),
                    1.4f.dp.toPx(),
                )
            }
        }

        drawCircle(
            color = activeColor.copy(alpha = 0.28f),
            radius = 13.dp.toPx(),
            center = Offset(progressX, centerY),
        )
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(progressX, centerY),
        )
    }
}

private fun DrawScope.drawWave(
    centerY: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    strokeWidth: Float,
    offsetY: Float,
) {
    val path = Path()
    var x = 0f
    while (x <= size.width + 3f) {
        val y = centerY + offsetY + sin((x / size.width) * 6f * PI.toFloat() + phase) * amplitude
        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
        x += 3f
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

/** True when the active connection is mobile data (and not Wi-Fi, ethernet or VPN). */
private fun isCellular(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    val cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    val other = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    return cellular && !other
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
    isPlaying: Boolean,
    artworkUrl: String?,
    accent: Color,
    animate: Boolean,
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

    val motion = rememberInfiniteTransition(label = "lyricsCanvas")
    val angle by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lyricsAngle",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (animate) angle else 0f
                        scaleX = 2.3f
                        scaleY = 2.3f
                        alpha = 0.32f
                    }
                    .then(
                        if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(50.dp) else Modifier,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
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
