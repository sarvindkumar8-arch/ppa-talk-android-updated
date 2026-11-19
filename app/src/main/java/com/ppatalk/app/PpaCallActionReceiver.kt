package com.ppatalk.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class PpaCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REJECT_CALL) return
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if (callId.isBlank()) return
        FirebaseFirestore.getInstance().collection("callSessions").document(callId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(callId.hashCode())
    }

    companion object {
        const val ACTION_REJECT_CALL = "com.ppatalk.app.REJECT_INCOMING_CALL"
        const val EXTRA_CALL_ID = "ppa_call_id"
    }
}
