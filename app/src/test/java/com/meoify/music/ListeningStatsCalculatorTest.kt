package com.meoify.music

import com.meoify.music.data.local.ListeningStatsCalculator
import com.meoify.music.data.model.ListeningEvent
import com.meoify.music.data.model.StatsTimeframe
import com.meoify.music.data.model.TimeOfDay
import com.meoify.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ListeningStatsCalculatorTest {

    @Test
    fun computeRanksTopSongsAndArtistsCorrectly() {
        val now = System.currentTimeMillis()
        val events = listOf(
            ListeningEvent(trackId = "1", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200_000, listenedMs = 200_000, timestampMs = now),
            ListeningEvent(trackId = "1", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200_000, listenedMs = 200_000, timestampMs = now - 1000),
            ListeningEvent(trackId = "2", title = "Save Your Tears", artist = "The Weeknd", durationMs = 210_000, listenedMs = 210_000, timestampMs = now - 2000),
            ListeningEvent(trackId = "3", title = "Levitating", artist = "Dua Lipa", durationMs = 180_000, listenedMs = 180_000, timestampMs = now - 3000),
        )

        val stats = ListeningStatsCalculator.compute(
            events = events,
            timeframe = StatsTimeframe.ALL_TIME,
            nowMs = now,
        )

        assertEquals(4, stats.totalPlays)
        assertEquals(790_000L, stats.totalListeningTimeMs)
        assertEquals(3, stats.uniqueTracksCount)
        assertEquals(2, stats.uniqueArtistsCount)

        // The Weeknd should be #1 artist
        assertEquals("The Weeknd", stats.topArtists.first().name)
        assertEquals(3, stats.topArtists.first().playCount)

        // Blinding Lights should be #1 song
        assertEquals("Blinding Lights", stats.topSongs.first().track.title)
        assertEquals(2, stats.topSongs.first().playCount)
    }

    @Test
    fun computeTimeOfDayDeterminesNightOwl() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 23, 30, 0) // 11:30 PM (Night)
        }
        val nightTime = cal.timeInMillis

        val events = listOf(
            ListeningEvent(trackId = "1", title = "Midnight Song", artist = "Artist A", durationMs = 180_000, listenedMs = 180_000, timestampMs = nightTime),
            ListeningEvent(trackId = "2", title = "Late Night Vibe", artist = "Artist B", durationMs = 180_000, listenedMs = 180_000, timestampMs = nightTime + 1000),
        )

        val stats = ListeningStatsCalculator.compute(
            events = events,
            timeframe = StatsTimeframe.ALL_TIME,
            nowMs = nightTime + 5000,
        )

        assertEquals(TimeOfDay.NIGHT, stats.favoriteTimeOfDay)
        assertEquals(100, stats.timeDistribution[TimeOfDay.NIGHT])
    }

    @Test
    fun fallbackToRecentAndLikedWhenEventsEmpty() {
        val recent = listOf(
            Track(id = "101", title = "Song A", artist = "Artist X", durationMs = 180_000),
        )
        val liked = listOf(
            Track(id = "102", title = "Song B", artist = "Artist Y", durationMs = 200_000),
        )

        val stats = ListeningStatsCalculator.compute(
            events = emptyList(),
            recentFallback = recent,
            likedFallback = liked,
            timeframe = StatsTimeframe.ALL_TIME,
        )

        assertEquals(2, stats.totalPlays)
        assertNotNull(stats.favoriteTimeOfDay)
        assertTrue(stats.topSongs.isNotEmpty())
        assertTrue(stats.topArtists.isNotEmpty())
    }
}
