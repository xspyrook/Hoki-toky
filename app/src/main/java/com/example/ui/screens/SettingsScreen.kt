package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AppSettings
import com.example.data.model.AudioOutputRoute
import com.example.data.model.AudioQualityPreset
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonBusy
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.WarningGold

@Composable
fun SettingsScreen(
    settings: AppSettings,
    user: UserProfile?,
    batteryUnrestricted: Boolean,
    onToggleSoundEffects: (Boolean) -> Unit,
    onSelectAudioQuality: (AudioQualityPreset) -> Unit,
    onToggleNoiseSuppression: (Boolean) -> Unit,
    onToggleEchoCancellation: (Boolean) -> Unit,
    onSelectOutputRoute: (AudioOutputRoute) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleVoiceRecordingConsent: (Boolean) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSaveEndpoints: (backendUrl: String, livekitUrl: String) -> Unit,
    onUpdateProfile: (nickname: String, avatarUri: Uri?) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> notificationsGranted = granted }
    )

    var nicknameDraft by remember(user?.nickname) { mutableStateOf(user?.nickname.orEmpty()) }
    var pickedAvatar by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> pickedAvatar = uri }
    )

    var backendUrl by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var livekitUrl by remember(settings.liveKitWsUrl) { mutableStateOf(settings.liveKitWsUrl) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(strings.settingsTitle, style = MaterialTheme.typography.headlineMedium)
            }

            // 1. Audio Effects & Opus Quality
            item {
                SettingsSectionCard(
                    title = strings.audioEffects,
                    icon = Icons.Default.GraphicEq
                ) {
                    SettingsSwitchRow(
                        label = strings.rogerBeepLabel,
                        checked = settings.soundEffectsEnabled,
                        onCheckedChange = onToggleSoundEffects,
                        tag = "switch_roger_beep"
                    )
                    SettingsSwitchRow(
                        label = strings.noiseSuppressionLabel,
                        checked = settings.noiseSuppression,
                        onCheckedChange = onToggleNoiseSuppression,
                        tag = "switch_noise_suppression"
                    )
                    SettingsSwitchRow(
                        label = strings.echoCancellationLabel,
                        checked = settings.echoCancellation,
                        onCheckedChange = onToggleEchoCancellation,
                        tag = "switch_echo_cancel"
                    )

                    Text(
                        text = strings.audioQualityLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioQualityPreset.entries.forEach { preset ->
                            val label = when (preset) {
                                AudioQualityPreset.LOW_DATA_16K -> strings.qualityLowData
                                AudioQualityPreset.BALANCED_32K -> strings.qualityBalanced
                                AudioQualityPreset.STUDIO_64K -> strings.qualityStudio
                            }
                            FilterChip(
                                selected = settings.audioQuality == preset,
                                onClick = { onSelectAudioQuality(preset) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Default Audio Output Route
            item {
                SettingsSectionCard(
                    title = strings.audioOutput,
                    icon = Icons.Default.SpeakerPhone
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AudioOutputRoute.SPEAKER to strings.outputSpeaker,
                            AudioOutputRoute.EARPIECE to strings.outputEarpiece,
                            AudioOutputRoute.BLUETOOTH to strings.outputBluetooth
                        ).forEach { (route, label) ->
                            FilterChip(
                                selected = settings.preferredOutputRoute == route,
                                onClick = { onSelectOutputRoute(route) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Background & Battery Optimization (Doze survival)
            item {
                SettingsSectionCard(
                    title = strings.backgroundBatterySection,
                    icon = Icons.Default.BatteryChargingFull
                ) {
                    Text(
                        text = if (batteryUnrestricted) strings.batteryUnrestricted else strings.batteryRestrictedWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (batteryUnrestricted) EmeraldOnline else WarningGold
                    )
                    if (!batteryUnrestricted) {
                        Button(
                            onClick = {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                } else {
                                    Intent(Settings.ACTION_SETTINGS)
                                }
                                runCatching { context.startActivity(intent) }.onFailure {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("battery_optimization_button")
                        ) {
                            Text(strings.allowBackgroundBattery)
                        }
                    }
                }
            }

            // 4. Notifications & Privacy
            item {
                SettingsSectionCard(
                    title = strings.notificationsSection,
                    icon = Icons.Default.Notifications
                ) {
                    SettingsSwitchRow(
                        label = strings.enableWakeAlerts,
                        checked = settings.notificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        tag = "switch_wake_alerts"
                    )
                    if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.grantNotificationPermission)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = TacticalCyan)
                                Text(strings.privacyRecordingOptIn, style = MaterialTheme.typography.labelLarge)
                            }
                            Text(
                                text = strings.privacyRecordingSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.voiceRecordingConsent,
                            onCheckedChange = onToggleVoiceRecordingConsent,
                            modifier = Modifier.testTag("switch_voice_recording_consent")
                        )
                    }
                }
            }

            // 5. Theme & Bilingual RTL Language
            item {
                SettingsSectionCard(
                    title = strings.appearanceLanguageSection,
                    icon = Icons.Default.Language
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ThemeMode.DARK to strings.themeDark,
                            ThemeMode.LIGHT to strings.themeLight,
                            ThemeMode.SYSTEM to strings.themeSystem
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSelectTheme(mode) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f).testTag("theme_chip_${mode.name}")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !settings.languageCode.startsWith("ar"),
                            onClick = { onSelectLanguage("en") },
                            label = { Text(strings.languageEnglish) },
                            modifier = Modifier.weight(1f).testTag("lang_chip_en")
                        )
                        FilterChip(
                            selected = settings.languageCode.startsWith("ar"),
                            onClick = { onSelectLanguage("ar") },
                            label = { Text(strings.languageArabic) },
                            modifier = Modifier.weight(1f).testTag("lang_chip_ar")
                        )
                    }
                }
            }

            // 6. Account Profile, Server Endpoints & Sign Out
            item {
                SettingsSectionCard(
                    title = strings.accountSessionSection,
                    icon = Icons.Default.Logout
                ) {
                    user?.let { u ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }) {
                                AvatarBadge(
                                    name = u.nickname,
                                    avatarUrl = pickedAvatar?.toString() ?: u.avatarUrl,
                                    isOnline = true,
                                    size = 52.dp
                                )
                            }
                            OutlinedTextField(
                                value = nicknameDraft,
                                onValueChange = { nicknameDraft = it },
                                label = { Text(strings.nicknameLabel) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            onClick = { onUpdateProfile(nicknameDraft, pickedAvatar) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.editProfile)
                        }
                    }

                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text(strings.backendUrlLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = livekitUrl,
                        onValueChange = { livekitUrl = it },
                        label = { Text(strings.livekitUrlLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { onSaveEndpoints(backendUrl, livekitUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.save)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonBusy),
                        modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.signOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = SignalAmber)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
