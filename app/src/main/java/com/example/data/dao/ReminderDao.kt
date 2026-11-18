package com.example.data.dao

import androidx.room.*
import com.example.data.model.ReminderNote
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_notes ORDER BY hour ASC, minute ASC")
    fun getAllReminders(): Flow<List<ReminderNote>>

    @Query("SELECT * FROM reminder_notes WHERE isAlarmEnabled = 1")
    suspend fun getEnabledReminders(): List<ReminderNote>

    @Query("SELECT * FROM reminder_notes WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderNote): Long

    @Update
    suspend fun updateReminder(reminder: ReminderNote)

    @Delete
    suspend fun deleteReminder(reminder: ReminderNote)

    @Query("DELETE FROM reminder_notes WHERE id = :id")
    suspend fun deleteReminderById(id: Long)
}
