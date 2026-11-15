package com.meoify.music

import com.meoify.music.data.backup.BackupPlaylist
import com.meoify.music.data.backup.BackupProfile
import com.meoify.music.data.backup.MeoifyBackup
import com.meoify.music.data.backup.looksLikeMeoifyBackup
import com.meoify.music.data.model.Track
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun roundTripsPlaylistsAndProfile() {
        val backup = MeoifyBackup(
            createdAt = 1_700_000_000_000,
            profile = BackupProfile(name = "Akash", avatarBase64 = "YWJj"),
            playlists = listOf(
                BackupPlaylist(
                    name = "Late night",
                    tracks = listOf(
                        Track(
                            id = "abc123",
                            title = "Someone Like You",
                            artist = "Adele",
                            album = "21",
                            durationMs = 285_000,
                            thumbnailUrl = "https://example.com/art.jpg",
                        ),
                    ),
                    createdAt = 1_600_000_000_000,
                ),
            ),
        )

        val text = json.encodeToString(backup)
        assertTrue("format marker should be written", text.contains("\"format\": \"meoify-backup\""))

        val restored = json.decodeFromString<MeoifyBackup>(text)
        assertEquals("meoify-backup", restored.format)
        assertEquals(1, restored.version)
        assertEquals("Akash", restored.profile?.name)
        assertEquals("YWJj", restored.profile?.avatarBase64)
        assertEquals(1, restored.playlists.size)
        assertEquals("Late night", restored.playlists.first().name)
        assertEquals("abc123", restored.playlists.first().tracks.first().id)
        assertEquals(285_000, restored.playlists.first().tracks.first().durationMs)
    }

    @Test
    fun acceptsOnlyFilesWithTheFormatMarker() {
        assertTrue(looksLikeMeoifyBackup(json.encodeToString(MeoifyBackup())))
        assertFalse(looksLikeMeoifyBackup("""{"playlists":[]}"""))
        assertFalse(looksLikeMeoifyBackup("not json at all"))
    }
}
