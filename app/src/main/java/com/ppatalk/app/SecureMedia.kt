package com.ppatalk.app

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.firebase.functions.FirebaseFunctions

@Composable
fun SecureMediaImage(
    mediaRef: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var resolvedUrl by remember(mediaRef) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaRef) {
        resolvedUrl = null
        if (mediaRef.isNotBlank()) {
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("getSecureMediaUrl")
                .call(mapOf("mediaRef" to mediaRef))
                .addOnSuccessListener { result ->
                    val data = result.getData() as? Map<*, *>
                    resolvedUrl = data?.get("url") as? String
                }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (resolvedUrl == null) CircularProgressIndicator()
        else AsyncImage(
            model = resolvedUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}

@Composable
fun SecureMediaVideo(mediaRef: String, modifier: Modifier = Modifier) {
    var resolvedUrl by remember(mediaRef) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaRef) {
        resolvedUrl = null
        if (mediaRef.isNotBlank()) {
            FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("getSecureMediaUrl")
                .call(mapOf("mediaRef" to mediaRef))
                .addOnSuccessListener { result ->
                    val data = result.getData() as? Map<*, *>
                    resolvedUrl = data?.get("url") as? String
                }
        }
    }
    if (resolvedUrl != null) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setVideoURI(Uri.parse(resolvedUrl))
                }
            },
            update = { view ->
                if (view.tag != resolvedUrl) {
                    view.tag = resolvedUrl
                    view.setVideoURI(Uri.parse(resolvedUrl))
                }
            },
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
