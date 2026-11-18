package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import com.example.ui.theme.PanicRed
import com.example.util.SmsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmergencyScreen(
    config: EmergencyConfig,
    logs: List<EmergencyLog>,
    emergencyContacts: List<com.example.util.EmergencyContact>,
    onAddEmergency: (String, String) -> Unit,
    onRemoveEmergency: (String) -> Unit,
    liveSharing: Boolean,
    mapLink: String?,
    onToggleLiveSharing: (Boolean) -> Unit,
    onTriggerPanic: () -> Unit,
    shakeEnabled: Boolean = false,
    onToggleShake: (Boolean) -> Unit = {},
    safeDeadline: Long = 0L,
    onStartSafeWalk: (Int) -> Unit = {},
    onCancelSafeWalk: () -> Unit = {},
    onFakeCall: (Int, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var sirenOnly by remember { mutableStateOf(com.example.util.SirenPlayer.isPlaying) }
    var fakeName by remember { mutableStateOf("Mom") }
    // Live countdown for the Safe Walk timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(safeDeadline) {
        while (safeDeadline > 0L) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val partnerName = if (emergencyContacts.isNotEmpty()) emergencyContacts.joinToString(", ") { it.name }
    else config.partnerName.ifBlank { "your emergency contacts" }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Dedicated Panic Button Banner
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PanicRed
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🚨 EMERGENCY PANIC HUB",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "In case of any danger or emergency, press the button below to instantly alert $partnerName & sound alarm siren!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Huge Central Panic SOS Button
                    Button(
                        onClick = onTriggerPanic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = PanicRed
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale)
                            .testTag("hub_big_panic_button")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "PANIC SOS",
                                modifier = Modifier.size(48.dp),
                                tint = PanicRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PANIC SOS",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = PanicRed
                                )
                            )
                            Text(
                                text = "PRESS NOW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = PanicRed
                                )
                            )
                        }
                    }
                }
            }
        }

        // Live location sharing with emergency contacts
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📍 Share my live location", fontWeight = FontWeight.Bold)
                        Text(
                            if (liveSharing) "ON: jin logo ke saath share kiya hai wo mujhe live dekh sakte hain."
                            else "OFF: SOS dabane par location phir bhi chali jaati hai.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = liveSharing, onCheckedChange = onToggleLiveSharing)
                }
            }
        }

        // ── Women's safety toolkit ──
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🚶‍♀️ Safe Walk timer", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    if (safeDeadline > 0L) {
                        val left = ((safeDeadline - now) / 1000).coerceAtLeast(0)
                        Text(
                            String.format("%02d:%02d baaki — isse pehle \"I'm safe\" dabao, warna SOS khud chala jayega", left / 60, left % 60),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onCancelSafeWalk,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("✅ I'm safe — timer band karo", fontWeight = FontWeight.Bold) }
                    } else {
                        Text(
                            "Akeli ja rahi ho? Time chuno. Us time tak safe confirm nahi kiya to emergency contacts ko SOS + live location chali jayegi.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 60, 120).forEach { m ->
                                FilledTonalButton(
                                    onClick = { onStartSafeWalk(m) },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (m < 60) "$m min" else "${m / 60} hr", fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📳 Shake-to-SOS", fontWeight = FontWeight.Bold)
                            Text(
                                "Phone ko 4 baar zor se hilao — screen band ho tab bhi SOS chala jayega. (Galti se na ho, isliye pehle test karo.)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = shakeEnabled, onCheckedChange = onToggleShake)
                    }
                    HorizontalDivider()
                    Text("📞 Fake call (bahana banao aur nikal jao)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = fakeName, onValueChange = { fakeName = it },
                        label = { Text("Caller ka naam") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Abhi", 10 to "10 sec", 30 to "30 sec", 60 to "1 min").forEach { (sec, label) ->
                            OutlinedButton(
                                onClick = { onFakeCall(sec, fakeName) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(label, fontSize = 12.sp) }
                        }
                    }
                    HorizontalDivider()
                    Text("🔊 Loud siren (sirf awaaz, kisi ko alert nahi)", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (sirenOnly) {
                                com.example.util.SirenPlayer.stop(context)
                                sirenOnly = false
                            } else {
                                com.example.util.SirenPlayer.start(context)
                                sirenOnly = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sirenOnly) Color(0xFF455A64) else PanicRed
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (sirenOnly) "⏹ Siren band karo" else "▶ Siren bajao (silent mode me bhi)", fontWeight = FontWeight.Bold) }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = PanicRed.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("☎️ Helplines (India)", fontWeight = FontWeight.Bold)
                    val lines = listOf(
                        "112" to "Emergency", "1091" to "Women Helpline",
                        "181" to "Women in Distress", "100" to "Police"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lines.forEach { (num, label) ->
                            OutlinedButton(
                                onClick = { SmsHelper.makeEmergencyCall(context, num) },
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(num, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text(label, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Emergency contacts (one-way: anyone I add gets my SOS, no acceptance needed)
        item {
            EmergencyContactsCard(
                contacts = emergencyContacts,
                onAdd = onAddEmergency,
                onRemove = onRemoveEmergency,
                onCall = { phone -> SmsHelper.makeEmergencyCall(context, phone) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Call Helpline 112
            Button(
                onClick = {
                    SmsHelper.makeEmergencyCall(context, config.emergencyCallNumber)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("emergency_call_112")
            ) {
                Icon(imageVector = Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Call Helpline (${config.emergencyCallNumber})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Emergency Message Config Preview Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pre-configured SOS Alert Message",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "\"${config.customSosMessage}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sosWithLoc = "🚨 URGENT EMERGENCY SOS ALERT! 🚨\n${config.customSosMessage}" +
                                    (if (mapLink != null) "\n📍 My Live Google Maps Location:\n$mapLink" else "") +
                                    "\n[Sent with 1-Click from Cute Reminder SOS App]"
                                SmsHelper.launchWhatsApp(context, config.partnerPhone, sosWithLoc)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Click WhatsApp Location SOS 📍", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Emergency Activation Log History
        item {
            Text(
                text = "Emergency Event Logs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (logs.isEmpty()) {
            item {
                Text(
                    text = "No emergency alarms activated yet. You are safe! 🛡️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(logs, key = { it.id }) { log ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PanicRed.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🚨 ${log.triggerType}",
                                fontWeight = FontWeight.Bold,
                                color = PanicRed
                            )
                            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            Text(
                                text = sdf.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.details,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyContactsCard(
    contacts: List<com.example.util.EmergencyContact>,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            r.data?.data?.let { uri ->
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        phone = c.getString(0).orEmpty()
                        if (name.isBlank()) name = c.getString(1).orEmpty()
                    }
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🚨 Emergency contacts", fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(
                "Kisi ka bhi naam/number daalo — unhe accept nahi karna padta. SOS par sabko alert jata hai: " +
                    "jinke paas app hai aur login hai unka phone ring ke saath live location + aapka number dikhata hai, " +
                    "baaki sabko SMS + map link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (contacts.isEmpty()) {
                Text("⚠️ Abhi koi emergency contact nahi. Kam se kam 2 add karo.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            contacts.forEach { c ->
                val isAppUser by produceState<Boolean?>(initialValue = null, c.phone) {
                    value = if (com.example.cloud.CloudRepository.isConfigured && com.example.cloud.CloudRepository.uid != null)
                        com.example.cloud.CloudRepository.appUserUid(c.phone) != null else null
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.Bold)
                        Text(
                            c.phone + when (isAppUser) {
                                true -> "  •  ✅ app user: ring + live location"
                                false -> "  •  📩 SMS only"
                                null -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onCall(c.phone) }) { Icon(Icons.Default.Call, contentDescription = "Call") }
                    IconButton(onClick = { onRemove(c.phone) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Naam") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Mobile number") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("emergency_phone")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            pick.launch(
                                android.content.Intent(
                                    android.content.Intent.ACTION_PICK,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📇 Contacts se", fontSize = 13.sp) }
                Button(
                    onClick = {
                        onAdd(name, phone)
                        name = ""
                        phone = ""
                    },
                    enabled = phone.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag("emergency_add")
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
