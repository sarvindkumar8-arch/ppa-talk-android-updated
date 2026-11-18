package com.ppatalk.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun PpaCallDialog(
    controller: PpaCallController,
    callType: String,
    status: String,
    onEnd: () -> Unit
) {
    var muted by remember(controller) { mutableStateOf(false) }
    var speaker by remember(controller) { mutableStateOf(true) }
    val context = LocalContext.current

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (callType == "video") "PPA Talk video call" else "PPA Talk voice call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status, color = MaterialTheme.colorScheme.primary)
                if (callType == "video") {
                    AndroidView(
                        factory = { viewContext ->
                            SurfaceViewRenderer(viewContext).also { controller.attachRemoteRenderer(it) }
                        },
                        modifier = Modifier.fillMaxWidth().height(190.dp)
                    )
                    AndroidView(
                        factory = { viewContext ->
                            SurfaceViewRenderer(viewContext).also { controller.attachLocalRenderer(it) }
                        },
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                } else {
                    Text("Voice connection is being established. Keep this screen open.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    muted = !muted
                    controller.setMuted(muted)
                }) {
                    Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (muted) "Unmute" else "Mute")
                }
                FilledTonalButton(onClick = {
                    speaker = !speaker
                    controller.setSpeaker(speaker)
                }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (speaker) "Speaker" else "Earpiece")
                }
                Button(onClick = onEnd, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.CallEnd, contentDescription = "End call")
                }
            }
        }
    )
}
