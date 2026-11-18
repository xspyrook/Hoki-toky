package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GlobalWalkieApp
import com.example.data.model.ActiveRadioSession
import com.example.data.model.ActivityLogEntry
import com.example.data.model.AppSettings
import com.example.data.model.AudioOutputRoute
import com.example.data.model.AudioQualityPreset
import com.example.data.model.FriendItem
import com.example.data.model.GroupDetail
import com.example.data.model.GroupSummary
import com.example.data.model.MemberRole
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class UiFeedback(
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class WalkieViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as GlobalWalkieApp).repository
    private val settingsStore = repo.settingsStore

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val currentUser: StateFlow<UserProfile?> = repo.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groups: StateFlow<List<GroupSummary>> = repo.groups
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val friends: StateFlow<List<FriendItem>> = repo.friends
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val session: StateFlow<ActiveRadioSession> = repo.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveRadioSession())

    val backendReachable: StateFlow<Boolean?> = repo.backendReachable
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedGroupDetail = MutableStateFlow<GroupDetail?>(null)
    val selectedGroupDetail: StateFlow<GroupDetail?> = _selectedGroupDetail.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val activityLogs: StateFlow<List<ActivityLogEntry>> = _activityLogs.asStateFlow()

    private val _feedback = MutableStateFlow(UiFeedback())
    val feedback: StateFlow<UiFeedback> = _feedback.asStateFlow()

    private val _healthStatusText = MutableStateFlow<String?>(null)
    val healthStatusText: StateFlow<String?> = _healthStatusText.asStateFlow()

    fun clearFeedback() {
        _feedback.value = UiFeedback()
    }

    fun testServerEndpoints(backendUrl: String, livekitUrl: String) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            settingsStore.saveServerEndpoints(backendUrl, livekitUrl)
            repo.checkServerHealth(backendUrl)
                .onSuccess { h ->
                    _healthStatusText.value = "OK (DB: ${h.postgres}, LiveKit: ${if (h.livekitConfigured) "ready" else "unconfigured"})"
                    _feedback.value = UiFeedback(info = "Server reachable (${h.version})")
                }
                .onFailure { e ->
                    _healthStatusText.value = e.message ?: "Unreachable"
                    _feedback.value = UiFeedback(error = e.message ?: "Server unreachable")
                }
        }
    }

    fun saveServerEndpoints(backendUrl: String, livekitUrl: String) {
        viewModelScope.launch {
            settingsStore.saveServerEndpoints(backendUrl, livekitUrl)
            _feedback.value = UiFeedback(info = "Endpoints saved")
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            repo.login(username, password)
                .onSuccess {
                    _feedback.value = UiFeedback()
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Login failed")
                }
        }
    }

    fun register(username: String, password: String, nickname: String, avatarUri: Uri?) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            val avatarDataUri = avatarUri?.let { encodeAvatarToDataUri(getApplication(), it) }
            repo.register(username, password, nickname, avatarDataUri)
                .onSuccess {
                    _feedback.value = UiFeedback()
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Registration failed")
                }
        }
    }

    fun updateProfile(nickname: String, avatarUri: Uri?) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            val avatarDataUri = avatarUri?.let { encodeAvatarToDataUri(getApplication(), it) }
            repo.updateProfile(nickname.trim().takeIf { it.isNotEmpty() }, avatarDataUri)
                .onSuccess {
                    _feedback.value = UiFeedback(info = "Profile updated")
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Update failed")
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut()
            _selectedGroupDetail.value = null
            _activityLogs.value = emptyList()
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            repo.refreshDashboard()
        }
    }

    fun addFriend(username: String) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            repo.addFriend(username)
                .onSuccess { f ->
                    _feedback.value = UiFeedback(info = "Added @${f.username}")
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Could not add friend")
                }
        }
    }

    fun pingFriend(friendId: String) {
        viewModelScope.launch {
            repo.pingFriend(friendId)
                .onSuccess {
                    _feedback.value = UiFeedback(info = "Wake alert sent")
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Wake alert failed")
                }
        }
    }

    fun createGroup(name: String, description: String, onCreated: (GroupSummary) -> Unit = {}) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            repo.createGroup(name, description)
                .onSuccess { g ->
                    _feedback.value = UiFeedback(info = "Code: ${g.inviteCode}")
                    onCreated(g)
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Failed to create group")
                }
        }
    }

    fun joinGroupByCode(code: String, onJoined: (GroupSummary) -> Unit = {}) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            repo.joinGroupByCode(code)
                .onSuccess { g ->
                    _feedback.value = UiFeedback(info = g.name)
                    onJoined(g)
                }
                .onFailure { e ->
                    _feedback.value = UiFeedback(error = e.message ?: "Invalid or revoked invite code")
                }
        }
    }

    fun loadGroupDetail(groupId: String) {
        viewModelScope.launch {
            repo.getGroupDetail(groupId)
                .onSuccess { detail -> _selectedGroupDetail.value = detail }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
            repo.getGroupActivity(groupId)
                .onSuccess { logs -> _activityLogs.value = logs }
        }
    }

    fun refreshActiveGroupActivity() {
        val gId = session.value.groupId
        if (gId.isNotEmpty()) {
            loadGroupDetail(gId)
        }
    }

    fun rotateInviteCode(groupId: String) {
        viewModelScope.launch {
            repo.rotateInviteCode(groupId)
                .onSuccess {
                    loadGroupDetail(groupId)
                    _feedback.value = UiFeedback(info = "Invite code rotated")
                }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun revokeInviteCode(groupId: String) {
        viewModelScope.launch {
            repo.revokeInviteCode(groupId)
                .onSuccess {
                    loadGroupDetail(groupId)
                    _feedback.value = UiFeedback(info = "Invite code revoked")
                }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun createChannel(
        groupId: String,
        name: String,
        frequencyMhz: String,
        moderatorsOnly: Boolean,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            repo.createChannel(groupId, name, frequencyMhz, moderatorsOnly, isDefault)
                .onSuccess {
                    loadGroupDetail(groupId)
                    _feedback.value = UiFeedback(info = "Channel created")
                }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun setMemberRole(groupId: String, userId: String, role: MemberRole) {
        viewModelScope.launch {
            repo.setMemberRole(groupId, userId, role)
                .onSuccess { loadGroupDetail(groupId) }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun kickMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repo.kickMember(groupId, userId)
                .onSuccess { loadGroupDetail(groupId) }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun banMember(groupId: String, userId: String) {
        viewModelScope.launch {
            repo.banMember(groupId, userId)
                .onSuccess { loadGroupDetail(groupId) }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    fun connectToGroupDefaultChannel(group: GroupSummary, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            _feedback.value = UiFeedback(isLoading = true)
            val detailRes = repo.getGroupDetail(group.id)
            detailRes.onSuccess { detail ->
                _selectedGroupDetail.value = detail
                val targetChannel = detail.channels.firstOrNull { it.isDefault } ?: detail.channels.firstOrNull()
                if (targetChannel == null) {
                    _feedback.value = UiFeedback(error = "No channels in group")
                    return@onSuccess
                }
                repo.joinChannel(
                    group = group,
                    channelId = targetChannel.id,
                    channelName = targetChannel.name,
                    frequencyMhz = targetChannel.frequencyMhz,
                    moderatorsOnly = targetChannel.moderatorsOnly
                )
                loadGroupDetail(group.id)
                _feedback.value = UiFeedback()
                onReady()
            }.onFailure { e ->
                _feedback.value = UiFeedback(error = e.message ?: "Failed to load group")
            }
        }
    }

    fun switchToChannel(group: GroupDetail, channelId: String) {
        val ch = group.channels.firstOrNull { it.id == channelId } ?: return
        val summary = GroupSummary(
            id = group.id,
            name = group.name,
            description = group.description,
            avatarUrl = group.avatarUrl,
            inviteCode = group.inviteCode,
            inviteRevoked = group.inviteRevoked,
            myRole = group.myRole
        )
        viewModelScope.launch {
            repo.joinChannel(
                group = summary,
                channelId = ch.id,
                channelName = ch.name,
                frequencyMhz = ch.frequencyMhz,
                moderatorsOnly = ch.moderatorsOnly
            )
            loadGroupDetail(group.id)
        }
    }

    fun leaveChannel() {
        viewModelScope.launch {
            repo.leaveActiveChannel()
        }
    }

    fun reconnectActiveChannel() {
        val current = session.value
        if (current.channelId.isEmpty() || current.groupId.isEmpty()) {
            repo.signalingClient.forceReconnect()
            return
        }
        val summary = GroupSummary(
            id = current.groupId,
            name = current.groupName,
            inviteCode = current.inviteCode,
            myRole = current.myRole
        )
        viewModelScope.launch {
            repo.joinChannel(
                group = summary,
                channelId = current.channelId,
                channelName = current.channelName,
                frequencyMhz = current.frequencyMhz,
                moderatorsOnly = current.moderatorsOnly
            )
        }
    }

    fun startPushToTalk() = repo.startPushToTalk()
    fun stopPushToTalk() = repo.stopPushToTalk()
    fun toggleSpeakerMute() = repo.toggleSpeakerMute()
    fun selectAudioOutput(route: AudioOutputRoute) = repo.selectAudioOutput(route)

    fun wakeChannelMembers() {
        val chId = session.value.channelId
        if (chId.isEmpty()) return
        viewModelScope.launch {
            repo.wakeChannelMembers(chId)
                .onSuccess { _feedback.value = UiFeedback(info = "Wake alert sent to group") }
                .onFailure { e -> _feedback.value = UiFeedback(error = e.message) }
        }
    }

    // Settings updates
    fun setSoundEffects(enabled: Boolean) = viewModelScope.launch { settingsStore.updateSoundEffects(enabled) }
    fun setAudioQuality(preset: AudioQualityPreset) = viewModelScope.launch { settingsStore.updateAudioQuality(preset) }
    fun setNoiseSuppression(enabled: Boolean) = viewModelScope.launch { settingsStore.updateNoiseSuppression(enabled) }
    fun setEchoCancellation(enabled: Boolean) = viewModelScope.launch { settingsStore.updateEchoCancellation(enabled) }
    fun setPreferredOutputRoute(route: AudioOutputRoute) = viewModelScope.launch {
        settingsStore.updatePreferredOutputRoute(route)
        repo.selectAudioOutput(route)
    }
    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.updateNotificationsEnabled(enabled) }
    fun setVoiceRecordingConsent(enabled: Boolean) = viewModelScope.launch { settingsStore.updateVoiceRecordingConsent(enabled) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.updateThemeMode(mode) }
    fun setLanguageCode(code: String) = viewModelScope.launch { settingsStore.updateLanguageCode(code) }

    fun isBatteryOptimizationIgnored(): Boolean {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private suspend fun encodeAvatarToDataUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bitmap == null) return@withContext null
            val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        }.getOrNull()
    }
}
