package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast

object SmsHelper {

    fun hasSmsPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun sendDirectSms(context: Context, phoneNumber: String, message: String): Boolean {
        if (phoneNumber.isBlank()) {
            Toast.makeText(context, "Phone number missing", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Toast.makeText(context, "SMS sent to $phoneNumber!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            // Fallback to launch system SMS app
            launchSmsApp(context, phoneNumber, message)
            false
        }
    }

    fun launchSmsApp(context: Context, phoneNumber: String, message: String) {
        try {
            val uri = Uri.parse("smsto:${phoneNumber.ifBlank { "" }}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch messaging app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchWhatsApp(context: Context, phoneNumber: String, message: String) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val url = if (cleanNumber.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}"
        } else {
            "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Send message via"))
        }
    }

    fun makeEmergencyCall(context: Context, phoneNumber: String) {
        val numberToCall = phoneNumber.ifBlank { "112" }
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$numberToCall")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // Permission missing, fallback to dialer
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numberToCall")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to place call: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
