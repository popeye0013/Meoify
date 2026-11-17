package com.meoify.music.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meoify.music.util.LyricShare
import com.meoify.music.util.ShareCardKind
import com.meoify.music.util.ShareCardOptions
import kotlinx.coroutines.launch

/**
 * Preview-and-share sheet used for both the now-playing song card and the
 * selectable lyric card. The preview is rendered with the same code that
 * produces the exported image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(
    title: String,
    artist: String,
    artworkUrl: String?,
    lines: List<String> = emptyList(),
    initialSelected: Set<Int> = emptySet(),
    songMode: Boolean = false,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeLines = lines.filter { it.isNotBlank() }
    var selected by remember {
        mutableStateOf(
            initialSelected.filter { it in safeLines.indices }.toSet()
                .ifEmpty { setOf(0.coerceAtMost(safeLines.lastIndex)) },
        )
    }
    val selectedLines = safeLines.filterIndexed { index, _ -> index in selected }
    val options = ShareCardOptions(
        kind = if (songMode) ShareCardKind.SONG else ShareCardKind.LYRICS,
        lines = selectedLines,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
    )

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(options) {
        preview = LyricShare.renderCard(context, options, 360, 640)
    }

    fun toggle(index: Int) {
        selected = if (index in selected) {
            if (selected.size > 1) selected - index else selected
        } else {
            selected + index
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Text(
                text = if (songMode) "Share song" else "Share lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = if (songMode) {
                    "A card with the artwork and track details."
                } else {
                    "Choose the lines you want on your card."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = preview
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Share card preview",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(color = accent)
                }
            }

            if (!songMode && safeLines.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Lines",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                ) {
                    itemsIndexed(safeLines) { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { toggle(index) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = index in selected,
                                onCheckedChange = { toggle(index) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            LyricShare.shareToInstagramStory(context, options)
                            onDismiss()
                        }
                    },
                    enabled = preview != null && (songMode || selectedLines.isNotEmpty()),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Instagram story")
                }
                Button(
                    onClick = {
                        scope.launch {
                            LyricShare.shareToChat(context, options)
                            onDismiss()
                        }
                    },
                    enabled = preview != null && (songMode || selectedLines.isNotEmpty()),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share to chat")
                }
            }
        }
    }
}
