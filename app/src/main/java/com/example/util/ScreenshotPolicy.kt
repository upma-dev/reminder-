package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/**
 * Privacy: when ON (default) screenshots, screen recording and the app preview in
 * "recent apps" are blocked (FLAG_SECURE), so profile photos, chats and locations stay private.
 */
object ScreenshotPolicy {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    fun isBlocked(context: Context) = prefs(context).getBoolean("block_screenshots", true)

    fun setBlocked(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("block_screenshots", on).apply()
        apply(context)
    }

    fun apply(context: Context) {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) {
                if (isBlocked(c)) c.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else c.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                return
            }
            c = c.baseContext
        }
    }
}
