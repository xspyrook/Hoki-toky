package com.example.data.network

import android.util.Log
import com.example.data.model.ConnectionStatus
import com.example.data.model.GroupMember
import com.example.data.model.MemberRole
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

sealed class SignalingEvent {
    data class Authenticated(val userId: String) : SignalingEvent()
    data class ChannelJoined(
        val channelId: String,
        val listenersCount: Int,
        val currentSpeakerId: String?,
        val currentSpeakerName: String?,
        val lastSpeakerName: String?,
        val members: List<GroupMember>
    ) : SignalingEvent()

    data class PresenceUpdate(
        val channelId: String,
        val listenersCount: Int,
        val members: List<GroupMember>
    ) : SignalingEvent()

    data class FloorGranted(
        val channelId: String,
        val speakerId: String,
        val speakerName: String
    ) : SignalingEvent()

    data class FloorBusy(
        val channelId: String,
        val holderId: String,
        val holderName: String
    ) : SignalingEvent()

    data class FloorReleased(
        val channelId: String,
        val lastSpeakerName: String?
    ) : SignalingEvent()

    data class FloorDenied(
        val channelId: String,
        val reason: String
    ) : SignalingEvent()

    data class ErrorNotice(val message: String) : SignalingEvent()
}

class SignalingWebSocketClient(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _latencyMs = MutableStateFlow<Int?>(null)
    val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var currentBaseUrl: String = ""
    private var currentToken: String = ""
    private var activeChannelId: String? = null
    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private var lastPingSentAt = 0L

    fun connect(backendBaseUrl: String, authToken: String) {
        if (backendBaseUrl.isBlank() || authToken.isBlank()) {
            _connectionState.value = ConnectionStatus.DISCONNECTED
            return
        }
        currentBaseUrl = backendBaseUrl.trim().trimEnd('/')
        currentToken = authToken
        shouldStayConnected = true
        reconnectAttempt = 0
        openSocket()
    }

    fun disconnect() {
        shouldStayConnected = false
        activeChannelId = null
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Client sign out")
        webSocket = null
        _latencyMs.value = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
    }

    fun forceReconnect() {
        if (!shouldStayConnected || currentBaseUrl.isBlank() || currentToken.isBlank()) return
        reconnectJob?.cancel()
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = ConnectionStatus.RECONNECTING
        openSocket()
    }

    fun joinChannel(channelId: String) {
        activeChannelId = channelId
        sendJson(JSONObject().apply {
            put("type", "channel:join")
            put("channelId", channelId)
        })
    }

    fun leaveChannel(channelId: String) {
        if (activeChannelId == channelId) {
            activeChannelId = null
        }
        sendJson(JSONObject().apply {
            put("type", "channel:leave")
            put("channelId", channelId)
        })
    }

    fun requestFloor(channelId: String): Boolean {
        return sendJson(JSONObject().apply {
            put("type", "ptt:request")
            put("channelId", channelId)
        })
    }

    fun releaseFloor(channelId: String): Boolean {
        return sendJson(JSONObject().apply {
            put("type", "ptt:release")
            put("channelId", channelId)
        })
    }

    private fun openSocket() {
        if (!shouldStayConnected) return
        _connectionState.value = if (reconnectAttempt > 0) {
            ConnectionStatus.RECONNECTING
        } else {
            ConnectionStatus.CONNECTING
        }

        val wsUrl = buildWsUrl(currentBaseUrl, currentToken)
        val request = Request.Builder().url(wsUrl).build()
        webSocket?.cancel()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                reconnectAttempt = 0
                _connectionState.value = ConnectionStatus.CONNECTED
                startHeartbeat()
                activeChannelId?.let { chId ->
                    joinChannel(chId)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _latencyMs.value = null
                if (shouldStayConnected) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionStatus.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                heartbeatJob?.cancel()
                _latencyMs.value = null
                if (shouldStayConnected) {
                    _connectionState.value = ConnectionStatus.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionStatus.ERROR
                }
            }
        })
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && shouldStayConnected) {
                lastPingSentAt = System.currentTimeMillis()
                sendJson(JSONObject().apply {
                    put("type", "ping")
                    put("ts", lastPingSentAt)
                })
                delay(12_000L)
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val backoffMs = min(30_000L, 1_500L * (1L shl min(reconnectAttempt, 4)))
            _connectionState.value = ConnectionStatus.RECONNECTING
            delay(backoffMs)
            if (shouldStayConnected) {
                openSocket()
            }
        }
    }

    private fun sendJson(payload: JSONObject): Boolean {
        val ws = webSocket ?: return false
        return ws.send(payload.toString())
    }

    private fun handleIncomingMessage(raw: String) {
        runCatching {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "pong" -> {
                    val sentAt = json.optLong("ts", lastPingSentAt)
                    if (sentAt > 0L) {
                        val rtt = (System.currentTimeMillis() - sentAt).toInt().coerceAtLeast(1)
                        _latencyMs.value = rtt
                    }
                }
                "authenticated" -> {
                    _events.tryEmit(SignalingEvent.Authenticated(json.optString("userId")))
                }
                "channel:joined" -> {
                    _events.tryEmit(
                        SignalingEvent.ChannelJoined(
                            channelId = json.optString("channelId"),
                            listenersCount = json.optInt("listenersCount", 0),
                            currentSpeakerId = json.optString("currentSpeakerId").takeIf { it.isNotBlank() && it != "null" },
                            currentSpeakerName = json.optString("currentSpeakerName").takeIf { it.isNotBlank() && it != "null" },
                            lastSpeakerName = json.optString("lastSpeakerName").takeIf { it.isNotBlank() && it != "null" },
                            members = parseMembers(json.optJSONArray("members"))
                        )
                    )
                }
                "channel:presence" -> {
                    _events.tryEmit(
                        SignalingEvent.PresenceUpdate(
                            channelId = json.optString("channelId"),
                            listenersCount = json.optInt("listenersCount", 0),
                            members = parseMembers(json.optJSONArray("members"))
                        )
                    )
                }
                "floor:granted" -> {
                    _events.tryEmit(
                        SignalingEvent.FloorGranted(
                            channelId = json.optString("channelId"),
                            speakerId = json.optString("speakerId"),
                            speakerName = json.optString("speakerName")
                        )
                    )
                }
                "floor:busy" -> {
                    _events.tryEmit(
                        SignalingEvent.FloorBusy(
                            channelId = json.optString("channelId"),
                            holderId = json.optString("holderId"),
                            holderName = json.optString("holderName")
                        )
                    )
                }
                "floor:released" -> {
                    _events.tryEmit(
                        SignalingEvent.FloorReleased(
                            channelId = json.optString("channelId"),
                            lastSpeakerName = json.optString("lastSpeakerName").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
                "floor:denied" -> {
                    _events.tryEmit(
                        SignalingEvent.FloorDenied(
                            channelId = json.optString("channelId"),
                            reason = json.optString("reason", "Not permitted")
                        )
                    )
                }
                "error" -> {
                    _events.tryEmit(SignalingEvent.ErrorNotice(json.optString("message", "Signaling error")))
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed parsing WS message", e)
        }
    }

    private fun parseMembers(arr: JSONArray?): List<GroupMember> {
        if (arr == null) return emptyList()
        val list = ArrayList<GroupMember>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val roleStr = obj.optString("role", "member")
            val role = when (roleStr.lowercase()) {
                "owner" -> MemberRole.OWNER
                "moderator" -> MemberRole.MODERATOR
                else -> MemberRole.MEMBER
            }
            list.add(
                GroupMember(
                    userId = obj.optString("userId"),
                    username = obj.optString("username"),
                    nickname = obj.optString("nickname"),
                    avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
                    role = role,
                    isOnline = obj.optBoolean("isOnline", true),
                    isBanned = obj.optBoolean("isBanned", false)
                )
            )
        }
        return list
    }

    private fun buildWsUrl(baseUrl: String, token: String): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            baseUrl.startsWith("wss://") || baseUrl.startsWith("ws://") -> baseUrl
            else -> "wss://$baseUrl"
        }
        return "$wsBase/ws?token=$token"
    }

    companion object {
        private const val TAG = "SignalingWS"
    }
}
