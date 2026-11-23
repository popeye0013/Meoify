package com.meoify.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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

    @Volatile
    private var cachedVersionDay: String? = null

    @Volatile
    private var cachedVersion: String? = null

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

    /** Fetches the next batch of rows for a browse page. */
    suspend fun browseContinuation(token: String): JsonObject {
        return post("browse", buildJsonObject { put("continuation", token) })
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

    suspend fun next(videoId: String, playlistId: String? = null): JsonObject {
        return post(
            "next",
            buildJsonObject {
                put("videoId", videoId)
                if (playlistId != null) put("playlistId", playlistId)
            },
        )
    }

    /**
     * The account menu holds the signed-in user's name, email and avatar.
     * It is only useful when cookies are set; YTMusicParser.parseAccount is
     * defensive about the many shapes this response takes.
     */
    suspend fun accountMenu(): JsonObject {
        return post("account/account_menu", buildJsonObject { })
    }

    /** One authenticated play-tracking ping, plus the session that produced it. */
    data class HistoryPlayback(
        val trackingUrl: String,
        val visitorData: String?,
    )

    /**
     * Asks the authenticated player for the per-play tracking URL used to
     * register a listen in YouTube Music history. Anonymous responses return a
     * URL whose ping is accepted but never recorded, so this returns null when
     * no cookies are present.
     *
     * The visitor id is returned with the URL because YouTube only credits the
     * listen to the account when the ping carries the same visitor id as the
     * player call that produced it.
     */
    suspend fun historyPlayback(videoId: String): HistoryPlayback? {
        if (cookies.isNullOrBlank()) return null
        // Make sure a visitor id exists before asking for the tracking URL.
        if (visitorData.isNullOrBlank()) runCatching { post("browse", buildJsonObject { put("browseId", "FEmusic_home") }) }
        val root = post(
            "player",
            buildJsonObject {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                // Same shape ytmusicapi sends: the player needs a playback
                // context before it hands out a usable tracking URL.
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("signatureTimestamp", System.currentTimeMillis() / 1000 - 1)
                    }
                }
            },
        )
        val url = root["playbackTracking"]
            ?.jsonObject
            ?.get("videostatsPlaybackUrl")
            ?.jsonObject
            ?.get("baseUrl")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        val pingVisitor = root["responseContext"]
            ?.jsonObject
            ?.get("visitorData")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: visitorData
        return HistoryPlayback(url, pingVisitor)
    }

    /**
     * Registers one listen in the signed-in account's YouTube Music history by
     * GET-ing the tracking URL, exactly as ytmusicapi's `add_history_item` does.
     * Returns the HTTP status; 204 means YouTube accepted the ping.
     *
     * The ping deliberately repeats the visitor id and the SAPISIDHASH
     * authorization of the player call: without them YouTube answers 204 but
     * never writes the item to the account's history.
     */
    suspend fun submitHistoryPlayback(
        trackingBaseUrl: String,
        cpn: String,
        visitorData: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        val base = trackingBaseUrl.toHttpUrlOrNull() ?: return@withContext 0
        if (!base.isHttps || (base.host != "youtube.com" && !base.host.endsWith(".youtube.com"))) {
            return@withContext 0
        }
        val url = base.newBuilder()
            .setQueryParameter("ver", "2")
            .setQueryParameter("c", "WEB_REMIX")
            .setQueryParameter("cpn", cpn)
            .build()
        val visitor = visitorData ?: this@InnerTube.visitorData
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", "https://music.youtube.com")
            .header("X-Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", clientVersion())
            .apply { authHeader()?.let { header("Authorization", it) } }
            .apply { if (cookies != null) header("X-Goog-AuthUser", "0") }
            .apply { visitor?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) } }
            .header("Cookie", cookies?.takeIf { it.isNotBlank() } ?: "SOCS=CAI")
            .build()
        try {
            client.newCall(request).execute().use { it.code }
        } catch (e: IOException) {
            throw MusicApiException("Couldn't reach YouTube Music history.", e)
        }
    }

    /** Adds or removes a song from the account's Liked music (LM). */
    suspend fun rateSong(videoId: String, liked: Boolean): Boolean = runCatching {
        val endpoint = if (liked) "like/like" else "like/removelike"
        post(
            endpoint,
            buildJsonObject {
                putJsonObject("target") { put("videoId", videoId) }
            },
        )
        // The like endpoint usually answers with a status field, but some
        // sessions only return a response context. A 2xx response is enough.
        true
    }.getOrDefault(false)

    /** Creates a private playlist in the account and returns its id. */
    suspend fun createPlaylist(title: String, videoIds: List<String> = emptyList()): String? {
        val root = post(
            "playlist/create",
            buildJsonObject {
                put("title", title)
                put("description", "")
                put("privacyStatus", "PRIVATE")
                if (videoIds.isNotEmpty()) {
                    putJsonArray("videoIds") { videoIds.forEach { add(it) } }
                }
            },
        )
        return root["playlistId"]?.jsonPrimitive?.contentOrNull
    }

    suspend fun deletePlaylist(playlistId: String): Boolean = runCatching {
        post(
            "playlist/delete",
            buildJsonObject { put("playlistId", playlistId.removePrefix("VL")) },
        )
        true
    }.getOrDefault(false)

    suspend fun renamePlaylist(playlistId: String, title: String): Boolean = runCatching {
        post(
            "browse/edit_playlist",
            buildJsonObject {
                put("playlistId", playlistId.removePrefix("VL"))
                putJsonArray("actions") {
                    addJsonObject {
                        put("action", "ACTION_SET_PLAYLIST_NAME")
                        put("playlistName", title)
                    }
                }
            },
        )
    }.isSuccess

    /**
     * Updates the parts of a playlist the signed-in account owns. Only the
     * fields that are passed are sent, so an edit never clears a description
     * or changes privacy by accident.
     */
    suspend fun editPlaylist(
        playlistId: String,
        title: String? = null,
        description: String? = null,
        privacy: String? = null,
    ): Boolean = runCatching {
        val actions = buildJsonArray {
            if (title != null) {
                addJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_NAME")
                    put("playlistName", title)
                }
            }
            if (description != null) {
                addJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    put("playlistDescription", description)
                }
            }
            if (privacy != null) {
                addJsonObject {
                    put("action", "ACTION_SET_PLAYLIST_PRIVACY")
                    put("playlistPrivacy", privacy.uppercase())
                }
            }
        }
        if (actions.isEmpty()) return@runCatching true
        val root = post(
            "browse/edit_playlist",
            buildJsonObject {
                put("playlistId", playlistId.removePrefix("VL"))
                put("actions", actions)
            },
        )
        root["status"]?.jsonPrimitive?.contentOrNull != "STATUS_FAILED"
    }.getOrDefault(false)

    /**
     * Applies the playlist comment setting. YouTube Music only publishes this
     * for some accounts and regions, so the request is sent on its own and a
     * rejection must not roll back a title, description or privacy change.
     */
    suspend fun setPlaylistComments(playlistId: String, comments: String): Boolean = runCatching {
        val root = post(
            "browse/edit_playlist",
            buildJsonObject {
                put("playlistId", playlistId.removePrefix("VL"))
                putJsonArray("actions") {
                    addJsonObject {
                        put("action", "ACTION_SET_PLAYLIST_COMMENTS")
                        put("playlistComments", comments.uppercase())
                    }
                }
            },
        )
        root["status"]?.jsonPrimitive?.contentOrNull != "STATUS_FAILED"
    }.getOrDefault(false)

    suspend fun addToPlaylist(playlistId: String, videoId: String): Boolean = runCatching {
        post(
            "browse/edit_playlist",
            buildJsonObject {
                put("playlistId", playlistId.removePrefix("VL"))
                putJsonArray("actions") {
                    addJsonObject {
                        put("action", "ACTION_ADD_VIDEO")
                        put("addedVideoId", videoId)
                    }
                }
            },
        )
    }.isSuccess

    /**
     * Removes one entry from a playlist. YouTube Music identifies the entry by
     * its per-playlist `setVideoId`; sending only the video id is rejected.
     */
    /**
     * Returns (success, reason). `reason` is the HTTP status code or the
     * YouTube `status` field (or an exception message) so failures can be
     * surfaced instead of swallowed.
     */
    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String?,
    ): Pair<Boolean, String> = runCatching {
        val root = post(
            "browse/edit_playlist",
            buildJsonObject {
                put("playlistId", playlistId.removePrefix("VL"))
                putJsonArray("actions") {
                    addJsonObject {
                        put("action", "ACTION_REMOVE_VIDEO")
                        put("removedVideoId", videoId)
                        setVideoId?.takeIf { it.isNotBlank() }?.let { put("setVideoId", it) }
                    }
                }
            },
        )
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        val ok = status == null || !status.contains("FAILED", ignoreCase = true)
        ok to (status ?: "HTTP ok")
    }.getOrElse { false to (it.message ?: "exception") }

    private suspend fun post(endpoint: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", clientVersion())
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
            .header("X-Youtube-Client-Version", clientVersion())
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
            throw MusicApiException("No connection right now. Reconnect to get back to your music.", e)
        }
    }

    /**
     * YouTube Music rejects web-client requests whose version is stale, so the
     * version tracks the current UTC date the same way ytmusicapi does.
     */
    private fun clientVersion(): String {
        val day = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
        val cached = cachedVersion
        if (cached != null && cachedVersionDay == day) return cached
        val version = "1.$day.01.00"
        cachedVersionDay = day
        cachedVersion = version
        return version
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
