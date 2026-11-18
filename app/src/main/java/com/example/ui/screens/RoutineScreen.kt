package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReminderNote
import com.example.ui.components.AddEditReminderDialog
import com.example.util.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(
    notes: List<ReminderNote>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTag: String,
    onTagSelect: (String) -> Unit,
    onSaveNote: (ReminderNote) -> Unit,
    onToggleAlarm: (ReminderNote, Boolean) -> Unit,
    onToggleCompleted: (ReminderNote, Boolean) -> Unit,
    onDeleteNote: (ReminderNote) -> Unit,
    contacts: List<com.example.cloud.Contact> = emptyList()
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<ReminderNote?>(null) }

    val tags = listOf("All", "Routine", "Health", "Study", "Love Note", "Personal")

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingNote = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("Add Routine Note", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_routine_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search routine notes or queries...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_search_bar")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tag Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tags) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onTagSelect(tag) },
                        label = { Text(tag) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (notes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🌸",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No routine notes added yet!" else "No notes matching '$searchQuery'",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button below to create your daily routine alarm!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        ReminderCardItem(
                            note = note,
                            onToggleAlarm = { enabled -> onToggleAlarm(note, enabled) },
                            onToggleCompleted = { completed -> onToggleCompleted(note, completed) },
                            onEdit = {
                                editingNote = note
                                showAddDialog = true
                            },
                            onDelete = { onDeleteNote(note) },
                            onTestNotification = {
                                NotificationHelper.createNotificationChannel(context)
                                NotificationHelper.showAlarmNotification(
                                    context = context,
                                    reminderId = note.id,
                                    title = note.title,
                                    details = note.queryDetails
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditReminderDialog(
            initialNote = editingNote,
            contacts = contacts,
            onDismiss = {
                showAddDialog = false
                editingNote = null
            },
            onSave = { note ->
                onSaveNote(note)
                showAddDialog = false
                editingNote = null
            }
        )
    }
}

@Composable
fun ReminderCardItem(
    note: ReminderNote,
    onToggleAlarm: (Boolean) -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestNotification: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time pill badge
                val formatAmPm = if (note.hour >= 12) "PM" else "AM"
                val hour12 = if (note.hour % 12 == 0) 12 else note.hour % 12
                val formattedTime = String.format("%02d:%02d %s", hour12, note.minute, formatAmPm)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = note.isCompleted,
                        onCheckedChange = onToggleCompleted,
                        modifier = Modifier.testTag("note_checkbox_${note.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Category Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = note.categoryTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Alarm switch
                Switch(
                    checked = note.isAlarmEnabled,
                    onCheckedChange = onToggleAlarm,
                    modifier = Modifier.testTag("note_switch_${note.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Note Title
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (note.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )

            if (note.queryDetails.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.queryDetails,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Alarm button
                TextButton(
                    onClick = onTestNotification,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Test Sound",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Notification", fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
