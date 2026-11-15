package com.meoify.music.data

import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.model.MediaCard
import com.meoify.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps Meoify's library in step with the signed-in YouTube Music account.
 *
 * While signed in, YouTube Music is the source of truth: playlists are read
 * straight from the account, likes are written through to the account, and the
 * local liked list is kept only as a mirror so the rest of the UI (hearts,
 * Liked Songs, stats) keeps working the same way.
 */
class YtLibrarySync(
    private val repository: MusicRepository,
    private val library: LibraryStore,
) {

    data class State(
        val signedIn: Boolean = false,
        val loading: Boolean = false,
        val playlists: List<MediaCard> = emptyList(),
        val error: String? = null,
        val ready: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Loads the account library once per session unless already loaded. */
    fun ensureLoaded() {
        scope.launch { sync(force = false) }
    }

    /** Re-reads the account library in the background. */
    fun refresh() {
        scope.launch { sync(force = true) }
    }

    /** Drops the account view after signing out. */
    fun reset() {
        _state.value = State(signedIn = false, ready = true)
    }

    private suspend fun sync(force: Boolean) = mutex.withLock {
        if (!repository.signedIn) {
            if (_state.value.signedIn || !_state.value.ready) {
                _state.value = State(signedIn = false, ready = true)
            }
            return@withLock
        }

        val current = _state.value
        if (!force && current.signedIn && current.ready) return@withLock

        _state.value = current.copy(signedIn = true, loading = true, error = null)

        var error: String? = null
        var playlists: List<MediaCard> = current.playlists
        var liked: List<Track>? = null
        try {
            coroutineScope {
                val likedJob = async { repository.likedSongs() }
                playlists = repository.libraryPlaylists()
                    .filterNot { it.id == "LM" || it.id == "SE" }
                liked = likedJob.await()
            }
        } catch (t: Throwable) {
            error = t.message ?: "Couldn't sync your YouTube Music library."
        }

        liked?.let { library.setLiked(it) }

        _state.value = State(
            signedIn = true,
            loading = false,
            playlists = playlists,
            error = error,
            ready = true,
        )
    }

    /** Adds or removes [track] from the account's Liked music. */
    suspend fun setLiked(track: Track, liked: Boolean): Boolean {
        if (!repository.signedIn) return false

        // Update the mirror first so the heart responds instantly, then write
        // through to YouTube Music and roll back if the request fails.
        val current = library.current().liked
        val updated = if (liked) {
            if (current.any { it.id == track.id }) current else listOf(track) + current
        } else {
            current.filterNot { it.id == track.id }
        }
        library.setLiked(updated)

        val ok = repository.setSongLiked(track.id, liked)
        if (!ok) library.setLiked(current)
        return ok
    }

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): String? {
        val id = repository.createPlaylist(name, tracks.map { it.id }) ?: return null
        sync(force = true)
        return id
    }

    suspend fun deletePlaylist(id: String): Boolean {
        val ok = repository.deletePlaylist(id)
        if (ok) {
            _state.value = _state.value.copy(
                playlists = _state.value.playlists.filterNot { it.id == id },
            )
            scope.launch { sync(force = true) }
        }
        return ok
    }

    suspend fun renamePlaylist(id: String, name: String): Boolean {
        val ok = repository.renamePlaylist(id, name)
        if (ok) {
            _state.value = _state.value.copy(
                playlists = _state.value.playlists.map {
                    if (it.id == id) it.copy(title = name.trim()) else it
                },
            )
            scope.launch { sync(force = true) }
        }
        return ok
    }

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        val ok = repository.addSongToPlaylist(playlistId, track.id)
        if (ok) scope.launch { sync(force = true) }
        return ok
    }
}
