package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The playlist action sheet opened by the round three-dot button in the
 * playlist header. Items that cannot apply to the current playlist are hidden
 * instead of being shown disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionSheet(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    canEdit: Boolean,
    canSave: Boolean,
    canDelete: Boolean,
    canDownload: Boolean,
    hasTracks: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onShuffle: () -> Unit,
    onStartMix: () -> Unit,
    onEdit: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloads: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        ArtworkImage(
                            url = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            SheetAction(Icons.Filled.PlaylistPlay, "Play next", enabled = hasTracks, onClick = onPlayNext)
            SheetAction(Icons.Filled.Share, "Share", onClick = onShare)
            SheetAction(Icons.Filled.Shuffle, "Shuffle play", enabled = hasTracks, onClick = onShuffle)
            SheetAction(Icons.Filled.Radio, "Start mix", enabled = hasTracks, onClick = onStartMix)
            if (canEdit) {
                SheetAction(Icons.Filled.Edit, "Edit playlist", onClick = onEdit)
            }
            SheetAction(Icons.Filled.QueueMusic, "Add to queue", enabled = hasTracks, onClick = onAddToQueue)
            if (canDownload) {
                SheetAction(Icons.Filled.Download, "Offline options", onClick = onDownloads)
            }
            if (canSave) {
                SheetAction(Icons.Filled.LibraryAdd, "Save to playlist", onClick = onSave)
            }
            if (canDelete) {
                SheetAction(
                    icon = Icons.Filled.Delete,
                    label = "Delete playlist",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val resolvedTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = resolvedTint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = resolvedTint,
        )
    }
}
