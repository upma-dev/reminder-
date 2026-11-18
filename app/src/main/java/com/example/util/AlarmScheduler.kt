package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.ReminderNote
import com.example.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_REMINDER_ID", reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /** Schedules the next occurrence. The receiver re-schedules itself after firing (daily repeat). */
    fun scheduleAlarm(context: Context, reminder: ReminderNote) {
        if (!reminder.isAlarmEnabled) {
            cancelAlarm(context, reminder.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(reminder.hour, reminder.minute)
        val pi = pendingIntent(context, reminder.id)

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminderId))
    }
}
