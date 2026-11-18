package com.example.util

import android.content.Context
import com.example.cloud.CloudRepository
import com.example.cloud.EventType
import com.example.data.AppDatabase
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import com.example.service.SafetyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one place that actually raises / cancels an SOS. Used by the PANIC button, the shake
 * gesture and the Safe Walk timer, so all three behave identically (even with the app closed).
 *
 * Who is alerted: EVERY emergency contact (no acceptance needed).
 *  - has the app + logged in -> full-screen ring with live location and my number (cloud push)
 *  - everyone                -> SMS with a map link (works without internet / without the app)
 */
object SosSender {

    data class Result(val recipients: List<String>, val mapLink: String?, val smsTargets: Int)

    suspend fun trigger(context: Context, reason: String = "PANIC_BUTTON", fromUi: Boolean = true): Result {
        val app = context.applicationContext
        CloudRepository.init(app)
        val db = AppDatabase.getDatabase(app)
        val config = db.emergencyDao().getEmergencyConfig() ?: EmergencyConfig()
        val contacts = EmergencyContacts.load(app)

        LivePrefs.setMySosActive(app, true)
        SafetyService.start(app)
        if (config.sirenSoundEnabled) SirenPlayer.start(app)

        if (CloudRepository.isConfigured && CloudRepository.uid != null && CloudRepository.myEmergencyInfo.isBlank()) {
            runCatching { CloudRepository.loadMyProfile() }
        }

        val location = LocationHelper.getCurrent(app)
        val link = location?.let { LocationHelper.mapsLink(it.latitude, it.longitude) }

        // 1) Cloud: ring + live location on phones that have the app
        val reached = mutableListOf<String>()
        val reachedUids = mutableListOf<String>()
        val me = CloudRepository.uid
        if (CloudRepository.isConfigured && me != null) {
            location?.let { CloudRepository.publishLocation(it.latitude, it.longitude, it.accuracy, true) }
            contacts.forEach { c ->
                runCatching {
                    // permission document first, then the event (security rules check it)
                    CloudRepository.publishEmergencyContact(c.name, c.phone)
                    val target = CloudRepository.appUserUid(c.phone)
                    if (target != null && target != me) {
                        val ok = CloudRepository.sendEvent(
                            to = target,
                            type = EventType.SOS,
                            title = "SOS",
                            body = config.customSosMessage,
                            lat = location?.latitude,
                            lng = location?.longitude,
                            extra = CloudRepository.myEmergencyInfo
                        )
                        if (ok) {
                            reached.add(c.name)
                            reachedUids.add(target)
                        }
                    }
                }
            }
        }
        LivePrefs.setSosTargets(app, reachedUids)

        // 2) SMS to everyone (also the offline backup number)
        val sms = buildString {
            append("🚨 SOS! ").append(config.customSosMessage)
            if (link != null) append("\n📍 ").append(link)
            if (CloudRepository.myEmergencyInfo.isNotBlank()) append("\n").append(CloudRepository.myEmergencyInfo)
        }
        val phones = (contacts.map { it.phone } + config.partnerPhone).filter { it.isNotBlank() }.distinct()
        withContext(Dispatchers.Main) {
            phones.forEachIndexed { index, phone ->
                if (SmsHelper.hasSmsPermission(app)) SmsHelper.sendDirectSms(app, phone, sms)
                else if (index == 0 && fromUi) SmsHelper.launchSmsApp(app, phone, sms)
            }
        }

        db.emergencyDao().insertEmergencyLog(
            EmergencyLog(
                triggerType = reason,
                details = "SOS: ${contacts.size} emergency contact(s), ${reached.size} reached in-app, " +
                    "${phones.size} SMS" + (if (link != null) ", with location" else ", location unavailable"),
                status = "Triggered"
            )
        )

        if (!fromUi) NotificationHelper.showSosActive(app, reason)
        val names = if (reached.isNotEmpty()) reached else contacts.map { it.name }
        return Result(names, link, phones.size)
    }

    /** "I am safe": stop my siren and silence the alarm on every phone that got the SOS. */
    suspend fun markSafe(context: Context) {
        val app = context.applicationContext
        CloudRepository.init(app)
        SirenPlayer.stop(app)
        LivePrefs.setMySosActive(app, false)
        NotificationHelper.cancelSosActive(app)
        SafeTimer.cancel(app)
        if (CloudRepository.isConfigured && CloudRepository.uid != null) {
            LivePrefs.sosTargets(app).forEach { CloudRepository.sendEvent(to = it, type = EventType.SOS_SAFE) }
            CloudRepository.markLocationSosOff()
        }
        LivePrefs.setSosTargets(app, emptyList())
        AppDatabase.getDatabase(app).emergencyDao().insertEmergencyLog(
            EmergencyLog(triggerType = "SOS_SAFE", details = "I marked myself safe", status = "Closed")
        )
    }
}
