package com.meoify.music.data

import com.meoify.music.data.model.DeveloperInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Looks up the newest published Meoify release on GitHub.
 *
 * GitHub releases are the single source of truth for both the update prompt
 * and the in-app changelog, so the two always show the same notes.
 */
object UpdateChecker {

    data class Release(
        val version: String,
        val url: String,
        val apkUrl: String? = null,
        /** Changelog bullets, newest release first. */
        val notes: List<String> = emptyList(),
        /** ISO publish date from GitHub or the manifest, when available. */
        val published: String? = null,
    )

    private const val RELEASES_URL = "https://github.com/${DeveloperInfo.HANDLE}/Meoify/releases"

    /**
     * GitHub's release feed. Unlike the REST API it is not limited to 60
     * anonymous requests per hour, so it is used whenever the API is rate
     * limited, blocked by a network, or otherwise unavailable.
     */
    private const val ATOM_URL = "$RELEASES_URL.atom"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        fromGitHubApi() ?: fromAtomFeed(limit = 1).firstOrNull()
    }

    private fun fromGitHubApi(): Release? = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${DeveloperInfo.HANDLE}/Meoify/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Meoify-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            parseGithubRelease(root)
        }
    }.getOrNull()

    /**
     * Fetches the [limit] most recent GitHub releases (newest first) with their
     * changelogs, so people can read what changed across updates before they
     * decide to download. The releases endpoint is public for this repository.
     */
    suspend fun history(limit: Int = 10): List<Release> = withContext(Dispatchers.IO) {
        val fromApi = runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${DeveloperInfo.HANDLE}/Meoify/releases?per_page=$limit")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Meoify-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                json.parseToJsonElement(response.body?.string().orEmpty())
                    .jsonArray
                    .mapNotNull { (it as? JsonObject)?.let(::parseGithubRelease) }
            }
        }.getOrElse { emptyList() }
        fromApi.ifEmpty { fromAtomFeed(limit) }
    }

    private fun fromAtomFeed(limit: Int): List<Release> = runCatching {
        val request = Request.Builder()
            .url(ATOM_URL)
            .header("User-Agent", "Meoify-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching emptyList()
            parseAtomFeed(response.body?.string().orEmpty(), limit)
        }
    }.getOrElse { emptyList() }

    /**
     * Reads the public releases Atom feed. Each entry exposes the tag in its
     * link, the version in its title, and the changelog as escaped HTML.
     */
    internal fun parseAtomFeed(xml: String, limit: Int = 10): List<Release> {
        if (xml.isBlank()) return emptyList()
        return xml.split("<entry>")
            .drop(1)
            .mapNotNull { chunk -> parseAtomEntry(chunk.substringBefore("</entry>")) }
            .take(limit)
    }

    private fun parseAtomEntry(entry: String): Release? {
        val title = entry.substringAfter("<title>", "").substringBefore("</title>").trim()
        val link = entry.substringAfter("rel=\"alternate\"", "")
            .substringAfter("href=\"", "")
            .substringBefore("\"")
            .trim()
        val tag = link.substringAfterLast("/tag/", "")
            .ifBlank { title.removePrefix("Meoify").trim() }
        val version = tag.removePrefix("v").removePrefix("V").trim()
        if (version.isBlank()) return null
        val updated = entry.substringAfter("<updated>", "").substringBefore("</updated>").trim()
        val content = entry.substringAfter("<content type=\"html\">", "")
            .substringBefore("</content>")
        return Release(
            version = version,
            url = link.ifBlank { "$RELEASES_URL/tag/v$version" },
            notes = parseAtomNotes(content),
            published = updated.ifBlank { null },
        )
    }

    private fun parseAtomNotes(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        // The feed escapes the HTML for XML, and the HTML escapes its own
        // entities, so the text is unescaped once before and once after tags
        // are stripped (e.g. "&amp;amp;" in the feed becomes "&" in the note).
        val html = unescapeHtml(content)
        val bullets = Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { match -> unescapeHtml(stripTags(match.groupValues[1])).trim() }
            .filter { it.isNotEmpty() }
            .take(8)
            .toList()
        return bullets.ifEmpty { parseNotes(unescapeHtml(stripTags(html))) }
    }

    private fun stripTags(value: String): String = value.replace(Regex("<[^>]+>"), "")

    private fun unescapeHtml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&amp;", "&")

    private fun parseGithubRelease(root: JsonObject): Release? {
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (tag.isBlank()) return null
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { asset ->
            asset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            url = root["html_url"]?.jsonPrimitive?.contentOrNull ?: DeveloperInfo.REPO_URL,
            apkUrl = assets?.firstOrNull { it.endsWith(".apk", ignoreCase = true) },
            notes = parseNotes(root["body"]?.jsonPrimitive?.contentOrNull),
            published = root["published_at"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** True when [candidate] is a newer dotted version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".", "-").mapNotNull { it.toIntOrNull() }
        val b = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Pulls the bullet lines (`- ...` / `* ...`) out of a release body. */
    internal fun parseNotes(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.removePrefix("- ")
                    .removePrefix("* ")
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .take(8)
            .toList()
    }
}
