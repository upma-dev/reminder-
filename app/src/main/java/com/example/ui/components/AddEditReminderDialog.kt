package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ReminderNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderDialog(
    initialNote: ReminderNote? = null,
    contacts: List<com.example.cloud.Contact> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ReminderNote) -> Unit
) {
    var notifyUid by remember { mutableStateOf(initialNote?.notifyUid ?: "") }
    var notifyName by remember { mutableStateOf(initialNote?.notifyName ?: "") }
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var queryDetails by remember { mutableStateOf(initialNote?.queryDetails ?: "") }
    var hour by remember { mutableIntStateOf(initialNote?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initialNote?.minute ?: 0) }
    var categoryTag by remember { mutableStateOf(initialNote?.categoryTag ?: "Routine") }
    var isAlarmEnabled by remember { mutableStateOf(initialNote?.isAlarmEnabled ?: true) }

    val tags = listOf("Routine", "Health", "Study", "Love Note", "Personal")

    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialNote == null) "🌸 Add Cute Routine Note" else "✏️ Edit Routine Note",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title / Reminder") },
                    placeholder = { Text("e.g. Drink warm lemon water") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                // Query details / Notes Input
                OutlinedTextField(
                    value = queryDetails,
                    onValueChange = { queryDetails = it },
                    label = { Text("Query Notes / Details") },
                    placeholder = { Text("Write extra details or instructions...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_details_input")
                )

                // Time Selection Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Alarm Time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val formatAmPm = if (hour >= 12) "PM" else "AM"
                                val hour12 = if (hour % 12 == 0) 12 else hour % 12
                                val formattedTime = String.format("%02d:%02d %s", hour12, minute, formatAmPm)
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            text = "Change ⏰",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Category Chips
                Column {
                    Text(
                        text = "Category Tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.take(3).forEach { tag ->
                            FilterChip(
                                selected = categoryTag == tag,
                                onClick = { categoryTag = tag },
                                label = { Text(tag) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.drop(3).forEach { tag ->
                            FilterChip(
                                selected = categoryTag == tag,
                                onClick = { categoryTag = tag },
                                label = { Text(tag) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Personal circle: also notify one of my people when this fires
                if (contacts.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Ye reminder kisko bhi bhejna hai? 💌",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = notifyUid.isBlank(),
                                    onClick = { notifyUid = ""; notifyName = "" },
                                    label = { Text("Sirf mujhe") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            items(contacts.size) { i ->
                                val c = contacts[i]
                                FilterChip(
                                    selected = notifyUid == c.uid,
                                    onClick = { notifyUid = c.uid; notifyName = c.name },
                                    label = { Text(c.name) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Alarm Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Alarm-like Notification",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Switch(
                        checked = isAlarmEnabled,
                        onCheckedChange = { isAlarmEnabled = it }
                    )
                }

                // Save Button
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                ReminderNote(
                                    id = initialNote?.id ?: 0L,
                                    title = title.trim(),
                                    queryDetails = queryDetails.trim(),
                                    hour = hour,
                                    minute = minute,
                                    categoryTag = categoryTag,
                                    isAlarmEnabled = isAlarmEnabled,
                                    notifyUid = notifyUid,
                                    notifyName = notifyName
                                )
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_note_button")
                ) {
                    Text(text = "Save Routine Note ✨", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    // Compose TimePicker Dialog if requested
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
