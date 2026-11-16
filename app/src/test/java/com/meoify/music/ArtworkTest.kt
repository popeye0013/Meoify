package com.meoify.music

import com.meoify.music.data.remote.Artwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun videoStillsUseAResolutionThatAlwaysExists() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            Artwork.highRes("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            Artwork.highRes("https://i.ytimg.com/vi/abc/maxresdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
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
    fun songsNeverUseAVideoStill() {
        assertNull(Artwork.songArt("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1200-h1200",
            Artwork.songArt("https://lh3.googleusercontent.com/abc=w60-h60"),
        )
    }

    @Test
    fun videoStillsOfferALargerFrameFirst() {
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
                "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            ),
            Artwork.candidates("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
        assertEquals(
            listOf("https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj"),
            Artwork.candidates("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj"),
        )
        assertTrue(Artwork.candidates(null).isEmpty())
    }

    @Test
    fun blankUrlsStayNull() {
        assertEquals(null, Artwork.highRes(null))
        assertEquals(null, Artwork.highRes(""))
        assertEquals(null, Artwork.songArt(null))
        assertEquals(null, Artwork.songArt(""))
    }
}
