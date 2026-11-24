package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meoify.music.data.model.Track
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalSignedIn
import com.meoify.music.ui.navigation.LocalYtLoading
import com.meoify.music.ui.navigation.LocalYtPlaylists

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val library = LocalLibrary.current
    val ytPlaylists = LocalYtPlaylists.current
    val signedIn = LocalSignedIn.current
    val ytLoading = LocalYtLoading.current
    val actions = LocalAppActions.current
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun createPlaylist() {
        val name = newName.trim()
        if (name.isBlank()) return
        actions.createPlaylistWithTrack(name, track)
        onDismiss()
    }

    // Put the field near the top and focus it as soon as the row is tapped, so
    // the keyboard never covers what the person is typing.
    LaunchedEffect(showCreate) {
        if (showCreate) runCatching { nameFocus.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (showCreate) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Playlist name") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { createPlaylist() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocus),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    showCreate = false
                                    newName = ""
                                },
                            ) { Text("Cancel") }
                            Button(
                                onClick = { createPlaylist() },
                                enabled = newName.isNotBlank(),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text("Create & add")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val empty = if (signedIn) ytPlaylists.isEmpty() else library.playlists.isEmpty()
            when {
                empty -> Text(
                    text = when {
                        signedIn && ytLoading -> "Loading your YouTube Music playlists…"
                        signedIn -> "No playlists yet. Create one below — it will be saved to YouTube Music."
                        else -> "No playlists yet. Create one below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                signedIn -> PlaylistChoiceList(
                    items = ytPlaylists.map {
                        PlaylistChoice(
                            id = it.id,
                            name = it.title,
                            subtitle = "YouTube Music",
                            containsTrack = false,
                            artworkUrl = it.thumbnailUrl,
                        )
                    },
                    maxHeight = if (showCreate) 220.dp else 340.dp,
                    onPick = { id -> actions.addToPlaylist(id, track); onDismiss() },
                )

                else -> PlaylistChoiceList(
                    items = library.playlists.map { playlist ->
                        PlaylistChoice(
                            id = playlist.id,
                            name = playlist.name,
                            subtitle = "${playlist.tracks.size} song${if (playlist.tracks.size == 1) "" else "s"}",
                            containsTrack = playlist.tracks.any { it.id == track.id },
                            artworkUrl = playlist.artworkUrl,
                        )
                    },
                    maxHeight = if (showCreate) 220.dp else 340.dp,
                    onPick = { id -> actions.addToPlaylist(id, track); onDismiss() },
                )
            }

            if (!showCreate) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreate = true },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "New playlist",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private data class PlaylistChoice(
    val id: String,
    val name: String,
    val subtitle: String,
    val containsTrack: Boolean,
    val artworkUrl: String? = null,
)

@Composable
private fun PlaylistChoiceList(
    items: List<PlaylistChoice>,
    maxHeight: Dp = 340.dp,
    onPick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.artworkUrl.isNullOrBlank()) {
                            Icon(
                                Icons.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            ArtworkImage(
                                url = item.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.containsTrack) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Already in this playlist",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    signedIn: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (signedIn) "New YouTube Music playlist" else "New playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (signedIn) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Created as a private playlist in your YouTube Music account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
