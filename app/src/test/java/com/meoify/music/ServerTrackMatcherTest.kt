package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.ServerTrackMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTrackMatcherTest {

    private fun item(json: String) = Json.parseToJsonElement(json).jsonObject

    private val wanted = Track(
        id = "yt123",
        title = "Azizam",
        artist = "Ed Sheeran",
        album = "Azizam",
        durationMs = 162_000L,
    )

    @Test
    fun matchesHifiApiItemWithArtistObject() {
        // Real hifi-api shape: artist is an object, not a string.
        val result = item(
            """
            {
              "id": 427520487,
              "title": "Azizam",
              "duration": 162,
              "artist": {"id": 3995478, "name": "Ed Sheeran", "type": "MAIN"},
              "album": {"id": 427520486, "title": "Azizam"},
              "version": null,
              "allowStreaming": true,
              "streamReady": true
            }
            """.trimIndent(),
        )
        assertTrue("correct Tidal item should match", ServerTrackMatcher.score(result, wanted) > 0)
    }

    @Test
    fun rejectsSameTitleByDifferentArtist() {
        val result = item(
            """
            {
              "id": 999,
              "title": "Azizam",
              "duration": 162,
              "artist": {"id": 1, "name": "Some Other Artist", "type": "MAIN"}
            }
            """.trimIndent(),
        )
        assertEquals("same title by another artist must never match", 0, ServerTrackMatcher.score(result, wanted))
    }

    @Test
    fun rejectsLiveVersionWhenStudioWasRequested() {
        val result = item(
            """
            {
              "id": 888,
              "title": "Azizam",
              "duration": 200,
              "artist": {"name": "Ed Sheeran"},
              "version": "Live at Wembley"
            }
            """.trimIndent(),
        )
        assertEquals("live version is a different recording", 0, ServerTrackMatcher.score(result, wanted))
    }

    @Test
    fun matchesSaavnStyleItemWithStringArtistAndMillisDuration() {
        val result = item(
            """
            {
              "id": "saavn1",
              "title": "Azizam",
              "duration": "162000",
              "primaryArtist": "Ed Sheeran"
            }
            """.trimIndent(),
        )
        assertTrue("Saavn item should match", ServerTrackMatcher.score(result, wanted) > 0)
    }

    @Test
    fun doesNotTreatEveryTitleContainingLiveAsAnAlternateVersion() {
        val liveTitleTrack = wanted.copy(title = "Live While We're Young", artist = "One Direction", album = "Take Me Home")
        val result = item(
            """
            {
              "id": 777,
              "title": "Live While We're Young",
              "duration": 193,
              "artists": [{"name": "One Direction"}]
            }
            """.trimIndent(),
        )
        assertTrue("ordinary title containing 'Live' must still match", ServerTrackMatcher.score(result, liveTitleTrack) > 0)
    }
}
