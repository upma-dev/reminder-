package com.example.util

import android.content.Context

/** "Clear chat for me": remembers the time up to which I cleared a chat (only affects this phone). */
object ChatPrefs {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

    fun clearedAt(context: Context, linkId: String): Long = prefs(context).getLong("cleared_$linkId", 0L)

    fun clearNow(context: Context, linkId: String) {
        prefs(context).edit().putLong("cleared_$linkId", System.currentTimeMillis()).apply()
    }
}
