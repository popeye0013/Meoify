package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meoify.music.data.model.PlaylistEdit

/**
 * The full YouTube Music playlist editor: profile (cover), title, description,
 * privacy and the comment policy. Local playlists only expose the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorSheet(
    title: String,
    description: String,
    privacy: String,
    comments: String,
    artworkUrl: String?,
    editableDescription: Boolean,
    editablePrivacy: Boolean,
    editableComments: Boolean,
    saving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (PlaylistEdit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var titleValue by remember { mutableStateOf(title) }
    var descriptionValue by remember { mutableStateOf(description) }
    var privacyValue by remember { mutableStateOf(normalizePrivacy(privacy)) }
    var commentsValue by remember { mutableStateOf(normalizeComments(comments)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Edit playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Profile",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    ArtworkImage(
                        url = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(92.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = titleValue,
                onValueChange = { titleValue = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (editableDescription) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descriptionValue,
                    onValueChange = { descriptionValue = it },
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (editablePrivacy) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivacyChip(
                        label = "Public",
                        selected = privacyValue == "PUBLIC",
                        onClick = { privacyValue = "PUBLIC" },
                    )
                    PrivacyChip(
                        label = "Unlisted",
                        selected = privacyValue == "UNLISTED",
                        onClick = { privacyValue = "UNLISTED" },
                    )
                    PrivacyChip(
                        label = "Private",
                        selected = privacyValue == "PRIVATE",
                        onClick = { privacyValue = "PRIVATE" },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (privacyValue) {
                        "PUBLIC" -> "Anyone can search for and view"
                        "UNLISTED" -> "Anyone with the link can view"
                        else -> "Only you can view"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editableComments) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommentChip(
                        label = "On",
                        selected = commentsValue == "ON",
                        onClick = { commentsValue = "ON" },
                    )
                    CommentChip(
                        label = "Paused",
                        selected = commentsValue == "PAUSED",
                        onClick = { commentsValue = "PAUSED" },
                    )
                    CommentChip(
                        label = "Off",
                        selected = commentsValue == "OFF",
                        onClick = { commentsValue = "OFF" },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(
                        PlaylistEdit(
                            title = titleValue.trim(),
                            description = descriptionValue.trim(),
                            privacy = privacyValue,
                            comments = commentsValue,
                        ),
                    )
                },
                enabled = titleValue.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacyChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun CommentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun normalizePrivacy(value: String?): String = when (value?.uppercase()) {
    "PUBLIC" -> "PUBLIC"
    "UNLISTED" -> "UNLISTED"
    else -> "PRIVATE"
}

private fun normalizeComments(value: String?): String = when (value?.uppercase()) {
    "PAUSED" -> "PAUSED"
    "OFF" -> "OFF"
    else -> "ON"
}
