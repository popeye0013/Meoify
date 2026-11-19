package com.meoify.music.playback

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.meoify.music.data.CoverArtResolver
import com.meoify.music.data.local.LibraryStore
import com.meoify.music.data.local.PlaybackSnapshot
import com.meoify.music.data.local.PlaybackStore
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
import kotlinx.coroutines.flow.first
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
    /** Audio output chosen in the volume sheet, used for the player icon. */
    val outputType: Int = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    val outputDeviceId: Int? = null,
    /** Reported by the Bluetooth stack when the connected audio device supports it. */
    val bluetoothBattery: Int? = null,
    /** Epoch millis when the sleep timer pauses playback, or null when off. */
    val sleepEndsAtMs: Long? = null,
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

private const val ACTION_BLUETOOTH_BATTERY =
    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
private const val EXTRA_BLUETOOTH_BATTERY =
    "android.bluetooth.device.extra.BATTERY_LEVEL"

internal fun Int.isBluetoothAudio(): Boolean =
    this == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || this == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

/**
 * The public SDK does not expose headset battery. The system's protected
 * broadcast and BluetoothDevice.getBatteryLevel() reflection are the only
 * sources; both are best-effort, and many headsets report no level at all.
 */
@Suppress("PrivateApi")
private fun readBluetoothBattery(context: Context, deviceName: String? = null): Int? =
    runCatching {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return@runCatching null
        fun levelOf(device: android.bluetooth.BluetoothDevice): Int? =
            runCatching { device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int }
                .getOrNull()
                ?.takeIf { it in 0..100 }

        val bonded = adapter.bondedDevices.toList()
        // Prefer the headset that is actually routed; fall back to any bonded
        // device that reports a level so the pill still has something useful.
        val named = deviceName?.takeIf { it.isNotBlank() }?.let { name ->
            bonded.firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
        }
        named?.let(::levelOf) ?: bonded.firstNotNullOfOrNull(::levelOf)
    }.getOrNull()

/** Best-effort initial route so the player icon matches a connected device on startup. */
@Suppress("DEPRECATION")
private fun detectAudioOutput(audio: AudioManager?): Pair<Int, Int?> {
    if (audio == null) return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to null
    val devices = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
        .getOrDefault(emptyArray())
        .toList()
    fun firstOf(vararg types: Int) = devices.firstOrNull { device -> device.type in types }
    return when {
        audio.isBluetoothA2dpOn -> {
            val device = firstOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            )
            (device?.type ?: AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) to device?.id
        }
        audio.isWiredHeadsetOn -> {
            val device = firstOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )
            (device?.type ?: AudioDeviceInfo.TYPE_WIRED_HEADSET) to device?.id
        }
        else -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to null
    }
}

