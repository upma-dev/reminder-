package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_notes")
data class ReminderNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val queryDetails: String = "",
    val hour: Int,
    val minute: Int,
    val isAlarmEnabled: Boolean = true,
    val categoryTag: String = "Routine", // e.g. Routine, Health, Study, Love Note, Personal
    val isCompleted: Boolean = false,
    val repeatDays: String = "Mon,Tue,Wed,Thu,Fri,Sat,Sun", // Comma-separated days
    val createdAt: Long = System.currentTimeMillis(),
    // Personal circle: when this reminder fires, the same notification is pushed to this person.
    val notifyUid: String = "",
    val notifyName: String = ""
)
