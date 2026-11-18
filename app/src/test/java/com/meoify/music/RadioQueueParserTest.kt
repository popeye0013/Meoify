package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioQueueParserTest {

    private val radio = """
        {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{
          "watchNextTabbedResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
            "musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[
              {"playlistPanelVideoRenderer":{
                "videoId":"seed123",
                "title":{"runs":[{"text":"Never Gonna Give You Up"}]},
                "shortBylineText":{"runs":[{"text":"Rick Astley"}]},
                "longBylineText":{"runs":[
                  {"text":"Rick Astley","navigationEndpoint":{"browseEndpoint":{
                    "browseId":"UCwZEU0wAwIyZb4x5G_KJp2w",
                    "browseEndpointContextSupportedConfigs":{
                      "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}
                    }
                  }}},
                  {"text":" • "},{"text":"1.8B views"}
                ]},
                "lengthText":{"runs":[{"text":"3:34"}]},
                "thumbnail":{"thumbnails":[
                  {"url":"https://i.ytimg.com/vi/seed123/hq720.jpg","width":800}
                ]}
              }},
              {"playlistPanelVideoRenderer":{
                "videoId":"next456",
                "title":{"runs":[{"text":"Forever Young"}]},
                "shortBylineText":{"runs":[{"text":"Alphaville"}]},
                "longBylineText":{"runs":[
                  {"text":"Alphaville","navigationEndpoint":{"browseEndpoint":{
                    "browseId":"UCabc",
                    "browseEndpointContextSupportedConfigs":{
                      "browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}
                    }
                  }}},
                  {"text":" • "},{"text":"837K views"}
                ]},
                "lengthText":{"runs":[{"text":"3:38"}]},
                "thumbnail":{"thumbnails":[
                  {"url":"https://lh3.googleusercontent.com/cover=w120-h120","width":120},
                  {"url":"https://lh3.googleusercontent.com/cover=w544-h544","width":544}
                ]}
              }}
            ]}}}
          }}}]}}
        }}}
    """.trimIndent()

    @Test
    fun parsesTheRadioQueueInOrder() {
        val root = Json.parseToJsonElement(radio).jsonObject
        val tracks = YTMusicParser.parseRadioQueue(root)

        assertEquals(listOf("seed123", "next456"), tracks.map { it.id })
        val next = tracks[1]
        assertEquals("Forever Young", next.title)
        assertEquals("Alphaville", next.artist)
        assertEquals("UCabc", next.artistId)
        assertEquals(3L * 60_000 + 38_000, next.durationMs)
        assertEquals("837K views", next.plays)
        assertTrue(next.thumbnailUrl!!.endsWith("=w900-h900"))
    }

    @Test
    fun missingQueueIsEmpty() {
        val root = Json.parseToJsonElement("""{"contents":{}}""").jsonObject
        assertTrue(YTMusicParser.parseRadioQueue(root).isEmpty())
    }
}
