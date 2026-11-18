package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sujal_task_messages")
data class SujalTaskMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskTitle: String,
    val noteText: String,
    val recipientName: String = "Sujal",
    val recipientPhone: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val statusSent: Boolean = false,
    val isUrgent: Boolean = false
)
