package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import com.meoify.music.data.model.MediaKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaylistsParserTest {

    private fun parse(json: String) =
        YTMusicParser.parseLibraryPlaylists(Json.parseToJsonElement(json).jsonObject)

    private val response = """
        {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
          "sectionListRenderer":{"contents":[
            {"gridRenderer":{"items":[
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Late Night Drive"}]},
                "subtitle":{"runs":[{"text":"Playlist"}]},
                "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                  {"url":"https://lh3.googleusercontent.com/a/one=w60-h60","width":60},
                  {"url":"https://lh3.googleusercontent.com/a/one=w544-h544","width":544}
                ]}}},
                "navigationEndpoint":{"browseEndpoint":{
                  "browseId":"VLPLlateNightDrive",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{
                    "pageType":"MUSIC_PAGE_TYPE_PLAYLIST"
                  }}
                }}
              }},
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Liked Music"}]},
                "subtitle":{"runs":[{"text":"Auto playlist"}]},
                "navigationEndpoint":{"browseEndpoint":{"browseId":"VLLM"}}
              }},
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"Some Album"}]},
                "subtitle":{"runs":[{"text":"Album"}]},
                "navigationEndpoint":{"browseEndpoint":{
                  "browseId":"MPREb_somealbum",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{
                    "pageType":"MUSIC_PAGE_TYPE_ALBUM"
                  }}
                }}
              }}
            ]}},
            {"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{
                "flexColumns":[
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Road Trip"}]}}},
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Playlist • Akash"}]}}}
                ],
                "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                  {"url":"https://lh3.googleusercontent.com/a/road=w120-h120","width":120}
                ]}}},
                "navigationEndpoint":{"browseEndpoint":{
                  "browseId":"VLPLroadTrip",
                  "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{
                    "pageType":"MUSIC_PAGE_TYPE_PLAYLIST"
                  }}
                }}
              }}
            ]}}
          ]}
        }}}]}}}
    """.trimIndent()

    @Test
    fun collectsPlaylistsFromGridAndRows() {
        val playlists = parse(response)
        assertEquals(3, playlists.size)
        assertEquals(listOf("Late Night Drive", "Liked Music", "Road Trip"), playlists.map { it.title })
        assertTrue(playlists.all { it.kind == MediaKind.PLAYLIST })
        assertEquals("VLPLlateNightDrive", playlists[0].id)
        assertTrue(playlists[0].thumbnailUrl.orEmpty().contains("=w900-h900"))
        assertEquals("Playlist • Akash", playlists[2].subtitle)
    }

    @Test
    fun deduplicatesRepeatedCards() {
        val duplicated = response.replace(
            "{\"musicShelfRenderer\"",
            "{\"gridRenderer\":{\"items\":[{\"musicTwoRowItemRenderer\":{\"title\":{\"runs\":[{\"text\":\"Late Night Drive\"}]},\"navigationEndpoint\":{\"browseEndpoint\":{\"browseId\":\"VLPLlateNightDrive\"}}}}]}},\n            {\"musicShelfRenderer\"",
        )
        val playlists = parse(duplicated)
        assertEquals(3, playlists.size)
        assertEquals(playlists.size, playlists.distinctBy { it.id }.size)
    }

    @Test
    fun signedOutPromptReturnsEmpty() {
        val json = """
            {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
              "sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
                {"messageRenderer":{"text":{"runs":[{"text":"Looking for what you've liked?"}]}}}
              ]}}]}
            }}}]}}}
        """.trimIndent()
        assertTrue(parse(json).isEmpty())
    }
}
