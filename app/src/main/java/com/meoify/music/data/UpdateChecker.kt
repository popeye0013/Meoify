package com.meoify.music.data

import com.meoify.music.data.model.DeveloperInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Looks up the newest published Meoify release on GitHub. */
object UpdateChecker {

    data class Release(val version: String, val url: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${DeveloperInfo.HANDLE}/Meoify/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Meoify-Android")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                if (tag.isBlank()) return@withContext null
                Release(
                    version = tag.removePrefix("v").removePrefix("V"),
                    url = root["html_url"]?.jsonPrimitive?.contentOrNull ?: DeveloperInfo.REPO_URL,
                )
            }
        }.getOrNull()
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
}
