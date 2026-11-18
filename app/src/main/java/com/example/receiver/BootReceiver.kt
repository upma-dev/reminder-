package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.cloud.CloudRepository
import com.example.data.AppDatabase
import com.example.service.SafetyService
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** After reboot / app update: restore all alarms and restart the safety monitor. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CloudRepository.init(context)
                AppDatabase.getDatabase(context).reminderDao().getEnabledReminders()
                    .forEach { AlarmScheduler.scheduleAlarm(context, it) }
                if (CloudRepository.uid != null) SafetyService.start(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
