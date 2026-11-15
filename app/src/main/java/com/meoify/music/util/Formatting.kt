package com.meoify.music.util

import android.text.format.DateUtils
import java.util.concurrent.TimeUnit
import kotlin.math.abs

fun formatDuration(ms: Long, showHours: Boolean = false): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = abs(ms) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0 || showHours) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatTotalDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}

fun formatPlayCount(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace(" plays", "")
}

fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

fun formatRelativeTime(timestamp: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
