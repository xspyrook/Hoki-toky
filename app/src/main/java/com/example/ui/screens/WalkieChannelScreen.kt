package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ActiveRadioSession
import com.example.data.model.ActivityLogEntry
import com.example.data.model.AudioOutputRoute
import com.example.data.model.ConnectionStatus
import com.example.data.model.FloorState
import com.example.data.model.GroupDetail
import com.example.ui.components.AvatarBadge
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.components.PushToTalkButton
import com.example.ui.components.SignalQualityBars
import com.example.ui.components.VoiceVuMeter
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonBusy
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.WarningGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieChannelScreen(
    session: ActiveRadioSession,
    groupDetail: GroupDetail?,
    activityLogs: List<ActivityLogEntry>,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectOutputRoute: (AudioOutputRoute) -> Unit,
    onSwitchChannel: (GroupDetail, String) -> Unit,
    onLeaveChannel: () -> Unit,
    onReconnect: () -> Unit,
    onWakeMembers: () -> Unit,
    onOpenGroupConsole: (String) -> Unit,
    onRefreshActivity: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasMicPermission = granted }
    )

    var showChannelPicker by remember { mutableStateOf(false) }
    var showActivitySheet by remember { mutableStateOf(false) }
    var showRouteMenu by remember { mutableStateOf(false) }

    if (session.channelId.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(strings.noGroupsTitle, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = strings.noGroupsHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onGoHome, modifier = Modifier.testTag("go_home_from_radio")) {
                        Text(strings.navHome)
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Telemetry & Channel Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.groupName,
                                style = MaterialTheme.typography.labelMedium,
                                color = TacticalCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable(enabled = groupDetail != null) {
                                    showChannelPicker = true
                                }
                            ) {
                                Text(
                                    text = "# ${session.channelName}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SignalAmber.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${session.frequencyMhz} MHz",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (session.inviteCode.isNotBlank()) {
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(session.inviteCode)) },
                                    modifier = Modifier.testTag("copy_channel_group_code")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = strings.copyCode)
                                }
                            }
                            IconButton(
                                onClick = { onOpenGroupConsole(session.groupId) },
                                modifier = Modifier.testTag("open_group_console_button")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = strings.manageGroup)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConnectionStatusPill(
                            status = session.connectionStatus,
                            transport = session.networkTransport,
                            latencyMs = session.latencyMs
                        )
                        SignalQualityBars(quality = session.signalQuality)
                    }

                    // Audio Output & Listeners Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(strings.listenersCountFmt, session.listenersCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                FilledTonalButton(
                                    onClick = { showRouteMenu = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(38.dp).testTag("audio_output_button")
                                ) {
                                    val routeIcon = when (session.outputRoute) {
                                        AudioOutputRoute.BLUETOOTH -> Icons.Default.BluetoothAudio
                                        AudioOutputRoute.WIRED -> Icons.Default.Headset
                                        AudioOutputRoute.EARPIECE -> Icons.Default.PhoneInTalk
                                        AudioOutputRoute.SPEAKER -> Icons.Default.VolumeUp
                                    }
                                    val routeLabel = when (session.outputRoute) {
                                        AudioOutputRoute.BLUETOOTH -> strings.outputBluetooth
                                        AudioOutputRoute.WIRED -> strings.outputWired
                                        AudioOutputRoute.EARPIECE -> strings.outputEarpiece
                                        AudioOutputRoute.SPEAKER -> strings.outputSpeaker
                                    }
                                    Icon(routeIcon, contentDescription = strings.audioOutput, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(routeLabel, style = MaterialTheme.typography.labelSmall)
                                }

                                DropdownMenu(
                                    expanded = showRouteMenu,
                                    onDismissRequest = { showRouteMenu = false }
                                ) {
                                    AudioOutputRoute.entries.forEach { route ->
                                        val label = when (route) {
                                            AudioOutputRoute.SPEAKER -> strings.outputSpeaker
                                            AudioOutputRoute.EARPIECE -> strings.outputEarpiece
                                            AudioOutputRoute.BLUETOOTH -> strings.outputBluetooth
                                            AudioOutputRoute.WIRED -> strings.outputWired
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onSelectOutputRoute(route)
                                                showRouteMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = onWakeMembers,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(38.dp).testTag("wake_members_button")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.wakeGroupMembers, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Reconnect / Error Warning Banner (Never fakes connected state!)
            if (session.connectionStatus != ConnectionStatus.CONNECTED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = session.statusError ?: strings.serverNotConfigured,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onReconnect,
                            modifier = Modifier.testTag("reconnect_channel_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.verifyConnection)
                        }
                    }
                }
            }

            // 2. Active Speaker / Floor Control Banner
            val (floorText, floorColor) = when (session.floorState) {
                FloorState.TRANSMITTING -> strings.transmittingNow to SignalAmber
                FloorState.REQUESTING -> strings.requestingFloor to WarningGold
                FloorState.RECEIVING -> (session.currentSpeakerName ?: "Speaking") to TacticalCyan
                FloorState.BUSY -> String.format(strings.channelBusyByFmt, session.currentSpeakerName ?: "Operator") to CrimsonBusy
                FloorState.IDLE -> strings.frequencyClear to EmeraldOnline
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = floorColor.copy(alpha = 0.13f),
                border = androidx.compose.foundation.BorderStroke(1.dp, floorColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = floorText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = floorColor
                        )
                        session.lastSpeakerName?.let { last ->
                            Text(
                                text = String.format(strings.lastSpeakerFmt, last),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    VoiceVuMeter(audioLevel = session.audioLevel, floorState = session.floorState)
                }
            }

            // 3. Active Channel Member Roster
            if (session.activeMembers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(session.activeMembers, key = { it.userId }) { member ->
                        val isTalking = member.userId == session.currentSpeakerId
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(64.dp)
                        ) {
                            AvatarBadge(
                                name = member.nickname,
                                avatarUrl = member.avatarUrl,
                                isOnline = member.isOnline,
                                isSpeaking = isTalking,
                                size = 46.dp
                            )
                            Text(
                                text = member.nickname,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. Massive Center Push-to-Talk Transceiver Button
            if (!hasMicPermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningGold.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.micPermissionRequired,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text(strings.grantMicPermission)
                        }
                    }
                }
            }

            PushToTalkButton(
                floorState = session.floorState,
                connectionStatus = session.connectionStatus,
                canSpeak = session.canSpeak,
                hasMicPermission = hasMicPermission,
                onPressStart = onStartPtt,
                onPressEnd = onStopPtt,
                onRequestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )

            // 5. Bottom Control Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("mute_speaker_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (session.isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (session.isSpeakerMuted) strings.unmuteSpeaker else strings.muteSpeaker,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        onRefreshActivity()
                        showActivitySheet = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("activity_log_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.activityLog, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
                    onClick = onLeaveChannel,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBusy),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("leave_channel_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = strings.leaveChannel)
                }
            }

            if (groupDetail != null && groupDetail.channels.size > 1) {
                OutlinedButton(
                    onClick = { showChannelPicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("switch_channel_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${strings.switchChannel} (${groupDetail.channels.size})")
                }
            }
        }
    }

    if (showChannelPicker && groupDetail != null) {
        ModalBottomSheet(onDismissRequest = { showChannelPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.channelsSection, style = MaterialTheme.typography.titleLarge)
                groupDetail.channels.forEach { ch ->
                    val isActive = ch.id == session.channelId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSwitchChannel(groupDetail, ch.id)
                                showChannelPicker = false
                            },
                        color = if (isActive) SignalAmber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, SignalAmber) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("# ${ch.name}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${ch.frequencyMhz} MHz" + if (ch.moderatorsOnly) " • ${strings.moderatorsOnlyNoSpeak}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                Text(strings.activeChannel, style = MaterialTheme.typography.labelSmall, color = SignalAmber)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showActivitySheet) {
        ModalBottomSheet(onDismissRequest = { showActivitySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(strings.activityLog, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = strings.voiceRecordingDisabledNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activityLogs.isEmpty()) {
                    Text(
                        text = strings.noActivityYet,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activityLogs, key = { it.id }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${entry.nickname} (@${entry.username})",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "${entry.eventType}${entry.channelName?.let { " • #$it" } ?: ""}${entry.detail?.let { " • $it" } ?: ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = entry.createdAt.take(19).replace("T", " "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
