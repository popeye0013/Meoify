package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.playback.SyncAction
import com.meoify.music.playback.SyncPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ListenTogetherManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSyncPayloadSerialization() {
        val track = Track(
            id = "yt_test_123",
            title = "Starboy",
            artist = "The Weeknd",
            durationMs = 230_000,
        )

        val payload = SyncPayload(
            senderId = "client_abc_1",
            action = SyncAction.PLAY,
            track = track,
            positionMs = 45_000,
            isPlaying = true,
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<SyncPayload>(encoded)

        assertEquals("client_abc_1", decoded.senderId)
        assertEquals(SyncAction.PLAY, decoded.action)
        assertEquals("yt_test_123", decoded.track?.id)
        assertEquals("Starboy", decoded.track?.title)
        assertEquals(45_000L, decoded.positionMs)
        assertEquals(true, decoded.isPlaying)
    }

    @Test
    fun testNtfyWrappedMessageExtraction() {
        val track = Track(
            id = "test_id",
            title = "Blinding Lights",
            artist = "The Weeknd",
        )

        val payload = SyncPayload(
            senderId = "peer_client_2",
            action = SyncAction.SEEK,
            track = track,
            positionMs = 82_000,
            isPlaying = true,
        )

        val payloadJson = json.encodeToString(payload)

        // Mock ntfy.sh JSON format
        val ntfyWrapper = """
            {
                "id": "msg_12345",
                "time": 1795280287,
                "event": "message",
                "topic": "meoify_sync_123456",
                "message": ${json.encodeToString(payloadJson)}
            }
        """.trimIndent()

        val root = json.parseToJsonElement(ntfyWrapper).jsonObject
        val extractedMessage = root["message"]?.jsonPrimitive?.content

        assertNotNull(extractedMessage)
        val decoded = json.decodeFromString<SyncPayload>(extractedMessage!!)

        assertEquals("peer_client_2", decoded.senderId)
        assertEquals(SyncAction.SEEK, decoded.action)
        assertEquals(82_000L, decoded.positionMs)
    }
}
