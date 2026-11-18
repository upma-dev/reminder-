package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.ProfileDetails
import com.example.ui.viewmodel.AuthState
import kotlinx.coroutines.delay

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ───────────────────────── Shared pieces ─────────────────────────

@Composable
private fun PlainShell(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(46.dp)) }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cute Reminder & SOS",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/** Shown when google-services.json is missing, so the app never crashes on a half set-up Firebase. */
@Composable
fun CloudSetupRequiredScreen(onContinueOffline: () -> Unit) {
    PlainShell {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cloud setup baaki hai", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Login, SOS alerts, chat aur live location ke liye Firebase chahiye. " +
                        "Project me app/google-services.json daalo (steps README me hain), phir app dobara build karo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
                    Text("Abhi sirf Routines use karo (offline)")
                }
            }
        }
    }
}

// ───────────────────────── Login (animated hero) ─────────────────────────

private data class Slide(val emoji: String, val title: String, val sub: String, val top: Color, val bottom: Color)

private val slides = listOf(
    Slide("🛡️", "Aapki safety, sabse pehle", "Ek tap SOS: aapke apne log, live location ke saath, turant alert.", Color(0xFFB0285A), Color(0xFFFF7A9C)),
    Slide("🚶‍♀️", "Safe Walk & Shake SOS", "Ghar pahunchne ka timer lagao, ya phone 4 baar hilao — SOS khud chala jayega.", Color(0xFF5B2A86), Color(0xFFB57EDC)),
    Slide("🚨", "Silent par bhi bajne wala alarm", "Siren full volume par bajta hai, phone silent ho tab bhi.", Color(0xFFC62828), Color(0xFFFF8A65)),
    Slide("💞", "Apno ke saath rehna", "Chat, ek-dusre ke liye alarm, saath me music aur reminders.", Color(0xFF00695C), Color(0xFF4DB6AC)),
    Slide("📍", "Live location, aapke control me", "Sirf unhe dikhti hai jinhe aap chunte ho. Kabhi bhi band karo.", Color(0xFF1565C0), Color(0xFF64B5F6))
)

@Composable
private fun HeroCarousel(modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            index = (index + 1) % slides.size
        }
    }
    val transition = rememberInfiniteTransition(label = "float")
    val bob by transition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier) {
        Crossfade(targetState = index, animationSpec = tween(900), label = "slide") { i ->
            val s = slides[i]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(s.top, s.bottom)))
            ) {
                // soft decorative circles
                Box(Modifier.offset(x = (-60).dp, y = 40.dp).size(220.dp).alpha(pulse).background(Color.White, CircleShape))
                Box(Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-30).dp).size(200.dp).alpha(pulse).background(Color.White, CircleShape))
                Box(Modifier.align(Alignment.BottomEnd).offset(x = 30.dp, y = 60.dp).size(150.dp).alpha(pulse).background(Color.White, CircleShape))

                Column(
                    Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(bottom = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.offset(y = bob.dp).size(120.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(s.emoji, fontSize = 60.sp) }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        s.title, color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(s.sub, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
        // page dots
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slides.indices.forEach { i ->
                Box(
                    Modifier
                        .height(7.dp)
                        .width(if (i == index) 22.dp else 7.dp)
                        .background(Color.White.copy(alpha = if (i == index) 1f else 0.5f), RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    state: AuthState,
    onSendOtp: (Activity, String) -> Unit,
    onVerifyOtp: (String) -> Unit,
    onChangeNumber: () -> Unit,
    onGoogle: (Activity) -> Unit
) {
    val activity = LocalContext.current.findActivity()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        HeroCarousel(Modifier.fillMaxWidth().weight(0.46f))

        // Sheet overlapping the hero
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().weight(0.54f).offset(y = (-28).dp)
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Welcome 💖", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text(
                    "Google se login karo. Uske baad apna mobile number daalna hoga, jisse aapke apne log aapko dhundh sakein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusLines(state)

                OutlinedButton(
                    onClick = { activity?.let(onGoogle) },
                    enabled = !state.busy && activity != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("google_sign_in")
                ) {
                    Text("G", fontWeight = FontWeight.Black, color = Color(0xFF4285F4), fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Google se Sign up / Login", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Apna sahi mobile number hi daalna, taaki SOS sahi logon tak jaye.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── First-time profile ─────────────────────────

@Composable
fun ProfileSetupScreen(
    state: AuthState,
    authEmail: String,
    onSaveProfile: (ProfileDetails) -> Unit,
    onSendOtp: (Activity, String) -> Unit,
    onVerifyOtp: (String) -> Unit,
    onChangeNumber: () -> Unit,
    onSavePhone: (String) -> Unit,
    onSignOut: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (state.needsPhone) "Step 1 of 2 • Mobile number" else "Step 2 of 2 • Aapki details",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
            )
            Text("Profile poora karo", fontWeight = FontWeight.Black, fontSize = 26.sp)

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.needsPhone) {
                        Text("Apna mobile number daalo — SOS isi number se jude logon tak jata hai.")
                        ManualPhoneSection(state, onSavePhone)
                    } else {
                        Text("📱 Mobile: ${state.profile?.phone.orEmpty()}", fontWeight = FontWeight.SemiBold)
                        ProfileForm(
                            initial = state.profile,
                            authEmail = authEmail,
                            busy = state.busy,
                            saveLabel = "Save & Continue",
                            onSave = onSaveProfile
                        )
                        StatusLines(state)
                    }
                }
            }
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Dusre account se login karo")
            }
        }
    }
}

@Composable
private fun ManualPhoneSection(state: AuthState, onSavePhone: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Mobile number") },
        placeholder = { Text("98765 43210") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("manual_phone_input")
    )
    Button(
        onClick = { onSavePhone(phone) },
        enabled = phone.isNotBlank() && !state.busy,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_phone_button")
    ) {
        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else Text("Aage badho", fontWeight = FontWeight.Bold)
    }
    StatusLines(state)
}

@Composable
private fun PhoneOtpSection(
    state: AuthState,
    onSendOtp: (Activity, String) -> Unit,
    onVerifyOtp: (String) -> Unit,
    onChangeNumber: () -> Unit,
    buttonLabel: String
) {
    val activity = LocalContext.current.findActivity()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    if (!state.otpSent) {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile number") },
            placeholder = { Text("98765 43210 ya +91 98765 43210") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("login_phone_input")
        )
        Button(
            onClick = { activity?.let { onSendOtp(it, phone) } },
            enabled = phone.isNotBlank() && !state.busy && activity != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("send_otp_button")
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text(buttonLabel, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
            label = { Text("6 digit OTP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("otp_input")
        )
        Button(
            onClick = { onVerifyOtp(code) },
            enabled = code.length == 6 && !state.busy,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify_otp_button")
        ) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Verify & Continue", fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onChangeNumber) { Text("Number badlo") }
            TextButton(onClick = { activity?.let { onSendOtp(it, phone) } }) { Text("OTP dobara bhejo") }
        }
    }
    StatusLines(state)
}

@Composable
private fun StatusLines(state: AuthState) {
    state.info?.let {
        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
    }
}
