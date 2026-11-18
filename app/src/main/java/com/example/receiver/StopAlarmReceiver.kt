package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.util.SirenPlayer

/** "Stop alarm" button on a reminder notification. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SirenPlayer.stop(context)
        val id = intent.getIntExtra("notif_id", -1)
        if (id >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
        }
    }
}
