package com.meoify.music.data

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds the original cover art for a track.
 *
 * A song's artwork should come from music, not from its video. YouTube Music
 * rows normally carry the square album cover, but tracks from the player, radio
 * queues or older library entries can point at a music-video still
 * (`i.ytimg.com/vi/…/hqdefault.jpg`). For those the album is fetched once; when
 * that fails the song is looked up in Spotify's music catalogue (with Apple's
 * catalogue as a keyless backup). A video frame is returned only when no
 * catalogue has the song, so the artwork is real whenever it exists.
 */
class CoverArtResolver(private val repository: MusicRepository) {

    private val cache = ConcurrentHashMap<String, String>()

    // One lookup per track at a time. The same song often appears in several
    // home shelves at once, and without this every row starts its own search.
    private val locks = ConcurrentHashMap<String, Mutex>()

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
        Artwork.songArt(track.thumbnailUrl)?.let { return it }

        val albumId = track.albumId?.takeIf { it.isNotBlank() }
        if (albumId != null) {
            val albumArt = runCatching { repository.albumArtwork(albumId) }.getOrNull()
            if (!albumArt.isNullOrBlank()) return albumArt
        }

        val musicArt = runCatching { repository.musicArtwork(track) }.getOrNull()
        if (!musicArt.isNullOrBlank()) return musicArt

        // Last resort: the video frame, or a frame built from the song id.
        return Artwork.still(track.thumbnailUrl) ?: Artwork.stillForVideo(track.id)
    }

    /** Already-resolved cover for [trackId], without touching the network. */
    fun cached(trackId: String): String? = cache[trackId]

    private fun remember(trackId: String, url: String) {
        if (cache.size > 600) cache.clear()
        if (locks.size > 800) locks.clear()
        cache[trackId] = url
    }
}
