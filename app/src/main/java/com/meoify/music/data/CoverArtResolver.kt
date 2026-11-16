package com.meoify.music.data

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the original cover art for a track.
 *
 * A song's artwork must come from music, not from its video. YouTube Music rows
 * normally carry the square album cover, but tracks from the player, radio
 * queues or older library entries can point at a music-video still
 * (`i.ytimg.com/vi/…/hqdefault.jpg`). For those the album is fetched once; when
 * that fails the song is looked up in Spotify's music catalogue (with Apple's
 * catalogue as a keyless backup). A video frame is never used as song artwork,
 * so the worst case is a neutral placeholder rather than a wrong image.
 */
class CoverArtResolver(private val repository: MusicRepository) {

    private val cache = ConcurrentHashMap<String, String>()

    /** Best cover for [track], or null when no music artwork can be found. */
    suspend fun cover(track: Track): String? {
        cache[track.id]?.let { return it }

        Artwork.songArt(track.thumbnailUrl)?.let { direct ->
            remember(track.id, direct)
            return direct
        }

        val albumId = track.albumId?.takeIf { it.isNotBlank() }
        if (albumId != null) {
            val albumArt = runCatching { repository.albumArtwork(albumId) }.getOrNull()
            if (!albumArt.isNullOrBlank()) {
                remember(track.id, albumArt)
                return albumArt
            }
        }

        val musicArt = runCatching { repository.musicArtwork(track) }.getOrNull()
        if (!musicArt.isNullOrBlank()) {
            remember(track.id, musicArt)
            return musicArt
        }

        return null
    }

    /** Already-resolved cover for [trackId], without touching the network. */
    fun cached(trackId: String): String? = cache[trackId]

    private fun remember(trackId: String, url: String) {
        if (cache.size > 600) cache.clear()
        cache[trackId] = url
    }
}
