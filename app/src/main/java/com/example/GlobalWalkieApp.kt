package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.example.data.prefs.SettingsStore
import com.example.data.repository.WalkieRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GlobalWalkieApp : Application() {

    lateinit var repository: WalkieRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val settingsStore = SettingsStore(this)
        repository = WalkieRepository(this, settingsStore)

        // Safely initialize FCM only when google-services.json has been configured by the operator
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        appScope.launch {
                            repository.registerFcmToken(token)
                        }
                    }
                }
            }
        }.onFailure { err ->
            Log.i(TAG, "FCM unavailable (configure google-services.json in Firebase Console for push wake): ${err.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return

            val fgsChannel = NotificationChannel(
                CHANNEL_RADIO_FGS,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_description)
                setShowBadge(false)
            }

            val wakeChannel = NotificationChannel(
                CHANNEL_WAKE_ALERTS,
                getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alert_channel_description)
                enableVibration(true)
            }

            nm.createNotificationChannel(fgsChannel)
            nm.createNotificationChannel(wakeChannel)
        }
    }

    companion object {
        private const val TAG = "GlobalWalkieApp"
        const val CHANNEL_RADIO_FGS = "walkie_radio_foreground"
        const val CHANNEL_WAKE_ALERTS = "walkie_wake_alerts"
    }
}
