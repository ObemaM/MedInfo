package com.example.medinfo.data.signalr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.cache.CallsCache
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.ui.incoming.IncomingCallActivity
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
        return runBlocking(Dispatchers.IO) {
            val number = callNumber?.trim().orEmpty()
            if (number.isEmpty()) return@runBlocking false

            val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
            val userLogin = sharedPrefs.getString("user_login", null) ?: return@runBlocking false
            callsCache.containsCallNumber(userLogin, number)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isSessionActive()) {
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
        val sharedPrefs = getSharedPreferences(ConfigManager.sessionPrefsName, Context.MODE_PRIVATE)
        val token = sharedPrefs.getString(ConfigManager.jwtTokenKey, "") ?: ""
        val hubUrl = ConfigManager.signalrHubUrl

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token))
            .build()

        hubConnection?.on("Receive", { callData ->

            if (!isSessionActive()) {
                stopAndCleanup()
                return@on
            }

            val statusLower = callData.status?.lowercase()
            when (statusLower) {
                "транспортировка" -> {
                    val inCache = isCallInDiskCache(callData.callNumber)
                    if (!inCache) {
                        CallsManager.addCall(callData)
                        triggerFullscreenAlert(callData)
                    }
                }
                "результат", "архив" -> {
                    callData.callNumber?.let { CallsManager.removeCall(it) }
                    IncomingCallRinger.stop()
                }
                else -> {
                }
            }
        }, CallNotificationDto::class.java)

        hubConnection?.onClosed { exception ->
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
            } catch (e: Exception) {
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
        stopAndCleanupInternal(stopSelf = false)
        super.onDestroy()
    }
}