package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class MemberRole {
    @Json(name = "owner") OWNER,
    @Json(name = "moderator") MODERATOR,
    @Json(name = "member") MEMBER
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    ERROR
}

enum class NetworkTransport {
    NONE,
    WIFI,
    CELLULAR,
    OTHER
}

enum class SignalQuality {
    UNKNOWN,
    EXCELLENT,
    GOOD,
    POOR,
    LOST
}

enum class FloorState {
    IDLE,
    REQUESTING,
    TRANSMITTING,
    RECEIVING,
    BUSY
}

enum class AudioOutputRoute {
    SPEAKER,
    EARPIECE,
    BLUETOOTH,
    WIRED
}

enum class AudioQualityPreset(val kbps: Int) {
    LOW_DATA_16K(16),
    BALANCED_32K(32),
    STUDIO_64K(64)
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: UserProfile
)

@JsonClass(generateAdapter = true)
data class FriendItem(
    val id: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeenAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChannelItem(
    val id: String,
    val groupId: String,
    val name: String,
    val frequencyMhz: String = "446.006",
    val isDefault: Boolean = false,
    val moderatorsOnly: Boolean = false,
    val activeListeners: Int = 0
)

@JsonClass(generateAdapter = true)
data class GroupMember(
    val userId: String,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val role: MemberRole = MemberRole.MEMBER,
    val isOnline: Boolean = false,
    val isBanned: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GroupSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val inviteCode: String = "",
    val inviteRevoked: Boolean = false,
    val myRole: MemberRole = MemberRole.MEMBER,
    val onlineCount: Int = 0,
    val memberCount: Int = 0,
    val defaultChannelId: String? = null,
    val defaultChannelName: String? = null
)

@JsonClass(generateAdapter = true)
data class GroupDetail(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val inviteCode: String = "",
    val inviteRevoked: Boolean = false,
    val myRole: MemberRole = MemberRole.MEMBER,
    val channels: List<ChannelItem> = emptyList(),
    val members: List<GroupMember> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ActivityLogEntry(
    val id: String,
    val groupId: String,
    val channelId: String? = null,
    val channelName: String? = null,
    val username: String,
    val nickname: String,
    val eventType: String,
    val detail: String? = null,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class LiveKitTokenResponse(
    val livekitUrl: String,
    val roomName: String,
    val token: String,
    val canPublish: Boolean,
    val channelName: String,
    val groupName: String
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val postgres: String,
    val livekitConfigured: Boolean,
    val version: String
)

// Request DTOs
@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String,
    val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val fcmToken: String? = null
)

@JsonClass(generateAdapter = true)
data class AddFriendRequest(
    val username: String
)

@JsonClass(generateAdapter = true)
data class CreateGroupRequest(
    val name: String,
    val description: String = "",
    val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class JoinGroupRequest(
    val inviteCode: String
)

@JsonClass(generateAdapter = true)
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateChannelRequest(
    val name: String,
    val frequencyMhz: String = "446.012",
    val moderatorsOnly: Boolean = false,
    val isDefault: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UpdateChannelRequest(
    val moderatorsOnly: Boolean? = null,
    val isDefault: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class UpdateRoleRequest(
    val role: String
)

@JsonClass(generateAdapter = true)
data class ChannelTokenRequest(
    val voiceRecordingConsent: Boolean = false
)

@JsonClass(generateAdapter = true)
data class InviteCodeResponse(
    val inviteCode: String,
    val inviteRevoked: Boolean
)

@JsonClass(generateAdapter = true)
data class GenericOkResponse(
    val ok: Boolean,
    val message: String? = null
)

data class AppSettings(
    val backendBaseUrl: String = DEFAULT_BACKEND_URL,
    val liveKitWsUrl: String = DEFAULT_LIVEKIT_URL,
    val soundEffectsEnabled: Boolean = true,
    val audioQuality: AudioQualityPreset = AudioQualityPreset.BALANCED_32K,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val preferredOutputRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val notificationsEnabled: Boolean = true,
    val voiceRecordingConsent: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val languageCode: String = "en"
) {
    companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8080"
        const val DEFAULT_LIVEKIT_URL = "ws://10.0.2.2:7880"
    }
}

data class ActiveRadioSession(
    val groupId: String = "",
    val groupName: String = "",
    val inviteCode: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val frequencyMhz: String = "446.006",
    val moderatorsOnly: Boolean = false,
    val myRole: MemberRole = MemberRole.MEMBER,
    val canSpeak: Boolean = true,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val latencyMs: Int? = null,
    val networkTransport: NetworkTransport = NetworkTransport.NONE,
    val floorState: FloorState = FloorState.IDLE,
    val currentSpeakerId: String? = null,
    val currentSpeakerName: String? = null,
    val lastSpeakerName: String? = null,
    val listenersCount: Int = 0,
    val isSpeakerMuted: Boolean = false,
    val audioLevel: Float = 0f,
    val outputRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val availableRoutes: Set<AudioOutputRoute> = setOf(AudioOutputRoute.SPEAKER, AudioOutputRoute.EARPIECE),
    val activeMembers: List<GroupMember> = emptyList(),
    val statusError: String? = null
)
