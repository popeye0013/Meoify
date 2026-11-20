package com.meoify.music

import com.meoify.music.data.remote.SpotifyPlaylistClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistParserTest {

    private val client = SpotifyPlaylistClient(OkHttpClient())

    @Test
    fun extractsPlaylistIds() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            client.playlistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"),
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            client.playlistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertNull(client.playlistId("https://music.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun parsesEmbedNextData() {
        val nextData = """
            {"props":{"pageProps":{"state":{"data":{"entity":{
              "name":"Road trip",
              "trackList":[
                {"title":"Song One","subtitle":"Artist A,\u00a0Artist B","duration":225000},
                {"title":"Song Two","subtitle":"Artist C","duration":198}
              ]
            }}}}}}
        """.trimIndent()
        val html = """<html><script id="__NEXT_DATA__" type="application/json">$nextData</script></html>"""

        val playlist = client.parse(html)
        assertEquals("Road trip", playlist.name)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Song One", playlist.tracks[0].title)
        assertEquals("Artist A, Artist B", playlist.tracks[0].artist)
        assertEquals(225_000L, playlist.tracks[0].durationMs)
        assertEquals(198_000L, playlist.tracks[1].durationMs)
    }
}
