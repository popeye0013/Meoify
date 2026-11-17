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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.meoify.music.util.LyricShareAspect
import com.meoify.music.util.LyricShareOptions
import com.meoify.music.util.LyricShareTemplate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricShareSheet(
    title: String,
    artist: String,
    artworkUrl: String?,
    lines: List<String>,
    initialSelected: Set<Int> = emptySet(),
    accent: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val safeLines = lines.ifEmpty { listOf(title.ifBlank { "Meoify" }) }

    var selected by remember { mutableStateOf(initialSelected.ifEmpty { setOf(0.coerceAtMost(safeLines.lastIndex)) }) }
    var template by remember { mutableStateOf(LyricShareTemplate.GRADIENT) }
    var aspect by remember { mutableStateOf(LyricShareAspect.STORY) }

    val selectedLines = remember(selected, safeLines) {
        safeLines.filterIndexed { i, _ -> i in selected }
    }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }

    LaunchedEffect(template, aspect, selected) {
        rendering = true
        val opts = LyricShareOptions(
            template = template,
            aspect = aspect,
            lines = selectedLines,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
        )
        val bmp = LyricShare.renderCard(context, opts)
        preview = bmp
        rendering = false
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
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = "Share lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Pick lines and a style, then share to your favourite app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect.width.toFloat() / aspect.height.toFloat())
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator(color = accent)
                }
                if (rendering) {
                    CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Template chips
            Text(
                text = "Style",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricShareTemplate.entries.forEach { t ->
                    FilterChip(
                        selected = template == t,
                        onClick = { template = t },
                        label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Aspect chips
            Text(
                text = "Format",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricShareAspect.entries.forEach { a ->
                    FilterChip(
                        selected = aspect == a,
                        onClick = { aspect = a },
                        label = { Text(a.label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Line selection
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
            ) {
                itemsIndexed(safeLines) { index, line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selected = if (index in selected) selected - index else selected + index
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = index in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + index else selected - index
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = line.ifBlank { "♪" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            LyricShare.shareToInstagramStory(
                                context,
                                LyricShareOptions(
                                    template = template,
                                    aspect = aspect,
                                    lines = selectedLines,
                                    title = title,
                                    artist = artist,
                                    artworkUrl = artworkUrl,
                                ),
                            )
                        }
                    },
                    enabled = selectedLines.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Instagram Story")
                }
                Button(
                    onClick = {
                        scope.launch {
                            LyricShare.shareGeneric(
                                context,
                                LyricShareOptions(
                                    template = template,
                                    aspect = aspect,
                                    lines = selectedLines,
                                    title = title,
                                    artist = artist,
                                    artworkUrl = artworkUrl,
                                ),
                            )
                        }
                    },
                    enabled = selectedLines.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    scope.launch {
                        LyricShare.saveToGallery(
                            context,
                            LyricShareOptions(
                                template = template,
                                aspect = aspect,
                                lines = selectedLines,
                                title = title,
                                artist = artist,
                                artworkUrl = artworkUrl,
                            ),
                        )
                    }
                },
                enabled = selectedLines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save to gallery")
            }
        }
    }
}
