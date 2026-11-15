package com.meoify.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meoify.music.data.model.Track
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.navigation.LocalSignedIn
import com.meoify.music.ui.navigation.LocalYtLoading
import com.meoify.music.ui.navigation.LocalYtPlaylists

@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val library = LocalLibrary.current
    val ytPlaylists = LocalYtPlaylists.current
    val signedIn = LocalSignedIn.current
    val ytLoading = LocalYtLoading.current
    val actions = LocalAppActions.current
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                val empty = if (signedIn) ytPlaylists.isEmpty() else library.playlists.isEmpty()
                if (empty) {
                    Text(
                        text = when {
                            signedIn && ytLoading -> "Loading your YouTube Music playlists…"
                            signedIn -> "No playlists yet. Create one below — it will be saved to YouTube Music."
                            else -> "No playlists yet. Create one below."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (signedIn) {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(ytPlaylists, key = { it.id }) { card ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        actions.addToPlaylist(card.id, track)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(card.title, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(library.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        actions.addToPlaylist(playlist.id, track)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(playlist.name, modifier = Modifier.weight(1f))
                                if (playlist.tracks.any { it.id == track.id }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (showCreate) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            actions.createPlaylistWithTrack(newName.trim(), track)
                            onDismiss()
                        }
                    }) { Text("Create & add") }
                } else {
                    TextButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New playlist")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
