package com.example.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.example.cloud.Profile
import com.example.data.model.EmergencyConfig
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.example.util.SosAlert
import com.example.util.SosCenter

@Composable
fun SettingsScreen(
    config: EmergencyConfig,
    profile: Profile?,
    onSaveConfig: (EmergencyConfig) -> Unit,
    onSignOut: () -> Unit,
    onSaveProfile: (com.example.cloud.ProfileDetails) -> Unit = {}
) {
    val context = LocalContext.current
    var showEditProfile by remember { mutableStateOf(false) }

    if (showEditProfile && profile != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEditProfile = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("Edit profile", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    ProfileForm(
                        initial = profile,
                        authEmail = profile.email,
                        busy = false,
                        saveLabel = "Save"
                    ) {
                        onSaveProfile(it)
                        showEditProfile = false
                    }
                }
            }
        }
    }

    var partnerName by remember(config) { mutableStateOf(config.partnerName) }
    var partnerPhone by remember(config) { mutableStateOf(config.partnerPhone) }
    var customSosMessage by remember(config) { mutableStateOf(config.customSosMessage) }
    var emergencyCallNumber by remember(config) { mutableStateOf(config.emergencyCallNumber) }
    var sirenEnabled by remember(config) { mutableStateOf(config.sirenSoundEnabled) }

    // Re-evaluate permission statuses whenever we come back from a system settings screen.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    val pkgUri = Uri.parse("package:${context.packageName}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.name.orEmpty(), 52, photoB64 = profile?.photoB64)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.name.orEmpty().ifBlank { "Offline mode" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(profile?.phone.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
                if (profile != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(onClick = { showEditProfile = true }) { Text("Edit") }
                        TextButton(onClick = onSignOut) { Text("Logout") }
                    }
                }
            }
        }

        // Reliability & permissions checklist
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("SOS & Alerts setup checklist", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    "Sab ✅ hone par hi SOS lock-screen par poori screen le leta hai aur app band hone par bhi alarm bajta hai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // tick is read so this block recomposes after returning from settings
                val refresh = tick

                val notifOk = if (Build.VERSION.SDK_INT >= 33)
                    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                else true
                CheckRow("Notifications", notifOk, refresh) {
                    if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }

                CheckRow("Location permission", LocationHelper.hasPermission(context), refresh) {
                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                CheckRow("GPS / Location ON", LocationHelper.isGpsEnabled(context), refresh) {
                    open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

                CheckRow("Location 'Allow all the time' (background)", LocationHelper.hasBackgroundPermission(context), refresh) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    } else {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                    }
                }

                if (Build.VERSION.SDK_INT >= 34) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    CheckRow("Full-screen SOS alerts", nm.canUseFullScreenIntent(), refresh) {
                        open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri))
                    }
                }

                CheckRow(
                    "Display over other apps (instant SOS takeover)",
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
                    refresh
                ) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)) }

                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                CheckRow(
                    "Battery: unrestricted (app band hone par bhi chale)",
                    pm.isIgnoringBatteryOptimizations(context.packageName),
                    refresh
                ) { open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)) }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    CheckRow("Exact alarms", am.canScheduleExactAlarms(), refresh) {
                        open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
                    }
                }

                CheckRow("SMS & Call (offline SOS backup)", com.example.util.SmsHelper.hasSmsPermission(context), refresh) {
                    permLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE))
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        // Rehearsal of what an emergency contact will see
                        SosCenter.onSos(
                            context,
                            SosAlert("test", "Test Contact", "", "This is a TEST SOS. Siren will ring.", null, null, System.currentTimeMillis())
                        )
                        NotificationHelper.showSosNotification(context, "Test Contact", "This is a TEST SOS")
                        context.startActivity(NotificationHelper.sosIntent(context))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Test SOS takeover screen 🚨") }
            }
        }

        // Privacy
        var blockShots by remember { mutableStateOf(com.example.util.ScreenshotPolicy.isBlocked(context)) }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔒 Screenshot / recording block", fontWeight = FontWeight.Bold)
                    Text(
                        "ON: photo, chat aur location ka screenshot ya screen-record nahi hoga, aur recent-apps me preview chhupa rahega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = blockShots,
                    onCheckedChange = {
                        blockShots = it
                        com.example.util.ScreenshotPolicy.setBlocked(context, it)
                    }
                )
            }
        }

        // Offline backup contact
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Offline backup contact (SMS)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    "Is number ko SOS par SMS + map link jata hai, chahe unke paas app ya internet na ho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("partner_name_input")
                )
                OutlinedTextField(
                    value = partnerPhone,
                    onValueChange = { partnerPhone = it },
                    label = { Text("Phone number") },
                    placeholder = { Text("e.g. +91 9876543210") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("partner_phone_input")
                )
            }
        }

        // Emergency SOS Message Settings
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Emergency Panic Message", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                OutlinedTextField(
                    value = customSosMessage,
                    onValueChange = { customSosMessage = it },
                    label = { Text("Custom SOS Alert Message") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sos_message_input")
                )
                OutlinedTextField(
                    value = emergencyCallNumber,
                    onValueChange = { emergencyCallNumber = it },
                    label = { Text("Emergency Helpline Call Number") },
                    placeholder = { Text("e.g. 112 or 108") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Play siren on my phone when I press PANIC", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = sirenEnabled, onCheckedChange = { sirenEnabled = it })
                }
            }
        }

        Button(
            onClick = {
                onSaveConfig(
                    EmergencyConfig(
                        id = 1,
                        partnerName = partnerName.trim(),
                        partnerPhone = partnerPhone.trim(),
                        customSosMessage = customSosMessage.trim(),
                        emergencyCallNumber = emergencyCallNumber.trim(),
                        sirenSoundEnabled = sirenEnabled
                    )
                )
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_settings_button")
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Save Settings ✨", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, @Suppress("UNUSED_PARAMETER") refresh: Int, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (if (ok) "✅ " else "⚠️ ") + label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (!ok) TextButton(onClick = onFix) { Text("Fix") }
    }
}
