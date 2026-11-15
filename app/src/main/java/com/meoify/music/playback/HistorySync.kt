package com.meoify.music.playback

import android.os.SystemClock
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.local.SettingsStore
import com.meoify.music.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Mirrors songs genuinely listened to in Meoify into the connected YouTube
 * Music account's history.
 *
 * It is completely independent of where the audio came from: a normal stream,
 * a lossless provider or a downloaded file all register the same video id, so
 * history stays correct regardless of the playback source. Playback is never
 * blocked or altered; matching and submission happen on background coroutines
 * and every failure is swallowed.
 *
 * Protocol (same as ytmusicapi's `add_history_item`): request an authenticated
 * `player` response for the video id, take
 * `playbackTracking.videostatsPlaybackUrl.baseUrl`, then GET that URL with
 * `ver=2`, `c=WEB_REMIX` and a random 16-character `cpn`. Anonymous player
 * responses return a URL whose ping is accepted but never recorded, so the
 * sync only runs while signed in.
 *
 * A listen qualifies after 30 seconds of real playback, the same bar YouTube
 * Music itself uses before it writes an item to history. Only real position
 * movement while playing and not buffering counts — pauses, buffering and
 * seeks contribute nothing.
 */
class HistorySync(
    private val player: PlayerConnection,
    private val repository: MusicRepository,
    private val settings: SettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<String?>(null)

    /** Short, human readable result of the last attempt, shown in Settings. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private var sessionTrackId: String? = null
    private var lastPositionMs = 0L
    private var submitted = false
    private var listeningMs = 0L
    private var lastSampleAt = SystemClock.elapsedRealtime()
    private var submissionJob: Job? = null

    init {
        scope.launch {
            player.state.collectLatest { onPlayerState(it) }
        }
    }

    private fun onPlayerState(state: PlayerUiState) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastSampleAt).coerceAtLeast(0L)
        lastSampleAt = now

        val track = state.track
        if (track == null) {
            reset()
            return
        }

        val delta = state.positionMs - lastPositionMs
        val restarted = state.positionMs < RESTART_POSITION_MS && lastPositionMs > RESTART_FLOOR_MS
        lastPositionMs = state.positionMs

        if (track.id != sessionTrackId || restarted) {
            sessionTrackId = track.id
            listeningMs = 0L
            submitted = false
        }

        val prefs = settings.settings.value
        if (!prefs.historySync || prefs.ytCookies.isNullOrBlank()) {
            if (!prefs.historySync) submissionJob?.cancel()
            return
        }
        if (!state.isPlaying || state.isBuffering) return
        if (submitted) return

        if (delta in 1..MAX_POSITION_DELTA_MS && delta <= elapsed + POSITION_JITTER_MS) {
            listeningMs += delta
        }
        if (listeningMs < requiredListenMs(state.durationMs)) return

        submitted = true
        submissionJob = scope.launch(Dispatchers.IO) { submit(track) }
    }

    private fun reset() {
        sessionTrackId = null
        lastPositionMs = 0L
        submitted = false
        listeningMs = 0L
    }

    private suspend fun submit(track: Track) {
        if (!VIDEO_ID.matches(track.id)) {
            _status.value = "Skipped “${track.title}” — not a YouTube song"
            return
        }
        val prefs = settings.current()
        if (!prefs.historySync || prefs.ytCookies.isNullOrBlank()) return

        val playback = repository.historyPlayback(track.id)
        if (playback == null) {
            _status.value = "Couldn't sync — YouTube Music didn't return a tracking URL"
            return
        }
        val cpn = buildString {
            repeat(CPN_LENGTH) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) }
        }

        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            if (!settings.settings.value.historySync) return
            val code = repository.submitHistoryPlayback(
                trackingBaseUrl = playback.trackingUrl,
                cpn = cpn,
                visitorData = playback.visitorData,
            )
            when {
                code in 200..299 -> {
                    _status.value = "Synced “${track.title}” to YouTube Music history"
                    return
                }
                // Bad credentials or invalid tracking URL: retrying can't help.
                code == 0 -> {
                    _status.value = "Couldn't sync — YouTube rejected the tracking URL"
                    return
                }
                code == 401 || code == 403 -> {
                    _status.value = "Couldn't sync — sign in to YouTube Music again"
                    return
                }
                code == 408 || code == 429 || code in 500..599 -> delay(RETRY_DELAY_MS * attempt)
                else -> {
                    _status.value = "Couldn't sync (HTTP $code)"
                    return
                }
            }
        }
        _status.value = "Couldn't sync — YouTube Music didn't respond in time"
    }

    private fun requiredListenMs(durationMs: Long): Long = LISTEN_THRESHOLD_MS

    private companion object {
        const val LISTEN_THRESHOLD_MS = 30_000L

        const val MAX_POSITION_DELTA_MS = 2_500L
        const val POSITION_JITTER_MS = 500L

        const val RESTART_POSITION_MS = 3_000L
        const val RESTART_FLOOR_MS = 10_000L

        const val MAX_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 2_000L

        const val CPN_LENGTH = 16
        const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
