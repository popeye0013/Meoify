package com.meoify.music.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.model.AudioQuality
import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ResolvedAudio(
    val url: String,
    val bitrate: Int,
    val format: String,
    /** Set for DASH manifests so ExoPlayer picks the DASH media source. */
    val mimeType: String? = null,
)

class StreamUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves a playable audio URL for a track.
 *
 * YouTube extraction is delegated to NewPipeExtractor. For the two higher
 * quality tiers, a configured hifi-api (Tidal-compatible) server is tried
 * first so "320 kbps" and "Lossless" are genuinely 320 kbps AAC and FLAC;
 * when no server is configured or it fails, playback falls back to the best
 * YouTube Music stream instead of erroring out.
 */
class StreamResolver(
    context: Context,
    private val settings: SettingsStore,
) {

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = ConcurrentHashMap<String, CachedAudio>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    init {
        NewPipe.init(
            NewPipeDownloader(client),
            Localization("en", "US"),
            ContentCountry("US"),
        )
    }

    suspend fun resolve(track: Track, force: Boolean = false): ResolvedAudio = withContext(Dispatchers.IO) {
        val current = settings.settings.value
        val quality = current.quality
        val server = current.losslessServer?.takeIf { it.isNotBlank() }
        // The cache key includes quality and server so switching either takes
        // effect on the next track instead of waiting for the TTL to expire.
        val key = cacheKey(track.id, quality, server)
        val cached = cache[key]
        if (!force && cached != null && System.currentTimeMillis() - cached.createdAt < CACHE_TTL_MS) {
            return@withContext cached.audio
        }

        val mutex = locks.getOrPut(key) { Mutex() }
        mutex.withLock {
            val again = cache[key]
            if (!force && again != null && System.currentTimeMillis() - again.createdAt < CACHE_TTL_MS) {
                return@withLock again.audio
            }
            val audio = if (server != null &&
                (quality == AudioQuality.HI_RES || quality == AudioQuality.STANDARD)
            ) {
                runCatching { resolveFromHifiApi(server, track, quality) }
                    .getOrElse { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        extract(track.id, quality)
                    }
            } else {
                extract(track.id, quality)
            }
            cache[key] = CachedAudio(audio, System.currentTimeMillis())
            audio
        }
    }

    private fun cacheKey(videoId: String, quality: AudioQuality, server: String?): String {
        val connection = if (quality == AudioQuality.AUTO) {
            if (isMeteredConnection()) "mobile" else "unmetered"
        } else {
            "fixed"
        }
        return "$videoId|${quality.name}|$connection|${server.orEmpty()}"
    }

    private fun isMeteredConnection(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    // ---------------------------------------------------------------------
    // hifi-api / Tidal
    // ---------------------------------------------------------------------

    private suspend fun resolveFromHifiApi(
        server: String,
        track: Track,
        quality: AudioQuality,
    ): ResolvedAudio {
        val query = listOf(track.title.trim(), track.artist.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { throw StreamUnavailableException("This track has no searchable title.") }

        val results = getJson(server, "search", "s" to query, "limit" to "10")
        val items = results.jsonObject["data"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
        val match = items.mapNotNull { it as? JsonObject }
            .map { it to matchScore(it, track) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
            ?: throw StreamUnavailableException("This track wasn't found on the lossless server.")

        if (match["allowStreaming"]?.jsonPrimitive?.contentOrNull == "false" ||
            match["streamReady"]?.jsonPrimitive?.contentOrNull == "false"
        ) {
            throw StreamUnavailableException("This track isn't streamable on the lossless server.")
        }
        val tidalId = match["id"]?.jsonPrimitive?.contentOrNull
            ?: throw StreamUnavailableException("The lossless server returned an invalid track.")

        val preferred = if (quality == AudioQuality.HI_RES) "HI_RES_LOSSLESS" else "HIGH"
        var audio = streamFromTrackEndpoint(server, tidalId, preferred, quality)
        if (audio == null && quality == AudioQuality.HI_RES) {
            // Some catalogues only have 16-bit CD lossless, which is still a
            // real FLAC stream and better than falling back to lossy AAC.
            audio = streamFromTrackEndpoint(server, tidalId, "LOSSLESS", quality)
        }
        if (audio == null) {
            audio = streamFromManifests(server, tidalId, quality)
        }
        return audio ?: throw StreamUnavailableException(
            "The lossless server couldn't provide this track in the selected quality.",
        )
    }

    /** `/track/` returns a base64 BTS manifest with a direct FLAC/AAC URL, or a base64 DASH manifest. */
    private suspend fun streamFromTrackEndpoint(
        server: String,
        tidalId: String,
        tidalQuality: String,
        quality: AudioQuality,
    ): ResolvedAudio? {
        val data = runCatching {
            getJson(server, "track", "id" to tidalId, "quality" to tidalQuality)["data"]?.jsonObject
        }.getOrNull() ?: return null
        val mime = data["manifestMimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val manifest = data["manifest"]?.jsonPrimitive?.contentOrNull ?: return null
        if (!mime.contains("bts", ignoreCase = true)) {
            // Hi-Res normally arrives as a base64 DASH manifest. ExoPlayer
            // needs a real manifest URL, so re-request it with uriScheme=HTTPS.
            return if (mime.contains("dash", ignoreCase = true)) {
                streamFromManifests(server, tidalId, quality)
            } else {
                null
            }
        }

        val bts = runCatching {
            Json.parseToJsonElement(String(decodeBase64(manifest), Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: return null
        val url = bts["urls"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("http") }
            ?: return null
        val codecs = bts["codecs"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sampleRate = data["sampleRate"]?.jsonPrimitive?.intOrNull ?: 0
        val bitDepth = data["bitDepth"]?.jsonPrimitive?.intOrNull ?: 0
        val bitrate = if (sampleRate > 0 && bitDepth > 0) {
            sampleRate * bitDepth * 2 / 1000
        } else if (codecs.contains("mp4a", ignoreCase = true)) {
            320
        } else {
            0
        }
        return ResolvedAudio(url, bitrate, codecs.ifBlank { data.string("audioQuality").orEmpty() })
    }

    private suspend fun streamFromManifests(
        server: String,
        tidalId: String,
        quality: AudioQuality,
    ): ResolvedAudio? {
        val formats = if (quality == AudioQuality.HI_RES) {
            "FLAC,FLAC_HIRES"
        } else {
            "AACLC"
        }
        val data = runCatching {
            getJson(
                server,
                "trackManifests",
                "id" to tidalId,
                "formats" to formats,
                "adaptive" to "true",
                "manifestType" to "MPEG_DASH",
                "uriScheme" to "HTTPS",
                "usage" to "PLAYBACK",
            )["data"]?.jsonObject?.get("data")?.jsonObject?.get("attributes")?.jsonObject
        }.getOrNull() ?: return null
        val uri = data["uri"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
            ?: return null
        return ResolvedAudio(
            url = uri,
            bitrate = if (quality == AudioQuality.HI_RES) 0 else 320,
            format = if (quality == AudioQuality.HI_RES) "FLAC DASH" else "AAC DASH",
            mimeType = "application/dash+xml",
        )
    }

    /** Returns a positive score when a search result plausibly matches the track. */
    private fun matchScore(item: JsonObject, track: Track): Int {
        val title = item.string("title").orEmpty()
        val artist = item["artist"]?.jsonObject?.string("name")
            ?: item["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.string("name")
            .orEmpty()
        if (title.isBlank()) return 0

        val wantedTitle = tokens(track.title)
        val foundTitle = tokens(title)
        val wantedArtist = tokens(track.artist)
        val foundArtist = tokens(artist)
        val titleHits = wantedTitle.count { it in foundTitle }
        if (titleHits == 0) return 0

        var score = 20 * titleHits
        if (normalize(title) == normalize(track.title)) score += 60
        if (wantedArtist.isNotEmpty() && wantedArtist.any { it in foundArtist }) score += 30

        val duration = item["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val wantedSeconds = track.durationMs / 1000
        if (duration > 0 && wantedSeconds > 0) {
            val delta = kotlin.math.abs(duration - wantedSeconds)
            when {
                delta <= 3 -> score += 25
                delta <= 15 -> score += 10
                delta > 45 -> score -= 30
            }
        }
        return score
    }

    private fun tokens(value: String): Set<String> = normalize(value).split(' ').filter { it.isNotBlank() }.toSet()

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun buildUrl(base: String, path: String, vararg params: Pair<String, String>): String {
        val root = base.toHttpUrlOrNull()
            ?: throw StreamUnavailableException("The lossless server URL isn't valid.")
        val builder = root.newBuilder().addPathSegments(path.trim('/'))
        params.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build().toString()
    }

    /** GETs JSON, following hifi-api's playback queue when accounts are busy. */
    private suspend fun getJson(base: String, path: String, vararg params: Pair<String, String>): JsonObject {
        var target = buildUrl(base, path, *params)
        var result: JsonObject? = null
        repeat(8) { attempt ->
            val request = Request.Builder().url(target).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 202) {
                    val status = runCatching { Json.parseToJsonElement(text).jsonObject.string("statusUrl") }
                        .getOrNull()
                    if (status != null) {
                        target = if (status.startsWith("http")) {
                            status
                        } else {
                            base.trimEnd('/') + status
                        }
                        delay(1000L + attempt * 250L)
                        return@use
                    }
                }
                if (!response.isSuccessful) {
                    throw StreamUnavailableException("Lossless server returned HTTP ${response.code}.")
                }
                if (text.isBlank()) throw StreamUnavailableException("Lossless server returned an empty response.")
                result = Json.parseToJsonElement(text).jsonObject
            }
            if (result != null) return result
        }
        throw StreamUnavailableException("The lossless server is busy. Try again in a moment.")
    }

    private fun decodeBase64(value: String): ByteArray {
        val clean = value.replace(Regex("\\s"), "")
        return runCatching { Base64.getDecoder().decode(clean) }
            .getOrElse { Base64.getUrlDecoder().decode(clean) }
    }

    // ---------------------------------------------------------------------
    // YouTube Music fallback
    // ---------------------------------------------------------------------

    private fun extract(videoId: String, quality: AudioQuality): ResolvedAudio {
        val cap = when (quality) {
            AudioQuality.AUTO -> if (isMeteredConnection()) 160_000 else 256_000
            // YouTube Music has no true 320 kbps or lossless stream; when the
            // hifi server is unavailable these tiers use the best it offers.
            AudioQuality.HI_RES -> Int.MAX_VALUE
            AudioQuality.STANDARD -> 320_000
            AudioQuality.YOUTUBE_MUSIC -> 256_000
            AudioQuality.SAVER -> 96_000
        }
        return try {
            val info = try {
                StreamInfo.getInfo(ServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            } catch (e: Exception) {
                StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            }
            val available = info.audioStreams.filter { !it.url.isNullOrBlank() }
            if (available.isEmpty()) {
                throw StreamUnavailableException("This track can't be streamed right now.")
            }

            fun bitrateOf(stream: AudioStream): Int =
                stream.averageBitrate.takeIf { it > 0 } ?: stream.bitrate

            val underCap = available.filter { bitrateOf(it) <= cap }
            val chosen = (underCap.maxByOrNull { bitrateOf(it) }
                ?: available.maxByOrNull { bitrateOf(it) })
                ?: throw StreamUnavailableException("This track can't be streamed right now.")

            val bitrate = bitrateOf(chosen)
            ResolvedAudio(
                url = chosen.url ?: throw StreamUnavailableException("This track can't be streamed right now."),
                bitrate = bitrate,
                format = chosen.format?.name ?: "audio",
            )
        } catch (e: StreamUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw StreamUnavailableException(
                "Couldn't get a stream for this track. It may be unavailable in your region.",
                e,
            )
        }
    }

    private class CachedAudio(val audio: ResolvedAudio, val createdAt: Long)

    private companion object {
        const val CACHE_TTL_MS = 20 * 60 * 1000L
    }
}

private class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NPRequest): NPResponse {
        val body = request.dataToSend()?.let { data ->
            val type = request.headers()["Content-Type"]?.firstOrNull()
                ?.substringBefore(';')?.trim()
                ?.toMediaType()
                ?: "application/json; charset=utf-8".toMediaType()
            data.toRequestBody(type)
        }

        val builder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)

        request.headers().forEach { (name, values) ->
            if (name.equals("Content-Length", true) ||
                name.equals("Host", true) ||
                name.equals("Connection", true) ||
                name.equals("Accept-Encoding", true)
            ) {
                return@forEach
            }
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", InnerTube.USER_AGENT)
        }
        if (request.headers()["Accept-Language"].isNullOrEmpty()) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                return NPResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        } catch (e: Exception) {
            throw java.io.IOException("Network request failed", e)
        }
    }
}
