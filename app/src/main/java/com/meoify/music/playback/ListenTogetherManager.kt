package com.meoify.music.playback

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.UUID

@Serializable
enum class SyncAction {
    PLAY,
    PAUSE,
    SEEK,
    CHANGE_TRACK,
    SYNC_STATE,
    JOIN,
    LEAVE,
    PING,
}

@Serializable
data class SyncPayload(
    val senderId: String,
    val action: SyncAction,
    val track: Track? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    /** Display name of the listener who sent this message. */
    val senderName: String? = null,
    /** Public avatar URL from YouTube Music; local pictures are not uploaded. */
    val senderAvatarUrl: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

/** The identity shown to other listeners in a session. */
data class SessionProfile(
    val name: String,
    val avatarUrl: String? = null,
    /** Local file path for the signed-in device's own avatar. */
    val localAvatarPath: String? = null,
)

data class SessionState(
    val connected: Boolean = false,
    val isConnecting: Boolean = false,
    val roomCode: String? = null,
    val isHost: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val selfName: String? = null,
    val selfAvatarUrl: String? = null,
    val selfLocalAvatarPath: String? = null,
    val peerName: String? = null,
    val peerAvatarUrl: String? = null,
)

class ListenTogetherManager(
    private val player: PlayerConnection,
    private val settings: SettingsStore,
    private val http: OkHttpClient = OkHttpClient(),
    private val relayServerBase: String = "https://ntfy.sh",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val clientId: String = UUID.randomUUID().toString()

    /**
     * YouTube Music account identity when signed in, otherwise the Meoify local
     * profile. Local avatar images stay on this device; only a public URL can be
     * shared with peers over the relay.
     */
    private fun currentProfile(): SessionProfile {
        val current = settings.settings.value
        val ytName = current.accountName?.takeIf { it.isNotBlank() }
        return if (current.ytCookies != null && ytName != null) {
            SessionProfile(
                name = ytName,
                avatarUrl = current.accountAvatarUrl?.takeIf { it.startsWith("http") },
            )
        } else {
            SessionProfile(
                name = current.localProfileName?.takeIf { it.isNotBlank() } ?: "Listener",
                localAvatarPath = current.localAvatarPath,
            )
        }
    }

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var isApplyingRemoteUpdate = false
    private var lastObservedTrackId: String? = null
    private var lastObservedIsPlaying: Boolean = false
    private var lastObservedPositionMs: Long = 0L
    private var heartbeatJob: Job? = null

    init {
        // Monitor player state to broadcast changes to peers
        scope.launch {
            player.state.collectLatest { state ->
                onLocalPlayerStateChange(state)
            }
        }
    }

    private fun topicForRoom(code: String): String = "meoify_sync_${code.trim()}"

    fun hostSession(code: String) {
        val cleanCode = code.trim()
        if (cleanCode.length < 4) return
        connectToRoom(cleanCode, isHost = true)
    }

    fun joinSession(code: String) {
        val cleanCode = code.trim()
        if (cleanCode.length < 4) return
        connectToRoom(cleanCode, isHost = false)
    }

    private fun connectToRoom(code: String, isHost: Boolean) {
        disconnect()

        val profile = currentProfile()
        _session.update {
            it.copy(
                isConnecting = true,
                roomCode = code,
                isHost = isHost,
                statusMessage = "Connecting to session $code…",
                error = null,
                selfName = profile.name,
                selfAvatarUrl = profile.avatarUrl,
                selfLocalAvatarPath = profile.localAvatarPath,
                peerName = null,
                peerAvatarUrl = null,
            )
        }

        val topic = topicForRoom(code)
        val wsHost = relayServerBase
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .trimEnd('/')

        val wsUrl = "$wsHost/$topic/ws"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        activeWebSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    _session.update {
                        it.copy(
                            connected = true,
                            isConnecting = false,
                            statusMessage = if (isHost) "Hosting session $code. Waiting for friends…" else "Joined session $code!",
                            error = null,
                        )
                    }

                    if (isHost) {
                        val state = player.state.value
                        broadcast(
                            SyncPayload(
                                senderId = clientId,
                                action = SyncAction.SYNC_STATE,
                                track = state.track,
                                positionMs = state.positionMs,
                                isPlaying = state.isPlaying,
                            )
                        )
                        startHostHeartbeat()
                    } else {
                        // Ask host to send current state
                        broadcast(
                            SyncPayload(
                                senderId = clientId,
                                action = SyncAction.JOIN,
                            )
                        )
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleIncomingMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    _session.update {
                        it.copy(
                            connected = false,
                            isConnecting = false,
                            error = "Connection failed: ${t.localizedMessage ?: "Network error"}",
                        )
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    _session.update {
                        it.copy(connected = false, isConnecting = false, statusMessage = "Session closed")
                    }
                }
            }
        })
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (_session.value.connected) {
            broadcast(
                SyncPayload(
                    senderId = clientId,
                    action = SyncAction.LEAVE,
                )
            )
        }
        runCatching { activeWebSocket?.close(1000, "User left") }
        activeWebSocket = null
        _session.update { SessionState() }
    }

    fun broadcast(payload: SyncPayload) {
        val code = _session.value.roomCode ?: return
        val topic = topicForRoom(code)
        val url = "${relayServerBase.trimEnd('/')}/$topic"

        // Stamp every outgoing message with the current identity so peers can
        // show who is hosting and who joined without a separate handshake.
        val profile = currentProfile()
        val outgoing = payload.copy(
            senderName = payload.senderName ?: profile.name,
            senderAvatarUrl = payload.senderAvatarUrl ?: profile.avatarUrl,
        )
        val body = json.encodeToString(outgoing).toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(body)
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silently ignore transient relay post failures
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun requestSyncWithHost() {
        broadcast(
            SyncPayload(
                senderId = clientId,
                action = SyncAction.JOIN,
            )
        )
        _session.update { it.copy(statusMessage = "Requesting state from host…") }
    }

    fun broadcastSeek(positionMs: Long) {
        broadcast(
            SyncPayload(
                senderId = clientId,
                action = SyncAction.SEEK,
                positionMs = positionMs,
            )
        )
    }

    internal fun handleIncomingMessage(rawText: String) {
        // ntfy wraps the payload as: {"id": "...", "event": "message", "message": "<SyncPayload JSON>"}
        val payloadJson = runCatching {
            val root = json.parseToJsonElement(rawText).jsonObject
            root["message"]?.jsonPrimitive?.content ?: rawText
        }.getOrDefault(rawText)

        val payload = runCatching {
            json.decodeFromString<SyncPayload>(payloadJson)
        }.getOrNull() ?: return

        // Ignore our own echo
        if (payload.senderId == clientId) return

        applyRemotePayload(payload)
    }

    private fun applyRemotePayload(payload: SyncPayload) {
        if (!payload.senderName.isNullOrBlank()) {
            _session.update {
                it.copy(
                    peerName = payload.senderName,
                    peerAvatarUrl = payload.senderAvatarUrl,
                )
            }
        }
        isApplyingRemoteUpdate = true
        scope.launch {
            try {
                when (payload.action) {
                    SyncAction.JOIN -> {
                        if (_session.value.isHost) {
                            val state = player.state.value
                            broadcast(
                                SyncPayload(
                                    senderId = clientId,
                                    action = SyncAction.SYNC_STATE,
                                    track = state.track,
                                    positionMs = state.positionMs,
                                    isPlaying = state.isPlaying,
                                )
                            )
                            _session.update { it.copy(statusMessage = "A friend connected to your session!") }
                        }
                    }

                    SyncAction.SYNC_STATE -> {
                        val state = player.state.value
                        val targetTrack = payload.track
                        if (targetTrack != null) {
                            if (state.track?.id != targetTrack.id) {
                                player.playTracks(
                                    tracks = listOf(targetTrack),
                                    startIndex = 0,
                                    sourceTitle = "Listen Together",
                                    startPositionMs = payload.positionMs,
                                    preservePlayback = false,
                                )
                                if (!payload.isPlaying) {
                                    delay(400)
                                    player.pause()
                                }
                            } else {
                                player.seekTo(payload.positionMs)
                                if (payload.isPlaying && !state.isPlaying) {
                                    player.togglePlayPause()
                                } else if (!payload.isPlaying && state.isPlaying) {
                                    player.pause()
                                }
                            }
                            _session.update { it.copy(statusMessage = "Synced with host: ${targetTrack.title}") }
                        }
                    }

                    SyncAction.CHANGE_TRACK -> {
                        val targetTrack = payload.track
                        if (targetTrack != null) {
                            player.playTracks(
                                tracks = listOf(targetTrack),
                                startIndex = 0,
                                sourceTitle = "Listen Together",
                                startPositionMs = payload.positionMs,
                            )
                            _session.update { it.copy(statusMessage = "Track changed: ${targetTrack.title}") }
                        }
                    }

                    SyncAction.PLAY -> {
                        val state = player.state.value
                        if (payload.track != null && state.track?.id != payload.track.id) {
                            player.playTracks(
                                tracks = listOf(payload.track),
                                startIndex = 0,
                                sourceTitle = "Listen Together",
                                startPositionMs = payload.positionMs,
                            )
                        } else {
                            player.seekTo(payload.positionMs)
                            if (!state.isPlaying) player.togglePlayPause()
                        }
                        _session.update { it.copy(statusMessage = "Playback resumed by peer") }
                    }

                    SyncAction.PAUSE -> {
                        player.seekTo(payload.positionMs)
                        if (player.state.value.isPlaying) player.pause()
                        _session.update { it.copy(statusMessage = "Playback paused by peer") }
                    }

                    SyncAction.SEEK -> {
                        player.seekTo(payload.positionMs)
                        _session.update { it.copy(statusMessage = "Seeked by peer to ${payload.positionMs / 1000}s") }
                    }

                    SyncAction.PING -> {
                        // Check for significant drift (> 2.5 seconds) while playing
                        val currentPos = player.state.value.positionMs
                        if (kotlin.math.abs(currentPos - payload.positionMs) > 2500) {
                            player.seekTo(payload.positionMs)
                        }
                    }

                    SyncAction.LEAVE -> {
                        _session.update { it.copy(statusMessage = "A peer left the room") }
                    }
                }
            } finally {
                delay(300)
                isApplyingRemoteUpdate = false
            }
        }
    }

    private fun onLocalPlayerStateChange(state: PlayerUiState) {
        if (!_session.value.connected || isApplyingRemoteUpdate) return

        val trackChanged = state.track != null && state.track.id != lastObservedTrackId
        val playStateChanged = state.isPlaying != lastObservedIsPlaying

        lastObservedTrackId = state.track?.id
        lastObservedIsPlaying = state.isPlaying
        lastObservedPositionMs = state.positionMs

        if (trackChanged && state.track != null) {
            broadcast(
                SyncPayload(
                    senderId = clientId,
                    action = SyncAction.CHANGE_TRACK,
                    track = state.track,
                    positionMs = state.positionMs,
                    isPlaying = state.isPlaying,
                )
            )
        } else if (playStateChanged) {
            broadcast(
                SyncPayload(
                    senderId = clientId,
                    action = if (state.isPlaying) SyncAction.PLAY else SyncAction.PAUSE,
                    track = state.track,
                    positionMs = state.positionMs,
                    isPlaying = state.isPlaying,
                )
            )
        }
    }

    private fun startHostHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(8000)
                if (!_session.value.connected || !_session.value.isHost) break
                val state = player.state.value
                if (state.isPlaying && state.track != null) {
                    broadcast(
                        SyncPayload(
                            senderId = clientId,
                            action = SyncAction.PING,
                            positionMs = state.positionMs,
                            isPlaying = true,
                        )
                    )
                }
            }
        }
    }
}
