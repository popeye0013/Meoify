package com.meoify.music.playback

import android.os.SystemClock
import com.meoify.music.data.MusicRepository
import com.meoify.music.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Submits listens to Last.fm.
 *
 * `track.updateNowPlaying` is sent as soon as a new song starts, and the
 * scrobble is sent once the song has been played for 30 seconds (or half its
 * duration, whichever comes first). All network work happens off the player
 * thread and failures only update the status shown in Settings; playback is
 * never affected.
 */
class ScrobbleSync(
    private val player: PlayerConnection,
    private val repository: MusicRepository,
    private val settings: SettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var sessionTrackId: String? = null
    private var lastPositionMs = 0L
    private var listeningMs = 0L
    private var submitted = false
    private var nowPlayingJob: Job? = null
    private var scrobbleJob: Job? = null

    init {
        scope.launch {
            player.state.collectLatest { onPlayerState(it) }
        }
    }

    private fun onPlayerState(state: PlayerUiState) {
        val track = state.track ?: run {
            sessionTrackId = null
            lastPositionMs = 0L
            listeningMs = 0L
            submitted = false
            return
        }

        val delta = state.positionMs - lastPositionMs
        val restarted = state.positionMs < RESTART_POSITION_MS && lastPositionMs > RESTART_FLOOR_MS
        lastPositionMs = state.positionMs

        if (track.id != sessionTrackId || restarted) {
            sessionTrackId = track.id
            listeningMs = 0L
            submitted = false
            val prefs = settings.settings.value
            if (prefs.scrobblingEnabled && !prefs.lastfmSessionKey.isNullOrBlank()) {
                nowPlayingJob?.cancel()
                nowPlayingJob = scope.launch {
                    val ok = runCatching { repository.lastFmNowPlaying(track) }.getOrDefault(false)
                    if (ok) _status.value = "Now playing on Last.fm: ${track.title}"
                }
            }
        }

        val prefs = settings.settings.value
        if (!prefs.scrobblingEnabled || prefs.lastfmSessionKey.isNullOrBlank()) return
        if (!state.isPlaying || state.isBuffering || submitted) return

        if (delta in 1..MAX_POSITION_DELTA_MS) {
            listeningMs += delta
        }
        if (listeningMs < requiredListenMs(state.durationMs)) return

        submitted = true
        scrobbleJob = scope.launch {
            val timestamp = System.currentTimeMillis() / 1000
            val ok = runCatching { repository.lastFmScrobble(track, timestamp) }.getOrDefault(false)
            _status.value = if (ok) {
                "Scrobbled “${track.title}” to Last.fm"
            } else {
                "Couldn't scrobble “${track.title}” — check your Last.fm connection"
            }
        }
    }

    private fun requiredListenMs(durationMs: Long): Long {
        val half = if (durationMs > 0) durationMs / 2 else Long.MAX_VALUE
        return minOf(SCROBBLE_THRESHOLD_MS, half)
    }

    private companion object {
        const val SCROBBLE_THRESHOLD_MS = 30_000L
        const val MAX_POSITION_DELTA_MS = 2_500L
        const val RESTART_POSITION_MS = 3_000L
        const val RESTART_FLOOR_MS = 10_000L
    }
}
