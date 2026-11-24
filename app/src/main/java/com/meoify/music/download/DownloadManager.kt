package com.meoify.music.download

import android.content.Context
import android.net.Uri
import com.meoify.music.data.local.DownloadStore
import com.meoify.music.data.model.DownloadFailure
import com.meoify.music.data.model.DownloadQueueItem
import com.meoify.music.data.model.DownloadState
import com.meoify.music.data.model.DownloadedTrack
import com.meoify.music.data.model.TrackedPlaylist
import com.meoify.music.data.model.Track
import com.meoify.music.data.model.UnsupportedDownloadFormatException
import com.meoify.music.data.model.safeDownloadId
import com.meoify.music.data.remote.InnerTube
import com.meoify.music.data.remote.ResolvedAudio
import com.meoify.music.data.remote.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the download queue and the offline files.
 *
 * Transfers run through a single serialized worker so storage writes never
 * overlap. The queue survives restarts, supports pause/resume/cancel/retry, and
 * publishes files atomically from a temp path. All state lives in Meoify's
 * DataStore and filesDir.
 */
class DownloadManager(
    context: Context,
    private val store: DownloadStore,
    private val resolver: StreamResolver,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val downloadsDir = File(appContext.filesDir, "downloads")
    private val tempDir = File(appContext.cacheDir, "download-export")
    private val artwork = DownloadedArtwork(appContext)

    // One song can have a probe + 8 chunk calls, so keep all of them per id.
    private val activeCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()
    private var currentJob: Job? = null
    private var queueJob: Job? = null
    private val drainCallbacks = mutableListOf<() -> Unit>()
    @Volatile private var queueNeedsRestart = false
    private var protectedTrackId: String? = null

    /** Bulk batches get one audible completion notice when every song lands. */
    private data class BatchInfo(val name: String, val trackIds: List<String>)
    private val batches = ConcurrentHashMap<String, BatchInfo>()

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 12,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<String> = _events

    val queue = store.queue
    val downloads = store.downloads
    val preferences = store.preferences
    val trackedPlaylists = store.trackedPlaylists

    init {
        runCatching {
            downloadsDir.mkdirs()
            tempDir.mkdirs()
        }
        scope.launch {
            // Keep batch summaries available if the process restarts mid-playlist.
            store.currentQueue()
                .filterNot { it.batchId.isNullOrBlank() }
                .groupBy { it.batchId.orEmpty() }
                .forEach { (batchId, items) ->
                    val first = items.first()
                    batches.putIfAbsent(
                        batchId,
                        BatchInfo(
                            name = first.batchName ?: "Playlist",
                            trackIds = first.batchTrackIds.ifEmpty { items.map { it.track.id } },
                        ),
                    )
                }
            // A download interrupted by a process death must not stay RUNNING.
            store.updateQueue { items ->
                items.map { item ->
                    if (item.state == DownloadState.RUNNING) {
                        item.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())
                    } else {
                        item
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Playback integration
    // ---------------------------------------------------------------------

    /** Returns a locally saved copy when one exists; callers fall back to a stream. */
    fun localAudio(track: Track): ResolvedAudio? {
        val entry = store.downloads.value.firstOrNull { it.track.id == track.id } ?: return null
        val file = File(entry.filePath)
        if (!file.exists() || file.length() <= 0L) return null
        scope.launch { runCatching { store.touch(track.id, System.currentTimeMillis()) } }
        return ResolvedAudio(
            url = Uri.fromFile(file).toString(),
            bitrate = 0,
            format = "Offline",
        )
    }

    fun isDownloaded(id: String): Flow<Boolean> = store.isDownloaded(id)

    fun coverUri(track: Track): String? = artwork.fileFor(track)?.let { Uri.fromFile(it).toString() }

    /** Prevents the storage cap from evicting the song currently playing. */
    fun setPlayingTrack(id: String?) {
        protectedTrackId = id
    }

    // ---------------------------------------------------------------------
    // Queue actions
    // ---------------------------------------------------------------------

    fun download(track: Track, playlistId: String? = null) {
        worker.launch {
            val queued = store.currentQueue()
            if (queued.any {
                it.track.id == track.id &&
                    (it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING)
            }
            ) {
                _events.tryEmit("${track.title} is already in the download queue")
                return@launch
            }
            if (store.currentDownloads().any { it.track.id == track.id }) {
                _events.tryEmit("${track.title} is already offline")
                return@launch
            }
            store.updateQueue { items ->
                items + DownloadQueueItem(
                    track = track,
                    enqueuedAt = System.currentTimeMillis(),
                    sourcePlaylistId = playlistId,
                )
            }
            DownloadNotifications.showQueued(appContext, track.title)
            startQueue()
        }
    }

    fun downloadAll(tracks: List<Track>, source: String, playlistId: String? = null) {
        if (tracks.isEmpty()) return
        worker.launch {
            val already = store.currentDownloads().map { it.track.id }.toSet()
            val queued = store.currentQueue()
                .filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING }
                .map { it.track.id }
                .toSet()
            val newTracks = tracks
                .filterNot { it.id in already || it.id in queued }
                .distinctBy { it.id }
            if (newTracks.isEmpty()) {
                _events.tryEmit("Everything from $source is already offline or queued")
                return@launch
            }
            val batchId = "batch-${UUID.randomUUID()}"
            val ids = newTracks.map { it.id }
            batches[batchId] = BatchInfo(source, ids)
            val additions = newTracks.map {
                DownloadQueueItem(
                    track = it,
                    sourcePlaylistId = playlistId,
                    batchId = batchId,
                    batchName = source,
                    batchTrackIds = ids,
                )
            }
            store.updateQueue { it + additions }
            DownloadNotifications.showQueued(
                appContext,
                additions.first().track.title,
                count = additions.size,
            )
            startQueue()
        }
    }

    fun toggle(track: Track) {
        worker.launch {
            if (store.currentDownloads().any { it.track.id == track.id }) {
                removeInternal(track)
                _events.tryEmit("Removed ${track.title} from downloads")
                return@launch
            }
            val item = store.currentQueue().firstOrNull { it.track.id == track.id }
            if (item != null && item.state != DownloadState.FAILED &&
                item.state != DownloadState.CANCELLED
            ) {
                cancel(track.id)
                _events.tryEmit("Cancelled ${track.title}")
                return@launch
            }
            download(track)
        }
    }

    fun remove(track: Track) {
        worker.launch { removeInternal(track) }
    }

    fun removeById(id: String) {
        worker.launch {
            store.currentDownloads().firstOrNull { it.track.id == id }?.let { removeInternal(it.track) }
        }
    }

    fun clearAll() {
        worker.launch {
            val entries = store.currentDownloads()
            entries.forEach { runCatching { File(it.filePath).delete() } }
            store.updateQueue { emptyList() }
            cancelAllCalls()
            activeCalls.clear()
            batches.clear()
            artwork.clear()
            entries.forEach { store.removeDownload(it.track.id) }
            DownloadNotifications.cancel(appContext)
            _events.tryEmit("Removed ${entries.size} downloaded song${if (entries.size == 1) "" else "s"}")
        }
    }

    fun dismiss(id: String) {
        worker.launch { store.updateQueue { items -> items.filterNot { it.track.id == id } } }
    }

    fun clearFinished() {
        worker.launch {
            store.updateQueue { items -> items.filterNot { it.state == DownloadState.COMPLETED } }
        }
    }

    fun retry(id: String) {
        worker.launch {
            store.updateQueue { items ->
                items.map {
                    if (it.track.id == id) {
                        it.copy(
                            state = DownloadState.QUEUED,
                            downloadedBytes = 0L,
                            totalBytes = null,
                            attemptCount = 0,
                            lastError = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        it
                    }
                }
            }
            startQueue()
        }
    }

    fun pauseAll() {
        worker.launch {
            val currentTitle = store.currentQueue()
                .firstOrNull { it.state == DownloadState.RUNNING }
                ?.track?.title
                ?: "Downloads"
            // Mark first so the worker's cancellation handler knows this was a
            // deliberate pause and does not requeue the item.
            store.updateQueue { items ->
                items.map {
                    if (it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING) {
                        it.copy(state = DownloadState.PAUSED, updatedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }
            cancelAllCalls()
            currentJob?.cancel()
            DownloadNotifications.showPaused(appContext, currentTitle)
        }
    }

    fun resumeAll() {
        worker.launch {
            store.updateQueue { items ->
                items.map {
                    if (it.state == DownloadState.PAUSED) {
                        it.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())
                    } else {
                        it
                    }
                }
            }
            DownloadNotifications.cancel(appContext)
            startQueue()
        }
    }

    fun cancelAll() {
        worker.launch {
            store.updateQueue { items ->
                items.filterNot {
                    it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING ||
                        it.state == DownloadState.PAUSED
                }
            }
            cancelAllCalls()
            currentJob?.cancel()
            DownloadNotifications.cancel(appContext)
        }
    }

    fun cancel(id: String) {
        worker.launch { cancelInternal(id) }
    }

    private suspend fun cancelInternal(id: String) {
        store.updateQueue { items ->
            items.map {
                if (it.track.id == id && it.state != DownloadState.COMPLETED) {
                    it.copy(state = DownloadState.CANCELLED, updatedAt = System.currentTimeMillis())
                } else {
                    it
                }
            }
        }
        cancelCalls(id)
    }

    /**
     * Runs queued items in order. Callers can register a drain callback, which
     * fires exactly once when no runnable items remain. Callbacks are kept even
     * when the worker is already busy — otherwise a service that starts while a
     * download is in flight would lose its stop signal and linger forever.
     */
    fun startQueue(onDrained: (() -> Unit)? = null) {
        if (onDrained != null) drainCallbacks += onDrained
        if (queueJob?.isActive == true) {
            // Items can be added while the worker is between passes (or just as
            // it decides the queue is empty). Flag another pass so nothing gets
            // stranded until some later action happens to kick the queue again.
            queueNeedsRestart = true
            return
        }
        queueJob = worker.launch {
            do {
                queueNeedsRestart = false
                while (isActive) {
                    val next = store.currentQueue().firstOrNull { it.state.isRunnable }
                        ?: break
                    runCatching { runItem(next) }
                }
            } while (queueNeedsRestart && isActive)
            currentJob = null
            queueJob = null
            // A download may finish without the service ever running (e.g. the
            // service stopped on an empty queue while items were being added).
            // Clear the progress notification on drain, but keep the Paused
            // notice so the Resume action stays available.
            if (store.currentQueue().none { it.state == DownloadState.PAUSED }) {
                DownloadNotifications.cancelProgress(appContext)
            }
            val callbacks = drainCallbacks.toList()
            drainCallbacks.clear()
            callbacks.forEach { runCatching(it) }
        }
    }

    fun isRunning(): Boolean = queueJob?.isActive == true

    /**
     * Bulk downloads use one audible notification, and only when every song in
     * the batch actually made it to disk. A paused batch stays pending so it can
     * still chime after the user resumes.
     */
    private suspend fun maybeFinishBatch(batchId: String?) {
        if (batchId.isNullOrBlank()) return
        val current = store.currentQueue()
        val stillPending = current.any { item ->
            item.batchId == batchId &&
                (item.state.isRunnable || item.state == DownloadState.PAUSED)
        }
        if (stillPending) return

        val info = batches.remove(batchId)
            ?: current.firstOrNull { it.batchId == batchId }?.let { item ->
                BatchInfo(
                    name = item.batchName ?: "Playlist",
                    trackIds = item.batchTrackIds.ifEmpty {
                        current.filter { it.batchId == batchId }.map { it.track.id }
                    },
                )
            }
            ?: return

        val saved = store.currentDownloads().map { it.track.id }.toSet()
        if (info.trackIds.isNotEmpty() && info.trackIds.all { it in saved }) {
            DownloadNotifications.showCompleted(
                appContext,
                "${info.name} is offline",
                count = info.trackIds.size,
                sound = true,
            )
        }
    }

    private fun registerCall(id: String, call: Call) {
        activeCalls.computeIfAbsent(id) { CopyOnWriteArrayList() }.add(call)
    }

    private fun cancelCalls(id: String) {
        activeCalls[id]?.forEach { runCatching { it.cancel() } }
    }

    private fun cancelAllCalls() {
        activeCalls.values.forEach { calls -> calls.forEach { runCatching { it.cancel() } } }
    }

    // ---------------------------------------------------------------------
    // Playlists, likes and retention
    // ---------------------------------------------------------------------

    fun trackPlaylist(id: String, name: String, tracks: List<Track>) {
        worker.launch {
            val previous = store.trackedPlaylists.value.firstOrNull { it.id == id }
            val ids = tracks.map { it.id }.toSet()
            if (previous != null && previous.trackIds.toSet() == ids) return@launch

            // Songs removed from a tracked playlist stop being offline copies.
            if (previous != null) {
                val removed = previous.trackIds.filterNot { it in ids }
                store.currentDownloads()
                    .filter { it.sourcePlaylistId == id && it.track.id in removed }
                    .forEach { removeInternal(it.track) }
            }

            val stored = store.trackedPlaylists.value.filterNot { it.id == id } +
                TrackedPlaylist(id, name, tracks.map { it.id })
            store.setTrackedPlaylists(stored)

            val already = store.currentDownloads().map { it.track.id }.toSet()
            val queued = store.currentQueue()
                .filter { it.state.isRunnable }
                .map { it.track.id }
                .toSet()
            val newTracks = tracks.filterNot { it.id in already || it.id in queued }
                .distinctBy { it.id }
            if (newTracks.isNotEmpty()) {
                val batchId = "batch-${UUID.randomUUID()}"
                val ids = newTracks.map { it.id }
                batches[batchId] = BatchInfo(name, ids)
                val additions = newTracks.map {
                    DownloadQueueItem(
                        track = it,
                        sourcePlaylistId = id,
                        batchId = batchId,
                        batchName = name,
                        batchTrackIds = ids,
                    )
                }
                store.updateQueue { it + additions }
                // Give immediate feedback and bring up the foreground service
                // only after the items exist. Starting it earlier drains an
                // empty queue and stops right away, delaying the first transfer.
                DownloadNotifications.showQueued(
                    appContext,
                    additions.first().track.title,
                    count = additions.size,
                )
                DownloadService.start(appContext)
                startQueue()
            }
        }
    }

    fun untrackPlaylist(id: String) {
        worker.launch {
            store.setTrackedPlaylists(store.trackedPlaylists.value.filterNot { it.id == id })
        }
    }

    fun onLikeChanged(track: Track, liked: Boolean) {
        if (!liked) return
        worker.launch {
            if (store.currentPreferences().autoDownloadOnLike) download(track)
        }
    }

    fun setMaxOfflineMiB(mib: Int) = store.setMaxOfflineMiB(mib)

    fun setAutoDownloadOnLike(enabled: Boolean) = store.setAutoDownloadOnLike(enabled)

    private suspend fun enforceStorageLimit() {
        val limitMiB = store.currentPreferences().maxOfflineMiB
        if (limitMiB <= 0) return
        val limit = limitMiB * 1024L * 1024L
        var total = store.currentDownloads().sumOf { it.fileSize }
        if (total <= limit) return
        val victims = store.currentDownloads()
            .filterNot { it.track.id == protectedTrackId }
            .sortedBy { it.lastAccessedAt }
        for (victim in victims) {
            if (total <= limit) break
            runCatching { File(victim.filePath).delete() }
            store.removeDownload(victim.track.id)
            total -= victim.fileSize
        }
    }

    // ---------------------------------------------------------------------
    // Worker internals
    // ---------------------------------------------------------------------

    private suspend fun runItem(item: DownloadQueueItem) {
        val track = item.track
        val id = track.id
        val now = System.currentTimeMillis()
        val itemContext = currentCoroutineContext()
        currentJob = itemContext[Job]
        store.updateQueue { items ->
            items.map {
                if (it.track.id == id) {
                    it.copy(
                        state = DownloadState.RUNNING,
                        attemptCount = it.attemptCount + 1,
                        updatedAt = now,
                        lastError = null,
                    )
                } else {
                    it
                }
            }
        }
        DownloadNotifications.showProgress(appContext, track.title, 0L, null, paused = false, queueSize = 1)

        try {
            // Cover art uses a different host; fetching it in parallel keeps it
            // off the audio critical path.
            val artworkJob = scope.launch { runCatching { artwork.ensure(track) } }

            val audio = resolveForDownload(track, refresh = item.attemptCount > 0)
            val url = audio.url
            if (audio.mimeType?.contains("dash", ignoreCase = true) == true ||
                audio.mimeType?.contains("manifest", ignoreCase = true) == true ||
                url.contains(".mpd", ignoreCase = true)
            ) {
                throw UnsupportedDownloadFormatException(
                    "This quality is a streaming manifest. Choose a direct format in Settings and retry.",
                )
            }

            if (downloadsDir.usableSpace < MIN_FREE_BYTES) {
                throw IOException("Not enough free storage to save this song.")
            }

            val temp = File(tempDir, "${safeDownloadId(id)}_${now}.tmp")
            var lastProgressAt = 0L
            val result = AudioFileDownloader.download(
                url = url,
                headers = downloadHeaders(url),
                destination = temp,
                onCall = { registerCall(id, it) },
                onProgress = { bytes, total ->
                    val at = System.currentTimeMillis()
                    if (at - lastProgressAt >= 800L) {
                        lastProgressAt = at
                        scope.launch {
                            store.updateQueue { items ->
                                items.map {
                                    if (it.track.id == id) {
                                        it.copy(downloadedBytes = bytes, totalBytes = total)
                                    } else {
                                        it
                                    }
                                }
                            }
                        }
                        DownloadNotifications.showProgress(
                            appContext,
                            track.title,
                            bytes,
                            total,
                            paused = false,
                            queueSize = 1,
                        )
                    }
                },
                isActive = { itemContext.isActive },
            )

            if (result.size > MAX_AUDIO_BYTES) {
                runCatching { temp.delete() }
                throw IOException("This file is too large to download.")
            }

            val target = File(downloadsDir, "${safeDownloadId(id)}${result.extension}")
            if (target.exists() && !target.delete()) {
                throw IOException("Couldn't replace an older offline copy.")
            }
            if (!temp.renameTo(target)) {
                target.writeBytes(temp.readBytes())
                runCatching { temp.delete() }
            }
            if (!target.exists() || target.length() <= 0L) {
                throw IOException("The downloaded file could not be saved.")
            }

            val entry = DownloadedTrack(
                track = track,
                filePath = target.absolutePath,
                mimeType = result.mimeType,
                fileSize = target.length(),
                downloadedAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                sourcePlaylistId = item.sourcePlaylistId,
            )
            store.upsert(entry)
            store.updateQueue { items -> items.filterNot { it.track.id == id } }
            artworkJob.join()
            artwork.prune(store.currentDownloads().map { it.track })
            enforceStorageLimit()
            _events.tryEmit("Downloaded ${track.title}")
            DownloadNotifications.showCompleted(appContext, track.title)
        } catch (e: CancellationException) {
            val state = store.currentQueue().firstOrNull { it.track.id == id }?.state
            if (state == DownloadState.RUNNING || state == DownloadState.QUEUED) {
                store.updateQueue { items ->
                    items.map {
                        if (it.track.id == id) {
                            it.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())
                        } else {
                            it
                        }
                    }
                }
            }
            throw e
        } catch (e: Exception) {
            // OkHttp reports call.cancel() as an IOException, so a deliberate
            // pause/cancel must be distinguished from a real transfer failure.
            val currentState = store.currentQueue().firstOrNull { it.track.id == id }?.state
            val stopped = currentState == DownloadState.PAUSED ||
                currentState == DownloadState.CANCELLED ||
                !itemContext.isActive
            if (stopped) {
                if (currentState == DownloadState.RUNNING || currentState == DownloadState.QUEUED) {
                    store.updateQueue { items ->
                        items.map {
                            if (it.track.id == id) {
                                it.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())
                            } else {
                                it
                            }
                        }
                    }
                }
            } else {
                val failure = DownloadFailure.from(e)
                val message = e.message?.takeIf { it.isNotBlank() }?.let { "$it" }
                    ?: "The download couldn't be completed."
                store.updateQueue { items ->
                    items.map {
                        if (it.track.id == id) {
                            it.copy(
                                state = DownloadState.FAILED,
                                lastError = "${failure.name}: $message",
                                downloadedBytes = 0L,
                                totalBytes = null,
                                updatedAt = System.currentTimeMillis(),
                            )
                        } else {
                            it
                        }
                    }
                }
                _events.tryEmit("Couldn't download ${track.title}: $message")
                DownloadNotifications.showFailure(appContext, track.title, failure)
            }
        } finally {
            maybeFinishBatch(item.batchId)
            // Includes the probe and all range chunks; needed for cancellation
            // paths where OkHttp is still transferring data.
            cancelCalls(id)
            activeCalls.remove(id)
            currentJob = null
        }
    }

    private suspend fun resolveForDownload(track: Track, refresh: Boolean): ResolvedAudio {
        return try {
            resolver.resolve(track, force = refresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stream URLs expire quickly; a refreshed resolution is worth one
            // extra attempt before failing the item.
            resolver.resolve(track, force = true)
        }
    }

    private fun downloadHeaders(url: String): Map<String, String> {
        val headers = mutableMapOf("User-Agent" to InnerTube.USER_AGENT)
        if (url.contains("googlevideo.com", ignoreCase = true) ||
            url.contains("youtube.com", ignoreCase = true)
        ) {
            headers["Referer"] = "https://music.youtube.com/"
            headers["Origin"] = "https://music.youtube.com"
        }
        return headers
    }

    private suspend fun removeInternal(track: Track) {
        cancelInternal(track.id)
        store.currentDownloads().firstOrNull { it.track.id == track.id }?.let { entry ->
            runCatching { File(entry.filePath).delete() }
            store.removeDownload(track.id)
        }
        store.updateQueue { items -> items.filterNot { it.track.id == track.id } }
        artwork.prune(store.currentDownloads().map { it.track })
    }

    private companion object {
        const val MIN_FREE_BYTES = 16L * 1024 * 1024
        const val MAX_AUDIO_BYTES = 512L * 1024 * 1024
    }
}
