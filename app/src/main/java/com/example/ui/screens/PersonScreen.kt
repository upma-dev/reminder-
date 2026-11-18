package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import com.example.util.ChatPrefs
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.ChatMessage
import com.example.cloud.CloudRepository
import com.example.cloud.Contact
import com.example.data.model.SujalTaskMessage
import com.example.ui.viewmodel.ListenState
import com.example.util.ActiveChat
import com.example.util.LocationHelper
import com.example.util.SmsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.input.KeyboardType
import com.example.util.MusicLibrary

private enum class PersonTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHAT("Chat", Icons.Default.Chat),
    REMIND("Remind", Icons.Default.Alarm),
    TASKS("Tasks", Icons.Default.CheckCircle),
    MUSIC("Music", Icons.Default.MusicNote),
    LOCATION("Location", Icons.Default.LocationOn)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    contact: Contact,
    history: List<SujalTaskMessage>,
    listenState: ListenState,
    onBack: () -> Unit,
    onOpenMusic: () -> Unit,
    onCloseMusic: () -> Unit,
    onSendChat: (String) -> Unit,
    onNudge: (String, String) -> Unit,
    onReminderNow: (String, String) -> Unit,
    onAlarm: (String, String, Int, Int) -> Unit,
    onAskLocation: () -> Unit,
    onShareLocation: (Boolean) -> Unit,
    onMusicLoad: (String, String) -> Unit,
    onMusicToggle: () -> Unit,
    onMusicSeek: (Long) -> Unit,
    onMusicInvite: (String) -> Unit,
    onMusicSolo: (java.io.File, String) -> Unit,
    onMusicExitSolo: () -> Unit,
    startTab: Int = 0
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(startTab) }
    BackHandler(onBack = onBack)

    DisposableEffect(contact.linkId) {
        onOpenMusic()
        onDispose { onCloseMusic() }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                Avatar(contact.name, 42, uid = contact.uid)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}")))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) { Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary) }
            }
        }

        TabRow(selectedTabIndex = tab) {
            PersonTab.values().forEachIndexed { index, t ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(t.title, fontSize = 12.sp) },
                    icon = { Icon(t.icon, contentDescription = t.title, modifier = Modifier.size(20.dp)) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (PersonTab.values()[tab]) {
                PersonTab.CHAT -> ChatTab(contact, onSendChat)
                PersonTab.REMIND -> RemindTab(contact, history, onNudge, onReminderNow, onAlarm)
                PersonTab.TASKS -> TasksTab(contact)
                PersonTab.MUSIC -> MusicTab(contact, listenState, onMusicLoad, onMusicToggle, onMusicSeek, onMusicInvite, onMusicSolo, onMusicExitSolo)
                PersonTab.LOCATION -> LocationTab(contact, onAskLocation, onShareLocation)
            }
        }
    }
}

