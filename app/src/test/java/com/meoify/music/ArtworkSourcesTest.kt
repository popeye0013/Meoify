package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.AppleArtwork
import com.meoify.music.data.remote.SpotifyArtwork
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSourcesTest {

    private val track = Track(
        id = "abc",
        title = "Shape of You",
        artist = "Ed Sheeran",
        album = "Divide (Deluxe)",
        durationMs = 233_713L,
    )

    private val spotify = SpotifyArtwork(OkHttpClient())
    private val apple = AppleArtwork(OkHttpClient())

    @Test
    fun spotifySearchParsesTheLargestAlbumImage() {
        val body = """
            {"tracks":{"items":[
              {"name":"Shape of You","duration_ms":233713,
               "artists":[{"name":"Ed Sheeran"}],
               "album":{"name":"Divide","images":[
                 {"url":"https://i.scdn.co/image/small","width":64,"height":64},
                 {"url":"https://i.scdn.co/image/large","width":640,"height":640}
               ]}},
              {"name":"Shape of You","duration_ms":233000,
               "artists":[{"name":"Karaoke Kings"}],
               "album":{"name":"Karaoke Hits","images":[
                 {"url":"https://i.scdn.co/image/karaoke","width":640,"height":640}
               ]}}
            ]}}
        """.trimIndent()

        assertEquals("https://i.scdn.co/image/large", spotify.parseSearch(body, track))
    }

    @Test
    fun spotifyTokenIsReadFromTheEmbeddedPlayerPage() {
        val html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"settings":{"session":{"accessToken":"BQanonymous","accessTokenExpirationTimestampMs":1700000000000}}}}}}</script>
            </body></html>
        """.trimIndent()

        assertEquals("BQanonymous" to 1_700_000_000_000L, spotify.parseToken(html))
    }

    @Test
    fun appleSearchUpscalesArtworkAndSkipsKaraoke() {
        val body = """
            {"resultCount":2,"results":[
              {"trackName":"Shape of You","artistName":"Ed Sheeran",
               "collectionName":"Divide (Deluxe)","trackTimeMillis":233713,
               "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/abc/100x100bb.jpg"},
              {"trackName":"Shape of You","artistName":"Karaoke Kings",
               "collectionName":"Karaoke Hits","trackTimeMillis":233000,
               "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/xyz/100x100bb.jpg"}
            ]}
        """.trimIndent()

        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/abc/1200x1200bb.jpg",
            apple.parseSearch(body, track),
        )
    }

    @Test
    fun malformedResponsesAreIgnored() {
        assertEquals(null, spotify.parseSearch("not json", track))
        assertEquals(null, apple.parseSearch("{}", track))
        assertEquals(null, spotify.parseToken("<html></html>"))
    }
}
