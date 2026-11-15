package com.meoify.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.meoify.music.data.remote.Artwork

/**
 * Artwork that always shows the sharpest version available.
 *
 * Video stills can be served at several sizes; this tries the largest one and
 * quietly drops to the size YouTube always generates if it is missing, so
 * artwork is never blurry when a big frame exists and never blank when it does
 * not. Non-video covers load exactly as before.
 */
@Composable
fun ArtworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackUrl: String? = null,
) {
    val candidates = remember(url, fallbackUrl) {
        (Artwork.candidates(url) + Artwork.candidates(fallbackUrl)).distinct()
    }
    var index by remember(url, fallbackUrl) { mutableIntStateOf(0) }
    AsyncImage(
        model = candidates.getOrNull(index),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            if (index < candidates.lastIndex) index++
        },
    )
}