class PlayerConnection(
    private val context: Context,
    private val resolver: StreamResolver,
    private val library: LibraryStore,
    private val coverArt: CoverArtResolver? = null,
    private val playbackStore: PlaybackStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val initialOutput = detectAudioOutput(audioManager)
    private val _state = MutableStateFlow(
        PlayerUiState(
            outputType = initialOutput.first,
            outputDeviceId = initialOutput.second,
            bluetoothBattery = readBluetoothBattery(context),
        ),
    )

    private val bluetoothBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action != ACTION_BLUETOOTH_BATTERY) return
            val level = intent.getIntExtra(EXTRA_BLUETOOTH_BATTERY, -1)
            if (level in 0..100) {
                _state.update { it.copy(bluetoothBattery = level) }
            }
        }
    }
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 12)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private var queueIndex: Int = -1
    private var sourceTitle: String = "YouTube Music"
    private var appendJob: Job? = null
    private var generation = 0
    private var lastVolume: Float = 1f
    private var sleepJob: Job? = null
    /** Last track whose stream URL was refreshed after a player error. */
    private var retriedTrackId: String? = null
    /** Guards against auto-skipping through an entire unavailable queue. */
    private var consecutiveFailures = 0
    /** True while the last saved session is on screen but not loaded in the player. */
    private var restored = false
    private var lastPersistAt = 0L
    private var persistedTrackId: String? = null
    private var persistedPlaying = false
    /**
     * Set when the activity detached from the service. A media controller keeps
     * the service bound, so it must be released before a paused service can be
     * destroyed. The next action (or the activity coming back) reconnects.
     */
    private var serviceReleased = false

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
            persist(force = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
            persist(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                consecutiveFailures = 0
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
            val failedTrack = _state.value.track
            val c = controller
            _state.update { it.copy(isPreparing = false, isBuffering = false) }

            if (failedTrack == null || c == null) {
                _events.tryEmit(
                    PlaybackEvent.Failure(
                        error.message?.takeIf { it.isNotBlank() }
                            ?.let { "Playback failed: $it" }
                            ?: "Playback failed. Try another track.",
                    ),
                )
                refresh()
                return
            }

            // Stream URLs are short lived. Refresh the URL once and resume at
            // the same position; this recovers from HTTP 403 and expired links.
            if (retriedTrackId != failedTrack.id) {
                retriedTrackId = failedTrack.id
                _events.tryEmit(PlaybackEvent.Message("Reconnecting to “${failedTrack.title}”…"))
                scope.launch {
                    val audio = try {
                        resolver.resolve(failedTrack, force = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (audio == null) {
                        failAndAdvance(failedTrack, c)
                        return@launch
                    }
                    runCatching {
                        val index = c.currentMediaItemIndex.coerceAtLeast(0)
                        val position = c.currentPosition.coerceAtLeast(0)
                        c.replaceMediaItem(index, failedTrack.toMediaItem(audio))
                        c.seekTo(index, position)
                        c.prepare()
                        c.play()
                    }.onFailure {
                        failAndAdvance(failedTrack, c)
                    }
                }
            } else {
                failAndAdvance(failedTrack, c)
            }
            refresh()
        }
    }

    /**
     * Reports the failure and moves to the next playable track. Stops after a
     * few failures so a fully unavailable queue does not spin forever.
     */
    private fun failAndAdvance(track: Track, c: MediaController) {
        consecutiveFailures++
        val canAdvance = c.hasNextMediaItem() || queueIndex in 0 until queue.lastIndex
        val skipping = consecutiveFailures <= MAX_CONSECUTIVE_FAILURES && canAdvance
        _events.tryEmit(
            PlaybackEvent.Failure(
                if (skipping) {
                    "Couldn't play “${track.title}”. Skipping…"
                } else {
                    "Couldn't play “${track.title}”."
                },
            ),
        )
        if (!skipping) return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
            c.play()
        } else {
            playTracks(queue, queueIndex + 1, sourceTitle)
        }
    }

    /**
     * The connection to the playback service. It is rebuilt when the service
     * was stopped (for example after the task was removed while paused) so the
     * last song can be resumed without restarting the app.
     */
    private var controllerFuture: ListenableFuture<MediaController> = buildController()

    init {
        runCatching {
            ContextCompat.registerReceiver(
                context,
                bluetoothBatteryReceiver,
                IntentFilter(ACTION_BLUETOOTH_BATTERY),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
        scope.launch { restoreLastPlayback() }

        scope.launch {
            while (isActive) {
                val c = controller
                var playing = false
                if (c != null && c.isConnected) {
                    playing = c.isPlaying
                    if (playing || c.playbackState == Player.STATE_BUFFERING) {
                        _state.update {
                            it.copy(
                                isPlaying = playing,
                                positionMs = c.currentPosition.coerceAtLeast(0),
                                durationMs = safeDuration(c),
                                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                            )
                        }
                    }
                }
                persist()
                // Lyrics highlight the line for the current position; 250 ms
                // keeps it close to the audio without busy-looping.
                delay(if (playing) 250 else 500)
            }
        }
    }

    private fun buildController(): ListenableFuture<MediaController> {
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java)),
        ).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { mediaController ->
                    if (controllerFuture !== future || serviceReleased) {
                        // A newer connection replaced this one, or the app went
                        // to the background while it was still connecting.
                        mediaController.release()
                        return@onSuccess
                    }
                    attachController(mediaController)
                }.onFailure {
                    if (controllerFuture === future) {
                        _events.tryEmit(PlaybackEvent.Failure("Couldn't start the audio service."))
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        return future
    }

    private fun attachController(mediaController: MediaController) {
        controller = mediaController
        serviceReleased = false
        mediaController.addListener(controllerListener)
        if (mediaController.mediaItemCount > 0) {
            // A live session is always fresher than the last saved snapshot.
            restored = false
            queue = emptyList()
            queueIndex = -1
            adoptControllerQueue(mediaController)
        } else if (queue.isNotEmpty()) {
            // The service was stopped with a queue on screen: keep showing the
            // last song and load it again when the person presses play.
            restored = true
        }
        refresh()
    }

    private suspend fun restoreLastPlayback() {
        val snapshot = playbackStore.snapshot.first() ?: return
        if (queue.isNotEmpty()) return
        if ((controller?.mediaItemCount ?: 0) > 0) return
        val index = snapshot.queueIndex
        if (index !in snapshot.queue.indices) return

        val track = snapshot.queue[index]
        queue = snapshot.queue
        queueIndex = index
        sourceTitle = snapshot.sourceTitle.ifBlank { "YouTube Music" }
        restored = true
        _state.value = PlayerUiState(
            track = track,
            isPlaying = false,
            positionMs = snapshot.positionMs.coerceAtLeast(0),
            durationMs = track.durationMs.coerceAtLeast(0),
            shuffle = snapshot.shuffle,
            repeatMode = snapshot.repeatMode,
            queue = queue,
            queueIndex = queueIndex,
            sourceTitle = sourceTitle,
            sleepEndsAtMs = _state.value.sleepEndsAtMs,
        )
    }

    /** Writes the current session to disk, throttled while it is playing. */
    private fun persist(force: Boolean = false) {
        if (queue.isEmpty() || queueIndex !in queue.indices) return
        val state = _state.value
        val track = state.track ?: queue.getOrNull(queueIndex) ?: return
        val trackChanged = track.id != persistedTrackId
        val playChanged = state.isPlaying != persistedPlaying
        val now = SystemClock.elapsedRealtime()
        if (!force && !trackChanged && !playChanged &&
            (!state.isPlaying || now - lastPersistAt < PERSIST_INTERVAL_MS)
        ) {
            return
        }
        lastPersistAt = now
        persistedTrackId = track.id
        persistedPlaying = state.isPlaying
        playbackStore.save(
            PlaybackSnapshot(
                queue = queue,
                queueIndex = queueIndex,
                positionMs = state.positionMs.coerceAtLeast(0),
                sourceTitle = sourceTitle,
                shuffle = state.shuffle,
                repeatMode = state.repeatMode,
                savedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Saves immediately; the playback service calls this before stopping. */
    fun saveSnapshotNow() = persist(force = true)

    /**
     * Called when the activity leaves the screen. Background playback is kept
     * only while audio is actually running. The controller is always released
     * here: it is what keeps the service bound, and a bound service cannot be
     * destroyed. With the controller gone, a paused session can be torn down so
     * Meoify stops appearing in the notification shade's active-app section.
     */
    fun onAppBackgrounded() {
        persist(force = true)
        val c = controller
        val ui = _state.value
        // A song that was just started can still be resolving/preparing, so
        // isPlaying is false for a moment. Stopping the service here would kill
        // that pending playback when the screen turns off. Keep it alive while
        // there is any active playback intent; the service stops itself after a
        // real pause.
        val keepsPlaying = ui.isPlaying || ui.isPreparing ||
            (c != null && c.isConnected && c.mediaItemCount > 0 &&
                (c.isPlaying || c.playWhenReady || c.playbackState == Player.STATE_BUFFERING))
        if (keepsPlaying) {
            // Keep the controller bound too: playTracks may still be resolving
            // the first stream, and releasing it here would make that pending
            // start fail. The service tears itself down after a real pause.
            return
        }
        detachController()
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }

    /** Reconnects the UI to a service that kept playing in the background. */
    fun onAppForegrounded() {
        if (controller?.isConnected == true) return
        scope.launch {
            runCatching { awaitController() }
        }
    }

    /** Unbinds this process from the service without touching playback. */
    private fun detachController() {
        serviceReleased = true
        val current = controller
        controller = null
        runCatching { current?.release() }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val current = controller
        if (!serviceReleased && current != null && current.isConnected) {
            block(current)
            return
        }
        scope.launch {
            val fresh = try {
                awaitController()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Failure("Couldn't connect to playback."))
                return@launch
            }
            block(fresh)
            refresh()
        }
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int,
        sourceTitle: String,
        startPositionMs: Long = 0L,
        preservePlayback: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        val previous = _state.value
        val resumePlaying = preservePlayback &&
            (controller?.takeIf { it.isConnected }?.isPlaying ?: previous.isPlaying)
        generation++
        appendJob?.cancel()
        retriedTrackId = null
        restored = false

        queue = tracks.toList()
        queueIndex = start
        this.sourceTitle = sourceTitle
        _state.value = PlayerUiState(
            track = tracks[start],
            isPlaying = resumePlaying,
            isPreparing = !preservePlayback,
            positionMs = startPositionMs.coerceAtLeast(0),
            durationMs = tracks[start].durationMs.coerceAtLeast(0),
            shuffle = if (preservePlayback) previous.shuffle else false,
            repeatMode = if (preservePlayback) previous.repeatMode else Player.REPEAT_MODE_OFF,
            queue = queue,
            queueIndex = start,
            sourceTitle = sourceTitle,
            volume = if (preservePlayback) previous.volume else 1f,
            muted = if (preservePlayback) previous.muted else false,
            sleepEndsAtMs = previous.sleepEndsAtMs,
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
            while (attempt <= minOf(tracks.lastIndex, start + SKIP_WINDOW) && audio == null) {
                if (generation != myGeneration) return@launch
                val track = queue.getOrNull(attempt) ?: break
                try {
                    audio = resolver.resolve(track)
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
                val skipped = attempt - start
                queueIndex = attempt
                _state.update { it.copy(queueIndex = attempt, track = queue.getOrNull(attempt)) }
                _events.tryEmit(
                    PlaybackEvent.Message(
                        "Skipped $skipped unavailable track${if (skipped == 1) "" else "s"}.",
                    ),
                )
            }

            if (generation != myGeneration) return@launch
            val playTrack = queue.getOrNull(attempt) ?: return@launch
            val resumeAt = startPositionMs.coerceAtLeast(0)
            if (preservePlayback && c.currentMediaItem?.mediaId == playTrack.id) {
                // A queue edit left the playing song in place. Replacing the
                // MediaItems would tear down and re-prepare the player, which
                // pauses the song. Keep the current item untouched and swap
                // only the upcoming items that follow it.
                val currentIndex = c.currentMediaItemIndex
                val tailCount = c.mediaItemCount - currentIndex - 1
                if (tailCount > 0) c.removeMediaItems(currentIndex + 1, tailCount)
            } else {
                c.setMediaItems(listOf(playTrack.toMediaItem(audio)), 0, resumeAt)
                if (preservePlayback) {
                    c.playWhenReady = resumePlaying
                    c.prepare()
                } else {
                    c.prepare()
                    c.play()
                }
            }
            _state.update {
                it.copy(
                    track = playTrack,
                    queueIndex = attempt,
                    positionMs = resumeAt,
                    isPreparing = false,
                    isPlaying = resumePlaying,
                )
            }
            library.addRecent(playTrack)
            refresh()
            persist(force = true)

            // Resolve the rest of the queue in order. The first items are ready
            // long before the current track ends, so transitions stay seamless.
            for (index in attempt + 1..queue.lastIndex) {
                if (generation != myGeneration) break
                val track = queue.getOrNull(index) ?: break
                try {
                    val nextAudio = resolver.resolve(track)
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
        scope.launch {
            val c = try {
                awaitController()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Failure("Couldn't connect to playback."))
                return@launch
            }
            if (c.mediaItemCount == 0) {
                // Nothing is loaded in the player but a session is on screen:
                // load the last song again at the position where it stopped.
                if (queue.isNotEmpty() && queueIndex in queue.indices) {
                    playTracks(queue, queueIndex, sourceTitle, _state.value.positionMs)
                }
                return@launch
            }
            // After a playback error, play() reuses the failed (probably
            // expired) source. Re-resolve the current track instead.
            if (c.playerError != null && queue.isNotEmpty()) {
                playTracks(queue, queueIndex.coerceIn(0, queue.lastIndex), sourceTitle)
                return@launch
            }
            if (c.isPlaying) c.pause() else c.play()
            refresh()
            persist(force = true)
        }
    }

    fun pause() {
        val c = controller
        if (c != null && c.isConnected && c.isPlaying) {
            c.pause()
        }
        _state.update { it.copy(isPlaying = false) }
        refresh()
        persist(force = true)
    }

    /**
     * Pauses playback after [minutes]. Passing 0 or less cancels a running
     * timer. The end time lives in [PlayerUiState] so the UI can count down
     * even when the player screen is closed.
     */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _state.update { it.copy(sleepEndsAtMs = null) }
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update { it.copy(sleepEndsAtMs = endsAt) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.takeIf { it.isConnected }?.let { c -> if (c.isPlaying) c.pause() }
            _state.update { it.copy(sleepEndsAtMs = null, isPlaying = false) }
            persist(force = true)
            _events.tryEmit(PlaybackEvent.Message("Sleep timer ended — playback paused."))
        }
    }

    fun setVolume(value: Float) {
        val level = value.coerceIn(0f, 1f)
        if (level > 0f) lastVolume = level
        _state.update { it.copy(volume = level, muted = level == 0f) }
        withController { it.volume = level }
    }

    fun toggleMute() {
        val next = if (_state.value.muted) {
            lastVolume.takeIf { it > 0f } ?: 1f
        } else {
            0f
        }
        if (next > 0f) lastVolume = next
        _state.update { it.copy(volume = next, muted = next == 0f) }
        withController { it.volume = next }
    }

    /** Routes ExoPlayer's audio to the chosen output device. */
    fun selectAudioDevice(device: AudioDeviceInfo) {
        val args = Bundle().apply {
            putInt(PlaybackService.EXTRA_AUDIO_DEVICE_ID, device.id)
        }
        withController { controller ->
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SET_AUDIO_DEVICE, Bundle.EMPTY),
                args,
            )
        }
        _state.update { it.copy(outputType = device.type, outputDeviceId = device.id) }
        if (device.type.isBluetoothAudio()) {
            scope.launch(Dispatchers.IO) {
                readBluetoothBattery(context, device.productName?.toString())?.let { level ->
                    _state.update { it.copy(bluetoothBattery = level) }
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        _state.update { it.copy(positionMs = target) }
        val c = controller
        if (c != null && c.isConnected && c.mediaItemCount > 0) {
            c.seekTo(target)
        }
        // Seeks are rare (tap or drag end), so keep the saved position exact.
        persist(force = true)
    }

    fun skipNext() {
        val c = controller
        if (c != null && c.isConnected && c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else if (queueIndex < queue.lastIndex) {
            playTracks(queue, queueIndex + 1, sourceTitle)
        }
    }

    fun skipPrevious() {
        val c = controller
        if (c != null && c.isConnected && c.currentPosition > 3_000) {
            c.seekTo(0)
            return
        }
        if (c != null && c.isConnected && c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else if (queueIndex > 0) {
            playTracks(queue, queueIndex - 1, sourceTitle)
        } else if (c != null && c.isConnected) {
            c.seekTo(0)
        } else if (restored) {
            _state.update { it.copy(positionMs = 0) }
            persist(force = true)
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
        val next = !_state.value.shuffle
        _state.update { it.copy(shuffle = next) }
        withController { it.shuffleModeEnabled = next }
        persist(force = true)
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _state.update { it.copy(repeatMode = next) }
        withController { it.repeatMode = next }
        persist(force = true)
    }

    fun playNext(track: Track) {
        addTrack(track, playNext = true)
    }

    fun addToQueue(track: Track) {
        addTrack(track, playNext = false)
    }

    private fun addTrack(track: Track, playNext: Boolean) {
        val c = controller
        if (queue.isEmpty() || c == null || !c.isConnected || c.mediaItemCount == 0) {
            // The player is empty but the last session is still on screen: add
            // to that queue instead of replacing it with a single song.
            if (restored && queue.isNotEmpty()) {
                val mutable = queue.toMutableList()
                val insertAt = if (playNext) {
                    (queueIndex + 1).coerceIn(0, mutable.size)
                } else {
                    mutable.size
                }
                mutable.add(insertAt, track)
                queue = mutable
                _state.update { it.copy(queue = queue) }
                persist(force = true)
                _events.tryEmit(PlaybackEvent.Message(if (playNext) "Playing next" else "Added to queue"))
                return
            }
            playTracks(listOf(track), 0, "Queue")
            return
        }
        scope.launch {
            try {
                val audio = resolver.resolve(track)
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
                persist(force = true)
                _events.tryEmit(PlaybackEvent.Message(if (playNext) "Playing next" else "Added to queue"))
            } catch (e: Exception) {
                _events.tryEmit(PlaybackEvent.Failure("Couldn't add “${track.title}” to the queue."))
            }
        }
    }

    /**
     * Appends a group of follow-up songs without interrupting the current
     * song. [expectedTrackId] guards against a late radio response landing
     * after the listener already skipped or started something else.
     */
    fun addTracksToQueue(
        tracks: List<Track>,
        expectedTrackId: String? = null,
        playNext: Boolean = false,
    ) {
        val candidates = tracks.distinctBy { it.id }
        if (candidates.isEmpty()) return
        val myGeneration = generation
        scope.launch {
            val c = try {
                awaitController()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            if (expectedTrackId != null && _state.value.track?.id != expectedTrackId) {
                return@launch
            }
            if (c.mediaItemCount == 0) {
                // Nothing is loaded yet: starting the collection is more
                // useful than silently dropping it into an empty queue.
                playTracks(candidates, 0, "Queue")
                return@launch
            }

            var added = 0
            for (track in candidates) {
                if (generation != myGeneration) return@launch
                if (expectedTrackId != null && _state.value.track?.id != expectedTrackId) {
                    return@launch
                }
                if (queue.any { it.id == track.id }) continue
                try {
                    val audio = resolver.resolve(track)
                    if (generation != myGeneration) return@launch
                    if (expectedTrackId != null && _state.value.track?.id != expectedTrackId) {
                        return@launch
                    }
                    val item = track.toMediaItem(audio)
                    if (playNext) {
                        val mediaAt = (c.currentMediaItemIndex + 1 + added)
                            .coerceIn(0, c.mediaItemCount)
                        c.addMediaItem(mediaAt, item)
                        val mutable = queue.toMutableList()
                        val queueAt = (queueIndex + 1 + added).coerceIn(0, mutable.size)
                        mutable.add(queueAt, track)
                        queue = mutable
                    } else {
                        c.addMediaItem(item)
                        queue = queue + track
                    }
                    _state.update { it.copy(queue = queue) }
                    added++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One unavailable song shouldn't drop the rest of the mix.
                }
            }
            if (added > 0) {
                persist(force = true)
                _events.tryEmit(
                    PlaybackEvent.Message(
                        if (playNext) "Playing $added songs next" else "Added $added songs to the queue",
                    ),
                )
            }
        }
    }

    /** Moves an existing queue item, keeping the same song playing. */
    fun moveQueueItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val next = queue.toMutableList().apply { add(to, removeAt(from)) }
        resyncQueue(next, QueueMath.indexAfterMove(queueIndex, from, to))
    }

    /** Moves a queue item to the slot right after the playing song. */
    fun playNextInQueue(index: Int) {
        if (index !in queue.indices || index == queueIndex) return
        moveQueueItem(index, QueueMath.playNextTarget(queueIndex, index))
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val next = queue.toMutableList().apply { removeAt(index) }
        if (next.isEmpty()) {
            clearQueue()
            return
        }
        resyncQueue(next, QueueMath.indexAfterRemove(queueIndex, index, next.size))
    }

    fun clearQueue() {
        generation++
        appendJob?.cancel()
        retriedTrackId = null
        restored = false
        queue = emptyList()
        queueIndex = -1
        _state.update {
            it.copy(
                track = null,
                queue = emptyList(),
                queueIndex = -1,
                isPlaying = false,
                isPreparing = false,
                isBuffering = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        playbackStore.clear()
        withController { c ->
            c.clearMediaItems()
            c.stop()
        }
        _events.tryEmit(PlaybackEvent.Message("Queue cleared"))
    }

    /**
     * Rebuilds the player's media items from an edited app queue. The current
     * song and its position are carried over so editing doesn't restart it.
     */
    private fun resyncQueue(next: List<Track>, nextIndex: Int) {
        if (next.isEmpty()) {
            clearQueue()
            return
        }
        val position = controller?.takeIf { it.isConnected && it.mediaItemCount > 0 }
            ?.currentPosition
            ?: _state.value.positionMs
        playTracks(
            tracks = next,
            startIndex = nextIndex,
            sourceTitle = sourceTitle,
            startPositionMs = position,
            preservePlayback = true,
        )
    }

    fun toggleLike(track: Track) {
        library.toggleLike(track)
    }

    private suspend fun awaitController(): MediaController {
        controller?.takeIf { it.isConnected && !serviceReleased }?.let { return it }
        val future = synchronized(this) {
            val stale = controller
            if (stale != null || serviceReleased) {
                // The service stopped (for example after the task was removed
                // while paused). Build a fresh connection for the next action.
                controller = null
                controllerFuture = buildController()
                serviceReleased = false
            }
            controllerFuture
        }
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
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
        if (!c.isConnected) return
        if (c.mediaItemCount == 0) {
            if (queue.isNotEmpty()) {
                // The service is gone but the last session is still on screen.
                restored = true
                _state.update {
                    it.copy(
                        isPlaying = false,
                        isPreparing = false,
                        isBuffering = false,
                        queue = queue,
                        queueIndex = queueIndex,
                        sourceTitle = sourceTitle,
                    )
                }
            }
            return
        }
        restored = false
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
        val artwork = coverArt?.cached(id)
            ?: Artwork.songArt(thumbnailUrl)
            ?: Artwork.still(thumbnailUrl)
            ?: Artwork.stillForVideo(id)
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
            .apply { audio.mimeType?.let { setMimeType(it) } }
            .setMediaMetadata(metadata)
            .build()
    }

    private companion object {
        /** How many following tracks are tried when the selected one fails. */
        const val SKIP_WINDOW = 8

        /** Stop auto-advancing after this many failures in a row. */
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** How often the playback position is written while music plays. */
        const val PERSIST_INTERVAL_MS = 5_000L
    }
}
