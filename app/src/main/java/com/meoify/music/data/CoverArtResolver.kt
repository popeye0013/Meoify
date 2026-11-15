package com.meoify.music.data

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the original cover art for a track.
 *
 * YouTube Music rows already carry the square album cover, but tracks that come
 * from the player, radio queues or older library entries can point at a
 * music-video still (`i.ytimg.com/vi/…/hqdefault.jpg`). For those the album is
 * fetched once and its high-resolution square cover is used instead, so the
 * app never shows a cropped video frame where real artwork exists.
 */
class CoverArtResolver(private val repository: MusicRepository) {

    private val cache = ConcurrentHashMap<String, String>()

    /** Best cover for [track], or the upgraded original thumbnail as a fallback. */
    suspend fun cover(track: Track): String? {        cache[track.id]?.let { return it }

        val fallback = Artwork.highRes(track.thumbnailUrl)
        val albumId = track.albumId?.takeIf { it.isNotBlank() }
        val needsAlbumArt = albumId != null &&
            (track.thumbnailUrl.isNullOrBlank() || Artwork.isVideoStill(track.thumbnailUrl))

        if (needsAlbumArt) {
            val albumArt = runCatching { repository.albumArtwork(albumId!!) }.getOrNull()
            if (!albumArt.isNullOrBlank()) {
                remember(track.id, albumArt)
                return albumArt
            }
        }

        if (fallback != null) remember(track.id, fallback)
        return fallback
    }

    /** Already-resolved cover for [trackId], without touching the network. */
    fun cached(trackId: String): String? = cache[trackId]

    private fun remember(trackId: String, url: String) {
        if (cache.size > 600) cache.clear()
        cache[trackId] = url
    }
}
