package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EmergencyConfig
import com.example.ui.theme.PanicRed
import com.example.util.SmsHelper

@Composable
fun EmergencySosDialog(
    config: EmergencyConfig,
    recipients: List<String>,
    mapLink: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "siren_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF880000)),
            color = Color(0xFF990000)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close SOS",
                            tint = Color.White
                        )
                    }
                }

                // Center SOS Alert Badge
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(PanicRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Emergency Siren",
                                tint = PanicRed,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "EMERGENCY SOS ACTIVATED!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (recipients.isEmpty()) "Alarm bajh raha hai. Koi emergency contact set nahi hai!"
                        else "Alert + live location bheji: ${recipients.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message Box
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${config.customSosMessage}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1-Click WhatsApp Location SOS Button
                    Button(
                        onClick = {
                            val sosWithLoc = "🚨 URGENT EMERGENCY SOS ALERT! 🚨\n${config.customSosMessage}" +
                                (if (mapLink != null) "\n📍 My Live Google Maps Location:\n$mapLink" else "")
                            SmsHelper.launchWhatsApp(context, config.partnerPhone, sosWithLoc)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("whatsapp_location_sos_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1-CLICK WHATSAPP LOCATION SOS 📍",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Call backup contact
                    Button(
                        onClick = {
                            SmsHelper.makeEmergencyCall(context, config.partnerPhone.ifBlank { config.emergencyCallNumber })
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = PanicRed
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("call_partner_button")
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CALL ${config.partnerName.ifBlank { "BACKUP CONTACT" }.uppercase()} NOW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Call Emergency Helpline (112)
                    OutlinedButton(
                        onClick = {
                            SmsHelper.makeEmergencyCall(context, config.emergencyCallNumber)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("call_emergency_112_button")
                    ) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CALL EMERGENCY (${config.emergencyCallNumber})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    // Send SMS again
                    Button(
                        onClick = {
                            val msg = "${config.customSosMessage}${if (mapLink != null) "\n📍 $mapLink" else ""}\n[URGENT SOS ALERT]"
                            SmsHelper.launchSmsApp(context, config.partnerPhone, msg)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PanicRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Resend SMS Alert", fontWeight = FontWeight.SemiBold)
                    }

                    // Dismiss / I am Safe
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "I AM SAFE NOW (STOP ALARM ON ALL PHONES)",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
