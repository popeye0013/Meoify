package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Offline actions for an album or playlist. The header uses one icon; the sheet
 * explains each action so bulk downloads and playlist syncing are discoverable
 * without crowding the page with two labelled buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    title: String,
    trackCount: Int,
    allDownloaded: Boolean,
    canTrack: Boolean,
    isTracked: Boolean,
    onDismiss: () -> Unit,
    onDownloadAll: () -> Unit,
    onRemoveAll: () -> Unit,
    onTrack: () -> Unit,
    onUntrack: () -> Unit,
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
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (allDownloaded) {
                OfflineAction(
                    icon = Icons.Filled.Delete,
                    label = "Remove downloads",
                    description = "Delete the offline copies of these $trackCount songs from this device.",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onRemoveAll,
                )
            } else {
                OfflineAction(
                    icon = Icons.Filled.Download,
                    label = "Download all",
                    description = "Save $trackCount songs for playback without a connection.",
                    onClick = onDownloadAll,
                )
            }

            if (canTrack) {
                if (isTracked) {
                    OfflineAction(
                        icon = Icons.Filled.OfflinePin,
                        label = "Stop keeping offline",
                        description = "Stop downloading songs added later. Songs already saved will stay.",
                        onClick = onUntrack,
                    )
                } else {
                    OfflineAction(
                        icon = Icons.Filled.Sync,
                        label = "Keep this playlist offline",
                        description = "Automatically save songs added to this playlist and remove ones deleted from it.",
                        onClick = onTrack,
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineAction(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
