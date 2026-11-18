package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.audio.LiveKitAudioEngine
import com.example.audio.RadioTonePlayer
import com.example.data.model.ActiveRadioSession
import com.example.data.model.ActivityLogEntry
import com.example.data.model.AddFriendRequest
import com.example.data.model.AppSettings
import com.example.data.model.AudioOutputRoute
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelTokenRequest
import com.example.data.model.ConnectionStatus
import com.example.data.model.CreateChannelRequest
import com.example.data.model.CreateGroupRequest
import com.example.data.model.FloorState
import com.example.data.model.FriendItem
import com.example.data.model.GroupDetail
import com.example.data.model.GroupSummary
import com.example.data.model.HealthResponse
import com.example.data.model.JoinGroupRequest
import com.example.data.model.LoginRequest
import com.example.data.model.MemberRole
import com.example.data.model.NetworkTransport
import com.example.data.model.RegisterRequest
import com.example.data.model.SignalQuality
import com.example.data.model.UpdateChannelRequest
import com.example.data.model.UpdateGroupRequest
import com.example.data.model.UpdateProfileRequest
import com.example.data.model.UpdateRoleRequest
import com.example.data.model.UserProfile
import com.example.data.network.ApiClientFactory
import com.example.data.network.NetworkMonitor
import com.example.data.network.SignalingEvent
import com.example.data.network.SignalingWebSocketClient
import com.example.data.network.WalkieApiService
import com.example.data.prefs.SettingsStore
import com.example.service.WalkieForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response

