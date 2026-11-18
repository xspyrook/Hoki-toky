package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ActiveRadioSession
import com.example.data.model.FriendItem
import com.example.data.model.GroupSummary
import com.example.data.model.UserProfile
import com.example.ui.components.AvatarBadge
import com.example.ui.components.ConnectionStatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan

@Composable
fun HomeScreen(
    currentUser: UserProfile?,
    session: ActiveRadioSession,
    groups: List<GroupSummary>,
    friends: List<FriendItem>,
    onRefresh: () -> Unit,
    onCreateGroup: (name: String, description: String) -> Unit,
    onJoinGroupByCode: (code: String) -> Unit,
    onConnectGroup: (GroupSummary) -> Unit,
    onManageGroup: (String) -> Unit,
    onAddFriend: (username: String) -> Unit,
    onPingFriend: (friendId: String) -> Unit,
    onOpenActiveRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tactical Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_radio_hero),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = SignalAmber,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = strings.appTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    currentUser?.let { u ->
                                        Text(
                                            text = "${u.nickname} (@${u.username})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TacticalCyan
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onRefresh,
                                    modifier = Modifier.testTag("home_refresh_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                currentUser?.let { u ->
                                    AvatarBadge(
                                        name = u.nickname,
                                        avatarUrl = u.avatarUrl,
                                        isOnline = session.connectionStatus == com.example.data.model.ConnectionStatus.CONNECTED,
                                        size = 40.dp
                                    )
                                }
                            }
                        }

                        ConnectionStatusPill(
                            status = session.connectionStatus,
                            transport = session.networkTransport,
                            latencyMs = session.latencyMs
                        )
                    }
                }
            }

            // Active Channel Quick-Resume Banner
            if (session.channelId.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onOpenActiveRadio() }
                            .testTag("active_channel_banner"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SignalAmber.copy(alpha = 0.14f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SignalAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${strings.activeChannel}: ${session.channelName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = SignalAmber
                                )
                                Text(
                                    text = "${session.groupName} • ${session.frequencyMhz} MHz",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onOpenActiveRadio,
                                colors = ButtonDefaults.buttonColors(containerColor = SignalAmber)
                            ) {
                                Text(strings.navRadio)
                            }
                        }
                    }
                }
            }

            // Create / Join Action Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("create_group_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.createGroup, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = { showJoinGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("join_group_code_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.joinByCode, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Online Friends Strip
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.onlineFriends,
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = { showAddFriendDialog = true },
                            modifier = Modifier.testTag("add_friend_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.addFriend)
                        }
                    }

                    if (friends.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(strings.noFriendsTitle, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = strings.noFriendsHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(friends, key = { it.id }) { friend ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.width(156.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AvatarBadge(
                                                name = friend.nickname,
                                                avatarUrl = friend.avatarUrl,
                                                isOnline = friend.isOnline,
                                                size = 38.dp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = friend.nickname,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "@${friend.username}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { onPingFriend(friend.id) },
                                            modifier = Modifier.fillMaxWidth().height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.pingFriend, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Radio Groups Header
            item {
                Text(
                    text = strings.recentGroups,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (groups.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = TacticalCyan,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(strings.noGroupsTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = strings.noGroupsHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onConnectGroup(group) }
                            .testTag("group_card_${group.id}"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AvatarBadge(
                                name = group.name,
                                avatarUrl = group.avatarUrl,
                                isOnline = group.onlineCount > 0,
                                size = 48.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (group.description.isNotBlank()) {
                                    Text(
                                        text = group.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldOnline.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = String.format(strings.onlineCountFmt, group.onlineCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldOnline,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!group.inviteRevoked && group.inviteCode.isNotBlank()) {
                                        Text(
                                            text = group.inviteCode,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TacticalCyan
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onManageGroup(group.id) },
                                modifier = Modifier.testTag("manage_group_button_${group.id}")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = strings.manageGroup)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text(strings.createGroup) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(strings.groupNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_group_name_input")
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text(strings.groupDescLabel) },
                        modifier = Modifier.fillMaxWidth().testTag("new_group_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateGroup(name, desc)
                            showCreateGroupDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_group_button")
                ) {
                    Text(strings.create)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showJoinGroupDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinGroupDialog = false },
            title = { Text(strings.joinByCode) },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text(strings.inviteCodeLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("join_code_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (code.isNotBlank()) {
                            onJoinGroupByCode(code)
                            showJoinGroupDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_join_group_button")
                ) {
                    Text(strings.join)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showAddFriendDialog) {
        var friendUsername by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            title = { Text(strings.addFriend) },
            text = {
                OutlinedTextField(
                    value = friendUsername,
                    onValueChange = { friendUsername = it },
                    label = { Text(strings.friendUsernameHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_friend_username_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (friendUsername.isNotBlank()) {
                            onAddFriend(friendUsername)
                            showAddFriendDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_friend_button")
                ) {
                    Text(strings.addFriend)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
