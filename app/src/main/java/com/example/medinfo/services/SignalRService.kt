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
        val result = isLoggedIn && token.isNotBlank()
        Log.d("SignalR", "=== isSessionActive: isLoggedIn=$isLoggedIn, hasToken=${token.isNotBlank()}, result=$result ===")
        return result
    }

    private fun isCallInDiskCache(callNumber: String?): Boolean {
        val number = callNumber?.trim().orEmpty()
        if (number.isEmpty()) {
            Log.d("SignalR", "=== isCallInDiskCache: Empty callNumber, returning false ===")
            return false
        }

        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val userLogin = sharedPrefs.getString("user_login", null) ?: run {
            Log.d("SignalR", "=== isCallInDiskCache: No user login, returning false ===")
            return false
        }
        val result = callsCache.containsCallNumber(userLogin, number)
        Log.d("SignalR", "=== isCallInDiskCache: callNumber=$number, user=$userLogin, result=$result ===")
        return result
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SignalR", "=== onCreate: Service created, initializing notification channels ===")
        createNotificationChannels()
        Log.d("SignalR", "=== onCreate: Notification channels created ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SignalR", "=== onStartCommand: Service start requested, startId=$startId ===")
        if (!isSessionActive()) {
            Log.w("SignalR", "=== onStartCommand: Session not active, stopping service ===")
            stopAndCleanup()
            return START_NOT_STICKY
        }
        Log.d("SignalR", "=== onStartCommand: Session is active, starting foreground service ===")
        // Запуск Foreground для живучести сервиса
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("Ожидание вызовов")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID_SERVICE, notification)

        if (hubConnection == null) {
            Log.d("SignalR", "=== onStartCommand: HubConnection is null, initializing SignalR ===")
            initSignalR()
        } else {
            Log.d("SignalR", "=== onStartCommand: HubConnection already exists, skipping init ===")
        }

        Log.d("SignalR", "=== onStartCommand: Returning START_STICKY ===")
        return START_STICKY
    }

    private fun initSignalR() {
        Log.d("SignalR", "=== initSignalR: Starting SignalR initialization ===")
        if (!isSessionActive()) {
            Log.w("SignalR", "=== initSignalR: Session not active, stopping ===")
            stopAndCleanup()
            return
        }
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""
        val hubUrl = "http://46.146.213.95:27234/call"
        Log.d("SignalR", "=== initSignalR: Building connection to $hubUrl ===")

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token))
            .build()
        Log.d("SignalR", "=== initSignalR: HubConnection built successfully ===")

        // Главный обработчик входящих данных
        hubConnection?.on("Receive", { callData ->
            Log.d("SignalR", "=== Receive: CALLBACK TRIGGERED ===")
            Log.d("SignalR", "=== Receive: Call #${callData.callNumber}, status=${callData.status}, patient=${callData.fullName} ===")
            if (!isSessionActive()) {
                Log.w("SignalR", "=== Receive: Session not active, stopping ===")
                stopAndCleanup()
                return@on
            }
            Log.d("SignalR", "=== Receive: Session is active, processing call ===")

            // 1. Реагируем в зависимости от статуса (ТЗ заказчика)
            val statusLower = callData.status?.lowercase()
            Log.d("SignalR", "=== Receive: Processing status='$statusLower' ===")
            when (statusLower) {
                "транспортировка" -> {
                    Log.d("SignalR", "=== Receive: Status is 'транспортировка' - CRITICAL ===")
                    Log.d("SignalR", "=== Receive: Checking disk cache first ===")
                    val inCache = isCallInDiskCache(callData.callNumber)
                    Log.d("SignalR", "=== Receive: isCallInDiskCache=$inCache ===")
                    if (!inCache) {
                        Log.d("SignalR", "=== Receive: Call NOT in cache, adding to queue and triggering alert ===")
                        // Only add to queue if not cached
                        CallsManager.addCall(callData)
                        triggerFullscreenAlert(callData)
                        Log.d("SignalR", "=== Receive: Alert triggered ===")
                    } else {
                        Log.d("SignalR", "=== Receive: Call in cache, SKIPPING entirely (no queue, no alert) ===")
                    }
                    Log.d("SignalR", "=== Receive: 'транспортировка' processing complete ===")
                }
                "результат", "архив" -> {
                    Log.d("SignalR", "=== Receive: Status is '$statusLower' - removing from active ===")
                    // Удаляем из активных, так как вызов завершен
                    callData.callNumber?.let { 
                        Log.d("SignalR", "=== Receive: Removing call #$it from CallsManager ===")
                        CallsManager.removeCall(it) 
                    }
                    Log.d("SignalR", "=== Receive: Stopping ringer ===")
                    IncomingCallRinger.stop()
                    Log.d("SignalR", "=== Receive: Cleanup complete for completed call ===")
                }
                else -> {
                    Log.d("SignalR", "=== Receive: Unknown status '$statusLower', no action ===")
                }
            }
            Log.d("SignalR", "=== Receive: CALLBACK COMPLETE ===")
        }, CallNotificationDto::class.java)

        hubConnection?.onClosed { exception ->
            Log.e("SignalR", "=== onClosed: Connection closed. Error: ${exception?.message} ===")
            if (isSessionActive()) {
                Log.d("SignalR", "=== onClosed: Session active, reconnecting... ===")
                startHubConnection()
            } else {
                Log.w("SignalR", "=== onClosed: Session not active, stopping ===")
                stopAndCleanup()
            }
        }

        Log.d("SignalR", "=== initSignalR: Starting hub connection ===")
        startHubConnection()
        Log.d("SignalR", "=== initSignalR: Initialization complete ===")
    }

    private fun startHubConnection() {
        Log.d("SignalR", "=== startHubConnection: Beginning connection attempt ===")
        // Используем Thread только для старта, чтобы не блокировать UI
        Thread {
            try {
                if (!isSessionActive()) {
                    Log.w("SignalR", "=== startHubConnection: Session not active, stopping ===")
                    stopAndCleanup()
                    return@Thread
                }
                Log.d("SignalR", "=== startHubConnection: Calling hubConnection.start() ===")
                hubConnection?.start()?.blockingAwait()
                Log.i("SignalR", "=== startHubConnection: Connection ESTABLISHED ===")
            } catch (e: Exception) {
                Log.e("SignalR", "=== startHubConnection: ERROR - ${e.message}. Retrying in 5s... ===")
                Thread.sleep(5000)
                if (isSessionActive()) {
                    Log.d("SignalR", "=== startHubConnection: Retrying connection ===")
                    startHubConnection()
                } else {
                    Log.w("SignalR", "=== startHubConnection: Session not active during retry, stopping ===")
                    stopAndCleanup()
                }
            }
        }.start()
    }

    private fun stopAndCleanup() {
        Log.d("SignalR", "=== stopAndCleanup: Stopping service ===")
        stopAndCleanupInternal(stopSelf = true)
        Log.d("SignalR", "=== stopAndCleanup: Service stopped ===")
    }

    private fun stopAndCleanupInternal(stopSelf: Boolean) {
        Log.d("SignalR", "=== stopAndCleanupInternal: stopSelf=$stopSelf ===")
        try {
            Log.d("SignalR", "=== stopAndCleanupInternal: Stopping hubConnection ===")
            hubConnection?.stop()
            Log.d("SignalR", "=== stopAndCleanupInternal: HubConnection stopped ===")
        } catch (e: Exception) {
            Log.e("SignalR", "=== stopAndCleanupInternal: Error stopping hubConnection - ${e.message} ===")
        }
        hubConnection = null
        Log.d("SignalR", "=== stopAndCleanupInternal: Stopping ringer and clearing calls ===")
        IncomingCallRinger.stop()
        CallsManager.clearAll()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Log.d("SignalR", "=== stopAndCleanupInternal: Canceling service notification ===")
        notificationManager.cancel(NOTIFICATION_ID_SERVICE)
        Log.d("SignalR", "=== stopAndCleanupInternal: Stopping foreground ===")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d("SignalR", "=== stopAndCleanupInternal: Foreground stopped ===")
        if (stopSelf) {
            Log.d("SignalR", "=== stopAndCleanupInternal: Calling stopSelf() ===")
            stopSelf()
        }
        Log.d("SignalR", "=== stopAndCleanupInternal: Complete ===")
    }

    private fun triggerFullscreenAlert(callData: CallNotificationDto) {
        Log.d("SignalR", "=== triggerFullscreenAlert: START - call #${callData.callNumber} ===")
        
        Log.d("SignalR", "=== triggerFullscreenAlert: Starting continuous ringer ===")
        IncomingCallRinger.start(this)
        Log.d("SignalR", "=== triggerFullscreenAlert: Continuous ringer started ===")

        Log.d("SignalR", "=== triggerFullscreenAlert: Creating intent for IncomingCallActivity ===")
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALL_DATA", callData)
            Log.d("SignalR", "=== triggerFullscreenAlert: Added CALL_DATA extra ===")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            Log.d("SignalR", "=== triggerFullscreenAlert: Intent flags=${flags} ===")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                putExtra("FORCE_SHOW", true)
                Log.d("SignalR", "=== triggerFullscreenAlert: Added FORCE_SHOW for Android O_MR1+ ===")
            }
        }

        Log.d("SignalR", "=== triggerFullscreenAlert: Attempting direct startActivity() ===")
        try {
            startActivity(fullScreenIntent)
            Log.d("SignalR", "=== triggerFullscreenAlert: SUCCESS - startActivity() completed ===")
        } catch (e: Exception) {
            Log.e("SignalR", "=== triggerFullscreenAlert: FAILED - startActivity() error: ${e.message} ===")
            Log.e("SignalR", "=== triggerFullscreenAlert: Exception type: ${e.javaClass.simpleName} ===")
        }

        Log.d("SignalR", "=== triggerFullscreenAlert: COMPLETE ===")
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
        Log.d("SignalR", "=== onDestroy: Service being destroyed ===")
        stopAndCleanupInternal(stopSelf = false)
        super.onDestroy()
        Log.d("SignalR", "=== onDestroy: Service destroyed ===")
    }
}