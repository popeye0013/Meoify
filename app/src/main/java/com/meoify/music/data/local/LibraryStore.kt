package com.meoify.music.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meoify.music.data.model.LibraryData
import com.meoify.music.data.model.ListeningEvent
import com.meoify.music.data.model.LocalPlaylist
import com.meoify.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.libraryStore by preferencesDataStore(name = "meoify_library")

class LibraryStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val liked = stringPreferencesKey("liked_tracks")
        val playlists = stringPreferencesKey("playlists")
        val recent = stringPreferencesKey("recent_tracks")
        val searches = stringPreferencesKey("recent_searches")
        val historyEvents = stringPreferencesKey("listening_history_events")
    }

    val library: Flow<LibraryData> = context.libraryStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            LibraryData(
                liked = decodeTracks(prefs[Keys.liked]),
                playlists = decodePlaylists(prefs[Keys.playlists]),
                recent = decodeTracks(prefs[Keys.recent]),
                recentSearches = decodeStrings(prefs[Keys.searches]),
                listeningHistory = decodeListeningEvents(prefs[Keys.historyEvents]),
            )
        }

    suspend fun current(): LibraryData = library.first()

    /** Replaces every local playlist (used when restoring a backup). */
    fun replacePlaylists(playlists: List<LocalPlaylist>) = scope.launch {
        context.libraryStore.edit { prefs ->
            prefs[Keys.playlists] = json.encodeToString(playlists)
        }
    }

    /**
     * Replaces the whole liked list. Used when the signed-in account's Liked
     * music is mirrored from YouTube Music, which is the source of truth while
     * the person is signed in.
     */
    suspend fun setLiked(tracks: List<Track>) {
        context.libraryStore.edit { prefs ->
            prefs[Keys.liked] = json.encodeToString(tracks.distinctBy { it.id }.take(500))
        }
    }

    fun toggleLike(track: Track) = scope.launch {
        context.libraryStore.edit { prefs ->
            val current = decodeTracks(prefs[Keys.liked])
            val updated = if (current.any { it.id == track.id }) {
                current.filterNot { it.id == track.id }
            } else {
                listOf(track) + current
            }
            prefs[Keys.liked] = json.encodeToString(updated.take(500))
        }
    }

    fun addRecent(track: Track) = scope.launch {
        context.libraryStore.edit { prefs ->
            val current = decodeTracks(prefs[Keys.recent])
            val updated = (listOf(track) + current.filterNot { it.id == track.id }).take(40)
            prefs[Keys.recent] = json.encodeToString(updated)
        }
    }

    /** Records a genuine listening session with verified listened time. */
    fun recordListeningEvent(event: ListeningEvent) = scope.launch {
        context.libraryStore.edit { prefs ->
            val existing = decodeListeningEvents(prefs[Keys.historyEvents])
            val updated = (listOf(event) + existing).take(5000)
            prefs[Keys.historyEvents] = json.encodeToString(updated)
        }
    }

    fun clearListeningHistory() = scope.launch {
        context.libraryStore.edit { it[Keys.historyEvents] = "[]" }
    }

    fun clearRecent() = scope.launch {
        context.libraryStore.edit { it[Keys.recent] = "[]" }
    }

    fun addRecentSearch(query: String) = scope.launch {
        val clean = query.trim()
        if (clean.length < 2) return@launch
        context.libraryStore.edit { prefs ->
            val current = decodeStrings(prefs[Keys.searches])
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, true) }).take(8)
            prefs[Keys.searches] = json.encodeToString(updated)
        }
    }

    fun clearRecentSearches() = scope.launch {
        context.libraryStore.edit { it[Keys.searches] = "[]" }
    }

    fun createPlaylist(name: String): String {
        val id = "local_${System.currentTimeMillis()}_${(100..999).random()}"
        scope.launch {
            context.libraryStore.edit { prefs ->
                val playlists = decodePlaylists(prefs[Keys.playlists])
                val playlist = LocalPlaylist(
                    id = id,
                    name = name.trim().ifBlank { "New playlist" },
                    createdAt = System.currentTimeMillis(),
                )
                prefs[Keys.playlists] = json.encodeToString(listOf(playlist) + playlists)
            }
        }
        return id
    }

    /** Creates a playlist with tracks in one edit (used by "Save to library"). */
    fun savePlaylist(name: String, tracks: List<Track>): String {
        val id = "local_${System.currentTimeMillis()}_${(100..999).random()}"
        scope.launch {
            context.libraryStore.edit { prefs ->
                val playlists = decodePlaylists(prefs[Keys.playlists])
                val playlist = LocalPlaylist(
                    id = id,
                    name = name.trim().ifBlank { "Saved playlist" },
                    tracks = tracks,
                    createdAt = System.currentTimeMillis(),
                )
                prefs[Keys.playlists] = json.encodeToString(listOf(playlist) + playlists)
            }
        }
        return id
    }

    fun deletePlaylist(id: String) = scope.launch {
        context.libraryStore.edit { prefs ->
            val playlists = decodePlaylists(prefs[Keys.playlists]).filterNot { it.id == id }
            prefs[Keys.playlists] = json.encodeToString(playlists)
        }
    }

    fun renamePlaylist(id: String, name: String) = scope.launch {
        context.libraryStore.edit { prefs ->
            val playlists = decodePlaylists(prefs[Keys.playlists]).map {
                if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it
            }
            prefs[Keys.playlists] = json.encodeToString(playlists)
        }
    }

    fun addToPlaylist(id: String, track: Track) = scope.launch {
        context.libraryStore.edit { prefs ->
            val playlists = decodePlaylists(prefs[Keys.playlists]).map { playlist ->
                if (playlist.id == id && playlist.tracks.none { it.id == track.id }) {
                    playlist.copy(tracks = playlist.tracks + track)
                } else {
                    playlist
                }
            }
            prefs[Keys.playlists] = json.encodeToString(playlists)
        }
    }

    fun removeFromPlaylist(id: String, trackId: String) = scope.launch {
        context.libraryStore.edit { prefs ->
            val playlists = decodePlaylists(prefs[Keys.playlists]).map { playlist ->
                if (playlist.id == id) {
                    playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
                } else {
                    playlist
                }
            }
            prefs[Keys.playlists] = json.encodeToString(playlists)
        }
    }

    private fun decodeTracks(raw: String?): List<Track> =
        raw?.let { runCatching { json.decodeFromString<List<Track>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodePlaylists(raw: String?): List<LocalPlaylist> =
        raw?.let { runCatching { json.decodeFromString<List<LocalPlaylist>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeStrings(raw: String?): List<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun decodeListeningEvents(raw: String?): List<ListeningEvent> =
        raw?.let { runCatching { json.decodeFromString<List<ListeningEvent>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
}
