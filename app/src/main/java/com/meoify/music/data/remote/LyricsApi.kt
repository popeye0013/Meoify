package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class TrackLyrics(
    val lines: List<LyricLine> = emptyList(),
    val plain: String? = null,
    val synced: Boolean = false,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
}

/**
 * Synced lyrics from LRCLIB, with a plain-text fallback for tracks that only
 * have lyrics inside YouTube Music. Network failures are deliberately soft:
 * the player simply shows "lyrics unavailable" rather than failing playback.
 */
class LyricsApi(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, TrackLyrics>()

    suspend fun load(track: Track): TrackLyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        val fromLrcLib = runCatching { fetchLrcLib(track) }.getOrNull()
        val result = fromLrcLib ?: TrackLyrics()
        if (cache.size > 80) cache.clear()
        cache[track.id] = result
        result
    }

    private fun fetchLrcLib(track: Track): TrackLyrics? {
        val exact = get(
            "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artist.substringBefore(",").trim())
                .apply {
                    track.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
                    if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString())
                }
                .build()
                .toString(),
        )
        exact?.let { body ->
            runCatching { parse(Json.parseToJsonElement(body).jsonObject) }.getOrNull()
        }?.takeIf { !it.isEmpty }?.let { return it }

        val search = get(
            "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artist.substringBefore(",").trim())
                .build()
                .toString(),
        ) ?: return null
        val array = runCatching { Json.parseToJsonElement(search).jsonArray }.getOrNull() ?: return null
        return array.asSequence()
            .mapNotNull { runCatching { parse(it.jsonObject) }.getOrNull() }
            .firstOrNull { it.synced && !it.isEmpty }
            ?: array.asSequence()
                .mapNotNull { runCatching { parse(it.jsonObject) }.getOrNull() }
                .firstOrNull { !it.isEmpty }
    }

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Meoify/1.0 (https://github.com/meoify)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()

    private fun parse(root: JsonObject): TrackLyrics {
        val synced = root["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        val plain = root["plainLyrics"]?.jsonPrimitive?.contentOrNull
        if (!synced.isNullOrBlank()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return TrackLyrics(lines = lines, plain = plain, synced = true)
        }
        return TrackLyrics(plain = plain)
    }

    private fun parseLrc(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val stamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        raw.lineSequence().forEach { line ->
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.replace(stamp, "").trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLongOrNull()?.times(100) ?: 0L
                    2 -> fraction.toLongOrNull()?.times(10) ?: 0L
                    else -> fraction.take(3).toLongOrNull() ?: 0L
                }
                out.add(LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text))
            }
        }
        return out.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }
}
