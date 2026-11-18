package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.CloudRepository
import com.example.cloud.EventType
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PanicRed
import com.example.util.NotificationHelper
import com.example.util.SmsHelper
import com.example.util.SosAlert
import com.example.util.SosCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen takeover shown when one of my people sends an SOS:
 * wakes the screen, shows over the lock screen, rings the alarm at full volume and
 * (best effort) pins itself so other apps cannot be opened until it is acknowledged.
 */
class SosAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Screen pinning: Android shows a one-time confirmation; afterwards other apps/home are blocked.
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                val alerts by SosCenter.alerts.collectAsState()
                BackHandler(enabled = true) { /* blocked on purpose until acknowledged */ }

                LaunchedEffect(alerts.isEmpty()) {
                    if (alerts.isEmpty()) closeScreen()
                }

                SosAlertContent(
                    alerts = alerts,
                    onAcknowledge = { acknowledge(alerts) },
                    onClose = {
                        SosCenter.clear(this)
                        NotificationHelper.cancelSosNotification(this)
                        closeScreen()
                    }
                )
            }
        }
    }

    private fun acknowledge(alerts: List<SosAlert>) {
        SosCenter.silence(this)
        CoroutineScope(Dispatchers.IO).launch {
            alerts.forEach {
                CloudRepository.sendEvent(to = it.fromUid, type = EventType.SOS_ACK)
            }
        }
    }

    private fun closeScreen() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            // not pinned
        }
        finish()
    }
}

@Composable
private fun SosAlertContent(
    alerts: List<SosAlert>,
    onAcknowledge: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var acknowledged by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "sos")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val flash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash"
    )
    val bg = if (acknowledged) Color(0xFF7A0000) else Color(0xFF8A0000).copy(alpha = 0.85f + 0.15f * flash)

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(110.dp).scale(if (acknowledged) 1f else pulse).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = PanicRed, modifier = Modifier.size(64.dp))
            }

            Text(
                "🚨 EMERGENCY SOS",
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
            )

            alerts.forEach { alert -> SosCard(alert) }

            Spacer(Modifier.height(4.dp))

            if (!acknowledged) {
                Button(
                    onClick = {
                        acknowledged = true
                        onAcknowledge()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PanicRed),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text("I SEE IT — STOP SIREN & TELL THEM I'M COMING", fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    "Siren band. Unhe bata diya gaya hai. Ab call karo ya map kholo.",
                    color = Color.White, textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Close this screen", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SosCard(alert: SosAlert) {
    val context = LocalContext.current
    // Live location (if they share with me) overrides the snapshot sent with the SOS.
    val live by remember(alert.fromUid) { CloudRepository.locationFlow(alert.fromUid) }
        .collectAsState(initial = null)
    val lat = live?.lat ?: alert.lat
    val lng = live?.lng ?: alert.lng
    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(alert.ts))

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(alert.fromName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            if (alert.fromPhone.isNotBlank()) Text(alert.fromPhone, color = Color.White.copy(alpha = 0.9f))
            Text("\"${alert.message}\"", color = Color.White)
            if (alert.info.isNotBlank()) {
                Text("🩺 ${alert.info}", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (lat != null && lng != null) {
                    "📍 %.5f, %.5f${if (live != null) "  (live)" else ""}  •  $time".format(lat, lng)
                } else "📍 Location not available  •  $time",
                color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp
            )

            if (lat != null && lng != null) {
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(alert.fromName)})")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("OPEN LIVE LOCATION IN MAPS", fontWeight = FontWeight.Bold)
                }
            }
            if (alert.fromPhone.isNotBlank()) {
                Button(
                    onClick = { SmsHelper.makeEmergencyCall(context, alert.fromPhone) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CALL ${alert.fromName.uppercase()}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
