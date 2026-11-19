package com.ppatalk.app

import android.content.Context
import android.util.Log
import org.webrtc.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firestore-signalled WebRTC peer call. Media travels peer-to-peer; Firestore
 * carries only SDP and ICE metadata. A TURN server is required for reliable
 * connectivity across restrictive mobile/carrier networks.
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
    private val egl = EglBase.create()
    private var factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var callId: String? = null
    private var caller = false
    private var callType = "audio"
    private var localAudio: AudioTrack? = null
    private var localVideo: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var callListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null
    private val receivedCandidates = mutableSetOf<String>()
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var released = false
    private var remoteDescriptionSet = false
    private var localRenderer: SurfaceViewRenderer? = null
    @Volatile private var remoteRenderer: SurfaceViewRenderer? = null
    @Volatile private var remoteVideo: VideoTrack? = null
    private val remoteTrackLock = Any()
    private var muted = false
    private var speakerEnabled = true

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

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
        ref.set(data).addOnSuccessListener {
            createPeer()
            createOffer()
            listenForCallUpdates()
            listenForRemoteCandidates()
        }.addOnFailureListener { onError(it.localizedMessage ?: "Could not start call. Check Firestore rules.") }
    }

    fun acceptIncoming(id: String) {
        callId = id
        caller = false
        val ref = db.collection("callSessions").document(id)
        ref.update(mapOf("status" to "accepting",
            "acceptedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.getString("calleeUid") != uid ||
                snapshot.getString("status") !in listOf("ringing", "accepting")) {
                onError("This call is no longer available.")
                return@addOnSuccessListener
            }
            callType = snapshot.getString("callType") ?: "audio"
            val offerMap = snapshot.get("offer") as? Map<*, *>
            val sdp = offerMap?.get("sdp") as? String
            if (sdp.isNullOrBlank()) {
                onError("Caller offer is not ready. Please try again.")
                return@addOnSuccessListener
            }
            createPeer()
            val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peer?.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    Log.d("PPATalkWebRTC", "Remote OFFER applied successfully")
                    logSdpMediaSections("REMOTE OFFER", sdp)
                    logTransceivers("after remote OFFER")
                    flushRemoteCandidates()
                    createAnswer()
                }
                override fun onSetFailure(error: String?) { onError("Could not accept call: $error") }
            }, offer)
            listenForCallUpdates()
            listenForRemoteCandidates()
        }.addOnFailureListener { onError(it.localizedMessage ?: "Could not load incoming call.") }
    }

    private fun createPeer() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        setSpeaker(true)
        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        Log.d("PPATalkWebRTC", "ICE connected: callType=$callType, callId=$callId")
                        if (callType == "video") {
                            val videoState = videoNegotiationSummary()
                            Log.d("PPATalkWebRTC", "VIDEO DIAGNOSTIC: $videoState")
                            onStatus("Connected • $videoState")
                        } else {
                            onStatus("Connected")
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> onStatus("Connection interrupted…")
                    PeerConnection.IceConnectionState.FAILED -> onError("Connection failed. A TURN server may be required.")
                    PeerConnection.IceConnectionState.CLOSED -> onStatus("Call ended")
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d("PPATalkWebRTC", "ICE receiving=$receiving, callType=$callType")
            }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d("PPATalkWebRTC", "ICE gathering state=$state, callType=$callType")
            }
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val id = callId ?: return
                val collection = if (caller) "callerCandidates" else "calleeCandidates"
                val candidateType = candidate.sdp.substringAfter(" typ ", "unknown").substringBefore(' ')
                Log.d("PPATalkWebRTC", "Local ICE candidate: type=$candidateType, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}")
                val data = hashMapOf<String, Any>(
                    "candidate" to candidate.sdp,
                    "sdpMid" to (candidate.sdpMid ?: ""),
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                db.collection("callSessions").document(id).collection(collection).add(data)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                // Compatibility fallback for peers that deliver a MediaStream callback.
                Log.d("PPATalkWebRTC", "onAddStream: videoTracks=${stream?.videoTracks?.size ?: 0}")
                stream?.videoTracks?.firstOrNull()?.let { attachRemoteVideoTrack(it) }
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val receiverTrack = receiver?.track()
                Log.d("PPATalkWebRTC", "onAddTrack: kind=${receiverTrack?.kind()}, streams=${streams?.size ?: 0}")
                val track = receiverTrack as? VideoTrack
                    ?: streams?.firstNotNullOfOrNull { it.videoTracks.firstOrNull() }
                    ?: return
                attachRemoteVideoTrack(track)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val receiverTrack = transceiver?.receiver?.track()
                Log.d("PPATalkWebRTC", "onTrack: kind=${receiverTrack?.kind()}, mid=${transceiver?.mid}, direction=${transceiver?.currentDirection}")
                val track = receiverTrack as? VideoTrack ?: return
                attachRemoteVideoTrack(track)
            }
            override fun onRemoveTrack(receiver: RtpReceiver?) {
                val track = receiver?.track() as? VideoTrack ?: return
                remoteRenderer?.let { track.removeSink(it) }
                if (remoteVideo === track) remoteVideo = null
                onStatus("Remote video track removed")
            }
        }) ?: run {
            onError("Could not create WebRTC connection.")
            return
        }

        val audioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("ppa_audio_$uid", audioSource)
        localAudio = audioTrack
        audioTrack.setEnabled(true)
        peer?.addTrack(audioTrack, listOf("ppa_stream"))

        if (callType == "video") {
            val enumerator = Camera2Enumerator(context)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (cameraName == null) {
                onError("No camera is available on this phone.")
                return
            }
            cameraCapturer = enumerator.createCapturer(cameraName, null)
            val capturer = cameraCapturer ?: return
            surfaceTextureHelper = SurfaceTextureHelper.create("PPA-Camera", egl.eglBaseContext)
            val source = factory.createVideoSource(false)
            videoSource = source
            capturer.initialize(surfaceTextureHelper, context, source.capturerObserver)
            capturer.startCapture(640, 480, 24)
            val videoTrack = factory.createVideoTrack("ppa_video_$uid", source)
            localVideo = videoTrack
            videoTrack.setEnabled(true)
            // Restore the previously working bidirectional transceiver setup.
            // The addTrack experiment caused ICE/audio regression on the test phones.
            val videoTransceiver = peer?.addTransceiver(
                videoTrack,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_RECV,
                    listOf("ppa_stream")
                )
            )
            if (videoTransceiver == null) {
                onError("Could not create video transceiver.")
                return
            }
            Log.d("PPATalkWebRTC", "Local video transceiver created: mid=${videoTransceiver.mid}, direction=${videoTransceiver.direction}")
            localRenderer?.let { localVideo?.addSink(it) }
        }
    }

    private fun createOffer() {
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) { onError("Could not create call offer."); return }
                val videoMLine = description.description.lineSequence().firstOrNull { it.startsWith("m=video") }
                val videoDirection = description.description.lineSequence().firstOrNull {
                    it == "a=sendrecv" || it == "a=sendonly" || it == "a=recvonly" || it == "a=inactive"
                }
                Log.d("PPATalkWebRTC", "Created offer: callType=$callType, videoMLine=$videoMLine, direction=$videoDirection")
                logSdpMediaSections("LOCAL OFFER", description.description)
                logTransceivers("after create OFFER")
                if (callType == "video") onStatus(if (videoMLine != null) "Video offer created" else "Video offer missing m=video")
                peer?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        logSdpMediaSections("SET LOCAL OFFER", description.description)
                        logTransceivers("after set LOCAL OFFER")
                        val id = callId ?: return
                        db.collection("callSessions").document(id).update(
                            mapOf("offer" to mapOf("type" to "offer", "sdp" to description.description),
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        ).addOnFailureListener { onError(it.localizedMessage ?: "Could not send call offer.") }
                    }
                    override fun onSetFailure(error: String?) { onError("Could not set local offer: $error") }
                }, description)
            }
            override fun onCreateFailure(error: String?) { onError("Offer failed: $error") }
        }, MediaConstraints())
    }

    private fun createAnswer() {
        peer?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                if (description == null) { onError("Could not create answer."); return }
                val videoMLine = description.description.lineSequence().firstOrNull { it.startsWith("m=video") }
                val videoDirection = description.description.lineSequence().firstOrNull {
                    it == "a=sendrecv" || it == "a=sendonly" || it == "a=recvonly" || it == "a=inactive"
                }
                Log.d("PPATalkWebRTC", "Created answer: callType=$callType, videoMLine=$videoMLine, direction=$videoDirection")
                logSdpMediaSections("LOCAL ANSWER", description.description)
                logTransceivers("after create ANSWER")
                if (callType == "video") onStatus(if (videoMLine != null) "Video answer created" else "Video answer missing m=video")
                peer?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        logSdpMediaSections("SET LOCAL ANSWER", description.description)
                        logTransceivers("after set LOCAL ANSWER")
                        val id = callId ?: return
                        db.collection("callSessions").document(id).update(
                            mapOf("answer" to mapOf("type" to "answer", "sdp" to description.description),
                                "status" to "accepted",
                                "acceptedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
                        ).addOnSuccessListener { onStatus("Connecting…") }
                            .addOnFailureListener { onError(it.localizedMessage ?: "Could not send answer.") }
                    }
                    override fun onSetFailure(error: String?) { onError("Could not set local answer: $error") }
                }, description)
            }
            override fun onCreateFailure(error: String?) { onError("Answer failed: $error") }
        }, MediaConstraints())
    }

    private fun listenForCallUpdates() {
        val id = callId ?: return
        callListener?.remove()
        callListener = db.collection("callSessions").document(id).addSnapshotListener { snapshot, error ->
            if (error != null) { onError(error.localizedMessage ?: "Call listener failed."); return@addSnapshotListener }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val status = snapshot.getString("status") ?: "ringing"
            when (status) {
                "accepted" -> {
                    val answerMap = snapshot.get("answer") as? Map<*, *>
                    val sdp = answerMap?.get("sdp") as? String
                    if (caller && !sdp.isNullOrBlank() && !remoteDescriptionSet) {
                        Log.d("PPATalkWebRTC", "Received answer SDP: hasVideoMLine=${sdp.lineSequence().any { it.startsWith("m=video") }}")
                        peer?.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                remoteDescriptionSet = true
                                Log.d("PPATalkWebRTC", "Remote ANSWER applied successfully")
                                logSdpMediaSections("REMOTE ANSWER", sdp)
                                logTransceivers("after remote ANSWER")
                                flushRemoteCandidates()
                                onStatus(if (callType == "video") "Answer received • negotiating video" else "Connecting…")
                            }
                            override fun onSetFailure(error: String?) { onError("Could not read answer: $error") }
                        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    }
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

    private fun listenForRemoteCandidates() {
        val id = callId ?: return
        val collection = if (caller) "calleeCandidates" else "callerCandidates"
        candidateListener?.remove()
        candidateListener = db.collection("callSessions").document(id).collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onError(error.localizedMessage ?: "ICE candidate listener failed."); return@addSnapshotListener }
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val data = change.document
                    if (!receivedCandidates.add(data.id)) return@forEach
                    val candidate = IceCandidate(
                        data.getString("sdpMid") ?: "",
                        (data.getLong("sdpMLineIndex") ?: 0L).toInt(),
                        data.getString("candidate") ?: return@forEach
                    )
                    if (remoteDescriptionSet) peer?.addIceCandidate(candidate) else pendingRemoteCandidates.add(candidate)
                }
            }
    }

    private fun flushRemoteCandidates() {
        pendingRemoteCandidates.forEach { peer?.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
        // Fetch candidates that arrived before the snapshot listener was attached.
        val id = callId ?: return
        val collection = if (caller) "calleeCandidates" else "callerCandidates"
        db.collection("callSessions").document(id).collection(collection).get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { data ->
                    if (!receivedCandidates.add(data.id)) return@forEach
                    val candidateText = data.getString("candidate") ?: return@forEach
                    peer?.addIceCandidate(IceCandidate(
                        data.getString("sdpMid") ?: "",
                        (data.getLong("sdpMLineIndex") ?: 0L).toInt(),
                        candidateText
                    ))
                }
            }
    }

    private fun logSdpMediaSections(label: String, sdp: String) {
        var sectionName = "SESSION"
        val selected = mutableListOf<String>()
        fun flush() {
            if (sectionName == "audio" || sectionName == "video") {
                Log.d("PPATalkWebRTC", "$label $sectionName: ${selected.joinToString(" | ")}")
            }
            selected.clear()
        }
        sdp.lineSequence().forEach { line ->
            if (line.startsWith("m=")) {
                flush()
                sectionName = when {
                    line.startsWith("m=video") -> "video"
                    line.startsWith("m=audio") -> "audio"
                    else -> "other"
                }
                if (sectionName == "video" || sectionName == "audio") selected.add(line)
            } else if (sectionName == "video" || sectionName == "audio") {
                if (line.startsWith("a=mid:") || line == "a=sendrecv" || line == "a=sendonly" ||
                    line == "a=recvonly" || line == "a=inactive" || line.startsWith("a=msid:") ||
                    line.startsWith("a=rtpmap:")) selected.add(line)
            }
        }
        flush()
    }

    private fun logTransceivers(stage: String) {
        val currentPeer = peer ?: return
        currentPeer.transceivers.forEachIndexed { index, transceiver ->
            val senderKind = transceiver.sender.track()?.kind()
            val receiverKind = transceiver.receiver.track()?.kind()
            Log.d("PPATalkWebRTC", "Transceiver[$index] $stage: mid=${transceiver.mid}, direction=${transceiver.direction}, currentDirection=${transceiver.currentDirection}, senderKind=$senderKind, receiverKind=$receiverKind")
        }
    }

    private fun videoNegotiationSummary(): String {
        val transceiver = peer?.transceivers?.firstOrNull {
            it.sender.track()?.kind() == "video" || it.receiver.track()?.kind() == "video"
        } ?: return "video transceiver missing"
        val receiverKind = transceiver.receiver.track()?.kind() ?: "none"
        val senderKind = transceiver.sender.track()?.kind() ?: "none"
        val negotiatedDirection = transceiver.currentDirection?.toString() ?: "not-negotiated"
        return "video mid=${transceiver.mid ?: "?"}, dir=$negotiatedDirection, tx=$senderKind, rx=$receiverKind"
    }
    private fun attachRemoteVideoTrack(track: VideoTrack) {
        Log.d("PPATalkWebRTC", "Remote video track attached: id=${track.id()}, enabled=${track.enabled()}")
        track.setEnabled(true)
        // Track callbacks run on WebRTC threads while Compose creates the renderer
        // on the UI thread. Synchronize both paths so a track arriving at the same
        // time as the renderer is attached can never be missed.
        synchronized(remoteTrackLock) {
            if (remoteVideo !== track) {
                remoteVideo?.let { previous -> remoteRenderer?.let(previous::removeSink) }
                remoteVideo = track
            }
            remoteRenderer?.let { renderer ->
                track.removeSink(renderer)
                track.addSink(renderer)
            }
        }
        onStatus("Remote video stream received")
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        renderer.init(egl.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        renderer.setMirror(true)
        localVideo?.addSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(egl.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        renderer.setMirror(false)
        synchronized(remoteTrackLock) {
            remoteRenderer?.let { previous -> remoteVideo?.removeSink(previous) }
            remoteRenderer = renderer
            remoteVideo?.let { track ->
                track.removeSink(renderer)
                track.addSink(renderer)
            }
        }
    }

    fun switchCamera() {
        val capturer = cameraCapturer
        if (capturer == null || released) {
            onError("Camera switch is not available.")
            return
        }
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                onStatus(if (isFrontCamera) "Front camera" else "Back camera")
            }
            override fun onCameraSwitchError(errorDescription: String?) {
                onError(errorDescription ?: "Could not switch camera.")
            }
        })
    }

    fun setMuted(value: Boolean) {
        muted = value
        localAudio?.setEnabled(!value)
    }

    fun setSpeaker(value: Boolean) {
        speakerEnabled = value
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = value
    }

    fun endCall() {
        val id = callId
        if (id != null) {
            db.collection("callSessions").document(id).update(
                mapOf("status" to "ended", "endedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
            )
        }
        release()
        onStatus("Call ended")
    }

    fun release() {
        if (released) return
        released = true
        callListener?.remove()
        candidateListener?.remove()
        localVideo?.let { track -> localRenderer?.let { track.removeSink(it) } }
        synchronized(remoteTrackLock) {
            remoteVideo?.let { track -> remoteRenderer?.let { track.removeSink(it) } }
            remoteVideo = null
            remoteRenderer = null
        }
        try { cameraCapturer?.stopCapture() } catch (_: Exception) {}
        cameraCapturer?.dispose()
        videoSource?.dispose()
        surfaceTextureHelper?.dispose()
        peer?.close()
        peer?.dispose()
        localRenderer?.release()
        remoteRenderer?.release()
        localRenderer = null
        remoteRenderer = null
        localAudio?.dispose()
        localVideo?.dispose()
        remoteVideo?.dispose()
        factory.dispose()
        egl.release()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = android.media.AudioManager.MODE_NORMAL
    }

    fun currentCallId(): String? = callId
    fun eglContext(): EglBase.Context = egl.eglBaseContext
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}
