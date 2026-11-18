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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.TrackLyrics
import com.meoify.music.playback.PlayerUiState
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.components.VolumeSheet
import com.meoify.music.ui.components.ArtworkImage
import com.meoify.music.ui.components.rememberTrackCover
import com.meoify.music.ui.navigation.AppActions
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalPlayerState
import com.meoify.music.ui.theme.ArtworkColorCache
import com.meoify.music.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val state = LocalPlayerState.current
    val activity = LocalContext.current as? Activity
    val app = (LocalContext.current.applicationContext as com.meoify.music.MeoifyApplication)
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val track = state.track
    val cover = rememberTrackCover(track)
    val scheme = MaterialTheme.colorScheme
    // The player is drawn over its own artwork-tinted backdrop, so it follows
    // the active light/dark/dynamic scheme instead of always being dark.
    val darkPlayer = scheme.background.luminance() < 0.5f

    var accent by remember(track?.id) { mutableStateOf(ArtworkColorCache.fallbackColor(track?.title ?: "meoify")) }
    LaunchedEffect(cover, track?.title) { accent = ArtworkColorCache.extract(cover, track?.title ?: "meoify") }

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<TrackLyrics?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }

    // Swipe down on the artwork to return to the mini player. An Animatable
    // keeps the content glued to the finger while dragging and gives a soft
    // spring-back when the gesture is released before the threshold.
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 96.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dismissOffset = remember { Animatable(0f) }
    var dismissTriggered by remember { mutableStateOf(false) }

    fun settleDismissSwipe() {
        if (dismissTriggered) return
        swipeScope.launch {
            dismissOffset.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
        }
    }

    // Keep the remaining sleep time fresh while the timer runs.
    val sleepEndsAtMs = state.sleepEndsAtMs
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepEndsAtMs) {
        while (sleepEndsAtMs != null) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val sleepLeftMinutes = sleepEndsAtMs?.let { end ->
        (((end - nowMs).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
    }

    DisposableEffect(activity, darkPlayer) {
        val view = activity?.window?.decorView ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !darkPlayer
        controller.isAppearanceLightNavigationBars = !darkPlayer
        onDispose {
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNav
        }
    }

    // One effect owns both the reset and the fetch. Splitting them left the
    // view empty after a track change: the load effect saw the previous track's
    // lyrics and returned early, then the reset effect cleared them.
    LaunchedEffect(track?.id, showLyrics) {
        lyrics = null
        lyricsLoading = false
        if (!showLyrics || track == null) return@LaunchedEffect
        lyricsLoading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching { app.container.repository.trackLyrics(track) }.getOrNull()
        }
        lyrics = loaded
        lyricsLoading = false
    }

    BackHandler(enabled = showLyrics) { showLyrics = false }

    if (showVolume) {
        VolumeSheet(onDismiss = { showVolume = false })
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            sleepLeftMinutes = sleepLeftMinutes,
            onSelect = { minutes ->
                actions.sleepTimer(minutes)
                actions.showSnackbar(
                    if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer off",
                )
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
    }

    fun openVideoInBrowser(t: Track) {
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${t.id}"))
        val chooser = Intent.createChooser(view, "Open video")
        runCatching { context.startActivity(chooser) }
            .onFailure { actions.showSnackbar("No app available to open the video") }
    }

    fun openVideo() {
        val t = track ?: return
        // Meoify itself verifies music.youtube.com links, so a plain ACTION_VIEW
        // resolves back to us (and only shows "song links aren't supported").
        // Target the YouTube Music app directly, then fall back to a browser on
        // www.youtube.com, which Meoify does not handle.
        val ytMusic = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://music.youtube.com/watch?v=${t.id}"),
        ).apply { setPackage("com.google.android.apps.youtube.music") }
        if (ytMusic.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(ytMusic) }
                .onFailure { openVideoInBrowser(t) }
        } else {
            openVideoInBrowser(t)
        }
    }

    fun shareTrack() {
        val t = track ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "https://music.youtube.com/watch?v=${t.id}\n\n${t.title} — ${t.artist}",
            )
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share song")) }
            .onFailure { actions.showSnackbar("No app available to share") }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = if (darkPlayer) 0.50f else 0.20f),
                        scheme.background.copy(alpha = 0.92f),
                        scheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    val progress = (dismissOffset.value / dismissThreshold).coerceIn(0f, 1f)
                    translationY = dismissOffset.value
                    alpha = 1f - progress * 0.25f
                    scaleX = 1f - progress * 0.04f
                    scaleY = 1f - progress * 0.04f
                },
        ) {
            if (!showLyrics) {
                NowPlayingHeader(
                    source = state.sourceTitle,
                    onClose = onClose,
                    onMenuClick = { menuOpen = true },
                    onShare = ::shareTrack,
                    onShowQueue = { showQueue = true },
                    onOpenVideo = ::openVideo,
                    sleepLeftMinutes = sleepLeftMinutes,
                    onSleepTimer = { showSleepTimer = true },
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
                        trackArtist = track?.artist.orEmpty(),
                        artworkUrl = cover,
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
                        actions = actions,
                        accent = accent,
                        onShowLyrics = { showLyrics = true },
                        onShowQueue = { showQueue = true },
                        onShowVolume = { showVolume = true },
                        artworkSwipeModifier = Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { dismissTriggered = false },
                                onVerticalDrag = { change, delta ->
                                    if (delta > 0f && !dismissTriggered) {
                                        change.consume()
                                        val next = (dismissOffset.value + delta)
                                            .coerceAtMost(dismissThreshold * 1.6f)
                                        swipeScope.launch { dismissOffset.snapTo(next) }
                                        // Same action as tapping the down arrow - no extra delay.
                                        if (next >= dismissThreshold) {
                                            dismissTriggered = true
                                            onClose()
                                        }
                                    }
                                },
                                onDragEnd = { settleDismissSwipe() },
                                onDragCancel = { settleDismissSwipe() },
                            )
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
            onMove = { from, to -> actions.moveQueueItem(from, to) },
            onRemove = { index -> actions.removeFromQueue(index) },
        )
    }

    if (menuOpen && track != null) {
        NowPlayingSheet(
            track = track,
            source = state.sourceTitle,
            sleepLeftMinutes = sleepLeftMinutes,
            onDismiss = { menuOpen = false },
            onShare = ::shareTrack,
            onShowQueue = { showQueue = true },
            onOpenVideo = ::openVideo,
            onSleepTimer = { showSleepTimer = true },
        )
    }

}

