package com.example.medinfo.ui.incoming

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

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
        } catch (e: Exception) {
            Log.w("IncomingCallRinger", "Не удалось запустить рингтон", e)
            try {
                mp.release()
            } catch (e: Exception) {
                Log.e("IncomingCallRinger", "Не удалось освободить MediaPlayer", e)
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
        } catch (e: Exception) {
            Log.w("IncomingCallRinger", "Не удалось остановить MediaPlayer", e)
        }
        try {
            mp?.release()
        } catch (e: Exception) {
            Log.e("IncomingCallRinger", "Не удалось освободить MediaPlayer в stop()", e)
        }
    }
}