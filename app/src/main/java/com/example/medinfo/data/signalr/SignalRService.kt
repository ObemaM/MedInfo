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

    // TEST MODE: Set to true to disable SignalR connection (for Doze mode testing)
    private val TEST_MODE_DISABLE_SIGNALR = ConfigManager.testModeDisableSignalR

    private var hubConnection: HubConnection? = null
    private val CHANNEL_ID_SERVICE = ConfigManager.notificationChannelIdService

    private val NOTIFICATION_ID_SERVICE = ConfigManager.notificationIdService

    private val callsCache by lazy { CallsCache(applicationContext) }

    private fun isSessionActive(): Boolean {
        val sharedPrefs = getSharedPreferences(ConfigManager.sessionPrefsName, Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)
        val token = sharedPrefs.getString(ConfigManager.jwtTokenKey, "") ?: ""
        return isLoggedIn && token.isNotBlank()
    }

    private fun isCallInDiskCache(callNumber: String?): Boolean {
        return runBlocking(Dispatchers.IO) {
            val number = callNumber?.trim().orEmpty()
            if (number.isEmpty()) return@runBlocking false

            val sharedPrefs = getSharedPreferences(ConfigManager.sessionPrefsName, Context.MODE_PRIVATE)
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
        // TEST MODE: Skip SignalR connection for Doze mode testing
        if (TEST_MODE_DISABLE_SIGNALR) {
            android.util.Log.i("CALL_LOG", "[SignalR] TEST MODE: SignalR disabled for testing")
            return
        }

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
            android.util.Log.i("CALL_LOG", "[SignalR] Message received at ${System.currentTimeMillis()}")
            android.util.Log.i("CALL_LOG", "[SignalR] Call#: ${callData.callNumber}, Status: ${callData.status}")

            if (!isSessionActive()) {
                android.util.Log.w("CALL_LOG", "[SignalR] Session inactive, ignoring call")
                stopAndCleanup()
                return@on
            }

            val statusLower = callData.status?.lowercase()
            when (statusLower) {
                "транспортировка" -> {
                    android.util.Log.d("CALL_LOG", "[SignalR] Status=транспортировка, checking cache...")
                    val inCache = isCallInDiskCache(callData.callNumber)
                    android.util.Log.d("CALL_LOG", "[SignalR] Cache check result: $inCache")
                    if (!inCache) {
                        android.util.Log.i("CALL_LOG", "[SignalR] *** TRIGGERING CALL SCREEN *** Call#: ${callData.callNumber}")
                        CallsManager.addCall(callData)
                        triggerFullscreenAlert(callData)
                    } else {
                        android.util.Log.i("CALL_LOG", "[SignalR] Call already in cache, skipping")
                    }
                }
                "результат", "архив" -> {
                    android.util.Log.i("CALL_LOG", "[SignalR] Closing call: ${callData.callNumber}")
                    callData.callNumber?.let { CallsManager.removeCall(it) }
                    IncomingCallRinger.stop()
                }
                else -> {
                    android.util.Log.w("CALL_LOG", "[SignalR] Unknown status: $statusLower")
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
        android.util.Log.i("CALL_LOG", "[SignalR] triggerFullscreenAlert() called")
        android.util.Log.i("CALL_LOG", "[SignalR] Starting ringer for call: ${callData.callNumber}")
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
            android.util.Log.i("CALL_LOG", "[SignalR] Launching IncomingCallActivity...")
            startActivity(fullScreenIntent)
            android.util.Log.i("CALL_LOG", "[SignalR] Activity launch successful")
        } catch (e: Exception) {
            android.util.Log.e("CALL_LOG", "[SignalR] FAILED to launch activity: ${e.message}", e)
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