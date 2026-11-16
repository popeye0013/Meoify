package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.ArtworkCandidate
import com.meoify.music.data.remote.MusicArtworkMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicArtworkMatchTest {

    private val track = Track(
        id = "abc",
        title = "Shape of You",
        artist = "Ed Sheeran",
        album = "Divide (Deluxe)",
        durationMs = 233_713L,
    )

    @Test
    fun picksTheMatchingStudioTrack() {
        val karaoke = ArtworkCandidate(
            title = "Shape of You",
            artist = "Karaoke Kings",
            album = "Karaoke Hits",
            durationMs = 233_000L,
            artworkUrl = "https://example.com/karaoke.jpg",
        )
        val real = ArtworkCandidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Deluxe)",
            durationMs = 233_713L,
            artworkUrl = "https://example.com/real.jpg",
        )

        assertEquals(real, MusicArtworkMatch.best(track, listOf(karaoke, real)))
    }

    @Test
    fun rejectsTheSameTitleByAnotherArtist() {
        val other = ArtworkCandidate(
            title = "Shape of You",
            artist = "Some Other Band",
            artworkUrl = "https://example.com/other.jpg",
        )

        assertNull(MusicArtworkMatch.best(track, listOf(other)))
    }

    @Test
    fun ignoresFeaturingCreditsAndVersionTags() {
        val remaster = ArtworkCandidate(
            title = "Shape of You (feat. Ed Sheeran) - Remastered 2017",
            artist = "Ed Sheeran",
            artworkUrl = "https://example.com/remaster.jpg",
        )

        assertEquals(remaster, MusicArtworkMatch.best(track, listOf(remaster)))
    }

    @Test
    fun durationBreaksATieBetweenEditions() {
        val short = ArtworkCandidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Deluxe)",
            durationMs = 180_000L,
            artworkUrl = "https://example.com/short.jpg",
        )
        val full = ArtworkCandidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Deluxe)",
            durationMs = 233_713L,
            artworkUrl = "https://example.com/full.jpg",
        )

        assertEquals(full, MusicArtworkMatch.best(track, listOf(short, full)))
    }

    @Test
    fun albumBreaksATieBetweenEditions() {
        val single = ArtworkCandidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "Shape of You - Single",
            artworkUrl = "https://example.com/single.jpg",
        )
        val album = ArtworkCandidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "Divide (Deluxe)",
            artworkUrl = "https://example.com/album.jpg",
        )

        assertEquals(album, MusicArtworkMatch.best(track, listOf(single, album)))
    }

    @Test
    fun normalizationDropsAccentsPunctuationAndBrackets() {
        assertEquals("", MusicArtworkMatch.normalize("÷ (Deluxe)"))
        assertEquals("cafe", MusicArtworkMatch.normalize("Café"))
        assertEquals("song", MusicArtworkMatch.normalize("Song (feat. X) - Remastered 2011"))
    }
}
