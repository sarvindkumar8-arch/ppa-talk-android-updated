package com.ppatalk.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PpaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["title"] ?: message.notification?.title ?: "PPA Talk"
        val body = message.data["body"] ?: message.notification?.body ?: "Incoming call"
        val callId = message.data["callId"].orEmpty()
        val callerUid = message.data["callerUid"].orEmpty()
        val callType = message.data["callType"] ?: "audio"
        showCallNotification(title, body, callId, callerUid, callType)
    }

    private fun saveToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(mapOf("token" to token, "platform" to "android",
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
    }

    private fun showCallNotification(title: String, body: String, callId: String,
                                     callerUid: String, callType: String) {
        val channelId = "ppa_incoming_calls"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Incoming calls", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for incoming PPA Talk calls" })
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ppa_call_id", callId)
            putExtra("ppa_caller_uid", callerUid)
            putExtra("ppa_call_type", callType)
        }
        val pending = PendingIntent.getActivity(this, callId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(callId.hashCode(), notification)
    }
}
