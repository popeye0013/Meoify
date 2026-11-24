package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meoify.music.ui.navigation.LocalDownloads

/**
 * Tiny marker over a song's artwork so offline copies are visible without
 * opening the track menu.
 */
@Composable
fun DownloadedBadge(trackId: String, modifier: Modifier = Modifier) {
    val downloads by LocalDownloads.current.collectAsStateWithLifecycle()
    val isDownloaded = remember(downloads, trackId) {
        downloads.any { it.track.id == trackId }
    }
    if (!isDownloaded) return

    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.OfflinePin,
            contentDescription = "Downloaded",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(10.dp),
        )
    }
}
