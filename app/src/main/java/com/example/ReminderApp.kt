package com.example

import android.app.Application
import com.example.cloud.CloudRepository
import com.example.util.NotificationHelper

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CloudRepository.init(this)
        try {
            NotificationHelper.createAllChannels(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
