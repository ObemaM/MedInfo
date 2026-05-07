package com.example.medinfo.ui.incoming

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

object IncomingCallRinger {

    private var mediaPlayer: MediaPlayer? = null
    private var isRunning = false

    // Помечает, что звон запустил именно startBrief (для алерта поверх IncomingCallActivity).
    // Нужен, чтобы при свайпе алерта не задеть continuous-звон основного вызова.
    private var isBriefMode = false

    fun start(context: Context) {
        // Continuous-режим перебивает brief: если звон уже шёл от brief, дальше он считается
        // основным звоном вызова и больше не глушится по dismiss алерта.
        isBriefMode = false

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
        startBrief(context, durationMs)
    }

    // Запускает звон в "brief"-режиме. Без auto-stop: останавливается через stopBrief()
    // при свайпе/тапе in-app алерта. Если continuous уже играет — не трогаем.
    @Suppress("UNUSED_PARAMETER")
    fun startBrief(context: Context, durationMs: Long) {
        if (isRunning) return
        start(context)
        if (isRunning) {
            isBriefMode = true
        }
    }

    fun stop() {
        isBriefMode = false
        stopInternal()
    }

    // Стопит ringer только если он был запущен brief'ом. continuous-звон основного
    // вызова трогать нельзя.
    fun stopBrief() {
        if (isBriefMode) {
            stop()
        }
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
