package com.meoify.music

import com.meoify.music.data.model.DownloadFailure
import com.meoify.music.data.model.DownloadQueueItem
import com.meoify.music.data.model.DownloadState
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.downloadBaseName
import com.meoify.music.data.model.downloadExtension
import com.meoify.music.data.model.formatDownloadBytes
import com.meoify.music.data.model.safeDownloadId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class DownloadHelpersTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun safeIdKeepsYouTubeIdsAndReplacesUnsafeCharacters() {
        assertEquals("abc123_-.", safeDownloadId("abc123_-."))
        assertEquals("a_b_c", safeDownloadId("a/b?c"))
        assertEquals("song", safeDownloadId("!!!"))
    }

    @Test
    fun baseNameIsFilesystemSafeAndHumanReadable() {
        assertEquals("Artist - Title", downloadBaseName("Artist", "Title", "id"))
        assertEquals("A_B - C_D", downloadBaseName("A/B", "C:D", "id"))
        assertEquals("song_id", downloadBaseName("", "", "id"))
    }

    @Test
    fun extensionUsesMimeTypeBeforeFormatAndUrl() {
        assertEquals(".flac", downloadExtension("audio/flac; codecs=flac", "OPUS", "https://x/a.mp3"))
        assertEquals(".m4a", downloadExtension(null, "AAC", null))
        assertEquals(".opus", downloadExtension(null, null, "https://x/audio.opus"))
        assertEquals(".audio", downloadExtension(null, null, null))
    }

    @Test
    fun byteFormattingIsReadable() {
        assertEquals("512 B", formatDownloadBytes(512L))
        assertEquals("1.5 KB", formatDownloadBytes(1536L))
        assertEquals("2.0 MB", formatDownloadBytes(2L * 1024 * 1024))
    }

    @Test
    fun queueItemsRoundTripThroughJson() {
        val item = DownloadQueueItem(
            track = Track(id = "video123", title = "Song", artist = "Artist"),
            state = DownloadState.PAUSED,
            downloadedBytes = 1_024L,
            totalBytes = 4_096L,
            sourcePlaylistId = "playlist1",
        )
        val encoded = json.encodeToString(listOf(item))
        assertEquals(listOf(item), json.decodeFromString<List<DownloadQueueItem>>(encoded))
    }

    @Test
    fun onlyQueuedAndRunningItemsAreRunnable() {
        assertTrue(DownloadState.QUEUED.isRunnable)
        assertTrue(DownloadState.RUNNING.isRunnable)
        assertFalse(DownloadState.PAUSED.isRunnable)
        assertFalse(DownloadState.FAILED.isRunnable)
        assertFalse(DownloadState.COMPLETED.isRunnable)
        assertFalse(DownloadState.CANCELLED.isRunnable)
    }

    @Test
    fun networkFailuresAreClassifiedForQueueMessages() {
        assertEquals(DownloadFailure.NETWORK, DownloadFailure.from(SocketTimeoutException()))
        assertEquals(DownloadFailure.OTHER, DownloadFailure.from(IllegalStateException()))
    }
}
