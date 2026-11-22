package com.meoify.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ListeningEvent(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val listenedMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class StatsTimeframe(val label: String) {
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time"),
}

enum class TimeOfDay(val title: String, val timeRange: String, val emoji: String) {
    MORNING("Early Bird", "6:00 AM – 12:00 PM", "🌅"),
    AFTERNOON("Daytime Groover", "12:00 PM – 6:00 PM", "☀️"),
    EVENING("Sunset Chiller", "6:00 PM – 10:00 PM", "🌆"),
    NIGHT("Night Owl", "10:00 PM – 6:00 AM", "🌙"),
}

data class RankedTrack(
    val track: Track,
    val playCount: Int,
    val totalListenedMs: Long,
)

data class RankedArtist(
    val name: String,
    val playCount: Int,
    val totalListenedMs: Long,
)

data class WrappedStats(
    val timeframe: StatsTimeframe,
    val totalListeningTimeMs: Long,
    val totalPlays: Int,
    val uniqueTracksCount: Int,
    val uniqueArtistsCount: Int,
    val topSongs: List<RankedTrack>,
    val topArtists: List<RankedArtist>,
    val favoriteTimeOfDay: TimeOfDay?,
    val timeDistribution: Map<TimeOfDay, Int>, // percentage 0..100
)
