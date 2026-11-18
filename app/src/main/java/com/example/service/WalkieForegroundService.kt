package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.GlobalWalkieApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.ActiveRadioSession
import com.example.data.model.ConnectionStatus
import com.example.data.model.FloorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WalkieForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null

    private var currentChannelName: String = "Channel"
    private var currentGroupName: String = "Global Walkie Talkie"
    private var bluetoothPttActive = false

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                val repo = (application as GlobalWalkieApp).repository
                repo.toggleSpeakerMute()
            }
            ACTION_LEAVE -> {
                val repo = (application as GlobalWalkieApp).repository
                serviceScope.launch {
                    repo.leaveActiveChannel()
                    stopForegroundAndSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                currentChannelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: currentChannelName
                currentGroupName = intent?.getStringExtra(EXTRA_GROUP_NAME) ?: currentGroupName
                promoteToForeground(buildNotification((application as GlobalWalkieApp).repository.session.value))
                observeSessionUpdates()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        releaseLocks()
        super.onDestroy()
    }

    private fun promoteToForeground(notification: Notification) {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val serviceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
        } else {
            0
        }

        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceTypes)
    }

    private fun observeSessionUpdates() {
        if (stateJob != null) return
        val repo = (application as GlobalWalkieApp).repository
        stateJob = serviceScope.launch {
            repo.session.collect { session ->
                if (session.channelId.isEmpty()) return@collect
                currentChannelName = session.channelName.ifBlank { currentChannelName }
                currentGroupName = session.groupName.ifBlank { currentGroupName }
                updateMediaSessionState(session)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, buildNotification(session))
            }
        }
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "GlobalWalkiePttSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                        val code = keyEvent.keyCode
                        if (code == KeyEvent.KEYCODE_HEADSETHOOK ||
                            code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                            code == KeyEvent.KEYCODE_MEDIA_PLAY ||
                            code == KeyEvent.KEYCODE_MEDIA_PAUSE
                        ) {
                            toggleBluetoothPtt()
                            return true
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                override fun onPlay() {
                    toggleBluetoothPtt()
                }

                override fun onPause() {
                    val repo = (application as GlobalWalkieApp).repository
                    if (bluetoothPttActive) {
                        bluetoothPttActive = false
                        repo.stopPushToTalk()
                    }
                }
            })
            isActive = true
        }
        mediaSession = session
    }

    private fun toggleBluetoothPtt() {
        val repo = (application as GlobalWalkieApp).repository
        bluetoothPttActive = !bluetoothPttActive
        if (bluetoothPttActive) {
            repo.startPushToTalk()
        } else {
            repo.stopPushToTalk()
        }
    }

    private fun updateMediaSessionState(session: ActiveRadioSession) {
        val playbackState = when {
            session.floorState == FloorState.TRANSMITTING -> PlaybackStateCompat.STATE_PLAYING
            session.connectionStatus == ConnectionStatus.CONNECTED -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_CONNECTING
        }
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE
                )
                .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, session.channelName.ifBlank { currentChannelName })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, session.currentSpeakerName ?: session.groupName)
                .build()
        )
    }

    private fun buildNotification(session: ActiveRadioSession): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, WalkieForegroundService::class.java).apply { action = ACTION_TOGGLE_MUTE }
        val mutePi = PendingIntent.getService(
            this,
            1,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val leaveIntent = Intent(this, WalkieForegroundService::class.java).apply { action = ACTION_LEAVE }
        val leavePi = PendingIntent.getService(
            this,
            2,
            leaveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelLabel = session.channelName.ifBlank { currentChannelName }
        val groupLabel = session.groupName.ifBlank { currentGroupName }
        val statusLine = when {
            session.connectionStatus == ConnectionStatus.RECONNECTING ||
                session.connectionStatus == ConnectionStatus.CONNECTING ->
                getString(R.string.notification_reconnecting, channelLabel)
            session.floorState == FloorState.TRANSMITTING ->
                getString(R.string.notification_transmitting, channelLabel)
            !session.currentSpeakerName.isNullOrBlank() ->
                getString(R.string.notification_speaking, session.currentSpeakerName)
            else ->
                getString(R.string.notification_listening, channelLabel)
        }

        val muteTitle = if (session.isSpeakerMuted) {
            getString(R.string.action_unmute)
        } else {
            getString(R.string.action_mute)
        }

        val builder = NotificationCompat.Builder(this, GlobalWalkieApp.CHANNEL_RADIO_FGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$groupLabel • $channelLabel")
            .setContentText(statusLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .addAction(0, muteTitle, mutePi)
            .addAction(0, getString(R.string.action_leave), leavePi)

        mediaSession?.sessionToken?.let { token ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1)
            )
        }

        return builder.build()
    }

    private fun acquireLocks() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlobalWalkie::RadioWakeLock").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6h safety ceiling
        }

        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm.createWifiLock(lockMode, "GlobalWalkie::RadioWifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
    }

    private fun stopForegroundAndSelf() {
        stateJob?.cancel()
        stateJob = null
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val NOTIFICATION_ID = 4102
        const val ACTION_START = "com.example.action.START_RADIO"
        const val ACTION_STOP = "com.example.action.STOP_RADIO"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
        const val ACTION_LEAVE = "com.example.action.LEAVE_RADIO"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_GROUP_NAME = "extra_group_name"
    }
}
