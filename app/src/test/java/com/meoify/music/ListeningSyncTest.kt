package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.ListeningSync
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningSyncTest {

    @Test
    fun buildSvgContainsTitleAndArtist() {
        val track = Track(
            id = "1",
            title = "Hello World",
            artist = "Test Artist",
            thumbnailUrl = "https://example.com/art.jpg",
        )
        val svg = ListeningSync.buildSvg(track)
        assertTrue(svg.contains("Hello World"))
        assertTrue(svg.contains("Test Artist"))
        assertTrue(svg.contains("https://example.com/art.jpg"))
        assertTrue(svg.contains("Meoify"))
    }

    @Test
    fun buildSvgEscapesXml() {
        val track = Track(
            id = "2",
            title = "A & B <tag>",
            artist = "O'Reilly \"quoted\"",
        )
        val svg = ListeningSync.buildSvg(track)
        assertTrue(svg.contains("A &amp; B &lt;tag&gt;"))
        assertTrue(svg.contains("O&apos;Reilly &quot;quoted&quot;"))
    }

    @Test
    fun buildSvgHandlesMissingArtwork() {
        val track = Track(id = "3", title = "No Art", artist = "Nobody")
        val svg = ListeningSync.buildSvg(track)
        assertTrue(svg.contains("No Art"))
        assertTrue(svg.contains("Nobody"))
    }
}
