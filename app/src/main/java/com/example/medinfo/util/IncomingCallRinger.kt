package com.example.medinfo.util

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

    fun start(context: Context, durationMs: Long) {
        stop()

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
        } catch (_: Exception) {
            try {
                mp.release()
            } catch (_: Exception) {
            }
            return
        }

        val runnable = Runnable { stop() }
        stopRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun stop() {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null

        val mp = mediaPlayer
        mediaPlayer = null
        try {
            mp?.stop()
        } catch (_: Exception) {
        }
        try {
            mp?.release()
        } catch (_: Exception) {
        }
    }
}
