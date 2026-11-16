package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Album artwork from Spotify's public catalogue.
 *
 * The Spotify web player receives an anonymous access token as part of every
 * embedded player page. That token is enough for the public track search, so
 * no account, client secret or API key is needed. Search results are matched
 * against the song before their album cover is used, which keeps karaoke and
 * tribute versions from replacing the real artwork.
 *
 * Every failure is soft: this returns null so the caller can try another music
 * catalogue or show a placeholder. It never falls back to a video frame.
 */
class SpotifyArtwork(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tokenLock = Mutex()
    private val searchLimiter = Semaphore(2)
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()

    @Volatile
    private var token: String? = null

    @Volatile
    private var tokenExpiresAt = 0L

    @Volatile
    private var blockedUntil = 0L

    /** The best Spotify album cover for [track], or null when none matches. */
    suspend fun cover(track: Track): String? = withContext(Dispatchers.IO) {
        val key = key(track)
        if (key.isBlank()) return@withContext null
        cache[key]?.let { return@withContext it }
        val now = System.currentTimeMillis()
        if (now < blockedUntil) return@withContext null
        (misses[key] ?: 0L).let { missedAt ->
            if (missedAt != 0L && now - missedAt < MISS_TTL_MS) return@withContext null
        }

        val artwork = runCatching {
            searchLimiter.withPermit { search(track) }
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

    private suspend fun search(track: Track): String? {
        val query = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        repeat(2) {
            val accessToken = accessToken() ?: return null
            val response = fetch(query, accessToken) ?: return null
            if (response.first == 401) {
                // The anonymous token expires about once an hour; fetch a new one.
                token = null
                tokenExpiresAt = 0L
                return@repeat
            }
            if (response.first == 429) {
                // Anonymous search is rate limited; let the Apple backup run
                // without hammering Spotify for every row on screen.
                blockedUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS
                return null
            }
            if (response.first !in 200..299) return null
            return parseSearch(response.second, track)
        }
        return null
    }

    /** Parses a Spotify track-search response into the best matching cover. */
    internal fun parseSearch(body: String, track: Track): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root.objectAt("tracks")?.arrayAt("items") ?: return null
        val candidates = items.mapNotNull { element ->
            runCatching { candidate(element.jsonObject) }.getOrNull()
        }
        return MusicArtworkMatch.best(track, candidates)?.artworkUrl
    }

    /** Reads the anonymous token and expiry out of an embedded player page. */
    internal fun parseToken(html: String): Pair<String, Long>? {
        val nextData = NEXT_DATA.find(html)?.groupValues?.getOrNull(1) ?: return null
        val root = runCatching { json.parseToJsonElement(nextData).jsonObject }.getOrNull()
            ?: return null
        val session = root.objectAt("props")
            ?.objectAt("pageProps")
            ?.objectAt("state")
            ?.objectAt("settings")
            ?.objectAt("session")
            ?: return null
        val value = session.stringAt("accessToken")?.takeIf { it.isNotBlank() } ?: return null
        val expiry = session.longAt("accessTokenExpirationTimestampMs")
            ?: (System.currentTimeMillis() + 50 * 60 * 1000L)
        return value to expiry
    }

    private fun candidate(item: JsonObject): ArtworkCandidate? {
        val title = item.stringAt("name") ?: return null
        val artist = item.arrayAt("artists")
            ?.mapNotNull { (it as? JsonObject)?.stringAt("name") }
            .orEmpty()
            .joinToString(", ")
        val album = item.objectAt("album")
        val cover = album?.arrayAt("images")
            ?.mapNotNull { image ->
                val obj = image as? JsonObject ?: return@mapNotNull null
                val url = obj.stringAt("url") ?: return@mapNotNull null
                url to (obj.intAt("width") ?: 0)
            }
            ?.maxByOrNull { it.second }
            ?.first
            ?: return null
        return ArtworkCandidate(
            title = title,
            artist = artist,
            album = album.stringAt("name"),
            durationMs = item.longAt("duration_ms") ?: 0L,
            artworkUrl = cover,
        )
    }

    /**
     * Reads the anonymous web-player token from Spotify's embedded player.
     * It is short-lived and only used for public search.
     */
    private suspend fun accessToken(): String? {
        val current = token
        if (!current.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt - 60_000L) {
            return current
        }
        return tokenLock.withLock {
            val fresh = token
            if (!fresh.isNullOrBlank() && System.currentTimeMillis() < tokenExpiresAt - 60_000L) {
                return@withLock fresh
            }

            val response = fetch(TOKEN_BOOTSTRAP_URL, null) ?: return@withLock null
            if (response.first !in 200..299) return@withLock null

            val (value, expiry) = parseToken(response.second) ?: return@withLock null
            token = value
            tokenExpiresAt = expiry
            value
        }
    }

    private fun fetch(url: String, accessToken: String?): Pair<Int, String>? =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply { accessToken?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        }.getOrNull()

    private fun key(track: Track): String =
        listOf(track.title, track.artist, track.album.orEmpty())
            .joinToString("|") { MusicArtworkMatch.normalize(it) }
            .trim('|')

    private fun JsonObject.objectAt(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.arrayAt(name: String): JsonArray? = this[name] as? JsonArray

    private fun JsonObject.stringAt(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longAt(name: String): Long? =
        (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intAt(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

    private companion object {
        /** A permanent public track; the page only bootstraps an anonymous token. */
        const val TOKEN_BOOTSTRAP_URL =
            "https://open.spotify.com/embed/track/4cOdK2wGLETKBW3PvgPWqT"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"

        const val MISS_TTL_MS = 10 * 60 * 1000L
        const val RATE_LIMIT_BACKOFF_MS = 5 * 60 * 1000L

        val NEXT_DATA = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
