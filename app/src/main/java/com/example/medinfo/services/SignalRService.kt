package com.example.medinfo.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.medinfo.R
import com.example.medinfo.data.cache.CallsCache
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.ui.incoming.IncomingCallActivity
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single

class SignalRService : Service() {

    private var hubConnection: HubConnection? = null
    private val CHANNEL_ID_SERVICE = "MedInfo_SignalR_Service"

    private val NOTIFICATION_ID_SERVICE = 101

    private val callsCache by lazy { CallsCache(applicationContext) }

    private fun isSessionActive(): Boolean {
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""
        return isLoggedIn && token.isNotBlank()
    }

    private fun isCallInDiskCache(callNumber: String?): Boolean {
        val number = callNumber?.trim().orEmpty()
        if (number.isEmpty()) return false

        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val userLogin = sharedPrefs.getString("user_login", null) ?: return false
        return callsCache.containsCallNumber(userLogin, number)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SignalR", "Service created")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isSessionActive()) {
            Log.w("SignalR", "Session not active, stopping service")
            stopAndCleanup()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Ожидание вызовов")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID_SERVICE, notification)

        if (hubConnection == null) {
            initSignalR()
        }

        return START_STICKY
    }

    private fun initSignalR() {
        if (!isSessionActive()) {
            stopAndCleanup()
            return
        }
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""
        val hubUrl = "http://46.146.213.95:27234/call"

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token))
            .build()

        hubConnection?.on("Receive", { callData ->
            Log.d("SignalR", "$callData")
            if (!isSessionActive()) {
                stopAndCleanup()
                return@on
            }

            val statusLower = callData.status?.lowercase()
            when (statusLower) {
                "транспортировка" -> {
                    val inCache = isCallInDiskCache(callData.callNumber)
                    if (!inCache) {
                        Log.d("SignalR", "New call, adding to queue and triggering alert")
                        CallsManager.addCall(callData)
                        triggerFullscreenAlert(callData)
                    } else {
                        Log.d("SignalR", "Call already in cache, skipping")
                    }
                }
                "результат", "архив" -> {
                    Log.d("SignalR", "Call completed, removing from queue")
                    callData.callNumber?.let { CallsManager.removeCall(it) }
                    IncomingCallRinger.stop()
                }
                else -> {
                    Log.d("SignalR", "Unknown status '$statusLower'")
                }
            }
        }, CallNotificationDto::class.java)

        hubConnection?.onClosed { exception ->
            Log.e("SignalR", "Connection closed: ${exception?.message}")
            if (isSessionActive()) {
                startHubConnection()
            } else {
                stopAndCleanup()
            }
        }

        startHubConnection()
    }

    private fun startHubConnection() {
        Thread {
            try {
                if (!isSessionActive()) {
                    stopAndCleanup()
                    return@Thread
                }
                hubConnection?.start()?.blockingAwait()
                Log.i("SignalR", "Connection established")
            } catch (e: Exception) {
                Log.e("SignalR", "Connection error: ${e.message}, retrying in 5s...")
                Thread.sleep(5000)
                if (isSessionActive()) {
                    startHubConnection()
                } else {
                    stopAndCleanup()
                }
            }
        }.start()
    }

    private fun stopAndCleanup() {
        stopAndCleanupInternal(stopSelf = true)
    }

    private fun stopAndCleanupInternal(stopSelf: Boolean) {
        try {
            hubConnection?.stop()
        } catch (e: Exception) {
            Log.e("SignalR", "Error stopping hubConnection: ${e.message}")
        }
        hubConnection = null
        IncomingCallRinger.stop()
        CallsManager.clearAll()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (stopSelf) {
            stopSelf()
        }
    }

    private fun triggerFullscreenAlert(callData: CallNotificationDto) {
        IncomingCallRinger.start(this)

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALL_DATA", callData)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                putExtra("FORCE_SHOW", true)
            }
        }

        try {
            startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e("SignalR", "Failed to start activity: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "MedInfo: подключение к серверу",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Фоновое уведомление для работы SignalR"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("SignalR", "Service destroyed")
        stopAndCleanupInternal(stopSelf = false)
        super.onDestroy()
    }
}