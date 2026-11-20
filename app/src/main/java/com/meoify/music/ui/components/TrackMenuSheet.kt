package com.meoify.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meoify.music.data.model.Track
import com.meoify.music.ui.navigation.LocalAppActions
import com.meoify.music.ui.navigation.LocalLibrary
import com.meoify.music.ui.theme.ArtworkColorCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    playlistId: String? = null,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val actions = LocalAppActions.current
    val library = LocalLibrary.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val cover = rememberTrackCover(track)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    ArtworkImage(
                        url = cover,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MenuItem(Icons.Filled.Favorite, if (library.isLiked(track.id)) "Remove from Liked Songs" else "Add to Liked Songs") {
                actions.toggleLike(track); onDismiss()
            }
            MenuItem(Icons.Filled.QueueMusic, "Play next") { actions.playNext(track); onDismiss() }
            MenuItem(Icons.Filled.Add, "Add to queue") { actions.addToQueue(track); onDismiss() }
            MenuItem(Icons.Filled.LibraryMusic, "Add to playlist") { onAddToPlaylist(); onDismiss() }
            if (!playlistId.isNullOrBlank() && playlistId != "liked") {
                MenuItem(Icons.Filled.Delete, "Remove from this playlist") {
                    actions.removeFromPlaylist(playlistId, track.id)
                    onDismiss()
                }
            }
            track.albumId?.let { id ->
                MenuItem(Icons.Filled.LibraryMusic, "Go to album") { actions.openAlbum(id); onDismiss() }
            }
            track.artistId?.let { id ->
                MenuItem(Icons.Filled.Person, "Go to artist") { actions.openArtist(id); onDismiss() }
            }
            MenuItem(Icons.Filled.MoreHoriz, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "https://music.youtube.com/watch?v=${track.id}\n\n${track.title} — ${track.artist}",
                    )
                }
                context.startActivity(Intent.createChooser(send, "Share song"))
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
