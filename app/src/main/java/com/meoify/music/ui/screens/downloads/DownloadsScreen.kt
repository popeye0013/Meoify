package com.meoify.music.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.DownloadState
import com.meoify.music.data.model.formatDownloadBytes
import com.meoify.music.ui.components.SectionHeader
import com.meoify.music.ui.components.TrackRow
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalBottomContentInset
import com.meoify.music.ui.navigation.LocalPlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = (context.applicationContext as MeoifyApplication).container.downloadManager
    val downloads by manager.downloads.collectAsStateWithLifecycle()
    val preferences by manager.preferences.collectAsStateWithLifecycle()
    val actions = LocalAppActions.current
    val currentTrackId = LocalPlayerState.current.track?.id
    var confirmClear by remember { mutableStateOf(false) }

    val totalBytes = downloads.sumOf { it.fileSize }
    val limitBytes = preferences.maxOfflineMiB * 1024L * 1024L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = LocalBottomContentInset.current + 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${downloads.size} song${if (downloads.size == 1) "" else "s"} • ${formatDownloadBytes(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { actions.openDownloadQueue() }) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Download queue")
                }
                if (downloads.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove all downloads")
                    }
                }
            }
        }

        if (preferences.maxOfflineMiB > 0) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        "${formatDownloadBytes(totalBytes)} of ${preferences.maxOfflineMiB} MiB kept offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (totalBytes.toFloat() / limitBytes).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("No offline songs yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Use Download from a song's menu or an album/playlist page. Saved songs play without a connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item { SectionHeader(title = "Saved songs") }
            items(downloads, key = { "download_${it.track.id}" }) { entry ->
                TrackRow(
                    track = entry.track,
                    isCurrent = currentTrackId == entry.track.id,
                    onClick = {
                        actions.playTracks(downloads.map { it.track }, downloads.indexOf(entry), "Downloads")
                    },
                    onMoreClick = { actions.openTrackMenu(entry.track, null) },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all downloads?") },
            text = { Text("${downloads.size} offline song${if (downloads.size == 1) "" else "s"} will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.clearDownloads()
                    confirmClear = false
                }) { Text("Remove all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = (context.applicationContext as MeoifyApplication).container.downloadManager
    val queue by manager.queue.collectAsStateWithLifecycle()
    val active = queue.any { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
    val paused = queue.any { it.state == DownloadState.PAUSED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = LocalBottomContentInset.current + 16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Download queue",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${queue.size} song${if (queue.size == 1) "" else "s"} in queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QueueButton(
                    icon = if (active) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (active) "Pause all" else "Resume all",
                    onClick = { if (active) manager.pauseAll() else manager.resumeAll() },
                    modifier = Modifier.weight(1f),
                )
                QueueButton(
                    icon = Icons.Filled.Delete,
                    label = "Cancel active",
                    onClick = { manager.cancelAll() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (queue.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "The queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(queue, key = { "${it.track.id}_${it.enqueuedAt}" }) { item ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (item.state) {
                            DownloadState.COMPLETED -> Icons.Filled.CheckCircle
                            DownloadState.FAILED, DownloadState.CANCELLED -> Icons.Filled.ErrorOutline
                            DownloadState.PAUSED -> Icons.Filled.Pause
                            else -> Icons.Filled.Download
                        },
                        contentDescription = null,
                        tint = when (item.state) {
                            DownloadState.FAILED, DownloadState.CANCELLED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            when (item.state) {
                                DownloadState.QUEUED -> "Queued"
                                DownloadState.RUNNING -> formatDownloadBytes(item.downloadedBytes)
                                DownloadState.PAUSED -> "Paused"
                                DownloadState.FAILED -> item.lastError.orEmpty()
                                DownloadState.COMPLETED -> "Downloaded"
                                DownloadState.CANCELLED -> "Cancelled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (item.state == DownloadState.FAILED) {
                        IconButton(onClick = { manager.retry(item.track.id) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                        }
                    }
                    IconButton(onClick = { manager.dismiss(item.track.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Dismiss")
                    }
                }
                if (item.state == DownloadState.RUNNING && (item.totalBytes ?: 0L) > 0L) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = {
                            (item.downloadedBytes.toFloat() / item.totalBytes!!.toFloat()).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }

        if (paused) {
            item {
                TextButton(
                    onClick = { manager.resumeAll() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("Resume paused downloads") }
            }
        }
    }
}

@Composable
private fun QueueButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
