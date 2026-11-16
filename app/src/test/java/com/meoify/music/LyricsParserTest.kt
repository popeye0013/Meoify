package com.meoify.music

import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.LyricLine
import com.meoify.music.data.remote.LyricsApi
import com.meoify.music.data.remote.LyricsCandidate
import com.meoify.music.data.remote.TrackLyrics
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsParserTest {

    private val api = LyricsApi(OkHttpClient())

    private val track = Track(
        id = "abc",
        title = "Shape of You",
        artist = "Ed Sheeran",
        album = "Divide",
        durationMs = 233_713L,
    )

    @Test
    fun readsMinutesSecondsAndFractions() {
        val lines = api.parseLrc(
            """
            [00:01.5]One
            [00:12.34]Two
            [01:02.345]Three
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LyricLine(1_500, "One"),
                LyricLine(12_340, "Two"),
                LyricLine(62_345, "Three"),
            ),
            lines,
        )
    }

    @Test
    fun expandsMultipleTimestampsPerLine() {
        val lines = api.parseLrc("[00:05.00][00:15.00][01:00.00]Chorus")

        assertEquals(
            listOf(
                LyricLine(5_000, "Chorus"),
                LyricLine(15_000, "Chorus"),
                LyricLine(60_000, "Chorus"),
            ),
            lines,
        )
    }

    @Test
    fun appliesOffsetTags() {
        val ahead = api.parseLrc(
            """
            [offset:+250]
            [00:10.00]Later
            """.trimIndent(),
        )
        val behind = api.parseLrc(
            """
            [offset:-500]
            [00:10.00]Earlier
            """.trimIndent(),
        )

        assertEquals(listOf(LyricLine(10_250, "Later")), ahead)
        assertEquals(listOf(LyricLine(9_500, "Earlier")), behind)
    }

    @Test
    fun negativeTimesClampToZeroAndSort() {
        val lines = api.parseLrc(
            """
            [offset:-5000]
            [00:00.10]Start
            [00:30.00]End
            [00:30.00]End
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                LyricLine(0, "Start"),
                LyricLine(25_000, "End"),
            ),
            lines,
        )
    }

    @Test
    fun ignoresLinesWithoutUsableText() {
        assertEquals(
            emptyList<LyricLine>(),
            api.parseLrc(
                """
                [ar:Someone]
                [00:10.00]
                no timestamp here
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun picksTheRecordingWithTheClosestDuration() {
        val live = candidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "Divide",
            duration = 268_000L,
            text = "live words",
        )
        val studio = candidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "Divide",
            duration = 233_000L,
            text = "studio words",
        )

        assertEquals(studio, api.bestLyrics(track, listOf(live, studio)))
    }

    @Test
    fun rejectsKaraokeAndTributeRecordings() {
        val karaoke = candidate(
            title = "Shape of You",
            artist = "Karaoke Kings",
            album = "Karaoke Hits",
            duration = 233_700L,
            text = "karaoke words",
        )
        val tribute = candidate(
            title = "Shape of You (Tribute to Ed Sheeran)",
            artist = "The Tribute Band",
            album = null,
            duration = 233_700L,
            text = "tribute words",
        )

        assertNull(api.bestLyrics(track, listOf(karaoke, tribute)))
    }

    @Test
    fun findsTheSongByItsCleanedTitle() {
        // YouTube titles carry video noise; LRCLIB stores the plain song name.
        val noisy = track.copy(title = "Ed Sheeran - Shape of You (Official Video)")
        val studio = candidate(
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "÷ (Deluxe)",
            duration = 233_700L,
            text = "words",
        )

        assertEquals(studio, api.bestLyrics(noisy, listOf(studio)))
    }

    private fun candidate(
        title: String,
        artist: String,
        album: String?,
        duration: Long,
        text: String,
    ) = LyricsCandidate(
        title = title,
        artist = artist,
        album = album,
        durationMs = duration,
        lyrics = TrackLyrics(
            lines = listOf(LyricLine(1_000, text)),
            synced = true,
        ),
    )
}
