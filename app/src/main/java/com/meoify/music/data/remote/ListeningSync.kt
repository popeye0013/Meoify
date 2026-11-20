package com.meoify.music.data.remote

import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Pushes the currently playing track to the GitHub profile README's
 * "Meoify Listening" widget.
 *
 * The profile README points at a Firebase Storage SVG
 * (`.../listening-on-ytmusic.svg?v=N`). This class regenerates that SVG from
 * the playing track and PUTs it to the configured URL. The README's `v=`
 * query is bumped by a GitHub Actions workflow so clients re-fetch it.
 *
 * Enable it in Settings -> GitHub profile sync and paste the Firebase Storage
 * URL. The storage rules must allow writes from the app (Firebase Auth or a
 * Cloud Function proxy is recommended over an open bucket).
 */
class ListeningSync(
    private val settings: SettingsStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private val scope = scope
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Call when the playing track changes. No-op when disabled or unconfigured. */
    fun onTrackChanged(track: Track?) {
        if (track == null) return
        scope.launch {
            val s = settings.current()
            if (!s.githubProfileSync) return@launch
            val url = s.githubProfileSyncUrl ?: return@launch
            val svg = buildSvg(track)
            upload(url, svg)
        }
    }

    private fun upload(url: String, svg: String): Boolean = runCatching {
        val body = svg.toRequestBody("image/svg+xml; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).put(body).build()
        client.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }.getOrDefault(false)

    companion object {
        /** Builds the SVG shown on the GitHub profile. Pure, testable. */
        fun buildSvg(track: Track): String {
            val title = escape(track.title.ifBlank { "Unknown track" })
            val artist = escape(track.artist.ifBlank { "Unknown artist" })
            val art = escape(track.thumbnailUrl.orEmpty())
            val imageTag = if (art.isNotBlank()) {
                """<image href="$art" x="8" y="8" width="104" height="104" preserveAspectRatio="xMidYMid slice" clip-path="url(#artClip)"/>"""
            } else {
                ""
            }
            return """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="400" height="120" viewBox="0 0 400 120" role="img" aria-label="$title by $artist">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1a1a2e"/>
      <stop offset="1" stop-color="#0d0d14"/>
    </linearGradient>
    <clipPath id="artClip">
      <rect x="8" y="8" width="104" height="104" rx="12"/>
    </clipPath>
  </defs>
  <rect width="400" height="120" rx="16" fill="url(#bg)"/>
  $imageTag
  <rect x="8" y="8" width="104" height="104" rx="12" fill="none" stroke="#ffffff" stroke-opacity="0.08"/>
  <text x="124" y="40" font-family="'Google Sans','Product Sans',Arial,sans-serif" font-size="16" font-weight="700" fill="#ffffff">$title</text>
  <text x="124" y="64" font-family="'Google Sans','Product Sans',Arial,sans-serif" font-size="13" font-weight="500" fill="#b3b3b3">$artist</text>
  <text x="124" y="96" font-family="'Google Sans','Product Sans',Arial,sans-serif" font-size="11" font-weight="600" fill="#FF4D67">Meoify · YouTube Music</text>
</svg>""".trimIndent()
        }

        /** Escapes text for safe inclusion in XML attributes and text nodes. */
        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
