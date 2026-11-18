package com.example.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.NotificationHelper
import kotlinx.coroutines.delay

/** Looks like a real incoming call so you can leave an uncomfortable situation ("Mom is calling"). */
class FakeCallActivity : ComponentActivity() {

    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val caller = intent.getStringExtra("caller")?.ifBlank { null } ?: "Mom"
        startRinging()

        setContent {
            var accepted by remember { mutableStateOf(false) }
            var seconds by remember { mutableIntStateOf(0) }
            LaunchedEffect(accepted) {
                if (accepted) while (true) {
                    delay(1000)
                    seconds++
                }
            }

            Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            if (accepted) String.format("%02d:%02d", seconds / 60, seconds % 60) else "Incoming call…",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.size(110.dp).background(Color(0xFF2E3A46), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(caller.take(1).uppercase(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(16.dp))
                        Text(caller, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Mobile", color = Color.White.copy(alpha = 0.6f))
                    }

                    if (!accepted) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            FilledIconButton(
                                onClick = { finishCall() },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                            ) { Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White) }
                            FilledIconButton(
                                onClick = {
                                    stopRinging()
                                    accepted = true
                                },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF43A047))
                            ) { Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White) }
                        }
                    } else {
                        FilledIconButton(
                            onClick = { finishCall() },
                            modifier = Modifier.padding(bottom = 48.dp).size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                        ) { Icon(Icons.Default.CallEnd, contentDescription = "End", tint = Color.White) }
                    }
                }
            }
        }
    }

    private fun startRinging() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FakeCallActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            // already stopped
        }
        player = null
    }

    private fun finishCall() {
        stopRinging()
        NotificationHelper.cancelFakeCall(this)
        finish()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

object FakeCall {
    fun schedule(context: Context, seconds: Int, callerName: String) {
        val app = context.applicationContext
        if (seconds <= 0) {
            NotificationHelper.showFakeCall(app, callerName)
            return
        }
        val pi = PendingIntent.getBroadcast(
            app, NotificationHelper.FAKE_CALL_ID,
            Intent(app, FakeCallReceiver::class.java).putExtra("caller", callerName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + seconds * 1000L
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showFakeCall(context, intent.getStringExtra("caller") ?: "Mom")
    }
}
