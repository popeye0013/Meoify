package com.meoify.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.meoify.music.MainActivity
import com.meoify.music.MeoifyApplication
import com.meoify.music.R
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.InnerTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hosts the ExoPlayer and the media session.
 *
 * The media session is also the source of the lockscreen and notification
 * controls, so the queue, the Like state and shuffle stay usable without the
 * activity being open.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as MeoifyApplication).container

    /** Track ids currently in Liked Music, mirrored from the library store. */
    private var likedIds: Set<String> = emptySet()

    /** Last button state pushed to the notification, to avoid needless rebuilds. */
    private var lastButtonState: Triple<Boolean, Boolean, Boolean>? = null

    /** Stops the service a moment after playback pauses in the background. */
    private var idleStopJob: Job? = null

    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()

        // YouTube's CDN rejects some stream URLs when the request carries
        // ExoPlayer's default user agent, and some hosts expect a browser
        // Referer/Origin. Use the same identity as the catalogue client so a
        // resolved URL keeps working when playback starts.
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(InnerTube.USER_AGENT)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://music.youtube.com/",
                    "Origin" to "https://music.youtube.com",
                ),
            )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // The heart follows the current song.
                refreshMediaButtons()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshMediaButtons()
                if (isPlaying) {
                    idleStopJob?.cancel()
                } else {
                    scheduleIdleStop()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                refreshMediaButtons()
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): ConnectionResult {
                // Custom commands must be advertised to controllers, otherwise
                // the notification drops the Like and Shuffle buttons.
                val commands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(likeCommand)
                    .add(shuffleCommand)
                    .build()
                return ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
                ACTION_TOGGLE_LIKE -> {
                    toggleLike()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_TOGGLE_SHUFFLE -> {
                    toggleShuffle()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(callback)
            .setMediaButtonPreferences(buildPreferences())
            .build()

        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification)
        }
        setMediaNotificationProvider(notificationProvider)

        // Watching the library keeps the notification heart in step with likes
        // made in the app (or mirrored from YouTube Music).
        scope.launch {
            container.library.library.collect { data ->
                likedIds = data.liked.mapTo(HashSet()) { it.id }
                refreshMediaButtons()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Only genuinely playing audio keeps the service alive after the task
        // is removed. A paused session is saved and then torn down, so Meoify
        // never lingers in the background draining the battery; reopening the
        // app restores the last song from the saved snapshot.
        if (player == null || player.mediaItemCount == 0 || !player.playWhenReady) {
            container.player.saveSnapshotNow()
            // pauseAllPlayersAndStopSelf also disables the 10-minute foreground
            // timeout, so the service disappears from active apps right away.
            pauseAllPlayersAndStopSelf()
        }
    }

    /**
     * A pause in the background should not leave a foreground service running
     * (Android lists it under active apps). Give playback a few seconds in case
     * the pause is just a track change, then stop and keep the snapshot.
     */
    private fun scheduleIdleStop() {
        if (container.inForeground) return
        idleStopJob?.cancel()
        idleStopJob = scope.launch {
            delay(IDLE_STOP_DELAY_MS)
            val player = mediaSession?.player ?: return@launch
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) return@launch
            if (container.inForeground) return@launch
            container.player.saveSnapshotNow()
            // This is the supported way to leave the foreground state and end
            // the service even while Media3's idle timeout is still armed.
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ media buttons

    private fun currentTrack(): Track? {
        val mediaItem = mediaSession?.player?.currentMediaItem ?: return null
        val id = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: return null
        // The app's own state has the richest metadata (album, artwork, ...).
        container.player.state.value.track?.takeIf { it.id == id }?.let { return it }
        val metadata = mediaItem.mediaMetadata
        return Track(
            id = id,
            title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown track",
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            durationMs = metadata.durationMs ?: 0L,
            thumbnailUrl = metadata.artworkUri?.toString(),
        )
    }

    private fun buildPreferences(): List<CommandButton> {
        val track = currentTrack()
        val liked = track != null && track.id in likedIds
        val shuffle = mediaSession?.player?.shuffleModeEnabled == true
        return listOf(
            CommandButton.Builder(
                if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
            )
                .setSessionCommand(likeCommand)
                .setDisplayName(if (liked) "Remove from Liked Music" else "Like")
                .setEnabled(track != null)
                .build(),
            CommandButton.Builder(
                if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
            )
                .setSessionCommand(shuffleCommand)
                .setDisplayName(if (shuffle) "Turn shuffle off" else "Turn shuffle on")
                .build(),
        )
    }

    private fun refreshMediaButtons() {
        val session = mediaSession ?: return
        val track = currentTrack()
        val liked = track != null && track.id in likedIds
        val shuffle = session.player.shuffleModeEnabled
        val state = Triple(liked, shuffle, track != null)
        if (state == lastButtonState) return
        lastButtonState = state
        val preferences = buildPreferences()
        session.setMediaButtonPreferences(preferences)
        // The session-wide call refreshes the notification. Calling it for
        // each controller as well makes the platform session (the lockscreen
        // and system media controls) redraw its custom actions right away
        // instead of waiting for the next playback state change.
        for (controller in session.connectedControllers) {
            session.setMediaButtonPreferences(controller, preferences)
        }
    }

    private fun toggleLike() {
        val track = currentTrack() ?: return
        val wasLiked = track.id in likedIds
        scope.launch {
            if (container.repository.signedIn) {
                val ok = runCatching { container.ytLibrary.setLiked(track, !wasLiked) }
                    .getOrDefault(false)
                if (!ok) {
                    Toast.makeText(
                        this@PlaybackService,
                        "Couldn't update Liked Music on YouTube Music",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                container.library.toggleLike(track)
            }
        }
    }

    private fun toggleShuffle() {
        val player = mediaSession?.player ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    private companion object {
        const val ACTION_TOGGLE_LIKE = "com.meoify.music.action.TOGGLE_LIKE"
        const val ACTION_TOGGLE_SHUFFLE = "com.meoify.music.action.TOGGLE_SHUFFLE"

        /** Grace period before a background pause tears the service down. */
        const val IDLE_STOP_DELAY_MS = 5_000L
    }
}
