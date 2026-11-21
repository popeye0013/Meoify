package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackRowParserTest {

    private fun parse(json: String) =
        YTMusicParser.parseTrack(Json.parseToJsonElement(json).jsonObject["musicResponsiveListItemRenderer"]!!.jsonObject)

    private fun row(subtitle: String, thirdColumn: String? = null) = """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"NJAv_7lHUIU"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Kesariya (From \"Brahmastra\")"}
            ]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[$subtitle]}}}
            ${thirdColumn?.let {
                """,
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$it"}]}}}"""
            }.orEmpty()}
          ],
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/a/song=w120-h120","width":120},
            {"url":"https://lh3.googleusercontent.com/a/song=w544-h544","width":544}
          ]}}}
        }}
    """.trimIndent()

    private val artistRun = """
        {"text":"Arijit Singh","navigationEndpoint":{"browseEndpoint":{
          "browseId":"UCDxKh1gFWeYsqePvgVzmPoQ",
          "browseEndpointContextSupportedConfigs":{
            "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}
          }
        }}}
    """.trimIndent()

    private val albumRun = """
        {"text":"Brahmastra","navigationEndpoint":{"browseEndpoint":{
          "browseId":"MPREb_abc123",
          "browseEndpointContextSupportedConfigs":{
            "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}
          }
        }}}
    """.trimIndent()

    @Test
    fun searchRowsDoNotTreatTheSongLabelAsTheArtist() {
        val track = parse(
            row(
                """{"text":"Song"},{"text":" • "},$artistRun""",
                thirdColumn = "973M plays",
            ),
        )!!
        assertEquals("Arijit Singh", track.artist)
        assertEquals("UCDxKh1gFWeYsqePvgVzmPoQ", track.artistId)
        assertNull(track.album)
        assertEquals("973M plays", track.plays)
    }

    @Test
    fun albumRunsGiveTheAlbumNameAndIdForArtwork() {
        val track = parse(
            row(
                """{"text":"Song"},{"text":" • "},$artistRun,{"text":" • "},$albumRun""",
                thirdColumn = "973M plays",
            ),
        )!!
        assertEquals("Arijit Singh", track.artist)
        assertEquals("Brahmastra", track.album)
        assertEquals("MPREb_abc123", track.albumId)
    }

    @Test
    fun videoRowsUseTheUploaderAsTheArtist() {
        val track = parse(
            row(
                """{"text":"Video"},{"text":" • "},
                   {"text":"Sheraz Khalil","navigationEndpoint":{"browseEndpoint":{
                     "browseId":"UCi96JE9PvFaXgTRGVeSDnBQ",
                     "browseEndpointContextSupportedConfigs":{
                       "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_USER_CHANNEL"}
                     }
                   }}},
                   {"text":" • "},{"text":"162K views"},{"text":" • "},{"text":"7:00"}""",
            ),
        )!!
        assertEquals("Sheraz Khalil", track.artist)
        assertNull(track.album)
        assertEquals("162K views", track.plays)
    }

    @Test
    fun plainPlaylistRowsStillReadArtistAndDuration() {
        val track = parse(
            row("""{"text":"Some Artist"},{"text":" • "},{"text":"3:21"}"""),
        )!!
        assertEquals("Some Artist", track.artist)
        assertEquals(3L * 60_000 + 21_000, track.durationMs)
    }

    @Test
    fun playlistRowsKeepTheSetVideoIdNeededForRemoval() {
        val json = row("""{"text":"Some Artist"}""")
            .replace(
                "\"videoId\":\"NJAv_7lHUIU\"",
                "\"videoId\":\"NJAv_7lHUIU\",\"playlistSetVideoId\":\"set-abc\"",
            )
        val track = parse(json)!!
        assertEquals("NJAv_7lHUIU", track.id)
        assertEquals("set-abc", track.setVideoId)
    }
}
