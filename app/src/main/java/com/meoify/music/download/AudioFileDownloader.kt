package com.meoify.music.download

import com.meoify.music.data.model.downloadExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class DownloadResult(
    val size: Long,
    val mimeType: String?,
    val extension: String,
)

/**
 * Streams an audio URL into a file.
 *
 * One ranged GET finds the total size and whether parallel ranges are supported.
 * When they are, the file is fetched in 8 chunks; when a server ignores Range
 * and returns the whole body, that same response is streamed instead of being
 * thrown away and requested a second time.
 */
object AudioFileDownloader {

    private const val MIN_RANGE_BYTES = 1L * 1024 * 1024L
    private const val RANGE_CHUNKS = 8
    private const val BUFFER_SIZE = 256 * 1024
    private const val PROGRESS_STEP = 256 * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        // OkHttp defaults to 5 requests per host, which would queue most chunks.
        .dispatcher(Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 16
        })
        .connectionPool(ConnectionPool(12, 2, TimeUnit.MINUTES))
        .build()

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destination: File,
        onCall: (Call) -> Unit,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        isActive: () -> Boolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw IOException("This stream can't be downloaded.")
        }

        destination.parentFile?.mkdirs()
        val probeCall = client.newCall(
            request(url, headers).header("Range", "bytes=0-0").get().build(),
        )
        onCall(probeCall)

        var rangedResult: DownloadResult? = null
        var mimeType: String? = null
        probeCall.execute().use { probe ->
            mimeType = probe.header("Content-Type")?.substringBefore(';')
            when {
                probe.code == 206 -> {
                    // 206 means ranges work. Most CDNs include the full size in
                    // Content-Range: bytes 0-0/<total>.
                    val total = probe.header("Content-Range")
                        ?.substringAfterLast('/', "")
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                    if (total != null && total >= MIN_RANGE_BYTES) {
                        val result = runCatching {
                            val size = downloadRanges(
                                url, headers, destination, total, onCall, onProgress, isActive,
                            )
                            DownloadResult(size, mimeType, downloadExtension(mimeType, null, url))
                        }.getOrNull()
                        if (result != null) {
                            rangedResult = result
                        } else {
                            runCatching { destination.delete() }
                        }
                    }
                }

                probe.isSuccessful -> {
                    // The server ignored Range and sent everything. Use it.
                    val size = streamBody(probe, destination, onProgress, isActive, null)
                    if (!isActive()) throw CancellationException("Download cancelled")
                    if (size <= 0L) throw IOException("The server returned an empty file.")
                    return@withContext DownloadResult(
                        size,
                        mimeType,
                        downloadExtension(mimeType, null, url),
                    )
                }

                else -> throw IOException("Download failed with HTTP ${probe.code}.")
            }
        }

        rangedResult?.let { return@withContext it }

        val single = downloadSingle(url, headers, destination, onCall, onProgress, isActive)
        if (!isActive()) throw CancellationException("Download cancelled")
        if (single <= 0L) throw IOException("The server returned an empty file.")
        DownloadResult(single, mimeType, downloadExtension(mimeType, null, url))
    }

    private fun downloadSingle(
        url: String,
        headers: Map<String, String>,
        destination: File,
        onCall: (Call) -> Unit,
        onProgress: (Long, Long?) -> Unit,
        isActive: () -> Boolean,
    ): Long {
        destination.parentFile?.mkdirs()
        val call = client.newCall(request(url, headers).get().build())
        onCall(call)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}.")
            }
            return streamBody(response, destination, onProgress, isActive, null)
        }
    }

    private fun streamBody(
        response: Response,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
        isActive: () -> Boolean,
        expectedTotal: Long?,
    ): Long {
        val total = response.body?.contentLength()?.takeIf { it > 0 } ?: expectedTotal
        var copied = 0L
        var sinceUpdate = 0L
        response.body?.byteStream()?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (!isActive()) throw CancellationException("Download cancelled")
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    sinceUpdate += read
                    if (sinceUpdate >= PROGRESS_STEP) {
                        onProgress(copied, total)
                        sinceUpdate = 0L
                    }
                }
            }
        } ?: throw IOException("The server returned no audio data.")
        onProgress(copied, total)
        return copied
    }

    private suspend fun downloadRanges(
        url: String,
        headers: Map<String, String>,
        destination: File,
        total: Long,
        onCall: (Call) -> Unit,
        onProgress: (Long, Long?) -> Unit,
        isActive: () -> Boolean,
    ): Long = coroutineScope {
        destination.parentFile?.mkdirs()
        val chunkSize = (total + RANGE_CHUNKS - 1) / RANGE_CHUNKS
        val copied = AtomicLong(0L)
        val rangeCalls = CopyOnWriteArrayList<Call>()
        val jobs = mutableListOf<Deferred<Unit>>()
        val file = RandomAccessFile(destination, "rw")
        try {
            file.setLength(total)
            (0 until RANGE_CHUNKS).forEach { index ->
                jobs += async(Dispatchers.IO) {
                    val start = index * chunkSize
                    val end = minOf(start + chunkSize - 1, total - 1)
                    if (start > end) return@async
                    val call = client.newCall(
                        request(url, headers)
                            .header("Range", "bytes=$start-$end")
                            .get()
                            .build(),
                    )
                    rangeCalls.add(call)
                    onCall(call)
                    call.execute().use { response ->
                        if (response.code != 206) {
                            throw IOException("Range requests aren't supported.")
                        }
                        val contentRange = response.header("Content-Range")
                        if (contentRange != null && !contentRange.startsWith("bytes $start-")) {
                            throw IOException("The server returned an unexpected range.")
                        }
                        val stream = response.body?.byteStream()
                            ?: throw IOException("The server returned no audio data.")
                        stream.use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var local = 0L
                            var sinceUpdate = 0L
                            while (true) {
                                if (!isActive()) throw CancellationException("Download cancelled")
                                val read = input.read(buffer)
                                if (read < 0) break
                                synchronized(file) {
                                    file.seek(start + local)
                                    file.write(buffer, 0, read)
                                }
                                local += read
                                copied.addAndGet(read.toLong())
                                sinceUpdate += read
                                if (sinceUpdate >= PROGRESS_STEP) {
                                    onProgress(copied.get(), total)
                                    sinceUpdate = 0L
                                }
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.await() }
            onProgress(copied.get(), total)
            copied.get()
        } catch (e: Exception) {
            // Cancel every chunk before deleting the temp file; otherwise a
            // sibling coroutine can keep writing into a file removed by the
            // single-stream fallback.
            rangeCalls.forEach { runCatching { it.cancel() } }
            jobs.forEach { it.cancel() }
            jobs.forEach { runCatching { it.join() } }
            runCatching { destination.delete() }
            throw e
        } finally {
            runCatching { file.close() }
        }
    }

    private fun request(url: String, headers: Map<String, String>): Request.Builder {
        val builder = Request.Builder().url(url)
        val builderHeaders = Headers.Builder()
        headers.forEach { (name, value) -> builderHeaders.add(name, value) }
        return builder.headers(builderHeaders.build())
    }
}
