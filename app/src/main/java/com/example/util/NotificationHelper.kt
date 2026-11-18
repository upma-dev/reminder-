package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.SosAlertActivity

object NotificationHelper {

    const val CHANNEL_ID = "cute_reminder_alarm_channel"
    const val LOVE_CHANNEL_ID = "cute_love_notes_channel"
    const val CHAT_CHANNEL_ID = "personal_chat_channel"
    const val SOS_CHANNEL_ID = "sos_emergency_channel"
    const val SERVICE_CHANNEL_ID = "safety_monitor_channel"

    const val SOS_NOTIFICATION_ID = 9001
    const val SERVICE_NOTIFICATION_ID = 9000

    const val EXTRA_OPEN_LINK_ID = "EXTRA_OPEN_LINK_ID"
    const val EXTRA_OPEN_LOCATION = "EXTRA_OPEN_LOCATION"

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel(context: Context) = createAllChannels(context)

    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = manager(context)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val notifAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val alarmAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders & Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Your routine reminders and alarms"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: soundUri, alarmAttrs)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(LOVE_CHANNEL_ID, "Love notes & nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nudges and reminders sent to you by your personal people"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 300, 100, 400)
                    setSound(soundUri, notifAttrs)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHAT_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Chat messages from your personal people"
                    setSound(soundUri, notifAttrs)
                }
            )
            // The siren itself is played by SirenPlayer on the alarm stream; this channel only carries the full-screen alert.
            nm.createNotificationChannel(
                NotificationChannel(SOS_CHANNEL_ID, "SOS emergency alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Takes over the screen when someone sends you an SOS"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(SERVICE_CHANNEL_ID, "Safety monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps the app listening for SOS, messages and reminders"
                    setShowBadge(false)
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openApp(context: Context, requestCode: Int, configure: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            configure()
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun uniqueId() = (System.currentTimeMillis() % 100000).toInt() + 10

    // ───────────── Foreground service ─────────────

    fun serviceNotification(context: Context): Notification {
        createAllChannels(context)
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Safety monitor is on")
            .setContentText("Listening for SOS, messages and reminders")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(context, 1))
            .build()
    }

    // ───────────── Local reminders ─────────────

    fun showAlarmNotification(context: Context, reminderId: Long, title: String, details: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ Reminder: $title")
            .setContentText(if (details.isNotBlank()) details else "It's time for your routine task!")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, reminderId.toInt()) { putExtra("EXTRA_REMINDER_ID", reminderId) })
            .addAction(
                0, "⏹ Stop alarm",
                PendingIntent.getBroadcast(
                    context, reminderId.toInt(),
                    Intent(context, com.example.receiver.StopAlarmReceiver::class.java)
                        .putExtra("notif_id", reminderId.toInt()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        manager(context).notify(reminderId.toInt(), builder.build())
    }

    // ───────────── Personal circle ─────────────

    fun showCuteLoveNoteNotification(context: Context, senderName: String, title: String, details: String) {
        createAllChannels(context)
        val builder = NotificationCompat.Builder(context, LOVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💖 $senderName")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (details.isBlank()) title else "$title\n$details"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, uniqueId()))
        manager(context).notify(uniqueId(), builder.build())
    }

    /** A reminder that a personal contact set for me: rings like an alarm. */
    fun showPersonalReminder(context: Context, fromName: String, title: String, details: String) {
        createAllChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ $fromName ka reminder: $title")
            .setContentText(details.ifBlank { "Reminder from $fromName" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details.ifBlank { title }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, uniqueId()))
        manager(context).notify(uniqueId(), builder.build())
    }

    fun showMessage(context: Context, fromName: String, text: String, linkId: String) {
        createAllChannels(context)
        val builder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💬 $fromName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, linkId.hashCode()) { putExtra(EXTRA_OPEN_LINK_ID, linkId) })
        manager(context).notify(linkId.hashCode(), builder.build())
    }

    fun showInfo(context: Context, title: String, text: String, linkId: String = "", openLocation: Boolean = false) {
        createAllChannels(context)
        val builder = NotificationCompat.Builder(context, LOVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, uniqueId()) {
                if (linkId.isNotBlank()) putExtra(EXTRA_OPEN_LINK_ID, linkId)
                if (openLocation) putExtra(EXTRA_OPEN_LOCATION, true)
            })
        manager(context).notify(uniqueId(), builder.build())
    }

    // ───────────── SOS takeover ─────────────

    fun sosIntent(context: Context): Intent =
        Intent(context, SosAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    fun showSosNotification(context: Context, fromName: String, message: String) {
        createAllChannels(context)
        val fullScreen = PendingIntent.getActivity(
            context, SOS_NOTIFICATION_ID, sosIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 SOS from $fromName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        manager(context).notify(SOS_NOTIFICATION_ID, builder.build())
    }

    fun cancelSosNotification(context: Context) = manager(context).cancel(SOS_NOTIFICATION_ID)

    // ───────────── My own SOS (shake / safe timer) ─────────────

    private const val SOS_ACTIVE_ID = 9002
    private const val SAFE_WARN_ID = 9003
    const val FAKE_CALL_ID = 9004

    /** Shown when my SOS was started without opening the app (shake gesture / Safe Walk timer). */
    fun showSosActive(context: Context, reason: String) {
        createAllChannels(context)
        val why = if (reason == "SAFE_TIMER") "Safe Walk timer khatam hua" else "Shake se SOS shuru hua"
        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 SOS bheja ja chuka hai")
            .setContentText("$why. Tap karke \"I am safe\" dabao agar sab theek hai.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp(context, SOS_ACTIVE_ID))
        manager(context).notify(SOS_ACTIVE_ID, builder.build())
    }

    fun cancelSosActive(context: Context) = manager(context).cancel(SOS_ACTIVE_ID)

    /** One minute before the Safe Walk timer sends an SOS. */
    fun showSafeWarning(context: Context) {
        createAllChannels(context)
        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏳ Safe Walk 1 minute me khatam")
            .setContentText("Tap karke \"I am safe\" dabao, warna SOS chala jayega.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, SAFE_WARN_ID))
        manager(context).notify(SAFE_WARN_ID, builder.build())
    }

    fun cancelSafeWarning(context: Context) = manager(context).cancel(SAFE_WARN_ID)

    // ───────────── Fake call ─────────────

    fun showFakeCall(context: Context, callerName: String) {
        createAllChannels(context)
        val intent = Intent(context, com.example.ui.FakeCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("caller", callerName)
        }
        val pi = PendingIntent.getActivity(
            context, FAKE_CALL_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, SOS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
        manager(context).notify(FAKE_CALL_ID, builder.build())
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // full-screen intent handles it
        }
    }

    fun cancelFakeCall(context: Context) = manager(context).cancel(FAKE_CALL_ID)
}
