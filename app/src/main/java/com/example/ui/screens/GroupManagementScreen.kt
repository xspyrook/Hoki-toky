package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLogEntry
import com.example.data.model.GroupDetail
import com.example.data.model.MemberRole
import com.example.ui.components.AvatarBadge
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonBusy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan

@Composable
fun GroupManagementScreen(
    groupDetail: GroupDetail?,
    activityLogs: List<ActivityLogEntry>,
    currentUserId: String?,
    onBack: () -> Unit,
    onRotateInvite: (String) -> Unit,
    onRevokeInvite: (String) -> Unit,
    onCreateChannel: (groupId: String, name: String, freq: String, modOnly: Boolean, isDefault: Boolean) -> Unit,
    onTuneChannel: (GroupDetail, String) -> Unit,
    onSetRole: (groupId: String, userId: String, role: MemberRole) -> Unit,
    onKickMember: (groupId: String, userId: String) -> Unit,
    onBanMember: (groupId: String, userId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showCreateChannelDialog by remember { mutableStateOf(false) }

    if (groupDetail == null) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(strings.noGroupsTitle)
        }
        return
    }

    val canAdmin = groupDetail.myRole == MemberRole.OWNER || groupDetail.myRole == MemberRole.MODERATOR

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("group_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(groupDetail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (groupDetail.description.isNotBlank()) {
                            Text(
                                text = groupDetail.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Invite Code & Security Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(strings.inviteSecurity, style = MaterialTheme.typography.titleMedium)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (groupDetail.inviteRevoked) strings.inviteRevokedLabel else groupDetail.inviteCode,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (groupDetail.inviteRevoked) CrimsonBusy else TacticalCyan
                                )
                                if (!groupDetail.inviteRevoked) {
                                    Row {
                                        IconButton(
                                            onClick = { clipboard.setText(AnnotatedString(groupDetail.inviteCode)) },
                                            modifier = Modifier.testTag("copy_invite_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = strings.copyCode)
                                        }
                                        IconButton(
                                            onClick = {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "${groupDetail.name} — ${strings.inviteCodeLabel}: ${groupDetail.inviteCode}"
                                                    )
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, strings.shareCode))
                                            },
                                            modifier = Modifier.testTag("share_invite_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = strings.shareCode)
                                        }
                                    }
                                }
                            }
                        }

                        if (canAdmin) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onRotateInvite(groupDetail.id) },
                                    modifier = Modifier.weight(1f).testTag("rotate_invite_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.rotateInviteCode)
                                }
                                OutlinedButton(
                                    onClick = { onRevokeInvite(groupDetail.id) },
                                    modifier = Modifier.weight(1f).testTag("revoke_invite_button")
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = CrimsonBusy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.revokeInviteCode, color = CrimsonBusy)
                                }
                            }
                        }
                    }
                }
            }

            // Channels Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.channelsSection, style = MaterialTheme.typography.titleMedium)
                    if (canAdmin) {
                        TextButton(
                            onClick = { showCreateChannelDialog = true },
                            modifier = Modifier.testTag("new_channel_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.newChannel)
                        }
                    }
                }
            }

            items(groupDetail.channels, key = { it.id }) { ch ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTuneChannel(groupDetail, ch.id) }
                        .testTag("channel_card_${ch.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("# ${ch.name}", style = MaterialTheme.typography.titleMedium)
                                if (ch.moderatorsOnly) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = strings.moderatorsOnlyChannel,
                                        tint = SignalAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${ch.frequencyMhz} MHz" + if (ch.isDefault) " • ${strings.setAsDefaultChannel}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onTuneChannel(groupDetail, ch.id) }) {
                            Text(strings.navRadio)
                        }
                    }
                }
            }

            // Members Roster
            item {
                Text(
                    text = "${strings.membersSection} (${groupDetail.members.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(groupDetail.members, key = { it.userId }) { member ->
                var showActions by remember { mutableStateOf(false) }
                val roleLabel = when (member.role) {
                    MemberRole.OWNER -> strings.roleOwner
                    MemberRole.MODERATOR -> strings.roleModerator
                    MemberRole.MEMBER -> strings.roleMember
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AvatarBadge(
                            name = member.nickname,
                            avatarUrl = member.avatarUrl,
                            isOnline = member.isOnline,
                            size = 42.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.nickname, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "@${member.username} • $roleLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canAdmin && member.userId != currentUserId && member.role != MemberRole.OWNER) {
                            Box {
                                IconButton(onClick = { showActions = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Member actions")
                                }
                                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                                    if (groupDetail.myRole == MemberRole.OWNER) {
                                        if (member.role == MemberRole.MEMBER) {
                                            DropdownMenuItem(
                                                text = { Text(strings.makeModerator) },
                                                onClick = {
                                                    onSetRole(groupDetail.id, member.userId, MemberRole.MODERATOR)
                                                    showActions = false
                                                }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text(strings.removeModerator) },
                                                onClick = {
                                                    onSetRole(groupDetail.id, member.userId, MemberRole.MEMBER)
                                                    showActions = false
                                                }
                                            )
                                        }
                                    }
                                    DropdownMenuItem(
                                        text = { Text(strings.kickMember) },
                                        onClick = {
                                            onKickMember(groupDetail.id, member.userId)
                                            showActions = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strings.banMember, color = CrimsonBusy) },
                                        onClick = {
                                            onBanMember(groupDetail.id, member.userId)
                                            showActions = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity Log Preview
            item {
                Text(strings.activityLog, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = strings.voiceRecordingDisabledNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activityLogs.isEmpty()) {
                item {
                    Text(strings.noActivityYet, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(activityLogs.take(20), key = { it.id }) { log ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${log.nickname}: ${log.eventType}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = log.createdAt.take(19).replace("T", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateChannelDialog) {
        var chName by remember { mutableStateOf("") }
        var freq by remember { mutableStateOf("446.018") }
        var modOnly by remember { mutableStateOf(false) }
        var isDefault by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateChannelDialog = false },
            title = { Text(strings.newChannel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = chName,
                        onValueChange = { chName = it },
                        label = { Text(strings.channelNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_channel_name_input")
                    )
                    OutlinedTextField(
                        value = freq,
                        onValueChange = { freq = it },
                        label = { Text(strings.frequencyLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.moderatorsOnlyChannel, modifier = Modifier.weight(1f))
                        Switch(checked = modOnly, onCheckedChange = { modOnly = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.setAsDefaultChannel, modifier = Modifier.weight(1f))
                        Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (chName.isNotBlank()) {
                            onCreateChannel(groupDetail.id, chName, freq, modOnly, isDefault)
                            showCreateChannelDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_channel_button")
                ) {
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateChannelDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
