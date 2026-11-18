package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.EmergencyDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.SujalTaskDao
import com.example.data.model.EmergencyConfig
import com.example.data.model.EmergencyLog
import com.example.data.model.ReminderNote
import com.example.data.model.SujalTaskMessage

@Database(
    entities = [
        ReminderNote::class,
        SujalTaskMessage::class,
        EmergencyConfig::class,
        EmergencyLog::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun sujalTaskDao(): SujalTaskDao
    abstract fun emergencyDao(): EmergencyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var instanceName: String? = null

        /** Every login has its own database, so two accounts on one phone never see each other's data. */
        private fun nameFor(uid: String?): String =
            if (uid.isNullOrBlank()) "cute_reminder_v3.db" else "cute_reminder_u_$uid.db"

        fun getDatabase(context: Context): AppDatabase {
            val wanted = nameFor(com.example.cloud.CloudRepository.uid)
            INSTANCE?.let { if (instanceName == wanted) return it }
            return synchronized(this) {
                INSTANCE?.let { if (instanceName == wanted) return@synchronized it }
                INSTANCE?.close()
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    wanted
                )
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instanceName = wanted
                instance
            }
        }
    }
}
