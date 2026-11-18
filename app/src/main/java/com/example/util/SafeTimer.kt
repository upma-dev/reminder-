package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.service.SafetyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Safe Walk: "if I don't tap I'm safe within N minutes, send an SOS automatically".
 * Also switches live location sharing on, so my emergency contacts can follow me meanwhile.
 */
object SafeTimer {
    private const val REQ_FIRE = 7001
    private const val REQ_WARN = 7002

    private fun pi(context: Context, request: Int, warn: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, request,
            Intent(context, SafeTimerReceiver::class.java).putExtra("warn", warn),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun schedule(context: Context, at: Long, pending: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun start(context: Context, minutes: Int) {
        val app = context.applicationContext
        val deadline = System.currentTimeMillis() + minutes * 60_000L
        LivePrefs.setSafeDeadline(app, deadline)
        schedule(app, deadline, pi(app, REQ_FIRE, false))
        if (minutes > 1) schedule(app, deadline - 60_000L, pi(app, REQ_WARN, true))
        LivePrefs.setSharing(app, true)
        SafetyService.start(app)
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        LivePrefs.setSafeDeadline(app, 0L)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(app, REQ_FIRE, false))
        am.cancel(pi(app, REQ_WARN, true))
        NotificationHelper.cancelSafeWarning(app)
    }

    fun remainingMillis(context: Context): Long {
        val d = LivePrefs.safeDeadline(context)
        return if (d == 0L) 0L else (d - System.currentTimeMillis()).coerceAtLeast(0L)
    }
}

class SafeTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deadline = LivePrefs.safeDeadline(context)
        if (deadline == 0L) return // cancelled meanwhile

        if (intent.getBooleanExtra("warn", false)) {
            NotificationHelper.showSafeWarning(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LivePrefs.setSafeDeadline(context, 0L)
                SosSender.trigger(context, reason = "SAFE_TIMER", fromUi = false)
            } finally {
                pending.finish()
            }
        }
    }
}
