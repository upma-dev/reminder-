package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_config")
data class EmergencyConfig(
    @PrimaryKey val id: Int = 1, // Single row config
    // Offline backup contact: gets a plain SMS (with map link) even when there is no internet.
    val partnerName: String = "",
    val partnerPhone: String = "",
    val customSosMessage: String = "EMERGENCY ALERT! I am in an urgent emergency situation. Please check on me immediately or call me back!",
    val emergencyCallNumber: String = "112",
    val sirenSoundEnabled: Boolean = true
)

@Entity(tableName = "emergency_logs")
data class EmergencyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerType: String = "PANIC_BUTTON", // e.g., PANIC_BUTTON, SOS_RECEIVED, SOS_SAFE
    val details: String = "Emergency alert triggered",
    val status: String = "Alert Sent"
)