class WalkieRepository(
    private val context: Context,
    val settingsStore: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private val okHttpClient = ApiClientFactory.createOkHttpClient { cachedToken }
    val networkMonitor = NetworkMonitor(context)
    val signalingClient = SignalingWebSocketClient(okHttpClient)
    val audioEngine = LiveKitAudioEngine(context)
    val tonePlayer = RadioTonePlayer()

    private val _session = MutableStateFlow(ActiveRadioSession())
    val session: StateFlow<ActiveRadioSession> = _session.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _groups = MutableStateFlow<List<GroupSummary>>(emptyList())
    val groups: StateFlow<List<GroupSummary>> = _groups.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendItem>>(emptyList())
    val friends: StateFlow<List<FriendItem>> = _friends.asStateFlow()

    private val _backendReachable = MutableStateFlow<Boolean?>(null)
    val backendReachable: StateFlow<Boolean?> = _backendReachable.asStateFlow()

    @Volatile
    private var pendingPttHold = false

    init {
        networkMonitor.start()

        scope.launch {
            settingsStore.settingsFlow.collect { settings ->
                cachedSettings = settings
            }
        }

        scope.launch {
            combine(settingsStore.authTokenFlow, settingsStore.currentUserFlow) { token, user ->
                token to user
            }.collect { (token, user) ->
                cachedToken = token
                _currentUser.value = user
                if (!token.isNullOrBlank()) {
                    signalingClient.connect(cachedSettings.backendBaseUrl, token)
                    refreshDashboard()
                } else {
                    signalingClient.disconnect()
                }
            }
        }

        // Handle network interface changes (Wi-Fi <-> 5G/4G recovery)
        scope.launch {
            networkMonitor.networkChangedEvents.collect { transport ->
                _session.update { it.copy(networkTransport = transport) }
                if (transport != NetworkTransport.NONE && !cachedToken.isNullOrBlank()) {
                    signalingClient.forceReconnect()
                }
            }
        }

        // Combine WebSocket + LiveKit + AudioEngine state into verified session state
        scope.launch {
            combine(
                signalingClient.connectionState,
                audioEngine.roomConnectionStatus,
                audioEngine.signalQuality,
                signalingClient.latencyMs,
                networkMonitor.transport
            ) { wsState, lkState, quality, rtt, transport ->
                val effectiveConnection = when {
                    transport == NetworkTransport.NONE -> ConnectionStatus.DISCONNECTED
                    _session.value.channelId.isEmpty() -> wsState
                    wsState == ConnectionStatus.CONNECTED && lkState == ConnectionStatus.CONNECTED -> ConnectionStatus.CONNECTED
                    wsState == ConnectionStatus.ERROR || lkState == ConnectionStatus.ERROR -> ConnectionStatus.ERROR
                    wsState == ConnectionStatus.RECONNECTING || lkState == ConnectionStatus.RECONNECTING -> ConnectionStatus.RECONNECTING
                    wsState == ConnectionStatus.CONNECTING || lkState == ConnectionStatus.CONNECTING -> ConnectionStatus.CONNECTING
                    else -> ConnectionStatus.DISCONNECTED
                }
                Triple(effectiveConnection, quality, Pair(rtt, transport))
            }.collect { (status, quality, net) ->
                _session.update {
                    it.copy(
                        connectionStatus = status,
                        signalQuality = quality,
                        latencyMs = net.first,
                        networkTransport = net.second
                    )
                }
            }
        }

        scope.launch {
            audioEngine.audioLevel.collect { lvl ->
                _session.update { it.copy(audioLevel = lvl) }
            }
        }

        scope.launch {
            combine(
                audioEngine.selectedRoute,
                audioEngine.availableRoutes,
                audioEngine.isSpeakerMuted
            ) { selected, available, muted ->
                Triple(selected, available, muted)
            }.collect { (selected, available, muted) ->
                _session.update {
                    it.copy(
                        outputRoute = selected,
                        availableRoutes = available,
                        isSpeakerMuted = muted
                    )
                }
            }
        }

        scope.launch {
            signalingClient.events.collect { event ->
                handleSignalingEvent(event)
            }
        }
    }

    private fun api(): WalkieApiService =
        ApiClientFactory.createService(cachedSettings.backendBaseUrl, okHttpClient)

    suspend fun checkServerHealth(customUrl: String? = null): Result<HealthResponse> {
        val url = customUrl?.trim()?.trimEnd('/') ?: cachedSettings.backendBaseUrl
        return runCatching {
            val resp = ApiClientFactory.createService(url, okHttpClient).healthCheck()
            val body = unwrapResponse(resp)
            _backendReachable.value = true
            body
        }.onFailure {
            _backendReachable.value = false
        }
    }

    suspend fun register(
        username: String,
        password: String,
        nickname: String,
        avatarUrl: String?
    ): Result<UserProfile> = runCatching {
        val resp = api().register(RegisterRequest(username.trim(), password, nickname.trim(), avatarUrl))
        val auth = unwrapResponse(resp)
        cachedToken = auth.token
        settingsStore.saveSession(auth.token, auth.user)
        syncFcmTokenIfPresent()
        auth.user
    }

    suspend fun login(username: String, password: String): Result<UserProfile> = runCatching {
        val resp = api().login(LoginRequest(username.trim(), password))
        val auth = unwrapResponse(resp)
        cachedToken = auth.token
        settingsStore.saveSession(auth.token, auth.user)
        syncFcmTokenIfPresent()
        auth.user
    }

    suspend fun signOut() {
        leaveActiveChannel()
        runCatching { api().logout() }
        cachedToken = null
        settingsStore.clearSession()
        _groups.value = emptyList()
        _friends.value = emptyList()
        signalingClient.disconnect()
    }

    suspend fun updateProfile(nickname: String?, avatarUrl: String?): Result<UserProfile> = runCatching {
        val resp = api().updateProfile(UpdateProfileRequest(nickname = nickname, avatarUrl = avatarUrl))
        val updated = unwrapResponse(resp)
        settingsStore.updateUser(updated)
        updated
    }

    suspend fun registerFcmToken(fcmToken: String) {
        settingsStore.saveFcmToken(fcmToken)
        if (!cachedToken.isNullOrBlank()) {
            runCatching { api().updateProfile(UpdateProfileRequest(fcmToken = fcmToken)) }
        }
    }

    private suspend fun syncFcmTokenIfPresent() {
        val fcm = settingsStore.getFcmToken()
        if (!fcm.isNullOrBlank()) {
            runCatching { api().updateProfile(UpdateProfileRequest(fcmToken = fcm)) }
        }
    }

    suspend fun refreshDashboard() {
        if (cachedToken.isNullOrBlank()) return
        runCatching {
            val gResp = api().getGroups()
            if (gResp.isSuccessful) {
                _groups.value = gResp.body().orEmpty()
                _backendReachable.value = true
            }
        }
        runCatching {
            val fResp = api().getFriends()
            if (fResp.isSuccessful) {
                _friends.value = fResp.body().orEmpty()
            }
        }
    }

    suspend fun addFriend(username: String): Result<FriendItem> = runCatching {
        val friend = unwrapResponse(api().addFriend(AddFriendRequest(username.trim())))
        refreshDashboard()
        friend
    }

    suspend fun removeFriend(friendId: String): Result<Unit> = runCatching {
        unwrapResponse(api().removeFriend(friendId))
        refreshDashboard()
    }

    suspend fun pingFriend(friendId: String): Result<Unit> = runCatching {
        unwrapResponse(api().pingFriend(friendId))
    }

    suspend fun createGroup(name: String, description: String): Result<GroupSummary> = runCatching {
        val created = unwrapResponse(api().createGroup(CreateGroupRequest(name.trim(), description.trim())))
        refreshDashboard()
        created
    }

    suspend fun joinGroupByCode(code: String): Result<GroupSummary> = runCatching {
        val joined = unwrapResponse(api().joinGroup(JoinGroupRequest(code.trim().uppercase())))
        refreshDashboard()
        joined
    }

    suspend fun getGroupDetail(groupId: String): Result<GroupDetail> = runCatching {
        unwrapResponse(api().getGroupDetail(groupId))
    }

    suspend fun updateGroup(groupId: String, name: String?, description: String?): Result<GroupDetail> = runCatching {
        unwrapResponse(api().updateGroup(groupId, UpdateGroupRequest(name, description)))
    }

    suspend fun rotateInviteCode(groupId: String): Result<String> = runCatching {
        val res = unwrapResponse(api().rotateInviteCode(groupId))
        refreshDashboard()
        res.inviteCode
    }

    suspend fun revokeInviteCode(groupId: String): Result<Unit> = runCatching {
        unwrapResponse(api().revokeInviteCode(groupId))
        refreshDashboard()
    }

    suspend fun setMemberRole(groupId: String, userId: String, role: MemberRole): Result<Unit> = runCatching {
        val roleWire = when (role) {
            MemberRole.OWNER -> "owner"
            MemberRole.MODERATOR -> "moderator"
            MemberRole.MEMBER -> "member"
        }
        unwrapResponse(api().updateMemberRole(groupId, userId, UpdateRoleRequest(roleWire)))
    }

    suspend fun kickMember(groupId: String, userId: String): Result<Unit> = runCatching {
        unwrapResponse(api().kickMember(groupId, userId))
    }

    suspend fun banMember(groupId: String, userId: String): Result<Unit> = runCatching {
        unwrapResponse(api().banMember(groupId, userId))
    }

    suspend fun createChannel(
        groupId: String,
        name: String,
        frequencyMhz: String,
        moderatorsOnly: Boolean,
        isDefault: Boolean
    ): Result<ChannelItem> = runCatching {
        unwrapResponse(
            api().createChannel(
                groupId,
                CreateChannelRequest(name.trim(), frequencyMhz.trim(), moderatorsOnly, isDefault)
            )
        )
    }

    suspend fun updateChannel(
        groupId: String,
        channelId: String,
        moderatorsOnly: Boolean?,
        isDefault: Boolean?
    ): Result<ChannelItem> = runCatching {
        unwrapResponse(api().updateChannel(groupId, channelId, UpdateChannelRequest(moderatorsOnly, isDefault)))
    }

    suspend fun getGroupActivity(groupId: String): Result<List<ActivityLogEntry>> = runCatching {
        unwrapResponse(api().getGroupActivity(groupId))
    }

    suspend fun wakeChannelMembers(channelId: String): Result<Unit> = runCatching {
        unwrapResponse(api().wakeChannelMembers(channelId))
    }

    suspend fun joinChannel(
        group: GroupSummary,
        channelId: String,
        channelName: String,
        frequencyMhz: String,
        moderatorsOnly: Boolean
    ): Result<Unit> {
        val previousChannel = _session.value.channelId
        if (previousChannel.isNotEmpty() && previousChannel != channelId) {
            signalingClient.leaveChannel(previousChannel)
        }

        _session.update {
            it.copy(
                groupId = group.id,
                groupName = group.name,
                inviteCode = group.inviteCode,
                channelId = channelId,
                channelName = channelName,
                frequencyMhz = frequencyMhz,
                moderatorsOnly = moderatorsOnly,
                myRole = group.myRole,
                connectionStatus = ConnectionStatus.CONNECTING,
                floorState = FloorState.IDLE,
                statusError = null
            )
        }

        return runCatching {
            val tokenResp = unwrapResponse(
                api().getChannelToken(
                    channelId,
                    ChannelTokenRequest(voiceRecordingConsent = cachedSettings.voiceRecordingConsent)
                )
            )
            _session.update {
                it.copy(
                    canSpeak = tokenResp.canPublish,
                    channelName = tokenResp.channelName,
                    groupName = tokenResp.groupName
                )
            }

            val lkUrl = tokenResp.livekitUrl.ifBlank { cachedSettings.liveKitWsUrl }
            val lkResult = audioEngine.connectToChannel(lkUrl, tokenResp.token, cachedSettings)
            lkResult.getOrThrow()

            signalingClient.joinChannel(channelId)
            startForegroundService(tokenResp.channelName, tokenResp.groupName)
        }.onFailure { err ->
            Log.e(TAG, "Failed joining channel: ${err.message}", err)
            _session.update {
                it.copy(
                    connectionStatus = ConnectionStatus.ERROR,
                    statusError = err.message ?: "Failed to connect to channel"
                )
            }
        }
    }

    suspend fun leaveActiveChannel() {
        val chId = _session.value.channelId
        pendingPttHold = false
        if (chId.isNotEmpty()) {
            signalingClient.releaseFloor(chId)
            signalingClient.leaveChannel(chId)
        }
        audioEngine.disconnect()
        stopForegroundService()
        _session.update {
            ActiveRadioSession(
                networkTransport = networkMonitor.transport.value
            )
        }
    }

    fun startPushToTalk() {
        val current = _session.value
        if (current.channelId.isEmpty() || current.connectionStatus != ConnectionStatus.CONNECTED) {
            return
        }
        if (!current.canSpeak) {
            if (cachedSettings.soundEffectsEnabled) tonePlayer.playBusyAlert()
            return
        }
        if (current.floorState == FloorState.BUSY || current.floorState == FloorState.RECEIVING) {
            if (cachedSettings.soundEffectsEnabled) tonePlayer.playBusyAlert()
            return
        }
        pendingPttHold = true
        _session.update { it.copy(floorState = FloorState.REQUESTING) }
        val sent = signalingClient.requestFloor(current.channelId)
        if (!sent) {
            pendingPttHold = false
            _session.update { it.copy(floorState = FloorState.IDLE) }
        }
    }

    fun stopPushToTalk() {
        pendingPttHold = false
        val current = _session.value
        val wasTransmitting = current.floorState == FloorState.TRANSMITTING ||
            current.floorState == FloorState.REQUESTING

        if (current.channelId.isNotEmpty()) {
            signalingClient.releaseFloor(current.channelId)
        }
        scope.launch {
            audioEngine.setTransmitting(false)
        }
        if (wasTransmitting) {
            if (cachedSettings.soundEffectsEnabled && current.floorState == FloorState.TRANSMITTING) {
                tonePlayer.playRogerBeep()
            }
            _session.update {
                it.copy(
                    floorState = FloorState.IDLE,
                    currentSpeakerId = null,
                    currentSpeakerName = null
                )
            }
        }
    }

    fun toggleSpeakerMute() {
        val next = !audioEngine.isSpeakerMuted.value
        audioEngine.setSpeakerMuted(next)
    }

    fun selectAudioOutput(route: AudioOutputRoute) {
        audioEngine.selectOutputRoute(route)
        scope.launch {
            settingsStore.updatePreferredOutputRoute(route)
        }
    }

    private suspend fun handleSignalingEvent(event: SignalingEvent) {
        val meId = _currentUser.value?.id
        when (event) {
            is SignalingEvent.ChannelJoined -> {
                if (event.channelId != _session.value.channelId) return
                val speakerIsOther = event.currentSpeakerId != null && event.currentSpeakerId != meId
                _session.update {
                    it.copy(
                        listenersCount = event.listenersCount,
                        currentSpeakerId = event.currentSpeakerId,
                        currentSpeakerName = event.currentSpeakerName,
                        lastSpeakerName = event.lastSpeakerName ?: it.lastSpeakerName,
                        activeMembers = event.members,
                        floorState = if (speakerIsOther) FloorState.RECEIVING else FloorState.IDLE
                    )
                }
            }
            is SignalingEvent.PresenceUpdate -> {
                if (event.channelId != _session.value.channelId) return
                _session.update {
                    it.copy(
                        listenersCount = event.listenersCount,
                        activeMembers = event.members
                    )
                }
            }
            is SignalingEvent.FloorGranted -> {
                if (event.channelId != _session.value.channelId) return
                if (event.speakerId == meId) {
                    if (!pendingPttHold) {
                        signalingClient.releaseFloor(event.channelId)
                        return
                    }
                    val micReady = audioEngine.setTransmitting(true)
                    if (micReady) {
                        if (cachedSettings.soundEffectsEnabled) tonePlayer.playTxStartChirp()
                        _session.update {
                            it.copy(
                                floorState = FloorState.TRANSMITTING,
                                currentSpeakerId = event.speakerId,
                                currentSpeakerName = event.speakerName
                            )
                        }
                    } else {
                        pendingPttHold = false
                        signalingClient.releaseFloor(event.channelId)
                        _session.update { it.copy(floorState = FloorState.IDLE) }
                    }
                } else {
                    _session.update {
                        it.copy(
                            floorState = FloorState.RECEIVING,
                            currentSpeakerId = event.speakerId,
                            currentSpeakerName = event.speakerName
                        )
                    }
                }
            }
            is SignalingEvent.FloorBusy -> {
                if (event.channelId != _session.value.channelId) return
                pendingPttHold = false
                if (cachedSettings.soundEffectsEnabled) tonePlayer.playBusyAlert()
                _session.update {
                    it.copy(
                        floorState = FloorState.BUSY,
                        currentSpeakerId = event.holderId,
                        currentSpeakerName = event.holderName
                    )
                }
            }
            is SignalingEvent.FloorReleased -> {
                if (event.channelId != _session.value.channelId) return
                if (_session.value.floorState == FloorState.TRANSMITTING) {
                    audioEngine.setTransmitting(false)
                }
                _session.update {
                    it.copy(
                        floorState = FloorState.IDLE,
                        currentSpeakerId = null,
                        currentSpeakerName = null,
                        lastSpeakerName = event.lastSpeakerName ?: it.lastSpeakerName
                    )
                }
            }
            is SignalingEvent.FloorDenied -> {
                if (event.channelId != _session.value.channelId) return
                pendingPttHold = false
                if (cachedSettings.soundEffectsEnabled) tonePlayer.playBusyAlert()
                _session.update {
                    it.copy(
                        floorState = FloorState.IDLE,
                        statusError = event.reason
                    )
                }
            }
            is SignalingEvent.ErrorNotice -> {
                _session.update { it.copy(statusError = event.message) }
            }
            is SignalingEvent.Authenticated -> Unit
        }
    }

    private fun startForegroundService(channelName: String, groupName: String) {
        val intent = Intent(context, WalkieForegroundService::class.java).apply {
            action = WalkieForegroundService.ACTION_START
            putExtra(WalkieForegroundService.EXTRA_CHANNEL_NAME, channelName)
            putExtra(WalkieForegroundService.EXTRA_GROUP_NAME, groupName)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(context, WalkieForegroundService::class.java).apply {
            action = WalkieForegroundService.ACTION_STOP
        }
        runCatching { context.startService(intent) }
    }

    private fun <T> unwrapResponse(response: Response<T>): T {
        if (response.isSuccessful) {
            return response.body() ?: throw IllegalStateException("Empty server response")
        }
        val errText = response.errorBody()?.string()
        val parsedMsg = runCatching {
            errText?.let { JSONObject(it).optString("error") }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        throw IllegalStateException(parsedMsg ?: "HTTP ${response.code()}: ${response.message()}")
    }

    companion object {
        private const val TAG = "WalkieRepository"
    }
}
