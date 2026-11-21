package com.meoify.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meoify.music.data.remote.Artwork
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalPlayerState
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val state = LocalPlayerState.current
    val actions = LocalAppActions.current
    val track = state.track ?: return
    val cover = rememberTrackCover(track)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val swipeThreshold = with(density) { 48.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val swipeOffset = remember { Animatable(0f) }
    var swipeHandled by remember { mutableStateOf(false) }

    fun springBackMiniPlayer() {
        if (swipeHandled) return
        swipeScope.launch {
            swipeOffset.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
        }
    }

    Surface(
        onClick = { if (!swipeHandled) onOpen() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onOpen) {
                var dragUp = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        dragUp = 0f
                        swipeHandled = false
                    },
                    onVerticalDrag = { change, delta ->
                        if (delta < 0f && !swipeHandled) {
                            change.consume()
                            dragUp += -delta
                            swipeScope.launch {
                                swipeOffset.snapTo(dragUp.coerceAtMost(swipeThreshold * 1.6f))
                            }
                            // Same action as tapping the pill - no extra delay.
                            if (dragUp >= swipeThreshold) {
                                swipeHandled = true
                                onOpen()
                            }
                        }
                    },
                    onDragEnd = { springBackMiniPlayer() },
                    onDragCancel = { springBackMiniPlayer() },
                )
            }
            .graphicsLayer {
                val progress = (swipeOffset.value / (swipeThreshold * 1.6f)).coerceIn(0f, 1f)
                translationY = -swipeOffset.value * 0.55f
                alpha = 1f - progress * 0.35f
            }
            // The 10dp bottom gap keeps the pill floating above the tab bar
            // (and, on detail pages, above the system navigation bar).
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    ArtworkImage(
                        url = cover,
                        fallbackUrl = Artwork.songArt(track?.thumbnailUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.toggleLike(track)
                }) {
                    Icon(
                        imageVector = if (actions.isLiked(track.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (actions.isLiked(track.id)) "Remove from liked songs" else "Like",
                        tint = if (actions.isLiked(track.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.togglePlayPause()
                }) {
                    if (state.isPreparing || state.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.skipNext()
                }) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
