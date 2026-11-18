package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.AudioOutputRoute
import com.example.data.model.AudioQualityPreset
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "global_walkie_prefs")

class SettingsStore(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val userAdapter = moshi.adapter(UserProfile::class.java)

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val LIVEKIT_URL = stringPreferencesKey("livekit_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_JSON = stringPreferencesKey("user_json")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val SOUND_EFFECTS = booleanPreferencesKey("sound_effects")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val OUTPUT_ROUTE = stringPreferencesKey("output_route")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val VOICE_RECORD_CONSENT = booleanPreferencesKey("voice_record_consent")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            backendBaseUrl = prefs[Keys.BACKEND_URL]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_BACKEND_URL,
            liveKitWsUrl = prefs[Keys.LIVEKIT_URL]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_LIVEKIT_URL,
            soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true,
            audioQuality = prefs[Keys.AUDIO_QUALITY]?.let {
                runCatching { AudioQualityPreset.valueOf(it) }.getOrNull()
            } ?: AudioQualityPreset.BALANCED_32K,
            noiseSuppression = prefs[Keys.NOISE_SUPPRESSION] ?: true,
            echoCancellation = prefs[Keys.ECHO_CANCELLATION] ?: true,
            preferredOutputRoute = prefs[Keys.OUTPUT_ROUTE]?.let {
                runCatching { AudioOutputRoute.valueOf(it) }.getOrNull()
            } ?: AudioOutputRoute.SPEAKER,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            voiceRecordingConsent = prefs[Keys.VOICE_RECORD_CONSENT] ?: false,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en"
        )
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_TOKEN] }

    val currentUserFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_JSON]?.let { json -> runCatching { userAdapter.fromJson(json) }.getOrNull() }
    }

    suspend fun getAuthToken(): String? = authTokenFlow.first()

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun saveSession(token: String, user: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTH_TOKEN] = token
            prefs[Keys.USER_JSON] = userAdapter.toJson(user)
        }
    }

    suspend fun updateUser(user: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_JSON] = userAdapter.toJson(user)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.AUTH_TOKEN)
            prefs.remove(Keys.USER_JSON)
        }
    }

    suspend fun saveServerEndpoints(backendUrl: String, liveKitUrl: String) {
        val cleanBackend = backendUrl.trim().trimEnd('/')
        val cleanLiveKit = liveKitUrl.trim().trimEnd('/')
        context.dataStore.edit { prefs ->
            if (cleanBackend.isNotEmpty()) prefs[Keys.BACKEND_URL] = cleanBackend
            if (cleanLiveKit.isNotEmpty()) prefs[Keys.LIVEKIT_URL] = cleanLiveKit
        }
    }

    suspend fun updateSoundEffects(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_EFFECTS] = enabled }
    }

    suspend fun updateAudioQuality(preset: AudioQualityPreset) {
        context.dataStore.edit { it[Keys.AUDIO_QUALITY] = preset.name }
    }

    suspend fun updateNoiseSuppression(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOISE_SUPPRESSION] = enabled }
    }

    suspend fun updateEchoCancellation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ECHO_CANCELLATION] = enabled }
    }

    suspend fun updatePreferredOutputRoute(route: AudioOutputRoute) {
        context.dataStore.edit { it[Keys.OUTPUT_ROUTE] = route.name }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun updateVoiceRecordingConsent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_RECORD_CONSENT] = enabled }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }

    suspend fun getFcmToken(): String? = context.dataStore.data.map { it[Keys.FCM_TOKEN] }.first()
}
