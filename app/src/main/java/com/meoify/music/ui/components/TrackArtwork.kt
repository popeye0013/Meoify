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
 * Shows the track's own square YouTube Music cover immediately, then swaps in
 * the Spotify or Apple catalogue cover when the row's thumbnail is missing or
 * is a video still. A video frame is never shown. Results are cached by
 * [com.meoify.music.data.CoverArtResolver], so scrolling back over the same row
 * is instant.
 */
@Composable
fun rememberTrackCover(track: Track?): String? {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeoifyApplication }
    var cover by remember(track?.id, track?.thumbnailUrl) {
        mutableStateOf(Artwork.songArt(track?.thumbnailUrl))
    }
    LaunchedEffect(track?.id, track?.thumbnailUrl) {
        val current = track ?: return@LaunchedEffect
        val resolved = app.container.coverArt.cover(current)
        if (!resolved.isNullOrBlank()) cover = resolved
    }
    return cover
}
