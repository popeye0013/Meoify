package com.meoify.music.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ResolvedAudio(
    val url: String,
    val bitrate: Int,
    val format: String,
)

class StreamUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves a playable audio URL for a YouTube video. YouTube changes its
 * player regularly, so extraction is delegated to NewPipeExtractor, the same
 * battle-tested engine used by open source YouTube clients.
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

    suspend fun resolve(videoId: String, force: Boolean = false): ResolvedAudio = withContext(Dispatchers.IO) {
        val quality = settings.settings.value.quality
        // The cache key includes the quality so switching it takes effect on
        // the next track instead of waiting for the 20 minute entry to expire.
        val key = cacheKey(videoId, quality)
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
            val audio = extract(videoId, quality)
            cache[key] = CachedAudio(audio, System.currentTimeMillis())
            audio
        }
    }

    private fun cacheKey(videoId: String, quality: AudioQuality): String {
        val connection = if (quality == AudioQuality.AUTO) {
            if (isMeteredConnection()) "mobile" else "unmetered"
        } else {
            "fixed"
        }
        return "$videoId|${quality.name}|$connection"
    }

    private fun isMeteredConnection(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun extract(videoId: String, quality: AudioQuality): ResolvedAudio {
        val cap = when (quality) {
            // Auto: Wi-Fi keeps the best stream, mobile data stays balanced.
            AudioQuality.AUTO -> if (isMeteredConnection()) 160_000 else 256_000
            AudioQuality.HI_RES -> 256_000
            // YouTube Music has no 320 kbps stream; use the best it offers.
            AudioQuality.STANDARD -> 256_000
            AudioQuality.YOUTUBE_MUSIC -> 256_000
            AudioQuality.SAVER -> 96_000
        }
        return try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://music.youtube.com/watch?v=$videoId")
            val available = info.audioStreams.filter { !it.url.isNullOrBlank() }
            if (available.isEmpty()) {
                throw StreamUnavailableException("This track can't be streamed right now.")
            }

            val m4a = available.filter { it.format == MediaFormat.M4A }
            val pool = if (m4a.isNotEmpty()) m4a else available
            val underCap = pool.filter { (it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate) <= cap }
            val chosen = (underCap.maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
                ?: pool.minByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate })
                ?: throw StreamUnavailableException("This track can't be streamed right now.")

            val bitrate = chosen.averageBitrate.takeIf { it > 0 } ?: chosen.bitrate
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
