package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppSettings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.viewmodel.UiFeedback

@Composable
fun AuthScreen(
    settings: AppSettings,
    feedback: UiFeedback,
    healthStatusText: String?,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String, nickname: String, avatarUri: Uri?) -> Unit,
    onTestServer: (backendUrl: String, livekitUrl: String) -> Unit,
    onToggleLanguage: () -> Unit,
    onClearFeedback: () -> Unit
) {
    val strings = LocalAppStrings.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showServerConfig by remember { mutableStateOf(false) }
    var backendUrl by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var livekitUrl by remember(settings.liveKitWsUrl) { mutableStateOf(settings.liveKitWsUrl) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedAvatarUri = uri }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_radio_hero),
                    contentDescription = strings.appTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )

                // Language quick toggle
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onToggleLanguage() }
                        .testTag("auth_language_toggle"),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (settings.languageCode.startsWith("ar")) "English" else "العربية",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SignalAmber.copy(alpha = 0.18f))
                                .border(1.dp, SignalAmber, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = SignalAmber
                            )
                        }
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (isRegisterMode) strings.createAccountTitle else strings.signInTitle,
                            style = MaterialTheme.typography.titleLarge
                        )

                        if (isRegisterMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.5.dp, TacticalCyan, CircleShape)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("pick_avatar_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedAvatarUri != null) {
                                        AsyncImage(
                                            model = selectedAvatarUri,
                                            contentDescription = strings.optionalAvatarLabel,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = strings.pickPhoto,
                                            tint = TacticalCyan
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.optionalAvatarLabel,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = strings.noPhoneRequiredNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearFeedback()
                            },
                            label = { Text(strings.usernameLabel) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input")
                        )

                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text(strings.nicknameLabel) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("nickname_input")
                            )
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                onClearFeedback()
                            },
                            label = { Text(strings.passwordLabel) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input")
                        )

                        feedback.error?.let { err ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isRegisterMode) {
                                    onRegister(
                                        username,
                                        password,
                                        nickname.ifBlank { username },
                                        selectedAvatarUri
                                    )
                                } else {
                                    onLogin(username, password)
                                }
                            },
                            enabled = !feedback.isLoading && username.isNotBlank() && password.length >= 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (feedback.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isRegisterMode) strings.registerButton else strings.signInButton,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                isRegisterMode = !isRegisterMode
                                onClearFeedback()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("toggle_auth_mode_button")
                        ) {
                            Text(if (isRegisterMode) strings.switchToLogin else strings.switchToRegister)
                        }
                    }
                }

                // Expandable Server Endpoint Config (for production HTTPS/WSS or local Docker)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showServerConfig = !showServerConfig }
                                .padding(vertical = 4.dp)
                                .testTag("server_config_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = TacticalCyan)
                                Text(
                                    text = strings.serverEndpointSettings,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                imageVector = if (showServerConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showServerConfig) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = backendUrl,
                                    onValueChange = { backendUrl = it },
                                    label = { Text(strings.backendUrlLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("backend_url_input")
                                )
                                OutlinedTextField(
                                    value = livekitUrl,
                                    onValueChange = { livekitUrl = it },
                                    label = { Text(strings.livekitUrlLabel) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("livekit_url_input")
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onTestServer(backendUrl, livekitUrl) },
                                        modifier = Modifier.testTag("ping_backend_button")
                                    ) {
                                        Text(strings.testServerConnection)
                                    }
                                    healthStatusText?.let { status ->
                                        Text(
                                            text = status,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (status.startsWith("OK")) EmeraldOnline else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
