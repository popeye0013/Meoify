package com.meoify.music.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.model.LocalPlaylist
import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single playlist inside a backup file. Ids are regenerated on restore. */
@Serializable
data class BackupPlaylist(
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = 0L,
)

/** The local (signed-out) profile: display name and an embedded profile picture. */
@Serializable
data class BackupProfile(
    val name: String? = null,
    val avatarBase64: String? = null,
)

/**
 * The `.json` file written by Meoify. Kept small and human readable, with a
 * format marker so a random JSON file can't be restored by mistake.
 */
@Serializable
data class MeoifyBackup(
    val format: String = "meoify-backup",
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val profile: BackupProfile? = null,
    val playlists: List<BackupPlaylist> = emptyList(),
)

/**
 * True only when [text] is JSON with the Meoify backup marker at the top level.
 * Checked before decoding so an unrelated JSON file can't replace a library.
 */
internal fun looksLikeMeoifyBackup(text: String): Boolean = runCatching {
    Json.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.contentOrNull == "meoify-backup"
}.getOrDefault(false)

data class RestoreResult(
    val playlists: Int,
    val profileName: String?,
    val restoredAvatar: Boolean,
)

/**
 * Exports and restores local playlists and the local profile as a single JSON
 * document, using the Storage Access Framework so no storage permission is
 * needed.
 */
class BackupManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val library: LibraryStore,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Suggested file name for the system "create document" screen. */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "meoify-backup-$stamp.json"
    }

    /** Writes the current library and profile to [uri]. Returns the playlist count. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val current = settings.current()
        val data = library.current()

        val avatar = current.localAvatarPath?.let { path ->
            runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()
        }

        val backup = MeoifyBackup(
            profile = BackupProfile(
                name = current.localProfileName,
                avatarBase64 = avatar?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
            ),
            playlists = data.playlists.map { playlist ->
                BackupPlaylist(
                    name = playlist.name,
                    tracks = playlist.tracks,
                    createdAt = playlist.createdAt,
                )
            },
        )

        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Couldn't open the selected file for writing")
        stream.use { it.write(json.encodeToString(backup).toByteArray()) }
        backup.playlists.size
    }

    /** Replaces local playlists and the local profile with the contents of [uri]. */
    suspend fun restoreFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().decodeToString()
        } ?: error("Couldn't open the selected file")

        require(looksLikeMeoifyBackup(text)) { "This file isn't a Meoify backup." }
        val backup = runCatching { json.decodeFromString<MeoifyBackup>(text) }
            .getOrElse { throw IllegalArgumentException("This file isn't a Meoify backup.") }

        val stamp = System.currentTimeMillis()
        val playlists = backup.playlists.mapIndexed { index, playlist ->
            LocalPlaylist(
                id = "local_${stamp}_${index}_${(100..999).random()}",
                name = playlist.name.trim().ifBlank { "Playlist ${index + 1}" },
                tracks = playlist.tracks,
                createdAt = playlist.createdAt,
            )
        }
        library.replacePlaylists(playlists)

        var restoredAvatar = false
        val profile = backup.profile
        if (profile != null) {
            settings.setLocalProfileName(profile.name)
            val avatarPath = profile.avatarBase64?.let { encoded ->
                runCatching {
                    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                    val dir = File(context.filesDir, "profile").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, "avatar_restore_$stamp.img")
                    file.writeBytes(bytes)
                    file.absolutePath
                }.getOrNull()
            }
            if (avatarPath != null) {
                settings.setLocalAvatarPath(avatarPath)
                restoredAvatar = true
            }
        }

        RestoreResult(
            playlists = playlists.size,
            profileName = profile?.name,
            restoredAvatar = restoredAvatar,
        )
    }
}
