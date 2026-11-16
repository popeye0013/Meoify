package com.meoify.music.data.remote

/**
 * Upgrades artwork URLs to the best resolution the source serves.
 *
 * `lh3.googleusercontent.com` / `yt3.ggpht.com` URLs are the square album and
 * artist covers used by YouTube Music; the `=w…-h…` suffix controls the size.
 * `i.ytimg.com/vi/<id>/…default.jpg` URLs are video stills (4:3 frames from a
 * music video). They are fine for playlist covers, but [songArt] never returns
 * one for a song: songs use album artwork from the music catalogue instead.
 */
object Artwork {

    private val GOOGLE_SIZE = Regex("=w\\d+-h\\d+")
    private val GOOGLE_SQUARE = Regex("=s\\d+(-[a-z0-9]+)*$")
    private val YT_VIDEO_ID = Regex("/vi/([^/]+)/")

    /** True when [url] is a music-video still rather than real cover art. */
    fun isVideoStill(url: String?): Boolean {
        val lower = url?.lowercase() ?: return false
        return lower.contains("ytimg.com") || lower.contains("img.youtube.com")
    }

    /**
     * Cover art for a song, never a video frame.
     *
     * Returns null for video stills so [com.meoify.music.data.CoverArtResolver]
     * can fetch the song's album cover from a music catalogue. General media
     * such as playlist covers should use [highRes] or [candidates] instead.
     */
    fun songArt(url: String?): String? = highRes(url)?.takeUnless { isVideoStill(url) }

    /**
     * URLs for [url], best quality first.
     *
     * Album and artist covers only have one source, but video stills can be
     * served at several sizes. The largest version (`maxresdefault`) does not
     * exist for every upload, so the always-present `hqdefault` follows it and
     * the image simply falls back when the big one is missing.
     */
    fun candidates(url: String?): List<String> {
        val high = highRes(url) ?: return emptyList()
        if (!isVideoStill(high)) return listOf(high)
        val videoId = YT_VIDEO_ID.find(high)?.groupValues?.getOrNull(1) ?: return listOf(high)
        return listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        )
    }

    /** The highest quality version of a cover or still URL. */
    fun highRes(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") ->
                GOOGLE_SIZE.replace(url, "=w1200-h1200")
                    .let { upgraded ->
                        if (upgraded == url) GOOGLE_SQUARE.replace(url, "=s1200") else upgraded
                    }

            isVideoStill(url) -> url
                // maxresdefault is missing for many uploads and leaves a blank
                // tile, so use the size YouTube always generates here. Songs
                // resolve their real album art separately.
                .replace("maxresdefault", "hqdefault")
                .replace("sddefault", "hqdefault")
                .replace("mqdefault", "hqdefault")
                .replace("/default.jpg", "/hqdefault.jpg")

            else -> url
        }
    }
}
