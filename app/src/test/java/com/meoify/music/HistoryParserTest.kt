package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile card's Played number comes from the account's History page.
 * These fixtures cover the row shapes YouTube Music uses there, including the
 * ones [YTMusicParser.parseTrack] cannot turn into a full Track.
 */
class HistoryParserTest {

    private fun ids(json: String) =
        YTMusicParser.historyVideoIds(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun countsSongsAcrossHistoryShelves() {
        val response = """
            {"contents":[
              {"musicShelfRenderer":{
                "title":{"runs":[{"text":"Today"}]},
                "contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"aaaaaaaaaaa"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"First song"}]}}},
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                        {"text":"Artist","navigationEndpoint":{"browseEndpoint":{
                          "browseId":"UCartist",
                          "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{
                            "pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}
                      ]}}}
                    ]
                  }},
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"bbbbbbbbbbb"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Second song"}]}}}
                    ]
                  }}
                ]
              }},
              {"musicShelfRenderer":{
                "title":{"runs":[{"text":"Yesterday"}]},
                "contents":[
                  {"musicResponsiveListItemRenderer":{
                    "playlistItemData":{"videoId":"ccccccccccc"},
                    "flexColumns":[
                      {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Third song"}]}}}
                    ]
                  }}
                ]
              }}
            ]}
        """.trimIndent()

        assertEquals(
            listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc"),
            ids(response),
        )
    }

    @Test
    fun findsIdsWithoutFlexColumnsOrPlaylistItemData() {
        // History occasionally shows video-ish rows; the id only lives in the
        // play/watch navigation endpoint.
        val response = """
            {"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{
                "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{
                  "playNavigationEndpoint":{"watchEndpoint":{"videoId":"ddddddddddd"}}
                }}}}
              }},
              {"musicTwoRowItemRenderer":{
                "title":{"runs":[{"text":"A video"}]},
                "navigationEndpoint":{"watchEndpoint":{"videoId":"eeeeeeeeeee"}}
              }}
            ]}}
        """.trimIndent()

        assertEquals(listOf("ddddddddddd", "eeeeeeeeeee"), ids(response))
    }

    @Test
    fun deDuplicatesRepeatedRows() {
        val response = """
            {"musicShelfRenderer":{"contents":[
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"fffffffffff"}}},
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"fffffffffff"}}},
              {"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"ggggggggggg"}}}
            ]}}
        """.trimIndent()

        assertEquals(listOf("fffffffffff", "ggggggggggg"), ids(response))
    }

    @Test
    fun ignoresPageChromeWithoutSongs() {
        val response = """
            {"contents":{"sectionListRenderer":{"contents":[
              {"musicNotifierShelfRenderer":{"title":{"runs":[{"text":"History is off"}]}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"abc"}}}}
            ]}}}
        """.trimIndent()

        assertTrue(ids(response).isEmpty())
    }
}
