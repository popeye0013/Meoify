package com.meoify.music.data

import android.content.Context
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds the original cover art for a track.
 *
 * Sources are tried in this order: Spotify, Apple iTunes, then YouTube Music.
 * A music-video frame is never used — when no catalogue carries the song the
 * cover is simply left unresolved instead of showing a video still. Resolved
 * covers are cached in memory and on disk, so scrolling back over a row and
 * restarting the app do not repeat the network lookup.
 */
class CoverArtResolver(
    private val repository: MusicRepository,
    context: Context? = null,
) {

    private val cache = ConcurrentHashMap<String, String>()

    // One lookup per track at a time. The same song often appears in several
    // home shelves at once, and without this every row starts its own search.
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Restore covers from the previous session. Each entry is stored on its
        // own so saving a new cover is a single small write, not a rewrite of
        // the whole cache on every row of a long list.
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String && value.isNotBlank() &&
                !Artwork.isVideoStill(value)
            ) {
                cache[key.removePrefix(KEY_PREFIX)] = value
            }
        }
    }

    /** Best cover for [track], or null when no music artwork can be found. */
    suspend fun cover(track: Track): String? {
        cache[track.id]?.let { return it }

        val mutex = locks.getOrPut(track.id) { Mutex() }
        return mutex.withLock {
            cache[track.id]?.let { return@withLock it }
            resolve(track)?.let { url ->
                remember(track.id, url)
                url
            }
        }
    }

    private suspend fun resolve(track: Track): String? {
        // Spotify first, then Apple iTunes, then YouTube Music. `musicArtwork`
        // owns that ordering and only ever returns square catalogue covers;
        // YouTube's own row thumbnail is used when nothing else matches.
        val musicArt = runCatching { repository.musicArtwork(track) }.getOrNull()
        if (!musicArt.isNullOrBlank()) return musicArt

        val albumId = track.albumId?.takeIf { it.isNotBlank() }
        if (albumId != null) {
            val albumArt = runCatching { repository.albumArtwork(albumId) }.getOrNull()
            if (!albumArt.isNullOrBlank()) return albumArt
        }

        // The thumbnail on a YouTube Music row is real square album art. A
        // video frame is deliberately not returned here.
        return Artwork.songArt(track.thumbnailUrl)
    }

    /** Already-resolved cover for [trackId], without touching the network. */
    fun cached(trackId: String): String? = cache[trackId]

    private fun remember(trackId: String, url: String) {
        if (cache.size > MAX_ENTRIES) {
            cache.clear()
            locks.clear()
            prefs?.edit()?.clear()?.apply()
        }
        cache[trackId] = url
        prefs?.edit()?.putString(KEY_PREFIX + trackId, url)?.apply()
    }

    private companion object {
        const val PREFS_NAME = "meoify_cover_art"
        const val KEY_PREFIX = "cover_"
        const val MAX_ENTRIES = 600
    }
}
