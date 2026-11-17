package com.meoify.music.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meoify.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.playbackStore by preferencesDataStore(name = "meoify_playback")

/**
 * What was playing when the app last went away.
 *
 * Only the catalogue data is stored, never a stream URL: YouTube links expire,
 * so the track is re-resolved when playback resumes. A single snapshot is
 * enough — it is only used to put the last song back on screen after the
 * process or the playback service was stopped.
 */
@Serializable
data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val positionMs: Long = 0L,
    val sourceTitle: String = "YouTube Music",
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val savedAtMs: Long = 0L,
)

/**
 * Persists the last playback session so reopening Meoify shows the song that
 * was playing, even when the service was stopped in the background. Starting
 * playback is always the person's choice: restoring never auto-plays.
 */
class PlaybackStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val snapshot = stringPreferencesKey("last_playback")
    }

    val snapshot: Flow<PlaybackSnapshot?> = context.playbackStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> decode(prefs[Keys.snapshot]) }

    fun save(snapshot: PlaybackSnapshot) = scope.launch {
        runCatching {
            context.playbackStore.edit { prefs ->
                prefs[Keys.snapshot] = json.encodeToString(snapshot)
            }
        }
    }

    fun clear() = scope.launch {
        runCatching { context.playbackStore.edit { it.remove(Keys.snapshot) } }
    }

    private fun decode(raw: String?): PlaybackSnapshot? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString<PlaybackSnapshot>(text) }.getOrNull()
    }
}
