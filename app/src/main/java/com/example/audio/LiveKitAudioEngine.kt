package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.model.AppSettings
import com.example.data.model.AudioOutputRoute
import com.example.data.model.ConnectionStatus
import com.example.data.model.SignalQuality
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.AudioTrackPublishDefaults
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveKitAudioEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioHandler: AudioSwitchHandler? = null
    private var room: Room? = null
    private var eventsJob: Job? = null

    private val _roomConnectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val roomConnectionStatus: StateFlow<ConnectionStatus> = _roomConnectionStatus.asStateFlow()

    private val _signalQuality = MutableStateFlow(SignalQuality.UNKNOWN)
    val signalQuality: StateFlow<SignalQuality> = _signalQuality.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _availableRoutes = MutableStateFlow(setOf(AudioOutputRoute.SPEAKER, AudioOutputRoute.EARPIECE))
    val availableRoutes: StateFlow<Set<AudioOutputRoute>> = _availableRoutes.asStateFlow()

    private val _selectedRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val selectedRoute: StateFlow<AudioOutputRoute> = _selectedRoute.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun connectToChannel(
        livekitUrl: String,
        token: String,
        settings: AppSettings
    ): Result<Unit> {
        disconnect()
        _lastError.value = null
        _roomConnectionStatus.value = ConnectionStatus.CONNECTING

        return runCatching {
            val switchHandler = AudioSwitchHandler(context).apply {
                preferredDeviceList = when (settings.preferredOutputRoute) {
                    AudioOutputRoute.BLUETOOTH -> listOf(
                        AudioDevice.BluetoothHeadset::class.java,
                        AudioDevice.WiredHeadset::class.java,
                        AudioDevice.Speakerphone::class.java,
                        AudioDevice.Earpiece::class.java
                    )
                    AudioOutputRoute.EARPIECE -> listOf(
                        AudioDevice.Earpiece::class.java,
                        AudioDevice.BluetoothHeadset::class.java,
                        AudioDevice.WiredHeadset::class.java,
                        AudioDevice.Speakerphone::class.java
                    )
                    else -> listOf(
                        AudioDevice.BluetoothHeadset::class.java,
                        AudioDevice.WiredHeadset::class.java,
                        AudioDevice.Speakerphone::class.java,
                        AudioDevice.Earpiece::class.java
                    )
                }
            }
            audioHandler = switchHandler

            val roomOptions = RoomOptions(
                audioTrackCaptureDefaults = LocalAudioTrackOptions(
                    noiseSuppression = settings.noiseSuppression,
                    echoCancellation = settings.echoCancellation,
                    autoGainControl = true
                ),
                audioTrackPublishDefaults = AudioTrackPublishDefaults(
                    dtx = true,
                    red = true,
                    audioBitrate = settings.audioQuality.kbps * 1000
                )
            )

            val createdRoom = LiveKit.create(
                appContext = context.applicationContext,
                options = roomOptions,
                overrides = LiveKitOverrides(
                    audioOptions = AudioOptions(
                        audioHandler = switchHandler
                    )
                )
            )
            room = createdRoom
            observeRoomEvents(createdRoom)

            createdRoom.connect(
                url = livekitUrl,
                token = token,
                options = ConnectOptions(autoSubscribe = true)
            )
            // Keep mic muted until Push-To-Talk is pressed
            createdRoom.localParticipant.setMicrophoneEnabled(false)
            _roomConnectionStatus.value = ConnectionStatus.CONNECTED
            refreshAudioDevices()
            selectOutputRoute(settings.preferredOutputRoute)
        }.onFailure { err ->
            Log.e(TAG, "LiveKit connect failed: ${err.message}", err)
            _lastError.value = err.message ?: "LiveKit connection failed"
            _roomConnectionStatus.value = ConnectionStatus.ERROR
        }
    }

    suspend fun setTransmitting(enabled: Boolean): Boolean {
        val activeRoom = room ?: return false
        return runCatching {
            activeRoom.localParticipant.setMicrophoneEnabled(enabled)
            true
        }.getOrElse { err ->
            Log.e(TAG, "Failed toggling mic: ${err.message}", err)
            _lastError.value = err.message
            false
        }
    }

    fun setSpeakerMuted(muted: Boolean) {
        _isSpeakerMuted.value = muted
        val activeRoom = room ?: return
        activeRoom.remoteParticipants.values.forEach { participant ->
            participant.audioTrackPublications.forEach { (_, track) ->
                (track as? RemoteAudioTrack)?.setVolume(if (muted) 0.0 else 1.0)
            }
        }
    }

    fun selectOutputRoute(route: AudioOutputRoute) {
        _selectedRoute.value = route
        val handler = audioHandler ?: return
        val devices = handler.availableAudioDevices
        val targetDevice = when (route) {
            AudioOutputRoute.BLUETOOTH -> devices.firstOrNull { it is AudioDevice.BluetoothHeadset }
            AudioOutputRoute.WIRED -> devices.firstOrNull { it is AudioDevice.WiredHeadset }
            AudioOutputRoute.SPEAKER -> devices.firstOrNull { it is AudioDevice.Speakerphone }
            AudioOutputRoute.EARPIECE -> devices.firstOrNull { it is AudioDevice.Earpiece }
        }
        if (targetDevice != null) {
            handler.selectDevice(targetDevice)
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = (route == AudioOutputRoute.SPEAKER)
        }
    }

    fun refreshAudioDevices() {
        val handler = audioHandler ?: return
        val routes = mutableSetOf<AudioOutputRoute>()
        handler.availableAudioDevices.forEach { dev ->
            when (dev) {
                is AudioDevice.BluetoothHeadset -> routes.add(AudioOutputRoute.BLUETOOTH)
                is AudioDevice.WiredHeadset -> routes.add(AudioOutputRoute.WIRED)
                is AudioDevice.Speakerphone -> routes.add(AudioOutputRoute.SPEAKER)
                is AudioDevice.Earpiece -> routes.add(AudioOutputRoute.EARPIECE)
            }
        }
        if (routes.isNotEmpty()) {
            _availableRoutes.value = routes
        }
    }

    fun disconnect() {
        eventsJob?.cancel()
        eventsJob = null
        runCatching {
            room?.disconnect()
            room?.release()
        }
        room = null
        audioHandler = null
        _audioLevel.value = 0f
        _signalQuality.value = SignalQuality.UNKNOWN
        _roomConnectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun observeRoomEvents(activeRoom: Room) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            activeRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        _roomConnectionStatus.value = ConnectionStatus.CONNECTED
                        refreshAudioDevices()
                    }
                    is RoomEvent.Reconnecting -> {
                        _roomConnectionStatus.value = ConnectionStatus.RECONNECTING
                    }
                    is RoomEvent.Reconnected -> {
                        _roomConnectionStatus.value = ConnectionStatus.CONNECTED
                    }
                    is RoomEvent.Disconnected -> {
                        _roomConnectionStatus.value = ConnectionStatus.DISCONNECTED
                        _audioLevel.value = 0f
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (track is RemoteAudioTrack && _isSpeakerMuted.value) {
                            track.setVolume(0.0)
                        }
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val topLevel = event.speakers.maxOfOrNull { it.audioLevel } ?: 0f
                        _audioLevel.value = topLevel.coerceIn(0f, 1f)
                    }
                    is RoomEvent.ConnectionQualityChanged -> {
                        if (event.participant == activeRoom.localParticipant) {
                            _signalQuality.value = when (event.quality) {
                                ConnectionQuality.EXCELLENT -> SignalQuality.EXCELLENT
                                ConnectionQuality.GOOD -> SignalQuality.GOOD
                                ConnectionQuality.POOR -> SignalQuality.POOR
                                ConnectionQuality.LOST -> SignalQuality.LOST
                                ConnectionQuality.UNKNOWN -> SignalQuality.UNKNOWN
                            }
                        }
                    }
                    is RoomEvent.FailedToConnect -> {
                        _lastError.value = event.error.message
                        _roomConnectionStatus.value = ConnectionStatus.ERROR
                    }
                    else -> Unit
                }
            }
        }
    }

    companion object {
        private const val TAG = "LiveKitAudioEngine"
    }
}
