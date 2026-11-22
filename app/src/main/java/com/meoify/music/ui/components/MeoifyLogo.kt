package com.meoify.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meoify.music.R

/**
 * The Meoify cat-and-play mark in full colour. Used wherever the app presents
 * itself — the welcome screen and the YouTube sign-in page.
 */
@Composable
fun MeoifyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Image(
        painter = painterResource(R.drawable.meoify_logo),
        contentDescription = "Meoify",
        modifier = modifier.size(size),
    )
}

/**
 * Single-colour version of the mark, for tinted placements such as inside a
 * filled button where the coral tile would disappear into the background.
 */
@Composable
fun MeoifyMark(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(R.mipmap.ic_launcher_monochrome),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
