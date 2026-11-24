package com.meoify.music.data.model

import kotlinx.serialization.Serializable

/** Whether an offline file is a permanent download or a temporary cache entry. */
const val OFFLINE_KIND_DOWNLOAD = "DOWNLOAD"
const val OFFLINE_KIND_CACHE = "CACHE"

@Serializable
enum class DownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELLED,
    ;

    val isRunnable: Boolean get() = this == QUEUED || this == RUNNING
}

/** A track stored on the device, with the local path used for playback. */
@Serializable
data class DownloadedTrack(
    val track: Track,
    val filePath: String,
    val mimeType: String? = null,
    val fileSize: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = downloadedAt,
    /** Kind is kept so temporary cache files can be evicted before downloads. */
    val kind: String = OFFLINE_KIND_DOWNLOAD,
    /** Set when the file was downloaded as part of a tracked playlist. */
    val sourcePlaylistId: String? = null,
)

/** One item in the serialized download queue. */
@Serializable
data class DownloadQueueItem(
    val track: Track,
    val state: DownloadState = DownloadState.QUEUED,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = enqueuedAt,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val sourcePlaylistId: String? = null,
    /** Groups a bulk playlist/album download so it can chime once when done. */
    val batchId: String? = null,
    val batchName: String? = null,
    val batchTrackIds: List<String> = emptyList(),
)

/** A playlist whose new songs are downloaded automatically. */
@Serializable
data class TrackedPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val trackedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DownloadPreferences(
    /** Offline storage limit in MiB. Zero means unlimited. */
    val maxOfflineMiB: Int = 0,
    val autoDownloadOnLike: Boolean = false,
)

/** Failures shown in the queue UI, grouped so retry guidance is useful. */
@Serializable
enum class DownloadFailure {
    STORAGE_FULL,
    STORAGE_ACCESS,
    NETWORK,
    UNSUPPORTED_FORMAT,
    OTHER,
    ;

    companion object {
        fun from(throwable: Throwable): DownloadFailure = when {
            throwable is UnsupportedDownloadFormatException -> UNSUPPORTED_FORMAT
            throwable is java.io.IOException && throwable.message.orEmpty().contains("space", true) -> STORAGE_FULL
            throwable is SecurityException -> STORAGE_ACCESS
            throwable is java.net.SocketTimeoutException -> NETWORK
            throwable is java.net.UnknownHostException -> NETWORK
            throwable is java.io.IOException -> NETWORK
            else -> OTHER
        }
    }
}

class UnsupportedDownloadFormatException(message: String) : Exception(message)

/** Sanitized file name component. YouTube ids are URL-safe but be defensive. */
fun safeDownloadId(id: String): String {
    val cleaned = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160)
    return if (cleaned.any { it.isLetterOrDigit() }) cleaned else "song"
}

/** Human readable, filesystem-safe name used for retained artwork files. */
fun downloadBaseName(artist: String, title: String, fallbackId: String): String =
    listOf(artist.trim(), title.trim())
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .replace(Regex("""[\\/:*?"<>|\p{Cc}]"""), "_")
        .trim(' ', '.')
        .take(120)
        .ifBlank { "song_${safeDownloadId(fallbackId)}" }

fun downloadExtension(mimeType: String?, format: String? = null, url: String? = null): String {
    val raw = (mimeType ?: format ?: url ?: "").substringBefore(';').trim().lowercase()
    return when {
        raw.contains("flac") -> ".flac"
        raw.contains("opus") || raw.contains("ogg") -> ".opus"
        raw.contains("webm") -> ".webm"
        raw.contains("mpeg") || raw.contains("mp3") -> ".mp3"
        raw.contains("m4a") || raw.contains("mp4") || raw.contains("aac") -> ".m4a"
        raw.contains("wav") -> ".wav"
        else -> ".audio"
    }
}

fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
