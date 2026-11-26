package com.ppatalk.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.Ringtone
import com.google.firebase.firestore.ListenerRegistration
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PpaMessagingService : FirebaseMessagingService() {
    private var incomingRingtone: Ringtone? = null
    private var incomingCallListener: ListenerRegistration? = null

    private fun stopIncomingRingtone() {
        try { incomingRingtone?.stop() } catch (_: Exception) {}
        incomingRingtone = null
        incomingCallListener?.remove()
        incomingCallListener = null
    }

    private fun startIncomingRingtone(callId: String) {
        stopIncomingRingtone()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            incomingRingtone = RingtoneManager.getRingtone(applicationContext, uri)?.also { tone ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
                tone.play()
            }
        } catch (_: Exception) {}
        incomingCallListener = FirebaseFirestore.getInstance().collection("callSessions")
            .document(callId).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    stopIncomingRingtone()
                    cancelCallNotification(callId)
                    return@addSnapshotListener
                }
                val status = snapshot.getString("status")
                if (status !in listOf("ringing")) {
                    stopIncomingRingtone()
                    cancelCallNotification(callId)
                }
            }
    }
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: "PPA Talk"
        val body = message.data["body"] ?: message.notification?.body ?: "Incoming call"
        val type = message.data["type"].orEmpty()
        if (type == "friend_request") {
            showFriendRequestNotification(title, body, message.data["requestId"].orEmpty())
            return
        }
        if (type == "chat_message") {
            showChatMessageNotification(
                title,
                body,
                message.data["chatId"].orEmpty(),
                message.data["senderUid"].orEmpty()
            )
            return
        }
        val callId = message.data["callId"].orEmpty()
        if (type == "call_cancel") {
            cancelCallNotification(callId)
            return
        }
        val callerUid = message.data["callerUid"].orEmpty()
        val callType = message.data["callType"] ?: "audio"
        if (callId.isBlank()) return

        // FCM can deliver an old ringing push after the caller has already
        // ended the call. Verify the live Firestore state before displaying it.
        FirebaseFirestore.getInstance().collection("callSessions").document(callId)
            .get()
            .addOnSuccessListener { snapshot ->
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (snapshot.exists() &&
                    snapshot.getString("status") == "ringing" &&
                    snapshot.getString("calleeUid") == currentUid) {
                    showCallNotification(title, body, callId, callerUid, callType)
                } else {
                    cancelCallNotification(callId)
                }
            }
            .addOnFailureListener {
                // Fail closed: do not show a potentially stale incoming call.
                cancelCallNotification(callId)
            }
    }


    private fun cancelCallNotification(callId: String) {
        if (callId.isBlank()) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(callId.hashCode())
    }

    private fun saveToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(mapOf("token" to token, "platform" to "android",
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
    }

    private fun showChatMessageNotification(title: String, body: String, chatId: String, senderUid: String) {
        val channelId = "ppa_chat_messages"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Chat messages", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New messages from your PPA Talk friends"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ppa_chat_id", chatId)
            putExtra("ppa_chat_sender_uid", senderUid)
        }
        val notificationId = if (chatId.isBlank()) System.currentTimeMillis().toInt() else chatId.hashCode()
        val pending = PendingIntent.getActivity(this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title.ifBlank { "PPA Talk" })
            .setContentText(body.ifBlank { "You received a new message" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(notificationId, notification)
    }

    private fun showFriendRequestNotification(title: String, body: String, requestId: String) {
        val channelId = "ppa_general_notifications"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "PPA Talk notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Friend requests and social activity"
                enableVibration(true)
            })
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, requestId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()
        manager.notify(requestId.hashCode(), notification)
    }

    private fun showCallNotification(title: String, body: String, callId: String,
                                     callerUid: String, callType: String) {
        val channelId = "ppa_incoming_calls_v4"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val channel = NotificationChannel(
                channelId, "Incoming calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ringtone and alerts for incoming PPA Talk calls"
                setSound(ringtoneUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ppa_call_id", callId)
            putExtra("ppa_caller_uid", callerUid)
            putExtra("ppa_call_type", callType)
        }
        val pending = PendingIntent.getActivity(this, callId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rejectIntent = Intent(this, PpaCallActionReceiver::class.java).apply {
            action = PpaCallActionReceiver.ACTION_REJECT_CALL
            putExtra(PpaCallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val rejectPending = PendingIntent.getBroadcast(
            this, callId.hashCode() + 1, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val answerPending = PendingIntent.getActivity(
            this, callId.hashCode() + 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title.ifBlank { "PPA Talk" })
            .setContentText(if (callType == "video") "Incoming video call" else "Incoming voice call")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pending)
            .addAction(android.R.drawable.sym_call_incoming, "Answer", answerPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reject", rejectPending)
            .build()
        manager.notify(callId.hashCode(), notification)
    }
}
