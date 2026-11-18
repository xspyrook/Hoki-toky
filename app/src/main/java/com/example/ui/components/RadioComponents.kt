package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ConnectionStatus
import com.example.data.model.FloorState
import com.example.data.model.NetworkTransport
import com.example.data.model.SignalQuality
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonBusy
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.WarningGold

@Composable
fun ConnectionStatusPill(
    status: ConnectionStatus,
    transport: NetworkTransport,
    latencyMs: Int?,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (label, dotColor) = when {
        transport == NetworkTransport.NONE -> strings.networkOffline to CrimsonBusy
        status == ConnectionStatus.CONNECTED -> {
            val rtt = latencyMs?.let { " • ${it}ms" } ?: ""
            "${strings.statusConnected}$rtt" to EmeraldOnline
        }
        status == ConnectionStatus.CONNECTING -> strings.statusConnecting to WarningGold
        status == ConnectionStatus.RECONNECTING -> strings.statusReconnecting to WarningGold
        status == ConnectionStatus.ERROR -> strings.statusError to CrimsonBusy
        else -> strings.statusDisconnected to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val transportLabel = when (transport) {
        NetworkTransport.WIFI -> "Wi-Fi"
        NetworkTransport.CELLULAR -> "4G/5G"
        NetworkTransport.OTHER -> "NET"
        NetworkTransport.NONE -> "OFF"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = dotColor.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = dotColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = transportLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SignalQualityBars(
    quality: SignalQuality,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (activeBars, color, text) = when (quality) {
        SignalQuality.EXCELLENT -> Triple(4, EmeraldOnline, strings.qualityExcellent)
        SignalQuality.GOOD -> Triple(3, TacticalCyan, strings.qualityGood)
        SignalQuality.POOR -> Triple(2, WarningGold, strings.qualityPoor)
        SignalQuality.LOST -> Triple(1, CrimsonBusy, strings.qualityLost)
        SignalQuality.UNKNOWN -> Triple(0, MaterialTheme.colorScheme.onSurfaceVariant, "—")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 1..4) {
                val barHeight = (5 + i * 3).dp
                val filled = i <= activeBars
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (filled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun AvatarBadge(
    name: String,
    avatarUrl: String?,
    isOnline: Boolean,
    isSpeaking: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        isSpeaking -> TacticalCyan
        isOnline -> EmeraldOnline
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isSpeaking) 2.5.dp else 1.5.dp,
                    color = ringColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isSpeaking) TacticalCyan else if (isOnline) EmeraldOnline else Color.Gray)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

@Composable
fun VoiceVuMeter(
    audioLevel: Float,
    floorState: FloorState,
    modifier: Modifier = Modifier
) {
    val activeColor = when (floorState) {
        FloorState.TRANSMITTING -> SignalAmber
        FloorState.RECEIVING -> TacticalCyan
        FloorState.BUSY -> CrimsonBusy
        else -> MaterialTheme.colorScheme.outline
    }
    val smoothLevel by animateFloatAsState(
        targetValue = if (floorState == FloorState.TRANSMITTING || floorState == FloorState.RECEIVING) {
            audioLevel.coerceAtLeast(0.15f)
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 90),
        label = "vuLevel"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val segments = 20
        val gap = 4.dp.toPx()
        val segWidth = (size.width - gap * (segments - 1)) / segments
        val litCount = (smoothLevel * segments).toInt()
        for (i in 0 until segments) {
            val x = i * (segWidth + gap)
            val isLit = i < litCount
            val segColor = when {
                !isLit -> activeColor.copy(alpha = 0.14f)
                i >= segments - 3 -> CrimsonBusy
                i >= segments - 7 -> WarningGold
                else -> activeColor
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(x, 2.dp.toPx()),
                size = Size(segWidth, size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
fun PushToTalkButton(
    floorState: FloorState,
    connectionStatus: ConnectionStatus,
    canSpeak: Boolean,
    hasMicPermission: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val isTransmitting = floorState == FloorState.TRANSMITTING
    val isRequesting = floorState == FloorState.REQUESTING
    val isBusy = floorState == FloorState.BUSY || floorState == FloorState.RECEIVING

    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            !isConnected -> MaterialTheme.colorScheme.surfaceVariant
            !canSpeak -> MaterialTheme.colorScheme.surfaceVariant
            isTransmitting -> SignalAmber
            isRequesting -> WarningGold
            isBusy -> CrimsonBusy.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(),
        label = "pttCoreColor"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            !isConnected -> MaterialTheme.colorScheme.outline
            !canSpeak -> MaterialTheme.colorScheme.outline
            isTransmitting -> SignalAmber
            isRequesting -> WarningGold
            isBusy -> CrimsonBusy
            else -> TacticalCyan
        },
        animationSpec = spring(),
        label = "pttRingColor"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isTransmitting || isRequesting) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "pttScale"
    )

    val labelText = when {
        !isConnected -> strings.connectFirstToTalk
        !hasMicPermission -> strings.grantMicPermission
        !canSpeak -> strings.moderatorsOnlyNoSpeak
        isTransmitting -> strings.releaseToStop
        isRequesting -> strings.requestingFloor
        isBusy -> strings.holdToTalk
        else -> strings.holdToTalk
    }

    Box(
        modifier = modifier.size(236.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isTransmitting || floorState == FloorState.RECEIVING) {
            val waveColor = if (isTransmitting) SignalAmber else TacticalCyan
            Box(
                modifier = Modifier
                    .size(194.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .border(2.5.dp, waveColor.copy(alpha = pulseAlpha), CircleShape)
            )
        }

        // Precision metallic outer bezel + interactive press/release target
        Box(
            modifier = Modifier
                .size(196.dp)
                .scale(buttonScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor,
                            coreColor.copy(alpha = 0.82f)
                        )
                    )
                )
                .border(4.dp, ringColor, CircleShape)
                .testTag("ptt_button")
                .pointerInput(isConnected, canSpeak, hasMicPermission) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (!hasMicPermission) {
                            waitForUpOrCancellation()
                            onRequestMicPermission()
                            return@awaitEachGesture
                        }
                        if (isConnected && canSpeak) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPressStart()
                            waitForUpOrCancellation()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPressEnd()
                        } else {
                            waitForUpOrCancellation()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Subtle inner reticle ring
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.3f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                val icon = when {
                    !isConnected -> Icons.Default.WifiOff
                    !canSpeak -> Icons.Default.Lock
                    !hasMicPermission -> Icons.Default.MicOff
                    else -> Icons.Default.Mic
                }
                val contentTint = if (isTransmitting || isRequesting) {
                    Color.White
                } else {
                    ringColor
                }
                Icon(
                    imageVector = icon,
                    contentDescription = labelText,
                    tint = contentTint,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = contentTint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
