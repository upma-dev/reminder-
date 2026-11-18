package com.example.data.dao

import androidx.room.*
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_config WHERE id = 1")
    fun getEmergencyConfigFlow(): Flow<EmergencyConfig?>

    @Query("SELECT * FROM emergency_config WHERE id = 1")
    suspend fun getEmergencyConfig(): EmergencyConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEmergencyConfig(config: EmergencyConfig)

    @Query("SELECT * FROM emergency_logs ORDER BY timestamp DESC")
    fun getAllEmergencyLogs(): Flow<List<EmergencyLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencyLog(log: EmergencyLog): Long
}
