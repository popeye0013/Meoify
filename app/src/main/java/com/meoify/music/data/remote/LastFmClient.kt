package com.meoify.music.data.remote

import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

/**
 * Minimal Last.fm scrobbling client.
 *
 * Uses the official mobile-session flow: authenticate once with the user's
 * Last.fm username and password to obtain a session key, then sign every
 * submission with the app's API secret. The password is never stored; only the
 * returned session key is kept in Settings.
 */
class LastFmClient(private val client: OkHttpClient) {

    data class Session(val key: String, val username: String)

    suspend fun mobileSession(
        apiKey: String,
        apiSecret: String,
        username: String,
        password: String,
    ): Session = withContext(Dispatchers.IO) {
        val params = mapOf(
            "method" to "auth.getMobileSession",
            "api_key" to apiKey,
            "username" to username,
            "password" to password,
        )
        val root = call(params, apiSecret)
        val session = root["session"]?.jsonObject
            ?: throw MusicApiException(lastFmError(root) ?: "Last.fm didn't return a session.")
        val key = session["key"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw MusicApiException("Last.fm didn't return a session key.")
        val name = session["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: username
        Session(key = key, username = name)
    }

    suspend fun updateNowPlaying(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        track: Track,
    ): Boolean = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist" to track.artist,
            "track" to track.title,
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        if (track.durationMs > 0) params["duration"] = (track.durationMs / 1000).toString()
        runCatching { call(params, apiSecret); true }.getOrDefault(false)
    }

    suspend fun scrobble(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        track: Track,
        timestampSeconds: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist[0]" to track.artist,
            "track[0]" to track.title,
            "timestamp[0]" to timestampSeconds.toString(),
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album[0]"] = it }
        if (track.durationMs > 0) params["duration[0]"] = (track.durationMs / 1000).toString()
        runCatching { call(params, apiSecret); true }.getOrDefault(false)
    }

    /** Posts a signed request and returns the parsed JSON response. */
    private fun call(params: Map<String, String>, apiSecret: String): kotlinx.serialization.json.JsonObject {
        val signed = params.toMutableMap()
        signed["api_sig"] = signature(signed, apiSecret)
        signed["format"] = "json"

        val body = FormBody.Builder().apply {
            signed.forEach { add(it.key, it.value) }
        }.build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body)
            .header("User-Agent", InnerTube.USER_AGENT)
            .build()

        val response = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw MusicApiException("Last.fm returned ${response.code}")
                }
                text
            }
        } catch (e: MusicApiException) {
            throw e
        } catch (e: IOException) {
            throw MusicApiException("Couldn't reach Last.fm.", e)
        }

        val root = runCatching { Json.parseToJsonElement(response).jsonObject }
            .getOrElse { throw MusicApiException("Couldn't read Last.fm's response.") }
        if (root.containsKey("error")) {
            throw MusicApiException(lastFmError(root) ?: "Last.fm rejected the request.")
        }
        return root
    }

    private fun signature(params: Map<String, String>, secret: String): String {
        val raw = params.toSortedMap()
            .map { (key, value) -> "$key$value" }
            .joinToString("") + secret
        val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun lastFmError(root: kotlinx.serialization.json.JsonObject): String? =
        root["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    }
}
