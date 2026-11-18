package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cloud.CloudRepository
import com.example.cloud.EventType
import com.example.data.AppDatabase
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import com.example.util.SirenPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("EXTRA_REMINDER_ID", -1L)
        if (reminderId < 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = AppDatabase.getDatabase(context).reminderDao().getReminderById(reminderId)
                if (reminder == null) return@launch

                NotificationHelper.createNotificationChannel(context)
                NotificationHelper.showAlarmNotification(
                    context = context,
                    reminderId = reminder.id,
                    title = reminder.title,
                    details = reminder.queryDetails
                )
                // Real alarm ring on the ALARM stream: audible even in silent mode (30 s, stoppable).
                SirenPlayer.startTimed(context, 30_000L)

                // Personal circle: forward this reminder to the person chosen on it.
                if (reminder.notifyUid.isNotBlank()) {
                    CloudRepository.sendEvent(
                        to = reminder.notifyUid,
                        type = EventType.REMINDER,
                        title = reminder.title,
                        body = reminder.queryDetails
                    )
                }

                // Daily repeat
                if (reminder.isAlarmEnabled) AlarmScheduler.scheduleAlarm(context, reminder)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