// ───────────────────────────── Chat ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTab(contact: Contact, onSend: (String) -> Unit) {
    val context = LocalContext.current
    val myUid = CloudRepository.uid
    val allMessages by remember(contact.linkId) { CloudRepository.messagesFlow(contact.linkId) }
        .collectAsState(initial = emptyList<ChatMessage>())
    var clearedAt by remember(contact.linkId) { mutableLongStateOf(ChatPrefs.clearedAt(context, contact.linkId)) }
    val messages = allMessages.filter { it.ts > clearedAt }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    DisposableEffect(contact.linkId) {
        ActiveChat.linkId = contact.linkId
        onDispose { if (ActiveChat.linkId == contact.linkId) ActiveChat.linkId = null }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    // Selected message actions (long-press)
    selected?.let { m ->
        val mine = m.from == myUid
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Message") },
            text = { Text(m.text, maxLines = 6) },
            confirmButton = {
                if (mine) TextButton(onClick = {
                    scope.launch { CloudRepository.deleteMessage(contact.linkId, m.id) }
                    selected = null
                }) { Text("Delete for everyone", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("message", m.text))
                    selected = null
                }) { Text("Copy") }
            }
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Meri saari messages delete?") },
            text = { Text("Aapke bheje saare messages dono ke phone se hat jayenge. ${contact.name} ke messages nahi hatenge.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { CloudRepository.deleteMyMessages(contact.linkId) }
                    confirmDeleteAll = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Chat menu: save / clear / delete
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Chat options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("💾 Chat save karo (text file / share)") },
                        onClick = {
                            menuOpen = false
                            val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            val body = messages.joinToString("\n") {
                                "[${fmt.format(Date(it.ts))}] ${if (it.from == myUid) "Me" else contact.name}: ${it.text}"
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Chat with ${contact.name}")
                                putExtra(Intent.EXTRA_TEXT, body.ifBlank { "(empty chat)" })
                            }
                            context.startActivity(Intent.createChooser(send, "Save / share chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🧹 Chat saaf karo (sirf mere phone se)") },
                        onClick = {
                            menuOpen = false
                            ChatPrefs.clearNow(context, contact.linkId)
                            clearedAt = System.currentTimeMillis()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🗑️ Meri saari messages delete (sabke liye)") },
                        onClick = {
                            menuOpen = false
                            confirmDeleteAll = true
                        }
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Say hi to ${contact.name} 👋",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                val mine = m.from == myUid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (mine) 18.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 18.dp
                        ),
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(onClick = {}, onLongClick = { selected = m })
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                m.text,
                                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            if (m.text.contains("http")) {
                                val url = m.text.split(" ", "\n").firstOrNull { it.startsWith("http") }
                                if (url != null) {
                                    Text(
                                        "Open link ↗",
                                        color = if (mine) Color.White else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    )
                                }
                            }
                            Text(
                                timeFmt.format(Date(m.ts)),
                                fontSize = 10.sp,
                                color = (if (mine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.75f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message likho...") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.weight(1f).testTag("chat_input")
            )
            FilledIconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(52.dp).testTag("chat_send")
            ) { Icon(Icons.Default.Send, contentDescription = "Send") }
        }
    }
}

// ───────────────────────────── Remind ─────────────────────────────

private val loveNudges = listOf(
    "Miss You So Much! 🥺" to "Thinking of you right now!",
    "Love You 3000! ❤️" to "Sending you a tight hug!",
    "Did You Eat Food? 🥗" to "Please don't skip your meal today!",
    "Drink Water Now! 🥤" to "Stay hydrated, drink a glass of water!",
    "Good Night 😴" to "Sleep tight, sweet dreams!",
    "Take a Break ☕" to "Rest for 10 minutes!"
)

private val presets = listOf(
    "Drink Water 💧" to "Please drink a big glass of water right now!",
    "Eat Lunch 🥗" to "Time to enjoy your meal. Don't skip food!",
    "Call Me 📞" to "Whenever you're free, give me a quick call!",
    "Important Task 📌" to "Friendly reminder to finish your scheduled work!"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindTab(
    contact: Contact,
    history: List<SujalTaskMessage>,
    onNudge: (String, String) -> Unit,
    onReminderNow: (String, String) -> Unit,
    onAlarm: (String, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(20) }
    var minute by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val mine = history.filter { it.recipientPhone == contact.phone }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1-tap love nudge (unke phone par tune + vibration) 💖",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(loveNudges) { (t, b) ->
                            Button(
                                onClick = { onNudge(t, b) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text(t, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { (t, b) ->
                    SuggestionChip(onClick = { title = t; note = b }, label = { Text(t) })
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reminder / Alarm for ${contact.name}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Title") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("person_task_title")
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("Note (optional)") }, minLines = 2, maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { showPicker = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Alarm, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Alarm time: ${formatTime(hour, minute)}")
                    }
                    Button(
                        onClick = { onAlarm(title, note, hour, minute); title = ""; note = "" },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("send_alarm_button")
                    ) { Text("Set alarm on ${contact.name}'s phone ⏰", fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = { onReminderNow(title, note); title = ""; note = "" },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text("Send reminder now 📌", fontWeight = FontWeight.Bold) }

                    HorizontalDivider()
                    Text("Agar unke phone me internet nahi:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                SmsHelper.launchWhatsApp(context, contact.phone, "📌 $title\n$note")
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("WhatsApp", fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { SmsHelper.launchSmsApp(context, contact.phone, "📌 $title\n$note") },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("SMS", fontSize = 12.sp) }
                    }
                }
            }
        }

        item { Text("Sent history", fontWeight = FontWeight.Bold) }
        if (mine.isEmpty()) {
            item { Text("Abhi kuch nahi bheja ✨", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(mine, key = { it.id }) { task ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text(task.taskTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (task.noteText.isNotBlank()) Text(task.noteText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        (if (task.statusSent) "Sent " else "Failed ") + sdf.format(Date(task.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.statusSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = { hour = state.hour; minute = state.minute; showPicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } }
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return String.format("%02d:%02d %s", h12, minute, if (hour >= 12) "PM" else "AM")
}

// ───────────────────────────── Music ─────────────────────────────

@Composable
private fun MusicTab(
    contact: Contact,
    state: ListenState,
    onLoad: (String, String) -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onInvite: (String) -> Unit,
    onSolo: (java.io.File, String) -> Unit,
    onExitSolo: () -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val tracks by MusicLibrary.tracks.collectAsState()
    val download by MusicLibrary.download.collectAsState()

    LaunchedEffect(Unit) { MusicLibrary.refresh(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) MusicLibrary.importFile(context, uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Player ──
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.solo) "🎧 Sirf mere liye" else "🎧 ${contact.name} ke saath",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp
                )
                if (state.url.isBlank()) {
                    Text(
                        "Koi gaana chalu nahi. Neeche library se gaana chuno, ya link daalo.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(state.title.ifBlank { "Now playing" }, fontWeight = FontWeight.SemiBold)
                    if (state.offlineCopy && !state.solo) {
                        Text("📥 Aapke phone ki downloaded copy chal rahi hai", style = MaterialTheme.typography.labelSmall)
                    }
                    val duration = state.durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { onSeek((it * duration).toLong()) }
                            dragging = null
                        },
                        enabled = state.durationMs > 0
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(state.positionMs), fontSize = 12.sp)
                        Text(formatMs(state.durationMs), fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(48.dp))
                        } else {
                            FilledIconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                                Icon(
                                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause", modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                    if (state.solo) {
                        TextButton(onClick = onExitSolo, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Saath me sunne wale mode par wapas")
                        }
                    } else {
                        Text("Play / pause / seek dono phones par sync hota hai.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // ── My library (downloaded songs) ──
        Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📚 Meri library (phone me saved)", fontWeight = FontWeight.Bold)
                Text(
                    "Ye gaane sirf is app ke andar rehte hain (doosre apps nahi dekh sakte) aur bina internet chalte hain.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tracks.isEmpty()) {
                    Text("Abhi koi gaana save nahi hai.", style = MaterialTheme.typography.bodySmall)
                }
                tracks.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "%.1f MB%s".format(t.sizeBytes / 1_048_576.0, if (t.canShare) "" else " • sirf mere liye"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSolo(MusicLibrary.fileFor(context, t), t.title) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play just me")
                        }
                        if (t.canShare) {
                            IconButton(onClick = {
                                onLoad(t.url, t.title)
                                onInvite(t.title)
                            }) { Icon(Icons.Default.People, contentDescription = "Play together", tint = MaterialTheme.colorScheme.primary) }
                        }
                        IconButton(onClick = { MusicLibrary.delete(context, t) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("audio/*")) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("📂 Phone se gaana add karo") }
            }
        }

        // ── Add by link ──
        Card(shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Link se gaana", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Audio link (https://....mp3)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("music_url")
                )
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Naam (optional)") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onLoad(url, title)
                            onInvite(title.ifBlank { "a song" })
                        },
                        enabled = url.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("music_play")
                    ) { Text("Saath me chalao", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    FilledTonalButton(
                        onClick = { MusicLibrary.download(context, url, title) },
                        enabled = url.isNotBlank() && !download.active,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("⬇ Download", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                if (download.active) {
                    Text("Download ho raha hai: ${download.title} (${download.percent}%)", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { download.percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
                download.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Sirf wahi gaane download karo jinka aapko adhikaar hai (free / apne / license wale). " +
                        "YouTube/Spotify ke link direct play ya download nahi hote.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", total / 60, total % 60)
}

// ───────────────────────────── Shared tasks ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTab(contact: Contact) {
    val myUid = CloudRepository.uid
    val scope = rememberCoroutineScope()
    val tasks by remember(contact.linkId) { CloudRepository.tasksFlow(contact.linkId) }
        .collectAsState(initial = emptyList<com.example.cloud.SharedTask>())
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var asRoutine by remember { mutableStateOf(false) }
    var routineHour by remember { mutableIntStateOf(7) }
    var routineMinute by remember { mutableIntStateOf(0) }
    var showTimePicker by remember { mutableStateOf(false) }
    val pending = tasks.filter { !it.done }
    val done = tasks.filter { it.done }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item {
                Text("📝 ${contact.name} ke saath shared tasks", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "Dono add kar sakte ho, tick kar sakte ho. Naya task ya done hone par dusre ko notification jata hai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tasks.isEmpty()) {
                item { Text("Abhi koi task nahi. Neeche se pehla add karo ✨", modifier = Modifier.padding(vertical = 16.dp)) }
            }
            items(pending, key = { it.id }) { t -> TaskRow(t, myUid, contact, scope) }
            if (done.isNotEmpty()) {
                item { Text("Ho gaye (${done.size})", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                items(done, key = { it.id }) { t -> TaskRow(t, myUid, contact, scope) }
            }
        }
        // Routine option: a shared DAILY routine rings on both phones every day
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !asRoutine, onClick = { asRoutine = false }, label = { Text("📝 Pending task") })
            FilterChip(selected = asRoutine, onClick = { asRoutine = true }, label = { Text("⏰ Daily routine") })
            if (asRoutine) {
                OutlinedButton(onClick = { showTimePicker = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(formatTime(routineHour, routineMinute))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(if (asRoutine) "Routine, jaise: Subah 6 baje walk" else "Naya task...") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).testTag("task_input")
            )
            FilledIconButton(
                onClick = {
                    val t = text.trim()
                    text = ""
                    scope.launch {
                        if (asRoutine) {
                            // my own daily alarm (also pings them), and the same alarm lands on their phone
                            val dao = com.example.data.AppDatabase.getDatabase(context).reminderDao()
                            val note = com.example.data.model.ReminderNote(
                                title = "With ${contact.name}: $t",
                                queryDetails = "Shared daily routine",
                                hour = routineHour, minute = routineMinute,
                                categoryTag = "Personal",
                                notifyUid = contact.uid, notifyName = contact.name
                            )
                            val id = dao.insertReminder(note)
                            com.example.util.AlarmScheduler.scheduleAlarm(context, note.copy(id = id))
                            CloudRepository.addTask(contact.linkId, contact.uid, t, routineHour, routineMinute)
                        } else {
                            CloudRepository.addTask(contact.linkId, contact.uid, t)
                        }
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(52.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
        }
    }

    if (showTimePicker) {
        val st = rememberTimePickerState(initialHour = routineHour, initialMinute = routineMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { routineHour = st.hour; routineMinute = st.minute; showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = st) } }
        )
    }
}

@Composable
private fun TaskRow(
    t: com.example.cloud.SharedTask,
    myUid: String?,
    contact: Contact,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (t.done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (t.done) 0.dp else 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = t.done,
                onCheckedChange = { scope.launch { CloudRepository.setTaskDone(contact.linkId, contact.uid, t, it) } }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.title,
                    textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    (if (t.hour >= 0) "⏰ Roz ${formatTime(t.hour, t.minute)} • " else "") +
                        (if (t.addedBy == myUid) "Maine add kiya" else "${contact.name} ne add kiya") +
                        if (t.done && t.doneBy.isNotBlank()) " • ${if (t.doneBy == myUid) "maine kiya ✅" else "${contact.name} ne kiya ✅"}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { scope.launch { CloudRepository.deleteTask(contact.linkId, t.id) } }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ───────────────────────────── Location ─────────────────────────────

@Composable
private fun LocationTab(
    contact: Contact,
    onAsk: () -> Unit,
    onShare: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val point by remember(contact.uid, contact.theyShareLocation) {
        if (contact.theyShareLocation) CloudRepository.locationFlow(contact.uid) else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📍 ${contact.name} ki location", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val p = point
                if (!contact.theyShareLocation) {
                    Text("${contact.name} abhi apni location share nahi kar rahe.")
                } else if (p == null) {
                    Text("Location abhi available nahi (unka GPS/internet band ho sakta hai).")
                } else {
                    if (p.sos) Text("🚨 SOS ACTIVE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                    Text("%.5f, %.5f  (±%d m)".format(p.lat, p.lng, p.accuracy.toInt()))
                    Text("Updated: ${fmt.format(Date(p.ts))}", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(LocationHelper.mapsLink(p.lat, p.lng)))
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open in Google Maps")
                    }
                }
                OutlinedButton(onClick = onAsk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unhe location ON karne ko bolo")
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Meri location ${contact.name} ke saath share karo", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Live location background me update hoti rehti hai. Kabhi bhi band kar sakte ho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = contact.iShareLocation, onCheckedChange = onShare)
            }
        }
    }
}
