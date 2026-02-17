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
    private var isRunning = false

    /**
     * Start ringer continuously until stop() is called.
     * @param context Application context
     */
    fun start(context: Context) {
        Log.d("IncomingCallRinger", "=== start: START (continuous mode) ===")
        if (isRunning) {
            Log.d("IncomingCallRinger", "=== start: Already running, skipping ===")
            return
        }
        Log.d("IncomingCallRinger", "=== start: Calling stop() to reset any existing ringer ===")
        stopInternal()
        Log.d("IncomingCallRinger", "=== start: Previous ringer stopped ===")

        val appContext = context.applicationContext
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        Log.d("IncomingCallRinger", "=== start: Using ringtone URI=$uri ===")
        val mp = MediaPlayer()
        try {
            Log.d("IncomingCallRinger", "=== start: Setting data source ===")
            mp.setDataSource(appContext, uri)
            Log.d("IncomingCallRinger", "=== start: Setting audio attributes ===")
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = true
            Log.d("IncomingCallRinger", "=== start: Preparing MediaPlayer ===")
            mp.prepare()
            Log.d("IncomingCallRinger", "=== start: Starting playback ===")
            mp.start()
            mediaPlayer = mp
            isRunning = true
            Log.d("IncomingCallRinger", "=== start: Ringer STARTED SUCCESSFULLY (continuous) ===")
        } catch (e: Exception) {
            Log.e("IncomingCallRinger", "=== start: FAILED to start rington - ${e.message} ===", e)
            try {
                mp.release()
                Log.d("IncomingCallRinger", "=== start: MediaPlayer released after error ===")
            } catch (e: Exception) {
                Log.e("IncomingCallRinger", "=== start: Failed to release MediaPlayer - ${e.message} ===", e)
            }
        }
    }

    /**
     * Start ringer with auto-stop after duration (legacy method for backward compatibility)
     * @param context Application context
     * @param durationMs Duration before auto-stop (deprecated, use stop() manually)
     */
    @Deprecated("Use start(context) and manual stop() instead for continuous ringing")
    fun start(context: Context, durationMs: Long) {
        Log.d("IncomingCallRinger", "=== start: START with duration=$durationMs (legacy mode) ===")
        start(context)
        // Планируем автоматическую остановку для обратной совместимости
        val runnable = Runnable { 
            Log.d("IncomingCallRinger", "=== Auto-stop runnable triggered after $durationMs ms ===")
            stop() 
        }
        stopRunnable = runnable
        Log.d("IncomingCallRinger", "=== start: Posting delayed stop for $durationMs ms ===")
        handler.postDelayed(runnable, durationMs)
    }

    fun stop() {
        Log.d("IncomingCallRinger", "=== stop: PUBLIC STOP CALLED ===")
        stopInternal()
        // Также отменяем отложенную автоматическую остановку
        stopRunnable?.let { 
            Log.d("IncomingCallRinger", "=== stop: Removing pending auto-stop callbacks ===")
            handler.removeCallbacks(it) 
        }
        stopRunnable = null
        Log.d("IncomingCallRinger", "=== stop: COMPLETE ===")
    }

    fun isPlaying(): Boolean {
        return isRunning && mediaPlayer?.isPlaying == true
    }

    private fun stopInternal() {
        Log.d("IncomingCallRinger", "=== stopInternal: START ===")
        isRunning = false
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            Log.d("IncomingCallRinger", "=== stopInternal: Stopping MediaPlayer ===")
            try {
                mp.stop()
                Log.d("IncomingCallRinger", "=== stopInternal: MediaPlayer stopped ===")
            } catch (e: Exception) {
                Log.w("IncomingCallRinger", "=== stopInternal: Error stopping MediaPlayer - ${e.message} ===", e)
            }
            try {
                Log.d("IncomingCallRinger", "=== stopInternal: Releasing MediaPlayer ===")
                mp.release()
                Log.d("IncomingCallRinger", "=== stopInternal: MediaPlayer released ===")
            } catch (e: Exception) {
                Log.e("IncomingCallRinger", "=== stopInternal: Error releasing MediaPlayer - ${e.message} ===", e)
            }
        } else {
            Log.d("IncomingCallRinger", "=== stopInternal: No MediaPlayer to stop ===")
        }
        Log.d("IncomingCallRinger", "=== stopInternal: COMPLETE ===")
    }
}