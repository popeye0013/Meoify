package com.meoify.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** One song as Spotify's public playlist page describes it. */
data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
)

data class SpotifyPlaylist(
    val name: String,
    val tracks: List<SpotifyTrack>,
)

/**
 * Reads a public Spotify playlist without an API key.
 *
 * Spotify's embed page renders the playlist in its `__NEXT_DATA__` JSON,
 * including the title and a `trackList` with song names, artists and
 * durations. That is enough to resolve the songs through YouTube Music search;
 * no Spotify account, token or client secret is needed.
 */
class SpotifyPlaylistClient(private val client: OkHttpClient) {

    /** Extracts a playlist id from an open.spotify.com link or a spotify: URI. */
    fun playlistId(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (value.startsWith("spotify:playlist:", ignoreCase = true)) {
            return value.substringAfterLast(':').takeIf { it.isNotBlank() }
        }
        val match = Regex("""(?:playlist/|playlist:)([A-Za-z0-9]+)""").find(value)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    suspend fun playlist(raw: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val id = playlistId(raw)
            ?: throw MusicApiException("Couldn't find a Spotify playlist id in that link.")
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$id?utm_source=generator")
            .header("User-Agent", InnerTube.USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        val html = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MusicApiException(
                        when (response.code) {
                            404 -> "That Spotify playlist doesn't exist."
                            401, 403 -> "That Spotify playlist is private."
                            else -> "Spotify returned ${response.code}."
                        },
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (e: MusicApiException) {
            throw e
        } catch (e: IOException) {
            throw MusicApiException("Couldn't reach Spotify. Check your connection.", e)
        }

        parse(html)
    }

    /** Visible for tests: turns an embed page into a playlist. */
    fun parse(html: String): SpotifyPlaylist {
        val nextData = NEXT_DATA.find(html)?.groupValues?.getOrNull(1)
            ?: throw MusicApiException("Spotify didn't send the playlist. Try the link again.")
        val root = runCatching { Json.parseToJsonElement(nextData).jsonObject }
            .getOrElse { throw MusicApiException("Couldn't read the Spotify playlist.") }
        val entity = findObjectWithKey(root, "trackList")
            ?: throw MusicApiException("That link isn't a public Spotify playlist.")

        val name = entity["name"].readString()
            ?: entity["title"].readString()
            ?: "Spotify playlist"

        val tracks = (entity["trackList"] as? JsonArray).orEmpty()
            .mapNotNull { item -> item.asObject()?.toTrack() }
            .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }

        return SpotifyPlaylist(name = name, tracks = tracks)
    }

    private fun JsonObject.toTrack(): SpotifyTrack? {
        val title = this["title"].readString()?.trim().orEmpty()
        if (title.isBlank()) return null

        val artist = this["subtitle"].readString()
            ?.let(::cleanArtist)
            .orEmpty()
            .ifBlank { artistsText() }

        val rawDuration = this["duration"]?.asLong()
            ?: this["durationMs"]?.asLong()
            ?: this["duration_ms"]?.asLong()
            ?: 0L
        // The embed page uses milliseconds, but a seconds value is cheap to
        // accept too in case Spotify changes the field.
        val durationMs = if (rawDuration in 1..10_000) rawDuration * 1000 else rawDuration

        return SpotifyTrack(title = title, artist = artist, durationMs = durationMs)
    }

    /** Spotify lists every featured artist in the subtitle; keep the main few. */
    private fun cleanArtist(raw: String): String =
        raw.replace('\u00A0', ' ')
            .substringBefore(" • ")
            .trim()
            .split(Regex("\\s*,\\s*"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(", ")

    private fun JsonObject.artistsText(): String {
        val artists = this["artists"] as? JsonArray ?: return ""
        return artists.mapNotNull { artist ->
            when (artist) {
                is JsonPrimitive -> artist.contentOrNull
                is JsonObject -> artist["name"].readString()
                else -> null
            }
        }.joinToString(", ")
    }

    private fun findObjectWithKey(element: JsonElement, key: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                if (element.containsKey(key)) return element
                element.values.forEach { value ->
                    findObjectWithKey(value, key)?.let { return it }
                }
            }

            is JsonArray -> element.forEach { value ->
                findObjectWithKey(value, key)?.let { return it }
            }

            else -> Unit
        }
        return null
    }

    private fun JsonElement?.readString(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }
        is JsonObject -> {
            this["name"].readString()
                ?: this["text"].readString()
                ?: this["title"].readString()
        }

        else -> null
    }

    private fun JsonElement?.asLong(): Long? = when (this) {
        is JsonPrimitive -> contentOrNull?.toLongOrNull()
        else -> null
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private companion object {
        val NEXT_DATA = Regex(
            """<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
    }
}
