package com.example.data

import com.example.data.dao.EmergencyDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.SujalTaskDao
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import com.example.data.model.ReminderNote
import com.example.data.model.SujalTaskMessage
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val sujalTaskDao: SujalTaskDao,
    private val emergencyDao: EmergencyDao
) {
    // Reminders
    val allReminders: Flow<List<ReminderNote>> = reminderDao.getAllReminders()

    suspend fun getReminderById(id: Long): ReminderNote? = reminderDao.getReminderById(id)

    suspend fun insertReminder(reminder: ReminderNote): Long = reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: ReminderNote) = reminderDao.updateReminder(reminder)

    suspend fun deleteReminder(reminder: ReminderNote) = reminderDao.deleteReminder(reminder)

    suspend fun deleteReminderById(id: Long) = reminderDao.deleteReminderById(id)

    // Sujal Tasks
    val allSujalTasks: Flow<List<SujalTaskMessage>> = sujalTaskDao.getAllSujalTasks()

    suspend fun insertSujalTask(task: SujalTaskMessage): Long = sujalTaskDao.insertSujalTask(task)

    suspend fun deleteSujalTask(task: SujalTaskMessage) = sujalTaskDao.deleteSujalTask(task)

    // Emergency Config & Logs
    val emergencyConfig: Flow<EmergencyConfig?> = emergencyDao.getEmergencyConfigFlow()

    suspend fun getEmergencyConfig(): EmergencyConfig = emergencyDao.getEmergencyConfig() ?: EmergencyConfig()

    suspend fun saveEmergencyConfig(config: EmergencyConfig) = emergencyDao.saveEmergencyConfig(config)

    val emergencyLogs: Flow<List<EmergencyLog>> = emergencyDao.getAllEmergencyLogs()

    suspend fun insertEmergencyLog(log: EmergencyLog): Long = emergencyDao.insertEmergencyLog(log)
}
