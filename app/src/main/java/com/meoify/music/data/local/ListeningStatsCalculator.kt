package com.meoify.music.data.local

import com.meoify.music.data.model.ListeningEvent
import com.meoify.music.data.model.RankedArtist
import com.meoify.music.data.model.RankedTrack
import com.meoify.music.data.model.StatsTimeframe
import com.meoify.music.data.model.TimeOfDay
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.WrappedStats
import java.util.Calendar

object ListeningStatsCalculator {

    fun compute(
        events: List<ListeningEvent>,
        recentFallback: List<Track> = emptyList(),
        likedFallback: List<Track> = emptyList(),
        timeframe: StatsTimeframe = StatsTimeframe.THIS_MONTH,
        nowMs: Long = System.currentTimeMillis(),
    ): WrappedStats {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMs }
        val timeframeStartMs = when (timeframe) {
            StatsTimeframe.THIS_MONTH -> {
                val cal = calendar.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatsTimeframe.THIS_YEAR -> {
                val cal = calendar.clone() as Calendar
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatsTimeframe.ALL_TIME -> 0L
        }

        val filteredEvents = events.filter { it.timestampMs >= timeframeStartMs }

        // If no events recorded yet for this timeframe, synthesize baseline events from recent & liked
        val effectiveEvents: List<ListeningEvent> = if (filteredEvents.isNotEmpty()) {
            filteredEvents
        } else {
            val combined = (recentFallback + likedFallback).distinctBy { it.id }
            combined.mapIndexed { index, track ->
                // Distribute timestamps over the current period so time-of-day has initial data
                val fakeTimestamp = nowMs - (index * 3_600_000L * 4)
                val duration = if (track.durationMs > 0) track.durationMs else 180_000L
                ListeningEvent(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumId = track.albumId,
                    thumbnailUrl = track.thumbnailUrl,
                    durationMs = duration,
                    listenedMs = duration,
                    timestampMs = fakeTimestamp,
                )
            }
        }

        if (effectiveEvents.isEmpty()) {
            return WrappedStats(
                timeframe = timeframe,
                totalListeningTimeMs = 0L,
                totalPlays = 0,
                uniqueTracksCount = 0,
                uniqueArtistsCount = 0,
                topSongs = emptyList(),
                topArtists = emptyList(),
                favoriteTimeOfDay = null,
                timeDistribution = TimeOfDay.entries.associateWith { 0 },
            )
        }

        val totalPlays = effectiveEvents.size
        val totalListeningTimeMs = effectiveEvents.sumOf {
            if (it.listenedMs > 0) it.listenedMs
            else if (it.durationMs > 0) it.durationMs
            else 180_000L
        }

        // Top songs
        val songGroups = effectiveEvents.groupBy { it.trackId.ifBlank { "${it.title}|${it.artist}" } }
        val topSongs = songGroups.map { (_, group) ->
            val first = group.first()
            val track = Track(
                id = first.trackId,
                title = first.title,
                artist = first.artist,
                album = first.album,
                albumId = first.albumId,
                thumbnailUrl = first.thumbnailUrl,
                durationMs = first.durationMs,
            )
            val listenedMs = group.sumOf {
                if (it.listenedMs > 0) it.listenedMs
                else if (it.durationMs > 0) it.durationMs
                else 180_000L
            }
            RankedTrack(
                track = track,
                playCount = group.size,
                totalListenedMs = listenedMs,
            )
        }.sortedWith(
            compareByDescending<RankedTrack> { it.playCount }
                .thenByDescending { it.totalListenedMs }
        ).take(10)

        // Top artists
        val artistGroups = effectiveEvents
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist.trim() }
        val topArtists = artistGroups.map { (artistName, group) ->
            val listenedMs = group.sumOf {
                if (it.listenedMs > 0) it.listenedMs
                else if (it.durationMs > 0) it.durationMs
                else 180_000L
            }
            RankedArtist(
                name = artistName,
                playCount = group.size,
                totalListenedMs = listenedMs,
            )
        }.sortedWith(
            compareByDescending<RankedArtist> { it.playCount }
                .thenByDescending { it.totalListenedMs }
        ).take(10)

        // Time of day analysis
        val cal = Calendar.getInstance()
        val timeCounts = mutableMapOf<TimeOfDay, Int>()
        TimeOfDay.entries.forEach { timeCounts[it] = 0 }

        effectiveEvents.forEach { event ->
            cal.timeInMillis = event.timestampMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val timeOfDay = when (hour) {
                in 6..11 -> TimeOfDay.MORNING
                in 12..17 -> TimeOfDay.AFTERNOON
                in 18..21 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }
            timeCounts[timeOfDay] = (timeCounts[timeOfDay] ?: 0) + 1
        }

        val favoriteTimeOfDay = timeCounts.maxByOrNull { it.value }?.key
        val timeDistribution = TimeOfDay.entries.associateWith { tod ->
            val count = timeCounts[tod] ?: 0
            if (totalPlays > 0) ((count.toDouble() / totalPlays) * 100).toInt() else 0
        }

        return WrappedStats(
            timeframe = timeframe,
            totalListeningTimeMs = totalListeningTimeMs,
            totalPlays = totalPlays,
            uniqueTracksCount = songGroups.size,
            uniqueArtistsCount = artistGroups.size,
            topSongs = topSongs,
            topArtists = topArtists,
            favoriteTimeOfDay = favoriteTimeOfDay,
            timeDistribution = timeDistribution,
        )
    }
}
