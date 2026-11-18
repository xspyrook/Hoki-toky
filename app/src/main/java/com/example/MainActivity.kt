package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GroupManagementScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalkieChannelScreen
import com.example.ui.theme.GlobalWalkieTheme
import com.example.ui.viewmodel.WalkieViewModel

private enum class AppTab {
    HOME,
    RADIO,
    GROUPS,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: WalkieViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val currentUser by vm.currentUser.collectAsStateWithLifecycle()

            GlobalWalkieTheme(
                themeMode = settings.themeMode,
                languageCode = settings.languageCode
            ) {
                if (currentUser == null) {
                    val feedback by vm.feedback.collectAsStateWithLifecycle()
                    val healthText by vm.healthStatusText.collectAsStateWithLifecycle()
                    AuthScreen(
                        settings = settings,
                        feedback = feedback,
                        healthStatusText = healthText,
                        onLogin = vm::login,
                        onRegister = vm::register,
                        onTestServer = vm::testServerEndpoints,
                        onToggleLanguage = {
                            val next = if (settings.languageCode.startsWith("ar")) "en" else "ar"
                            vm.setLanguageCode(next)
                        },
                        onClearFeedback = vm::clearFeedback
                    )
                } else {
                    MainTransceiverShell(vm = vm)
                }
            }
        }
    }
}

@Composable
private fun MainTransceiverShell(vm: WalkieViewModel) {
    val strings = LocalAppStrings.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val currentUser by vm.currentUser.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val groupDetail by vm.selectedGroupDetail.collectAsStateWithLifecycle()
    val activityLogs by vm.activityLogs.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback.info, feedback.error) {
        val msg = feedback.info ?: feedback.error
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearFeedback()
        }
    }

    if (currentTab != AppTab.HOME) {
        BackHandler {
            currentTab = AppTab.HOME
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val (label, icon) = tabInfo(tab, selected, strings)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val (label, icon) = tabInfo(tab, selected, strings)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        AppTab.HOME -> HomeScreen(
                            currentUser = currentUser,
                            session = session,
                            groups = groups,
                            friends = friends,
                            onRefresh = vm::refreshDashboard,
                            onCreateGroup = { name, desc ->
                                vm.createGroup(name, desc) { created ->
                                    vm.connectToGroupDefaultChannel(created) {
                                        currentTab = AppTab.RADIO
                                    }
                                }
                            },
                            onJoinGroupByCode = { code ->
                                vm.joinGroupByCode(code) { joined ->
                                    vm.connectToGroupDefaultChannel(joined) {
                                        currentTab = AppTab.RADIO
                                    }
                                }
                            },
                            onConnectGroup = { group ->
                                vm.connectToGroupDefaultChannel(group) {
                                    currentTab = AppTab.RADIO
                                }
                            },
                            onManageGroup = { groupId ->
                                vm.loadGroupDetail(groupId)
                                currentTab = AppTab.GROUPS
                            },
                            onAddFriend = vm::addFriend,
                            onPingFriend = vm::pingFriend,
                            onOpenActiveRadio = { currentTab = AppTab.RADIO }
                        )
                        AppTab.RADIO -> WalkieChannelScreen(
                            session = session,
                            groupDetail = groupDetail,
                            activityLogs = activityLogs,
                            onStartPtt = vm::startPushToTalk,
                            onStopPtt = vm::stopPushToTalk,
                            onToggleMute = vm::toggleSpeakerMute,
                            onSelectOutputRoute = vm::selectAudioOutput,
                            onSwitchChannel = vm::switchToChannel,
                            onLeaveChannel = {
                                vm.leaveChannel()
                                currentTab = AppTab.HOME
                            },
                            onReconnect = vm::reconnectActiveChannel,
                            onWakeMembers = vm::wakeChannelMembers,
                            onOpenGroupConsole = { gId ->
                                vm.loadGroupDetail(gId)
                                currentTab = AppTab.GROUPS
                            },
                            onRefreshActivity = vm::refreshActiveGroupActivity,
                            onGoHome = { currentTab = AppTab.HOME }
                        )
                        AppTab.GROUPS -> {
                            LaunchedEffect(groups) {
                                if (groupDetail == null && groups.isNotEmpty()) {
                                    vm.loadGroupDetail(groups.first().id)
                                }
                            }
                            GroupManagementScreen(
                                groupDetail = groupDetail,
                                activityLogs = activityLogs,
                                currentUserId = currentUser?.id,
                                onBack = { currentTab = AppTab.HOME },
                                onRotateInvite = vm::rotateInviteCode,
                                onRevokeInvite = vm::revokeInviteCode,
                                onCreateChannel = vm::createChannel,
                                onTuneChannel = { detail, chId ->
                                    vm.switchToChannel(detail, chId)
                                    currentTab = AppTab.RADIO
                                },
                                onSetRole = vm::setMemberRole,
                                onKickMember = vm::kickMember,
                                onBanMember = vm::banMember
                            )
                        }
                        AppTab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            user = currentUser,
                            batteryUnrestricted = vm.isBatteryOptimizationIgnored(),
                            onToggleSoundEffects = vm::setSoundEffects,
                            onSelectAudioQuality = vm::setAudioQuality,
                            onToggleNoiseSuppression = vm::setNoiseSuppression,
                            onToggleEchoCancellation = vm::setEchoCancellation,
                            onSelectOutputRoute = vm::setPreferredOutputRoute,
                            onToggleNotifications = vm::setNotificationsEnabled,
                            onToggleVoiceRecordingConsent = vm::setVoiceRecordingConsent,
                            onSelectTheme = vm::setThemeMode,
                            onSelectLanguage = vm::setLanguageCode,
                            onSaveEndpoints = vm::saveServerEndpoints,
                            onUpdateProfile = vm::updateProfile,
                            onSignOut = vm::signOut
                        )
                    }
                }
            }
        }
    }
}

private fun tabInfo(
    tab: AppTab,
    selected: Boolean,
    strings: com.example.ui.i18n.AppStrings
) = when (tab) {
    AppTab.HOME -> strings.navHome to if (selected) Icons.Filled.Home else Icons.Outlined.Home
    AppTab.RADIO -> strings.navRadio to if (selected) Icons.Filled.Radio else Icons.Outlined.Radio
    AppTab.GROUPS -> strings.navGroups to if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
    AppTab.SETTINGS -> strings.navSettings to if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
