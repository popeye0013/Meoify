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

/** One LRCLIB result, kept with the metadata used to match it to a track. */
internal data class LyricsCandidate(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val lyrics: TrackLyrics,
)

/**
 * Synced lyrics from LRCLIB, with a plain-text fallback.
 * Network failures are deliberately soft: the player shows "lyrics unavailable"
 * rather than failing playback.
 *
 * LRCLIB search often returns live, karaoke and cover versions of the same
 * song. Picking the first synced result makes the lyrics drift out of time, so
 * results are scored with the same title/artist/duration rules used for
 * artwork, and the closest recording wins.
 */
class LyricsApi(private val client: OkHttpClient) {

    private val cache = ConcurrentHashMap<String, TrackLyrics>()

    suspend fun load(track: Track): TrackLyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        val result = runCatching { fetchLrcLib(track) }.getOrNull() ?: TrackLyrics()
        // Don't cache a miss: a timeout must not hide lyrics for the rest of
        // the session. Reopening the lyrics view retries.
        if (!result.isEmpty) {
            if (cache.size > 80) cache.clear()
            cache[track.id] = result
        }
        result
    }

    private fun fetchLrcLib(track: Track): TrackLyrics? {
        val exact = runCatching { fetchExact(track) }.getOrNull()
        if (exact?.synced == true && !exact.isEmpty) return exact

        val searched = runCatching { fetchSearch(track) }.getOrNull()
        if (searched != null && !searched.isEmpty) return searched

        return exact?.takeIf { !it.isEmpty }
    }

    private fun fetchExact(track: Track): TrackLyrics? {
        for (title in titlesFor(track)) {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artistFor(track))
                .apply {
                    track.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) }
                    if (track.durationMs > 0) addQueryParameter("duration", (track.durationMs / 1000).toString())
                }
                .build()
            val body = get(url.toString()) ?: continue
            val parsed = runCatching { parse(Json.parseToJsonElement(body).jsonObject) }.getOrNull()
            if (parsed != null && !parsed.isEmpty) return parsed
        }
        return null
    }

    private fun fetchSearch(track: Track): TrackLyrics? {
        val artist = artistFor(track)
        val candidates = mutableListOf<LyricsCandidate>()
        for (title in titlesFor(track)) {
            val builder = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
            if (artist.isNotBlank()) builder.addQueryParameter("artist_name", artist)
            val body = get(builder.build().toString()) ?: continue

            val array = runCatching { Json.parseToJsonElement(body).jsonArray }.getOrNull() ?: continue
            candidates += array.mapNotNull { runCatching { candidate(it.jsonObject) }.getOrNull() }

            // Stop as soon as a result actually matches this recording. A
            // poorly matched live version should not stop the next query.
            val synced = candidates.filter { it.lyrics.synced }
            if (bestLyrics(track, synced) != null) break
        }
        return bestLyrics(track, candidates.filter { it.lyrics.synced })?.lyrics
            ?: bestLyrics(track, candidates)?.lyrics
    }

    /** Full title plus the cleaned song name, e.g. without "Official Video". */
    private fun titlesFor(track: Track): List<String> {
        val title = track.title.trim()
        if (title.isBlank()) return emptyList()
        val core = MusicArtworkMatch.coreTitle(title).ifBlank { title }
        return listOf(title, core).distinctBy { MusicArtworkMatch.normalize(it) }
    }

    private fun artistFor(track: Track): String =
        track.artist.substringBefore(",").removeSuffix(" - Topic").trim()

    private fun candidate(root: JsonObject): LyricsCandidate? {
        val title = root["trackName"]?.jsonPrimitive?.contentOrNull ?: return null
        val lyrics = parse(root)
        if (lyrics.isEmpty) return null
        val durationMs = root["duration"]?.jsonPrimitive?.contentOrNull
            ?.toDoubleOrNull()
            ?.times(1_000)
            ?.toLong()
            ?: 0L
        return LyricsCandidate(
            title = title,
            artist = root["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            album = root["albumName"]?.jsonPrimitive?.contentOrNull,
            durationMs = durationMs,
            lyrics = lyrics,
        )
    }

    /** The best matching candidate, using the artwork title/artist rules. */
    internal fun bestLyrics(track: Track, candidates: List<LyricsCandidate>): LyricsCandidate? {
        if (candidates.isEmpty()) return null
        val artworkCandidates = candidates.map { candidate ->
            ArtworkCandidate(
                title = candidate.title,
                artist = candidate.artist,
                album = candidate.album,
                durationMs = candidate.durationMs,
                artworkUrl = "",
            )
        }
        val best = MusicArtworkMatch.best(track, artworkCandidates) ?: return null
        return candidates[artworkCandidates.indexOf(best)]
    }

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Meoify/1.0.23 (Android)")
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

    /**
     * Parses LRC text, including multiple timestamps per line, 1–3 digit
     * fractions and the `[offset:±ms]` tag that many lyrics files use.
     */
    internal fun parseLrc(raw: String): List<LyricLine> {
        val offsetMs = OFFSET.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val matches = STAMP.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.replace(STAMP, "").trim()
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
                val time = minutes * 60_000 + seconds * 1_000 + millis + offsetMs
                out.add(LyricLine(time.coerceAtLeast(0L), text))
            }
        }
        return out.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }

    private companion object {
        val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
        val STAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    }
}
