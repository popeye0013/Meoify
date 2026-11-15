package com.meoify.music

import com.meoify.music.data.remote.Artwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTest {

    @Test
    fun googleArtworkIsUpscaled() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj",
            Artwork.highRes("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        assertEquals(
            "https://yt3.ggpht.com/abc=s1200",
            Artwork.highRes("https://yt3.ggpht.com/abc=s120"),
        )
    }

    @Test
    fun videoStillsPreferTheLargestFrame() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            Artwork.highRes("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            Artwork.highRes("https://i.ytimg.com/vi/abc/default.jpg"),
        )
    }

    @Test
    fun detectsVideoStills() {
        assertTrue(Artwork.isVideoStill("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertFalse(Artwork.isVideoStill("https://lh3.googleusercontent.com/abc=w60-h60"))
        assertFalse(Artwork.isVideoStill(null))
    }

    @Test
    fun blankUrlsStayNull() {
        assertEquals(null, Artwork.highRes(null))
        assertEquals(null, Artwork.highRes(""))
    }
}
