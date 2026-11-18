package com.example.data.dao

import androidx.room.*
import com.example.data.model.SujalTaskMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface SujalTaskDao {
    @Query("SELECT * FROM sujal_task_messages ORDER BY timestamp DESC")
    fun getAllSujalTasks(): Flow<List<SujalTaskMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSujalTask(task: SujalTaskMessage): Long

    @Delete
    suspend fun deleteSujalTask(task: SujalTaskMessage)
}
