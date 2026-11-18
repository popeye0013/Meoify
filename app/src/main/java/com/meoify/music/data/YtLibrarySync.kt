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
        val likedCount: Int = 0,
        val playedCount: Int = 0,
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

    /**
     * Re-reads only the Played count. Listening updates the account history
     * without changing likes or playlists, so the profile card can stay
     * current without a full library sync.
     */
    fun refreshPlayedCount() {
        scope.launch {
            if (!repository.signedIn) return@launch
            val count = repository.playedCount()
            // A failed read returns 0; keep the last known good number instead
            // of blanking the stat on a flaky connection.
            if (count > 0) {
                _state.value = _state.value.copy(playedCount = count)
            }
        }
    }

    /**
     * Counts a listen that YouTube just accepted. The history page can take a
     * moment to list the new item, so the profile card follows immediately
     * instead of waiting for the next full sync.
     */
    fun notePlayed() {
        val current = _state.value
        if (!current.signedIn || !current.ready) return
        _state.value = current.copy(playedCount = current.playedCount + 1)
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
        var playedCount = current.playedCount
        try {
            coroutineScope {
                // Each part of the account is fetched independently: a failing
                // playlists or likes request must not discard the Played count
                // (and vice versa).
                val likedJob = async { runCatching { repository.likedSongs() } }
                val playedJob = async { runCatching { repository.playedCount() } }
                val playlistsJob = async { runCatching { repository.libraryPlaylists() } }

                val playlistsResult = playlistsJob.await()
                playlistsResult.onSuccess { all ->
                    playlists = all.filterNot { it.id == "LM" || it.id == "SE" }
                }
                val likedResult = likedJob.await()
                likedResult.getOrNull()?.let { liked = it }
                val playedResult = playedJob.await()
                playedResult.onSuccess { playedCount = it }

                val failure = listOfNotNull(
                    playlistsResult.exceptionOrNull(),
                    likedResult.exceptionOrNull(),
                    playedResult.exceptionOrNull(),
                ).firstOrNull()
                if (failure != null) {
                    error = failure.message ?: "Couldn't sync your YouTube Music library."
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: "Couldn't sync your YouTube Music library."
        }

        liked?.let { library.setLiked(it) }

        _state.value = State(
            signedIn = true,
            loading = false,
            playlists = playlists,
            likedCount = liked?.size ?: current.likedCount,
            playedCount = playedCount,
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
        val wasLiked = current.any { it.id == track.id }
        val updated = if (liked) {
            if (wasLiked) current else listOf(track) + current
        } else {
            current.filterNot { it.id == track.id }
        }
        library.setLiked(updated)
        if (wasLiked != liked) {
            _state.value = _state.value.copy(
                likedCount = (_state.value.likedCount + if (liked) 1 else -1).coerceAtLeast(0),
            )
        }

        val ok = repository.setSongLiked(track.id, liked)
        if (!ok) {
            library.setLiked(current)
            if (wasLiked != liked) {
                _state.value = _state.value.copy(
                    likedCount = (_state.value.likedCount + if (liked) -1 else 1).coerceAtLeast(0),
                )
            }
        }
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

    /** Saves the full YouTube Music playlist editor: title, description, privacy and comments. */
    suspend fun editPlaylist(
        id: String,
        title: String,
        description: String,
        privacy: String,
        comments: String,
    ): Boolean {
        val ok = repository.editPlaylist(id, title, description, privacy, comments)
        if (ok) {
            _state.value = _state.value.copy(
                playlists = _state.value.playlists.map { card ->
                    if (matchesPlaylistId(card.id, id)) card.copy(title = title.trim()) else card
                },
            )
            scope.launch { sync(force = true) }
        }
        return ok
    }

    private fun matchesPlaylistId(a: String, b: String): Boolean =
        a.removePrefix("VL") == b.removePrefix("VL")

    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        val ok = repository.addSongToPlaylist(playlistId, track.id)
        if (ok) scope.launch { sync(force = true) }
        return ok
    }
}
