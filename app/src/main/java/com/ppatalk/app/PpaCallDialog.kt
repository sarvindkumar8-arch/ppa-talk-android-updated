package com.ppatalk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import org.webrtc.SurfaceViewRenderer

@Composable
fun PpaCallDialog(
    controller: PpaCallController,
    callerName: String,
    callType: String,
    status: String,
    onEnd: () -> Unit
) {
    var muted by remember(controller) { mutableStateOf(false) }
    var speaker by remember(controller) { mutableStateOf(true) }
    var previewOffset by remember(controller) { mutableStateOf(Offset.Zero) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val baseX = with(density) { (maxWidth - 112.dp - 18.dp).toPx() }
            val baseY = with(density) { 52.dp.toPx() }
            val maxX = with(density) { (maxWidth - 112.dp).toPx() }
            val maxY = with(density) { (maxHeight - 158.dp).toPx() }

            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF07111F))
            ) {
                if (callType == "video") {
                    AndroidView(
                        factory = { viewContext ->
                            SurfaceViewRenderer(viewContext).also {
                                controller.attachRemoteRenderer(it)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    AndroidView(
                        factory = { viewContext ->
                            SurfaceViewRenderer(viewContext).also {
                                controller.attachLocalRenderer(it)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    (baseX + previewOffset.x).roundToInt(),
                                    (baseY + previewOffset.y).roundToInt()
                                )
                            }
                            .width(112.dp)
                            .height(158.dp)
                            .pointerInput(controller, maxX, maxY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val minOffsetX = -baseX
                                    val maxOffsetX = maxX - baseX
                                    val minOffsetY = -baseY
                                    val maxOffsetY = maxY - baseY
                                    previewOffset = Offset(
                                        (previewOffset.x + dragAmount.x).coerceIn(minOffsetX, maxOffsetX),
                                        (previewOffset.y + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                                    )
                                }
                            }
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(116.dp),
                            shape = CircleShape,
                            color = Color(0xFF173B62)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("PPA", color = Color.White, style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("PPA Talk voice call", color = Color.White,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(start = 20.dp, top = 42.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        callerName.ifBlank { "PPA Talk user" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    val connected = status.contains("connected", ignoreCase = true) ||
                        status.contains("remote video displaying", ignoreCase = true) ||
                        status.contains("remote video frame", ignoreCase = true)
                    Text(
                        when {
                            connected -> "● Connected"
                            status.isNotBlank() -> status
                            else -> "Connecting…"
                        },
                        color = if (connected) Color(0xFF55D98A) else Color(0xFFD4DCE8),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (callType == "video") "Video call" else "Voice call",
                        color = Color(0xFFB8C4D8),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControl(
                        icon = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (muted) "Unmute" else "Mute",
                        active = muted,
                        onClick = {
                            muted = !muted
                            controller.setMuted(muted)
                        }
                    )
                    CallControl(
                        icon = Icons.Default.VolumeUp,
                        label = if (speaker) "Speaker" else "Earpiece",
                        active = speaker,
                        onClick = {
                            speaker = !speaker
                            controller.setSpeaker(speaker)
                        }
                    )
                    if (callType == "video") {
                        CameraSwitchControl(onClick = { controller.switchCamera() })
                    }
                    Surface(
                        modifier = Modifier.size(66.dp),
                        shape = CircleShape,
                        color = Color(0x66E53935)
                    ) {
                        IconButton(onClick = onEnd, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End call",
                                tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraSwitchControl(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = Color(0x5507111F)
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(27.dp)
                )
            }
        }
        Text("Camera", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = if (active) Color(0x6655A9FF) else Color(0x5507111F)
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = label, tint = Color.White,
                    modifier = Modifier.size(25.dp))
            }
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
