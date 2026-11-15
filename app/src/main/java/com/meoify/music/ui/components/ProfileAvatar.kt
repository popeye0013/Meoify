package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.meoify.music.ui.theme.ArtworkColorCache

/**
 * Account avatar: the real YouTube/GitHub picture when we have one, otherwise a
 * tinted gradient circle with the person's initials.
 */
@Composable
fun ProfileAvatar(
    name: String?,
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    initials: String? = null,
    ring: Boolean = false,
) {
    val seed = name?.ifBlank { null } ?: "meoify"
    val pair = ArtworkColorCache.gradientPair(seed)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second)))
            .then(
                if (ring) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name?.let { "$it's profile picture" } ?: "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else if (!initials.isNullOrBlank()) {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
