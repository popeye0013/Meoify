package com.meoify.music

import com.meoify.music.data.playlistIdFromLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinkParserTest {

    @Test
    fun readsYouTubeMusicQueryLinks() {
        assertEquals(
            "PL1234567890",
            playlistIdFromLink("https://music.youtube.com/playlist?list=PL1234567890&si=abc"),
        )
        assertEquals(
            "PL1234567890",
            playlistIdFromLink("https://www.youtube.com/watch?v=video&list=PL1234567890"),
        )
        assertEquals(
            "PL1234567890",
            playlistIdFromLink("https://music.youtube.com/playlist#list=PL1234567890"),
        )
    }

    @Test
    fun readsBrowseLinksAndEncodedIds() {
        assertEquals(
            "VLPL1234567890",
            playlistIdFromLink("https://music.youtube.com/browse/VLPL1234567890"),
        )
        assertEquals(
            "MPREb_abc123",
            playlistIdFromLink("https://music.youtube.com/browse/MPREb_abc123?feature=share"),
        )
        assertEquals(
            "PL123=456",
            playlistIdFromLink("https://music.youtube.com/playlist?list=PL123%3D456"),
        )
    }

    @Test
    fun acceptsBareIdsAndRejectsOtherLinks() {
        assertEquals("PL1234567890", playlistIdFromLink("PL1234567890"))
        assertEquals("OLAK5uy_n123", playlistIdFromLink("OLAK5uy_n123"))
        assertNull(playlistIdFromLink("https://example.com/playlist"))
    }
}
