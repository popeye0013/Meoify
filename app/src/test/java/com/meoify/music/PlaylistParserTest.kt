package com.meoify.music

import com.meoify.music.data.model.MediaKind
import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistParserTest {

    private fun parse(json: String) =
        YTMusicParser.parsePlaylist(Json.parseToJsonElement(json).jsonObject, "VLPLroadtrip")

    private fun root(json: String) = Json.parseToJsonElement(json).jsonObject

    private fun row(videoId: String, title: String, duration: String) = """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$videoId"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"Some Artist"},{"text":" • "},{"text":"$duration"}
            ]}}}
          ],
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/a/row=w120-h120","width":120},
            {"url":"https://lh3.googleusercontent.com/a/row=w544-h544","width":544}
          ]}}}
        }}
    """.trimIndent()

    private val response = """
        {"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
          "contents":[
            {"musicShelfRenderer":{"contents":[
              ${row("vidOne", "First Song", "3:21")},
              ${row("vidTwo", "Second Song", "4:02")}
            ]}},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{
              "token":"CONTINUATION_TOKEN","request":"CONTINUATION_REQUEST_TYPE_BROWSE"
            }}}}
          ]
       }}}},
        "header":{"musicDetailHeaderRenderer":{
          "title":{"runs":[{"text":"Road Trip"}]},
          "subtitle":{"runs":[{"text":"Playlist"}]},
          "secondSubtitle":{"runs":[{"text":"2 songs"}]},
          "thumbnail":{"croppedSquareThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/a/cover=w120-h120","width":120},
            {"url":"https://lh3.googleusercontent.com/a/cover=w544-h544","width":544}
          ]}}}
        }}}
    """.trimIndent()

    @Test
    fun collectsTracksFromAnyShelf() {
        val page = parse(response)
        assertEquals(MediaKind.PLAYLIST, page.kind)
        assertEquals("Road Trip", page.title)
        assertEquals("2 songs", page.subtitle.orEmpty().substringAfter(" • "))
        assertEquals(listOf("vidOne", "vidTwo"), page.tracks.map { it.id })
        assertEquals(listOf("First Song", "Second Song"), page.tracks.map { it.title })
        assertEquals(3L * 60_000 + 21_000, page.tracks[0].durationMs)
        assertNotNull(page.thumbnailUrl)
        assertTrue(page.thumbnailUrl.orEmpty().contains("=w900-h900"))
        assertTrue(page.tracks.all { it.thumbnailUrl != null })
    }

    @Test
    fun readsTheContinuationToken() {
        assertEquals("CONTINUATION_TOKEN", YTMusicParser.nextContinuation(root(response)))
        assertEquals(null, YTMusicParser.nextContinuation(root("""{"contents":{}}""")))
    }

    @Test
    fun aContinuationPageYieldsMoreTracks() {
        val page = root(
            """{"continuationContents":{"musicShelfContinuation":{"contents":[
                 ${row("vidThree", "Third Song", "2:10")}
               ]}}}""",
        )
        val tracks = YTMusicParser.parseTracks(page)
        assertEquals(listOf("vidThree"), tracks.map { it.id })
    }
}
