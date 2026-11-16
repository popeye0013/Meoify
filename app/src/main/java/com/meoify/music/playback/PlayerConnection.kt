package com.meoify.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.meoify.music.data.CoverArtResolver
import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.Artwork
import com.meoify.music.data.remote.ResolvedAudio
import com.meoify.music.data.remote.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val sourceTitle: String = "YouTube Music",
    val volume: Float = 1f,
    val muted: Boolean = false,
) {
    val hasNext: Boolean get() = queueIndex in 0 until queue.lastIndex
    val hasPrevious: Boolean get() = queueIndex > 0
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

sealed interface PlaybackEvent {
    data class Message(val text: String) : PlaybackEvent
    data class Failure(val text: String) : PlaybackEvent
}

class PlayerConnection(
    private val context: Context,
    private val resolver: StreamResolver,
    private val library: LibraryStore,
    private val coverArt: CoverArtResolver? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 12)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
    ).buildAsync()

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = -1
    private var sourceTitle: String = "YouTube Music"
    private var appendJob: Job? = null
    private var generation = 0
    private var lastVolume: Float = 1f

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (id != null) {
                val forward = (queueIndex until queue.size).firstOrNull { queue[it].id == id }
                val found = forward ?: queue.indexOfFirst { it.id == id }
                if (found >= 0) queueIndex = found
                queue.getOrNull(queueIndex)?.let { track ->
                    _state.update { it.copy(track = track) }
                    library.addRecent(track)
                }
            }
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _state.update { it.copy(isPreparing = false) }
            }
            refresh()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(isPreparing = false, isBuffering = false) }
            _events.tryEmit(
                PlaybackEvent.Failure(
                    error.message?.takeIf { it.isNotBlank() }?.let { "Playback failed: $it" }
                        ?: "Playback failed. Try another track.",
                ),
            )
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(controllerListener)
                    if (mediaController.mediaItemCount > 0) {
                        adoptControllerQueue(mediaController)
                    }
                    refresh()
                }.onFailure {
                    _events.tryEmit(PlaybackEvent.Failure("Couldn't start the audio service."))
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && (c.isPlaying || c.playbackState == Player.STATE_BUFFERING)) {
                    _state.update {
                        it.copy(
                            isPlaying = c.isPlaying,
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = safeDuration(c),
                            isBuffering = c.playbackState == Player.STATE_BUFFERING,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int, sourceTitle: String) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        generation++
        appendJob?.cancel()

        queue = tracks.toList()
        queueIndex = start
        this.sourceTitle = sourceTitle
        _state.value = PlayerUiState(
            track = tracks[start],
            isPlaying = false,
            isPreparing = true,
            queue = queue,
            queueIndex = start,
            sourceTitle = sourceTitle,
        )

        val myGeneration = generation
        appendJob = scope.launch {
            val c = try {
                awaitController()
            } catch (e: Exception) {
                _state.update { it.copy(isPreparing = false) }
                _events.tryEmit(PlaybackEvent.Failure("Couldn't connect to playback."))
                return@launch
            }

            var attempt = start
            var audio: ResolvedAudio? = null
            var lastError: Exception? = null
            while (attempt <= minOf(tracks.lastIndex, start + 2) && audio == null) {
                if (generation != myGeneration) return@launch
                val track = queue.getOrNull(attempt) ?: break
                try {
                    audio = resolver.resolve(track.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    attempt++
                }
            }

            if (audio == null) {
                _state.update { it.copy(isPreparing = false) }
                _events.tryEmit(
                    PlaybackEvent.Failure(lastError?.message ?: "This track isn't available."),
                )
                return@launch
            }

            if (attempt != start) {
                queueIndex = attempt
                _state.update { it.copy(queueIndex = attempt, track = queue.getOrNull(attempt)) }
                _events.tryEmit(PlaybackEvent.Message("Skipped an unavailable track."))
            }

            if (generation != myGeneration) return@launch
            val playTrack = queue.getOrNull(attempt) ?: return@launch
            c.setMediaItems(listOf(playTrack.toMediaItem(audio)))
            c.prepare()
            c.play()
            _state.update {
                it.copy(
                    track = playTrack,
                    queueIndex = attempt,
                    isPreparing = false,
                )
            }
            library.addRecent(playTrack)
            refresh()

            // Resolve the rest of the queue in order. The first items are ready
            // long before the current track ends, so transitions stay seamless.
            for (index in attempt + 1..queue.lastIndex) {
                if (generation != myGeneration) break
                val track = queue.getOrNull(index) ?: break
                try {
                    val nextAudio = resolver.resolve(track.id)
                    if (generation == myGeneration) {
                        c.addMediaItem(track.toMediaItem(nextAudio))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _events.tryEmit(PlaybackEvent.Message("Couldn't queue “${track.title}”."))
                }
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        if (c.isPlaying) c.pause() else c.play()
        refresh()
    }

    fun setVolume(value: Float) {
        val c = controller ?: return
        val level = value.coerceIn(0f, 1f)
        c.volume = level
        if (level > 0f) lastVolume = level
        _state.update { it.copy(volume = level, muted = level == 0f) }
    }

    fun toggleMute() {
        val c = controller ?: return
        val next = if (c.volume > 0f) 0f else lastVolume.takeIf { it > 0f } ?: 1f
        c.volume = next
        if (next > 0f) lastVolume = next
        _state.update { it.copy(volume = next, muted = next == 0f) }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    fun skipNext() {
        val c = controller
        if (c != null && c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else if (queueIndex < queue.lastIndex) {
            playTracks(queue, queueIndex + 1, sourceTitle)
        }
    }

    fun skipPrevious() {
        val c = controller
        if (c != null && c.currentPosition > 3_000) {
            c.seekTo(0)
            return
        }
        if (c != null && c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else if (queueIndex > 0) {
            playTracks(queue, queueIndex - 1, sourceTitle)
        } else {
            c?.seekTo(0)
        }
    }

    fun playAtIndex(index: Int) {
        if (index !in queue.indices) return
        if (index == queueIndex) {
            togglePlayPause()
        } else {
            playTracks(queue, index, sourceTitle)
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        _state.update { it.copy(shuffle = c.shuffleModeEnabled) }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _state.update { it.copy(repeatMode = next) }
    }

    fun playNext(track: Track) {
        addTrack(track, playNext = true)
    }

    fun addToQueue(track: Track) {
        addTrack(track, playNext = false)
    }

    private fun addTrack(track: Track, playNext: Boolean) {
        val c = controller
        if (c == null || c.mediaItemCount == 0 || queue.isEmpty()) {
            playTracks(listOf(track), 0, "Queue")
            return
        }
        scope.launch {
            try {
                val audio = resolver.resolve(track.id)
                val item = track.toMediaItem(audio)
                if (playNext) {
                    c.addMediaItem(c.currentMediaItemIndex + 1, item)
                    val mutable = queue.toMutableList()
                    val insertAt = (queueIndex + 1).coerceIn(0, mutable.size)
                    mutable.add(insertAt, track)
                    queue = mutable
                } else {
                    c.addMediaItem(item)
                    queue = queue + track
                }
                _state.update { it.copy(queue = queue) }
                _events.tryEmit(PlaybackEvent.Message(if (playNext) "Playing next" else "Added to queue"))
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Failure("Couldn't add “${track.title}” to the queue."))
            }
        }
    }

    fun toggleLike(track: Track) {
        library.toggleLike(track)
    }

    private suspend fun awaitController(): MediaController {
        val existing = controller
        if (existing != null) return existing
        return suspendCancellableCoroutine { continuation ->
            controllerFuture.addListener(
                {
                    try {
                        continuation.resume(controllerFuture.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    private fun adoptControllerQueue(c: MediaController) {
        if (queue.isNotEmpty()) return
        val adopted = (0 until c.mediaItemCount).mapNotNull { index ->
            val item = c.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            val id = item.mediaId.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Track(
                id = id,
                title = metadata.title?.toString().orEmpty().ifBlank { "Unknown track" },
                artist = metadata.artist?.toString().orEmpty(),
                album = metadata.albumTitle?.toString(),
                durationMs = metadata.durationMs ?: 0L,
                thumbnailUrl = metadata.artworkUri?.toString(),
            )
        }
        if (adopted.isEmpty()) return
        queue = adopted
        queueIndex = c.currentMediaItemIndex.coerceIn(0, adopted.lastIndex)
        sourceTitle = "YouTube Music"
        _state.update {
            it.copy(queue = queue, queueIndex = queueIndex, sourceTitle = sourceTitle, track = queue.getOrNull(queueIndex))
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val track = item?.mediaId?.let { id ->
            queue.getOrNull(queueIndex)?.takeIf { it.id == id }
                ?: queue.firstOrNull { it.id == id }
                ?: Track(
                    id = id,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    album = item.mediaMetadata.albumTitle?.toString(),
                    durationMs = item.mediaMetadata.durationMs ?: 0L,
                    thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                )
        } ?: _state.value.track

        _state.update {
            it.copy(
                track = track,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = safeDuration(c),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue.ifEmpty { it.queue },
                queueIndex = queueIndex,
                sourceTitle = sourceTitle,
                volume = c.volume,
                muted = c.volume == 0f,
            )
        }
    }

    private fun safeDuration(c: MediaController): Long {
        val duration = c.duration
        return if (duration > 0 && duration != C.TIME_UNSET) {
            duration
        } else {
            _state.value.track?.durationMs ?: 0L
        }
    }

    private suspend fun Track.toMediaItem(audio: ResolvedAudio): MediaItem {
        val artwork = coverArt?.cached(id) ?: Artwork.songArt(thumbnailUrl)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.takeIf { it.isNotBlank() }?.let { setAlbumTitle(it) }
                if (durationMs > 0) setDurationMs(durationMs)
                artwork?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(audio.url)
            .setMediaMetadata(metadata)
            .build()
    }
}
