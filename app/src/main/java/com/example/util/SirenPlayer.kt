package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Loud looping siren on the ALARM stream (rings even if media volume is low). */
object SirenPlayer {
    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timedStop: Runnable? = null

    /**
     * Normal reminder / alarm ring: uses the ALARM stream (rings even in silent mode),
     * at the user's own alarm volume, and stops by itself after [millis].
     */
    fun startTimed(context: Context, millis: Long = 30_000L) {
        if (player != null) return // an SOS or another alarm is already ringing
        start(context, forceMax = false)
        val app = context.applicationContext
        val r = Runnable { stop(app) }
        timedStop = r
        handler.postDelayed(r, millis)
    }

    @Synchronized
    fun start(context: Context, forceMax: Boolean = true) {
        if (forceMax) {
            timedStop?.let { handler.removeCallbacks(it) }
            timedStop = null
        }
        val app = context.applicationContext
        if (player != null) {
            // A reminder alarm is already ringing and an SOS arrives: make it full volume and keep it on.
            if (forceMax && previousVolume == null) {
                try {
                    val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                    audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return
        }
        try {
            if (forceMax) {
                val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vibrate(app, true)
    }

    @Synchronized
    fun stop(context: Context) {
        val app = context.applicationContext
        timedStop?.let { handler.removeCallbacks(it) }
        timedStop = null
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        player = null
        try {
            previousVolume?.let {
                val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        previousVolume = null
        vibrate(app, false)
    }

    val isPlaying: Boolean get() = player != null

    private fun vibrate(context: Context, on: Boolean) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!on) {
                vibrator.cancel()
                return
            }
            val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
