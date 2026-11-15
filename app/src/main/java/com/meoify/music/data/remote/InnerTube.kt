package com.meoify.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest

class MusicApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * A tiny InnerTube client for the YouTube Music web client.
 * It only uses read endpoints: browse, search, next and suggestions.
 */
class InnerTube(private val client: OkHttpClient) {

    @Volatile
    private var visitorData: String? = null

    @Volatile
    private var cookies: String? = null

    fun setCookies(raw: String?) {
        cookies = raw
    }

    private fun sapisid(): String? {
        val raw = cookies ?: return null
        raw.split(';').forEach { part ->
            val trimmed = part.trim()
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                val name = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if ((name == "SAPISID" || name == "__Secure-3PAPISID") && value.isNotBlank()) {
                    return value
                }
            }
        }
        return null
    }

    private fun authHeader(): String? {
        val sid = sapisid() ?: return null
        val ts = System.currentTimeMillis() / 1000
        val input = "$ts $sid https://music.youtube.com"
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH $ts" + "_" + sha1
    }

    suspend fun browse(browseId: String, params: String? = null): JsonObject {
        return post(
            "browse",
            buildJsonObject {
                put("browseId", browseId)
                if (params != null) put("params", params)
            },
        )
    }

    suspend fun search(query: String, params: String? = null): JsonObject {
        return post(
            "search",
            buildJsonObject {
                put("query", query)
                if (params != null) put("params", params)
            },
        )
    }

    suspend fun suggestions(input: String): JsonObject {
        return post("music/get_search_suggestions", buildJsonObject { put("input", input) })
    }

    suspend fun next(videoId: String): JsonObject {
        return post("next", buildJsonObject { put("videoId", videoId) })
    }

    private suspend fun post(endpoint: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", CLIENT_VERSION)
                    put("hl", "en")
                    put("gl", "US")
                    put("timeZone", "UTC")
                    put("utcOffsetMinutes", 0)
                    visitorData?.let { put("visitorData", it) }
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
            }
            payload.forEach { (key, value) -> put(key, value) }
        }

        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", CLIENT_VERSION)
            .apply { authHeader()?.let { header("Authorization", it) } }
            .apply { if (cookies != null) header("X-Goog-AuthUser", "0") }
            .header("Cookie", cookies?.takeIf { it.isNotBlank() } ?: "SOCS=CAI")
            .header("User-Agent", USER_AGENT)
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw MusicApiException("YouTube Music returned ${response.code}")
                }
                val root = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw MusicApiException("Could not read the response from YouTube Music") }
                root["responseContext"]
                    ?.jsonObject
                    ?.get("visitorData")
                    ?.let { element ->
                        val value = element.toString().trim('"')
                        if (value.isNotBlank()) visitorData = value
                    }
                root
            }
        } catch (e: MusicApiException) {
            throw e
        } catch (e: IOException) {
            throw MusicApiException("Couldn't reach YouTube Music. Check your connection.", e)
        }
    }

    companion object {
        private const val CLIENT_VERSION = "1.20250101.00.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
