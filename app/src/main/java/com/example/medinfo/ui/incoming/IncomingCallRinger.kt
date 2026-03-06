package com.example.medinfo.ui.incoming

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

object IncomingCallRinger {

    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRunning = false

    fun start(context: Context) {
        if (isRunning) return
        stopInternal()

        val appContext = context.applicationContext
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val mp = MediaPlayer()
        try {
            mp.setDataSource(appContext, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            isRunning = true
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {}
        }
    }

    @Deprecated("Use start(context) and manual stop() instead")
    fun start(context: Context, durationMs: Long) {
        start(context)
        val runnable = Runnable { stop() }
        stopRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun stop() {
        stopInternal()
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
    }

    fun isPlaying(): Boolean {
        return isRunning && mediaPlayer?.isPlaying == true
    }

    private fun stopInternal() {
        isRunning = false
        val mp = mediaPlayer
        mediaPlayer = null
        mp?.let {
            try {
                it.stop()
            } catch (_: Exception) {}
            try {
                it.release()
            } catch (_: Exception) {}
        }
    }
}
