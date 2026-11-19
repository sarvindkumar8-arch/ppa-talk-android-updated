package com.ppatalk.app

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import io.livekit.android.events.collect
import kotlinx.coroutines.tasks.await

/**
 * Firestore coordinates ringing/accept/end; LiveKit carries all call media.
 */
class PpaCallController(
    private val context: Context,
    private val uid: String,
    private val friendUid: String,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onRemoteCallEnded: () -> Unit = {}
) {
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-south1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var callId: String? = null
    private var caller = false
    private var callType = "audio"
    private var callListener: ListenerRegistration? = null
    private var eventJob: kotlinx.coroutines.Job? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var localVideo: LocalVideoTrack? = null
    private var remoteVideo: VideoTrack? = null
    private var joining = false
    private var released = false
    private var muted = false
    private var speakerEnabled = true

    fun startOutgoing(type: String) {
        callType = type
        caller = true
        val ref = db.collection("callSessions").document()
        callId = ref.id
        onStatus("Calling…")
        val data = hashMapOf<String, Any>(
            "callerUid" to uid,
            "calleeUid" to friendUid,
            "status" to "ringing",
            "callType" to type,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        ref.set(data)
            .addOnSuccessListener { listenForCallUpdates() }
            .addOnFailureListener { onError(it.localizedMessage ?: "Could not start call.") }
    }

    fun acceptIncoming(id: String) {
        callId = id
        caller = false
        onStatus("Accepting call…")
        val ref = db.collection("callSessions").document(id)
        ref.update(
            mapOf(
                "status" to "accepting",
                "acceptedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            ref.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || snapshot.getString("calleeUid") != uid ||
                    snapshot.getString("status") !in listOf("accepting", "accepted")) {
                    onError("This call is no longer available.")
                    return@addOnSuccessListener
                }
                callType = snapshot.getString("callType") ?: "audio"
                listenForCallUpdates()
                joinLiveKitRoom()
            }.addOnFailureListener { onError(it.localizedMessage ?: "Could not load call.") }
        }.addOnFailureListener { onError(it.localizedMessage ?: "Could not accept call.") }
    }

    private fun listenForCallUpdates() {
        val id = callId ?: return
        callListener?.remove()
        callListener = db.collection("callSessions").document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Call listener failed.")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                when (snapshot.getString("status") ?: "ringing") {
                    "accepting", "accepted" -> {
                        if (caller && !joining && room == null) joinLiveKitRoom()
                    }
                    "rejected" -> {
                        onStatus("Call declined")
                        onRemoteCallEnded()
                    }
                    "ended", "missed" -> {
                        onStatus("Call ended")
                        onRemoteCallEnded()
                    }
                    "ringing" -> if (caller) onStatus("Calling…")
                }
            }
    }

    private fun joinLiveKitRoom() {
        val id = callId ?: return
        if (joining || released) return
        joining = true
        onStatus("Connecting securely…")
        scope.launch {
            try {
                val response = functions.getHttpsCallable("createLiveKitToken")
                    .call(mapOf("callId" to id)).await()
                val result = response.getData() as? Map<*, *>
                    ?: error("Token service returned an invalid response.")
                val serverUrl = result["serverUrl"] as? String
                    ?: error("LiveKit server URL is missing.")
                val token = result["token"] as? String
                    ?: error("LiveKit access token is missing.")

                val liveRoom = LiveKit.create(context.applicationContext)
                room = liveRoom

                eventJob = scope.launch {
                    liveRoom.events.collect { event ->
                            when (event) {
                                is RoomEvent.TrackSubscribed -> {
                                    val track = event.track
                                    if (track is VideoTrack) {
                                        remoteVideo = track
                                        remoteRenderer?.let { track.addRenderer(it) }
                                        onStatus("Connected • remote video received")
                                    }
                                }
                                is RoomEvent.ParticipantConnected -> onStatus("Connected • waiting for video")
                                is RoomEvent.ParticipantDisconnected -> onStatus("Other participant disconnected")
                                is RoomEvent.Disconnected -> if (!released) onStatus("Connection interrupted")
                                is RoomEvent.TrackUnsubscribed -> {
                                    val track = event.track
                                    if (track is VideoTrack && remoteVideo === track) {
                                        remoteVideo = null
                                        onStatus("Remote video paused")
                                    }
                                }
                                else -> Unit
                            }
                        }
                }

                liveRoom.connect(serverUrl, token)
                val participant = liveRoom.localParticipant
                participant.setMicrophoneEnabled(true)
                if (callType == "video") {
                    participant.setCameraEnabled(true)
                    localVideo = participant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                    localVideo?.let { track -> localRenderer?.let { track.addRenderer(it) } }
                    // A peer may have published before our event collector finished connecting.
                    liveRoom.remoteParticipants.values.forEach { remote ->
                        val track = remote.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
                        if (track != null) {
                            remoteVideo = track
                            remoteRenderer?.let { track.addRenderer(it) }
                        }
                    }
                }
                db.collection("callSessions").document(id).update(
                    mapOf(
                        "status" to "accepted",
                        "acceptedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { Log.w("PPATalkLiveKit", "Could not update accepted status", it) }
                onStatus(if (callType == "video") "Connected • waiting for remote video" else "Connected")
            } catch (t: Throwable) {
                Log.e("PPATalkLiveKit", "Could not join room", t)
                joining = false
                onError(t.localizedMessage ?: "Could not connect the call.")
            }
        }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        room?.initVideoRenderer(renderer)
        localVideo?.let { it.addRenderer(renderer) }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        room?.initVideoRenderer(renderer)
        remoteVideo?.let { it.addRenderer(renderer) }
    }

    fun switchCamera() {
        val track = localVideo
        if (track == null) onError("Camera is not ready yet.")
        else track.switchCamera()
    }

    fun setMuted(value: Boolean) {
        muted = value
        scope.launch {
            try { room?.localParticipant?.setMicrophoneEnabled(!value) }
            catch (t: Throwable) { onError(t.localizedMessage ?: "Could not change microphone.") }
        }
    }

    fun setSpeaker(value: Boolean) {
        speakerEnabled = value
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = value
    }

    fun endCall() {
        val id = callId
        if (id != null) {
            db.collection("callSessions").document(id).update(
                mapOf(
                    "status" to "ended",
                    "endedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
        }
        release()
        onStatus("Call ended")
    }

    fun release() {
        if (released) return
        released = true
        callListener?.remove()
        callListener = null
        eventJob?.cancel()
        localVideo?.let { track -> localRenderer?.let(track::removeRenderer) }
        remoteVideo?.let { track -> remoteRenderer?.let(track::removeRenderer) }
        localVideo = null
        remoteVideo = null
        localRenderer?.release()
        remoteRenderer?.release()
        localRenderer = null
        remoteRenderer = null
        val activeRoom = room
        room = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { activeRoom?.disconnect() } catch (_: Throwable) { }
        }
        scope.cancel()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        run {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    fun currentCallId(): String? = callId
}
