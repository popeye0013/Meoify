package com.meoify.music.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meoify.music.data.model.DownloadPreferences
import com.meoify.music.data.model.DownloadQueueItem
import com.meoify.music.data.model.DownloadedTrack
import com.meoify.music.data.model.TrackedPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.downloadStore by preferencesDataStore(name = "meoify_downloads")

/**
 * Persists offline tracks, the download queue and download preferences.
 *
 * Meoify uses DataStore + JSON everywhere else, so downloads follow the same
 * pattern instead of adding a database dependency to a single-module app.
 */
class DownloadStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val tracks = stringPreferencesKey("downloaded_tracks")
        val queue = stringPreferencesKey("download_queue")
        val trackedPlaylists = stringPreferencesKey("tracked_download_playlists")
        val maxOfflineMiB = intPreferencesKey("max_offline_mib")
        val autoDownloadOnLike = booleanPreferencesKey("auto_download_on_like")
    }

    private val data = context.downloadStore.data.catch { error ->
        if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
    }

    val downloads: StateFlow<List<DownloadedTrack>> = data
        .map { prefs -> decode<DownloadedTrack>(prefs[Keys.tracks]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val queue: StateFlow<List<DownloadQueueItem>> = data
        .map { prefs -> decode<DownloadQueueItem>(prefs[Keys.queue]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val trackedPlaylists: StateFlow<List<TrackedPlaylist>> = data
        .map { prefs -> decode<TrackedPlaylist>(prefs[Keys.trackedPlaylists]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val preferences: StateFlow<DownloadPreferences> = data
        .map { prefs ->
            DownloadPreferences(
                maxOfflineMiB = prefs[Keys.maxOfflineMiB] ?: 0,
                autoDownloadOnLike = prefs[Keys.autoDownloadOnLike] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, DownloadPreferences())

    suspend fun currentDownloads(): List<DownloadedTrack> = downloads.first()
    suspend fun currentQueue(): List<DownloadQueueItem> = queue.first()
    suspend fun currentPreferences(): DownloadPreferences = preferences.first()

    fun isDownloaded(id: String): Flow<Boolean> = downloads.map { entries -> entries.any { it.track.id == id } }

    suspend fun setQueue(items: List<DownloadQueueItem>) {
        context.downloadStore.edit { it[Keys.queue] = json.encodeToString(items) }
    }

    suspend fun updateQueue(transform: (List<DownloadQueueItem>) -> List<DownloadQueueItem>) {
        context.downloadStore.edit { prefs ->
            val current = decode<DownloadQueueItem>(prefs[Keys.queue])
            prefs[Keys.queue] = json.encodeToString(transform(current))
        }
    }

    suspend fun upsert(entry: DownloadedTrack) {
        context.downloadStore.edit { prefs ->
            val current = decode<DownloadedTrack>(prefs[Keys.tracks])
            val updated = listOf(entry) + current.filterNot { it.track.id == entry.track.id }
            prefs[Keys.tracks] = json.encodeToString(updated)
        }
    }

    suspend fun removeDownload(id: String) {
        context.downloadStore.edit { prefs ->
            val current = decode<DownloadedTrack>(prefs[Keys.tracks])
            prefs[Keys.tracks] = json.encodeToString(current.filterNot { it.track.id == id })
        }
    }

    suspend fun touch(id: String, at: Long) {
        context.downloadStore.edit { prefs ->
            val current = decode<DownloadedTrack>(prefs[Keys.tracks])
            prefs[Keys.tracks] = json.encodeToString(current.map {
                if (it.track.id == id) it.copy(lastAccessedAt = at) else it
            })
        }
    }

    suspend fun setTrackedPlaylists(playlists: List<TrackedPlaylist>) {
        context.downloadStore.edit { it[Keys.trackedPlaylists] = json.encodeToString(playlists) }
    }

    fun setMaxOfflineMiB(mib: Int) = scope.launch {
        context.downloadStore.edit { it[Keys.maxOfflineMiB] = mib.coerceIn(0, 8192) }
    }

    fun setAutoDownloadOnLike(enabled: Boolean) = scope.launch {
        context.downloadStore.edit { it[Keys.autoDownloadOnLike] = enabled }
    }

    private inline fun <reified T> decode(raw: String?): List<T> =
        raw?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
}
