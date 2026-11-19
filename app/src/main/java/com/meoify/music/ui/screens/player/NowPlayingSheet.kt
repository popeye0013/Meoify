package com.meoify.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meoify.music.data.model.Track
import com.meoify.music.ui.components.ArtworkImage
import com.meoify.music.ui.components.rememberTrackCover
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.util.formatDuration

/**
 * The now-playing three-dot menu, presented as a modal sheet instead of a small
 * dropdown. It keeps the player actions (queue, share, video, sleep timer) and
 * adds like/queue shortcuts plus a readable song-details section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSheet(
    track: Track,
    source: String,
    sleepLeftMinutes: Int?,
    lyricsPreviewEnabled: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onShowQueue: () -> Unit,
    onToggleLyricsPreview: () -> Unit,
    onOpenVideo: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    val actions = LocalAppActions.current
    val library = LocalLibrary.current
    val haptic = LocalHapticFeedback.current
    val cover = rememberTrackCover(track)
    val liked = library.isLiked(track.id)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Close the sheet first so the next surface (queue, timer, link) opens on a
    // clean stack rather than behind a still-animating sheet.
    fun closeThen(block: () -> Unit) {
        onDismiss()
        block()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    ArtworkImage(
                        url = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickAction(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (liked) "Liked" else "Like",
                    active = liked,
                    modifier = Modifier.weight(1f),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.toggleLike(track)
                }
                QuickAction(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Queue",
                    modifier = Modifier.weight(1f),
                ) {
                    closeThen(onShowQueue)
                }
                QuickAction(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    modifier = Modifier.weight(1f),
                ) {
                    closeThen(onShare)
                }
                QuickAction(
                    icon = Icons.Filled.Timer,
                    label = "Sleep",
                    active = sleepLeftMinutes != null,
                    modifier = Modifier.weight(1f),
                ) {
                    closeThen(onSleepTimer)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            SheetAction(
                icon = Icons.Filled.PlayArrow,
                label = "Play next",
                supporting = "Queue this song right after the current one",
            ) {
                closeThen { actions.playNext(track) }
            }
            SheetAction(
                icon = Icons.Filled.Add,
                label = "Add to queue",
                supporting = "Put this song at the end of the queue",
            ) {
                closeThen { actions.addToQueue(track) }
            }
            SheetAction(
                icon = Icons.Filled.VideoLibrary,
                label = "Open video",
                supporting = "Watch on YouTube Music",
            ) {
                closeThen(onOpenVideo)
            }
            SheetAction(
                icon = Icons.Filled.Lyrics,
                label = if (lyricsPreviewEnabled) "Lyrics on" else "Lyrics off",
                supporting = "Show synced lyrics on Now Playing",
            ) {
                closeThen(onToggleLyricsPreview)
            }
            SheetAction(
                icon = Icons.Filled.Timer,
                label = "Sleep timer",
                supporting = sleepLeftMinutes?.let { "$it min left" }
                    ?: "Stop playback automatically",
                tint = if (sleepLeftMinutes != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                closeThen(onSleepTimer)
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Text(
                text = "Song details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            )

            DetailRow(label = "Title", value = track.title)
            DetailRow(
                label = "Artist",
                value = track.artist.ifBlank { "Unknown artist" },
                onClick = track.artistId?.let { id ->
                    { closeThen { actions.openArtist(id) } }
                },
            )
            track.album?.takeIf { it.isNotBlank() }?.let { album ->
                DetailRow(
                    label = "Album",
                    value = album,
                    onClick = track.albumId?.let { id ->
                        { closeThen { actions.openAlbum(id) } }
                    },
                )
            }
            DetailRow(
                label = "Duration",
                value = if (track.durationMs > 0) formatDuration(track.durationMs, showHours = true) else "—",
            )
            track.plays?.takeIf { it.isNotBlank() }?.let { plays ->
                DetailRow(label = "Plays", value = plays)
            }
            DetailRow(label = "Explicit", value = if (track.explicit) "Yes" else "No")
            DetailRow(label = "Video ID", value = track.id)
            DetailRow(label = "Source", value = source.ifBlank { "—" })
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetAction(
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
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                    .padding(horizontal = 8.dp)
            } else {
                Modifier.padding(horizontal = 20.dp)
            },
        )
        .padding(vertical = 9.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
