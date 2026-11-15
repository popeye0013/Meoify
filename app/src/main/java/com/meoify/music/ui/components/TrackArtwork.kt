package com.meoify.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.meoify.music.MeoifyApplication
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork

/**
 * The original cover art for [track].
 *
 * Shows an upgraded thumbnail immediately, then swaps in the album's square
 * high-resolution artwork when the track's own thumbnail is a video still. The
 * result is cached by [com.meoify.music.data.CoverArtResolver], so scrolling
 * back over the same row is instant.
 */
@Composable
fun rememberTrackCover(track: Track?): String? {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeoifyApplication }
    var cover by remember(track?.id, track?.thumbnailUrl) {
        mutableStateOf(Artwork.highRes(track?.thumbnailUrl))
    }
    LaunchedEffect(track?.id, track?.thumbnailUrl) {
        val current = track ?: return@LaunchedEffect
        val resolved = app.container.coverArt.cover(current)
        if (!resolved.isNullOrBlank()) cover = resolved
    }
    return cover
}
