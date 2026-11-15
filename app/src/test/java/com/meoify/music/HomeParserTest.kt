package com.meoify.music

import com.meoify.music.data.remote.YTMusicParser
import com.meoify.music.data.remote.YTMusicParser.SearchFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeParserTest {

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun parsesHomeShelves() {
        val root = Json.parseToJsonElement(fixture("home.json")).jsonObject
        val (quickPicks, playlists) = YTMusicParser.parseHome(root)
        assertTrue("quick picks should be parsed", quickPicks.isNotEmpty())
        assertTrue("playlist cards should be parsed", playlists.isNotEmpty())
        assertTrue(quickPicks.first().id.isNotBlank())
        assertTrue(quickPicks.first().title.isNotBlank())
    }

    @Test
    fun parsesSongSearch() {
        val root = Json.parseToJsonElement(fixture("search.json")).jsonObject
        val result = YTMusicParser.parseSearch(root, SearchFilter.SONGS)
        assertTrue("songs should be parsed", result.tracks.isNotEmpty())
        assertTrue(result.tracks.first().id.isNotBlank())
        assertFalse(result.tracks.first().thumbnailUrl.isNullOrBlank())
    }
}
