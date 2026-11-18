package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.Contact
import com.example.cloud.LinkState

/**
 * Personal tab: my circle. Add people by phone number, accept requests, choose who is an
 * emergency contact and who may see my live location, then open a person for chat, reminders,
 * alarms and music.
 */
@Composable
fun PersonalScreen(
    contacts: List<Contact>,
    onAdd: (String) -> Unit,
    onAccept: (Contact) -> Unit,
    onRemove: (Contact) -> Unit,
    blockedUids: Set<String>,
    onBlock: (Contact) -> Unit,
    onUnblock: (Contact) -> Unit,
    onShareLocation: (Contact, Boolean) -> Unit,
    onOpen: (Contact) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current
    // System contact picker: returns only the contact the user taps, so no READ_CONTACTS permission is needed.
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.data?.let { uri ->
                context.contentResolver.query(
                    uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
                )?.use { c -> if (c.moveToFirst()) phone = c.getString(0).orEmpty() }
            }
        }
    }
    val incoming = contacts.filter { it.state == LinkState.INCOMING }
    val outgoing = contacts.filter { it.state == LinkState.OUTGOING }
    val people = contacts.filter { it.state == LinkState.ACCEPTED && it.uid !in blockedUids }
    val blockedPeople = contacts.filter { it.state == LinkState.ACCEPTED && it.uid in blockedUids }
    var confirmBlock by remember { mutableStateOf<Contact?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mutual — apne khaas log 💌", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Unka mobile number daalo (jo is app me login hai). Wo accept karein tab hi: chat, music, alarm, " +
                            "daily routine aur pending tasks dono ko dikhenge. (Emergency contacts alag hain — SOS Hub me.)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).testTag("add_person_phone")
                        )
                        Button(
                            onClick = {
                                onAdd(phone)
                                phone = ""
                            },
                            enabled = phone.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(56.dp).testTag("add_person_button")
                        ) { Icon(Icons.Default.Add, contentDescription = "Add") }
                    }
                    TextButton(onClick = {
                        try {
                            pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Phone contacts se chuno")
                    }
                }
            }
        }

        if (incoming.isNotEmpty()) {
            item { SectionTitle("Requests aapke liye") }
            items(incoming, key = { it.linkId }) { c ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 44, uid = c.uid)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text(c.phone, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onRemove(c) }) {
                            Icon(Icons.Default.Close, contentDescription = "Decline", tint = MaterialTheme.colorScheme.error)
                        }
                        FilledTonalButton(onClick = { onAccept(c) }) { Text("Accept") }
                    }
                }
            }
        }

        item { SectionTitle("Meri personal list (${people.size})") }

        if (people.isEmpty()) {
            item {
                Text(
                    "Abhi koi nahi hai. Upar number daalkar pehli request bhejo ✨",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        items(people, key = { it.linkId }) { c ->
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 52, uid = c.uid)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (c.theyShareLocation) Tag("📍 Sharing with me")
                            }
                        }
                        Button(onClick = { onOpen(c) }, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                    SwitchRow("Meri live location isse share karo", c.iShareLocation) { onShareLocation(c, it) }
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { confirmBlock = c }) { Text("🚫 Block") }
                        TextButton(onClick = { onRemove(c) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        if (outgoing.isNotEmpty()) {
            item { SectionTitle("Bheji hui requests (pending)") }
            items(outgoing, key = { it.linkId }) { c ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 40, uid = c.uid)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text("Accept hone ka wait...", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRemove(c) }) { Text("Cancel") }
                    }
                }
            }
        }

        if (blockedPeople.isNotEmpty()) {
            item { SectionTitle("🚫 Blocked (${blockedPeople.size})") }
            items(blockedPeople, key = { "b_" + it.linkId }) { c ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 40, uid = c.uid)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text("Message, alarm, music kuch nahi bhej sakta", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onUnblock(c) }) { Text("Unblock") }
                    }
                }
            }
        }
    }

    confirmBlock?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmBlock = null },
            title = { Text("${c.name} ko block karein?") },
            text = {
                Text(
                    "Block karne par ${c.name} aapko message, alarm, reminder, music ya task nahi bhej payenge, " +
                        "aur aapki live location bhi nahi dekh payenge. Unhe pata nahi chalega. Kabhi bhi unblock kar sakte ho."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBlock(c)
                    confirmBlock = null
                }) { Text("Block", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
}

/** Round avatar: shows the profile photo when there is one (own: [photoB64], others: loaded by [uid]). */
@Composable
internal fun Avatar(name: String, size: Int, uid: String? = null, photoB64: String? = null) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uid, photoB64) {
        val b64 = photoB64 ?: uid?.let { com.example.cloud.CloudRepository.photoOf(it) }
        value = withContext(Dispatchers.Default) { com.example.util.ImageUtil.decode(b64) }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
            ),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size / 2.4).sp
            )
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