@Composable
private fun NowPlayingHeader(
    source: String,
    onClose: () -> Unit,
    onMenuClick: () -> Unit,
    onShare: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenVideo: () -> Unit,
    sleepLeftMinutes: Int?,
    onSleepTimer: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onMenuClick()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerMenuItem(
    icon: ImageVector,
    label: String,
    supporting: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}
@Composable
private fun PlayerMainContent(
    state: PlayerUiState,
    track: Track?,
    actions: AppActions,
    accent: Color,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onShowVolume: () -> Unit,
    artworkSwipeModifier: Modifier = Modifier,
) {
    val cover = rememberTrackCover(track)
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
                    .fillMaxHeight()
                    .then(artworkSwipeModifier),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                MotionCanvas(
                    url = cover,
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
                actions = actions,
                accent = accent,
                onShowLyrics = onShowLyrics,
                onShowVolume = onShowVolume,
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
                    .padding(top = 8.dp, bottom = 16.dp)
                    .then(artworkSwipeModifier),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                MotionCanvas(
                    url = cover,
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
                actions = actions,
                accent = accent,
                onShowLyrics = onShowLyrics,
                onShowVolume = onShowVolume,
            )
            QueueSwipeHandle(
                queueSize = state.queue.size,
                onOpen = onShowQueue,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Bottom edge of the now playing screen: swipe up (or tap) to view the queue. */
@Composable
private fun QueueSwipeHandle(
    queueSize: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var dragUp by remember { mutableStateOf(0f) }
    val threshold = 56f
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, delta ->
                        if (delta < 0f) {
                            change.consume()
                            dragUp += -delta
                            if (dragUp >= threshold) {
                                dragUp = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpen()
                            }
                        } else {
                            dragUp = 0f
                        }
                    },
                    onDragEnd = { dragUp = 0f },
                    onDragCancel = { dragUp = 0f },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Queue · $queueSize ${if (queueSize == 1) "song" else "songs"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    track: Track?,
    actions: AppActions,
    accent: Color,
    onShowLyrics: () -> Unit,
    onShowVolume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liked = track != null && actions.isLiked(track.id)
    val haptic = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            CircleAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Remove from liked songs" else "Like",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                size = 46.dp,
                background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                onClick = { track?.let { actions.toggleLike(it) } },
            )
            Spacer(Modifier.width(10.dp))
            CircleAction(
                icon = Icons.Filled.FormatQuote,
                contentDescription = "Lyrics",
                tint = MaterialTheme.colorScheme.onSurface,
                size = 46.dp,
                background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                onClick = onShowLyrics,
            )
        }

        Spacer(Modifier.height(8.dp))

        WavySeekBar(
            progress = state.progress,
            durationMs = state.durationMs,
            onSeek = actions.seekTo,
            modifier = Modifier.fillMaxWidth(),
            playing = state.isPlaying,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.durationMs > 0) formatDuration(state.positionMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (state.durationMs > 0) {
                    "-${formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0L))}"
                } else {
                    "--:--"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                tint = MaterialTheme.colorScheme.onSurface,
                size = 54.dp,
                background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                onClick = actions.skipPrevious,
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.togglePlayPause()
                },
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
                tint = MaterialTheme.colorScheme.onSurface,
                size = 54.dp,
                background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                    modifier = Modifier.size(22.dp),
                )
            }

            PillButton(
                active = state.muted,
                contentDescription = "Volume and output device",
                onClick = onShowVolume,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (state.muted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
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
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
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
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        if (!url.isNullOrBlank()) {
            ArtworkImage(
                url = url,
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
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
}

@Composable
private fun WavySeekBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    var lastTickBucket by remember { mutableStateOf(-1) }
    val haptic = LocalHapticFeedback.current
    val shown = (if (dragging) dragProgress else progress).coerceIn(0f, 1f)
    // Match the play/pause button so the seek bar reads as part of the same control.
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val knobColor = MaterialTheme.colorScheme.onSurface
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
    // Freeze the wave while paused: the shape stays but nothing moves.
    val wavePhase = if (playing || dragging) phase else 0f

    Canvas(
        modifier = modifier
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSeek(((offset.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        lastTickBucket = -1
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        // One light tick per 5% of the track while scrubbing.
                        val bucket = (dragProgress * 20f).toInt()
                        if (bucket != lastTickBucket) {
                            lastTickBucket = bucket
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeek((dragProgress * durationMs).toLong())
                    },
                    onDragCancel = {
                        dragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((dragProgress * durationMs).toLong())
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * shown
        val amplitude = 4.2.dp.toPx()
        val wavelength = size.width / 7.5f

        drawLine(
            color = trackColor,
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
                    val y = centerY + sin((x / wavelength) * 2f * PI + wavePhase).toFloat() * amplitude
                    path.lineTo(x, y)
                    x += 3f
                }
                drawPath(
                    path = path,
                    color = progressColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            drawPath(
                path = Path().apply {
                    moveTo(0f, centerY)
                    var x = 0f
                    while (x <= progressX) {
                        val y = centerY + sin((x / wavelength) * 2f * PI + wavePhase).toFloat() * amplitude
                        lineTo(x, y)
                        x += 3f
                    }
                },
                color = progressColor.copy(alpha = 0.35f),
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        drawCircle(
            color = progressColor.copy(alpha = 0.28f),
            radius = 15.dp.toPx(),
            center = Offset(progressX, centerY),
        )
        drawCircle(
            color = knobColor,
            radius = 8.5.dp.toPx(),
            center = Offset(progressX, centerY),
        )
        drawCircle(
            color = progressColor,
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
    background: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
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
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
    trackArtist: String,
    artworkUrl: String?,
    accent: Color,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    // Tapping a line seeks; it must not paint the rectangular ripple that a
    // full-width text row would show.
    val tapInteraction = remember { MutableInteractionSource() }
    val currentIndex = lyrics?.lines?.indexOfLast { it.timeMs <= positionMs } ?: -1
    // Jump straight to the current line when the view opens (or after a seek);
    // only later line changes animate. Scrolling must also follow the position
    // while paused, otherwise a manual seek leaves the old line highlighted.
    var centered by remember(lyrics) { mutableStateOf(false) }

    LaunchedEffect(lyrics, currentIndex) {
        if (currentIndex < 0) return@LaunchedEffect
        val target = (currentIndex - 2).coerceAtLeast(0)
        if (centered) {
            listState.animateScrollToItem(target)
        } else {
            listState.scrollToItem(target)
            centered = true
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Back to player",
                        tint = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    text = trackTitle.ifBlank { "Now playing" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                }

                lyrics == null || lyrics.isEmpty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Lyrics aren't available for this track.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = tapInteraction,
                                    indication = null,
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSeek(line.timeMs)
                                }
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.5f,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        WavySeekBar(
            progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            durationMs = durationMs,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
            playing = isPlaying,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = 48.dp,
                    background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    onClick = onPrevious,
                )
                Spacer(Modifier.width(14.dp))
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePlay()
                    },
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = 48.dp,
                    background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    onClick = onNext,
                )
            }

            Text(
                text = if (durationMs > 0) "-${formatDuration((durationMs - positionMs).coerceAtLeast(0L))}" else "--:--",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragDistance by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fallbackRowHeight = with(density) { 72.dp.toPx() }
    var rowHeight by remember { mutableStateOf(fallbackRowHeight) }

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
            if (state.queue.isNotEmpty()) {
                Text(
                    text = "Hold a song to reorder · Swipe right to remove",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            ) {
                items(state.queue.size) { index ->
                    val track = state.queue[index]
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemove(index)
                            }
                            // Stay settled: removing the entry is the feedback, so the
                            // next row must not inherit a half-dismissed state.
                            false
                        },
                    )
                    val rowModifier = Modifier
                        .zIndex(if (draggingIndex == index) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (draggingIndex == index) dragDistance else 0f
                        }
                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = rowModifier,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 22.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Remove",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                    ) {
                        TrackRow(
                            track = track,
                            index = index,
                            isCurrent = track.id == state.track?.id,
                            onClick = { onPlayAt(index) },
                            onMoreClick = { onMore(track) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .pointerInput(index) {
                                // Hold the song, then drag up or down to change play order.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingIndex = index
                                        dragDistance = 0f
                                        rowHeight = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == index }
                                            ?.size?.toFloat()
                                            ?.takeIf { it > 0f }
                                            ?: fallbackRowHeight
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDistance += dragAmount.y
                                        val steps = (dragDistance / rowHeight).roundToInt()
                                        if (steps != 0) {
                                            val current = draggingIndex
                                            val max = listState.layoutInfo.totalItemsCount - 1
                                            val target = (current + steps).coerceIn(0, max)
                                            if (current >= 0 && target != current) {
                                                onMove(current, target)
                                                draggingIndex = target
                                            }
                                            dragDistance -= steps * rowHeight
                                        }
                                    },
                                    onDragEnd = {
                                        draggingIndex = -1
                                        dragDistance = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragDistance = 0f
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleep timer chooser. Times pause playback from the player service, so the
 * countdown continues even if this screen is closed.
 */
@Composable
private fun SleepTimerDialog(
    sleepLeftMinutes: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    text = sleepLeftMinutes?.let { "Playback pauses in about $it min." }
                        ?: "Pause playback automatically after a while.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(minutes) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (sleepLeftMinutes != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(0) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Turn off timer",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
