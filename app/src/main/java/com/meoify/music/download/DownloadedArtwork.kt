package com.meoify.music.download

import android.content.Context
import com.meoify.music.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Keeps a copy of each downloaded song's cover so the Downloads list and the
 * player still show artwork when YouTube is unreachable.
 *
 * The URL hash makes the file stable across app restarts and avoids storing a
 * separate image for every download of the same cover.
 */
class DownloadedArtwork(context: Context) {

    private val directory = File(context.applicationContext.filesDir, "downloaded-artwork")
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        runCatching { directory.mkdirs() }
    }

    fun fileFor(track: Track): File? {
        val url = track.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return null
        val file = File(directory, "${hash(url)}.img")
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    suspend fun ensure(track: Track) = withContext(Dispatchers.IO) {
        val url = track.thumbnailUrl?.takeIf { it.isNotBlank() } ?: return@withContext
        val target = File(directory, "${hash(url)}.img")
        if (target.exists() && target.length() > 0L) return@withContext
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty()
                if (!response.isSuccessful || !type.startsWith("image/")) return@use
                val bytes = response.body?.bytes() ?: return@use
                if (bytes.isEmpty()) return@use
                val temp = File(directory, "${hash(url)}.tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) {
                    target.writeBytes(bytes)
                    temp.delete()
                }
            }
        }
    }

    /** Removes artwork no longer referenced by any offline track. */
    fun prune(activeTracks: List<Track>) {
        val active = activeTracks.mapNotNull { it.thumbnailUrl?.takeIf { url -> url.isNotBlank() } }
            .map(::hash)
            .toSet()
        runCatching {
            directory.listFiles()?.forEach { file ->
                val key = file.nameWithoutExtension
                if (key !in active) file.delete()
            }
        }
    }

    fun clear() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
