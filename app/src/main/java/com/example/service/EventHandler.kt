package com.example.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.cloud.EventType
import com.example.cloud.InboxEvent
import com.example.data.AppDatabase
import com.example.data.model.EmergencyLog
import com.example.data.model.ReminderNote
import com.example.util.ActiveChat
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import com.example.util.SosAlert
import com.example.util.SosCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One place that turns an incoming event into UI (notification, SOS takeover, alarm...).
 * Events arrive twice on purpose: via FCM push (works when the app is killed) and via the
 * Firestore listener (works when push is delayed). The id check makes sure it only fires once.
 */
object EventHandler {

    private val seen = object : LinkedHashMap<String, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 300
    }

    @Synchronized
    private fun firstTime(id: String): Boolean {
        if (id.isBlank()) return true
        if (seen.containsKey(id)) return false
        seen[id] = true
        return true
    }

    fun handle(context: Context, scope: CoroutineScope, e: InboxEvent) {
        if (!firstTime(e.id)) return
        val ctx = context.applicationContext
        val who = e.fromName.ifBlank { "Someone" }

        when (e.type) {
            EventType.SOS -> onSos(ctx, scope, e, who)

            EventType.SOS_SAFE -> {
                SosCenter.onSafe(ctx, e.from)
                if (SosCenter.alerts.value.isEmpty()) NotificationHelper.cancelSosNotification(ctx)
                NotificationHelper.showInfo(ctx, "✅ $who is safe now", "SOS band ho gaya.")
                log(ctx, scope, "SOS_SAFE", "$who marked themselves safe", "Closed")
            }

            EventType.SOS_ACK ->
                NotificationHelper.showInfo(ctx, "👀 $who ne aapka SOS dekh liya", "Wo aapki madad ke liye aa rahe hain.")

            EventType.MESSAGE -> {
                if (ActiveChat.linkId != e.extra) NotificationHelper.showMessage(ctx, who, e.body, e.extra)
            }

            EventType.REMINDER -> NotificationHelper.showPersonalReminder(ctx, who, e.title, e.body)

            EventType.NUDGE -> NotificationHelper.showCuteLoveNoteNotification(ctx, who, e.title, e.body)

            EventType.ALARM -> onAlarm(ctx, scope, e, who)

            EventType.LOC_REQUEST ->
                NotificationHelper.showInfo(
                    ctx, "📍 $who aapki location maang rahe hain",
                    "Tap karke location ON karo taaki wo aapko dekh sakein.", openLocation = true
                )

            EventType.TASK ->
                NotificationHelper.showInfo(ctx, "📝 $who: ${e.title}", e.body, linkId = e.extra)

            EventType.MUSIC ->
                NotificationHelper.showInfo(ctx, "🎵 $who ke saath music suno", e.title.ifBlank { "Listen together invite" }, linkId = e.extra)
        }
    }

    private fun onSos(ctx: Context, scope: CoroutineScope, e: InboxEvent, who: String) {
        val message = e.body.ifBlank { "Emergency! Please help." }
        SosCenter.onSos(ctx, SosAlert(e.from, who, e.fromPhone, message, e.lat, e.lng, e.ts, e.extra))
        NotificationHelper.showSosNotification(ctx, who, message)
        log(ctx, scope, "SOS_RECEIVED", "SOS received from $who", "Alarm ringing")
        // Keep the process alive (siren + live location of the sender) after a push wakes us.
        SafetyService.start(ctx)
        // With "Display over other apps" granted the screen is taken over immediately,
        // otherwise the full-screen-intent notification above does it.
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)) {
                ctx.startActivity(NotificationHelper.sosIntent(ctx))
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun onAlarm(ctx: Context, scope: CoroutineScope, e: InboxEvent, who: String) {
        if (e.hour !in 0..23 || e.minute !in 0..59) return
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(ctx).reminderDao()
                val title = "From $who: ${e.title}"
                val exists = dao.getEnabledReminders().any { it.title == title && it.hour == e.hour && it.minute == e.minute }
                if (!exists) {
                    val note = ReminderNote(
                        title = title,
                        queryDetails = e.body.ifBlank { "Alarm set by $who" },
                        hour = e.hour,
                        minute = e.minute,
                        categoryTag = "Love Note",
                        isAlarmEnabled = true
                    )
                    val id = dao.insertReminder(note)
                    AlarmScheduler.scheduleAlarm(ctx, note.copy(id = id))
                }
                NotificationHelper.showInfo(
                    ctx, "⏰ $who ne alarm set kiya",
                    "${e.title} • ${String.format("%02d:%02d", e.hour, e.minute)}"
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun log(ctx: Context, scope: CoroutineScope, type: String, details: String, status: String) {
        scope.launch {
            try {
                AppDatabase.getDatabase(ctx).emergencyDao()
                    .insertEmergencyLog(EmergencyLog(triggerType = type, details = details, status = status))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
