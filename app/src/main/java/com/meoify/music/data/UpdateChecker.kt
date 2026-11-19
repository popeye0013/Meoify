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
 * Looks up the newest published Meoify release.
 *
 * The Meoify repository is private, so an anonymous call to the GitHub API
 * cannot see its releases (it answers 404). A tiny public manifest is kept in
 * the profile repository and is read first; the GitHub API is still tried as a
 * fallback so the check keeps working if the repository is ever made public.
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

    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/${DeveloperInfo.HANDLE}/${DeveloperInfo.HANDLE}/main/meoify-latest.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        fromManifest() ?: fromGitHubApi()
    }

    private fun fromManifest(): Release? = runCatching {
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("User-Agent", "Meoify-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val version = root["version"]
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
                .trim()
                .removePrefix("v")
                .removePrefix("V")
            if (version.isBlank()) return@runCatching null
            Release(
                version = version,
                url = root["release_url"]?.jsonPrimitive?.contentOrNull ?: DeveloperInfo.REPO_URL,
                apkUrl = root["apk_url"]?.jsonPrimitive?.contentOrNull,
                notes = (root["changelog"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                published = root["published"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }.getOrNull()

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
        runCatching {
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
    }

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
