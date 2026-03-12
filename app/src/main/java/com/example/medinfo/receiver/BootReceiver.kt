package com.example.medinfo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.medinfo.data.signalr.SignalRService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("app_session", Context.MODE_PRIVATE)
            val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)
            val token = sharedPrefs.getString("jwt_token", "") ?: ""

            if (isLoggedIn && token.isNotBlank()) {
                val serviceIntent = Intent(context, SignalRService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
