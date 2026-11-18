package com.example.service

import com.example.cloud.CloudRepository
import com.example.cloud.InboxEvent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM entry point. The Cloud Function (functions/index.js) sends a high-priority DATA message
 * for every new event, so this runs even when the app is closed or the phone is asleep.
 */
class PushService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        CloudRepository.init(this)
        scope.launch { CloudRepository.saveFcmToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        CloudRepository.init(this)
        // Ignore pushes meant for another account that used this phone before.
        if (CloudRepository.uid == null) return
        val d = message.data
        if (d.isEmpty()) return

        val event = InboxEvent(
            id = d["eventId"].orEmpty(),
            type = d["type"].orEmpty(),
            from = d["from"].orEmpty(),
            fromName = d["fromName"].orEmpty(),
            fromPhone = d["fromPhone"].orEmpty(),
            title = d["title"].orEmpty(),
            body = d["body"].orEmpty(),
            lat = d["lat"]?.toDoubleOrNull(),
            lng = d["lng"]?.toDoubleOrNull(),
            hour = d["hour"]?.toIntOrNull() ?: -1,
            minute = d["minute"]?.toIntOrNull() ?: -1,
            extra = d["extra"].orEmpty(),
            ts = d["ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
        EventHandler.handle(this, scope, event)
    }
}
