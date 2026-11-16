package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Album artwork from Apple's public iTunes catalogue.
 *
 * Spotify is the preferred source, but anonymous Spotify search can be rate
 * limited. This keyless catalogue is the backup, so a song still gets real
 * square album art instead of a video frame or an empty tile. Results are
 * matched with the same title/artist/album/duration rules as Spotify.
 */
class AppleArtwork(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val limiter = Mutex()
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()

    /** The best Apple catalogue cover for [track], or null when none matches. */
    suspend fun cover(track: Track): String? = withContext(Dispatchers.IO) {
        val key = key(track)
        if (key.isBlank()) return@withContext null
        cache[key]?.let { return@withContext it }
        val now = System.currentTimeMillis()
        (misses[key] ?: 0L).let { missedAt ->
            if (missedAt != 0L && now - missedAt < MISS_TTL_MS) return@withContext null
        }

        val artwork = runCatching {
            limiter.withLock { search(track) }
        }.getOrNull()

        if (artwork.isNullOrBlank()) {
            if (misses.size > 400) misses.clear()
            misses[key] = now
            null
        } else {
            if (cache.size > 400) cache.clear()
            cache[key] = artwork
            artwork
        }
    }

    private fun search(track: Track): String? {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        val body = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull() ?: return null

        return parseSearch(body, track)
    }

    /** Parses an iTunes song-search response into the best matching cover. */
    internal fun parseSearch(body: String, track: Track): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val results = root.arrayAt("results") ?: return null
        val candidates = results.mapNotNull { element ->
            candidate(element as? JsonObject ?: return@mapNotNull null)
        }
        return MusicArtworkMatch.best(track, candidates)?.artworkUrl
    }

    private fun candidate(item: JsonObject): ArtworkCandidate? {
        val title = item.stringAt("trackName") ?: return null
        val artwork = item.stringAt("artworkUrl100") ?: return null
        return ArtworkCandidate(
            title = title,
            artist = item.stringAt("artistName").orEmpty(),
            album = item.stringAt("collectionName"),
            durationMs = item.longAt("trackTimeMillis") ?: 0L,
            artworkUrl = artwork.replace(ARTWORK_SIZE, "/1200x1200bb."),
        )
    }

    private fun key(track: Track): String =
        listOf(track.title, track.artist, track.album.orEmpty())
            .joinToString("|") { MusicArtworkMatch.normalize(it) }
            .trim('|')

    private fun JsonObject.arrayAt(name: String): JsonArray? = this[name] as? JsonArray

    private fun JsonObject.stringAt(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longAt(name: String): Long? =
        (this[name] as? JsonPrimitive)?.longOrNull

    private companion object {
        const val USER_AGENT = "Meoify/1.7.3 (Android)"
        const val MISS_TTL_MS = 10 * 60 * 1000L

        val ARTWORK_SIZE = Regex("""/\d+x\d+bb\.""")
    }
}
