package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An SOS that someone else sent to me. */
data class SosAlert(
    val fromUid: String,
    val fromName: String,
    val fromPhone: String,
    val message: String,
    val lat: Double?,
    val lng: Double?,
    val ts: Long,
    /** Blood group / medical note / address the sender stored in their profile. */
    val info: String = ""
)

/** Shared state between the background service and the full-screen SOS activity. */
object SosCenter {
    private val _alerts = MutableStateFlow<List<SosAlert>>(emptyList())
    val alerts: StateFlow<List<SosAlert>> = _alerts.asStateFlow()

    fun onSos(context: Context, alert: SosAlert) {
        _alerts.update { list -> list.filterNot { it.fromUid == alert.fromUid } + alert }
        SirenPlayer.start(context)
    }

    fun onSafe(context: Context, fromUid: String) {
        _alerts.update { list -> list.filterNot { it.fromUid == fromUid } }
        if (_alerts.value.isEmpty()) SirenPlayer.stop(context)
    }

    /** Stops the siren but keeps the alerts visible until the person marks them handled. */
    fun silence(context: Context) = SirenPlayer.stop(context)

    fun clear(context: Context) {
        _alerts.value = emptyList()
        SirenPlayer.stop(context)
    }
}

/** Which chat is currently on screen (so we don't notify for it). */
object ActiveChat {
    @Volatile
    var linkId: String? = null
}

/** Small persisted flags shared by UI and the background service. */
object LivePrefs {
    private const val FILE = "live_prefs"
    private const val KEY_SHARING = "live_sharing"
    private const val KEY_SOS = "my_sos_active"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isSharing(context: Context) = prefs(context).getBoolean(KEY_SHARING, false)
    fun setSharing(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SHARING, on).apply()

    fun isShakeEnabled(context: Context) = prefs(context).getBoolean("shake_sos", false)
    fun setShakeEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("shake_sos", on).apply()

    /** Epoch millis when the Safe Walk timer fires an SOS; 0 = no timer. */
    fun safeDeadline(context: Context) = prefs(context).getLong("safe_deadline", 0L)
    fun setSafeDeadline(context: Context, millis: Long) = prefs(context).edit().putLong("safe_deadline", millis).apply()

    /** uids of the app users my last SOS reached (so "I am safe" can silence them). */
    fun sosTargets(context: Context): List<String> =
        (prefs(context).getString("sos_targets", "") ?: "").split(",").filter { it.isNotBlank() }
    fun setSosTargets(context: Context, uids: List<String>) =
        prefs(context).edit().putString("sos_targets", uids.joinToString(",")).apply()

    fun isMySosActive(context: Context) = prefs(context).getBoolean(KEY_SOS, false)
    fun setMySosActive(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SOS, on).apply()

    fun register(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).registerOnSharedPreferenceChangeListener(l)

    fun unregister(context: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).unregisterOnSharedPreferenceChangeListener(l)
}
