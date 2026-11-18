package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.util.SosSender
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.cloud.CloudRepository
import com.example.cloud.EventType
import com.example.cloud.InboxEvent
import com.example.data.AppDatabase
import com.example.data.model.EmergencyLog
import com.example.data.model.ReminderNote
import com.example.util.ActiveChat
import com.example.util.AlarmScheduler
import com.example.util.LivePrefs
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.example.util.SosAlert
import com.example.util.SosCenter
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service: keeps a realtime listener on my cloud inbox so SOS, messages, reminders
 * and alarms from my people show up even when the app is closed. It also publishes my live
 * location while sharing / SOS is on.
 */
class SafetyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inbox: ListenerRegistration? = null
    private var locationCallback: LocationCallback? = null
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "shake_sos") updateShake() else restartLocationUpdates()
    }

    // ───────────── shake-to-SOS ─────────────
    private var sensorManager: SensorManager? = null
    private var shakeRegistered = false
    private var shakeCount = 0
    private var shakeFirst = 0L
    private var shakeLast = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val g = Math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()) / 9.81
            if (g < 2.7) return
            val now = System.currentTimeMillis()
            if (now - shakeLast < 350) return // one jolt = one count
            if (now - shakeFirst > 3000) {
                shakeCount = 0
                shakeFirst = now
            }
            shakeLast = now
            shakeCount++
            if (shakeCount >= 4) {
                shakeCount = 0
                onShake()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateShake() {
        val want = LivePrefs.isShakeEnabled(this)
        val sm = sensorManager ?: (getSystemService(Context.SENSOR_SERVICE) as SensorManager).also { sensorManager = it }
        if (want && !shakeRegistered) {
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(shakeListener, accel, SensorManager.SENSOR_DELAY_UI)
            shakeRegistered = true
        } else if (!want && shakeRegistered) {
            sm.unregisterListener(shakeListener)
            shakeRegistered = false
        }
    }

    private fun onShake() {
        if (LivePrefs.isMySosActive(this)) return
        scope.launch { SosSender.trigger(this@SafetyService, reason = "SHAKE", fromUi = false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CloudRepository.init(this)
        enterForeground()
        if (!CloudRepository.isConfigured || CloudRepository.uid == null) {
            stopSelf()
            return
        }
        scope.launch { runCatching { CloudRepository.loadMyProfile() } }
        inbox = CloudRepository.listenInbox { EventHandler.handle(this, scope, it) }
        LivePrefs.register(this, prefsListener)
        restartLocationUpdates()
        updateShake()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        inbox?.remove()
        LivePrefs.unregister(this, prefsListener)
        if (shakeRegistered) sensorManager?.unregisterListener(shakeListener)
        stopLocationUpdates()
        scope.cancel()
        super.onDestroy()
    }

    // ───────────── foreground ─────────────

    private fun enterForeground() {
        val notification = NotificationHelper.serviceNotification(this)
        val wantLocation = LocationHelper.hasPermission(this) &&
            (LivePrefs.isSharing(this) || LivePrefs.isMySosActive(this))
        try {
            ServiceCompat.startForeground(
                this, NotificationHelper.SERVICE_NOTIFICATION_ID, notification,
                foregroundType(wantLocation)
            )
        } catch (e: Exception) {
            // Location type refused (e.g. started from boot) -> fall back to the plain type.
            try {
                ServiceCompat.startForeground(
                    this, NotificationHelper.SERVICE_NOTIFICATION_ID, notification,
                    foregroundType(false)
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun foregroundType(withLocation: Boolean): Int {
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (withLocation) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            return type
        }
        return if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST else 0
    }

    // ───────────── live location ─────────────

    @Suppress("MissingPermission")
    private fun restartLocationUpdates() {
        stopLocationUpdates()
        val sos = LivePrefs.isMySosActive(this)
        if (!(LivePrefs.isSharing(this) || sos)) return
        if (!LocationHelper.hasPermission(this)) return

        val interval = if (sos) 5_000L else 30_000L
        val priority = if (sos) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    CloudRepository.publishLocation(
                        loc.latitude, loc.longitude, loc.accuracy, LivePrefs.isMySosActive(this@SafetyService)
                    )
                }
            }
        }
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
            locationCallback = callback
            enterForeground() // refresh foreground type to include location
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        locationCallback = null
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SafetyService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SafetyService::class.java))
        }
    }
}
